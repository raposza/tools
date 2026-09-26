// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.LedgerException;
import com.raposza.api.TokenSource_i;
import com.raposza.api.model.ApiGeneration;
import com.raposza.api.profile.AccessMode;
import com.raposza.api.profile.HostProfile;
import com.raposza.jwt.StaticTokenSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A running Sandbox, read from the discovery endpoint it publishes.
 *
 * <h2>The two ways in, and why this is the easy one</h2>
 *
 * A standalone participant has to be described - host, ports, auth type,
 * audience or scope, and a credential from somewhere. A Sandbox describes
 * ITSELF: one GET answers where the Ledger API is, which Canton is behind it,
 * whether authentication is on, and a URL that hands back a token. So this path
 * asks the operator for a URL and nothing else.
 *
 * Both paths end in the SAME PAIR - a {@link HostProfile} and a {@link
 * TokenSource_i} - which is the seam. Nothing downstream can tell which
 * produced it, and the catalogue path is untouched.
 *
 * <h2>ONE NODE OF WHAT MAY BE SEVERAL</h2>
 *
 * The document became a LIST of nodes on 2026-09-21 so that a multi-node
 * topology could be described at all. A Sandbox publishes one; LocalNetND
 * publishes three in `LocalNetPorts.LST_ROLE` order, which puts `sv` first and
 * the two ORGANIZATIONAL participants second and third.
 *
 * This window holds one ledger client, so an instance of this class describes
 * exactly ONE node - the first, unless {@link #onNode} was asked for another.
 * Every reading below is that node's: the profile, the audience or scope, the
 * mint url, the credential and the display name. Giving the window a tab per
 * node is a different piece of work and is not this.
 *
 * WHICH NODE IS THE OPERATOR'S ANSWER, NOT A DEFAULT WORTH KEEPING QUIET. On
 * LocalNetND the first node is `sv`, and a fixture submitted on the
 * application side is not on it - so a window that silently took the first
 * would show an empty ledger and look like a defect in the reader.
 *
 * <h2>The generation comes from the document, not from a probe</h2>
 *
 * {@code canton.version} names the Canton behind the Ledger API, so the
 * generation follows from its major version with no extra round trip. That is
 * only true HERE. A standalone participant says nothing about itself before it
 * is connected to, and choosing its generation is a different problem.
 *
 * <h2>The token is fetched ONCE</h2>
 *
 * {@code mint.token} is a GET that returns a bare token, and the Sandbox mints
 * it with a long life. It is fetched at connect and held for the session, so a
 * gRPC call costs no HTTP. A session outliving the token is possible and is not
 * handled: it would show as the participant refusing calls it accepted a moment
 * ago, and the fix is to reconnect. Renewal belongs with the standalone path,
 * where lifetimes are somebody else's.
 *
 * Author Claude/bentzn
 */
public final class Discovery {

    /** What the window shows for a stack that is up but holds no stack detail. */
    public static final String STR_RUNNING = "RUNNING";

    private static final Duration TIMEOUT_HTTP = Duration.ofSeconds(10);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonNode node;

    private final String strUrl;

    /** Which of the document's nodes this instance reads. */
    private final int idxNode;


    /**
     * A document, read as its first node.
     *
     * KEPT AT TWO ARGUMENTS. Fetching produces a document nobody has chosen a
     * node in yet, and that is the first one; the third argument exists for
     * {@link #onNode} and for nothing else.
     *
     * @param strUrl where the document came from
     * @param node the parsed document
     */
    private Discovery(String strUrl, JsonNode node) {
        this(strUrl, node, 0);
    }


    /**
     * @param strUrl where the document came from
     * @param node the parsed document
     * @param idxNode which of its nodes this instance reads, 0-based
     */
    private Discovery(String strUrl, JsonNode node, int idxNode) {
        this.strUrl = strUrl;
        this.node = node;
        this.idxNode = idxNode;
    }


    /**
     * @param strUrl the discovery endpoint, e.g. {@code http://127.0.0.1:32001/}
     * @return the document
     * @throws LedgerException when it cannot be read or is not a Sandbox's
     */
    public static Discovery fetch(String strUrl) {
        if (strUrl == null || strUrl.isBlank())
            throw new LedgerException("no discovery url supplied");

        String strBody = get(strUrl);
        JsonNode nodeHere;
        try {
            nodeHere = MAPPER.readTree(strBody);
        }
        catch (IOException ex) {
            throw new LedgerException(strUrl + " did not answer JSON: " + ex, ex);
        }

        // A product check rather than a shape check. Something else answering
        // on that port with well formed JSON is the case worth naming.
        if (!nodeHere.path("product").isTextual()) {
            throw new LedgerException(strUrl + " answered JSON that names no product, so it is"
                    + " not a Raposza Sandbox discovery document");
        }
        return new Discovery(strUrl, nodeHere);
    }


    /** @return the document, parsed, for anything this class does not expose */
    public JsonNode node() {
        return node;
    }


    public String strUrl() {
        return strUrl;
    }


    public String strState() {
        return node.path("state").asText("");
    }


    public boolean isRunning() {
        return STR_RUNNING.equals(strState());
    }


    public String strVersion() {
        return node.path("canton").path("version").asText("");
    }


    public String strEdition() {
        return node.path("canton").path("edition").asText("");
    }


    /**
     * What the producer says this is, rather than what a node count suggests.
     *
     * @return {@code sandbox}, {@code localnet}, or "" when the document names
     *         none
     */
    public String strTopology() {
        return node.path("topology").asText("");
    }


    /**
     * @return how many nodes the document lists; 0 for a stack that is not
     *         running, which is what a stopped stack publishes
     */
    public int cntNode() {
        JsonNode nodeList = node.path("nodes");
        return nodeList.isArray() ? nodeList.size() : 0;
    }


    /** @return what this node is FOR, or "" when the document says nothing */
    public String strRoleNode() {
        return nodeSelected().path("role").asText("");
    }


    /** @return which node this instance reads, 0-based */
    public int idxNode() {
        return idxNode;
    }


    /**
     * @return which Ledger API the participant behind this stack speaks
     * @throws LedgerException when the document names no usable version
     */
    public ApiGeneration generation() {
        String strVersion = strVersion();
        int idxDot = strVersion.indexOf('.');
        if (idxDot < 1) {
            throw new LedgerException(strUrl + " names canton version '" + strVersion
                    + "', which has no major version to read a Ledger API generation from");
        }

        int nMajor;
        try {
            nMajor = Integer.parseInt(strVersion.substring(0, idxDot));
        }
        catch (NumberFormatException ex) {
            throw new LedgerException(strUrl + " names canton version '" + strVersion
                    + "', whose major version is not a number", ex);
        }

        if (nMajor == 2)
            return ApiGeneration.V1;
        if (nMajor == 3)
            return ApiGeneration.V2;

        // NOT defaulted to the newer one. A Canton 4 would speak something
        // nobody here has seen, and guessing v2 would produce failures that
        // read as defects in this client.
        throw new LedgerException(strUrl + " names canton version '" + strVersion
                + "', and no Ledger API generation is known for major version " + nMajor);
    }


    /**
     * @return where and how to reach the Ledger API
     * @throws LedgerException when the stack is not running or names no port
     */
    public HostProfile profile() {
        if (!isRunning()) {
            throw new LedgerException(strUrl + " reports state " + strState()
                    + "; there is no ledger to connect to");
        }

        JsonNode nodeFirst = nodeSelected();
        int nPortLedger = nodeFirst.path("port.ledger-api").asInt(0);
        if (nPortLedger <= 0) {
            throw new LedgerException(strUrl + " lists no node carrying a port.ledger-api,"
                    + " so the running stack cannot be reached");
        }

        int nPortJson = nodeFirst.path("port.json-api").asInt(-1);
        String nameHost = nodeFirst.path("host").asText("localhost");

        // Scope and audience are MUTUALLY EXCLUSIVE and a participant accepts
        // one shape. The document says which, so it is read rather than
        // resolved by precedence.
        String strShape = nodeFirst.path("auth").path("shape").asText("");
        String strAudience = "AUDIENCE".equals(strShape) ? strAudience() : null;
        String strScope = "SCOPE".equals(strShape) ? strScope() : null;

        // READ_WRITE, and it is the one value here that is a POLICY and not a
        // reading. A sandbox is a developer's own disposable ledger and the
        // point of connecting a workbench to one is to act on it. A standalone
        // participant keeps the READ_ONLY default of its catalogue line.
        return new HostProfile(nameDisplay(), "http", nameHost, nPortLedger, nPortJson,
                strScope, strAudience, AccessMode.READ_WRITE, null);
    }


    /**
     * The same document, read as another of its nodes.
     *
     * A NEW INSTANCE rather than a setter. A caller holding one has already
     * built a profile and possibly a connection from it, and moving the node
     * underneath would change what those mean without the caller being told.
     *
     * @param idxWanted which node, 0-based
     * @return this document read as that node
     * @throws LedgerException when the document lists no such node
     */
    public Discovery onNode(int idxWanted) {
        nodeAt(idxWanted);
        return new Discovery(strUrl, node, idxWanted);
    }


    /**
     * What this node is called, short enough for a tab.
     *
     * THE NAME, FALLING BACK TO THE ROLE. On LocalNetND the two coincide and
     * both are `sv`, `app-provider`, `app-user`; on a Sandbox the name is what
     * Canton calls the participant, which is what the operator sees everywhere
     * else the participant id appears.
     *
     * @return never blank
     */
    public String strNameNode() {
        String strName = nodeSelected().path("name").asText("");
        if (!strName.isBlank())
            return strName;

        String strRole = nodeSelected().path("role").asText("");
        return strRole.isBlank() ? "node " + idxNode : strRole;
    }


    /**
     * Which of the document's nodes are LEDGERS.
     *
     * A NODE CARRYING A `port.ledger-api` IS ONE, and that is a reading rather
     * than a rule about topologies: a consumer cannot connect to a node that
     * publishes nowhere to connect, and a document may describe infrastructure
     * beside its participants. A window opening a tab per entry of this list
     * opens one per participant and none it cannot reach.
     *
     * @return the indices, in the producer's order; empty for a stack that is
     *         not running
     */
    public List<Integer> lstIdxLedger() {
        List<Integer> lstOut = new ArrayList<>();
        for (int idxAt = 0; idxAt < cntNode(); idxAt++) {
            if (node.path("nodes").path(idxAt).path("port.ledger-api").asInt(0) > 0)
                lstOut.add(Integer.valueOf(idxAt));
        }
        return lstOut;
    }


    /**
     * The node this instance reads.
     *
     * THE FIRST ONE UNTIL TOLD OTHERWISE, and that is a policy rather than a
     * reading: this window holds one ledger client, so it takes the node the
     * producer put first unless {@link #onNode} chose another.
     *
     * @return the selected node, or a missing node when none is listed
     */
    private JsonNode nodeSelected() {
        return node.path("nodes").path(idxNode);
    }


    /**
     * @param idxWanted which node, 0-based
     * @return that node, never a missing one
     * @throws LedgerException when the document lists no such node
     */
    private JsonNode nodeAt(int idxWanted) {
        if (idxWanted < 0 || idxWanted >= cntNode()) {
            throw new LedgerException(strUrl + " lists " + cntNode() + " node(s), so there is"
                    + " no node " + idxWanted + " in it to connect to");
        }
        return node.path("nodes").path(idxWanted);
    }


    private String strAudience() {
        String strFrom = nodeSelected().path("auth").path("oidc").path("audience").asText("");
        return strFrom.isBlank() ? null : strFrom;
    }


    private String strScope() {
        String strFrom = nodeSelected().path("auth").path("oidc").path("scope").asText("");
        return strFrom.isBlank() ? null : strFrom;
    }


    /**
     * @return a name that says which stack this is, for the profile picker
     */
    public String nameDisplay() {
        // OFF THE DOCUMENT rather than the word `sandbox` compiled in here. A
        // LocalNet publishes three nodes with names of their own and a picker
        // offering three entries called `sandbox` would be useless.
        String strName = nodeSelected().path("name").asText("");
        String strTopology = node.path("topology").asText("");
        String strBase = !strName.isBlank() ? strName
                : (strTopology.isBlank() ? "sandbox" : strTopology);

        String strVersion = strVersion();
        if (strVersion.isBlank())
            return strBase;
        String strEdition = strEdition();
        return strEdition.isBlank()
                ? strBase + " " + strVersion
                : strBase + " " + strVersion + " "
                        + strEdition.toLowerCase().replace('_', '-');
    }


    /**
     * The url a consumer can GET a token from, for ANY user.
     *
     * Exposed beside {@link #tokenSource} because the two answer different
     * questions: that one hands over the credential the stack chose, this one
     * is the route to a credential for somebody else - which is what a window
     * offering a user choice needs.
     *
     * @return the selected node's `auth.token` url, or null when it publishes
     *         none - which is what a provider this application does not run
     *         looks like
     */
    public String strUrlMintToken() {
        String strUrlToken = nodeSelected().path("auth").path("token").asText("");
        return strUrlToken.isBlank() ? null : strUrlToken;
    }


    public TokenSource_i tokenSource() {
        String strMode = nodeSelected().path("auth").path("mode").asText("");
        if (strMode.isBlank() || "NONE".equalsIgnoreCase(strMode))
            return null;

        String strUrlToken = nodeSelected().path("auth").path("token").asText("");
        if (strUrlToken.isBlank()) {
            throw new LedgerException(strUrl + " reports auth mode " + strMode
                    + " and publishes no auth.token url for node " + idxNode + ", so this"
                    + " window has no way to obtain a credential for it");
        }

        String strToken = get(strUrlToken).trim();
        if (strToken.isBlank())
            throw new LedgerException(strUrlToken + " returned an empty token");

        return new StaticTokenSource(strToken);
    }


    private static String get(String strUrl) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(strUrl))
                    .timeout(TIMEOUT_HTTP)
                    .GET()
                    .build();
        }
        catch (IllegalArgumentException ex) {
            throw new LedgerException("'" + strUrl + "' is not a url: " + ex.getMessage(), ex);
        }

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT_HTTP)
                .build()) {

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LedgerException(strUrl + " answered HTTP " + response.statusCode()
                        + "; a running Sandbox answers 200");
            }
            return response.body();
        }
        catch (IOException ex) {
            throw new LedgerException("could not reach " + strUrl + " - is the Sandbox window"
                    + " open? " + ex, ex);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LedgerException("interrupted while reading " + strUrl, ex);
        }
    }

}
