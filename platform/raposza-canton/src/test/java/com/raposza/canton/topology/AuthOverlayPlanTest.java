// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.raposza.canton.install.VersionId;
import com.raposza.jwt.AuthPlan;
import com.raposza.jwt.TokenShape;
import com.raposza.jwt.TokenSpec;

/**
 * THE SEAM. Everything here asserts that what the participant will REQUIRE and
 * what a token will CARRY came out of one object.
 *
 * That is not a hypothetical failure. `AuthOverlay` has held
 * `daml_ledger_api` in STR_SCOPE_DEFAULT since it was written and no token
 * this project minted ever carried it, because the live test built its own
 * shape. The disagreement was invisible to every unit test in the tree and
 * took a live run to find.
 *
 * `AuthOverlayTest` covers the factories on their own. This covers the pair.
 *
 * Author Claude/bentzn
 */
class AuthOverlayPlanTest {

    private static final String STR_URL_JWKS = "file:/tmp/jwks.json";

    private static final String STR_SECRET = "raposza-probe-secret";

    private static final String STR_USER = "raposza-pqs";

    /** Below the floor, and the whole 2.x line is below it. */
    private static final VersionId VER_2X = VersionId.of(2, 10, 0);

    /** Below the floor and on the 3.x line - the case a family gate gets wrong. */
    private static final VersionId VER_3X_BELOW = VersionId.of(3, 4, 11);

    /** The floor itself. */
    private static final VersionId VER_3X = VersionId.of(3, 5, 6);


    @Test
    void aScopePlanPinsTheScopeItsTokensCarry() {
        AuthPlan plan = AuthPlan.ofJwksScope(STR_URL_JWKS);
        AuthOverlay overlay = AuthOverlay.of(VER_3X, plan);
        TokenSpec spec = plan.specFor(STR_USER);

        // One field, two renderings. This is the whole point of the type.
        assertTrue(overlay.render().contains("target-scope = \"" + spec.strScope() + "\""));
        assertEquals(AuthOverlay.STR_TYPE_JWKS, overlay.strType());
    }


    @Test
    void anAudiencePlanPinsTheAudienceItsTokensCarry() {
        AuthPlan plan = AuthPlan.ofJwksAudience(STR_URL_JWKS, "sandbox");
        AuthOverlay overlay = AuthOverlay.of(VER_3X, plan);
        TokenSpec spec = plan.specFor(STR_USER);

        assertTrue(overlay.render().contains("target-audience = \"" + spec.strAudience() + "\""));
        assertFalse(overlay.render().contains("target-scope"));
    }


    @Test
    void theLineDecidesTheTypeAndNothingElse() {
        AuthPlan plan = AuthPlan.ofJwksScope(STR_URL_JWKS);
        assertEquals(AuthOverlay.STR_TYPE_JWKS_2X,
                AuthOverlay.of(VER_2X, plan).strType());
        assertEquals(AuthOverlay.STR_TYPE_JWKS,
                AuthOverlay.of(VER_3X, plan).strType());
    }


    @Test
    void anHmacScopePlanPinsItsScopeToo() {
        // MEASURED, and this is what makes the key worth rendering:
        // with target-scope present an audience-only token flipped from
        // accepted to refused, and the log gave a distinct reason. A key that
        // only parsed would have changed nothing.
        AuthPlan plan = AuthPlan.ofUnsafeHmac256Scope(STR_SECRET);
        AuthOverlay overlay = AuthOverlay.of(VER_3X, plan);

        assertEquals(AuthOverlay.STR_TYPE_HMAC_256, overlay.strType());
        assertTrue(overlay.render().contains("target-scope = \"daml_ledger_api\""));
        assertTrue(overlay.render().contains("secret = \"" + STR_SECRET + "\""));
    }


    @Test
    void anHmacCustomPlanPinsNothingBeyondTheSecret() {
        // The custom claim carries its own rights; there is no scope for the
        // participant to compare against. Measured working on 2.x, 2026-08-07.
        AuthPlan plan = AuthPlan.ofUnsafeHmac256Custom(STR_SECRET);
        AuthOverlay overlay = AuthOverlay.of(VER_2X, plan);

        assertEquals(AuthOverlay.STR_TYPE_HMAC_256, overlay.strType());
        assertFalse(overlay.render().contains("target-scope"));
        assertFalse(overlay.render().contains("target-audience"));
    }


    @Test
    void aCustomPlanOverJwksIsRefusedRatherThanGuessed() {
        // 2.x verifies custom-claim tokens against a JWKS with neither a
        // target-audience nor a target-scope, and this project has never
        // started such a stack. Rendering one would be inventing a shape.
        // The refusal names it so the next reader knows it is unmeasured
        // rather than unsupported.
        AuthPlan plan = new AuthPlan(TokenShape.CUSTOM, STR_URL_JWKS, null, null, null,
                AuthPlan.TTL_DEFAULT);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AuthOverlay.of(VER_2X, plan));
        assertTrue(ex.getMessage().contains("UNMEASURED"));
    }


    @Test
    void theParticipantCapIsTheTokenLifetime() {
        // THE DRIFT THIS TYPE EXISTS FOR, and it would have shipped. Without
        // max-token-life a participant caps at its own default, which is far
        // below what TTL_DEFAULT mints. Every token would have been refused at
        // verification and read as a broken key. With the key set, the minted
        // lifetime is accepted.
        AuthPlan plan = AuthPlan.ofJwksScope(STR_URL_JWKS);
        TokenSpec spec = plan.specFor(STR_USER);
        String strRendered = AuthOverlay.of(VER_3X, plan).render();

        assertTrue(strRendered.contains("max-token-life = " + spec.ttl().toSeconds() + "s"));
        assertEquals(86400L, spec.ttl().toSeconds());
    }


    @Test
    void aShortLifetimeReachesTheCapToo() {
        AuthPlan plan = AuthPlan.ofUnsafeHmac256Scope(STR_SECRET)
                .withTtl(java.time.Duration.ofMinutes(5));
        assertTrue(AuthOverlay.of(VER_3X, plan).render()
                .contains("max-token-life = 300s"));
    }


    @Test
    void theTwoXLineRendersNoCapAtAll() {
        // A 2.x participant refuses `max-token-life` on the auth-service by
        // path, file and line, and the stack does not start.
        AuthPlan plan = AuthPlan.ofJwksScope(STR_URL_JWKS);
        assertFalse(AuthOverlay.of(VER_2X, plan).render()
                .contains("max-token-life"));

        // The same for the two factories the window reaches, which is where
        // the refused configuration actually came from.
        assertFalse(AuthOverlay.ofSecret(VER_2X, STR_SECRET, null,
                "daml_ledger_api", AuthPlan.TTL_DEFAULT).render()
                .contains("max-token-life"));
        assertTrue(AuthOverlay.ofSecret(VER_3X, STR_SECRET, null,
                "daml_ledger_api", AuthPlan.TTL_DEFAULT).render()
                .contains("max-token-life = 86400s"));
    }


    @Test
    void theOldFactoriesCarryNoCap() {
        // A caller holding no plan has no lifetime to declare, and the eleven
        // cases in AuthOverlayTest assert the old rendering exactly.
        assertFalse(AuthOverlay.ofJwksScope(STR_URL_JWKS, "daml_ledger_api").render()
                .contains("max-token-life"));
    }


    /**
     * TWO COPIES OF ONE CONVENTION, asserted equal, because that is the same
     * defect one layer below the one this class exists for.
     *
     * `AuthOverlay.STR_AUDIENCE_PREFIX` and `AuthPlan.STR_AUDIENCE_PREFIX` are
     * separate literals in separate modules, and the participant compares the
     * string it was configured with against the string the token carries. If
     * they ever drift, every audience-based token is refused and the failure
     * reads as a key problem - which is precisely how the scope disagreement
     * presented. Nothing but an assertion keeps two literals in step.
     */
    @Test
    void theTwoAudienceConventionsAreTheSameString() {
        assertEquals(AuthPlan.STR_AUDIENCE_PREFIX, AuthOverlay.STR_AUDIENCE_PREFIX);
        assertEquals(AuthPlan.audienceFor("participant1"),
                AuthOverlay.audienceForParticipant("participant1"));
    }


    /**
     * And the scope default, for the same reason. This one has already drifted
     * once in effect: the overlay held it and no token carried it.
     */
    @Test
    void theTwoScopeDefaultsAreTheSameString() {
        assertEquals(AuthPlan.STR_SCOPE_DEFAULT, AuthOverlay.STR_SCOPE_DEFAULT);
    }


    @Test
    void theParticipantNameReachesTheRenderedPath() {
        AuthPlan plan = AuthPlan.ofJwksScope(STR_URL_JWKS);
        String strRendered = AuthOverlay.of(VER_3X, plan).render("mynode");
        assertTrue(strRendered.contains("canton.participants.mynode.ledger-api.auth-services"));
    }


    /**
     * THE GATE IS A VERSION FLOOR, NOT A FAMILY, and this is the case that
     * shipped broken. Reading the 2.x refusal as a 2.x/3.x difference gives
     * every 3.x install the key, and 3.x installs below the floor refuse it by
     * path, file and line exactly as the 2.x line does.
     */
    @Test
    void aThreeFourParticipantIsBelowTheFloorAndGetsNoCap() {
        AuthPlan plan = AuthPlan.ofJwksScope(STR_URL_JWKS);
        assertFalse(AuthOverlay.of(VER_3X_BELOW, plan).render()
                .contains("max-token-life"));
        assertFalse(AuthOverlay.ofSecret(VER_3X_BELOW, STR_SECRET, null,
                "daml_ledger_api", AuthPlan.TTL_DEFAULT).render()
                .contains("max-token-life"));
        assertFalse(AuthOverlay.ofJwksUrl(VER_3X_BELOW, STR_URL_JWKS, "sandbox",
                null, AuthPlan.TTL_DEFAULT).render().contains("max-token-life"));
    }


    /**
     * 3.5.1 refuses and 3.5.6 accepts, with nothing installable in between -
     * no bundle carries a Canton numbered 3.5.2 to 3.5.5. These two
     * assertions are the boundary itself.
     */
    @Test
    void theFloorSitsBetweenThreeFiveOneAndThreeFiveSix() {
        AuthPlan plan = AuthPlan.ofJwksScope(STR_URL_JWKS);
        assertFalse(AuthOverlay.of(VersionId.of(3, 5, 1), plan).render()
                .contains("max-token-life"));
        assertTrue(AuthOverlay.of(VersionId.of(3, 5, 6), plan).render()
                .contains("max-token-life = 86400s"));
        assertEquals(VersionId.of(3, 5, 6), AuthOverlay.VER_FLOOR_TOKEN_LIFE);
    }


    /**
     * THE CAP THE MINT MUST RESPECT, and the reason it exists.
     *
     * Below the floor the participant enforces a short ceiling of its own and
     * cannot be told otherwise, so a mint that issues a long lifetime there
     * produces a stack that starts and then refuses every call - which is what
     * shipped before this.
     */
    @Test
    void belowTheFloorTheMintIsCappedAndAboveItIsNot() {
        java.time.Duration ttlDay = java.time.Duration.ofHours(24);

        assertEquals(ttlDay, AuthOverlay.ttlTokenFor(VER_3X, ttlDay));
        assertEquals(AuthOverlay.TTL_BELOW_FLOOR,
                AuthOverlay.ttlTokenFor(VER_3X_BELOW, ttlDay));
        assertEquals(AuthOverlay.TTL_BELOW_FLOOR,
                AuthOverlay.ttlTokenFor(VER_2X, ttlDay));

        // The margin is real, not decorative: minting AT the measured ceiling
        // puts every call on the boundary.
        assertTrue(AuthOverlay.TTL_BELOW_FLOOR
                .compareTo(AuthOverlay.TTL_CEILING_BELOW_FLOOR) < 0);
        assertEquals(300L, AuthOverlay.TTL_CEILING_BELOW_FLOOR.toSeconds());

        // A caller asking for LESS than the cap keeps what it asked for.
        java.time.Duration ttlShort = java.time.Duration.ofSeconds(30);
        assertEquals(ttlShort, AuthOverlay.ttlTokenFor(VER_3X_BELOW, ttlShort));
    }

}
