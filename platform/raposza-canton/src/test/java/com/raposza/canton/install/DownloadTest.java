// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>Against a real server on a real socket</h2>
 *
 * The behaviours that matter here - a redirect chain, a 404 body, a truncated
 * response - are properties of an HTTP conversation, and a stubbed client would
 * assert the stub. The server binds an ephemeral port on the loopback.
 *
 * Author Claude/bentzn
 */
class DownloadTest {

    private static final byte[] ARR_BODY = "canton-open-source, pretend"
            .getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dirTemp;

    private HttpServer server;

    private String strBase;


    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        strBase = "http://127.0.0.1:" + server.getAddress().getPort();

        server.createContext("/asset", exch -> {
            exch.sendResponseHeaders(200, ARR_BODY.length);
            try (OutputStream out = exch.getResponseBody()) {
                out.write(ARR_BODY);
            }
        });

        // What a release download actually does: the tag URL redirects to a
        // storage host and the asset is served from there.
        server.createContext("/releases", exch -> {
            exch.getResponseHeaders().add("Location", strBase + "/asset");
            exch.sendResponseHeaders(302, -1);
            exch.close();
        });

        server.createContext("/missing", exch -> {
            byte[] arrPage = "<html>Not Found</html>".getBytes(StandardCharsets.UTF_8);
            exch.sendResponseHeaders(404, arrPage.length);
            try (OutputStream out = exch.getResponseBody()) {
                out.write(arrPage);
            }
        });

        // Declares more than it sends, then closes.
        server.createContext("/short", exch -> {
            exch.sendResponseHeaders(200, 4096);
            try (OutputStream out = exch.getResponseBody()) {
                out.write(ARR_BODY);
            }
        });

        server.start();
    }


    @AfterEach
    void tearDown() {
        server.stop(0);
    }


    @Test
    void aShortTextEndpointIsReadWhole() throws IOException {
        assertEquals(new String(ARR_BODY, StandardCharsets.UTF_8),
                Download.strFetch(strBase + "/asset"));
    }


    @Test
    void aTextReadOfANotFoundIsAFailureRatherThanTheErrorPage() {
        IOException ex = assertThrows(IOException.class,
                () -> Download.strFetch(strBase + "/missing"));

        assertTrue(ex.getMessage().contains("404"), ex.getMessage());
    }


    @Test
    void aFetchWritesTheBodyAndReportsItsLength() throws IOException {
        Path fileOut = dirTemp.resolve("asset.tar.gz");

        long cntBytes = Download.fetch(strBase + "/asset", fileOut, null);

        assertEquals(ARR_BODY.length, cntBytes);
        assertEquals(ARR_BODY.length, Files.size(fileOut));
    }


    @Test
    void aRedirectIsFollowed() throws IOException {
        Path fileOut = dirTemp.resolve("viaRedirect.tar.gz");

        assertEquals(ARR_BODY.length, Download.fetch(strBase + "/releases", fileOut, null));
    }


    @Test
    void theDestinationDirectoryIsCreated() throws IOException {
        Path fileOut = dirTemp.resolve("a").resolve("b").resolve("asset.tar.gz");

        Download.fetch(strBase + "/asset", fileOut, null);

        assertTrue(Files.isRegularFile(fileOut));
    }


    /**
     * The failure this prevents: a 404 page written under the artefact's name,
     * reported as success, and discovered at extract time as an unrecognised
     * archive.
     */
    @Test
    void aNotFoundIsAFailureAndLeavesNoFile() {
        Path fileOut = dirTemp.resolve("gone.tar.gz");

        IOException ex = assertThrows(IOException.class,
                () -> Download.fetch(strBase + "/missing", fileOut, null));

        assertTrue(ex.getMessage().contains("404"), ex.getMessage());
        assertFalse(Files.exists(fileOut));
    }


    @Test
    void aTruncatedBodyIsAFailureAndLeavesNoFile() {
        Path fileOut = dirTemp.resolve("short.tar.gz");

        assertThrows(IOException.class, () -> Download.fetch(strBase + "/short", fileOut, null));
        assertFalse(Files.exists(fileOut));
        assertFalse(Files.exists(dirTemp.resolve("short.tar.gz.part")));
    }


    /** Nothing is left behind under the real name OR the partial one. */
    @Test
    void noPartialFileSurvivesASuccessfulFetch() throws IOException {
        Path fileOut = dirTemp.resolve("asset.tar.gz");

        Download.fetch(strBase + "/asset", fileOut, null);

        assertFalse(Files.exists(dirTemp.resolve("asset.tar.gz.part")));
    }


    @Test
    void progressIsReportedWithTheDeclaredTotal() throws IOException {
        List<Long> lstSeen = new ArrayList<>();
        AtomicLong cntTotalSeen = new AtomicLong(0L);

        Download.fetch(strBase + "/asset", dirTemp.resolve("asset.tar.gz"),
                (cntBytes, cntTotal) -> {
                    lstSeen.add(cntBytes);
                    cntTotalSeen.set(cntTotal);
                });

        assertFalse(lstSeen.isEmpty());
        assertEquals(ARR_BODY.length, lstSeen.get(lstSeen.size() - 1).longValue());
        assertEquals(ARR_BODY.length, cntTotalSeen.get());
    }


    @Test
    void anUnreachableHostFailsRatherThanWriting() {
        Path fileOut = dirTemp.resolve("nothing.tar.gz");

        assertThrows(IOException.class,
                () -> Download.fetch("http://127.0.0.1:1/asset", fileOut, null));
        assertFalse(Files.exists(fileOut));
    }

}
