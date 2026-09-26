// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.model.Contract;
import com.raposza.api.model.TxNode;
import com.raposza.api.model.TxTree;
import com.raposza.api.model.UpdateScan;
import com.raposza.render.BlockRenderer;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * What happened TO one contract, folded out of a stretch of the update stream.
 *
 * <h2>What this is a merge of</h2>
 *
 * Every transaction in the window is walked and every node touching this
 * contract is kept: the create, each exercise on it, and - under a consuming
 * one - the contracts that exercise produced. Everything else in those
 * transactions is dropped. So this is the transaction trees the tool already
 * renders, re-rooted on a contract rather than on a transaction.
 *
 * <h2>Why it is drawn as a tree rather than as a list</h2>
 *
 * The entries are siblings in time and each carries a block of its own, so a
 * blank line between them is the only thing separating a transaction id
 * belonging to one entry from the acting parties of the next. The connector
 * carries down the left of every line an entry owns, which makes the extent of
 * an entry visible rather than inferred, and the last one closes the run so
 * that "this is everything" is something the shape says.
 *
 * <h2>Two things it says about itself, and neither is decoration</h2>
 *
 * WHOSE VIEW. The heading names the user, because the read was made as that
 * user's parties and a different user would honestly see a different list. An
 * activity pane with no name on it invites the reading that it is the ledger's
 * account rather than one party's.
 *
 * WHETHER IT WAS TRUNCATED. A capped scan is marked. Without the mark a scan
 * that stopped early reads as "nothing else ever happened to this contract",
 * which is the one wrong answer this pane must not give. A window that opened
 * after the contract was created is marked for the same reason.
 *
 * No Swing in here on purpose: the fold is the part with the logic in it and it
 * is asserted without a display.
 *
 * Author Claude/bentzn
 */
public final class ContractActivity {

    /**
     * Transactions read in one scan before the cap bites.
     *
     * The cost of this feature is TRANSACTIONS IN THE WINDOW, not contracts on
     * the ledger, and the window for an active contract runs from its create to
     * the ledger end. On a busy participant that is the expensive case, so the
     * cap is what keeps a click from becoming a scan of the year.
     */
    public static final int CNT_SCAN_DEFAULT = 2000;

    private static final String STR_INDENT = "  ";

    /** An entry that has siblings after it, and the run's last one. */
    private static final String STR_FORK = "\u251c\u2500 ";

    private static final String STR_LAST = "\u2514\u2500 ";

    /** What continues down the left of an entry's own lines, and of the last. */
    private static final String STR_BAR = "\u2502  ";

    private static final String STR_GAP = "   ";

    private static final String STR_BAR_ONLY = "\u2502";

    /**
     * SECONDS, and no zone marker. The ledger reports microseconds, which is a
     * precision nobody reads off a screen and eleven characters of noise on
     * every entry heading; the exact value stays available in the transaction
     * view. UTC because that is what the participant reported and converting it
     * here would be this pane inventing a timezone.
     */
    private static final DateTimeFormatter FMT_SECOND =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);


    private ContractActivity() {
    }


    /**
     * @param contract the contract the pane is rooted on
     * @param scan what the update read returned
     * @param idUser the user the read was made as, may be null
     * @return the activity, ready for the result pane
     */
    public static String text(Contract contract, UpdateScan scan, String idUser) {
        List<String> lstEntry = new ArrayList<>();
        boolean flagCreateSeen = false;

        for (TxTree tree : lstTouching(scan.lstTree(), contract.idContract())) {
            for (TxNode node : tree.lstRoot()) {
                flagCreateSeen |= walk(lstEntry, node, contract.idContract(), tree);
            }
        }

        StringBuilder buf = new StringBuilder(strHead());
        if (lstEntry.isEmpty())
            return buf.append("Nothing in this window touched the contract.")
                    .append(strFoot(contract, scan, false)).toString();

        for (int cntLoop = 0; cntLoop < lstEntry.size(); cntLoop++) {
            entry(buf, lstEntry.get(cntLoop), cntLoop == lstEntry.size() - 1);
        }

        return buf.append(strFoot(contract, scan, flagCreateSeen)).toString();
    }


    /**
     * The same heading over a scan that could not be made.
     *
     * A REFUSAL IS AN ANSWER and gets the same frame as one. Reporting it as a
     * bare error string beside a contract, with no statement of who was asking,
     * is how an operator concludes the contract has no activity rather than
     * that the question was never put.
     *
     * @param contract the contract the pane is rooted on
     * @param idUser the user the read would have been made as, may be null
     * @param strProblem what came back
     * @return the failure, ready for the result pane
     */
    public static String textProblem(Contract contract, String idUser, String strProblem) {
        return strHead() + "The activity could not be read.\n\n" + strProblem + '\n';
    }


    /**
     * @return the heading every rendering here shares
     */
    private static String strHead() {
        // NO USER IN THE HEADING - operator instruction, 2026-09-09. The pane
        // is opened from a contract the reader selected as somebody, and
        // naming them again on every rendering is a parenthesis read past.
        return Banner.head("CONTRACT ACTIVITY");
    }


    /**
     * Draws one entry with its connector.
     *
     * The prefix is THREE characters on every line, so the two indent levels
     * underneath keep the columns they had before the connector existed - the
     * tree is drawn beside the block rather than inside it.
     *
     * @param buf where it goes
     * @param strEntry the entry, its own lines, no connector
     * @param flagLast whether it closes the run
     */
    private static void entry(StringBuilder buf, String strEntry, boolean flagLast) {
        String[] arrLine = strEntry.split("\n", -1);
        for (int cntLoop = 0; cntLoop < arrLine.length; cntLoop++) {
            if (cntLoop == arrLine.length - 1 && arrLine[cntLoop].isEmpty())
                continue;
            if (cntLoop == 0) {
                buf.append(flagLast ? STR_LAST : STR_FORK);
            }
            else {
                buf.append(flagLast ? STR_GAP : STR_BAR);
            }
            buf.append(arrLine[cntLoop]).append('\n');
        }

        if (!flagLast)
            buf.append(STR_BAR_ONLY).append('\n');
    }


    /**
     * @param contract the contract
     * @param scan what was read
     * @param flagCreateSeen whether the create fell inside the window
     * @return the notes that must not be left to inference
     */
    private static String strFoot(Contract contract, UpdateScan scan,
            boolean flagCreateSeen) {
        // The notes are ABOUT the run of entries above them, not part of it,
        // so they get a line of air rather than butting onto the last row.
        StringBuilder buf = new StringBuilder("\n");

        if (scan.flagCapped()) {
            buf.append("TRUNCATED - the read stopped at its cap of ").append(CNT_SCAN_DEFAULT)
                    .append(" transactions, before the end\nof the window. There is more"
                            + " activity after offset ").append(scan.offsetTo())
                    .append(" that is not listed here.\n");
        }

        if (!flagCreateSeen) {
            buf.append("The create is NOT in this window, so this is activity from offset ")
                    .append(scan.offsetFrom()).append("\nonwards and not the whole of the"
                            + " contract's life.\n");
        }

        // `The contract was active at offset N` IS DROPPED - operator
        // instruction, 2026-09-09. The block above already says `active`, and
        // the offset it named was where the scan stopped rather than anything
        // about the contract.

        return buf.toString();
    }


    /**
     * @param lstTree everything the scan returned
     * @param idContract the contract
     * @return the transactions with at least one node on this contract, in the
     *         order they were read, which is offset order
     */
    static List<TxTree> lstTouching(List<TxTree> lstTree, String idContract) {
        List<TxTree> lstOut = new ArrayList<>();
        for (TxTree tree : lstTree) {
            if (flagTouches(tree.lstRoot(), idContract))
                lstOut.add(tree);
        }
        return List.copyOf(lstOut);
    }


    private static boolean flagTouches(List<TxNode> lstNode, String idContract) {
        for (TxNode node : lstNode) {
            if (idOf(node).equals(idContract))
                return true;
            if (flagTouches(node.lstChild(), idContract))
                return true;
        }
        return false;
    }


    /**
     * Walks one node, making an entry for it when it is about this contract,
     * and carrying on into its children either way.
     *
     * CHILDREN ARE WALKED EVEN AFTER A HIT. A nonconsuming choice can exercise
     * the same contract again in its own consequences, and stopping at the
     * first match would drop the second entry without saying so.
     *
     * @param lstEntry where entries go, in ledger order
     * @param node the node
     * @param idContract the contract the pane is rooted on
     * @param tree the transaction the node came from
     * @return true when this subtree held the contract's create
     */
    private static boolean walk(List<String> lstEntry, TxNode node, String idContract,
            TxTree tree) {
        boolean flagCreate = false;

        switch (node) {
            case TxNode.Created val -> {
                if (val.idContract().equals(idContract)) {
                    lstEntry.add(strCreated(val, tree));
                    flagCreate = true;
                }
            }

            case TxNode.Exercised val -> {
                if (val.idContract().equals(idContract))
                    lstEntry.add(strExercised(val, tree));

                for (TxNode child : val.lstChild()) {
                    flagCreate |= walk(lstEntry, child, idContract, tree);
                }
            }
        }
        return flagCreate;
    }


    /**
     * A create has no actor, so its party row is SIGNATORIES. That is right for
     * display and would be exactly wrong as a visibility filter, which is the
     * participant's job and is done by reading as the user's parties.
     */
    private static String strCreated(TxNode.Created node, TxTree tree) {
        StringBuilder buf = new StringBuilder("Created ").append(strWhen(tree)).append('\n');
        parties(buf, "signatories", node.lstSignatory());
        if (!node.lstObserver().isEmpty())
            parties(buf, "observers", node.lstObserver());
        transaction(buf, tree);
        return strTrim(buf);
    }


    /**
     * Consuming is stated only when it is TRUE. The nonconsuming case is the
     * common one and a row saying so on every choice is noise that hides the
     * one line that matters.
     */
    private static String strExercised(TxNode.Exercised node, TxTree tree) {
        StringBuilder buf = new StringBuilder("Choice: ").append(node.nameChoice()).append(' ')
                .append(strWhen(tree)).append('\n');
        parties(buf, "acting parties", node.lstActor());
        if (node.flagConsuming())
            buf.append(STR_INDENT).append("Consuming\n");

        if (node.argument() != null) {
            buf.append(STR_INDENT).append("arguments\n");
            indent(buf, BlockRenderer.block(node.argument()), 2);
        }
        transaction(buf, tree);

        List<TxNode.Created> lstMade = lstConsequence(node.lstChild());
        if (!lstMade.isEmpty()) {
            buf.append(STR_INDENT).append("Created as consequence\n");
            for (TxNode.Created made : lstMade) {
                buf.append(STR_INDENT).append(STR_INDENT)
                        .append(made.idTemplate().shortName()).append("   ")
                        .append(made.idContract()).append('\n');
            }
        }

        if (node.flagConsuming())
            buf.append(STR_INDENT).append("Contract archived\n");

        return strTrim(buf);
    }


    /**
     * Every contract the choice produced, at any depth.
     *
     * THE WHOLE SUBTREE, not the immediate children. A choice that exercises
     * another contract which creates the interesting one still produced it, and
     * listing only the top level would show an empty consequence list under a
     * choice that plainly made something.
     *
     * @param lstNode the exercise's consequences
     * @return the creates among them, in ledger order
     */
    static List<TxNode.Created> lstConsequence(List<TxNode> lstNode) {
        List<TxNode.Created> lstOut = new ArrayList<>();
        for (TxNode node : lstNode) {
            if (node instanceof TxNode.Created val)
                lstOut.add(val);
            lstOut.addAll(lstConsequence(node.lstChild()));
        }
        return List.copyOf(lstOut);
    }


    /** @return when the transaction took effect, to the second */
    private static String strWhen(TxTree tree) {
        return tree.instEffective() == null ? "" : FMT_SECOND.format(tree.instEffective());
    }


    /**
     * NO TIMESTAMP HERE. It moved to the entry's own heading, where it dates
     * the thing that happened rather than sitting at the bottom of the block
     * dating the transaction that carried it.
     */
    private static void transaction(StringBuilder buf, TxTree tree) {
        buf.append(STR_INDENT).append("transaction ").append(tree.idUpdate())
                .append("   offset ").append(tree.offset()).append('\n');
    }


    /**
     * One party per line. Two party ids joined by a comma is 260 characters and
     * a pane that scrolls sideways is unreadable, which is the same reason the
     * transaction view splits them.
     */
    private static void parties(StringBuilder buf, String strLabel, List<String> lstParty) {
        buf.append(STR_INDENT).append(strLabel).append('\n');
        if (lstParty.isEmpty()) {
            buf.append(STR_INDENT).append(STR_INDENT).append("(none)\n");
            return;
        }
        for (String idParty : lstParty) {
            buf.append(STR_INDENT).append(STR_INDENT).append(idParty).append('\n');
        }
    }


    /**
     * @param buf where it goes
     * @param strBlock an already rendered multi-line block
     * @param cntLevel how many indents to add to every line of it
     */
    private static void indent(StringBuilder buf, String strBlock, int cntLevel) {
        if (strBlock == null || strBlock.isBlank()) {
            buf.append(STR_INDENT.repeat(cntLevel)).append("(none)\n");
            return;
        }
        for (String strLine : strBlock.split("\n", -1)) {
            if (strLine.isBlank())
                continue;
            buf.append(STR_INDENT.repeat(cntLevel)).append(strLine).append('\n');
        }
    }


    /**
     * @param buf an entry
     * @return it without its trailing newline, so the connector decides where
     *         the entry ends rather than the block that built it
     */
    private static String strTrim(StringBuilder buf) {
        int cntEnd = buf.length();
        while (cntEnd > 0 && buf.charAt(cntEnd - 1) == '\n') {
            cntEnd--;
        }
        return buf.substring(0, cntEnd);
    }


    /**
     * @param node any node
     * @return the contract it is about; a create names the one it made and an
     *         exercise names the one it was made on
     */
    private static String idOf(TxNode node) {
        return switch (node) {
            case TxNode.Created val -> val.idContract();
            case TxNode.Exercised val -> val.idContract();
        };
    }

}
