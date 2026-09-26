// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.Substitution_i;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.PrimKind;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The adapter, and specifically the one thing it exists to get right: an
 * unknown name and a stale one are different failures and must not arrive as
 * the same answer.
 *
 * Author Claude/bentzn
 */
class EnvSubstitutionTest {

    private static final String CID = "00abcd";
    private static final DamlType TYPE_CID = new DamlType.Prim(PrimKind.CONTRACT_ID);


    private static Env env() {
        Env env = new Env();
        env.bind(Env.of("acct", new DamlValue.ContractRef(CID), TYPE_CID, 1, "upd-1"), "s");
        env.bind(Env.of("alice", new DamlValue.Party("Alice::1220ab"),
                new DamlType.Prim(PrimKind.PARTY), 2, null), "s");
        return env;
    }


    @Test
    void aBoundNameComesBackWithItsType() {
        Substitution_i.Bound bound = new EnvSubstitution(env(), 3, "s").lookup("acct")
                .orElseThrow();

        assertEquals(new DamlValue.ContractRef(CID), bound.value());
        assertEquals(TYPE_CID, bound.type());
    }


    /** Empty means "no such name", and the coercer reports it with the path. */
    @Test
    void anUnknownNameIsEmptyRatherThanAFailure() {
        assertEquals(Optional.empty(), new EnvSubstitution(env(), 3, "s").lookup("nope"));
    }


    /**
     * Stale THROWS. Returning empty would report "not bound", which sends the
     * operator looking for a typo in a name that is right there and correct.
     */
    @Test
    void aStaleBindingThrowsRatherThanLookingUnbound() {
        Env env = env();
        env.consume(CID);

        CaqlException ex = assertThrows(CaqlException.class,
                () -> new EnvSubstitution(env, 7, "AS $alice EXERCISE ON $acct X").lookup("acct"));

        assertEquals(7, ex.numLine());
        assertTrue(ex.getMessage().contains("stale"), ex.getMessage());
        assertTrue(ex.getMessage().contains(CID), ex.getMessage());
    }


    @Test
    void anEnvironmentIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new EnvSubstitution(null, 1, "s"));
    }

}
