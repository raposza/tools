// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raposza.canton.topology.SandboxPorts;
import com.raposza.jwt.TokenShape;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The RAWAR server against a real loopback socket and a stub JSON Ledger API:
 * mounts, the slash, `_env.json` with and without a stack and with and without
 * auth, the forward with its path, query, method, body and bearer intact, and
 * every refusal able to fire.
 *
 * Author Claude/bentzn
 */
class RawarServerTest {

    @TempDir
    Path dirTmp;

    private final RawarServer server = new RawarServer();

    private HttpServer stubLedger;

    private final AtomicReference<String> refSeen = new AtomicReference<>();

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).build();

    private static final ObjectMapper MAPPER = new ObjectMapper();


    @AfterEach
    void stop() {
        server.stop();
        if (stubLedger != null)
            stubLedger.stop(0);
    }


    private Path dirRawar(String strName, String strMount) throws IOException {
        Path dir = dirTmp.resolve("rawars").resolve(strName);
        Files.createDirectories(dir.resolve("css"));
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve("index.html"), "<html>" + strName + "</html>");
        Files.writeString(dir.resolve("css/a.css"), "body{}");
        Files.writeString(dir.resolve(".git/HEAD"), "secret");
        if (strMount != null)
            Files.writeString(dir.resolve("rawar.json"), "{\"rawar\":1,\"name\":\"" + strName
                    + "\",\"mount\":\"" + strMount + "\",\"dars\":[]}");
        return dir;
    }


    private int nStartStubLedger() throws IOException {
        stubLedger = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        stubLedger.createContext("/", exchange -> {
            byte[] arrIn = exchange.getRequestBody().readAllBytes();
            refSeen.set(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + exchange.getRequestHeaders().getFirst("Authorization") + " "
                    + new String(arrIn, StandardCharsets.UTF_8));
            byte[] arrOut = "{\"version\":\"3.5.18\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, arrOut.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(arrOut);
            }
        });
        stubLedger.start();
        return stubLedger.getAddress().getPort();
    }


    private static DiscoveryDoc docRunning(int nPortJson, AuthSettings auth) {
        SandboxPorts ports = new SandboxPorts(22211, 22212, nPortJson, 22214, 22215, 22216);
        ReadyReport report = new ReadyReport("3.5.12", "OPEN_SOURCE", ports, 33321, null,
                Path.of("/run/work"), Path.of("/run/data"));
        Map<String, Object> mapOidc = new LinkedHashMap<>();
        if (auth.mode().flagTargets()) {
            mapOidc.put("client_id", "participant_admin");
            mapOidc.put("audience", "https://canton.example/sandbox");
        }
        DiscoveryNode node = new DiscoveryNode("sandbox", DiscoveryNode.STR_ROLE_APP_PROVIDER, auth,
                "http://127.0.0.1:32002/oauth2/jwks", null, mapOidc, report);
        Map<String, Object> mapProvider = new LinkedHashMap<>();
        mapProvider.put("issuer", "http://127.0.0.1:32002");
        return new DiscoveryDoc("RUNNING", 32001, DiscoveryDoc.STR_TOPOLOGY_SANDBOX, "3.5.12",
                "OPEN_SOURCE", null, null, true, DiscoveryDoc.STR_MODE_EMBEDDED, mapProvider,
                List.of(node));
    }


    private static DiscoveryDoc docStopped() {
        return new DiscoveryDoc("STOPPED", 32001, DiscoveryDoc.STR_TOPOLOGY_SANDBOX, null, null,
                null, null, false, DiscoveryDoc.STR_MODE_EMBEDDED, null, List.of());
    }


    private static AuthSettings authJwks() {
        return new AuthSettings(AuthSettings.Mode.JWKS, TokenShape.AUDIENCE, "", "", "", "");
    }


    private static AuthSettings authNone() {
        return new AuthSettings(AuthSettings.Mode.NONE, TokenShape.AUDIENCE, "", "", "", "");
    }


    private HttpResponse<String> get(String strPath) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.nPort()
                + strPath)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }


    @Test
    void pagesAreServedAtTheirMountsAndTheLongestMountWins() throws Exception {
        dirRawar("desk", null);
        dirRawar("usdcx", "/usdcx");
        server.start(0, dirTmp.resolve("rawars"), RawarServerTest::docStopped);

        assertEquals("<html>desk</html>", get("/").body());
        assertEquals("<html>usdcx</html>", get("/usdcx/").body());
        assertEquals("body{}", get("/usdcx/css/a.css").body());
        assertEquals("text/css; charset=utf-8",
                get("/usdcx/css/a.css").headers().firstValue("Content-Type").orElse(""));
        assertEquals("no-store", get("/").headers().firstValue("Cache-Control").orElse(""));

        HttpResponse<String> resSlash = get("/usdcx");
        assertEquals(301, resSlash.statusCode());
        assertEquals("/usdcx/", resSlash.headers().firstValue("Location").orElse(""));
    }


    /**
     * THE CONTROL CAN FAIL: the three requests are answered by three
     * different branches with three different statuses, and the file is asked
     * for with a query, so a report that took the URI whole would carry it.
     */
    @Test
    void everyRequestIsToldWithWhatAnsweredItAndItsStatus() throws Exception {
        dirRawar("usdcx", "/usdcx");
        BlockingQueue<RawarServer.Access> queue = new LinkedBlockingQueue<>();
        server.useAccess(queue::add);
        server.start(0, dirTmp.resolve("rawars"), RawarServerTest::docStopped);

        get("/usdcx");
        get("/usdcx/css/a.css?token=abc");
        get("/nope/");
        List<RawarServer.Access> lstSeen = List.of(poll(queue), poll(queue), poll(queue));
        assertTrue(lstSeen.contains(new RawarServer.Access("GET", "/usdcx", "redirect", 301)),
                lstSeen.toString());
        assertTrue(lstSeen.contains(new RawarServer.Access("GET", "/usdcx/css/a.css", "file", 200)),
                lstSeen.toString());
        assertTrue(lstSeen.contains(new RawarServer.Access("GET", "/nope/", "none", 404)),
                lstSeen.toString());
    }


    private static RawarServer.Access poll(BlockingQueue<RawarServer.Access> queue)
            throws InterruptedException {
        RawarServer.Access access = queue.poll(5, TimeUnit.SECONDS);
        assertNotNull(access, "no access was told within 5 s");
        return access;
    }


    @Test
    void anEditIsServedOnTheNextRequestWithNothingRestarted() throws Exception {
        Path dir = dirRawar("desk", null);
        server.start(0, dirTmp.resolve("rawars"), RawarServerTest::docStopped);
        assertEquals("body{}", get("/css/a.css").body());
        Files.writeString(dir.resolve("css/a.css"), "body{color:red}");
        assertEquals("body{color:red}", get("/css/a.css").body());

        dirRawar("late", "/late/");
        assertEquals("<html>late</html>", get("/late/").body());
    }


    @Test
    void hiddenEntriesTraversalAndMissingFilesAreRefused() throws Exception {
        dirRawar("desk", null);
        Files.writeString(dirTmp.resolve("outside.txt"), "outside");
        server.start(0, dirTmp.resolve("rawars"), RawarServerTest::docStopped);

        assertEquals(404, get("/.git/HEAD").statusCode());
        assertEquals(404, get("/nope.js").statusCode());
        HttpResponse<String> resUp = get("/%2e%2e/%2e%2e/outside.txt");
        assertTrue(resUp.statusCode() >= 400, resUp.statusCode() + " " + resUp.body());
        assertFalse(resUp.body().contains("outside\n") && !resUp.body().contains("not found"),
                resUp.body());
    }


    @Test
    void withNoRawarAPathSaysWhatIsMountedAndServesNoDefaultPage() throws Exception {
        Files.createDirectories(dirTmp.resolve("rawars"));
        server.start(0, dirTmp.resolve("rawars"), RawarServerTest::docStopped);
        HttpResponse<String> res = get("/");
        assertEquals(404, res.statusCode());
        assertTrue(res.body().contains("mounted: none"), res.body());
    }


    @Test
    void twoRawarsOnOneMountServeTheFirstByNameAndReportTheOther() throws Exception {
        dirRawar("alpha", "/x/");
        dirRawar("beta", "/x");
        dirRawar("Bad_Name", null);
        RawarSites.Scan scan = RawarSites.scan(dirTmp.resolve("rawars"));
        assertEquals(List.of("/x/"), scan.lstMount());
        assertEquals("alpha", scan.lstSite().get(0).strName());
        assertEquals(2, scan.lstProblem().size(), scan.lstProblem().toString());
        assertTrue(scan.lstProblem().toString().contains("beta - not served: /x/ is alpha's"));
        assertTrue(scan.lstProblem().toString().contains("Bad_Name"));
    }


    @Test
    void aMountIntoAReservedNameOrUpwardsIsRefused() {
        assertNull(RawarSites.strMountNormalised("usdcx"));
        assertNull(RawarSites.strMountNormalised("/../x"));
        assertNull(RawarSites.strMountNormalised("/_ledger/"));
        assertEquals("/a/b/", RawarSites.strMountNormalised("//a//b"));
        assertEquals("/", RawarSites.strMountNormalised("/"));
    }


    @Test
    void envWithAStackAndAuthNamesTheIssuerTheAudienceAndTheRedirect() throws Exception {
        dirRawar("usdcx", "/usdcx/");
        server.start(0, dirTmp.resolve("rawars"), () -> docRunning(30020, authJwks()));
        HttpResponse<String> res = get("/usdcx/_env.json");
        assertEquals(200, res.statusCode());
        assertEquals("no-store", res.headers().firstValue("Cache-Control").orElse(""));
        JsonNode node = MAPPER.readTree(res.body());
        assertEquals(1, node.path("env").asInt());
        assertEquals("sandbox", node.path("host").asText());
        assertEquals("/usdcx/", node.path("mount").asText());
        assertTrue(node.path("running").asBoolean());
        assertEquals("./_ledger/", node.path("ledgerBase").asText());
        assertEquals("jwt-jwks", node.path("auth").asText());
        assertEquals("http://127.0.0.1:32002", node.path("issuer").asText());
        assertEquals("rawar", node.path("clientId").asText());
        assertEquals("http://127.0.0.1:" + server.nPort() + "/usdcx/",
                node.path("redirectUri").asText());
        assertEquals("https://canton.example/sandbox", node.path("audience").asText());
    }


    @Test
    void envWithoutAuthOrWithoutAStackSaysSoAndNamesNoProvider() throws Exception {
        dirRawar("desk", null);
        server.start(0, dirTmp.resolve("rawars"), () -> docRunning(30020, authNone()));
        JsonNode nodeNone = MAPPER.readTree(get("/_env.json").body());
        assertEquals("none", nodeNone.path("auth").asText());
        assertTrue(nodeNone.path("running").asBoolean());
        assertTrue(nodeNone.path("issuer").isMissingNode());

        server.start(0, dirTmp.resolve("rawars"), RawarServerTest::docStopped);
        JsonNode nodeStopped = MAPPER.readTree(get("/_env.json").body());
        assertFalse(nodeStopped.path("running").asBoolean());
        assertEquals(503, get("/_ledger/v2/version").statusCode());
    }


    @Test
    void theLedgerIsForwardedWithPathQueryMethodBodyAndBearerIntact() throws Exception {
        int nPortJson = nStartStubLedger();
        dirRawar("usdcx", "/usdcx/");
        server.start(0, dirTmp.resolve("rawars"), () -> docRunning(nPortJson, authJwks()));

        HttpResponse<String> resGet = get("/usdcx/_ledger/v2/version?x=1");
        assertEquals(200, resGet.statusCode());
        assertEquals("{\"version\":\"3.5.18\"}", resGet.body());
        assertEquals("GET /v2/version?x=1 null ", refSeen.get());

        HttpResponse<String> resPost = client.send(HttpRequest.newBuilder(URI.create(
                "http://127.0.0.1:" + server.nPort() + "/usdcx/_ledger/v2/state/active-contracts"))
                .header("Authorization", "Bearer abc")
                .POST(HttpRequest.BodyPublishers.ofString("{\"filter\":1}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resPost.statusCode());
        assertEquals("POST /v2/state/active-contracts Bearer abc {\"filter\":1}", refSeen.get());
    }


    @Test
    void aLedgerThatDoesNotAnswerIsA502NotAHang() throws Exception {
        int nPortDead;
        try (java.net.ServerSocket sock = new java.net.ServerSocket(0, 0,
                InetAddress.getLoopbackAddress())) {
            nPortDead = sock.getLocalPort();
        }
        dirRawar("desk", null);
        server.start(0, dirTmp.resolve("rawars"), () -> docRunning(nPortDead, authNone()));
        HttpResponse<String> res = get("/_ledger/v2/version");
        assertEquals(502, res.statusCode());
        assertTrue(res.body().contains("did not answer"), res.body());
    }


    @Test
    void theStatusLineNamesTheUrlAndTheMountsOrSaysNotBound() throws Exception {
        assertTrue(server.strStatus(31100).startsWith("NOT BOUND - port 31100"));
        dirRawar("desk", null);
        dirRawar("usdcx", "/usdcx/");
        server.start(0, dirTmp.resolve("rawars"), RawarServerTest::docStopped);
        assertEquals("http://127.0.0.1:" + server.nPort() + " - /usdcx/ /", server.strStatus(31100));
    }


    @Test
    void theRedirectsCoverBothLoopbackSpellingsAndTheClientIsPublic() {
        assertEquals(List.of("http://127.0.0.1:31100/usdcx/", "http://localhost:31100/usdcx/",
                "http://127.0.0.1:31100/", "http://localhost:31100/"),
                RawarEnv.lstRedirect(31100, List.of("/usdcx/", "/")));
        String strBody = RawarClient.strBodyClient(List.of("http://127.0.0.1:31100/"));
        assertTrue(strBody.contains("\"client_id\":\"rawar\""), strBody);
        assertTrue(strBody.contains("\"secret\":\"\""), strBody);
        assertNotNull(RawarEnv.node(docRunning(30020, authNone())));
        assertNull(RawarEnv.node(docStopped()));
    }
}
