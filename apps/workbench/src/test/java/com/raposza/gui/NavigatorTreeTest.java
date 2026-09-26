// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * The shape of the tree, asserted without a display.
 *
 * buildRoot is static and touches no component for exactly this reason: a
 * navigator whose only tests need a window is a navigator that goes untested on
 * a build machine, and the interesting part is which nodes exist rather than
 * how they are painted.
 *
 * Author Claude/bentzn
 */
class NavigatorTreeTest {

    /**
     * Long enough that the tree label truncates, and with the distinguishing
     * part at the END, which is where a real contract id keeps it too.
     */
    private static final String ID_HEAD = "0011223344556677889900112233445566778899";

    private static final String ID_A = ID_HEAD + "aa01";
    private static final String ID_B = ID_HEAD + "bb02";
    private static final String ID_C = ID_HEAD + "cc03";


    private static LedgerSnapshot snapshot() {
        FakeNavClient client = new FakeNavClient().withParty("alice::abc", "Alice")
                .withParty("bank::abc", "Bank").withUser("participant_admin", true)
                .withContract(ID_A, "pkg1", "Account", "owner")
                .withContract(ID_B, "pkg1", "Account", "owner")
                .withContract(ID_C, "pkg1", "Iou", "issuer");
        return LedgerSnapshot.load(client, List.of("alice::abc"), 500);
    }


    private static DefaultMutableTreeNode section(DefaultMutableTreeNode root, int idx) {
        return (DefaultMutableTreeNode) root.getChildAt(idx);
    }


    private static String tip(DefaultMutableTreeNode node) {
        return ((NavItem) node.getUserObject()).tip();
    }


    private static String label(DefaultMutableTreeNode node) {
        return ((NavItem) node.getUserObject()).label();
    }


    @Test
    void fourSectionsInAFixedOrder() {
        DefaultMutableTreeNode root = NavigatorPanel.buildRoot(snapshot(), "");

        assertEquals(4, root.getChildCount());
        assertTrue(label(section(root, 0)).startsWith("Users (1)"));
        assertTrue(label(section(root, 1)).startsWith("Parties (2)"));
        assertTrue(label(section(root, 2)).startsWith("Templates seen (2)"));
        assertTrue(label(section(root, 3)).startsWith("Contracts (3)"));
    }


    /**
     * An absent section and an empty one look identical in a screenshot and
     * mean entirely different things, so the sections are always there.
     */
    @Test
    void anEmptyLedgerStillHasAllFourSections() {
        LedgerSnapshot snap = LedgerSnapshot.load(new FakeNavClient(), List.of("alice::abc"), 500);
        DefaultMutableTreeNode root = NavigatorPanel.buildRoot(snap, "");

        assertEquals(4, root.getChildCount());
        for (int idx = 0; idx < 4; idx++) {
            assertEquals(0, section(root, idx).getChildCount());
        }
    }


    /**
     * A TEMPLATE IS A LEAF. Its contracts used to hang under it AND appear in
     * the flat section, so every contract was on screen twice.
     */
    @Test
    void templatesAreLeavesAndContractsAppearFlat() {
        DefaultMutableTreeNode root = NavigatorPanel.buildRoot(snapshot(), "");

        DefaultMutableTreeNode nodeTemplate = section(root, 2);
        assertEquals(2, nodeTemplate.getChildCount());

        for (int idx = 0; idx < nodeTemplate.getChildCount(); idx++) {
            assertEquals(0, ((DefaultMutableTreeNode) nodeTemplate.getChildAt(idx))
                    .getChildCount());
        }
        assertEquals(3, section(root, 3).getChildCount());
    }


    @Test
    void contractsAreSortedByTemplateThenById() {
        // THE PARTICIPANT HANDS THEM BACK IN ITS OWN ORDER, and the tree is
        // read by somebody looking for one row among twenty-seven.
        FakeNavClient client = new FakeNavClient().withParty("alice::abc", "Alice")
                .withContract(ID_C, "pkg1", "Iou", "issuer")
                .withContract(ID_B, "pkg1", "Account", "owner")
                .withContract(ID_A, "pkg1", "Account", "owner");
        DefaultMutableTreeNode root = NavigatorPanel.buildRoot(
                LedgerSnapshot.load(client, List.of("alice::abc"), 500), "");

        DefaultMutableTreeNode nodeContract = section(root, 3);
        assertEquals(3, nodeContract.getChildCount());
        assertEquals(ID_A, tip((DefaultMutableTreeNode) nodeContract.getChildAt(0)));
        assertEquals(ID_B, tip((DefaultMutableTreeNode) nodeContract.getChildAt(1)));
        assertEquals(ID_C, tip((DefaultMutableTreeNode) nodeContract.getChildAt(2)));
    }


    @Test
    void theFilterMatchesAContractIdEvenThoughTheLabelIsShortened() {
        assertTrue(new NavItem.Ct(snapshot().lstContract().get(2)).label()
                .contains(ShortIds.STR_CUT));

        DefaultMutableTreeNode root = NavigatorPanel.buildRoot(snapshot(), "cc03");

        assertEquals(0, section(root, 1).getChildCount());
        assertEquals(1, section(root, 3).getChildCount());
        // The template row carries neither the id nor the contract, so it is
        // not a match. The contract is, on its tooltip.
        assertEquals(0, section(root, 2).getChildCount());
    }


    @Test
    void matchingATemplateNameKeepsItsContractsInTheFlatSection() {
        DefaultMutableTreeNode root = NavigatorPanel.buildRoot(snapshot(), "account");

        assertEquals(1, section(root, 2).getChildCount());
        assertEquals(2, section(root, 3).getChildCount());
    }


    @Test
    void theFilterIsCaseInsensitiveAndMatchesPartyIds() {
        DefaultMutableTreeNode root = NavigatorPanel.buildRoot(snapshot(), "ALICE");
        assertEquals(1, section(root, 1).getChildCount());
    }


    /**
     * A truncated listing that does not say it is truncated is a wrong answer,
     * so the cap is reported on every section that counts contracts.
     */
    @Test
    void theCapIsShownOnBothContractBearingSections() {
        FakeNavClient client = new FakeNavClient().withParty("alice::abc", "Alice")
                .withContract(ID_A, "pkg1", "Account", "owner")
                .withContract(ID_B, "pkg1", "Account", "owner");
        LedgerSnapshot snap = LedgerSnapshot.load(client, List.of("alice::abc"), 1);

        DefaultMutableTreeNode root = NavigatorPanel.buildRoot(snap, "");

        assertTrue(label(section(root, 2)).contains("capped at 1"));
        assertTrue(label(section(root, 3)).contains("capped at 1"));
    }


    /**
     * A REFUSED SECTION COUNTS (-), NOT (0). Zero is an answer about the
     * ledger; a refusal is the absence of one. The reason is on the tooltip
     * rather than the heading: it is a gRPC status line and would push the tree
     * three screens wide.
     */
    @Test
    void aRefusedUserListReadsAsADashAndKeepsItsReasonOnTheTooltip() {
        FakeNavClient client = new FakeNavClient().withParty("alice::abc", "Alice")
                .failingUsers(FakeNavClient.refused("requires an admin token"));
        LedgerSnapshot snap = LedgerSnapshot.load(client, List.of("alice::abc"), 500);

        DefaultMutableTreeNode root = NavigatorPanel.buildRoot(snap, "");

        assertEquals("Users (-)", label(section(root, 0)));
        assertTrue(tip(section(root, 0)).contains("admin token"));
        assertEquals("Parties (1)", label(section(root, 1)));

        // The refusal belongs to the Users section and nowhere else.
        assertNull(NavigatorPanel.noteContract(snap));
    }

}
