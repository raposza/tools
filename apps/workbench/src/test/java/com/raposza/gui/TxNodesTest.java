// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;
import com.raposza.api.model.TxNode;
import com.raposza.api.model.TxTree;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * The shape of a rendered transaction, asserted without a display.
 *
 * Author Claude/bentzn
 */
class TxNodesTest {

    private static final DataId ID_ACCOUNT = new DataId("pkg", "Main", "Account");
    private static final String ID_CREATED = "00aa11bb22cc33dd44ee55ff";


    private static DamlValue.Rec.Field fld(String nameField, DamlValue value) {
        return new DamlValue.Rec.Field(nameField, value);
    }


    private static DamlValue.Rec payload() {
        DamlValue.Rec address = new DamlValue.Rec(new DataId("pkg", "Main", "Address"),
                List.of(fld("street", new DamlValue.Text("Rua Augusta 1")),
                        fld("city", new DamlValue.Text("Lisboa"))));

        return new DamlValue.Rec(ID_ACCOUNT,
                List.of(fld("owner", new DamlValue.Party("alice::1")),
                        fld("address", address),
                        fld("tags", new DamlValue.Lst(List.of(new DamlValue.Text("eur"),
                                new DamlValue.Text("retail"))))));
    }


    private static TxNode.Created created(String idContract) {
        return new TxNode.Created("ev-1", idContract, ID_ACCOUNT, payload(), List.of("bank::1"),
                List.of("alice::1"));
    }


    private static TxTree tree(List<TxNode> lstRoot) {
        return new TxTree("tx-1", Optional.of("cmd-1"), Optional.empty(), "0000007",
                Instant.EPOCH, lstRoot);
    }


    private static TxItem item(DefaultMutableTreeNode node) {
        return (TxItem) node.getUserObject();
    }


    private static DefaultMutableTreeNode child(DefaultMutableTreeNode node, int idx) {
        return (DefaultMutableTreeNode) node.getChildAt(idx);
    }


    @Test
    void theRootIsTheTransactionAndItsChildrenAreTheRootEvents() {
        DefaultMutableTreeNode root = TxNodes.build(tree(List.of(created(ID_CREATED))), null);

        assertTrue(item(root) instanceof TxItem.Tx);
        assertTrue(item(root).label().contains("tx-1"));
        assertTrue(item(root).full().contains("cmd-1"));
        assertEquals(1, root.getChildCount());
        assertTrue(item(child(root, 0)) instanceof TxItem.Create);
    }


    @Test
    void aCreateOpensItsPayloadOneLevelPerLevelTheDataHas() {
        DefaultMutableTreeNode root = TxNodes.build(tree(List.of(created(ID_CREATED))), null);
        DefaultMutableTreeNode nodeCreate = child(root, 0);

        assertEquals(3, nodeCreate.getChildCount());
        assertTrue(item(child(nodeCreate, 0)).label().startsWith("owner = "));

        // A scalar is a leaf; a record and a list open.
        assertEquals(0, child(nodeCreate, 0).getChildCount());
        assertEquals(2, child(nodeCreate, 1).getChildCount());
        assertEquals(2, child(nodeCreate, 2).getChildCount());
        assertEquals("[0] = \"eur\"", item(child(child(nodeCreate, 2), 0)).label());
    }


    @Test
    void anUnlabelledFieldShowsItsPosition() {
        DamlValue.Rec rec = new DamlValue.Rec(null, List.of(fld(null, new DamlValue.Int64(7L))));
        TxNode.Created node = new TxNode.Created("ev-1", ID_CREATED, ID_ACCOUNT, rec, List.of(),
                List.of());

        DefaultMutableTreeNode root = TxNodes.build(tree(List.of(node)), null);

        assertEquals("[0] = 7", item(child(child(root, 0), 0)).label());
    }


    /**
     * Pasting a contract id and getting a tree with no indication of WHICH node
     * answered the question is the failure this view exists to prevent.
     */
    @Test
    void theSearchedContractIsMarkedAndNothingElseIs() {
        TxNode.Exercised root = new TxNode.Exercised("ev-0", "00parent", ID_ACCOUNT, "Deposit",
                true, new DamlValue.Rec(null, List.of()), new DamlValue.Unit(),
                List.of("alice::1"), List.of(created(ID_CREATED)));

        DefaultMutableTreeNode nodeRoot = TxNodes.build(tree(List.of(root)), "  " + ID_CREATED
                + "  ");

        DefaultMutableTreeNode found = TxTreePanel.findFound(nodeRoot);
        assertNotNull(found);
        assertTrue(item(found) instanceof TxItem.Create);
        assertTrue(item(found).flagFound());
        assertFalse(item(child(nodeRoot, 0)).flagFound());
    }


    @Test
    void nothingIsMarkedWithoutASearchTerm() {
        DefaultMutableTreeNode root = TxNodes.build(tree(List.of(created(ID_CREATED))), "");

        assertNull(TxTreePanel.findFound(root));
        assertFalse(item(child(root, 0)).flagFound());
    }


    @Test
    void anExerciseCarriesArgumentResultAndItsConsequences() {
        TxNode.Exercised node = new TxNode.Exercised("ev-0", "00parent", ID_ACCOUNT, "Deposit",
                true, new DamlValue.Rec(null, List.of(fld("amount", new DamlValue.Int64(5L)))),
                new DamlValue.ContractRef(ID_CREATED), List.of("alice::1"),
                List.of(created(ID_CREATED)));

        DefaultMutableTreeNode nodeEx = child(TxNodes.build(tree(List.of(node)), null), 0);

        assertEquals(3, nodeEx.getChildCount());
        assertTrue(item(child(nodeEx, 0)).label().startsWith("argument"));
        assertTrue(item(child(nodeEx, 1)).label().startsWith("result"));
        assertTrue(item(child(nodeEx, 2)) instanceof TxItem.Create);
        assertEquals(1, child(nodeEx, 0).getChildCount());
    }


    /**
     * Whether the choice archived the contract is usually the question the
     * operator opened the tree to answer, so it is in the row and not only in
     * the detail.
     */
    @Test
    void aConsumingChoiceIsVisibleInTheRow() {
        TxNode.Exercised consuming = new TxNode.Exercised("ev-0", "00parent", ID_ACCOUNT,
                "Archive", true, null, null, List.of("bank::1"), List.of());
        TxNode.Exercised nonConsuming = new TxNode.Exercised("ev-1", "00parent", ID_ACCOUNT,
                "Peek", false, null, null, List.of("bank::1"), List.of());

        DefaultMutableTreeNode root = TxNodes.build(tree(List.of(consuming, nonConsuming)), null);

        assertTrue(item(child(root, 0)).label().startsWith("exercised* "));
        assertTrue(item(child(root, 1)).label().startsWith("exercised  "));
        assertTrue(item(child(root, 0)).full().contains("consuming"));

        // A null argument and result add no rows rather than two empty ones.
        assertEquals(0, child(root, 0).getChildCount());
    }

}
