// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.idp;

import com.raposza.jwt.AuthPlan;
import com.raposza.jwt.TokenShape;
import com.raposza.jwt.TokenSpec;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE SEAM, on the token side. Everything here asserts that a client registered
 * from an {@link AuthPlan} issues exactly what {@link AuthPlan#specFor} says it
 * does.
 *
 * It is a UNIT test on purpose. The disagreement it guards against - the
 * participant pinning one thing and the minter carrying another - was live for
 * weeks behind a suite that only runs against an installed Canton. A gated
 * test nobody runs is where this started.
 *
 * Author Claude/bentzn
 */
class IdpClientPlanTest {

    private static final String STR_CLIENT_ID = "raposza-pqs";

    private static final String STR_SECRET = "raposza-test-client-secret";

    private static final String STR_SUBJECT = "raposza-pqs";

    private static final String STR_URL_JWKS = "http://127.0.0.1:9999/jwks.json";

    private static final String STR_ISSUER = "http://127.0.0.1:9999";

    private static final String STR_NODE = "participant1";


    @Test
    void anAudiencePlanIssuesExactlyWhatSpecForSays() {
        AuthPlan plan = AuthPlan.ofJwksAudience(STR_URL_JWKS, STR_NODE);
        TokenSpec specPlan = plan.specFor(STR_SUBJECT);
        TokenSpec specClient =
                IdpClient.ofPlan(STR_CLIENT_ID, STR_SECRET, STR_SUBJECT, plan)
                        .tokenSpec(STR_ISSUER);

        assertEquals(specPlan.shape(), specClient.shape());
        assertEquals(specPlan.strSubject(), specClient.strSubject());
        assertEquals(specPlan.strAudience(), specClient.strAudience());
        assertEquals(specPlan.strScope(), specClient.strScope());
        assertEquals(specPlan.ttl(), specClient.ttl());

        // The one field that legitimately differs, asserted rather than left
        // to be noticed: only a bound provider knows its own issuer, so the
        // plan cannot carry one and does not pretend to.
        assertNull(specPlan.strIssuer());
        assertEquals(STR_ISSUER, specClient.strIssuer());
    }


    @Test
    void aScopePlanIssuesExactlyWhatSpecForSays() {
        AuthPlan plan = AuthPlan.ofJwksScope(STR_URL_JWKS);
        TokenSpec specPlan = plan.specFor(STR_SUBJECT);
        TokenSpec specClient =
                IdpClient.ofPlan(STR_CLIENT_ID, STR_SECRET, STR_SUBJECT, plan)
                        .tokenSpec(STR_ISSUER);

        assertEquals(TokenShape.SCOPE, specClient.shape());
        assertEquals(specPlan.strScope(), specClient.strScope());
        assertEquals(AuthPlan.STR_SCOPE_DEFAULT, specClient.strScope());
        assertEquals(specPlan.ttl(), specClient.ttl());
    }


    /**
     * The plan's lifetime reaches the registration, and it is NOT
     * `IdpClient.TTL_DEFAULT`. Those are five minutes and twenty-four hours,
     * and a client that quietly kept its own would mint a token the
     * participant's rendered `max-token-life` never sees.
     */
    @Test
    void theLifetimeComesFromThePlan() {
        AuthPlan plan = AuthPlan.ofJwksScope(STR_URL_JWKS);
        IdpClient client = IdpClient.ofPlan(STR_CLIENT_ID, STR_SECRET, STR_SUBJECT, plan);

        assertEquals(AuthPlan.TTL_DEFAULT, client.ttl());
        assertEquals(Duration.ofHours(24), client.ttl());

        AuthPlan planShort = plan.withTtl(Duration.ofMinutes(5));
        assertEquals(Duration.ofMinutes(5),
                IdpClient.ofPlan(STR_CLIENT_ID, STR_SECRET, STR_SUBJECT, planShort).ttl());
    }


    /**
     * Two refusals, and both name their reason. A CUSTOM plan has no shape this
     * endpoint issues; a plan carrying both an audience and a scope has two
     * token sides and one registration mints one.
     */
    @Test
    void thePlansThisEndpointCannotIssueAreRefusedByName() {
        AuthPlan planCustom = AuthPlan.ofUnsafeHmac256Custom("a-shared-secret");
        IdpException exCustom = assertThrows(IdpException.class,
                () -> IdpClient.ofPlan(STR_CLIENT_ID, STR_SECRET, STR_SUBJECT, planCustom));
        assertTrue(exCustom.getMessage().contains("CUSTOM"));

        AuthPlan planBoth = AuthPlan.ofJwksScope(STR_URL_JWKS).withAudience("some-audience");
        IdpException exBoth = assertThrows(IdpException.class,
                () -> IdpClient.ofPlan(STR_CLIENT_ID, STR_SECRET, STR_SUBJECT, planBoth));
        assertTrue(exBoth.getMessage().contains("one shape"));

        assertThrows(IdpException.class,
                () -> IdpClient.ofPlan(STR_CLIENT_ID, STR_SECRET, STR_SUBJECT, null));
    }


    /**
     * The url a plan is built from before the provider binds is the url the
     * provider serves once it has. Composed in one place, and this asserts the
     * composition rather than the binding - `TestIdpTest` covers a running
     * provider.
     */
    @Test
    void theUrlComposedBeforeTheBindIsTheOneServed() {
        assertEquals(STR_URL_JWKS, TestIdp.strUrlJwksOn(9999));
        assertEquals(STR_ISSUER, TestIdp.strUrlIssuerOn(9999));
        assertTrue(TestIdp.strUrlJwksOn(9999).endsWith(TestIdp.STR_PATH_JWKS));
    }

}
