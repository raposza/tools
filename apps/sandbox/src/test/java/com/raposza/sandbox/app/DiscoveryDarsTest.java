// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.Edition;
import com.raposza.canton.install.InstallSource;
import com.raposza.canton.install.VersionId;
import com.raposza.canton.topology.SandboxPorts;
import com.raposza.jwt.TokenShape;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The DARs half of discovery, 2026-10-07: `dars` and `snapshot` on the
 * document, `GET /dars` on the endpoint, and which installed Canton lends the
 * reader.
 *
 * Author Claude/bentzn
 */
class DiscoveryDarsTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final DiscoveryDar DAR_SELECTED = new DiscoveryDar("registry", "0.6.0", "ab12",
            DiscoveryDar.STR_ORIGIN_USER, DiscoveryDar.STR_STATUS_SELECTED, Path.of("/s/registry.dar"));

    private final DiscoveryServer server = new DiscoveryServer();


    @AfterEach
    void unbind() {
        server.stop();
    }


    /** Stopped, the list is what the next start carries - his decision. */
    @Test
    void a_stopped_document_lists_the_dars_and_no_snapshot() {
        DiscoveryDoc doc = new DiscoveryDoc("STOPPED", 31011, DiscoveryDoc.STR_TOPOLOGY_SANDBOX,
                null, null, null, null, false, null, null, List.of(), List.of(DAR_SELECTED), "usdcx");

        Map<String, Object> map = doc.map();
        List<?> lstDar = (List<?>) map.get(DiscoveryDoc.STR_KEY_DARS);
        assertEquals(1, lstDar.size());
        Map<?, ?> mapDar = (Map<?, ?>) lstDar.get(0);
        assertEquals("registry", mapDar.get("name"));
        assertEquals("selected", mapDar.get("status"));
        assertEquals("user", mapDar.get("origin"));
        assertTrue(map.containsKey(DiscoveryDoc.STR_KEY_SNAPSHOT), "null, not absent");
        assertNull(map.get(DiscoveryDoc.STR_KEY_SNAPSHOT), "nothing runs, so nothing was loaded");
        assertFalse(map.containsKey(DiscoveryDoc.STR_KEY_NODES), "the stopped rule still holds");
    }


    @Test
    void a_running_document_names_the_snapshot_it_was_restored_from() {
        SandboxPorts ports = new SandboxPorts(22211, 22212, 22213, 22214, 22215, 22216);
        ReadyReport report = new ReadyReport("3.5.18", "OPEN_SOURCE", ports, 33321, null,
                Path.of("/run/work"), Path.of("/run/data"));
        AuthSettings auth = new AuthSettings(AuthSettings.Mode.NONE, TokenShape.AUDIENCE, "", "",
                "", "");
        DiscoveryNode node = new DiscoveryNode("sandbox", DiscoveryNode.STR_ROLE_APP_PROVIDER, auth,
                null, null, Map.of(), report);
        DiscoveryDoc doc = new DiscoveryDoc("RUNNING", 31011, DiscoveryDoc.STR_TOPOLOGY_SANDBOX,
                "3.5.18", "OPEN_SOURCE", Path.of("/run"), null, false, null, null, List.of(node),
                List.of(DAR_SELECTED), "usdcx");

        assertEquals("usdcx", doc.map().get(DiscoveryDoc.STR_KEY_SNAPSHOT));
        assertTrue(doc.strJson().contains("\"dars\""), doc.strJson());
    }


    /** The old constructor still means "no DARs, no snapshot". */
    @Test
    void the_constructor_without_dars_writes_an_empty_list() {
        DiscoveryDoc doc = new DiscoveryDoc("STOPPED", 31011, DiscoveryDoc.STR_TOPOLOGY_SANDBOX,
                null, null, null, null, false, null, null, List.of());

        assertEquals(List.of(), doc.map().get(DiscoveryDoc.STR_KEY_DARS));
    }


    @Test
    void dars_answers_with_what_the_supplier_describes() throws Exception {
        server.start(0, DiscoveryDarsTest::docStopped);
        server.useDars(() -> "{\"schema\":1,\"packages\":[]}");

        HttpResponse<String> response = get("/dars");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"packages\""), response.body());
        assertEquals(200, get("/").statusCode(), "the document is still there");
    }


    @Test
    void dars_without_a_supplier_is_a_404() throws Exception {
        server.start(0, DiscoveryDarsTest::docStopped);

        assertEquals(404, get("/dars").statusCode());
    }


    /** The reason goes back, so the reader can act on it. */
    @Test
    void a_description_that_fails_says_why() throws Exception {
        server.start(0, DiscoveryDarsTest::docStopped);
        server.useDars(() -> {
            throw new IllegalStateException("no open-source Canton 3.5.1 or newer is installed");
        });

        HttpResponse<String> response = get("/dars");
        assertEquals(500, response.statusCode());
        assertTrue(response.body().contains("no open-source Canton"), response.body());
    }


    @Test
    void the_reader_is_the_newest_open_source_canton_from_3_5_1() {
        CantonInstallation inst351 = inst("3.5.1", Edition.OPEN_SOURCE, true);
        CantonInstallation inst3518 = inst("3.5.18", Edition.OPEN_SOURCE, true);
        CantonInstallation inst3519Ent = inst("3.5.19", Edition.ENTERPRISE, true);
        CantonInstallation inst3520NoJar = inst("3.5.20", Edition.OPEN_SOURCE, false);
        CantonInstallation inst3411 = inst("3.4.11", Edition.OPEN_SOURCE, true);

        assertSame(inst3518, DarDescription.instReader(
                List.of(inst351, inst3519Ent, inst3411, inst3518, inst3520NoJar)));
        assertNull(DarDescription.instReader(List.of(inst3411, inst3519Ent)),
                "3.4 refuses LF 2.3, and enterprise is not measured");
        assertNotNull(DarDescription.instReader(List.of(inst351)));
    }


    private static CantonInstallation inst(String strVersion, Edition edition, boolean flagJar) {
        Path dirHome = Path.of("/dpm/" + strVersion);
        return new CantonInstallation(VersionId.parse(strVersion), edition, InstallSource.DPM, dirHome,
                flagJar ? dirHome.resolve("lib/canton.jar") : null);
    }


    private HttpResponse<String> get(String strPath) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.nPort() + strPath))
                        .timeout(TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }


    private static DiscoveryDoc docStopped() {
        return new DiscoveryDoc("STOPPED", 0, DiscoveryDoc.STR_TOPOLOGY_SANDBOX, null, null, null,
                null, false, DiscoveryDoc.STR_MODE_EMBEDDED, null, List.of());
    }

}
