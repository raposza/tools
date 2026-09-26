// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.canton.install.VersionId;
import com.raposza.canton.pqs.PqsOAuth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.raposza.jwt.TokenShape;
import com.raposza.sandbox.app.AuthSettings;
import com.raposza.runtime.settings.RaposzaSettings;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The JWT mint, as an ordinary OS process this window starts and stops.
 *
 * <h2>Why a process and not a bean in this JVM</h2>
 *
 * The mint is a Spring Boot service and the Sandbox window is a Swing
 * application shaded into one jar. Putting Spring inside that jar means merging
 * `spring.factories` and `spring.handlers` through the shade plugin, an
 * autoconfiguration scan on every window open, and a Tomcat holding a port
 * whose lifetime is now a Swing window's. The sandbox already supervises Canton
 * and PostgreSQL as ordinary processes; this is the same shape and the mint
 * stays runnable without the window, which is what "standalone" was asked for.
 *
 * <h2>A missing jar is not a failure</h2>
 *
 * The window must open whether or not Raposza OIDC has been installed into
 * jar is LOOKED FOR and its absence is reported in the tab as something to
 * build, rather than thrown at a constructor that is halfway through building a
 * window.
 *
 * <h2>The issuer is pinned, and it is the url this window publishes</h2>
 *
 * The mint resolves its own issuer from the machine's routable address when it
 * is not told one. That is right for a mint anything on the network can reach
 * and wrong for this one, which is only ever reached on the loopback address -
 * the discovery endpoint refuses to bind anything else. So the child is started
 * with `--raposza.jwtmint.issuer` set to {@link #strUrlBase}, and the issuer
 * in a token, the issuer in the discovery document and the url the Sandbox
 * publishes are one string.
 *
 * <h2>The port is fixed for a machine, not for a build</h2>
 *
 * 32002 by default - an administrative port, `PortClass` - and not
 * negotiated between runs. Every `auth-services` block written
 * against this mint carries the url, so a port that moved from one start to the
 * next would silently invalidate configuration files that are still correct in
 * every other respect. It is a SETTING rather than a constant - a developer with
 * something already on 32002 has to be able to move it - and it is read from
 * {@link RaposzaSettings}, which is machine-wide and changes when the operator
 * changes it and at no other moment.
 *
 * <h2>The Sandbox is for test, so every password is 123456</h2>
 *
 * The operator's decision of 2026-09-23: every user the Sandbox signs in at
 * this provider, and the provider's own admin credential, take
 * {@link #STR_PASSWORD}, shown plainly and never generated. It is what the
 * SANDBOX passes to the child. The provider's own default is untouched - a
 * standalone Raposza OIDC still refuses to start with no admin password.
 *
 * Author Claude/bentzn
 */
public final class JwtMintProcess {

    /** Point this at a jar to override the search. */
    public static final String STR_PROP_JAR = "raposza.jwtmint.jar";

    /** Where the child JVM keeps its JWKS. It reads no settings file. */
    public static final String STR_PROP_DIR_KEYS = "raposza.jwtmint.dir-keys";

    /** What the child JVM puts in `iss` and in its discovery document. */
    public static final String STR_PROP_ISSUER = "raposza.jwtmint.issuer";

    /** The credential in front of the provider's write paths and its web UI. */
    public static final String STR_PROP_ADMIN_PASSWORD = "raposza.jwtmint.admin.password";

    /** The provider's own default admin name, which the Sandbox keeps. */
    public static final String STR_ADMIN_USER = "admin";

    /** Every Sandbox password - see the type comment. */
    public static final String STR_PASSWORD = "123456";

    /** The one address the child binds - see {@link #lstArgSpring}. */
    public static final String STR_ADDRESS = "127.0.0.1";

    /** The service carried inside the app jar, for a one-file deployment. */
    public static final String STR_RESOURCE_JAR = "raposza-oidc-app.jar";

    /** Where the carried copy is unpacked to, under the state root. */
    public static final String STR_DIR_CARRIED = "raposza-oidc";

    /** OpenID Connect Discovery 1.0 section 4. Every provider serves it. */
    public static final String STR_PATH_DISCOVERY = "/.well-known/openid-configuration";

    /**
     * THIS PROJECT'S OWN PATHS, and a convention rather than a standard.
     * Nothing in OpenID Connect makes either of them anybody else's - a
     * conforming provider publishes `jwks_uri` and `token_endpoint` in its
     * discovery document and they may be any url at all. So these are used for
     * the provider this window starts, and a foreign one is ASKED.
     */
    public static final String STR_PATH_JWKS = "/oauth2/jwks";

    public static final String STR_PATH_TOKEN = "/oauth2/token";

    /** How long a discovery read is given before it is called unreachable. */
    public static final Duration DUR_DISCOVERY = Duration.ofSeconds(3);

    /** The only grant this project's own provider is asked for. */
    public static final String STR_GRANT = "client_credentials";

    private static final String STR_GLOB_JAR = "raposza-oidc-server-*-app.jar";

    private static final ObjectMapper MAPPER_DOC = new ObjectMapper();

    /** What a foreign provider's discovery document said, or null. */
    private static volatile String strUrlJwksDiscovered;

    private static volatile String strUrlTokenDiscovered;

    /** Whether a read has been ATTEMPTED since the settings last moved. */
    private static volatile boolean flagDiscoveryTried;

    /** Where a Maven install leaves the artefact, under the local repository. */
    private static final String STR_PATH_ARTIFACT =
            "com/raposza/oidc/raposza-oidc-server";

    /** How long a stop waits for the service to go down on its own. */
    private static final int N_SECONDS_STOP = 10;

    private final Consumer<String> sinkLine;

    /**
     * Told the exit code when the service goes down ON ITS OWN.
     *
     * Without this, a service that died two seconds after start is
     * indistinguishable from one that never started, and both read as
     * `not running` on a header that polls. The whole diagnosis is in the
     * lines the process printed before it went, so the event exists to
     * send the reader to them.
     */
    private transient IntConsumer sinkExit;

    /**
     * True while a DELIBERATE stop is in progress, so the exit that stop
     * causes is not reported as a failure. A stop and a crash produce the
     * same exit from a pump thread's point of view; only this can tell
     * them apart.
     */
    private volatile boolean flagStopping;

    /** The last exit code, or -1 when nothing has exited. */
    private volatile int nExit = -1;

    private transient Process proc;

    private transient Thread threadPump;

    private transient Thread threadShutdown;

    private Path fileJar;


    /**
     * @param sinkLine told every line the service prints, from the pump thread
     */
    public JwtMintProcess(Consumer<String> sinkLine) {
        this.sinkLine = sinkLine;
    }


    /**
     * What the child JVM is told, and the reason each one is told to it.
     *
     * EXTRACTED SO IT CAN BE ASSERTED. Every one of these exists because
     * leaving it out produced a failure that was invisible at the point it was
     * caused, and a list built inside `start` can only be checked by launching
     * a process.
     *
     * @return the Spring arguments, in order; never empty
     */
    public static List<String> lstArgSpring() {
        List<String> lstOut = new ArrayList<>();
        lstOut.add("--server.port=" + nPort());
        // LOOPBACK, because every consumer of this child is on this machine:
        // the participant reads the JWKS at 127.0.0.1, the window and the
        // Workbench mint at 127.0.0.1, and the issuer below names 127.0.0.1.
        // The service's own default is every interface - its reason is a
        // Canton inside a virtual machine, which the Sandbox does not have -
        // and `/mint` answers anyone who reaches it, so every interface would
        // hand a participant-accepted token to the whole network.
        lstOut.add("--server.address=" + STR_ADDRESS);
        // THE CHILD IS A SEPARATE JVM and reads no settings file of its own.
        // Without this the window would move the keys and the mint would keep
        // signing with the ones at the compiled-in default - two JWKS, one url,
        // and a participant rejecting tokens for no visible reason.
        lstOut.add("--" + STR_PROP_DIR_KEYS + "=" + RaposzaSettings.current().dirMintKeys());
        // PINNED TO THE ADDRESS THE MINT IS REACHED ON. Left to resolve itself
        // it takes the machine's routable address, so a token minted here said
        // `iss: http://192.168.0.170:33301` while the discovery document the
        // Sandbox publishes said `127.0.0.1`. OpenID Connect Discovery 1.0
        // requires a document's issuer to equal the origin it was fetched from,
        // and a client validating `iss` against the published issuer refuses
        // every token. The endpoint refuses to leave loopback, so a routable
        // issuer names an address this application will not hand out.
        lstOut.add("--" + STR_PROP_ISSUER + "=" + strUrlBase());
        // THE ADMIN CREDENTIAL, which also closes the write paths the window
        // registers its web UI client and users through - `MintRegistration`
        // sends it as HTTP Basic.
        lstOut.add("--" + STR_PROP_ADMIN_PASSWORD + "=" + STR_PASSWORD);
        return lstOut;
    }


    /**
     * The provider this window points at, wherever it is.
     *
     * <h2>ONE STRING, TWO MODES</h2>
     *
     * With `oidc.url` unset this is the loopback address of the child process
     * this class starts, which is what it has always been. With it set no
     * child is started at all and this is the external provider - the
     * operator's instruction of 2026-09-21. Everything downstream reads this
     * rather than composing an address of its own, so the key set the
     * participant fetches, the issuer a token carries and the url the
     * discovery document publishes stay one string in both modes.
     *
     * @return the provider's base url, with no trailing slash
     */
    public static String strUrlBase() {
        String strExternal = strUrlExternal();
        return strExternal.isEmpty() ? "http://127.0.0.1:" + nPort() : strExternal;
    }


    /**
     * @return the external provider's base url, or "" when this window starts
     *         one of its own
     */
    public static String strUrlExternal() {
        return RaposzaSettings.current().strUrlOidc();
    }


    /**
     * @return whether this window points at a provider it does not run
     */
    public static boolean isExternal() {
        return !strUrlExternal().isEmpty();
    }


    /**
     * @return the port the mint binds, from the settings
     */
    public static int nPort() {
        return RaposzaSettings.current().nPortMint();
    }


    /**
     * @return this service's API documentation; against a foreign provider
     *         there is none to promise, so it is the base url instead
     */
    public static String strUrlSwagger() {
        return isExternal() ? strUrlBase() : strUrlBase() + "/swagger-ui.html";
    }


    /**
     * @return where the provider publishes its discovery document
     */
    public static String strUrlDiscovery() {
        return strUrlBase() + STR_PATH_DISCOVERY;
    }


    /**
     * Where the participant is told to fetch its verification keys.
     *
     * <h2>ASKED OF A FOREIGN PROVIDER, KNOWN FOR OUR OWN</h2>
     *
     * `/oauth2/jwks` is this project's path and no standard makes it anybody
     * else's. Hard-coding it against an external provider configures the
     * participant with a url that 404s, and the failure then arrives as a
     * start timeout rather than as a wrong address. So an external provider's
     * discovery document is read once and its `jwks_uri` wins.
     *
     * <h2>The one blocking call, and why it is tolerated</h2>
     *
     * This is called from the Sandbox form on the event dispatch thread. With
     * no external provider it does NO I/O at all, which is the default
     * configuration and the common case. With one it may block for at most
     * {@link #DUR_DISCOVERY}, once, and the result - including a failure - is
     * held until the settings move. A field that is filled in from a guess and
     * silently corrected a second later is worse than a pause nobody notices.
     *
     * @return the JWKS url a participant is configured with
     */
    public static String strUrlJwks() {
        ensureDiscovered(DUR_DISCOVERY);
        String strFound = strUrlJwksDiscovered;
        return strFound == null ? strUrlBase() + STR_PATH_JWKS : strFound;
    }


    /**
     * @return the token endpoint, as the provider's own document gives it
     */
    public static String strUrlTokenEndpoint() {
        ensureDiscovered(DUR_DISCOVERY);
        String strFound = strUrlTokenDiscovered;
        return strFound == null ? strUrlBase() + STR_PATH_TOKEN : strFound;
    }


    /**
     * @return whether a foreign provider's document has been read, which is
     *         always true when there is no foreign provider
     */
    public static boolean isDiscovered() {
        return !isExternal() || strUrlJwksDiscovered != null;
    }


    /**
     * Forgets what was read, so the next call asks again.
     *
     * CALLED WHEN THE SETTINGS MOVE. A url held from the provider that was
     * named before would configure a participant against a key set nobody
     * intended, and nothing on screen would say which provider it came from.
     */
    /**
     * The provider's own endpoints, for the discovery document's `oidc` block.
     *
     * ONE SPELLING FOR BOTH PRODUCERS. The window and the headless stack
     * publish the same block, and a second copy of it in either is the one
     * that goes stale. The DISCOVERY DOCUMENT URL LEADS, because that is the
     * one endpoint a conforming client needs to find the rest - operator
     * instruction, 2026-09-21.
     *
     * @return never null
     */
    public static Map<String, Object> mapProvider() {
        Map<String, Object> mapOut = new LinkedHashMap<>();
        mapOut.put("discovery", strUrlDiscovery());
        mapOut.put("issuer", strUrlBase());
        // KEPT BESIDE THE DISCOVERY URL rather than replaced by it. Canton is
        // configured with a literal key set url - `AuthOverlay.ofJwksUrl` - so
        // this is what a participant was actually told, and a consumer
        // comparing it against what the provider publishes is how a mismatch
        // is found instead of guessed at.
        mapOut.put("jwks", strUrlJwks());
        mapOut.put("token_endpoint", strUrlTokenEndpoint());
        mapOut.put("grant_type", STR_GRANT);
        return mapOut;
    }


    public static synchronized void forgetDiscovered() {
        strUrlJwksDiscovered = null;
        strUrlTokenDiscovered = null;
        flagDiscoveryTried = false;
    }


    /**
     * Forgets and reads again, as ONE operation.
     *
     * WHY IT IS NOT TWO CALLS. A failed read is remembered, so that the event
     * thread is not made to wait on a dead host once a second; but a provider
     * that was down at window open and started afterwards has to be picked up
     * without the operator touching the settings. So the tab retries from a
     * worker thread through this, and it holds the lock across both halves -
     * two calls would leave a window in which the event thread reads a null
     * key set url and falls back to a guessed path.
     *
     * @param timeout how long to give the read
     * @return whether a usable document is now held
     */
    public static synchronized boolean rediscover(Duration timeout) {
        forgetDiscovered();
        return ensureDiscovered(timeout);
    }


    /**
     * Reads the provider's discovery document once, and holds what it said.
     *
     * @param timeout how long to give the read
     * @return whether a usable document is now held
     */
    public static synchronized boolean ensureDiscovered(Duration timeout) {
        if (!isExternal()) {
            strUrlJwksDiscovered = null;
            strUrlTokenDiscovered = null;
            return true;
        }
        if (strUrlJwksDiscovered != null)
            return true;
        if (flagDiscoveryTried)
            return false;

        flagDiscoveryTried = true;
        Map<String, String> mapDoc = mapDiscovered(timeout);
        if (mapDoc == null)
            return false;
        strUrlJwksDiscovered = strTrimmed(mapDoc.get("jwks_uri"));
        strUrlTokenDiscovered = strTrimmed(mapDoc.get("token_endpoint"));
        return strUrlJwksDiscovered != null;
    }


    /**
     * Fetches the provider's discovery document.
     *
     * BLOCKING. A provider that does not answer is not an error here: the
     * caller reports it as the provider being down, which is the same
     * condition said in a sentence somebody can act on.
     *
     * @param timeout how long to give the whole exchange
     * @return `issuer`, `jwks_uri` and `token_endpoint` as served, or null
     *         when nothing answered or the body was not a document
     */
    public static Map<String, String> mapDiscovered(Duration timeout) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(strUrlDiscovery()))
                    .timeout(timeout).GET().build();
            HttpResponse<String> resp = client.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200)
                return null;

            JsonNode nodeRoot = MAPPER_DOC.readTree(resp.body());
            Map<String, String> mapOut = new LinkedHashMap<>();
            putIf(mapOut, nodeRoot, "issuer");
            putIf(mapOut, nodeRoot, "jwks_uri");
            putIf(mapOut, nodeRoot, "token_endpoint");
            return mapOut.isEmpty() ? null : mapOut;
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
        catch (IOException | RuntimeException ex) {
            // Not up, not a provider, or not serving JSON. All three are one
            // answer to the caller: there is nothing there to use.
            return null;
        }
    }


    private static void putIf(Map<String, String> map, JsonNode nodeRoot, String strKey) {
        String strVal = nodeRoot.path(strKey).asText("");
        if (!strVal.isBlank())
            map.put(strKey, strVal.trim());
    }


    private static String strTrimmed(String strValue) {
        return (strValue == null || strValue.isBlank()) ? null : strValue.trim();
    }


    /**
     * The query that mints a token the participant this window starts accepts.
     *
     * ONE SPELLING OF THE RULE. The JWT tab built this string twice, and the
     * rule in it - the shape decides between `scope` and `aud`, and the ceiling
     * comes from the same settings the overlay was rendered from - has to match
     * what the participant was configured with or nothing verifies.
     *
     * @param auth what the participant checks
     * @param strUser the `sub`, which is a ledger user id
     * @param nameParticipant the participant node name, for the audience
     * @return the path and query, relative to {@link #strUrlBase}
     */
    public static String strMintQuery(AuthSettings auth, VersionId version, String strUser,
            String nameParticipant) {
        StringBuilder sb = new StringBuilder("/mint.txt?sub=");
        sb.append(enc(strUser));
        // THE SAME LIFETIME THE PARTICIPANT WAS CONFIGURED WITH. Below
        // AuthOverlay.VER_FLOOR_TOKEN_LIFE the participant enforces five
        // minutes and cannot be told otherwise, measured on 3.4.9, so a mint
        // that ignored the version would issue tokens refused on arrival.
        sb.append("&ttlSeconds=").append(auth.ttlToken(version).toSeconds());
        if (auth.shape() == TokenShape.SCOPE)
            sb.append("&scope=").append(enc(auth.strScopeEffective()));
        else
            sb.append("&aud=").append(enc(auth.strAudienceFor(nameParticipant)));
        // THE PARTICIPANT'S OWN SECRET, sent to the mint, so the token is
        // signed with the key the participant was configured to verify
        // against. Without it the mint signs with its own random HS256 key,
        // which no participant has ever been told about - so before this every
        // token minted for a `unsafe-jwt-hmac-256` stack was refused, and the
        // symmetric column could be configured but never exercised.
        //
        // The ALGORITHM has to be named as well. The service's default is
        // asymmetric, and a secret sent without an HS* algorithm is a secret
        // it has no use for.
        String strAlg = auth.mode().strAlgMint();
        if (strAlg != null)
            sb.append("&alg=").append(strAlg);
        if (auth.mode() == AuthSettings.Mode.UNSAFE_HMAC_256)
            sb.append("&secret=").append(enc(auth.strSecretEffective()));
        return sb.toString();
    }


    /**
     * The token endpoint scribe re-mints from, carrying in its query string
     * everything the mint needs that OAuth has no field for.
     *
     * ONE SPELLING OF THE SAME RULE as {@link #strMintQuery}: the shape decides
     * between a scope and an audience, the lifetime comes from the version, and
     * a symmetric participant's own secret goes with the request or the mint
     * signs with a key nothing was ever told about.
     *
     * THE LIFETIME IS SENT, and this is the one place the two spellings differ
     * in why. The mint's own default is 86400 s, from
     * `raposza.jwtmint.ttl-seconds`, and a participant below
     * `AuthOverlay.VER_FLOOR_TOKEN_LIFE` refuses anything over 240 s outright -
     * so a request that left it out would authenticate nowhere on exactly the
     * column this exists for.
     *
     * THE SCOPE IS NOT SENT. scribe puts it in the form body from
     * `--pipeline-oauth-scope`, and the same parameter arriving in both halves
     * of one request is a precedence question nothing here has measured.
     *
     * THE SUBJECT IS SENT, and it is not a duplicate of the client id.
     * MEASURED: scribe transmits its client credentials the way RFC
     * 6749 section 2.3.1 prefers - as HTTP Basic, not as body parameters - and
     * `OAuthController.mapTokenForm` reads `client_id` only as a
     * `@RequestParam`. So the client id arrived as null, the subject fell back
     * to `raposza.jwtmint.default-subject`, and Canton refused every call
     * with `PERMISSION_DENIED ... UserNotFound(raposza)` on a token whose
     * signature, scope and lifetime it had just accepted. `sub` wins over
     * `client_id` in that controller and reaches it whatever scribe does with
     * the credentials, so it is the one that carries the rule.
     *
     * @param auth what the participant checks
     * @param version the Canton being started, which caps the lifetime
     * @param strUser the ledger user the token speaks for
     * @param nameParticipant the participant node name, for the audience
     * @return an absolute URL
     */
    public static String strUrlToken(AuthSettings auth, VersionId version, String strUser,
            String nameParticipant) {
        StringBuilder sb = new StringBuilder(strUrlBase());
        sb.append("/oauth2/token?sub=").append(enc(strUser));
        sb.append("&ttl_seconds=").append(auth.ttlToken(version).toSeconds());
        sb.append("&shape=").append(auth.shape().name());
        if (auth.shape() != TokenShape.SCOPE)
            sb.append("&audience=").append(enc(auth.strAudienceFor(nameParticipant)));
        String strAlg = auth.mode().strAlgMint();
        if (strAlg != null)
            sb.append("&alg=").append(strAlg);
        if (auth.mode() == AuthSettings.Mode.UNSAFE_HMAC_256)
            sb.append("&secret=").append(enc(auth.strSecretEffective()));
        return sb.toString();
    }


    /**
     * @param auth what the participant checks
     * @param version the Canton being started
     * @param strUser the ledger user PQS speaks as. It is sent as the token's
     *        `sub` AND set as the OAuth client id: the subject is what Canton
     *        resolves to a user, and the client id is what scribe presents to
     *        the mint. A subject naming no user authenticates and then
     *        authorises nothing.
     * @param nameParticipant the participant node name
     * @return what scribe is handed, ready to render
     */
    public static PqsOAuth oauthPqs(AuthSettings auth, VersionId version, String strUser,
            String nameParticipant) {
        return new PqsOAuth(strUrlToken(auth, version, strUser, nameParticipant), strUser, null,
                auth.shape() == TokenShape.SCOPE ? auth.strScopeEffective() : null);
    }


    /**
     * Mints a token, waiting for the service to come up.
     *
     * BLOCKING, and called from a start thread rather than the event dispatch
     * thread: the mint is a Spring Boot process the window started moments ago
     * and it answers when it answers. Polled rather than slept on, so a service
     * that is already up costs one request.
     *
     * @param auth what the participant checks
     * @param version the Canton being started, which caps the lifetime
     * @param strUser the ledger user the token speaks for
     * @param nameParticipant the participant node name
     * @param timeout how long to keep trying
     * @return the compact serialisation, or null when the service never
     *         answered - what that means for the run is the caller's to say
     */
    public static String strMintBlocking(AuthSettings auth, VersionId version, String strUser,
            String nameParticipant, Duration timeout) {
        return strMintBlocking(auth, version, strUser, nameParticipant, timeout, null);
    }


    /**
     * The same, telling the caller the moment the service first answers.
     *
     * WHY FIRST CONTACT AND NOT A SEPARATE READINESS PROBE. The wait here is a
     * Spring Boot start - tens of seconds - and the mint itself is one
     * signature. So the first HTTP RESPONSE of any status IS the moment the
     * service came up, and the token follows it within a millisecond. A second
     * endpoint polled for readiness would measure the same instant and cost
     * another request to do it.
     *
     * The callback runs ON THIS THREAD, once, before the response is looked
     * at. Anything slow in it delays the mint by that much.
     *
     * @param auth what the participant checks
     * @param version the Canton being started, which caps the lifetime
     * @param strUser the ledger user the token speaks for
     * @param nameParticipant the participant node name
     * @param timeout how long to keep trying
     * @param runContact told that the service answered, or null for none
     * @return the compact serialisation, or null when the service never
     *         answered
     */
    public static String strMintBlocking(AuthSettings auth, VersionId version, String strUser,
            String nameParticipant, Duration timeout, Runnable runContact) {
        String strUrl = strUrlBase() + strMintQuery(auth, version, strUser, nameParticipant);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build();
        long nMsDeadline = System.currentTimeMillis() + timeout.toMillis();
        boolean flagContact = false;

        while (System.currentTimeMillis() < nMsDeadline) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(strUrl))
                        .timeout(Duration.ofSeconds(5)).GET().build();
                HttpResponse<String> resp = client.send(req,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (!flagContact) {
                    flagContact = true;
                    if (runContact != null)
                        runContact.run();
                }
                if (resp.statusCode() == 200 && !resp.body().isBlank())
                    return resp.body().trim();
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
            catch (IOException ex) {
                // not up yet, or not coming up at all; the deadline decides
            }

            try {
                Thread.sleep(500L);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }


    /**
     * Waits for the JWKS the participant is configured to read, MINTING
     * NOTHING.
     *
     * <h2>Why this is not just {@link #strMintBlocking} with the result
     * ignored</h2>
     *
     * The blocking mint was doing two jobs: producing the credential PQS
     * presents, and being the gate that holds Canton back until the mint's
     * Spring Boot is answering - because the participant fetches the JWKS
     * while it starts. With PQS off the first job has no consumer
     * (`SandboxService.pqsFor` returns null and neither the token nor the
     * OAuth endpoint is read), but the second still has to happen or Canton
     * races a service that is not up.
     *
     * So the wait moves onto the endpoint that is actually needed. It is also
     * the honest one: a 200 from `/oauth2/jwks` is the participant's own
     * precondition, where a successful mint was only a proxy for it.
     *
     * BLOCKING, and called from a start thread for the same reason
     * {@link #strMintBlocking} is.
     *
     * @param timeout how long to keep trying
     * @param runContact told the moment the service first answers, or null
     * @return whether the JWKS was served inside the timeout
     */
    public static boolean isJwksServing(Duration timeout, Runnable runContact) {
        return isJwksServing(strUrlJwks(), timeout, runContact);
    }


    /**
     * The same, against a NAMED key set.
     *
     * <h2>Why the url became a parameter</h2>
     *
     * The participant is configured from `AuthSettings.strUrlJwksEffective` -
     * the typed override when there is one, the provider's own url otherwise.
     * This wait exists to hold Canton back until the key set the participant
     * is about to fetch is being served, and polling this class's own address
     * instead means a stack pointed anywhere else waits on a service it will
     * never read. Found by reading the call site, not by running it.
     *
     * @param strUrl the key set to poll
     * @param timeout how long to keep trying
     * @param runContact told the moment it first answers, or null
     * @return whether that JWKS was served inside the timeout
     */
    public static boolean isJwksServing(String strUrl, Duration timeout,
            Runnable runContact) {
        if (strUrl == null || strUrl.isBlank())
            return false;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build();
        long nMsDeadline = System.currentTimeMillis() + timeout.toMillis();
        boolean flagContact = false;

        while (System.currentTimeMillis() < nMsDeadline) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(strUrl))
                        .timeout(Duration.ofSeconds(5)).GET().build();
                HttpResponse<String> resp = client.send(req,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (!flagContact) {
                    flagContact = true;
                    if (runContact != null)
                        runContact.run();
                }
                if (resp.statusCode() == 200 && !resp.body().isBlank())
                    return true;
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
            catch (IOException ex) {
                // not up yet, or not coming up at all; the deadline decides
            }

            try {
                Thread.sleep(500L);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }


    private static String enc(String strValue) {
        return URLEncoder.encode(strValue, StandardCharsets.UTF_8);
    }

    /**
     * Looks for the packaged service.
     *
     * Three places, in order: an explicit system property; the LOCAL MAVEN
     * REPOSITORY, where `raposza_oidc/build.sh` installs it; and the copy
     * carried inside this application's own jar.
     *
     * <h2>Why the local repository and not a sibling directory</h2>
     *
     * Until 2026-09-19 this walked up from the working directory looking for
     * `apps/jwtmint/target`, because the service was a module of this reactor.
     * It is Raposza OIDC now - a separate tree, released separately, and
     * consumed as an artefact - so the place it reliably is, is the place
     * Maven puts it. That is also the only path that keeps working once it is
     * resolved from a remote repository rather than built here.
     *
     * Every version directory under the artefact is considered and the NEWEST
     * jar wins, so a developer who rebuilds gets the rebuild without naming a
     * version anywhere.
     *
     * @return the newest matching jar, or null when there is none
     */
    public static Path fileJarFound() {
        String strProp = System.getProperty(STR_PROP_JAR);
        if (strProp != null && !strProp.isBlank()) {
            Path fileNamed = Path.of(strProp.trim());
            return Files.isRegularFile(fileNamed) ? fileNamed : null;
        }

        Path fileBest = null;
        Path dirArtifact = dirRepoLocal().resolve(STR_PATH_ARTIFACT);
        if (Files.isDirectory(dirArtifact)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirArtifact)) {
                for (Path dirVersion : stream) {
                    if (!Files.isDirectory(dirVersion))
                        continue;
                    Path fileHere = fileNewestIn(dirVersion);
                    if (fileHere == null)
                        continue;
                    if (fileBest == null || nModifiedOf(fileHere) > nModifiedOf(fileBest))
                        fileBest = fileHere;
                }
            }
            catch (IOException ex) {
                // An unreadable repository is the same as an empty one here.
                fileBest = null;
            }
        }

        // LAST, so an installed jar still wins. A developer who rebuilds the
        // service expects the rebuild to be what runs; the carried copy is for
        // the machine that has one file and nothing else.
        return fileBest != null ? fileBest : fileCarried();
    }


    /**
     * @return the local Maven repository, `maven.repo.local` when it is set and
     *         `~/.m2/repository` otherwise
     */
    private static Path dirRepoLocal() {
        String strSet = System.getProperty("maven.repo.local");
        if (strSet != null && !strSet.isBlank())
            return Path.of(strSet.trim());
        return Path.of(System.getProperty("user.home", ".")).resolve(".m2")
                .resolve("repository");
    }


    /**
     * <b>What makes the app jar the whole application.</b> The mint is a
     * separate executable jar and has to be one - it is launched as a child
     * process - so a single-file deployment has to carry it and put it on
     * disk before it can be run.
     *
     * Unpacked under the state root rather than a temp directory, so it
     * survives a reboot and is found beside everything else this application
     * owns.
     *
     * @return the unpacked copy, or null when this build carries none
     */
    private static Path fileCarried() {
        Path fileOut = RaposzaSettings.current().dirHome()
                .resolve(STR_DIR_CARRIED).resolve(STR_RESOURCE_JAR);
        try (InputStream in = JwtMintProcess.class.getClassLoader()
                .getResourceAsStream(STR_RESOURCE_JAR)) {
            if (in == null)
                return null;

            Files.createDirectories(fileOut.getParent());
            // WRITTEN THEN MOVED. A reader that finds a half-written jar gets
            // an error about a corrupt archive, which sends them looking at
            // the mint rather than at the interrupted unpack that caused it.
            Path filePart = fileOut.resolveSibling(STR_RESOURCE_JAR + ".part");
            Files.copy(in, filePart, StandardCopyOption.REPLACE_EXISTING);
            Files.move(filePart, fileOut, StandardCopyOption.REPLACE_EXISTING);
            return fileOut;
        }
        catch (IOException ex) {
            // The one on disk is as good as the one that would not unpack.
            return Files.isRegularFile(fileOut) ? fileOut : null;
        }
    }


    /**
     * Starts the service, doing nothing when it is already up OR when an
     * external provider is named.
     *
     * NOTHING IS STARTED AGAINST AN EXTERNAL PROVIDER. A second provider on
     * the loopback address would serve a key set nothing is configured to
     * read, and stopping it later would report a shutdown that never concerned
     * the service the participant actually uses.
     *
     * @throws IllegalStateException when no jar can be found, naming what to
     *         build
     */
    public void start() {
        if (isRunning() || isExternal())
            return;

        Path fileFound = fileJarFound();
        if (fileFound == null) {
            throw new IllegalStateException("no " + STR_GLOB_JAR + " was found."
                    + " Build it with:\n\n"
                    + "    (cd ../raposza_oidc && ./build.sh)\n\n"
                    + "or point -D" + STR_PROP_JAR + " at one.");
        }
        this.fileJar = fileFound;
        this.flagStopping = false;
        this.nExit = -1;

        List<String> lstCmd = new ArrayList<>();
        lstCmd.add(strJavaCommand());
        lstCmd.add("-jar");
        lstCmd.add(fileFound.toString());
        lstCmd.addAll(lstArgSpring());

        ProcessBuilder bld = new ProcessBuilder(lstCmd);
        // ONE STREAM. The tab shows what the service printed, and a reader
        // trying to work out why a start failed should not have to know which
        // of two streams a line came from.
        bld.redirectErrorStream(true);

        Process procNew;
        try {
            procNew = bld.start();
        }
        catch (IOException ex) {
            throw new IllegalStateException("could not start " + fileFound + ": "
                    + ex.getMessage(), ex);
        }
        this.proc = procNew;

        Thread threadNew = new Thread(() -> pump(procNew), "jwtmint-pump");
        threadNew.setDaemon(true);
        threadNew.start();
        this.threadPump = threadNew;

        // Last defence only. The window stops it on close; this catches a kill
        // that never reaches the window.
        Thread threadHook = new Thread(procNew::destroy, "jwtmint-shutdown");
        Runtime.getRuntime().addShutdownHook(threadHook);
        this.threadShutdown = threadHook;
    }


    /**
     * Stops the service and waits for it to go, forcing it when it will not.
     */
    /**
     * @param sinkExitNew told the exit code from the pump thread when the
     *        service goes down by itself; null for none
     */
    public void useExitSink(IntConsumer sinkExitNew) {
        this.sinkExit = sinkExitNew;
    }


    /**
     * @return the last exit code, or -1 when nothing has exited
     */
    public int nExit() {
        return nExit;
    }


    public void stop() {
        this.flagStopping = true;
        Process procHere = proc;
        this.proc = null;
        removeHook();

        if (procHere == null || !procHere.isAlive())
            return;

        procHere.destroy();
        try {
            if (!procHere.waitFor(N_SECONDS_STOP, TimeUnit.SECONDS))
                procHere.destroyForcibly();
        }
        catch (InterruptedException ex) {
            procHere.destroyForcibly();
            Thread.currentThread().interrupt();
        }

        Thread threadHere = threadPump;
        this.threadPump = null;
        if (threadHere != null)
            threadHere.interrupt();
    }


    public boolean isRunning() {
        Process procHere = proc;
        return procHere != null && procHere.isAlive();
    }


    /**
     * @return the jar that is running, or null when nothing is
     */
    public Path fileJar() {
        return fileJar;
    }


    private void pump(Process procHere) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                procHere.getInputStream(), StandardCharsets.UTF_8))) {
            String strLine = reader.readLine();
            while (strLine != null) {
                sinkLine.accept(strLine);
                strLine = reader.readLine();
            }
        }
        catch (IOException ex) {
            // The process went away while it was being read, which is what a
            // stop looks like from here.
        }

        // THE STREAM ENDING IS THE PROCESS ENDING. Waiting here rather than
        // polling elsewhere means the code is read once, by the thread that
        // was already attached to it, and is available before anything asks.
        int nCode;
        try {
            nCode = procHere.waitFor();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        }
        this.nExit = nCode;

        IntConsumer sinkHere = sinkExit;
        if (sinkHere != null && !flagStopping)
            sinkHere.accept(nCode);
    }


    private void removeHook() {
        Thread threadHere = threadShutdown;
        if (threadHere == null)
            return;
        this.threadShutdown = null;
        try {
            Runtime.getRuntime().removeShutdownHook(threadHere);
        }
        catch (IllegalStateException ex) {
            // The JVM is already shutting down; the hook is running or has run.
        }
    }


    /**
     * @return the java this window is running on, so the child cannot end up on
     *         a different one because of a PATH
     */
    private static String strJavaCommand() {
        String strHome = System.getProperty("java.home");
        if (strHome == null || strHome.isBlank())
            return "java";
        Path fileJava = Path.of(strHome, "bin", "java");
        if (Files.isRegularFile(fileJava))
            return fileJava.toString();
        Path fileJavaExe = Path.of(strHome, "bin", "java.exe");
        return Files.isRegularFile(fileJavaExe) ? fileJavaExe.toString() : "java";
    }


    /**
     * @return the directory holding the jar this class came from, or null
     */
    private static Path dirOfOwnJar() {
        try {
            java.security.CodeSource src =
                    JwtMintProcess.class.getProtectionDomain().getCodeSource();
            if (src == null || src.getLocation() == null)
                return null;
            Path fileSelf = Path.of(src.getLocation().toURI());
            return Files.isDirectory(fileSelf) ? fileSelf : fileSelf.getParent();
        }
        catch (RuntimeException | java.net.URISyntaxException ex) {
            return null;
        }
    }


    /**
     * @param dir a directory that may not exist
     * @return the newest matching jar in it, or null
     */
    private static Path fileNewestIn(Path dir) {
        if (dir == null || !Files.isDirectory(dir))
            return null;

        Path fileBest = null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, STR_GLOB_JAR)) {
            for (Path fileHere : stream) {
                if (fileBest == null || nModifiedOf(fileHere) > nModifiedOf(fileBest))
                    fileBest = fileHere;
            }
        }
        catch (IOException ex) {
            return null;
        }
        return fileBest;
    }


    private static long nModifiedOf(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        }
        catch (IOException ex) {
            return 0L;
        }
    }

}
