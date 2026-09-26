// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * actAs alone, which is the question "may this choice be exercised" asks.
 *
 * Plain (unsigned) JWTs throughout, for the same reason TokenPartiesTest uses
 * them: nothing here verifies a token, and a test that signed them would be
 * asserting something else.
 *
 * Author Claude/bentzn
 */
class TokenPartiesActAsTest {

    private static String token(Map<String, Object> mapClaim) {
        JWTClaimsSet.Builder bld = new JWTClaimsSet.Builder().subject("probe");
        if (mapClaim != null)
            bld.claim(JwtMinter.CLAIM_LEDGER_API, mapClaim);
        return new PlainJWT(bld.build()).serialize();
    }


    @Test
    void readsActAsAndNotReadAs() {
        String strToken = token(Map.of("readAs", List.of("Alice::1220ab"),
                "actAs", List.of("Bank::1220ab"), "admin", Boolean.FALSE));

        assertEquals(List.of("Bank::1220ab"), TokenParties.actAs(strToken));
    }


    @Test
    void aReadOnlyTokenActsAsNobody() {
        String strToken = token(Map.of("readAs", List.of("Alice::1220ab"), "actAs", List.of()));

        assertTrue(TokenParties.actAs(strToken).isEmpty());
    }


    @Test
    void aTokenWithoutTheCustomClaimSaysNothing() {
        assertTrue(TokenParties.actAs(token(null)).isEmpty());
    }


    @Test
    void garbageAndNullAreNotErrors() {
        assertTrue(TokenParties.actAs("not-a-token").isEmpty());
        assertTrue(TokenParties.actAs(null).isEmpty());
        assertTrue(TokenParties.actAs("   ").isEmpty());
    }


    @Test
    void duplicatesCollapseAndOrderIsKept() {
        String strToken = token(Map.of("actAs",
                List.of("Bank::1220ab", "Alice::1220ab", "Bank::1220ab")));

        assertEquals(List.of("Bank::1220ab", "Alice::1220ab"), TokenParties.actAs(strToken));
    }

}
