// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.jwt;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generating half of the JWKS story.
 *
 * The assertion that matters most is not that a file was written but that the
 * PUBLIC document has no private members in it. A public JWKS that leaked `d`
 * would work perfectly - the participant would verify tokens and every test
 * about behaviour would pass - while handing the signing key to whoever could
 * read the participant's configured url.
 *
 * Author Claude/bentzn
 */
class JwksMaterialTest {

    private static final Duration TTL = Duration.ofMinutes(5);


    @Test
    void generateProducesAPrivateRsaKeyWithTheRequestedId() {
        JwksMaterial material = JwksMaterial.generate("k-1");

        assertEquals("k-1", material.idKey());
        assertTrue(material.jwk().isPrivate());
        assertTrue(material.describe().contains("k-1"));
    }


    @Test
    void aBlankKeyIdTakesTheDefault() {
        assertEquals(JwksMaterial.STR_DEFAULT_KEY_ID, JwksMaterial.generate("  ").idKey());
        assertEquals(JwksMaterial.STR_DEFAULT_KEY_ID, JwksMaterial.generate(null).idKey());
    }


    @Test
    void thePublicDocumentCarriesNoPrivateMembers() throws Exception {
        JwksMaterial material = JwksMaterial.generate("k-2");
        String strPublic = material.renderPublicJwks();

        JWKSet setPublic = JWKSet.parse(strPublic);
        assertEquals(1, setPublic.getKeys().size());
        assertFalse(setPublic.getKeys().get(0).isPrivate());

        // Named individually rather than trusting isPrivate() alone: the CRT
        // members are the ones a hand-rolled encoder forgets to strip.
        String[] arrForbidden = new String[] { "\"d\"", "\"p\"", "\"q\"", "\"dp\"", "\"dq\"",
                "\"qi\"" };
        for (int idxName = 0; idxName < arrForbidden.length; idxName++) {
            assertFalse(strPublic.contains(arrForbidden[idxName]),
                    "public JWKS carries " + arrForbidden[idxName]);
        }

        assertTrue(JWKSet.parse(material.renderPrivateJwks()).getKeys().get(0).isPrivate());
    }


    @Test
    void ensureWritesOnceAndLoadsAfterwards(@TempDir Path dirTemp) {
        Path fileJwks = dirTemp.resolve("keys").resolve("jwks-private.json");

        JwksMaterial first = JwksMaterial.ensure(fileJwks, "k-3");
        assertTrue(Files.isRegularFile(fileJwks));

        JwksMaterial second = JwksMaterial.ensure(fileJwks, "ignored-on-this-path");
        assertEquals(first.idKey(), second.idKey());
        assertEquals("k-3", second.idKey());
    }


    @Test
    void aTokenMintedFromTheMaterialVerifiesAgainstItsOwnPublicKey() throws Exception {
        JwksMaterial material = JwksMaterial.generate("k-4");
        TokenSpec spec = TokenSpec.fromProfile("test", null,
                "https://daml.com/jwt/aud/participant/sandbox", "alice", TTL);

        String strToken = material.minter().mint(spec);
        SignedJWT jwt = SignedJWT.parse(strToken);

        RSAKey jwkPublic = ((RSAKey) material.jwk()).toPublicJWK();
        assertTrue(jwt.verify(new RSASSAVerifier(jwkPublic)));
        assertEquals("k-4", jwt.getHeader().getKeyID());
        assertEquals("alice", jwt.getJWTClaimsSet().getSubject());
    }


    @Test
    void theWrittenPrivateFileIsWhatTheTokenSourceReads(@TempDir Path dirTemp) {
        JwksMaterial material = JwksMaterial.generate("k-5");
        Path fileJwks = material.writePrivateJwks(dirTemp.resolve("jwks-private.json"));

        TokenSpec spec = TokenSpec.fromProfile("test", null, "aud-x", "bob", TTL);
        JwksFileTokenSource source = new JwksFileTokenSource(fileJwks, "k-5", spec);

        String strToken = source.token();
        assertNotNull(strToken);
        assertEquals(3, strToken.split("[.]").length);
        assertTrue(source.describe().contains("k-5"));
    }


    @Test
    void urlForIsAnAbsoluteFileUrl(@TempDir Path dirTemp) {
        JwksMaterial material = JwksMaterial.generate("k-6");
        Path filePublic = material.writePublicJwks(dirTemp.resolve("jwks.json"));

        String strUrl = JwksMaterial.urlFor(filePublic);
        assertTrue(strUrl.startsWith("file:"), strUrl);
        assertTrue(strUrl.endsWith("jwks.json"), strUrl);
    }


    @Test
    void aFileWithoutAPrivateKeyIsRefused(@TempDir Path dirTemp) {
        JwksMaterial material = JwksMaterial.generate("k-7");
        Path filePublic = material.writePublicJwks(dirTemp.resolve("jwks.json"));

        TokenException ex = assertThrows(TokenException.class,
                () -> JwksMaterial.load(filePublic));
        assertTrue(ex.getMessage().contains("no private RSA key"), ex.getMessage());
    }


    @Test
    void anAbsentFileIsRefusedByName(@TempDir Path dirTemp) {
        Path fileAbsent = dirTemp.resolve("nothing-here.json");
        TokenException ex = assertThrows(TokenException.class,
                () -> JwksMaterial.load(fileAbsent));
        assertTrue(ex.getMessage().contains("nothing-here.json"), ex.getMessage());
    }

}
