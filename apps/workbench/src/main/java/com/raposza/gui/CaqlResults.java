// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.raposza.caql.CaqlException;
import com.raposza.caql.Entry;
import com.raposza.caql.Stmt;
import com.raposza.caql.Transcript;

import java.util.Locale;

/**
 * A transcript, as the operator wants to read it.
 *
 * <h2>Why not the transcript JSON</h2>
 *
 * The transcript is the record: it carries the argument twice, the statement
 * hash and the resolved package id, and it is written for something that will
 * read it later. The pane is read NOW, by the person who just pressed Run, and
 * the question is what happened - which of a transcript entry's nine fields is
 * two of them.
 *
 * <h2>The statement is ECHOED, not described</h2>
 *
 * Operator instruction. A line reading `query <package>:Main:Batch` restated
 * what the runner sent, and left the reader matching it back against what they
 * had written. `>> AS <party> QUERY Main:Batch` IS what they wrote, and the
 * ids in it are the same links the fields under it carry. It is the SPLITTER's
 * form - comments stripped, a continued object joined onto one line - so it is
 * what ran rather than how it was typed.
 *
 * The per-statement STATUS went with it. Only `validated` and `committed` let a
 * run continue, so every other status belongs to the LAST statement in the
 * transcript, and the closing line already names it with its line number.
 *
 * <h2>Bound parameters are not printed here</h2>
 *
 * Operator instruction. The Parameters tab holds them, with their types, and
 * it outlives the run - which a block at the bottom of one transcript does
 * not.
 *
 * <h2>Identifiers are printed WHOLE and bare</h2>
 *
 * {@link LinkText} finds them by shape and turns them into links, and it can
 * only do that to an identifier that is on its own rather than inside a JSON
 * string or a sentence. So every id here sits at the end of its line with
 * nothing after it. That is what makes a created contract clickable.
 *
 * <h2>An EXERCISE does not list what it created</h2>
 *
 * The runner records the exercise result and the update id, not the created
 * contracts - only a CREATE binds one. The update id is the link that answers
 * it: opening it puts the whole transaction tree, creates included, in the
 * Ledger tab. Inventing a "created" line here would mean re-reading the tree
 * to fill it, which is the pane doing a ledger call the operator did not ask
 * for.
 *
 * Author Claude/bentzn
 */
public final class CaqlResults {

    /** How many contract ids a QUERY lists before it stops. */
    public static final int CNT_ID_MAX = 25;

    /** How much of a rendered value a line carries. */
    private static final int CNT_VALUE_MAX = 160;

    private static final String STR_PAD = "         ";


    private CaqlResults() {
    }


    /**
     * @param transcript what the run produced
     * @return the block for the results pane
     */
    public static String text(Transcript transcript) {
        // NO BANNER ON A RUN - operator instruction. The pane is the CaQL
        // tab's own output, so a heading saying CAQL over it is a label on
        // a box that is already labelled. The VALIDATE form stays: that one
        // says something the transcript underneath it does not.
        StringBuilder buf = new StringBuilder(transcript.flagValidateOnly()
                ? Banner.head("CAQL - VALIDATE, NOTHING WAS SENT") : "");

        if (transcript.lstEntry().isEmpty())
            buf.append("The script holds no statements.\n");

        for (Entry entry : transcript.lstEntry()) {
            append(buf, entry);
        }

        buf.append('\n').append(strEnd(transcript)).append('\n');
        return buf.toString();
    }


    /** How much of a statement's first line a progress line carries. */
    static final int CNT_PROGRESS_SOURCE = 90;


    /**
     * The first line of a run's progress - his instruction, 2026-10-04.
     *
     * @param cntStmt how many statements the script holds
     * @param idUser who the run submits as
     * @return the line
     */
    public static String strProgressHead(int cntStmt, String idUser) {
        return "running " + cntStmt + (cntStmt == 1 ? " statement" : " statements")
                + " as " + (idUser == null || idUser.isBlank() ? "(no user)" : idUser);
    }


    /**
     * The line for a statement that is running now.
     *
     * @param numStmt one-based position in the script
     * @param cntStmt how many statements the script holds
     * @param stmt the statement
     * @return the line
     */
    public static String strProgressRunning(int numStmt, int cntStmt, Stmt stmt) {
        return strCounter(numStmt, cntStmt, stmt.numLine()) + String.format("%-15s", "running")
                + "         " + strFirstLine(stmt.strSource());
    }


    /**
     * The line for a statement that has ended, which replaces its running line.
     *
     * @param numStmt one-based position in the script
     * @param cntStmt how many statements the script holds
     * @param entry what it did
     * @param nMs how long it took
     * @return the line
     */
    public static String strProgressDone(int numStmt, int cntStmt, Entry entry, long nMs) {
        return strCounter(numStmt, cntStmt, entry.numLine())
                + String.format("%-15s", entry.status().strJson())
                + String.format(Locale.ROOT, "%7.2f s ", nMs / 1000.0) + strFirstLine(entry.strSource());
    }


    private static String strCounter(int numStmt, int cntStmt, int numLine) {
        int cntWidth = String.valueOf(cntStmt).length();
        return String.format("[%" + cntWidth + "d/%d]  line %-5d ", numStmt, cntStmt, numLine);
    }


    /**
     * @param strSource a statement as written, possibly over several lines
     * @return its first non-blank line, cut to {@link #CNT_PROGRESS_SOURCE}
     */
    static String strFirstLine(String strSource) {
        String strOut = "";
        for (String strLine : (strSource == null ? "" : strSource).split("\\R")) {
            if (!strLine.isBlank()) {
                strOut = strLine.strip();
                break;
            }
        }
        return strOut.length() <= CNT_PROGRESS_SOURCE ? strOut
                : strOut.substring(0, CNT_PROGRESS_SOURCE) + " ...";
    }


    /**
     * A script that never ran.
     *
     * The line and the statement are the whole of what the operator needs, and
     * the editor puts the caret on that line, so the two agree.
     *
     * @param ex what the parser refused
     * @return the block for the results pane
     */
    public static String textParse(CaqlException ex) {
        StringBuilder buf = new StringBuilder(Banner.head("CAQL - NOT RUN"));
        buf.append("line ").append(ex.numLine()).append('\n');
        if (!ex.strSource().isBlank())
            buf.append('\n').append(ex.strSource()).append('\n');
        buf.append('\n').append(MainWindow.wrap(ex.getMessage()));
        buf.append("\nNothing was sent to the participant.\n");
        return buf.toString();
    }


    /**
     * @param strWhy what stopped the run before it began
     * @return the block for the results pane
     */
    public static String textProblem(String strWhy) {
        return Banner.head("CAQL - NOT RUN") + MainWindow.wrap(strWhy);
    }


    private static void append(StringBuilder buf, Entry entry) {
        buf.append(">> ").append(entry.strSource()).append('\n');

        JsonNode resolved = entry.resolved();
        if (resolved != null) {
            line(buf, "party", resolved.path("party"));
            line(buf, "user", resolved.path("userId"));
            line(buf, "primary", resolved.path("primaryParty"));
            line(buf, "admin", resolved.path("admin"));
            texts(buf, "actAs", resolved.path("actAs"));
            texts(buf, "readAs", resolved.path("readAs"));
            line(buf, "contract", resolved.path("contractId"));
            line(buf, "offset", resolved.path("offset"));
            count(buf, resolved);
            items(buf, resolved);
            value(buf, "result", resolved.path("result"));
        }

        if (entry.idUpdate().isPresent())
            buf.append(STR_PAD).append("update   ").append(entry.idUpdate().get()).append('\n');

        if (entry.strError().isPresent()) {
            buf.append('\n').append(MainWindow.wrap(entry.strError().get()));
        }
    }


    /**
     * A QUERY's answer, capped.
     *
     * The cap is called out when it bites. A listing that silently stops is a
     * wrong answer about how many contracts there are, and the count above it
     * would then contradict the lines under it.
     */
    private static void count(StringBuilder buf, JsonNode resolved) {
        JsonNode arr = resolved.path("contractIds");
        if (!arr.isArray())
            return;

        buf.append(STR_PAD)
                .append(MainWindow.count(resolved.path("count").asInt(), "contract", "contracts"))
                .append('\n');

        int cntShown = 0;
        for (JsonNode node : arr) {
            if (cntShown >= CNT_ID_MAX) {
                buf.append(STR_PAD).append("... ").append(arr.size() - CNT_ID_MAX)
                        .append(" more, not listed\n");
                break;
            }
            buf.append(STR_PAD).append("  ").append(node.asText()).append('\n');
            cntShown++;
        }
    }


    /**
     * What a LIST enumerated.
     *
     * The same cap and the same one-per-line form as a QUERY's contract ids,
     * for the same reason: a party id has to sit at the end of its own line
     * or {@link LinkText} cannot turn it into a link. Without this the three
     * LIST statements rendered as a status line and nothing else - the
     * runner had put the answer in `items` and no reader looked at it.
     *
     * @param buf where it goes
     * @param resolved the statement's resolved node
     */
    private static void items(StringBuilder buf, JsonNode resolved) {
        JsonNode arr = resolved.path("items");
        if (!arr.isArray())
            return;

        buf.append(STR_PAD).append(MainWindow.count(arr.size(), "item", "items"))
                .append('\n');

        int cntShown = 0;
        for (JsonNode node : arr) {
            if (cntShown >= CNT_ID_MAX) {
                buf.append(STR_PAD).append("... ").append(arr.size() - CNT_ID_MAX)
                        .append(" more, not listed\n");
                break;
            }
            buf.append(STR_PAD).append("  ").append(node.asText()).append('\n');
            cntShown++;
        }
    }


    /**
     * @param buf where it goes
     * @param strLabel what to call them
     * @param node an array of strings, or anything else, which is skipped
     */
    private static void texts(StringBuilder buf, String strLabel, JsonNode node) {
        if (node == null || !node.isArray())
            return;

        for (JsonNode item : node) {
            buf.append(STR_PAD).append(String.format("%-8s ", strLabel))
                    .append(item.asText()).append('\n');
        }
    }


    private static void line(StringBuilder buf, String strLabel, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank())
            return;
        buf.append(STR_PAD).append(String.format("%-8s ", strLabel)).append(node.asText())
                .append('\n');
    }


    /**
     * A choice result is a whole value and can be a large one, so it is put on
     * one line and cut. The transcript holds it in full; this is a pane.
     */
    private static void value(StringBuilder buf, String strLabel, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull())
            return;

        String strValue = node.toString();
        if (strValue.length() > CNT_VALUE_MAX)
            strValue = strValue.substring(0, CNT_VALUE_MAX) + "\u2026";
        buf.append(STR_PAD).append(String.format("%-8s ", strLabel)).append(strValue).append('\n');
    }


    /**
     * @param transcript the run
     * @return the last line, which is the one the eye goes to
     */
    private static String strEnd(Transcript transcript) {
        if (transcript.flagOk()) {
            return transcript.flagValidateOnly()
                    ? "Validated. Nothing was sent."
                    : "Completed successfully.";
        }

        Entry entry = transcript.lstEntry().get(transcript.lstEntry().size() - 1);
        return "STOPPED at line " + entry.numLine() + " - " + entry.status().strJson()
                + ". Statements after it did not run.";
    }

}
