// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.model.Contract;
import com.raposza.api.model.PartyInfo;

import java.util.List;

/**
 * The script the CaQL editor opens with when the ledger is the Aviation
 * fixture.
 *
 * <h2>An empty editor is a worse first impression than an empty ledger</h2>
 *
 * A language nobody has seen before, in a pane with nothing in it, against a
 * ledger whose party ids are 130 characters: every one of those is a reason
 * not to try it. A script that runs as it stands removes all three.
 *
 * <h2>It reads, it writes, and the writes are chosen to SUCCEED</h2>
 *
 * The fixture's own defects are closed or already under investigation, and
 * an exercise that depends on which of them the last read happened to show
 * comes and goes. So the write half raises a defect of its OWN, on the one
 * airframe the fixture leaves without one, has the technician add evidence
 * to it, and has maintenance control take it into investigation. Each
 * exercise consumes the incarnation before it and takes its target out of
 * the previous result, `$defect`, `$withEvidence`, which is the whole of what
 * a binding is for - and one business defect with three contract ids is the
 * fixture's own demonstration, `fixture_aviation.md` section 6.3, done live.
 *
 * The read half's one query carries a `WHERE`, on the field the write half
 * changes: run it again afterwards and the count has moved.
 *
 * The two administrative statements at the end need admin rights and say so:
 * on an authenticated stack a reading user is refused them, which is correct.
 *
 * <h2>IT IS LAID OUT TO FIT THE PANE - his instruction, 2026-09-22</h2>
 *
 * Every line is at most {@link #CNT_WIDTH} columns in the short-id form the
 * editor shows, so the starter is read without dragging the horizontal
 * scrollbar. The statements were one line each until that instruction, on the
 * reasoning that a reader runs them one at a time with Ctrl-Enter.
 *
 * THAT REASONING WAS WRONG AND THE EDITOR ALREADY DISAGREED.
 * `CaqlPanel.runLine` cuts the statement with `CaqlParser.spanAt`, which
 * scans to the first `;` at brace depth zero and does not care about newlines,
 * so Ctrl-Enter runs a statement from ANY of its lines. `CaqlSkills` said the
 * opposite in two places and has been corrected with this change.
 *
 * With `Short ids` cleared the ids go back to their full form and the lines
 * run long again; nothing can be laid out to hold a 130-character party id
 * inside a pane, and that is unchanged behaviour rather than a regression.
 *
 * <h2>The ids are the ledger's own, SHORTENED</h2>
 *
 * {@link ShortIds} is applied to the finished script, so what the editor shows
 * is what every other pane shows for the same party, and {@link LongIds} puts
 * the full form back on the way to the participant.
 *
 * The parties are written BARE, without quotes, which is what the grammar
 * accepts for anything carrying `::`. The contract the script fetches keeps
 * its quotes: a bare one would be read as a template reference.
 *
 * <h2>It is offered ONLY when the fixture is recognised, and the ROLES are
 * the whole gate</h2>
 *
 * The seven role parties have to be present and nothing else decides it.
 * Three times the food-safety starter vanished because a template name in its
 * gate stopped matching the ledger. A participant hosting parties named
 * `MaintenanceControl-`, `Technician-`, `Inspector-`, `Engineering-`,
 * `Quality-`, `ConfigurationControl-` and `ReleaseAuthority-` is not another
 * ledger by accident, and everything the script needs beyond those seven is
 * emitted only where it is actually present.
 *
 * Author Claude/bentzn
 */
public final class AviationScript {

    /**
     * THE LAYOUT WIDTH, in columns of the short-id form.
     *
     * `PharmaScript` carries the same constant and the same value. Two
     * literals rather than one shared home is deliberate: the two starters are
     * independent of each other, and the cost of them drifting is that the two
     * tabs wrap differently, which is cosmetic.
     */
    public static final int CNT_WIDTH = 88;

    /** The module every template of the fixture sits in. */
    public static final String NAME_MODULE = "Main";

    /** The template the script fetches one contract of. */
    public static final String NAME_RELEASE = "Release";

    /** The template the write half creates and exercises. */
    public static final String NAME_DEFECT = "Defect";

    /** The business identity of the defect this script raises. */
    public static final String STR_DEFECT_CAQL = "DEF-CAQL";

    /**
     * THE AIRFRAME THE DEFECT IS RAISED ON: the one the fixture leaves without
     * a defect, so the script adds a story rather than editing one.
     */
    public static final String STR_MSN = "MSN-5822";

    private static final String USER_SUPER = "superuser";

    private static final String USER_ADMIN = "participant_admin";

    private static final String USER_CONTROL = "maintenancecontrol";

    private static final String USER_TECHNICIAN = "technician";

    private static final String USER_ENGINEERING = "engineering";

    private static final String USER_RELEASE = "releaseauthority";

    private static final String HINT_CONTROL = "MaintenanceControl-";

    private static final String HINT_TECHNICIAN = "Technician-";

    private static final String HINT_INSPECTOR = "Inspector-";

    private static final String HINT_ENGINEERING = "Engineering-";

    private static final String HINT_QUALITY = "Quality-";

    private static final String HINT_CONFIG = "ConfigurationControl-";

    private static final String HINT_RELEASE = "ReleaseAuthority-";

    /** What `Main.daml` stamps on every contract of the fixture. */
    private static final String STR_FIXTURE = "AVIATION-1";

    /**
     * MSN-5822's utilisation as the fixture leaves it: 11999 cycles is the
     * WHERE boundary value it carries, and the defect reports the same.
     */
    private static final String NUM_HOURS = "19875.25";

    private static final String NUM_CYCLES = "11999";

    private static final String NUM_STATION = "1180.00";

    private static final String NUM_CABIN_ALT = "8.10";

    private static final String NUM_CABIN_TEST = "8.05";

    /** One level of the WITH block, and the JSON inside it. */
    private static final String PAD_WITH = "    ";

    private static final String PAD_FIELD = "        ";

    private static final String PAD_ITEM = "            ";

    private static final String PAD_INNER = "              ";


    private AviationScript() {
    }


    /**
     * @param snapshot what the last read returned, may be null
     * @return the script, or null when this is not the fixture
     */
    public static String strFor(LedgerSnapshot snapshot) {
        if (snapshot == null)
            return null;
        return strFor(snapshot.lstParty(), snapshot.lstTemplate());
    }


    /**
     * @param lstParty the parties the participant reported
     * @param lstTemplate the templates the read found instances of
     * @return the script, or null when this is not the fixture
     */
    public static String strFor(List<PartyInfo> lstParty,
            List<LedgerSnapshot.TemplateGroup> lstTemplate) {
        String idControl = idFor(lstParty, HINT_CONTROL);
        String idTechnician = idFor(lstParty, HINT_TECHNICIAN);
        String idInspector = idFor(lstParty, HINT_INSPECTOR);
        String idEngineering = idFor(lstParty, HINT_ENGINEERING);
        String idQuality = idFor(lstParty, HINT_QUALITY);
        String idConfig = idFor(lstParty, HINT_CONFIG);
        String idRelease = idFor(lstParty, HINT_RELEASE);
        // THE SEVEN ROLES ARE THE FIXTURE, and they are the whole of the gate.
        if (idControl == null || idTechnician == null || idInspector == null
                || idEngineering == null || idQuality == null || idConfig == null
                || idRelease == null) {
            return null;
        }

        StringBuilder buf = new StringBuilder();
        buf.append("-- Aviation fixture. Ctrl-Enter runs the statement the caret is in,\n");
        buf.append("-- however many lines it runs to; Run runs all of it.\n");
        buf.append("\n");
        buf.append("-- READS\n");
        buf.append(who(USER_ADMIN));
        buf.append("LIST PARTIES;\n");
        buf.append("LIST USERS;\n");
        buf.append("GET USER \"" + USER_ENGINEERING + "\";\n");
        buf.append("\n").append(who(USER_CONTROL));
        // ONE QUERY - operator instruction. Four of them read as a list of
        // templates rather than as an example of the statement; the one
        // shown is the template the write half creates, sieved on the field
        // the write half sets, so the reader can run it again afterwards and
        // see the count move.
        buf.append(query(idControl, NAME_DEFECT, "grounded = true"));
        String idReleaseContract = idContractOf(lstTemplate, NAME_RELEASE);
        if (idReleaseContract != null) {
            buf.append("\n").append(who(USER_RELEASE));
            buf.append("release = AS ").append(idRelease).append(" FETCH ")
                    .append(quoted(idReleaseContract)).append(";\n");
        }
        buf.append("\n");
        buf.append("-- WRITES. A defect of our own on " + STR_MSN + ", evidence the\n");
        buf.append("-- technician adds to it, and maintenance control taking it into\n");
        buf.append("-- investigation. Each exercise consumes the incarnation before it and\n");
        buf.append("-- takes its target out of the last result.\n");
        buf.append(who(USER_CONTROL));
        buf.append(createDefect(idControl, idTechnician, idEngineering, idQuality));
        buf.append("\n").append(who(USER_TECHNICIAN));
        buf.append(addEvidence(idTechnician));
        buf.append("\n").append(who(USER_CONTROL));
        buf.append(advance(idControl, idTechnician));
        buf.append("\n");
        buf.append("-- ADMINISTRATIVE. Both need admin rights on this connection.\n");
        buf.append(whoAdmin());
        buf.append("trainee = ALLOCATE PARTY \"Trainee\";\n");
        buf.append(createUser());

        return ShortIds.text(buf.toString());
    }


    /**
     * WHO HAS TO RUN THE LINE UNDER IT.
     *
     * `work as` decides the token, and the token decides whether a statement
     * is accepted - a party in `AS` that the session's user cannot act as is
     * refused by the participant rather than by CaQL. So every statement says
     * which user it needs.
     *
     * `superuser` is the fixture's own: `CanActAs` on every role, and no
     * admin rights - which is why the administrative pair does not offer it.
     * It is named on every line because it is the one user that can run all
     * of them.
     *
     * @param idUser the user whose rights the statement needs
     * @return the comment line, terminated
     */
    private static String who(String idUser) {
        return "-- User: " + idUser + " or " + USER_SUPER + "\n";
    }


    /**
     * NO `superuser`. It holds parties and no participant admin rights -
     * `Main.daml` grants the fixture's superuser `CanActAs` and `CanReadAs`
     * and nothing else - so the two administrative statements name the one
     * user that can run them and no alternative.
     *
     * @return the comment line, terminated
     */
    private static String whoAdmin() {
        return "-- User: " + USER_ADMIN + "\n";
    }


    /**
     * THE CLAUSE GOES ON ITS OWN LINE when the whole statement would not fit,
     * which is the one wrap a read needs.
     *
     * @param idParty who reads
     * @param nameEntity the template, unqualified
     * @param strWhere the clause, without the keyword
     * @return the QUERY statement, terminated
     */
    private static String query(String idParty, String nameEntity, String strWhere) {
        String strHead = "AS " + idParty + " QUERY " + NAME_MODULE + ":" + nameEntity;
        String strOne = strHead + " WHERE " + strWhere + ";";
        if (ShortIds.text(strOne).length() <= CNT_WIDTH)
            return strOne + "\n";
        return strHead + "\n" + PAD_WITH + "WHERE " + strWhere + ";\n";
    }


    /**
     * The defect carries the whole of the datatype spread the fixture exists
     * to render - a record with two Optionals inside it, a list of records
     * with a list of records inside each, an enum and a Bool - because a
     * starter that creates a flat contract shows nothing the fixture's own
     * contracts do not show better.
     *
     * @param idControl the signatory
     * @param idTechnician the technician, who is assigned it
     * @param idEngineering engineering, an observer
     * @param idQuality quality, an observer
     * @return the CREATE statement, terminated
     */
    private static String createDefect(String idControl, String idTechnician,
            String idEngineering, String idQuality) {
        return "defect = AS " + idControl + "\n"
                + PAD_WITH + "CREATE " + NAME_MODULE + ":" + NAME_DEFECT + " WITH {\n"
                + PAD_FIELD + field("fixtureId", STR_FIXTURE) + ",\n"
                + PAD_FIELD + field("maintenanceControl", idControl) + ",\n"
                + PAD_FIELD + field("technician", idTechnician) + ",\n"
                + PAD_FIELD + field("engineering", idEngineering) + ",\n"
                + PAD_FIELD + field("quality", idQuality) + ",\n"
                + PAD_FIELD + field("defectId", STR_DEFECT_CAQL) + ",\n"
                + PAD_FIELD + field("msn", STR_MSN) + ",\n"
                + PAD_FIELD + field("reportedBy", "Anja Larsen") + ",\n"
                + PAD_FIELD + field("assignedTo", idTechnician) + ",\n"
                + PAD_FIELD + field("reportedAt", "2026-09-14T07:30:00Z") + ",\n"
                + PAD_FIELD + field("flightHours", NUM_HOURS) + ",\n"
                + PAD_FIELD + field("cycles", NUM_CYCLES) + ",\n"
                + PAD_FIELD + field("severity", "Major") + ",\n"
                + PAD_FIELD + "\"location\": {\n"
                + PAD_ITEM + field("ataChapter", "21") + ", "
                + field("zone", "Aft cargo, right") + ",\n"
                + PAD_ITEM + field("station", NUM_STATION) + "\n"
                + PAD_FIELD + "},\n"
                + PAD_FIELD
                + field("description", "Cabin pressure controller 1 fault light on descent")
                + ",\n"
                + PAD_FIELD + "\"evidence\": [\n"
                + PAD_ITEM + evidence("Cockpit ECAM message, descent",
                        "\"TLB-5822-0288\"", NUM_CABIN_ALT) + "\n"
                + PAD_FIELD + "],\n"
                + PAD_FIELD + "\"tags\": [ \"pressurisation\" ],\n"
                + PAD_FIELD + "\"grounded\": true,\n"
                + PAD_FIELD + field("state", "Open") + "\n"
                + PAD_WITH + "};\n";
    }


    /**
     * ONE Evidence OVER THREE LINES, so the measurement list is not pushed off
     * the right edge by the description in front of it.
     *
     * @param strDescription what was seen
     * @param strReference the reference, as JSON - a quoted text or `null`
     * @param numCabinAlt the one measurement, in psi
     * @return one Evidence record, as JSON, its first line unindented
     */
    private static String evidence(String strDescription, String strReference,
            String numCabinAlt) {
        return "{ " + field("description", strDescription) + ",\n"
                + PAD_INNER + "\"reference\": " + strReference + ",\n"
                + PAD_INNER + "\"measurements\": [ { " + field("value", numCabinAlt) + ", "
                + field("unit", "psi") + " } ] }";
    }


    /**
     * THE EVIDENCE IS THE TECHNICIAN'S TO ADD and the template enforces it, so
     * this is also the shortest demonstration that the role rules are real:
     * maintenance control signs the defect and cannot exercise this choice
     * on it.
     *
     * @param idTechnician the only controller of the choice
     * @return the EXERCISE statement, terminated
     */
    private static String addEvidence(String idTechnician) {
        return "withEvidence = AS " + idTechnician + "\n"
                + PAD_WITH + "EXERCISE ON $defect AddEvidence WITH {\n"
                + PAD_FIELD + "\"evidenceNew\":\n"
                + PAD_ITEM
                + evidence("Ground test, controller 1 self-test", "null", NUM_CABIN_TEST)
                + "\n"
                + PAD_WITH + "};\n";
    }


    /**
     * The third incarnation. Its target is the SECOND one, out of the
     * previous exercise's result: `$defect` was consumed by `AddEvidence` and
     * a script that exercised it again would be refused locally as stale.
     *
     * @param idControl the only controller of the choice
     * @param idTechnician who the defect is assigned to
     * @return the EXERCISE statement, terminated
     */
    private static String advance(String idControl, String idTechnician) {
        return "assigned = AS " + idControl + "\n"
                + PAD_WITH + "EXERCISE ON $withEvidence AdvanceDefect WITH {\n"
                + PAD_FIELD + field("stateNew", "UnderInvestigation") + ",\n"
                + PAD_FIELD + field("assignedNew", idTechnician) + ",\n"
                + PAD_FIELD + "\"groundedNew\": true\n"
                + PAD_WITH + "};\n";
    }


    /**
     * @return the CREATE USER statement, terminated
     */
    private static String createUser() {
        return "CREATE USER \"caql-trainee\" WITH {\n"
                + PAD_WITH + "\"primaryParty\": \"$trainee\",\n"
                + PAD_WITH + "\"rights\": [ { \"canReadAs\": \"$trainee\" } ]\n"
                + "};\n";
    }


    private static String field(String nameField, String strValue) {
        return quoted(nameField) + ": " + quoted(strValue);
    }


    private static String quoted(String strValue) {
        return "\"" + strValue + "\"";
    }


    /**
     * @param lstTemplate what was read
     * @param nameEntity the template wanted
     * @return the id of its first contract, or null when there is not one
     */
    private static String idContractOf(List<LedgerSnapshot.TemplateGroup> lstTemplate,
            String nameEntity) {
        LedgerSnapshot.TemplateGroup group = group(lstTemplate, nameEntity);
        if (group == null || group.lstContract() == null || group.lstContract().isEmpty())
            return null;

        Contract contract = group.lstContract().get(0);
        return contract == null ? null : contract.idContract();
    }


    /**
     * @param lstTemplate what was read
     * @param nameEntity the template wanted
     * @return its group, or null
     */
    private static LedgerSnapshot.TemplateGroup group(
            List<LedgerSnapshot.TemplateGroup> lstTemplate, String nameEntity) {
        if (lstTemplate == null)
            return null;

        for (LedgerSnapshot.TemplateGroup group : lstTemplate) {
            if (group.idTemplate() != null
                    && NAME_MODULE.equals(group.idTemplate().nameModule())
                    && nameEntity.equals(group.idTemplate().nameEntity())) {
                return group;
            }
        }
        return null;
    }


    /**
     * @param lstParty the parties
     * @param strHint what the fixture names the role
     * @return the one party id starting with it, or null when it is not there
     *         or is there twice
     */
    private static String idFor(List<PartyInfo> lstParty, String strHint) {
        String idFound = null;
        if (lstParty == null)
            return null;

        for (PartyInfo party : lstParty) {
            if (party.idParty() == null || !party.idParty().startsWith(strHint))
                continue;
            if (idFound != null)
                return null;
            idFound = party.idParty();
        }
        return idFound;
    }

}
