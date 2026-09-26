// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Author Claude/bentzn
 */
class TokenSourceTest {

    private static final String KID = "workbench-test";
    private static final Duration TTL = Duration.ofMinutes(5);

    @TempDir
    Path dirTmp;

    private Path fileJwks;
    private Path filePublicOnly;


    @BeforeEach
    void keys() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID(KID).algorithm(JWSAlgorithm.RS256).generate();

        // The JWKS JSON is assembled explicitly rather than via JWKSet.toString(),
        // which emits PUBLIC keys only. Writing a "private" JWKS that way yields
        // a file the source correctly rejects as public-only, and the resulting
        // failure looks like a parser bug rather than a test bug.
        fileJwks = dirTmp.resolve("jwks-private.json");
        Files.writeString(fileJwks, "{\"keys\":[" + key.toJSONString() + "]}");

        filePublicOnly = dirTmp.resolve("jwks-public.json");
        Files.writeString(filePublicOnly, "{\"keys\":[" + key.toPublicJWK().toJSONString() + "]}");
    }


    @Test
    void theTestFixtureReallyHoldsAPrivateKey() throws Exception {
        // Everything else here depends on this file carrying private material.
        // If it silently did not, the public-only rejection test would pass for
        // the wrong reason and every minting test would fail confusingly.
        assertTrue(JWKSet.parse(Files.readString(fileJwks)).getKeyByKeyId(KID).isPrivate());
        assertFalse(JWKSet.parse(Files.readString(filePublicOnly)).getKeyByKeyId(KID).isPrivate());
    }


    private JWTClaimsSet claimsOf(String strToken) throws Exception {
        return SignedJWT.parse(strToken).getJWTClaimsSet();
    }


    // ---------------------------------------------------------------- shapes

    @Test
    void audienceShapeCarriesAudienceAndNoScope() throws Exception {
        TokenSpec spec = TokenSpec.fromProfile("p", null,
                "https://daml.com/jwt/aud/participant/p1", "alice", TTL);
        assertEquals(TokenShape.AUDIENCE, spec.shape());

        JWTClaimsSet claims = claimsOf(new JwksFileTokenSource(fileJwks, KID, spec).token());
        assertEquals(List.of("https://daml.com/jwt/aud/participant/p1"), claims.getAudience());
        assertEquals("alice", claims.getSubject());
        assertNull(claims.getClaim("scope"));
        assertNull(claims.getClaim(JwtMinter.CLAIM_LEDGER_API));
    }


    @Test
    void scopeShapeCarriesScope() throws Exception {
        TokenSpec spec = TokenSpec.fromProfile("p", "daml_ledger_api", null, "alice", TTL);
        assertEquals(TokenShape.SCOPE, spec.shape());

        JWTClaimsSet claims = claimsOf(new JwksFileTokenSource(fileJwks, KID, spec).token());
        assertEquals("daml_ledger_api", claims.getClaim("scope"));
        assertNull(claims.getClaim(JwtMinter.CLAIM_LEDGER_API));
    }


    /**
     * admin and the two party arrays are always written, empty if unset,
     * because Canton reads the claim's shape rather than probing for presence.
     */
    @Test
    void customShapeAlwaysWritesAdminAndBothPartyArrays() throws Exception {
        TokenSpec spec = TokenSpec.fromProfile("p", null, null, "alice", TTL);
        assertEquals(TokenShape.CUSTOM, spec.shape());

        JWTClaimsSet claims = claimsOf(new JwksFileTokenSource(fileJwks, KID, spec).token());
        Object raw = claims.getClaim(JwtMinter.CLAIM_LEDGER_API);
        assertNotNull(raw, "the legacy Daml claim must be present");

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) raw;
        assertTrue(map.containsKey("actAs"));
        assertTrue(map.containsKey("readAs"));
        assertEquals(Boolean.FALSE, map.get("admin"));
    }


    @Test
    void customShapeCarriesPartiesWhenGiven() throws Exception {
        TokenSpec spec = new TokenSpec(TokenShape.CUSTOM, "alice", null, null, null, TTL,
                List.of("Bank"), List.of("Alice"), true, "workbench", null, "participant1");

        JWTClaimsSet claims = claimsOf(new JwksFileTokenSource(fileJwks, KID, spec).token());
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) claims.getClaim(JwtMinter.CLAIM_LEDGER_API);

        assertEquals(List.of("Bank"), map.get("actAs"));
        assertEquals(List.of("Alice"), map.get("readAs"));
        assertEquals(Boolean.TRUE, map.get("admin"));
        assertEquals("workbench", map.get("applicationId"));
        assertEquals("participant1", map.get("participantId"));
    }


    // ------------------------------------------------------- mutual exclusion

    @Test
    void profileSettingBothScopeAndAudienceIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> TokenSpec.fromProfile("Shared DEV", "daml_ledger_api",
                        "https://daml.com/jwt/aud/participant/p1", "alice", TTL));
        assertTrue(ex.getMessage().contains("Shared DEV"), "the message must name the profile");
    }


    @Test
    void audienceShapeRequiresAnAudience() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenSpec(TokenShape.AUDIENCE, "alice", null, null, null, TTL,
                        null, null, false, null, null, null));
    }


    @Test
    void scopeShapeRequiresAScope() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenSpec(TokenShape.SCOPE, "alice", null, null, "  ", TTL,
                        null, null, false, null, null, null));
    }


    // --------------------------------------------------------------- leakage

    /**
     * describe() reaches the status bar and the logs, so it must not contain
     * the token. Asserted by substring scan rather than by eye: a prefix or a
     * fragment leaks just as effectively as the whole thing.
     */
    @Test
    void staticSourceDescriptionContainsNoFragmentOfTheToken() throws Exception {
        TokenSpec spec = TokenSpec.fromProfile("p", "daml_ledger_api", null, "alice", TTL);
        String strToken = new JwksFileTokenSource(fileJwks, KID, spec).token();

        StaticTokenSource src = new StaticTokenSource(strToken);
        String strDesc = src.describe();

        assertEquals(strToken, src.token());
        assertFalse(strDesc.contains(strToken));
        for (String part : strToken.split("\\.")) {
            assertFalse(strDesc.contains(part), "describe() leaked a token segment");
        }
        for (int idx = 0; idx + 12 <= strToken.length(); idx += 4) {
            assertFalse(strDesc.contains(strToken.substring(idx, idx + 12)),
                    "describe() leaked a 12-char run of the token at " + idx);
        }
    }


    @Test
    void jwksSourceDescriptionContainsNoKeyMaterial() throws Exception {
        TokenSpec spec = TokenSpec.fromProfile("p", null, "aud1", "alice", TTL);
        JwksFileTokenSource src = new JwksFileTokenSource(fileJwks, KID, spec);

        String strDesc = src.describe();
        String strJwks = Files.readString(fileJwks);

        assertTrue(strDesc.contains(KID));
        assertFalse(strDesc.contains(src.token()));

        RSAKey key = (RSAKey) JWKSet.parse(strJwks).getKeyByKeyId(KID);
        assertFalse(strDesc.contains(key.getPrivateExponent().toString()));
        assertFalse(strDesc.contains(key.getModulus().toString()));
    }


    @Test
    void unparseableStaticTokenStillDescribesSafely() {
        StaticTokenSource src = new StaticTokenSource("not-a-jwt-at-all");
        assertFalse(src.describe().contains("not-a-jwt-at-all"));
    }


    // ----------------------------------------------------------- key handling

    @Test
    void publicOnlyKeyIsRejected() {
        TokenSpec spec = TokenSpec.fromProfile("p", null, "aud1", "alice", TTL);
        TokenException ex = assertThrows(TokenException.class,
                () -> new JwksFileTokenSource(filePublicOnly, KID, spec));
        assertTrue(ex.getMessage().contains("public-only"));
    }


    @Test
    void unknownKeyIdIsRejected() {
        TokenSpec spec = TokenSpec.fromProfile("p", null, "aud1", "alice", TTL);
        assertThrows(TokenException.class,
                () -> new JwksFileTokenSource(fileJwks, "no-such-kid", spec));
    }


    @Test
    void missingFileIsRejected() {
        TokenSpec spec = TokenSpec.fromProfile("p", null, "aud1", "alice", TTL);
        assertThrows(TokenException.class,
                () -> new JwksFileTokenSource(dirTmp.resolve("absent.json"), KID, spec));
    }


    @Test
    void tokenIsCachedWithinItsLifetime() {
        TokenSpec spec = TokenSpec.fromProfile("p", null, "aud1", "alice", TTL);
        JwksFileTokenSource src = new JwksFileTokenSource(fileJwks, KID, spec);
        assertEquals(src.token(), src.token(), "a valid token should be reused, not re-minted");
    }


    @Test
    void emptyStaticTokenIsRejected() {
        assertThrows(TokenException.class, () -> new StaticTokenSource("   "));
    }

}
