// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server end to end, against a bundle and an upstream this test builds.
 *
 * RAW SOCKETS, NOT HttpClient. Every case here turns on the Host header, and
 * java.net.http refuses to set it without a JVM-wide system property. A
 * hand-written request line is smaller than that and says exactly what is on
 * the wire.
 *
 * PORT 0 IS WHY LocalNetWeb REPORTS ITS BOUND PORTS. A test that took 2000
 * would fight a real LocalNet on the developer's own machine and fail for a
 * reason that has nothing to do with the code.
 *
 * Author Claude/bentzn
 */
class LocalNetWebTest {

    @Test
    void servesProxiesAndLogs() throws Exception {
        Path dirTemp = Files.createTempDirectory("localnet-web");
        Path dirWebUis = dirTemp.resolve("web-uis");
        Path dirApp = dirWebUis.resolve("wallet");
        Files.createDirectories(dirApp.resolve("assets"));
        Files.writeString(dirApp.resolve("index.html"), "<html>WALLET</html>");
        Files.writeString(dirApp.resolve("assets").resolve("app.css"), "body{}");
        Path fileLog = dirTemp.resolve("web.log");

        HttpServer upstream = upstream();
        String strUp = "http://127.0.0.1:" + upstream.getAddress().getPort();

        LocalNetUi.Site site = new LocalNetUi.Site("app-user", 0, "app-user", List.of(
                new LocalNetUi.Vhost("wallet.localhost", "wallet",
                        "window.splice_config = {};\n",
                        List.of(new LocalNetUi.Route("/api/validator", strUp)), false),
                new LocalNetUi.Vhost("canton.localhost", null, null,
                        List.of(new LocalNetUi.Route("/", strUp)), true)));

        LocalNetWeb web = new LocalNetWeb("127.0.0.1", dirWebUis, List.of(site), fileLog);
        web.start();
        try {
            int nPort = web.lstPortBound().get(0).intValue();

            assertTrue(strGet(nPort, "wallet.localhost", "/").contains("WALLET"));
            assertTrue(strGet(nPort, "wallet.localhost", "/assets/app.css").contains("text/css"));
            assertTrue(strGet(nPort, "wallet.localhost", "/config.js").contains("splice_config"));

            // A deep link has no file behind it and must reach the app, not a
            // 404; an asset that is genuinely absent must NOT become index.html,
            // or a missing bundle file reads as a working page.
            assertTrue(strGet(nPort, "wallet.localhost", "/wallet/tx/123").contains("WALLET"));
            assertTrue(strGet(nPort, "wallet.localhost", "/assets/gone.css").contains(" 404 "));

            assertTrue(strGet(nPort, "wallet.localhost", "/api/validator/v0/who")
                    .contains("UP:/api/validator/v0/who"));
            String strCanton = strGet(nPort, "canton.localhost", "/v2/parties");
            assertTrue(strCanton.contains("UP:/v2/parties"));
            // LOWERCASED ON BOTH SIDES. The JDK's own Headers class normalises
            // every key it writes, so the CORS headers reach the wire as
            // Access-control-allow-origin. That is a legal spelling - header
            // names are case-insensitive - and asserting the canonical one
            // fails a server that is behaving correctly.
            assertTrue(strCanton.toLowerCase(Locale.ROOT)
                    .contains("access-control-allow-origin"));

            // An unmatched Host reaches the first vhost - the deliberate
            // divergence from nginx, asserted so it cannot drift back silently.
            assertTrue(strGet(nPort, "nothing.example", "/").contains("WALLET"));
        }
        finally {
            web.stop();
            upstream.stop(0);
        }

        String strLog = Files.readString(fileLog, StandardCharsets.UTF_8);
        assertTrue(strLog.contains("/config.js  config  200"), strLog);
        assertTrue(strLog.contains("static wallet  200"), strLog);
        assertTrue(strLog.contains("proxy " + strUp), strLog);
        assertTrue(strLog.contains("/assets/gone.css  static wallet  404"), strLog);
    }


    /**
     * `todo.md` L-12: the request log rotates. With the ceiling at 300 B a few
     * requests move `web.log` to `web.log.1` and begin a new one, and the live
     * file never grows past the ceiling plus one line.
     */
    @Test
    void theRequestLogRotates() throws Exception {
        Path dirTemp = Files.createTempDirectory("localnet-web-rotate");
        Path dirWebUis = dirTemp.resolve("web-uis");
        Files.createDirectories(dirWebUis.resolve("wallet"));
        Files.writeString(dirWebUis.resolve("wallet").resolve("index.html"),
                "<html>WALLET</html>");
        Path fileLog = dirTemp.resolve("web.log");
        Path fileRotated = dirTemp.resolve("web.log" + LocalNetWeb.STR_SUFFIX_ROTATED);

        LocalNetUi.Site site = new LocalNetUi.Site("app-user", 0, "app-user", List.of(
                new LocalNetUi.Vhost("wallet.localhost", "wallet",
                        "window.splice_config = {};\n", List.of(), false)));

        LocalNetWeb web = new LocalNetWeb("127.0.0.1", dirWebUis, List.of(site), fileLog, 300L);
        web.start();
        try {
            int nPort = web.lstPortBound().get(0).intValue();
            for (int cntReq = 0; cntReq < 20; cntReq++) {
                assertTrue(strGet(nPort, "wallet.localhost", "/").contains("WALLET"));
            }
        }
        finally {
            web.stop();
        }

        assertTrue(Files.isRegularFile(fileRotated), "no " + fileRotated);
        assertTrue(Files.size(fileRotated) >= 300L, "rotated at " + Files.size(fileRotated));
        assertTrue(Files.size(fileLog) < 300L + 200L, "the live log is " + Files.size(fileLog));
        assertTrue(Files.readString(fileRotated, StandardCharsets.UTF_8)
                .contains("static wallet"));
    }


    /**
     * THE POINT OF THE CHECK IS THAT IT RESOLVES NOTHING. Neither name below
     * exists in any resolver on the build machine, and both are measured
     * anyway - which is what makes the same call trustworthy on Windows, where
     * no .localhost name resolves and every page still opens.
     */
    @Test
    void checksEachPageWithoutResolvingItsName() throws Exception {
        Path dirTemp = Files.createTempDirectory("localnet-check");
        Path dirWebUis = dirTemp.resolve("web-uis");
        Files.createDirectories(dirWebUis.resolve("wallet"));
        Files.writeString(dirWebUis.resolve("wallet").resolve("index.html"),
                "<html>WALLET</html>");

        HttpServer upstream = upstream();
        String strUp = "http://127.0.0.1:" + upstream.getAddress().getPort();

        LocalNetUi.Site site = new LocalNetUi.Site("app-user", 0, "app-user", List.of(
                new LocalNetUi.Vhost("wallet.localhost", "wallet",
                        "window.splice_config = {};\n",
                        List.of(new LocalNetUi.Route("/api/validator", strUp)), false),
                new LocalNetUi.Vhost("ans.localhost", "ans", "window.splice_config = {};\n",
                        List.of(new LocalNetUi.Route("/api/validator", strUp)), false)));

        LocalNetWeb web = new LocalNetWeb("127.0.0.1", dirWebUis, List.of(site), null);
        web.start();
        List<LocalNetWeb.Check> lstCheck;
        try {
            lstCheck = web.lstCheck();
        }
        finally {
            web.stop();
            upstream.stop(0);
        }

        assertEquals(2, lstCheck.size());
        assertTrue(lstCheck.get(0).flagOk(), lstCheck.get(0).strNote());
        assertTrue(lstCheck.get(0).strNote().contains("index 200"), lstCheck.get(0).strNote());
        assertTrue(lstCheck.get(0).strNote().contains("config.js 200"),
                lstCheck.get(0).strNote());
        assertTrue(lstCheck.get(0).strNote().contains("/api/validator 200"),
                lstCheck.get(0).strNote());

        // A BUNDLE THAT IS NOT ON DISK MUST NOT READ AS A WORKING PAGE. This is
        // the case the operator hits with an incomplete zip, and it is the
        // whole reason the table says OK or FAULT rather than listing URLs.
        assertFalse(lstCheck.get(1).flagOk(), lstCheck.get(1).strNote());
        assertTrue(lstCheck.get(1).strNote().contains("index 404"), lstCheck.get(1).strNote());
    }


    /**
     * @return a started server that echoes the path it was asked for, so a
     *         forwarded request proves both that it arrived and that the path
     *         survived the hop
     */
    private HttpServer upstream() throws IOException {
        HttpServer server = HttpServer
                .create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] arrBody = ("UP:" + exchange.getRequestURI().getPath())
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, arrBody.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(arrBody);
            }
        });
        server.start();
        return server;
    }


    /**
     * @param nPort the bound UI port
     * @param strHost what to put in the Host header
     * @param strPath the request path
     * @return the whole response, status line and headers included
     */
    private String strGet(int nPort, String strHost, String strPath) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", nPort), 2000);
            socket.setSoTimeout(5000);
            String strRequest = "GET " + strPath + " HTTP/1.1\r\nHost: " + strHost
                    + "\r\nConnection: close\r\n\r\n";
            OutputStream stream = socket.getOutputStream();
            stream.write(strRequest.getBytes(StandardCharsets.UTF_8));
            stream.flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
