// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.canton.topology.SandboxPorts;
import com.raposza.jwt.TokenShape;
import com.raposza.sandbox.app.ReadyReport;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * What the discovery document says, with no window and no HTTP. B-8.
 *
 * The statement worth asserting hardest is the one a consumer builds on: a
 * stopped Sandbox says the state and nothing about a stack. Everything a
 * consumer could act on - the version, the run directory, what a participant
 * checks, the ports - is present only when there IS a participant, because a
 * consumer cannot tell an intention from a fact once it is in the JSON.
 *
 * Author Claude/bentzn
 */
class DiscoveryDocTest {

    private static final String STR_URL_PROVIDER = "http://127.0.0.1:33301";

    private static final String STR_URL_JWKS = STR_URL_PROVIDER + "/oauth2/jwks";

    private static final String STR_URL_TOKEN = STR_URL_PROVIDER + "/mint.txt?sub=alice";


    /**
     * NOTHING RUNNING is a document, not a refused connection - and it says the
     * state, the topology, the discovery port and the provider. NOTHING ELSE.
     */
    @Test
    void a_stopped_sandbox_says_the_state_and_nothing_about_a_stack() {
        DiscoveryDoc doc = new DiscoveryDoc("STOPPED", 31011,
                DiscoveryDoc.STR_TOPOLOGY_SANDBOX, "3.5.12", "OPEN_SOURCE",
                Path.of("/home/x/.raposza/sandbox/3.5.12-open_source"), null,
                false, DiscoveryDoc.STR_MODE_EMBEDDED, mapProvider(), List.of());

        Map<String, Object> map = doc.map();
        assertEquals("STOPPED", map.get(DiscoveryDoc.STR_KEY_STATE));
        assertEquals(DiscoveryDoc.STR_PRODUCT, map.get(DiscoveryDoc.STR_KEY_PRODUCT));
        assertEquals(DiscoveryDoc.STR_TOPOLOGY_SANDBOX,
                map.get(DiscoveryDoc.STR_KEY_TOPOLOGY));
        assertTrue(map.containsKey(DiscoveryDoc.STR_KEY_DISCOVERY));
        assertTrue(map.containsKey(DiscoveryDoc.STR_KEY_OIDC),
                "the provider is a service of its own");
        assertFalse(map.containsKey(DiscoveryDoc.STR_KEY_CANTON),
                "nothing is running, so no edition is");
        assertFalse(map.containsKey(DiscoveryDoc.STR_KEY_RUN),
                "a directory with no ledger in it is not an answer");
        assertFalse(map.containsKey(DiscoveryDoc.STR_KEY_NODES),
                "a stopped stack has no nodes to describe");
        assertFalse(doc.flagRunning());
    }


    /**
     * THE NODE KEEPS THE REPORT'S OWN KEYS. A consumer that reads
     * `sandbox.properties` finds `port.ledger-api` spelt identically here, and
     * a second spelling would be a second thing to keep in step.
     */
    @Test
    void a_node_carries_the_report_keys_unchanged() {
        Map<?, ?> mapNode = mapNodeFirst(docRunning(authJwks(), STR_URL_TOKEN));

        assertEquals("22211", mapNode.get(ReadyReport.KEY_LEDGER_API));
        assertEquals("22212", mapNode.get(ReadyReport.KEY_ADMIN_API));
    }


    /** The node says what it is called and what it is for, and they differ. */
    @Test
    void a_node_states_its_name_and_its_role() {
        Map<?, ?> mapNode = mapNodeFirst(docRunning(authJwks(), STR_URL_TOKEN));

        assertEquals("sandbox", mapNode.get(DiscoveryNode.STR_KEY_NAME));
        assertEquals(DiscoveryNode.STR_ROLE_APP_PROVIDER,
                mapNode.get(DiscoveryNode.STR_KEY_ROLE));
    }


    /** With a stack up, what it is pointed at is a fact and is published. */
    @Test
    void a_running_sandbox_publishes_what_it_is_pointed_at() {
        Map<String, Object> map = docRunning(authJwks(), STR_URL_TOKEN).map();

        assertTrue(map.containsKey(DiscoveryDoc.STR_KEY_CANTON));
        assertTrue(map.containsKey(DiscoveryDoc.STR_KEY_RUN));
        assertTrue(map.containsKey(DiscoveryDoc.STR_KEY_NODES));
        Map<?, ?> mapCanton = (Map<?, ?>) map.get(DiscoveryDoc.STR_KEY_CANTON);
        assertEquals("3.5.12", mapCanton.get("version"));
        Map<?, ?> mapRun = (Map<?, ?>) map.get(DiscoveryDoc.STR_KEY_RUN);
        assertEquals("/run/work/canton.log", mapRun.get("log"));
        assertTrue(docRunning(authJwks(), STR_URL_TOKEN).flagRunning());
    }


    /** With no check configured there is no shape and no key set to publish. */
    @Test
    void a_wildcard_node_publishes_no_target() {
        Map<?, ?> mapAuth = mapAuthFirst(docRunning(authNone(), null));

        assertEquals(AuthSettings.Mode.NONE.name(), mapAuth.get("mode"));
        assertFalse(mapAuth.containsKey("jwks"), "a wildcard participant reads no key set");
        assertFalse(mapAuth.containsKey("token"), "and needs no token");
    }


    /**
     * THE EFFECTIVE URL, not the field, and it is the KEY SET rather than the
     * provider's base url. A consumer handed the base would fetch the
     * discovery document where it expected keys.
     */
    @Test
    void a_blank_jwks_url_resolves_to_the_provider_key_set() {
        assertEquals(STR_URL_JWKS,
                mapAuthFirst(docRunning(authJwks(), STR_URL_TOKEN)).get("jwks"));
    }


    /** A typed override wins over the provider's own url. */
    @Test
    void a_typed_jwks_url_wins() {
        AuthSettings auth = new AuthSettings(AuthSettings.Mode.JWKS, TokenShape.AUDIENCE,
                "", "", "", "https://id.example.com/keys");

        assertEquals("https://id.example.com/keys",
                mapAuthFirst(docRunning(auth, STR_URL_TOKEN)).get("jwks"));
    }


    /**
     * THE TOKEN URL IS THE HAND-OVER, and it belongs to the NODE: on a
     * multi-node topology each participant checks its own audience, so one
     * document-wide token url would be wrong for all but one of them.
     */
    @Test
    void a_configured_node_publishes_where_to_get_a_token() {
        assertEquals(STR_URL_TOKEN,
                mapAuthFirst(docRunning(authJwks(), STR_URL_TOKEN)).get("token"));
    }


    /**
     * WHOSE PROVIDER IT IS. A consumer cannot infer it from the url, and an
     * external one outlives the window that published the document.
     */
    @Test
    void the_provider_block_says_whose_it_is_and_where_its_document_is() {
        Map<?, ?> mapOidc = (Map<?, ?>) docRunning(authJwks(), STR_URL_TOKEN).map()
                .get(DiscoveryDoc.STR_KEY_OIDC);

        assertEquals(DiscoveryDoc.STR_MODE_EMBEDDED, mapOidc.get(DiscoveryDoc.STR_KEY_MODE));
        assertEquals(STR_URL_PROVIDER + "/.well-known/openid-configuration",
                mapOidc.get("discovery"));
        assertEquals("client_credentials", mapOidc.get("grant_type"));
        assertEquals(Boolean.TRUE, mapOidc.get("running"));
    }


    /**
     * THE ONE THING A CONFORMING CLIENT CANNOT DISCOVER has to be on the node,
     * or it fetches a token that verifies and is then refused.
     */
    @Test
    void the_node_carries_the_audience_no_discovery_document_describes() {
        Map<?, ?> mapAuth = mapAuthFirst(docRunning(authJwks(), STR_URL_TOKEN));
        Map<?, ?> mapOidc = (Map<?, ?>) mapAuth.get(DiscoveryNode.STR_KEY_OIDC);

        assertEquals("participant_admin", mapOidc.get("client_id"));
        assertTrue(mapOidc.containsKey("audience"), "the participant checks one");
        assertTrue(mapOidc.containsKey("how"), "and a client has to be told to send it");
    }


    /** It has to be JSON, and it has to say what it is. */
    @Test
    void the_rendering_is_json_and_names_the_product() {
        DiscoveryDoc doc = new DiscoveryDoc("STOPPED", 31011, null, null, null, null, null,
                false, null, null, null);

        String strJson = doc.strJson();
        assertTrue(strJson.startsWith("{"), strJson);
        assertTrue(strJson.contains("\"" + DiscoveryDoc.STR_PRODUCT + "\""), strJson);
        assertTrue(strJson.endsWith("\n"), "a file written from this ends in a newline");
    }


    /** The schema moved when `report` and `mint` were removed. */
    @Test
    void the_schema_says_two() {
        assertEquals(Integer.valueOf(2),
                docRunning(authJwks(), STR_URL_TOKEN).map().get(DiscoveryDoc.STR_KEY_SCHEMA));
    }


    private static DiscoveryDoc docRunning(AuthSettings auth, String strUrlToken) {
        SandboxPorts ports = new SandboxPorts(22211, 22212, 22213, 22214, 22215, 22216);
        ReadyReport report = new ReadyReport("3.5.12", "OPEN_SOURCE", ports, 33321, null,
                Path.of("/run/work"), Path.of("/run/data"));
        DiscoveryNode nodeOne = new DiscoveryNode("sandbox",
                DiscoveryNode.STR_ROLE_APP_PROVIDER, auth, STR_URL_JWKS, strUrlToken,
                mapNodeOidc(auth), report);
        return new DiscoveryDoc("RUNNING", 31011, DiscoveryDoc.STR_TOPOLOGY_SANDBOX,
                "3.5.12", "OPEN_SOURCE", Path.of("/run"),
                Path.of("/run/work/canton.log"), true, DiscoveryDoc.STR_MODE_EMBEDDED,
                mapProvider(), List.of(nodeOne));
    }


    private static Map<?, ?> mapNodeFirst(DiscoveryDoc doc) {
        List<?> lstNode = (List<?>) doc.map().get(DiscoveryDoc.STR_KEY_NODES);
        return (Map<?, ?>) lstNode.get(0);
    }


    private static Map<?, ?> mapAuthFirst(DiscoveryDoc doc) {
        return (Map<?, ?>) mapNodeFirst(doc).get(DiscoveryNode.STR_KEY_AUTH);
    }


    private static Map<String, Object> mapProvider() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("discovery", STR_URL_PROVIDER + "/.well-known/openid-configuration");
        map.put("issuer", STR_URL_PROVIDER);
        map.put("jwks", STR_URL_JWKS);
        map.put("token_endpoint", STR_URL_PROVIDER + "/oauth2/token");
        map.put("grant_type", "client_credentials");
        return map;
    }


    private static Map<String, Object> mapNodeOidc(AuthSettings auth) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (!auth.mode().flagTargets())
            return map;
        map.put("client_id", "participant_admin");
        map.put("audience", "https://canton.example/sandbox");
        map.put("how", "send the audience");
        return map;
    }


    private static AuthSettings authNone() {
        return new AuthSettings(AuthSettings.Mode.NONE, TokenShape.AUDIENCE, "", "", "", "");
    }


    private static AuthSettings authJwks() {
        return new AuthSettings(AuthSettings.Mode.JWKS, TokenShape.AUDIENCE, "", "", "", "");
    }
}
