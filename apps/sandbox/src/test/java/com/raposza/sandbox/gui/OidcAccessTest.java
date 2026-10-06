// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.sandbox.app.JwtMintProcess;
import com.raposza.sandbox.app.ProviderUsers;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A valve line becomes a line a reader can use: who, what, the outcome, the
 * origin with its CORS verdict, and with what client - never the time taken,
 * never a stylesheet, and never the secret a mint query carries.
 *
 * THE CONTROL CAN FAIL: the mint line below carries `secret=` in its query,
 * so a formatter that echoed the query would put it in the assertion's way,
 * and the provider's own origin is on the sign-in line, so a formatter that
 * marked every Origin would put a CORS verdict where none belongs.
 *
 * Author Claude/bentzn
 */
class OidcAccessTest {

    private static final int N_PORT_OWN = 33301;


    @Test
    void aMintIsNamedForItsSubjectAndTheSecretStaysOut() {
        String strLine = OidcAccess.strLine("127.0.0.1|GET|/mint.txt|200|"
                + "?sub=ledger-api-user&ttlSeconds=3600&aud=https%3A%2F%2Fcanton.network.global"
                + "&alg=HS256&secret=s3cr3t|-|-|Java-http-client/21.0.9", N_PORT_OWN);
        assertEquals("127.0.0.1  token minted for ledger-api-user  ok  Java-http-client/21.0.9",
                strLine);
        assertFalse(strLine.contains("s3cr3t"));
    }


    @Test
    void aRefusedTokenRequestSaysFailedWithItsStatus() {
        assertEquals("10.0.0.7  token request  FAILED 401  curl/8.5.0",
                OidcAccess.strLine("10.0.0.7|POST|/oauth2/token|401||-|-|curl/8.5.0", N_PORT_OWN));
    }


    @Test
    void theOriginIsShownWithItsCorsVerdict() {
        assertEquals("127.0.0.1  token request  FAILED 401  origin http://localhost:3000"
                + "  CORS NOT ALLOWED  browser",
                OidcAccess.strLine("127.0.0.1|POST|/oauth2/token|401||http://localhost:3000|-|"
                        + "Mozilla/5.0", N_PORT_OWN));
        assertEquals("127.0.0.1  key set  ok  origin http://wallet.localhost  CORS allowed  browser",
                OidcAccess.strLine("127.0.0.1|GET|/oauth2/jwks|200||http://wallet.localhost|*|"
                        + "Mozilla/5.0", N_PORT_OWN));
        assertEquals("127.0.0.1  CORS preflight for token request  ok  origin http://a.localhost"
                + "  CORS allows http://b.localhost only  browser",
                OidcAccess.strLine("127.0.0.1|OPTIONS|/oauth2/token|200||http://a.localhost|"
                        + "http://b.localhost|Mozilla/5.0", N_PORT_OWN));
        // ITS OWN SIGN-IN FORM posts with its own origin, which is no CORS.
        assertEquals("127.0.0.1  POST /login  ok  browser",
                OidcAccess.strLine("127.0.0.1|POST|/login|302||http://localhost:33301|-|"
                        + "Mozilla/5.0", N_PORT_OWN));
    }


    @Test
    void stylesheetsFontsAndTheIconAreNotOidcAndAreDropped() {
        assertNull(OidcAccess.strLine("127.0.0.1|GET|/raposza/fonts.css|200||-|-|Mozilla/5.0",
                N_PORT_OWN));
        assertNull(OidcAccess.strLine("127.0.0.1|GET|/raposza/fonts/InterVariable.woff2|200||-|-|-",
                N_PORT_OWN));
        assertNull(OidcAccess.strLine("127.0.0.1|GET|/favicon.ico|404||-|-|-", N_PORT_OWN));
        assertEquals("127.0.0.1  admin UI  ok  browser",
                OidcAccess.strLine("127.0.0.1|GET|/ui|200||-|-|Mozilla/5.0 (X11; Linux x86_64)",
                        N_PORT_OWN));
        assertEquals("127.0.0.1  GET /.well-known/jwks.json  FAILED 404",
                OidcAccess.strLine("127.0.0.1|GET|/.well-known/jwks.json|404||-|-|-", N_PORT_OWN));
    }


    /**
     * The window's own read of the user list, every 3 s, is not a request
     * anybody made of the server. THE CONTROL CAN FAIL: the same request from
     * another client is kept, so a filter on the path alone would drop it.
     */
    @Test
    void theWindowsOwnUserReadIsDroppedAndAnotherClientsIsKept() {
        assertNull(OidcAccess.strLine("127.0.0.1|GET|/api/ui/users|200|-|-|-|"
                + ProviderUsers.STR_AGENT, N_PORT_OWN));
        assertEquals("127.0.0.1  admin GET users  ok  curl/8.5.0",
                OidcAccess.strLine("127.0.0.1|GET|/api/ui/users|200|-|-|-|curl/8.5.0",
                        N_PORT_OWN));
    }


    @Test
    void aLineNotInThePatternIsDropped() {
        assertNull(OidcAccess.strLine("2026-10-04 INFO started", N_PORT_OWN));
        assertNull(OidcAccess.strLine("", N_PORT_OWN));
        assertNull(OidcAccess.strLine("127.0.0.1|GET|/x|abc||-|-|-", N_PORT_OWN));
    }


    /**
     * THE PATTERN THE PROVIDER IS STARTED WITH IS THE ONE THIS READS: eight
     * fields, `|` between them, and the switches that make the file one file
     * written per request.
     */
    @Test
    void theProviderIsStartedWithThePatternThisReads() {
        List<String> lstArg = JwtMintProcess.lstArgAccessLog(Path.of("/tmp/raposza-oidc-1"));
        assertTrue(lstArg.contains("--server.tomcat.accesslog.enabled=true"), lstArg.toString());
        assertTrue(lstArg.contains("--server.tomcat.accesslog.rotate=false"), lstArg.toString());
        assertTrue(lstArg.contains("--server.tomcat.accesslog.buffered=false"), lstArg.toString());
        assertTrue(lstArg.contains("--server.tomcat.accesslog.pattern="
                + JwtMintProcess.STR_ACCESS_PATTERN), lstArg.toString());
        assertEquals(8, JwtMintProcess.STR_ACCESS_PATTERN.split("\\|").length);
        assertEquals(JwtMintProcess.STR_FILE_ACCESS, JwtMintProcess.fileAccessLog().getFileName()
                .toString());
    }

}
