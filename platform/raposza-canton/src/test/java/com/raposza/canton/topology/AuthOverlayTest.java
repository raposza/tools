// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the overlay renders, and one thing it must never render.
 *
 * These tests cannot say whether Canton accepts the result - only a start can,
 * and that is `SandboxStackLiveTest`. What they can hold is the difference
 * that was measured: the 3.x type name, and the fact that the field names
 * either side of it did not change.
 *
 * Author Claude/bentzn
 */
class AuthOverlayTest {

    private static final String STR_URL = "file:///tmp/keys/jwks.json";


    @Test
    void theJwksTypeIsTheThreeXNameAndNotTheTwoXOne() {
        String strConf = AuthOverlay.ofJwksAudience(STR_URL, "aud-x").render();

        assertTrue(strConf.contains("type = jwt-jwks"), strConf);
        assertFalse(strConf.contains(AuthOverlay.STR_TYPE_JWKS_2X), strConf);
    }


    @Test
    void anAudienceOverlayCarriesUrlAndTargetAudienceAndNoScope() {
        String strConf = AuthOverlay.ofJwksAudience(STR_URL, "aud-x").render();

        assertTrue(strConf.contains("url = \"" + STR_URL + "\""), strConf);
        assertTrue(strConf.contains("target-audience = \"aud-x\""), strConf);
        assertFalse(strConf.contains("target-scope"), strConf);
    }


    @Test
    void aScopeOverlayCarriesUrlAndTargetScopeAndNoAudience() {
        String strConf = AuthOverlay.ofJwksScope(STR_URL, AuthOverlay.STR_SCOPE_DEFAULT).render();

        assertTrue(strConf.contains("target-scope = \"daml_ledger_api\""), strConf);
        assertFalse(strConf.contains("target-audience"), strConf);
    }


    @Test
    void theKeyPathIsTheParticipantTheBundledConfigNames() {
        String strConf = AuthOverlay.ofWildcard().render();

        assertTrue(strConf.contains("canton.participants." + StorageOverlay.STR_NODE_PARTICIPANT
                + ".ledger-api.auth-services = ["), strConf);
    }


    @Test
    void theWildcardOverlayIsAnEntryRatherThanAnEmptyList() {
        String strConf = AuthOverlay.ofWildcard().render();

        assertTrue(strConf.contains("type = wildcard"), strConf);
        assertFalse(strConf.contains("url"), strConf);
        assertFalse(strConf.contains("secret"), strConf);
    }


    @Test
    void theHmacOverlayCarriesItsSecretInTheFileAndNeverInTheDescription() {
        AuthOverlay overlay = AuthOverlay.ofUnsafeHmac256("s3cr3t");

        assertTrue(overlay.render().contains("secret = \"s3cr3t\""), overlay.render());
        assertFalse(overlay.describe().contains("s3cr3t"), overlay.describe());
        assertFalse(overlay.toString().contains("s3cr3t"), overlay.toString());
    }


    @Test
    void aQuotedPathIsEscapedRatherThanClosingTheString() {
        String strConf = AuthOverlay.ofJwksAudience("file:///tmp/od\"d/jwks.json", "aud-x")
                .render();

        assertTrue(strConf.contains("od\\\"d"), strConf);
    }


    @Test
    void aBackslashIsDoubled() {
        String strConf = AuthOverlay.ofJwksAudience("file:///tmp/back\\slash/jwks.json", "aud-x")
                .render();

        assertTrue(strConf.contains("back\\\\slash"), strConf);
    }


    @Test
    void theAudienceHelperUsesTheDocumentedPrefix() {
        assertEquals("https://daml.com/jwt/aud/participant/sandbox",
                AuthOverlay.audienceForParticipant("sandbox"));
    }


    @Test
    void missingValuesAreRefusedAtConstructionRatherThanRenderedEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthOverlay.ofJwksAudience(null, "aud-x"));
        assertThrows(IllegalArgumentException.class,
                () -> AuthOverlay.ofJwksAudience(STR_URL, " "));
        assertThrows(IllegalArgumentException.class, () -> AuthOverlay.ofJwksScope(STR_URL, null));
        assertThrows(IllegalArgumentException.class, () -> AuthOverlay.ofUnsafeHmac256(""));
        assertThrows(IllegalArgumentException.class,
                () -> AuthOverlay.audienceForParticipant(null));
    }


    @Test
    void describeSaysWhichShapeTheParticipantWillDemand() {
        assertTrue(AuthOverlay.ofJwksAudience(STR_URL, "aud-x").describe()
                .contains("audience-based"));
        assertTrue(AuthOverlay.ofJwksScope(STR_URL, "sc").describe().contains("scope-based"));
        assertEquals("wildcard", AuthOverlay.ofWildcard().describe());
    }

}
