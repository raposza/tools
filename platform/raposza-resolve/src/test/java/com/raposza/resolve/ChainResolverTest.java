// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.LedgerException;
import com.raposza.api.RefProbe_i;
import com.raposza.api.model.Resolved;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

/**
 * The chain rule, exercised where every input is controlled.
 *
 * These tests are the specification of (c): confirmed ends the chain, syntactic
 * does not, several guesses become Ambiguous, and a probe that throws is not a
 * probe that found nothing.
 *
 * Author Claude/bentzn
 */
class ChainResolverTest {

    private static final String ID_CONTRACT =
            "0015157225b0e69e5e77c0df0c95901e1b74b7912f9013edc4bcb00fb3efbeb034";
    private static final String ID_PARTY = "Alice-d4d9::122093c6658bca786e58d86ff003578126274";
    private static final List<String> LST_PARTY = List.of(ID_PARTY);


    /** A probe that always finds the given thing, on the given evidence. */
    private static RefProbe_i probe(int numOrder, boolean flagConfirmed, Resolved resolved) {
        return new RefProbe_i() {

            @Override
            public boolean accepts(String strRef) {
                return true;
            }


            @Override
            public Optional<Finding> probe(String strRef, List<String> lstPartyRead) {
                return Optional.of(new Finding(resolved, flagConfirmed));
            }


            @Override
            public int order() {
                return numOrder;
            }

        };
    }


    private static RefProbe_i probeThrowing(int numOrder, LedgerException ex) {
        return new RefProbe_i() {

            @Override
            public boolean accepts(String strRef) {
                return true;
            }


            @Override
            public Optional<Finding> probe(String strRef, List<String> lstPartyRead) {
                throw ex;
            }


            @Override
            public int order() {
                return numOrder;
            }

        };
    }


    @Test
    void confirmedFindingEndsTheChain() {
        Resolved hit = new Resolved.AsOffset("confirmed");
        Resolved later = new Resolved.AsOffset("later");

        Resolved out = new ChainResolver(List.of(probe(10, true, hit), probe(20, true, later)))
                .resolve("anything", LST_PARTY);

        assertSame(hit, out);
    }


    /**
     * The rule that makes this (c) rather than "first hit wins": a shape match
     * is not evidence, so the chain keeps going and the later confirmation
     * beats it.
     */
    @Test
    void syntacticFindingDoesNotEndTheChain() {
        Resolved guess = new Resolved.AsOffset("guess");
        Resolved real = new Resolved.AsParty(new com.raposza.api.model.PartyInfo(
                ID_PARTY, "", true));

        Resolved out = new ChainResolver(List.of(probe(10, false, guess), probe(20, true, real)))
                .resolve("anything", LST_PARTY);

        assertSame(real, out);
    }


    @Test
    void oneGuessAloneIsReturned() {
        Resolved guess = new Resolved.AsOffset("0000007");
        Resolved out = new ChainResolver(List.of(probe(10, false, guess)))
                .resolve("0000007", LST_PARTY);
        assertSame(guess, out);
    }


    @Test
    void severalGuessesBecomeAmbiguousInOrder() {
        Resolved first = new Resolved.AsOffset("first");
        Resolved second = new Resolved.AsOffset("second");

        Resolved out = new ChainResolver(List.of(probe(20, false, second), probe(10, false, first)))
                .resolve("anything", LST_PARTY);

        Resolved.Ambiguous amb = assertInstanceOf(Resolved.Ambiguous.class, out);
        assertEquals("anything", amb.strRef());
        assertEquals(List.of(first, second), amb.lstCandidate());
    }


    @Test
    void nothingFoundIsNone() {
        Resolved out = new ChainResolver(List.<RefProbe_i>of()).resolve("whatever", LST_PARTY);
        assertEquals(new Resolved.None("whatever"), out);
    }


    @Test
    void blankReferenceIsNoneWithoutTouchingTheLedger() {
        FakeLedgerClient fake = new FakeLedgerClient();
        assertEquals(new Resolved.None(""), new ChainResolver(fake).resolve("   ", LST_PARTY));
        assertEquals(0, fake.cntCallParties);
        assertEquals(0, fake.cntCallContract);
    }


    /**
     * A failing probe must not be reported as an absent object. "You may not
     * read the ACS" and "there is no such contract" are different answers and
     * the operator needs the first one.
     */
    @Test
    void aFailingProbeWithNoOtherResultRethrows() {
        LedgerException ex = new LedgerException("PERMISSION_DENIED", "PERMISSION_DENIED", null);

        LedgerException thrown = assertThrows(LedgerException.class,
                () -> new ChainResolver(List.of(probeThrowing(10, ex))).resolve("x", LST_PARTY));

        assertSame(ex, thrown);
    }


    @Test
    void aFailingProbeDoesNotStopTheOthers() {
        Resolved real = new Resolved.AsOffset("found anyway");

        Resolved out = new ChainResolver(List.of(
                probeThrowing(10, new LedgerException("boom")), probe(20, true, real)))
                    .resolve("x", LST_PARTY);

        assertSame(real, out);
    }


    @Test
    void probesAreSortedByOrderNotByListPosition() {
        Resolved late = new Resolved.AsOffset("order 30");
        Resolved early = new Resolved.AsOffset("order 10");

        Resolved out = new ChainResolver(List.of(probe(30, true, late), probe(10, true, early)))
                .resolve("x", LST_PARTY);

        assertSame(early, out);
    }


    // ---- the standard chain, against a controlled ledger ----

    @Test
    void knownPartyResolvesAsPartyWithoutScanningContracts() {
        FakeLedgerClient fake = new FakeLedgerClient().withParty(ID_PARTY);

        Resolved out = new ChainResolver(fake).resolve(ID_PARTY, LST_PARTY);

        assertInstanceOf(Resolved.AsParty.class, out);
        assertEquals(0, fake.cntCallContract, "the party probe confirmed; the ACS scan should"
                + " never have run");
    }


    @Test
    void knownContractIdResolvesAsContract() {
        FakeLedgerClient fake = new FakeLedgerClient().withContract(ID_CONTRACT);

        Resolved out = new ChainResolver(fake).resolve(ID_CONTRACT, LST_PARTY);

        assertEquals(ID_CONTRACT, assertInstanceOf(Resolved.AsContract.class, out).contract()
                .idContract());
    }


    @Test
    void knownUpdateIdResolvesAsUpdateBeforeTheAcsIsScanned() {
        FakeLedgerClient fake = new FakeLedgerClient().withTree(ID_CONTRACT);

        Resolved out = new ChainResolver(fake).resolve(ID_CONTRACT, LST_PARTY);

        assertInstanceOf(Resolved.AsUpdate.class, out);
        assertEquals(0, fake.cntCallContract, "the tree lookup confirmed first; the ACS scan is"
                + " the expensive step and should not have run");
    }


    /**
     * The case the whole design is about: a hex string the ledger knows nothing
     * about. It has the shape of a contract id AND of an offset, and the only
     * honest answer is the guess, not a fabricated contract.
     */
    @Test
    void unknownHexStringFallsBackToTheOffsetGuess() {
        FakeLedgerClient fake = new FakeLedgerClient();

        Resolved out = new ChainResolver(fake).resolve(ID_CONTRACT, LST_PARTY);

        assertEquals(new Resolved.AsOffset(ID_CONTRACT), out);
        assertTrue(fake.cntCallContract > 0, "the contract lookup should have been attempted"
                + " before falling back to a guess");
    }


    @Test
    void unknownNonHexStringIsNone() {
        Resolved out = new ChainResolver(new FakeLedgerClient()).resolve("not-an-id", LST_PARTY);
        assertEquals(new Resolved.None("not-an-id"), out);
    }


    @Test
    void referenceIsTrimmedBeforeProbing() {
        FakeLedgerClient fake = new FakeLedgerClient().withParty(ID_PARTY);

        Resolved out = new ChainResolver(fake).resolve("  " + ID_PARTY + "\n", LST_PARTY);

        assertEquals(ID_PARTY, assertInstanceOf(Resolved.AsParty.class, out).party().idParty());
    }


    @Test
    void unknownPartyDoesNotBecomeAFabricatedParty() {
        FakeLedgerClient fake = new FakeLedgerClient().withParty("someone-else::1220ab");

        Resolved out = new ChainResolver(fake).resolve("ghost::1220ab", LST_PARTY);

        assertEquals(new Resolved.None("ghost::1220ab"), out);
    }

}
