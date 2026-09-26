// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.model.DamlValue;
import com.raposza.api.model.TxNode;
import com.raposza.api.model.TxTree;
import com.raposza.render.BlockRenderer;
import com.raposza.render.LineRenderer;

import java.util.List;

/**
 * What a node in the transaction tree stands for.
 *
 * Sealed, for the same reason NavItem is: a node kind nobody wrote a label for
 * is a compile error rather than a blank row.
 *
 * label() is one line and lossy - LineRenderer. full() is the detail pane and
 * goes down the page - BlockRenderer. A payload rendered as one line into a
 * pane that scrolls sideways is the failure that split these two apart.
 *
 * Author Claude/bentzn
 */
public sealed interface TxItem {

    /** How much of a value a row carries before it is cut. */
    int CNT_ROW = LineRenderer.CNT_LINE_DEFAULT;


    /** @return what the tree shows */
    String label();


    /** @return the full text, for the tooltip and the detail pane */
    String full();


    /** @return true when this row is the thing the operator searched for */
    boolean flagFound();


    /**
     * One party per line. Two party ids joined by a comma is 260 characters,
     * and a detail pane that scrolls sideways is what BlockRenderer exists to
     * stop; doing it for payloads and not for signatories would be half a fix.
     *
     * @param buf where to write
     * @param strLabel the heading
     * @param lstValue the values, possibly empty
     */
    static void lines(StringBuilder buf, String strLabel, List<String> lstValue) {
        buf.append(strLabel).append('\n');
        if (lstValue.isEmpty()) {
            buf.append("  (none)\n");
            return;
        }
        for (String strValue : lstValue) {
            buf.append("  ").append(strValue).append('\n');
        }
    }


    /**
     * The transaction itself.
     *
     * @param tree the transaction
     */
    record Tx(TxTree tree) implements TxItem {

        @Override
        public String label() {
            return "transaction " + tree.idUpdate() + "   offset " + tree.offset() + "   "
                    + tree.instEffective();
        }


        @Override
        public String full() {
            StringBuilder buf = new StringBuilder();
            buf.append("update id   ").append(tree.idUpdate()).append('\n');
            buf.append("command id  ").append(tree.idCommand().orElse("(none)")).append('\n');
            buf.append("workflow id ").append(tree.idWorkflow().orElse("(none)")).append('\n');
            buf.append("offset      ").append(tree.offset()).append('\n');
            buf.append("effective   ").append(tree.instEffective()).append('\n');
            buf.append("roots       ").append(tree.lstRoot().size());
            return buf.toString();
        }


        @Override
        public boolean flagFound() {
            return false;
        }

    }


    /**
     * @param node the create
     * @param flagFound true when this contract is the searched one
     */
    record Create(TxNode.Created node, boolean flagFound) implements TxItem {

        @Override
        public String label() {
            return "created  " + node.idTemplate().shortName() + "   "
                    + ShortIds.id(node.idContract());
        }


        @Override
        public String full() {
            StringBuilder buf = new StringBuilder();
            buf.append("CREATED\n\n");
            buf.append("event id    ").append(node.idEvent()).append('\n');
            buf.append("contract id ").append(node.idContract()).append('\n');
            buf.append("template    ").append(node.idTemplate()).append('\n');
            lines(buf, "signatories", node.lstSignatory());
            lines(buf, "observers", node.lstObserver());
            buf.append("\npayload\n").append(BlockRenderer.block(node.payload()));
            return buf.toString();
        }

    }


    /**
     * @param node the exercise
     * @param flagFound true when the contract exercised upon is the searched one
     */
    record Exercise(TxNode.Exercised node, boolean flagFound) implements TxItem {

        /**
         * Consuming is marked in the row and not only in the detail, because
         * whether a choice archived the contract is usually the question the
         * operator opened the tree to answer.
         */
        @Override
        public String label() {
            return (node.flagConsuming() ? "exercised* " : "exercised  ")
                    + node.idTemplate().nameEntity() + ":" + node.nameChoice() + "   "
                    + ShortIds.id(node.idContract());
        }


        @Override
        public String full() {
            StringBuilder buf = new StringBuilder();
            buf.append("EXERCISED").append(node.flagConsuming() ? " (consuming)" : "")
                    .append("\n\n");
            buf.append("event id    ").append(node.idEvent()).append('\n');
            buf.append("contract id ").append(node.idContract()).append('\n');
            buf.append("template    ").append(node.idTemplate()).append('\n');
            buf.append("choice      ").append(node.nameChoice()).append('\n');
            lines(buf, "actors", node.lstActor());
            buf.append("children    ").append(node.lstChild().size()).append('\n');
            buf.append("\nargument\n").append(BlockRenderer.block(node.argument()));
            buf.append("\n\nresult\n").append(BlockRenderer.block(node.valueResult()));
            return buf.toString();
        }

    }


    /**
     * One field of a payload, an argument or a result. Fields are nodes so a
     * wide payload is browsable rather than elided into uselessness.
     *
     * @param nameField the field name, or a positional index when the ledger
     *        returned no labels
     * @param value the value
     */
    record Field(String nameField, DamlValue value) implements TxItem {

        @Override
        public String label() {
            return nameField + " = " + LineRenderer.line(value, CNT_ROW);
        }


        @Override
        public String full() {
            return nameField + "\n\ntype  " + LineRenderer.kind(value) + "\n\n"
                    + BlockRenderer.block(value);
        }


        @Override
        public boolean flagFound() {
            return false;
        }

    }


    /**
     * A heading under an event, so an argument and a result do not appear as
     * two anonymous lists of fields.
     *
     * @param strLabel what it is
     * @param value the whole value it heads
     */
    record Part(String strLabel, DamlValue value) implements TxItem {

        @Override
        public String label() {
            return strLabel + "   " + LineRenderer.line(value, CNT_ROW);
        }


        @Override
        public String full() {
            return strLabel + "\n\n" + BlockRenderer.block(value);
        }


        @Override
        public boolean flagFound() {
            return false;
        }

    }

}
