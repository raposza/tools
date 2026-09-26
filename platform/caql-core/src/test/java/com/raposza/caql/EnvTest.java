// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.model.DamlType;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;
import com.raposza.api.model.PrimKind;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The environment, against the rules of `dql-design.md` sec. 6 and sec. 7.
 *
 * The staleness tests are the ones worth reading: each is a case a boolean per
 * binding would get wrong, which is why staleness is derived from the consumed
 * contract ids rather than stored.
 *
 * Author Claude/bentzn
 */
class EnvTest {

    private static final String CID_A = "00aaaa";
    private static final String CID_B = "00bbbb";
    private static final DamlType TYPE_CID = new DamlType.Prim(PrimKind.CONTRACT_ID);
    private static final DamlType TYPE_PARTY = new DamlType.Prim(PrimKind.PARTY);


    private static Binding cid(String name, String idContract, int numLine) {
        return Env.of(name, new DamlValue.ContractRef(idContract), TYPE_CID, numLine, "upd-1");
    }


    @Test
    void aBoundNameIsFoundWithItsType() {
        Env env = new Env();
        env.bind(Env.of("alice", new DamlValue.Party("Alice::1220ab"), TYPE_PARTY, 1, null), "s");

        Binding binding = env.require("alice", 2, "s");
        assertEquals("Alice::1220ab", ((DamlValue.Party) binding.value()).idParty());
        assertEquals(TYPE_PARTY, binding.type());
        assertTrue(binding.idUpdate().isEmpty(), "ALLOCATE PARTY makes no submission");
    }


    /**
     * The register is put back before a run, and the run wins when it binds
     * the name again - which is what re-running a line means.
     */
    @Test
    void aRestoredNameIsBoundOverRatherThanRefused() {
        Env env = new Env();
        env.restore(cid("a", CID_A, 3));
        assertEquals(CID_A,
                ((DamlValue.ContractRef) env.require("a", 5, "s").value()).idContract());

        env.bind(cid("a", CID_B, 9), "s");

        assertEquals(CID_B,
                ((DamlValue.ContractRef) env.require("a", 9, "s").value()).idContract());
        assertEquals(1, env.all().size());
    }


    /** Bound over ONCE. A second bind in the same run is the refused case. */
    @Test
    void aRestoredNameIsRefusedTheSecondTimeInOneRun() {
        Env env = new Env();
        env.restore(cid("a", CID_A, 3));
        env.bind(cid("a", CID_B, 9), "s");

        assertThrows(CaqlException.class, () -> env.bind(cid("a", CID_A, 11), "s"));
    }


    /** Sec. 6: refused, not shadowed - and the message names the earlier line. */
    @Test
    void rebindingIsRefusedAndNamesTheLineThatWon() {
        Env env = new Env();
        env.bind(cid("a", CID_A, 3), "s");

        CaqlException ex = assertThrows(CaqlException.class,
                () -> env.bind(cid("a", CID_B, 9), "s"));

        assertTrue(ex.getMessage().contains("already bound"), ex.getMessage());
        assertTrue(ex.getMessage().contains("line 3"), ex.getMessage());
        assertEquals(CID_A, ((DamlValue.ContractRef) env.require("a", 9, "s").value()).idContract());
    }


    /**
     * The overwhelmingly likely cause of an unknown name is a typo against a
     * name that is right there, so the message shows what is bound.
     */
    @Test
    void anUnknownNameListsWhatIsBound() {
        Env env = new Env();
        env.bind(cid("acct", CID_A, 1), "s");

        CaqlException ex = assertThrows(CaqlException.class, () -> env.require("acct2", 2, "s"));
        assertTrue(ex.getMessage().contains("$acct"), ex.getMessage());
    }


    @Test
    void anUnknownNameInAnEmptyEnvironmentSaysSoRatherThanListingNothing() {
        CaqlException ex = assertThrows(CaqlException.class,
                () -> new Env().require("a", 1, "s"));
        assertTrue(ex.getMessage().contains("nothing is bound yet"), ex.getMessage());
    }


    /** Sec. 7's own example: two bindings, one id, archived through one of them. */
    @Test
    void consumingAnIdStalesEveryBindingHoldingIt() {
        Env env = new Env();
        env.bind(cid("a", CID_A, 1), "s");
        env.bind(cid("b", CID_A, 2), "s");
        env.bind(cid("c", CID_B, 3), "s");

        env.consume(CID_A);

        assertThrows(CaqlException.class, () -> env.require("a", 4, "s"));
        assertThrows(CaqlException.class, () -> env.require("b", 4, "s"));
        assertEquals(CID_B,
                ((DamlValue.ContractRef) env.require("c", 4, "s").value()).idContract());
    }


    /** A boolean per binding would leave this one looking fresh. */
    @Test
    void aBindingMadeAfterTheConsumeIsStaleImmediately() {
        Env env = new Env();
        env.consume(CID_A);
        env.bind(cid("late", CID_A, 5), "s");

        CaqlException ex = assertThrows(CaqlException.class, () -> env.require("late", 6, "s"));
        assertTrue(ex.getMessage().contains("stale"), ex.getMessage());
        assertTrue(ex.getMessage().contains(CID_A), ex.getMessage());
    }


    /**
     * Sec. 7 in as many words: a contract id returned INSIDE a choice result
     * stales its binding, even though the language cannot read into the result
     * to find it.
     */
    @Test
    void anIdNestedInsideAStructuredResultStalesItsBinding() {
        DamlValue.Rec result = new DamlValue.Rec(new DataId("p", "Main", "R"), List.of(
                new DamlValue.Rec.Field("note", new DamlValue.Text("kept")),
                new DamlValue.Rec.Field("children", new DamlValue.Lst(List.of(
                        new DamlValue.Opt(new DamlValue.ContractRef(CID_A)))))));

        Env env = new Env();
        env.bind(Env.of("r", result, new DamlType.Ref(new DataId("p", "Main", "R")), 1, "upd"),
                "s");
        assertTrue(env.staleFor(env.lookup("r").orElseThrow()).isEmpty());

        env.consume(CID_A);
        assertEquals(CID_A, env.staleFor(env.lookup("r").orElseThrow()).orElseThrow());
    }


    /** A GenMap KEY that has been archived is as stale as a value that has. */
    @Test
    void aContractIdUsedAsAMapKeyIsFound() {
        DamlValue map = new DamlValue.GenMap(List.of(new DamlValue.GenMap.Entry(
                new DamlValue.ContractRef(CID_A), new DamlValue.Text("v"))));

        Env env = new Env();
        env.bind(Env.of("m", map, TYPE_CID, 1, null), "s");
        env.consume(CID_A);

        assertTrue(env.staleFor(env.lookup("m").orElseThrow()).isPresent());
    }


    /** A value holding no contract id can never go stale, whatever is consumed. */
    @Test
    void aScalarBindingIsNeverStale() {
        Env env = new Env();
        env.bind(Env.of("p", new DamlValue.Party("Alice::1220ab"), TYPE_PARTY, 1, null), "s");
        env.consume(CID_A);
        env.consume(CID_B);

        assertTrue(env.staleFor(env.lookup("p").orElseThrow()).isEmpty());
    }


    @Test
    void bindingsAreReportedInTheOrderTheScriptMadeThem() {
        Env env = new Env();
        env.bind(cid("z", CID_A, 1), "s");
        env.bind(cid("a", CID_B, 2), "s");
        env.bind(Env.of("m", new DamlValue.Text("x"), new DamlType.Prim(PrimKind.TEXT), 3, null),
                "s");

        assertEquals(List.of("z", "a", "m"), env.all().stream().map(Binding::name).toList());
    }


    @Test
    void consumeIgnoresNothing() {
        Env env = new Env();
        env.consume(null);
        env.consume("  ");
        assertTrue(env.consumed().isEmpty());
        assertFalse(env.consumed().contains(""));
    }

}
