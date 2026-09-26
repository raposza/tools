// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.model.DamlValue;
import com.raposza.api.model.TxNode;
import com.raposza.api.model.TxTree;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Turns a transaction into tree nodes.
 *
 * Static and free of components, so the shape of a rendered transaction can be
 * asserted without a display - the interesting part is which rows exist and
 * which one is marked, not how they are painted.
 *
 * Values become nodes rather than one elided line. A record, a list and a Some
 * each open one level per level they actually have, so a wide payload is
 * browsable instead of ending in an ellipsis at the point it got interesting.
 * Scalars are leaves. Nothing here invents depth the data does not have.
 *
 * Author Claude/bentzn
 */
public final class TxNodes {

    private TxNodes() {
    }


    /**
     * @param tree the transaction
     * @param strRefFound the identifier the operator searched for, so the row
     *        that answers it can be marked; null or blank marks nothing
     * @return the root, holding a TxItem.Tx and one child per root event
     */
    public static DefaultMutableTreeNode build(TxTree tree, String strRefFound) {
        String strFound = strRefFound == null ? "" : strRefFound.trim();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new TxItem.Tx(tree));

        for (TxNode node : tree.lstRoot()) {
            root.add(event(node, strFound));
        }
        return root;
    }


    private static DefaultMutableTreeNode event(TxNode node, String strFound) {
        return switch (node) {
            case TxNode.Created val -> {
                DefaultMutableTreeNode out = new DefaultMutableTreeNode(
                        new TxItem.Create(val, val.idContract().equals(strFound)));
                addFields(out, val.payload());
                yield out;
            }

            case TxNode.Exercised val -> {
                DefaultMutableTreeNode out = new DefaultMutableTreeNode(
                        new TxItem.Exercise(val, val.idContract().equals(strFound)));

                if (val.argument() != null)
                    addPart(out, "argument", val.argument());
                if (val.valueResult() != null)
                    addPart(out, "result", val.valueResult());

                for (TxNode child : val.lstChild()) {
                    out.add(event(child, strFound));
                }
                yield out;
            }
        };
    }


    /**
     * A heading, then the value under it. A record heading carries its fields
     * directly; anything else is one row that can still be opened.
     */
    private static void addPart(DefaultMutableTreeNode parent, String strLabel, DamlValue value) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new TxItem.Part(strLabel, value));
        addChildren(node, value);
        parent.add(node);
    }


    /**
     * @param parent the event node
     * @param payload the create argument, may be null on a payload the ledger
     *        did not return
     */
    static void addFields(DefaultMutableTreeNode parent, DamlValue.Rec payload) {
        if (payload == null)
            return;
        addChildren(parent, payload);
    }


    /**
     * Opens ONE level. Each child that has structure of its own opens the same
     * way when it is reached, so depth follows the data.
     */
    private static void addChildren(DefaultMutableTreeNode parent, DamlValue value) {
        if (value == null)
            return;

        switch (value) {
            case DamlValue.Rec val -> {
                for (int idx = 0; idx < val.lstField().size(); idx++) {
                    DamlValue.Rec.Field fld = val.lstField().get(idx);
                    // A read without labels returns positional fields. Showing
                    // the position is honest; inventing a name is not.
                    String nameField = fld.nameField() == null || fld.nameField().isBlank()
                            ? "[" + idx + "]"
                            : fld.nameField();
                    addField(parent, nameField, fld.value());
                }
            }

            case DamlValue.Lst val -> {
                for (int idx = 0; idx < val.lstElem().size(); idx++) {
                    addField(parent, "[" + idx + "]", val.lstElem().get(idx));
                }
            }

            case DamlValue.Opt val -> {
                if (val.value() != null)
                    addField(parent, "Some", val.value());
            }

            case DamlValue.TextMap val -> {
                for (DamlValue.TextMap.Entry entry : val.lstEntry()) {
                    addField(parent, entry.strKey(), entry.value());
                }
            }

            case DamlValue.GenMap val -> {
                for (int idx = 0; idx < val.lstEntry().size(); idx++) {
                    addField(parent, "[" + idx + "]", val.lstEntry().get(idx).value());
                }
            }

            default -> {
                // A scalar is a leaf. Nothing to open.
            }
        }
    }


    private static void addField(DefaultMutableTreeNode parent, String nameField,
            DamlValue value) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(
                new TxItem.Field(nameField, value));
        addChildren(node, value);
        parent.add(node);
    }

}
