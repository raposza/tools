// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Resolution against a payload, including every way it is allowed to fail.
 *
 * The failures matter more than the successes here: an empty answer is what
 * puts a button into the "the ledger decides" state, and anything that returned
 * a partial party set instead would produce a confident wrong answer about who
 * may act.
 *
 * Author Claude/bentzn
 */
class ChoiceControllersTest {

    private static final DataId ID_ACCOUNT = new DataId("abc123", "Main", "Account");
    private static final DataId ID_HOLDER = new DataId("abc123", "Main", "Holder");

    private static final String ID_ALICE = "Alice::1220ab";
    private static final String ID_BANK = "Bank::1220ab";


    private static DamlValue.Rec payload() {
        return new DamlValue.Rec(ID_ACCOUNT, List.of(
                new DamlValue.Rec.Field("owner", new DamlValue.Party(ID_ALICE)),
                new DamlValue.Rec.Field("observers",
                        new DamlValue.Lst(List.of(new DamlValue.Party(ID_BANK)))),
                new DamlValue.Rec.Field("label", new DamlValue.Text("savings")),
                new DamlValue.Rec.Field("delegate",
                        new DamlValue.Opt(new DamlValue.Party(ID_BANK))),
                new DamlValue.Rec.Field("nobody", new DamlValue.Opt(null)),
                new DamlValue.Rec.Field("holder", new DamlValue.Rec(ID_HOLDER, List.of(
                        new DamlValue.Rec.Field("party", new DamlValue.Party(ID_BANK)))))));
    }


    @Test
    void literalPartiesResolveWithoutAPayload() {
        Optional<List<String>> optParty = ChoiceControllers
                .resolve(new ChoiceControllers.Parties(List.of(ID_BANK, ID_BANK)), payload());

        assertEquals(List.of(ID_BANK), optParty.orElseThrow(), "duplicates should collapse");
    }


    @Test
    void aFieldPathResolvesToTheParty() {
        Optional<List<String>> optParty = ChoiceControllers
                .resolve(new ChoiceControllers.Fields(List.of("owner")), payload());

        assertEquals(List.of(ID_ALICE), optParty.orElseThrow());
    }


    @Test
    void aNestedPathAListAndAnOptionAllResolve() {
        Optional<List<String>> optParty = ChoiceControllers.resolve(new ChoiceControllers.Fields(
                List.of("owner", "observers", "delegate", "holder.party")), payload());

        assertEquals(List.of(ID_ALICE, ID_BANK), optParty.orElseThrow());
    }


    @Test
    void unresolvedStaysUnresolved() {
        Optional<List<String>> optParty = ChoiceControllers
                .resolve(new ChoiceControllers.Unresolved("controller is computed"), payload());

        assertTrue(optParty.isEmpty());
    }


    @Test
    void oneUnreachablePathFailsTheWholeAnswer() {
        Optional<List<String>> optParty = ChoiceControllers
                .resolve(new ChoiceControllers.Fields(List.of("owner", "missing")), payload());

        assertTrue(optParty.isEmpty(), "a partial set must not be reported as a complete one");
    }


    @Test
    void aFieldThatIsNotAPartyFails() {
        Optional<List<String>> optParty = ChoiceControllers
                .resolve(new ChoiceControllers.Fields(List.of("label")), payload());

        assertTrue(optParty.isEmpty());
    }


    @Test
    void aNoneIsNotAController() {
        Optional<List<String>> optParty = ChoiceControllers
                .resolve(new ChoiceControllers.Fields(List.of("nobody")), payload());

        assertTrue(optParty.isEmpty());
    }


    @Test
    void aMissingPayloadFailsRatherThanThrows() {
        Optional<List<String>> optParty = ChoiceControllers
                .resolve(new ChoiceControllers.Fields(List.of("owner")), null);

        assertTrue(optParty.isEmpty());
    }

}
