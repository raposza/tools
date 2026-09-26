// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.LedgerException;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * What the navigator is allowed to claim about a participant.
 *
 * Headless: everything asserted here is decided before a component exists.
 *
 * Author Claude/bentzn
 */
class LedgerSnapshotTest {

    @Test
    void readsAllFourSectionsInOnePass() {
        FakeNavClient client = new FakeNavClient().withParty("alice::1", "Alice")
                .withUser("participant_admin", true)
                .withContract("00aa", "pkg1", "Account", "owner");

        LedgerSnapshot snap = LedgerSnapshot.load(client, List.of("alice::1"), 500);

        assertEquals(1, snap.lstParty().size());
        assertEquals(1, snap.lstUser().size());
        assertEquals(1, snap.lstTemplate().size());
        assertEquals(1, snap.cntContract());
        assertNull(snap.strProblemUser());
        assertNull(snap.strProblemContract());
        assertFalse(snap.flagCapped());
    }


    @Test
    void theCallerIsCapAndReadAsPartiesPassThroughUntouched() {
        FakeNavClient client = new FakeNavClient().withParty("alice::1", "Alice");

        LedgerSnapshot.load(client, List.of("alice::1", "bank::1"), 42);

        assertEquals(42, client.queryLast.cntLimit());
        assertEquals(List.of("alice::1", "bank::1"), client.queryLast.lstPartyRead());
    }


    /**
     * The whole reason the sections are read independently: the tool connects
     * unauthenticated, so users() is the call expected to be refused on a real
     * participant while everything else answers.
     */
    @Test
    void aRefusedUserListDoesNotEmptyTheRest() {
        FakeNavClient client = new FakeNavClient().withParty("alice::1", "Alice")
                .withContract("00aa", "pkg1", "Account", "owner")
                .failingUsers(FakeNavClient.refused("requires an admin token"));

        LedgerSnapshot snap = LedgerSnapshot.load(client, List.of("alice::1"), 500);

        assertTrue(snap.lstUser().isEmpty());
        assertNotNull(snap.strProblemUser());
        assertTrue(snap.strProblemUser().contains("admin"));
        assertEquals(1, snap.lstParty().size());
        assertEquals(1, snap.cntContract());
    }


    @Test
    void aRefusedContractReadIsCarriedNotThrown() {
        FakeNavClient client = new FakeNavClient().withParty("alice::1", "Alice")
                .failingContracts(FakeNavClient.refused("no read rights"));

        LedgerSnapshot snap = LedgerSnapshot.load(client, List.of("alice::1"), 500);

        assertTrue(snap.lstContract().isEmpty());
        assertTrue(snap.lstTemplate().isEmpty());
        assertNotNull(snap.strProblemContract());
    }


    /**
     * parties() is an ADMIN call and a read token is refused it. Failing the
     * snapshot there threw away the contracts that same token COULD read, which
     * is the opposite of what the operator needs: they are holding a credential
     * that works and being shown nothing.
     *
     * Reversal of the earlier rule, which required parties() to propagate.
     */
    @Test
    void aFailedPartyListIsReportedRatherThanPropagated() {
        FakeNavClient client = new FakeNavClient()
                .failingParties(new LedgerException("PERMISSION_DENIED"))
                .withContract("00aa", "pkg1", "Account", "owner");

        LedgerSnapshot snap = LedgerSnapshot.load(client, List.of("alice::1"), 500);

        assertTrue(snap.lstParty().isEmpty());
        assertNotNull(snap.strProblemParty());
        assertTrue(snap.strProblemParty().contains("PERMISSION_DENIED"));
        assertEquals(1, snap.lstContract().size());
        assertNull(snap.strProblemContract());
    }


    /** A participant that answers leaves no problem behind. */
    @Test
    void aWorkingPartyListCarriesNoProblem() {
        FakeNavClient client = new FakeNavClient().withParty("alice::1", "Alice");

        LedgerSnapshot snap = LedgerSnapshot.load(client, List.of("alice::1"), 500);

        assertEquals(1, snap.lstParty().size());
        assertNull(snap.strProblemParty());
    }


    @Test
    void noReadAsPartiesSkipsContractsWithAReasonAndStillListsParties() {
        FakeNavClient client = new FakeNavClient().withParty("alice::1", "Alice")
                .withUser("alice", false);

        LedgerSnapshot snap = LedgerSnapshot.load(client, List.of(), 500);

        assertEquals(0, client.cntCallContracts);
        assertEquals(1, snap.lstParty().size());
        assertEquals(1, snap.lstUser().size());
        assertNotNull(snap.strProblemContract());
    }


    @Test
    void hittingTheCapIsReported() {
        FakeNavClient client = new FakeNavClient().withParty("alice::1", "Alice")
                .withContract("00aa", "pkg1", "Account", "owner")
                .withContract("00bb", "pkg1", "Account", "owner")
                .withContract("00cc", "pkg1", "Account", "owner");

        LedgerSnapshot snap = LedgerSnapshot.load(client, List.of("alice::1"), 2);

        assertEquals(2, snap.cntContract());
        assertTrue(snap.flagCapped());
        assertEquals(2, snap.cntLimit());
    }


    /**
     * Their sandbox holds Accounts under two package ids after any DAR rebuild,
     * so two groups carrying the same module and entity name is the normal case
     * rather than an exotic one.
     */
    @Test
    void twoPackagesWithTheSameEntityNameGetQualifiedLabels() {
        FakeNavClient client = new FakeNavClient().withParty("alice::1", "Alice")
                .withContract("00aa", "0123456789abcdef0123", "Account", "owner")
                .withContract("00bb", "fedcba98765432100000", "Account", "owner")
                .withContract("00cc", "0123456789abcdef0123", "Other", "x");

        LedgerSnapshot snap = LedgerSnapshot.load(client, List.of("alice::1"), 500);

        assertEquals(3, snap.lstTemplate().size());

        int cntQualified = 0;
        for (LedgerSnapshot.TemplateGroup group : snap.lstTemplate()) {
            if (group.strLabel().startsWith("Main:Account")) {
                assertTrue(group.strLabel().contains("["), "expected a package qualifier: "
                        + group.strLabel());
                cntQualified++;
            }
            if (group.strLabel().startsWith("Main:Other"))
                assertEquals("Main:Other", group.strLabel());
        }
        assertEquals(2, cntQualified);
    }


    @Test
    void contractsAreGroupedUnderTheirOwnTemplateAndKeptInLedgerOrder() {
        FakeNavClient client = new FakeNavClient().withParty("alice::1", "Alice")
                .withContract("00aa", "pkg1", "Account", "owner")
                .withContract("00bb", "pkg1", "Other", "x")
                .withContract("00cc", "pkg1", "Account", "owner");

        LedgerSnapshot snap = LedgerSnapshot.load(client, List.of("alice::1"), 500);

        assertEquals(2, snap.lstTemplate().size());
        LedgerSnapshot.TemplateGroup groupAccount = snap.lstTemplate().get(0);
        assertEquals("Main:Account", groupAccount.strLabel());
        assertEquals(2, groupAccount.lstContract().size());
        assertEquals("00aa", groupAccount.lstContract().get(0).idContract());
        assertEquals("00cc", groupAccount.lstContract().get(1).idContract());

        assertSame(snap.lstContract().get(0), groupAccount.lstContract().get(0));
    }


    @Test
    void partiesAreSortedByWhatTheTreeShows() {
        FakeNavClient client = new FakeNavClient().withParty("z::1", "Alice")
                .withParty("a::1", "zoe").withParty("m::1", "");

        LedgerSnapshot snap = LedgerSnapshot.load(client, List.of(), 500);

        assertEquals("Alice", snap.lstParty().get(0).label());
        assertEquals("m::1", snap.lstParty().get(1).label());
        assertEquals("zoe", snap.lstParty().get(2).label());
    }

}
