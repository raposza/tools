// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.LedgerException;
import com.raposza.api.model.ApiGeneration;
import com.raposza.api.profile.AccessMode;
import com.raposza.api.profile.HostProfile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a Sandbox discovery document, with no Sandbox.
 *
 * The fixture is the document a 3.5.12 stack actually served on 2026-08-24,
 * trimmed to the fields this class reads and with the credentials replaced. It
 * is a RECORDING rather than an invention, because the point of these
 * assertions is that the shape the Sandbox publishes is the shape this parses -
 * a fixture written from the reader would agree with the reader and prove
 * nothing.
 *
 * What is NOT tested here is the fetching. That needs a server and belongs
 * against a running Sandbox.
 *
 * Author Claude/bentzn
 */
class DiscoveryTest {

    private static final String STR_URL = "http://127.0.0.1:32001/";

    private static final String STR_RUNNING = """
            {
              "product" : "raposza-sandbox",
              "schema" : 2,
              "state" : "RUNNING",
              "topology" : "sandbox",
              "discovery" : { "port" : 32001 },
              "oidc" : {
                "running" : true,
                "mode" : "embedded",
                "discovery" : "http://127.0.0.1:32002/.well-known/openid-configuration",
                "issuer" : "http://127.0.0.1:32002",
                "jwks" : "http://127.0.0.1:32002/oauth2/jwks",
                "token_endpoint" : "http://127.0.0.1:32002/oauth2/token",
                "grant_type" : "client_credentials"
              },
              "canton" : { "version" : "3.5.12", "edition" : "OPEN_SOURCE" },
              "nodes" : [ {
                "name" : "sandbox",
                "role" : "app-provider",
                "auth" : {
                  "mode" : "JWKS",
                  "type" : "jwt-jwks",
                  "shape" : "AUDIENCE",
                  "jwks" : "http://127.0.0.1:32002/oauth2/jwks",
                  "token" : "http://127.0.0.1:32002/mint.txt?sub=participant_admin",
                  "oidc" : {
                    "client_id" : "participant_admin",
                    "audience" : "https://daml.com/jwt/aud/participant/sandbox"
                  }
                },
                "canton.version" : "3.5.12",
                "canton.edition" : "OPEN_SOURCE",
                "port.ledger-api" : "30011",
                "port.admin-api" : "30012",
                "port.json-api" : "30010",
                "host" : "localhost"
              } ]
            }
            """;

    private static final String STR_STOPPED = """
            {
              "product" : "raposza-sandbox",
              "schema" : 2,
              "state" : "STOPPED",
              "topology" : "sandbox",
              "discovery" : { "port" : 32001 },
              "oidc" : { "running" : false, "mode" : "embedded" }
            }
            """;


    /**
     * A THREE NODE DOCUMENT, AND IT IS NOT A RECORDING.
     *
     * Unlike {@link #STR_RUNNING} above, this one is CONSTRUCTED from the
     * producer - `SandboxWindow.docDiscoveryLocalNet()`, `DiscoveryNode.map()`
     * and `ReadyReport`'s LocalNet constructor - and from
     * `LocalNetPorts.N_PORT_FIRST_DEFAULT` 30010 with `N_STRIDE_ROLE` 10, in
     * `LST_ROLE` order. Nothing here was read off a running stack, and it is
     * trimmed to the fields this class reads.
     *
     * THE AUDIENCE IS A PLACEHOLDER. On this topology it comes from the
     * Sandbox form, so it has no literal value to record, and no assertion
     * below depends on what it says.
     */
    private static final String STR_LOCALNET = """
            {
              "product" : "raposza-sandbox",
              "schema" : 2,
              "state" : "RUNNING",
              "topology" : "localnet",
              "discovery" : { "port" : 32001 },
              "oidc" : {
                "running" : true,
                "mode" : "embedded",
                "discovery" : "http://127.0.0.1:32002/.well-known/openid-configuration",
                "issuer" : "http://127.0.0.1:32002",
                "jwks" : "http://127.0.0.1:32002/oauth2/jwks",
                "token_endpoint" : "http://127.0.0.1:32002/oauth2/token",
                "grant_type" : "client_credentials"
              },
              "canton" : { "version" : "3.5.13", "edition" : "OPEN_SOURCE" },
              "nodes" : [ {
                "name" : "sv",
                "role" : "sv",
                "auth" : {
                  "mode" : "JWKS",
                  "type" : "jwt-jwks",
                  "shape" : "AUDIENCE",
                  "jwks" : "http://127.0.0.1:32002/oauth2/jwks",
                  "token" : "http://127.0.0.1:32002/mint.txt?sub=participant_admin",
                  "oidc" : { "audience" : "https://localnet.invalid/aud/participant" }
                },
                "port.ledger-api" : "30010",
                "port.admin-api" : "30011",
                "port.json-api" : "30012",
                "host" : "localhost"
              }, {
                "name" : "app-provider",
                "role" : "app-provider",
                "auth" : {
                  "mode" : "JWKS",
                  "type" : "jwt-jwks",
                  "shape" : "AUDIENCE",
                  "jwks" : "http://127.0.0.1:32002/oauth2/jwks",
                  "token" : "http://127.0.0.1:32002/mint.txt?sub=participant_admin",
                  "oidc" : { "audience" : "https://localnet.invalid/aud/participant" }
                },
                "port.ledger-api" : "30020",
                "port.admin-api" : "30021",
                "port.json-api" : "30022",
                "host" : "localhost"
              }, {
                "name" : "app-user",
                "role" : "app-user",
                "auth" : {
                  "mode" : "JWKS",
                  "type" : "jwt-jwks",
                  "shape" : "AUDIENCE",
                  "jwks" : "http://127.0.0.1:32002/oauth2/jwks",
                  "token" : "http://127.0.0.1:32002/mint.txt?sub=participant_admin",
                  "oidc" : { "audience" : "https://localnet.invalid/aud/participant" }
                },
                "port.ledger-api" : "30030",
                "port.admin-api" : "30031",
                "port.json-api" : "30032",
                "host" : "localhost"
              } ]
            }
            """;


    /**
     * The constructor is private because a document is FETCHED, not built. The
     * parsing is what these tests are about, so they reach it directly rather
     * than making the class more open than it should be for their convenience.
     */
    private static Discovery of(String strJson) throws Exception {
        JsonNode node = new ObjectMapper().readTree(strJson);
        Constructor<Discovery> ctor =
                Discovery.class.getDeclaredConstructor(String.class, JsonNode.class);
        ctor.setAccessible(true);
        return ctor.newInstance(STR_URL, node);
    }


    @Test
    void aRunningStackYieldsTheLedgerApiPortAndHost() throws Exception {
        HostProfile profile = of(STR_RUNNING).profile();

        assertEquals("localhost", profile.nameHost());
        assertEquals(30011, profile.portLedger());
        assertEquals(30010, profile.portJson());
        assertEquals("http", profile.strProtocol());
    }


    /**
     * The ports are STRINGS in the report and integers here. That is the one
     * conversion in this class that a hand written fixture would have got right
     * by accident.
     */
    @Test
    void thePortsAreReadThroughTheirQuotes() throws Exception {
        assertEquals(30011, of(STR_RUNNING).profile().portLedger());
    }


    @Test
    void anAudienceShapeCarriesTheAudienceAndNoScope() throws Exception {
        HostProfile profile = of(STR_RUNNING).profile();

        assertEquals("https://daml.com/jwt/aud/participant/sandbox", profile.strAudience());
        assertNull(profile.strScope());
    }


    @Test
    void aSandboxIsReadWriteBecauseThatIsWhatOneIsFor() throws Exception {
        assertEquals(AccessMode.READ_WRITE, of(STR_RUNNING).profile().mode());
        assertTrue(of(STR_RUNNING).profile().canSubmit());
    }


    @Test
    void theGenerationComesFromTheCantonMajorVersion() throws Exception {
        assertEquals(ApiGeneration.V2, of(STR_RUNNING).generation());
        assertEquals(ApiGeneration.V1,
                of(STR_RUNNING.replace("\"3.5.12\"", "\"2.10.4\"")).generation());
    }


    /**
     * A version nobody has seen is refused rather than assumed to be the newer
     * generation: guessing produces failures that read as defects in the
     * client.
     */
    @Test
    void anUnknownMajorVersionIsRefusedByName() throws Exception {
        Discovery doc = of(STR_RUNNING.replace("\"3.5.12\"", "\"4.0.1\""));
        LedgerException ex = assertThrows(LedgerException.class, doc::generation);
        assertTrue(ex.getMessage().contains("4.0.1"), ex.getMessage());
    }


    @Test
    void aStoppedStackHasNoLedgerToConnectTo() throws Exception {
        Discovery doc = of(STR_STOPPED);

        assertTrue(doc.strState().contains("STOPPED"));
        LedgerException ex = assertThrows(LedgerException.class, doc::profile);
        assertTrue(ex.getMessage().contains("STOPPED"), ex.getMessage());
    }


    @Test
    void anUnauthenticatedStackYieldsNoTokenSourceRatherThanFailing() throws Exception {
        Discovery doc = of(STR_RUNNING.replace("\"mode\" : \"JWKS\"", "\"mode\" : \"NONE\""));
        assertNull(doc.tokenSource());
    }


    /**
     * Auth on and no mint url is the case that must not be survivable: the
     * window would connect anonymously to a participant that refuses it, and
     * report a credential problem it could have named here.
     */
    @Test
    void authWithNoMintUrlIsRefusedByName() throws Exception {
        Discovery doc = of(STR_RUNNING.replace(
                "\"token\" : \"http://127.0.0.1:32002/mint.txt?sub=participant_admin\"",
                "\"token\" : \"\""));

        LedgerException ex = assertThrows(LedgerException.class, doc::tokenSource);
        assertTrue(ex.getMessage().contains("auth.token"), ex.getMessage());
    }


    @Test
    void theDisplayNameSaysWhichStackItIs() throws Exception {
        assertEquals("sandbox 3.5.12 open-source", of(STR_RUNNING).nameDisplay());
    }


    @Test
    void aSandboxPublishesOneNodeAndAStoppedStackNone() throws Exception {
        assertEquals(1, of(STR_RUNNING).cntNode());
        assertEquals(0, of(STR_STOPPED).cntNode());
    }


    /**
     * The default is the FIRST node and the document says so - on this
     * topology that is `sv`, which is not where an application fixture is
     * submitted. The window connecting there is correct behaviour and is why
     * the choice is offered before a connection rather than diagnosed after
     * one.
     */
    @Test
    void aThreeNodeDocumentIsReadAsItsFirstNodeUntilToldOtherwise() throws Exception {
        Discovery doc = of(STR_LOCALNET);

        assertEquals(3, doc.cntNode());
        assertEquals(0, doc.idxNode());
        assertEquals("localnet", doc.strTopology());
        assertEquals(30010, doc.profile().portLedger());
        assertEquals("sv 3.5.13 open-source", doc.nameDisplay());
        assertEquals("sv", doc.strNameNode());
    }


    @Test
    void anotherNodeIsAnotherParticipantAndAnotherDisplayName() throws Exception {
        Discovery doc = of(STR_LOCALNET).onNode(1);

        assertEquals(1, doc.idxNode());
        assertEquals(30020, doc.profile().portLedger());
        assertEquals(30022, doc.profile().portJson());
        assertEquals("app-provider 3.5.13 open-source", doc.nameDisplay());
    }


    /**
     * The original is UNCHANGED by the choice: a caller that had already built
     * a profile from it keeps describing the node that profile is for.
     */
    @Test
    void choosingANodeLeavesTheDocumentItWasChosenFromAlone() throws Exception {
        Discovery doc = of(STR_LOCALNET);
        Discovery docOther = doc.onNode(2);

        assertEquals(30030, docOther.profile().portLedger());
        assertEquals(30010, doc.profile().portLedger());
    }


    /**
     * WHAT THE TAB STRIP SHOWS. `app-provider 3.5.13 open-source` is the
     * profile's name and is not a tab label; the node's own name is.
     */
    @Test
    void aNodeIsNamedShortEnoughForATab() throws Exception {
        assertEquals("sv", of(STR_LOCALNET).strNameNode());
        assertEquals("app-user", of(STR_LOCALNET).onNode(2).strNameNode());
        assertEquals("sandbox", of(STR_RUNNING).strNameNode());
    }


    /**
     * A TAB PER LEDGER, and a node with no Ledger API port is not one. The
     * window opens one tab per entry of this list, so a document describing
     * infrastructure beside its participants does not produce a tab nobody
     * can connect.
     */
    @Test
    void onlyTheNodesCarryingALedgerPortAreLedgers() throws Exception {
        assertEquals(List.of(0, 1, 2), of(STR_LOCALNET).lstIdxLedger());
        assertEquals(List.of(0), of(STR_RUNNING).lstIdxLedger());
        assertEquals(List.of(), of(STR_STOPPED).lstIdxLedger());

        String strNoPort = STR_LOCALNET.replace("\"port.ledger-api\" : \"30020\",", "");
        assertEquals(List.of(0, 2), of(strNoPort).lstIdxLedger());
    }


    @Test
    void aNodeTheDocumentDoesNotListIsRefusedByName() throws Exception {
        Discovery doc = of(STR_LOCALNET);

        LedgerException ex = assertThrows(LedgerException.class, () -> doc.onNode(3));
        assertTrue(ex.getMessage().contains("3 node"), ex.getMessage());
        assertThrows(LedgerException.class, () -> doc.onNode(-1));
    }


    /**
     * MEASURED ON 2026-09-21 AND STATED BY THE PRODUCER: the three roles are
     * given the same audience, so one token works against whichever Ledger API
     * it is presented to. The document states it by carrying the same block on
     * each node, and this asserts the reader does not lose that by moving.
     */
    @Test
    void everyNodeOfThisStackCarriesTheSameCredentialUrl() throws Exception {
        Discovery doc = of(STR_LOCALNET);

        assertEquals(doc.strUrlMintToken(), doc.onNode(2).strUrlMintToken());
        assertEquals(doc.profile().strAudience(), doc.onNode(2).profile().strAudience());
    }

}
