// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.idp;

import com.raposza.jwt.JwksMaterial;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole round trip, over real HTTP, against a real socket.
 *
 * This suite is NOT gated. It stages nothing, needs no Canton and no network
 * beyond loopback, so there is no version to name and no reason for it to be
 * opt-in - which is the argument for building the provider rather than staging
 * one.
 *
 * The assertion that matters is the last step of the token test: the JWT that
 * comes back verifies against the key served at the JWKS endpoint. Anything
 * less - a 200, a well-formed body, three dot-separated segments - would pass
 * just as happily if the endpoint published one key and signed with another,
 * and that failure reaches a participant as an authentication refusal that
 * reads like a wrong audience.
 *
 * Author Claude/bentzn
 */
class TestIdpTest {

    private static final String STR_CLIENT_ID = "raposza-pqs";

    private static final String STR_SECRET = "not-a-real-secret";

    private static final String STR_SUBJECT = "raposza-pqs";

    private static final String STR_AUDIENCE = "https://daml.com/jwt/aud/participant/sandbox";

    private static final Duration TTL = Duration.ofMinutes(5);

    private static JwksMaterial material;

    private static TestIdp idp;

    private static HttpClient client;


    @BeforeAll
    static void startTheProvider() {
        material = JwksMaterial.generate("raposza-test-idp");
        idp = new TestIdp(material)
                .register(IdpClient.ofAudience(STR_CLIENT_ID, STR_SECRET, STR_SUBJECT,
                        STR_AUDIENCE).withTtl(TTL))
                .start();
        client = HttpClient.newHttpClient();
    }


    @AfterAll
    static void stopTheProvider() {
        if (idp != null)
            idp.stop();
    }


    /**
     * The one that settles it: signed by the key that is published, and
     * carrying the shape the participant is configured for.
     */
    @Test
    void aTokenFromTheEndpointVerifiesAgainstTheKeyTheEndpointPublishes() throws Exception {
        HttpResponse<String> rspToken = post(form("grant_type", TestIdp.STR_GRANT_CLIENT_CREDENTIALS,
                "client_id", STR_CLIENT_ID, "client_secret", STR_SECRET));
        assertEquals(200, rspToken.statusCode(), rspToken.body());

        assertEquals("Bearer", member(rspToken.body(), "token_type"));
        assertTrue(rspToken.body().contains("\"expires_in\": " + TTL.toSeconds()),
                "the lifetime the client is told does not match the one it was registered with:\n"
                        + rspToken.body());

        String strToken = member(rspToken.body(), "access_token");
        assertNotNull(strToken);
        assertTrue(idp.cntTokenIssued() > 0);

        RSAKey keyPublished = published();
        SignedJWT jwt = SignedJWT.parse(strToken);
        assertTrue(jwt.verify(new RSASSAVerifier(keyPublished)),
                "the token does not verify against the published key, so a participant reading"
                        + " the JWKS endpoint would refuse it");

        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertEquals(STR_SUBJECT, claims.getSubject());
        assertEquals(List.of(STR_AUDIENCE), claims.getAudience());
        assertEquals(idp.strUrlIssuer(), claims.getIssuer());
        assertNotNull(claims.getExpirationTime());
        assertEquals(material.idKey(), jwt.getHeader().getKeyID());
    }


    /**
     * The endpoint publishes the PUBLIC half. A private member here is a key
     * leak that every test above would still pass.
     */
    @Test
    void theJwksEndpointCarriesNoPrivateMaterial() throws Exception {
        HttpResponse<String> rsp = get(idp.strUrlJwks());
        assertEquals(200, rsp.statusCode());
        assertFalse(rsp.body().contains("\"d\""), "the JWKS endpoint served a private key");
        assertTrue(idp.cntJwksRequest() > 0);

        RSAKey keyPublished = published();
        assertFalse(keyPublished.isPrivate());
        assertEquals(material.idKey(), keyPublished.getKeyID());
    }


    /** RFC 6749 allows either; scribe's choice between them is not measured. */
    @Test
    void clientSecretBasicIsAcceptedBesideClientSecretPost() throws Exception {
        String strBasic = Base64.getEncoder().encodeToString(
                (STR_CLIENT_ID + ":" + STR_SECRET).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(idp.strUrlToken()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + strBasic)
                .POST(HttpRequest.BodyPublishers.ofString(
                        form("grant_type", TestIdp.STR_GRANT_CLIENT_CREDENTIALS)))
                .build();

        HttpResponse<String> rsp = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, rsp.statusCode(), rsp.body());
        assertNotNull(member(rsp.body(), "access_token"));
    }


    @Test
    void aWrongSecretAndAnUnknownClientGetTheSameAnswer() throws Exception {
        HttpResponse<String> rspWrong = post(form("grant_type",
                TestIdp.STR_GRANT_CLIENT_CREDENTIALS, "client_id", STR_CLIENT_ID,
                "client_secret", "wrong"));
        HttpResponse<String> rspUnknown = post(form("grant_type",
                TestIdp.STR_GRANT_CLIENT_CREDENTIALS, "client_id", "nobody",
                "client_secret", STR_SECRET));

        assertEquals(401, rspWrong.statusCode());
        assertEquals(401, rspUnknown.statusCode());
        assertEquals("invalid_client", member(rspWrong.body(), "error"));
        assertEquals(rspWrong.body(), rspUnknown.body(),
                "the two answers differ, so the endpoint is a client id oracle");
    }


    @Test
    void onlyClientCredentialsIsGranted() throws Exception {
        HttpResponse<String> rsp = post(form("grant_type", "password", "client_id", STR_CLIENT_ID,
                "client_secret", STR_SECRET, "username", "someone", "password", "something"));

        assertEquals(400, rsp.statusCode());
        assertEquals("unsupported_grant_type", member(rsp.body(), "error"));
    }


    @Test
    void theTokenEndpointRefusesGet() throws Exception {
        assertEquals(405, get(idp.strUrlToken()).statusCode());
    }


    @Test
    void theDiscoveryDocumentNamesBothEndpoints() throws Exception {
        HttpResponse<String> rsp = get(idp.strUrlIssuer() + TestIdp.STR_PATH_DISCOVERY);

        assertEquals(200, rsp.statusCode());
        assertEquals(idp.strUrlIssuer(), member(rsp.body(), "issuer"));
        assertEquals(idp.strUrlToken(), member(rsp.body(), "token_endpoint"));
        assertEquals(idp.strUrlJwks(), member(rsp.body(), "jwks_uri"));
    }


    /**
     * The endpoint and the direct call are the same minter and the same spec.
     * A caller that holds the registration should never need the round trip,
     * and should never get a different token if it takes it.
     */
    @Test
    void theDirectMintAgreesWithTheEndpoint() throws Exception {
        SignedJWT jwt = SignedJWT.parse(idp.mint(STR_CLIENT_ID));

        assertTrue(jwt.verify(new RSASSAVerifier(published())));
        assertEquals(STR_SUBJECT, jwt.getJWTClaimsSet().getSubject());
        assertEquals(List.of(STR_AUDIENCE), jwt.getJWTClaimsSet().getAudience());
        assertThrows(IdpException.class, () -> idp.mint("nobody"));
    }


    /** A record's generated toString would print the secret. */
    @Test
    void nothingPrintableCarriesTheSecret() {
        IdpClient registration = IdpClient.ofAudience(STR_CLIENT_ID, STR_SECRET, STR_SUBJECT,
                STR_AUDIENCE);

        assertFalse(registration.toString().contains(STR_SECRET));
        assertFalse(registration.describe().contains(STR_SECRET));
        assertFalse(idp.toString().contains(STR_SECRET));
        assertFalse(idp.describe().contains(STR_SECRET));
    }


    /**
     * One shape or the other, and the refusal is at registration rather than at
     * the first authentication failure.
     */
    @Test
    void aClientCarriesExactlyOneShape() {
        assertThrows(IdpException.class, () -> new IdpClient(STR_CLIENT_ID, STR_SECRET,
                STR_SUBJECT, STR_AUDIENCE, "daml_ledger_api", TTL));
        assertThrows(IdpException.class, () -> new IdpClient(STR_CLIENT_ID, STR_SECRET,
                STR_SUBJECT, null, null, TTL));
        assertThrows(IdpException.class, () -> IdpClient.ofAudience(STR_CLIENT_ID, " ",
                STR_SUBJECT, STR_AUDIENCE));

        assertTrue(IdpClient.ofAudience(STR_CLIENT_ID, STR_SECRET, STR_SUBJECT, STR_AUDIENCE)
                .isAudienceBased());
        assertFalse(IdpClient.ofScope(STR_CLIENT_ID, STR_SECRET, STR_SUBJECT, "daml_ledger_api")
                .isAudienceBased());
    }


    /**
     * Starting with no client would produce a provider that refuses everything
     * and says 401, which reads as a wrong secret rather than as an empty
     * registration.
     */
    @Test
    void aProviderWithNoClientRefusesToStart() {
        TestIdp idpEmpty = new TestIdp(material);

        assertThrows(IdpException.class, () -> idpEmpty.start());
        assertFalse(idpEmpty.isRunning());
        assertThrows(IdpException.class, () -> idpEmpty.port());
    }


    @Test
    void clientsCannotBeRegisteredAfterItStarts() {
        assertThrows(IdpException.class, () -> idp.register(
                IdpClient.ofAudience("late", STR_SECRET, STR_SUBJECT, STR_AUDIENCE)));
        assertThrows(IdpException.class, () -> idp.start());
    }


    @Test
    void theFormParserTakesWhatAnHttpClientSends() {
        assertEquals("a b", TestIdp.parseForm("k=a+b").get("k"));
        assertEquals("a b", TestIdp.parseForm("k=a%20b").get("k"));
        assertEquals("", TestIdp.parseForm("k=").get("k"));
        assertEquals("v=1", TestIdp.parseForm("k=v%3D1").get("k"));
        assertTrue(TestIdp.parseForm("").isEmpty());
        assertEquals(2, TestIdp.parseForm("a=1&b=2").size());
        assertThrows(IllegalArgumentException.class, () -> TestIdp.parseForm("k=%zz"));
    }


    private static RSAKey published() throws Exception {
        JWKSet set = JWKSet.parse(get(idp.strUrlJwks()).body());
        assertEquals(1, set.getKeys().size());
        return (RSAKey) set.getKeys().get(0);
    }


    private static HttpResponse<String> get(String strUrl) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(strUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }


    private static HttpResponse<String> post(String strForm) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(idp.strUrlToken()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(strForm)).build(),
                HttpResponse.BodyHandlers.ofString());
    }


    private static String form(String... arrPair) {
        StringBuilder sb = new StringBuilder();
        for (int idx = 0; idx + 1 < arrPair.length; idx += 2) {
            if (sb.length() > 0)
                sb.append('&');
            sb.append(arrPair[idx]).append('=')
                    .append(java.net.URLEncoder.encode(arrPair[idx + 1], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }


    /**
     * Reads one string member out of a small, known JSON body. A JSON parser
     * would be a dependency this module does not otherwise need, and the bodies
     * under test are four members written by one method in the class under
     * test.
     */
    private static String member(String strJson, String strKey) {
        String strNeedle = "\"" + strKey + "\": \"";
        int nStart = strJson.indexOf(strNeedle);
        if (nStart < 0)
            return null;

        nStart += strNeedle.length();
        int nEnd = strJson.indexOf('"', nStart);
        return nEnd < 0 ? null : strJson.substring(nStart, nEnd);
    }

}
