// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.runtime.db.PostgresCoordinates;
import com.raposza.sandbox.gui.LedgerProbe.Contract;
import com.raposza.sandbox.gui.LedgerProbe.Exercise;
import com.raposza.sandbox.gui.LedgerProbe.Ledger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Ledger log: one line per contract created, per choice exercised and per
 * contract archived, so a reader can see that SOMETHING happened and which
 * template it involved - his instruction, 2026-10-04.
 *
 * <h2>Read out of the participant's database, like the data pane</h2>
 *
 * {@link LedgerProbe} is the one reader of Canton's schema this application
 * has measured, on 2.x and on 3.x, and it needs no token and no party list -
 * where the JSON Ledger API's update stream needs both and has never been
 * measured from this window. Every {@link #N_MS_POLL} each followed database
 * is read and compared with the previous read: a contract id not seen before
 * is `created`, a created id that has moved to the archive side is `archived`
 * - or `exercised` with the choice, where the archive table names the choice
 * that consumed it - and a row not seen before in a table of choices that
 * archive nothing is `exercised`. The first read of a database is the
 * baseline and says only how many contracts it found.
 *
 * <h2>SHORT LINES - his instruction, 2026-10-04</h2>
 *
 * `app-provider  created  Main:Aircraft  #81`. Where the templates and the
 * choices were found is said ONCE, and only when one of them was not:
 * measured on his LocalNetND 0.8.3 the templates come from
 * `participant.par_contracts.template_id`, and a line saying so on every
 * start was a line nobody needed.
 *
 * <h2>Splice's own contracts are not reported</h2>
 *
 * On LocalNetND the DSO mints rounds and the validators churn amulet state
 * every few seconds; a log that showed those would always be moving and would
 * say nothing about the application. A template whose module starts with one
 * of {@link #ARR_MODULE_QUIET} is skipped.
 *
 * <h2>EXCEPT FUNDS - his question, 2026-10-04</h2>
 *
 * "The Ledger log does not show the addition of funds?" A wallet's Tap was
 * hidden with the rest of Splice. The amulet holdings,
 * {@link #SET_TEMPLATE_FUNDS}, and the choices that move them,
 * {@link #SET_CHOICE_FUNDS}, are shown. The names are Splice's own Daml -
 * `Splice.Amulet` and `Splice.AmuletRules` in splice-amulet - and NOT
 * MEASURED on his ledger; the choice names only show where a table of choices
 * is found. A round's reward collection creates an amulet too, so the sv
 * node will show one now and then with no one having tapped.
 *
 * Author Claude/bentzn
 */
final class LedgerWatch {

    /** How often each followed database is read. */
    static final long N_MS_POLL = 2000L;

    /** Modules whose contracts are the network's own business, not the user's. */
    static final String[] ARR_MODULE_QUIET = { "Splice." };

    /** Splice's templates that ARE shown: what a wallet holds. */
    static final Set<String> SET_TEMPLATE_FUNDS = Set.of("Splice.Amulet:Amulet",
            "Splice.Amulet:LockedAmulet");

    /** Splice's choices that ARE shown: what adds or moves funds. */
    static final Set<String> SET_CHOICE_FUNDS = Set.of("AmuletRules_DevNet_Tap",
            "AmuletRules_Transfer");

    private final Consumer<String> sink;

    private Thread thread;


    /**
     * @param sinkNew told one line at a time, from the poll thread
     */
    LedgerWatch(Consumer<String> sinkNew) {
        this.sink = sinkNew;
    }


    /**
     * What to follow from now on. An empty map stops the watch.
     *
     * @param mapNode node label to its participant database
     */
    synchronized void follow(Map<String, PostgresCoordinates> mapNode) {
        stop();
        if (mapNode == null || mapNode.isEmpty())
            return;
        Map<String, PostgresCoordinates> mapHere = new LinkedHashMap<>(mapNode);
        thread = new Thread(() -> loop(mapHere), "sandbox-ledger-watch");
        thread.setDaemon(true);
        thread.start();
    }


    synchronized void stop() {
        Thread threadHere = thread;
        thread = null;
        if (threadHere != null)
            threadHere.interrupt();
    }


    private void loop(Map<String, PostgresCoordinates> mapNode) {
        Map<String, Snapshot> mapLast = new LinkedHashMap<>();
        Set<String> setFailed = new LinkedHashSet<>();
        while (!Thread.currentThread().isInterrupted()) {
            for (Map.Entry<String, PostgresCoordinates> entry : mapNode.entrySet()) {
                String strNode = entry.getKey();
                Ledger ledger;
                try {
                    ledger = LedgerProbe.read(entry.getValue());
                }
                catch (SQLException | RuntimeException ex) {
                    // ONCE PER STREAK. A database that is going away with the
                    // stack would otherwise write the same refusal every poll.
                    if (setFailed.add(strNode))
                        sink.accept(strNode + "  cannot be read: " + ex.getMessage());
                    continue;
                }
                setFailed.remove(strNode);
                Snapshot now = Snapshot.of(ledger);
                Snapshot before = mapLast.put(strNode, now);
                if (before == null) {
                    sink.accept(strNode + "  following, " + now.setCreated.size() + " contracts");
                    // THE MEASUREMENT, only when it came up empty.
                    if (ledger.strTemplateSource().startsWith("not found"))
                        sink.accept(strNode + "  templates " + ledger.strTemplateSource());
                    if (ledger.lstSourceExercise().isEmpty())
                        sink.accept(strNode + "  no table of choices found - only choices"
                                + " that archive a contract are shown");
                    continue;
                }
                for (String strLine : lstLine(strNode, before, now)) {
                    sink.accept(strLine);
                }
            }
            try {
                Thread.sleep(N_MS_POLL);
            }
            catch (InterruptedException ex) {
                return;
            }
        }
    }


    /**
     * @param strNode the node label
     * @param before the previous read
     * @param now this read
     * @return one line per contract created, choice exercised or contract
     *         archived between the two, in ledger order, the quiet modules
     *         left out
     */
    static List<String> lstLine(String strNode, Snapshot before, Snapshot now) {
        List<Event> lstEvent = new ArrayList<>();
        for (Contract contract : now.lstCreated) {
            if (!before.setCreated.contains(contract.strId()))
                lstEvent.add(new Event(contract.strOffset(), "created", contract.strTemplate(),
                        "", contract.strId()));
        }
        for (Contract contract : now.lstArchived) {
            if (before.setArchived.contains(contract.strId()))
                continue;
            boolean flagChoice = contract.strChoice() != null && !contract.strChoice().isEmpty();
            lstEvent.add(new Event(contract.strOffset(), flagChoice ? "exercised" : "archived",
                    contract.strTemplate(), flagChoice ? contract.strChoice() : "",
                    contract.strId()));
        }
        for (Exercise exercise : now.lstExercise) {
            if (!before.setExercise.contains(exercise.strKey()))
                lstEvent.add(new Event(exercise.strOffset(), "exercised", exercise.strTemplate(),
                        exercise.strChoice(), exercise.strContract()));
        }
        // LEDGER ORDER, by the ordering column where it is a number; the order
        // the reads produced otherwise, which is the creates first.
        lstEvent.sort(Comparator.comparingLong(Event::nOrder));

        List<String> lstOut = new ArrayList<>();
        for (Event event : lstEvent) {
            if (isQuiet(event.strTemplate, event.strChoice))
                continue;
            String strWhat = strTemplateShown(event.strTemplate)
                    + (event.strChoice.isEmpty() ? "" : "." + event.strChoice);
            lstOut.add(strNode + "  " + event.strVerb + "  " + strWhat
                    + (event.strId.isEmpty() ? "" : "  #" + strShort(event.strId)));
        }
        return lstOut;
    }


    /**
     * @param strId a contract id, or an internal contract number
     * @return its last 8 characters
     */
    private static String strShort(String strId) {
        return strId.length() > 8 ? strId.substring(strId.length() - 8) : strId;
    }


    /**
     * @param strTemplate an interned template, `package:Module:Entity`
     * @return `Module:Entity`; the whole string when it carries no package,
     *         which is anything with fewer than two colons
     */
    static String strTemplateShown(String strTemplate) {
        if (strTemplate == null || strTemplate.isEmpty())
            return "?";
        int idxColon = strTemplate.indexOf(':');
        if (idxColon < 0 || strTemplate.indexOf(':', idxColon + 1) < 0)
            return strTemplate;
        return strTemplate.substring(idxColon + 1);
    }


    /**
     * @param strTemplate an interned template
     * @return whether its module is one of the network's own and it is not
     *         one of the funds
     */
    static boolean isQuiet(String strTemplate) {
        return isQuiet(strTemplate, "");
    }


    /**
     * @param strTemplate an interned template
     * @param strChoice the choice exercised on it, or empty
     * @return whether the event is the network's own business
     */
    static boolean isQuiet(String strTemplate, String strChoice) {
        String strShown = strTemplateShown(strTemplate);
        if (SET_TEMPLATE_FUNDS.contains(strShown) || SET_CHOICE_FUNDS.contains(strChoice))
            return false;
        for (String strPrefix : ARR_MODULE_QUIET) {
            if (strShown.startsWith(strPrefix))
                return true;
        }
        return false;
    }


    /** One line's worth, before it is worded. */
    private record Event(String strOffset, String strVerb, String strTemplate, String strChoice,
            String strId) {

        long nOrder() {
            try {
                return Long.parseLong(strOffset.trim());
            }
            catch (NumberFormatException | NullPointerException ex) {
                return Long.MAX_VALUE;
            }
        }
    }


    /** One read, as the next one is compared against it. */
    static final class Snapshot {

        final List<Contract> lstCreated = new ArrayList<>();

        final List<Contract> lstArchived = new ArrayList<>();

        final List<Exercise> lstExercise = new ArrayList<>();

        final Set<String> setCreated = new LinkedHashSet<>();

        final Set<String> setArchived = new LinkedHashSet<>();

        final Set<String> setExercise = new LinkedHashSet<>();


        /**
         * @param lstActive the active contracts, newest first
         * @param lstArchived the archived ones, newest first
         */
        Snapshot(List<Contract> lstActive, List<Contract> lstArchived) {
            this(lstActive, lstArchived, List.of());
        }


        /**
         * @param lstActive the active contracts, newest first
         * @param lstArchived the archived ones, newest first
         * @param lstExercise the choices that archived nothing
         */
        Snapshot(List<Contract> lstActive, List<Contract> lstArchived,
                List<Exercise> lstExercise) {
            this.lstCreated.addAll(lstActive);
            this.lstCreated.addAll(lstArchived);
            this.lstArchived.addAll(lstArchived);
            this.lstExercise.addAll(lstExercise);
            for (Contract contract : lstCreated) {
                setCreated.add(contract.strId());
            }
            for (Contract contract : lstArchived) {
                setArchived.add(contract.strId());
            }
            for (Exercise exercise : lstExercise) {
                setExercise.add(exercise.strKey());
            }
        }


        static Snapshot of(Ledger ledger) {
            return new Snapshot(ledger.lstActive(), ledger.lstArchived(), ledger.lstExercise());
        }
    }

}
