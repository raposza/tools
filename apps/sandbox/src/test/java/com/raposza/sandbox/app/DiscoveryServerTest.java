// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.jwt.TokenShape;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The endpoint itself, on an ephemeral port. B-8.
 *
 * NO FIXED PORT ANYWHERE HERE. A test that bound 31011 would fail on the one
 * machine where the thing being tested is already running, which is the
 * developer's own - so it binds 0 and asks what it got.
 *
 * What is asserted is the shape of the contract rather than the document: GET
 * on the one path answers, everything else is refused, a port already taken is
 * reported as an exception rather than swallowed, and a stop actually unbinds.
 *
 * Author Claude/bentzn
 */
class DiscoveryServerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final DiscoveryServer server = new DiscoveryServer();

    private final DiscoveryServer serverOther = new DiscoveryServer();


    @AfterEach
    void unbind() {
        server.stop();
        serverOther.stop();
    }


    @Test
    void a_get_on_the_root_answers_with_the_document() throws Exception {
        server.start(0, DiscoveryServerTest::docStopped);
        assertTrue(server.isRunning());
        assertTrue(server.nPort() > 0, "an ephemeral bind reports the port it got");

        HttpResponse<String> response = get(DiscoveryServer.strUrlOf(server.nPort()));
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(DiscoveryDoc.STR_PRODUCT), response.body());
        assertTrue(response.headers().firstValue("content-type").orElse("")
                .startsWith("application/json"), "a consumer parses it, so it says so");
    }


    /** Read-only, and one path. Anything else is a mistake worth reporting. */
    @Test
    void another_path_is_refused() throws Exception {
        server.start(0, DiscoveryServerTest::docStopped);

        HttpResponse<String> response = get("http://127.0.0.1:" + server.nPort() + "/status");
        assertEquals(404, response.statusCode());
    }


    @Test
    void a_post_is_refused() throws Exception {
        server.start(0, DiscoveryServerTest::docStopped);

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(DiscoveryServer.strUrlOf(server.nPort())))
                        .timeout(TIMEOUT).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
    }


    /**
     * THE CONFLICT THE OPERATOR IS TOLD ABOUT. It has to arrive as an exception
     * the window can catch; a bind that failed quietly would leave a Settings
     * tab naming a port nothing is listening on.
     */
    @Test
    void a_port_already_bound_is_reported() throws Exception {
        server.start(0, DiscoveryServerTest::docStopped);
        int nPortTaken = server.nPort();

        assertThrows(IOException.class,
                () -> serverOther.start(nPortTaken, DiscoveryServerTest::docStopped));
    }


    /** A rebind on a new port leaves nothing behind on the old one. */
    @Test
    void a_stop_unbinds() throws Exception {
        server.start(0, DiscoveryServerTest::docStopped);
        int nPortWas = server.nPort();
        server.stop();

        assertEquals(0, server.nPort());
        assertFalse(server.isRunning());
        // The proof is that the port can be taken again.
        serverOther.start(nPortWas, DiscoveryServerTest::docStopped);
        assertEquals(nPortWas, serverOther.nPort());
    }


    /** A window that has not built its first snapshot says so. */
    @Test
    void no_snapshot_yet_is_a_503() throws Exception {
        server.start(0, () -> null);

        HttpResponse<String> response = get(DiscoveryServer.strUrlOf(server.nPort()));
        assertEquals(503, response.statusCode());
    }


    private static HttpResponse<String> get(String strUrl) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(strUrl)).timeout(TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertNotNull(response);
        return response;
    }


    private static DiscoveryDoc docStopped() {
        return new DiscoveryDoc("STOPPED", 0, DiscoveryDoc.STR_TOPOLOGY_SANDBOX, "3.5.12",
                "OPEN_SOURCE", Path.of("/run"), null, false,
                DiscoveryDoc.STR_MODE_EMBEDDED, null, List.of());
    }
}
