// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.idp;

import com.raposza.jwt.JwksMaterial;
import com.raposza.jwt.JwtMinter;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A JWKS endpoint and a client-credentials token endpoint, over the key
 * material this project already generates.
 *
 * <h2>Why this exists rather than a staged Dex or Hydra</h2>
 *
 * Both are single static binaries and both would genuinely fit a runtime made
 * of ordinary OS processes, which is what this project stages Canton and scribe
 * as. Build won on ONE criterion: a sandbox whose premise is that it comes up
 * with nothing obtained beforehand cannot require a binary to be obtained
 * beforehand. Hydra needs its own database and client registration; Dex needs a
 * staged binary and a configuration file. An endpoint over keys
 * {@link JwksMaterial} already generates needs neither.
 *
 * The trade is stated rather than hidden: this is a TEST identity provider. It
 * has no user store, no consent, no refresh tokens, no revocation, no TLS and
 * no rate limiting, and it must never be described as production auth.
 *
 * <h2>What makes it structurally unable to reach a production configuration</h2>
 *
 * <ul>
 * <li>it binds to the LOOPBACK address and there is no constructor that takes a
 * bind address, so a caller cannot expose it by configuration;</li>
 * <li>it speaks plain HTTP and cannot be given a certificate, so a client that
 * requires TLS cannot use it at all;</li>
 * <li>clients are registered in the constructor of the process that starts it -
 * there is no registration endpoint and no persistence, so nothing survives the
 * run;</li>
 * <li>the only grant is client_credentials.</li>
 * </ul>
 *
 * <h2>What it settles</h2>
 *
 * The participant reads its JWKS over `http://` instead of the `file:` scheme,
 * which has been listed as unverified since 2026-08-11 and which no run has
 * exercised. NOTE the limit of that: pointing `AuthOverlay` at
 * {@link #strUrlJwks()} does NOT by itself measure the fetch. Canton reads the
 * url when it first VERIFIES a token, and a stack whose console authenticates
 * with a pinned admin token never presents one. The fetch is measured when a
 * token minted here reaches the Ledger API.
 *
 * <h2>Endpoints</h2>
 *
 * <pre>
 * GET  /.well-known/openid-configuration   issuer, token_endpoint, jwks_uri
 * GET  /jwks.json                          the PUBLIC half, no `d`, no CRT members
 * POST /token                              client_credentials only
 * </pre>
 *
 * The discovery document is served because scribe takes
 * `--pipeline-oauth-issuer` beside `--pipeline-oauth-endpoint` and it is not
 * measured which of the two it uses; serving both costs one handler and removes
 * the question.
 *
 * Author Claude/bentzn
 */
public final class TestIdp implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TestIdp.class);

    public static final String STR_PATH_JWKS = "/jwks.json";

    public static final String STR_PATH_TOKEN = "/token";

    public static final String STR_PATH_DISCOVERY = "/.well-known/openid-configuration";

    /** The only grant. Anything else is refused by name. */
    public static final String STR_GRANT_CLIENT_CREDENTIALS = "client_credentials";

    /** Ask the operating system for a free port. */
    public static final int N_PORT_EPHEMERAL = 0;

    private static final String STR_CONTENT_JSON = "application/json";

    private static final int N_BACKLOG = 0;

    private static final int N_THREADS = 4;

    /** Seconds. Zero: nothing here holds a long-lived exchange. */
    private static final int N_STOP_DELAY = 0;

    private final JwksMaterial material;
    private final JwtMinter minter;
    private final Map<String, IdpClient> mapClient = new LinkedHashMap<>();
    private final int nPortRequested;

    /**
     * Counted because the interesting question about the JWKS endpoint is
     * whether the PARTICIPANT ever asks. Canton parses `auth-services` at
     * start-up; whether it fetches the url then or on the first token it has
     * to verify is not measured, and those have opposite consequences for what
     * a stack that started proves. A counter answers it from the serving side
     * without inferring anything from a log.
     */
    private final AtomicInteger cntJwks = new AtomicInteger();

    private final AtomicInteger cntToken = new AtomicInteger();

    private HttpServer server;
    private ExecutorService executor;


    /**
     * @param material the key material; the PRIVATE half signs and only the
     *        public half is ever served
     * @param nPortRequested the port to bind, or {@link #N_PORT_EPHEMERAL}
     */
    public TestIdp(JwksMaterial material, int nPortRequested) {
        if (material == null)
            throw new IdpException("no key material supplied");
        if (nPortRequested < 0 || nPortRequested > 65535)
            throw new IdpException("not a port: " + nPortRequested);

        this.material = material;
        this.minter = material.minter();
        this.nPortRequested = nPortRequested;
    }


    /**
     * @param material the key material
     */
    public TestIdp(JwksMaterial material) {
        this(material, N_PORT_EPHEMERAL);
    }


    /**
     * MUTATES and returns this, like the sandbox stack's own configuration
     * methods: a half-configured server is not a value worth copying.
     *
     * @param client the registration
     * @return this provider
     * @throws IdpException when it is already running or the id is taken
     */
    public TestIdp register(IdpClient client) {
        if (client == null)
            throw new IdpException("no client supplied");
        synchronized (this) {
            if (server != null)
                throw new IdpException("clients have to be registered before the provider starts");
            if (mapClient.containsKey(client.strClientId()))
                throw new IdpException("client '" + client.strClientId() + "' is already registered");
            mapClient.put(client.strClientId(), client);
        }
        return this;
    }


    /**
     * @return the key material, so a caller can write the public JWKS to disk
     *         as well - the file and the endpoint are the same key
     */
    public JwksMaterial material() {
        return material;
    }


    /**
     * @param strClientId a registered client
     * @return its registration, or null
     */
    public synchronized IdpClient client(String strClientId) {
        return mapClient.get(strClientId);
    }


    /**
     * Binds and serves.
     *
     * @return this provider
     * @throws IdpException when it is already running, has no client, or the
     *         port cannot be bound
     */
    public synchronized TestIdp start() {
        if (server != null)
            throw new IdpException("the identity provider is already running");
        if (mapClient.isEmpty()) {
            throw new IdpException("no client is registered, so every token request would be"
                    + " refused; register one before starting");
        }

        try {
            HttpServer serverNew = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), nPortRequested),
                    N_BACKLOG);
            serverNew.createContext(STR_PATH_DISCOVERY, this::handleDiscovery);
            serverNew.createContext(STR_PATH_JWKS, this::handleJwks);
            serverNew.createContext(STR_PATH_TOKEN, this::handleToken);

            ExecutorService executorNew = Executors.newFixedThreadPool(N_THREADS);
            serverNew.setExecutor(executorNew);
            serverNew.start();

            this.server = serverNew;
            this.executor = executorNew;
        }
        catch (IOException ex) {
            throw new IdpException("could not bind the test identity provider on loopback port "
                    + nPortRequested, ex);
        }

        log.info("test identity provider on {}, key {}, {} client(s)", strUrlIssuer(),
                material.describe(), mapClient.size());
        for (IdpClient client : mapClient.values()) {
            log.info("  client {}", client.describe());
        }
        return this;
    }


    /**
     * @return the bound port
     * @throws IdpException when it is not running
     */
    public synchronized int port() {
        if (server == null)
            throw new IdpException("the identity provider is not running");
        return server.getAddress().getPort();
    }


    public synchronized boolean isRunning() {
        return server != null;
    }


    /**
     * @return how many times the public JWKS has been served since this
     *         provider started; zero after a stack has come up means Canton
     *         has not read the url yet
     */
    public int cntJwksRequest() {
        return cntJwks.get();
    }


    /**
     * @return how many tokens the endpoint has issued; a second one during a
     *         run is a client that renewed rather than restarted
     */
    public int cntTokenIssued() {
        return cntToken.get();
    }


    /**
     * The scheme and host every url this provider serves is built on. Loopback
     * by construction and not configurable - see the type comment.
     */
    public static final String STR_URL_BASE = "http://127.0.0.1:";


    /**
     * The issuer url this provider WILL serve on, before it has bound.
     *
     * It exists because of an ordering the instance methods cannot satisfy:
     * clients are registered BEFORE {@link #start()} and the bound port is not
     * known until after it, so a caller that wants one {@code AuthPlan} to
     * describe both the participant and the client has nowhere to get the url
     * from. Requesting a known port and composing the url from it closes that,
     * and this is the only place either half is spelled.
     *
     * @param nPort the port this provider is asked to bind
     * @return the base url, which is also the `iss` claim of every token it
     *         issues
     */
    public static String strUrlIssuerOn(int nPort) {
        return STR_URL_BASE + nPort;
    }


    /**
     * @param nPort the port this provider is asked to bind
     * @return where a participant will read the public JWKS
     */
    public static String strUrlJwksOn(int nPort) {
        return strUrlIssuerOn(nPort) + STR_PATH_JWKS;
    }


    /**
     * @return the base url, which is also the `iss` claim of every token issued
     */
    public String strUrlIssuer() {
        return strUrlIssuerOn(port());
    }


    /**
     * @return where a participant reads the public JWKS; this is what
     *         `AuthOverlay.ofJwksAudience` takes
     */
    public String strUrlJwks() {
        return strUrlJwksOn(port());
    }


    /**
     * @return where a client posts its credentials; this is what scribe's
     *         `--pipeline-oauth-endpoint` takes
     */
    public String strUrlToken() {
        return strUrlIssuer() + STR_PATH_TOKEN;
    }


    /**
     * Issues a token WITHOUT the round trip, for a caller that holds the
     * registration anyway.
     *
     * This is not a shortcut past the endpoint - it mints from the same minter,
     * with the same spec, so a token from here and one from POST /token differ
     * only in their `jti` and their issue time.
     *
     * @param strClientId a registered client
     * @return a signed, compact-serialised JWT
     * @throws IdpException when the client is not registered or it is not
     *         running, because the issuer claim needs the bound port
     */
    public String mint(String strClientId) {
        IdpClient client;
        synchronized (this) {
            if (server == null)
                throw new IdpException("the identity provider is not running");
            client = mapClient.get(strClientId);
        }
        if (client == null)
            throw new IdpException("no client '" + strClientId + "' is registered");
        return minter.mint(client.tokenSpec(strUrlIssuer()));
    }


    public synchronized void stop() {
        HttpServer serverHere = server;
        if (serverHere != null) {
            serverHere.stop(N_STOP_DELAY);
            server = null;
        }

        ExecutorService executorHere = executor;
        if (executorHere != null) {
            executorHere.shutdownNow();
            executor = null;
        }
    }


    @Override
    public void close() {
        stop();
    }


    /**
     * Safe for a log line: what is served and to whom, never a secret and never
     * a token.
     *
     * @return the description
     */
    public String describe() {
        synchronized (this) {
            if (server == null)
                return "test idp, not running, " + mapClient.size() + " client(s)";
        }
        return "test idp on " + strUrlIssuer() + ", key " + material.describe() + ", "
                + mapClient.size() + " client(s)";
    }


    @Override
    public String toString() {
        return describe();
    }


    private void handleDiscovery(HttpExchange exchange) throws IOException {
        try {
            if (!isGet(exchange)) {
                respondError(exchange, 405, "invalid_request", "this endpoint takes GET");
                return;
            }
            respond(exchange, 200, STR_CONTENT_JSON, jsonDiscovery());
        }
        finally {
            exchange.close();
        }
    }


    /**
     * The PUBLIC half only. {@link JwksMaterial#renderPublicJwks()} strips `d`
     * and the CRT members, which is the difference between a JWKS endpoint and
     * a key leak.
     */
    private void handleJwks(HttpExchange exchange) throws IOException {
        try {
            if (!isGet(exchange)) {
                respondError(exchange, 405, "invalid_request", "this endpoint takes GET");
                return;
            }
            cntJwks.incrementAndGet();
            respond(exchange, 200, STR_CONTENT_JSON, material.renderPublicJwks());
        }
        finally {
            exchange.close();
        }
    }


    private void handleToken(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respondError(exchange, 405, "invalid_request", "the token endpoint takes POST");
                return;
            }

            Map<String, String> mapForm;
            try {
                mapForm = parseForm(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
            }
            catch (IllegalArgumentException ex) {
                respondError(exchange, 400, "invalid_request", "the body is not form-encoded");
                return;
            }

            String strGrant = mapForm.get("grant_type");
            if (!STR_GRANT_CLIENT_CREDENTIALS.equals(strGrant)) {
                respondError(exchange, 400, "unsupported_grant_type",
                        "only " + STR_GRANT_CLIENT_CREDENTIALS + " is supported");
                return;
            }

            Credential credential = credential(exchange, mapForm);
            if (credential == null) {
                respondError(exchange, 401, "invalid_client", "no client credentials were sent");
                return;
            }

            IdpClient client;
            synchronized (this) {
                client = mapClient.get(credential.strClientId());
            }

            // One answer for an unknown client and for a wrong secret. Telling
            // them apart is a client id oracle, and the caller can do nothing
            // useful with the difference either way.
            if (client == null || !secretMatches(client, credential.strSecret())) {
                log.info("token refused for client '{}'", credential.strClientId());
                respondError(exchange, 401, "invalid_client", "the client credentials were refused");
                return;
            }

            String strToken = minter.mint(client.tokenSpec(strUrlIssuer()));
            cntToken.incrementAndGet();
            log.info("token issued to {}", client.describe());
            respond(exchange, 200, STR_CONTENT_JSON,
                    jsonToken(strToken, client.ttl().toSeconds(), client.strScope()));
        }
        finally {
            exchange.close();
        }
    }


    /**
     * client_secret_basic first, then client_secret_post. Both are named in the
     * discovery document, and RFC 6749 allows either.
     *
     * @param exchange the request
     * @param mapForm its decoded body
     * @return the credentials, or null when neither was sent
     */
    private static Credential credential(HttpExchange exchange, Map<String, String> mapForm) {
        String strAuth = exchange.getRequestHeaders().getFirst("Authorization");
        if (strAuth != null && strAuth.regionMatches(true, 0, "Basic ", 0, 6)) {
            String strDecoded;
            try {
                strDecoded = new String(Base64.getDecoder().decode(strAuth.substring(6).trim()),
                        StandardCharsets.UTF_8);
            }
            catch (IllegalArgumentException ex) {
                return null;
            }

            int nColon = strDecoded.indexOf(':');
            if (nColon < 0)
                return null;
            return new Credential(strDecoded.substring(0, nColon), strDecoded.substring(nColon + 1));
        }

        String strClientId = mapForm.get("client_id");
        String strSecret = mapForm.get("client_secret");
        if (strClientId == null || strSecret == null)
            return null;
        return new Credential(strClientId, strSecret);
    }


    /**
     * Constant time, through {@link MessageDigest#isEqual}. The comparison is
     * of a shared secret and the endpoint is on loopback, so the timing channel
     * is theoretical - and a hand-written early-return comparison of a secret
     * is the kind of thing that gets copied out of a test harness into
     * something that is not one.
     */
    private static boolean secretMatches(IdpClient client, String strSecret) {
        if (strSecret == null)
            return false;
        return MessageDigest.isEqual(client.strClientSecret().getBytes(StandardCharsets.UTF_8),
                strSecret.getBytes(StandardCharsets.UTF_8));
    }


    private static boolean isGet(HttpExchange exchange) {
        return "GET".equalsIgnoreCase(exchange.getRequestMethod());
    }


    private String jsonDiscovery() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendMember(sb, "issuer", strUrlIssuer()).append(",\n");
        appendMember(sb, "token_endpoint", strUrlToken()).append(",\n");
        appendMember(sb, "jwks_uri", strUrlJwks()).append(",\n");
        sb.append("  \"grant_types_supported\": [\"").append(STR_GRANT_CLIENT_CREDENTIALS)
                .append("\"],\n");
        sb.append("  \"token_endpoint_auth_methods_supported\":"
                + " [\"client_secret_basic\", \"client_secret_post\"],\n");
        sb.append("  \"id_token_signing_alg_values_supported\": [\"RS256\"],\n");
        sb.append("  \"response_types_supported\": []\n");
        sb.append("}\n");
        return sb.toString();
    }


    private static String jsonToken(String strToken, long numExpiresIn, String strScope) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendMember(sb, "access_token", strToken).append(",\n");
        appendMember(sb, "token_type", "Bearer").append(",\n");
        sb.append("  \"expires_in\": ").append(numExpiresIn);
        if (strScope != null && !strScope.isBlank()) {
            sb.append(",\n");
            appendMember(sb, "scope", strScope);
        }
        sb.append("\n}\n");
        return sb.toString();
    }


    private static String jsonError(String strError, String strDescription) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendMember(sb, "error", strError).append(",\n");
        appendMember(sb, "error_description", strDescription).append("\n");
        sb.append("}\n");
        return sb.toString();
    }


    private static StringBuilder appendMember(StringBuilder sb, String strKey, String strValue) {
        return sb.append("  \"").append(escape(strKey)).append("\": \"").append(escape(strValue))
                .append("\"");
    }


    private static String escape(String strValue) {
        StringBuilder sb = new StringBuilder(strValue.length() + 8);
        for (int idx = 0; idx < strValue.length(); idx++) {
            char ch = strValue.charAt(idx);
            switch (ch) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    }
                    else {
                        sb.append(ch);
                    }
                    break;
            }
        }
        return sb.toString();
    }


    /**
     * @param strBody an application/x-www-form-urlencoded body
     * @return its decoded pairs, empty when there are none
     * @throws IllegalArgumentException when a percent escape is malformed
     */
    static Map<String, String> parseForm(String strBody) {
        Map<String, String> mapForm = new LinkedHashMap<>();
        if (strBody == null || strBody.isBlank())
            return mapForm;

        String[] arrPair = strBody.split("&");
        for (int idx = 0; idx < arrPair.length; idx++) {
            String strPair = arrPair[idx];
            if (strPair.isEmpty())
                continue;

            int nEq = strPair.indexOf('=');
            String strKey = nEq < 0 ? strPair : strPair.substring(0, nEq);
            String strValue = nEq < 0 ? "" : strPair.substring(nEq + 1);
            mapForm.put(URLDecoder.decode(strKey, StandardCharsets.UTF_8),
                    URLDecoder.decode(strValue, StandardCharsets.UTF_8));
        }
        return mapForm;
    }


    private static void respondError(HttpExchange exchange, int nStatus, String strError,
            String strDescription) throws IOException {
        if (nStatus == 401)
            exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"raposza\"");
        respond(exchange, nStatus, STR_CONTENT_JSON, jsonError(strError, strDescription));
    }


    private static void respond(HttpExchange exchange, int nStatus, String strContentType,
            String strBody) throws IOException {
        byte[] arrBody = strBody.getBytes(StandardCharsets.UTF_8);

        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", strContentType);
        // A token in a proxy cache is a token in a place nobody is looking.
        headers.set("Cache-Control", "no-store");
        headers.set("Pragma", "no-cache");

        exchange.sendResponseHeaders(nStatus, arrBody.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(arrBody);
        }
    }


    /**
     * @param strClientId what the request claims to be
     * @param strSecret what it presented; never logged
     */
    private record Credential(String strClientId, String strSecret) {

        @Override
        public String toString() {
            return "credential for " + strClientId;
        }

    }

}
