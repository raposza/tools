// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Scribe's client at the window's provider - raposza/tools#4. A loopback
 * stand-in for `/api/ui/clients` answers whether the registry is strict and
 * records what was posted.
 *
 * Author Claude/bentzn
 */
class MintRegistrationScribeTest {

    private HttpServer server;

    private final AtomicReference<String> refPosted = new AtomicReference<>();


    @AfterEach
    void tearDown() {
        if (server != null)
            server.stop(0);
    }


    @Test
    void anOpenProviderIsLeftOpen() throws Exception {
        String strUrl = start(false);
        String strLine = MintRegistration.strRegisterScribe(strUrl, "pqs-user", "s3cret");

        assertTrue(strLine.contains("checks no clients"), strLine);
        assertNull(refPosted.get(), "registering into an open registry closes it for everyone");
    }


    @Test
    void aStrictProviderGetsAConfidentialClient() throws Exception {
        String strUrl = start(true);
        String strLine = MintRegistration.strRegisterScribe(strUrl, "pqs-user", "s3cret");

        assertEquals("{\"client_id\":\"pqs-user\",\"secret\":\"s3cret\",\"redirect_uris\":\""
                + MintRegistration.STR_REDIRECT_NONE + "\"}", refPosted.get());
        assertFalse(strLine.contains("s3cret"), "the secret never reaches a log line");
    }


    @Test
    void aSecretIsNewEachTimeAndUrlSafe() {
        String strOne = MintRegistration.strSecretNew();
        String strTwo = MintRegistration.strSecretNew();

        assertNotEquals(strOne, strTwo);
        assertTrue(strOne.matches("[A-Za-z0-9_-]{32}"), strOne);
    }


    private String start(boolean flagStrict) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(MintRegistration.STR_PATH_CLIENTS, exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                refPosted.set(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
            }
            byte[] arrBody = ("{\"strict\":" + flagStrict + ",\"clients\":[]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, arrBody.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(arrBody);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
