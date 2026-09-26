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
 * What the tool may conclude from a token it has not verified.
 *
 * Plain (unsigned) JWTs throughout, on purpose: TokenParties must not care
 * whether a token is signed, and a test that signed them would be asserting
 * something else.
 *
 * Author Claude/bentzn
 */
class TokenPartiesTest {

    private static String token(Map<String, Object> mapClaim) {
        JWTClaimsSet.Builder bld = new JWTClaimsSet.Builder().subject("probe");
        if (mapClaim != null)
            bld.claim(JwtMinter.CLAIM_LEDGER_API, mapClaim);
        return new PlainJWT(bld.build()).serialize();
    }


    @Test
    void readsTheReadAsParties() {
        String strToken = token(Map.of("readAs", List.of("Alice::1220ab", "Bank::1220ab"),
                "actAs", List.of(), "admin", Boolean.FALSE));

        List<String> lstParty = TokenParties.readable(strToken);

        assertEquals(List.of("Alice::1220ab", "Bank::1220ab"), lstParty);
    }


    /** readAs first, and a party in both arrays appears once. */
    @Test
    void mergesActAsWithoutDuplicating() {
        String strToken = token(Map.of("readAs", List.of("Alice::1220ab", "Bank::1220ab"),
                "actAs", List.of("Bank::1220ab", "Carol::1220ab"), "admin", Boolean.FALSE));

        List<String> lstParty = TokenParties.readable(strToken);

        assertEquals(List.of("Alice::1220ab", "Bank::1220ab", "Carol::1220ab"), lstParty);
    }


    /**
     * An admin token names no parties, which is exactly the case that made this
     * class necessary: it can list parties from the participant instead.
     */
    @Test
    void anAdminTokenNamesNoParties() {
        String strToken = token(Map.of("readAs", List.of(), "actAs", List.of(), "admin",
                Boolean.TRUE));

        assertTrue(TokenParties.readable(strToken).isEmpty());
    }


    /** A scope or audience token authorises a user; party names live elsewhere. */
    @Test
    void aTokenWithoutTheCustomClaimYieldsNothing() {
        assertTrue(TokenParties.readable(token(null)).isEmpty());
    }


    /**
     * Unparseable input is not an error. The participant remains the source of
     * the party list, which is where it came from before this existed.
     */
    @Test
    void rubbishYieldsNothingRatherThanThrowing() {
        assertTrue(TokenParties.readable("not-a-token").isEmpty());
        assertTrue(TokenParties.readable("").isEmpty());
        assertTrue(TokenParties.readable(null).isEmpty());
    }

}
