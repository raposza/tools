// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * What this suite is FOR, stated plainly because it is not obvious from the
 * assertions: it exists so that the participant's configuration and the tokens
 * presented to it cannot drift apart again. They did drift - `AuthOverlay` held
 * one scope and the live test minted another shape entirely - and the cost was
 * three probes. Every test below is a statement about the two sides agreeing.
 *
 * Author Claude/bentzn
 */
class AuthPlanTest {

    private static final String STR_URL_JWKS = "file:/tmp/jwks.json";

    private static final String STR_SECRET = "raposza-probe-secret";

    private static final String STR_USER = "raposza-pqs";


    @Test
    void scopePlanMintsTheScopeItRequires() {
        AuthPlan plan = AuthPlan.ofJwksScope(STR_URL_JWKS);
        TokenSpec spec = plan.specFor(STR_USER);

        // The whole point of the type in one assertion: what the participant
        // will require and what the token will carry come from one field.
        assertEquals(plan.strScope(), spec.strScope());
        assertEquals(TokenShape.SCOPE, spec.shape());
        assertEquals(STR_USER, spec.strSubject());
        assertEquals(AuthPlan.STR_SCOPE_DEFAULT, spec.strScope());
    }


    @Test
    void audiencePlanMintsTheAudienceItRequires() {
        AuthPlan plan = AuthPlan.ofJwksAudience(STR_URL_JWKS, "sandbox");
        TokenSpec spec = plan.specFor(STR_USER);

        assertEquals(plan.strAudience(), spec.strAudience());
        assertEquals(TokenShape.AUDIENCE, spec.shape());
        assertEquals("https://daml.com/jwt/aud/participant/sandbox", spec.strAudience());
    }


    @Test
    void anAudienceMayAccompanyAScope() {
        // A token carrying both authenticated on 3.5.11, on a participant
        // configured with a secret alone and again with target-scope set.
        // This is not an inference from the field being nullable.
        AuthPlan plan = AuthPlan.ofUnsafeHmac256Scope(STR_SECRET)
                .withAudience(AuthPlan.audienceFor("sandbox"));
        TokenSpec spec = plan.specFor(STR_USER);

        assertEquals(TokenShape.SCOPE, spec.shape());
        assertEquals(AuthPlan.STR_SCOPE_DEFAULT, spec.strScope());
        assertEquals("https://daml.com/jwt/aud/participant/sandbox", spec.strAudience());
    }


    @Test
    void anAudiencePlanCarriesNoScope() {
        // TokenSpec clears it rather than rejecting it, and the reason is worth
        // holding onto: an audience token that also carried a scope would be
        // ambiguous about which shape it is, and 3.5.11 refuses an audience
        // token outright once a target scope is pinned.
        AuthPlan plan = AuthPlan.ofJwksAudience(STR_URL_JWKS, "sandbox");
        assertNull(plan.specFor(STR_USER).strScope());
    }


    @Test
    void partyRightsReachACustomTokenAndNoOther() {
        AuthPlan planCustom = AuthPlan.ofUnsafeHmac256Custom(STR_SECRET);
        TokenSpec specCustom = planCustom.specFor(STR_USER, List.of("alice"), List.of("alice"),
                true);

        assertEquals(TokenShape.CUSTOM, specCustom.shape());
        assertEquals(List.of("alice"), specCustom.lstActAs());
        assertTrue(specCustom.flagAdmin());

        // Accepted and carried on the record, and ignored by the minter for
        // this shape - on SCOPE the rights live on the ledger user. The
        // assertion is that the call does not throw, because a caller should
        // not have to branch on shape to ask for a token.
        AuthPlan planScope = AuthPlan.ofJwksScope(STR_URL_JWKS);
        assertEquals(TokenShape.SCOPE,
                planScope.specFor(STR_USER, List.of("alice"), List.of(), true).shape());
    }


    @Test
    void exactlyOneVerificationSourceIsRequired() {
        // Both, which would leave the participant's type name undecided.
        assertThrows(IllegalArgumentException.class,
                () -> new AuthPlan(TokenShape.SCOPE, STR_URL_JWKS, STR_SECRET, null,
                        AuthPlan.STR_SCOPE_DEFAULT, AuthPlan.TTL_DEFAULT));

        // Neither.
        assertThrows(IllegalArgumentException.class,
                () -> new AuthPlan(TokenShape.SCOPE, null, null, null,
                        AuthPlan.STR_SCOPE_DEFAULT, AuthPlan.TTL_DEFAULT));
    }


    @Test
    void aShapeWithoutItsRequiredFieldIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuthPlan(TokenShape.SCOPE, STR_URL_JWKS, null, null, null,
                        AuthPlan.TTL_DEFAULT));
        assertThrows(IllegalArgumentException.class,
                () -> new AuthPlan(TokenShape.AUDIENCE, STR_URL_JWKS, null, null, null,
                        AuthPlan.TTL_DEFAULT));
    }


    @Test
    void theLifetimeIsCarriedIntoTheToken() {
        AuthPlan plan = AuthPlan.ofJwksScope(STR_URL_JWKS).withTtl(Duration.ofMinutes(5));
        assertEquals(Duration.ofMinutes(5), plan.specFor(STR_USER).ttl());

        // The default is 24 h by decision and is ABOVE a cap nobody has
        // located: 3.5.11 refused a seven-day token while maxTokenLife defaults
        // to infinite. This asserts the number, NOT that Canton accepts it.
        assertEquals(Duration.ofHours(24), AuthPlan.TTL_DEFAULT);
    }


    @Test
    void aBlankUserIsRefused() {
        AuthPlan plan = AuthPlan.ofJwksScope(STR_URL_JWKS);
        assertThrows(IllegalArgumentException.class, () -> plan.specFor("  "));
    }

}
