// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * What a running native LocalNet can be asked WITHOUT knowing an API this
 * project has not measured.
 *
 * THE BOUNDARY IS DELIBERATE AND IT IS THE POINT. Every check here rests on
 * something already measured: the port table out of env/common.env, the three
 * readyz endpoints out of docker/splice/health-check.sh, and PostgreSQL's own
 * catalogue. Nothing here invents a Splice REST path, a wallet endpoint or a
 * Ledger API call, because none of those has been read from the bundle. A probe
 * that guesses a path reports a 404 as a broken node.
 *
 * So this answers: is every port that should be listening listening, is every
 * port that should NOT be listening closed, do the health endpoints answer, did
 * all twelve databases actually get a schema, and how much connection headroom
 * is left. It does not answer whether a transaction can be submitted - that
 * needs a Ledger API client, and this project has none for Canton 3.
 *
 * Author Claude/bentzn
 */
public final class LocalNetProbe {

    private static final Duration TIMEOUT_HTTP = Duration.ofSeconds(5);

    private static final int N_TIMEOUT_TCP_MS = 2000;

    private static final int N_BODY_SHOWN = 240;

    private static final int N_DAR_SHOWN = 40;

    private static final int N_DEPTH_DAR = 8;

    private static final String[] ARR_ROLE = { "sv", "app-provider", "app-user" };

    private static final String[] ARR_AUTH_MARK = { "AUTH", "OIDC", "OAUTH", "TOKEN", "CLIENT",
            "AUDIENCE", "ISSUER", "JWK", "JWT", "KEYCLOAK", "SECRET", "PASSWORD", "USER",
            "LEDGER_API" };

    private static final String[] ARR_SECRET_MARK = { "SECRET", "PASSWORD", "TOKEN",
            "PRIVATE" };

    /**
     * The endpoint that cannot answer without a token, and is therefore the one
     * that settles whether a token works.
     */
    private static final String STR_PATH_WHOAMI = "/v2/authenticated-user";

    /**
     * Read endpoints, taken from the route table this build serves at
     * /docs/openapi rather than from anybody's recollection.
     */
    private static final String[] ARR_JSON_READ = { "/v2/version", "/v2/parties",
            "/v2/users", "/v2/packages", "/v2/state/ledger-end",
            "/v2/state/connected-synchronizers" };

    private final String strHost;
    private final int nPortPg;
    private final LocalNetPorts ports;
    private final LocalNetStager stager;
    private final List<String> lstFailure = new ArrayList<>();

    public LocalNetProbe(String strHost, int nPortPg) {
        this(strHost, nPortPg, null);
    }


    /**
     * @param strHost what the stack is dialled on
     * @param nPortPg the embedded cluster's port
     * @param stager the run's own stager, or null when the probe is standalone
     *        and was given no bundle - the auth pass is skipped without one
     */
    public LocalNetProbe(String strHost, int nPortPg, LocalNetStager stager) {
        this(strHost, new LocalNetPorts(LocalNetPorts.ofBundle().mapPort(), nPortPg), stager);
    }


    /**
     * THE NUMBERING THE STACK WAS STARTED ON - `todo.md` A-42. Until
     * 2026-09-23 every port check here dialled the bundle's own numbering,
     * `LocalNetSpec`, whatever the runner had moved the stack to, so a stack on
     * the 30xxx block was probed on ports nothing listened on.
     *
     * @param strHost what the stack is dialled on
     * @param portsNew what it binds
     * @param stager the run's own stager, or null when the probe is standalone
     */
    public LocalNetProbe(String strHost, LocalNetPorts portsNew, LocalNetStager stager) {
        this.strHost = strHost;
        this.ports = portsNew;
        this.nPortPg = portsNew.nPortPostgres();
        this.stager = stager;
    }


    /**
     * usage: LocalNetProbe [host] [pg-port] [splice-version|bundle-path]
     * [--first &lt;port&gt;] [--ui-first &lt;port&gt;]
     *
     * WITHOUT `--first` THE BUNDLE'S OWN NUMBERING IS PROBED, which is what the
     * Windows runner binds. The runner's own re-probe line names both flags.
     */
    public static void main(String[] args) {
        List<String> lstArg = new ArrayList<>();
        int nPortFirst = 0;
        int nPortUiFirst = LocalNetPorts.N_PORT_UI_FIRST_DEFAULT;
        for (int cntArg = 0; cntArg < args.length; cntArg++) {
            if ("--first".equals(args[cntArg]) && cntArg + 1 < args.length)
                nPortFirst = Integer.parseInt(args[++cntArg]);
            else if ("--ui-first".equals(args[cntArg]) && cntArg + 1 < args.length)
                nPortUiFirst = Integer.parseInt(args[++cntArg]);
            else
                lstArg.add(args[cntArg]);
        }
        args = lstArg.toArray(new String[0]);
        String strHost = args.length > 0 ? args[0] : "127.0.0.1";
        int nPortPg = args.length > 1 ? Integer.parseInt(args[1]) : LocalNetSpec.N_PORT_PG;

        LocalNetStager stager = null;
        if (args.length > 2) {
            Path dirBundle = Path.of(args[2]);
            if (!Files.isDirectory(dirBundle))
                dirBundle = SpliceInstallations.dirBundle(args[2]);
            if (Files.isDirectory(dirBundle)) {
                stager = new LocalNetStager(dirBundle,
                        Path.of(System.getProperty("java.io.tmpdir"), "localnet-probe"));
            }
            else {
                System.out.println("NOT A BUNDLE: " + dirBundle);
            }
        }
        LocalNetProbe probe = nPortFirst > 0
                ? new LocalNetProbe(strHost,
                        LocalNetPorts.ofFirst(nPortFirst, nPortUiFirst, nPortPg), stager)
                : new LocalNetProbe(strHost, nPortPg, stager);
        System.exit(probe.run() ? 0 : 1);
    }


    /**
     * @return whether every check that is expected to hold did hold
     */
    public boolean run() {
        runChecks();
        return printVerdict();
    }


    /**
     * The checks WITHOUT the verdict. Split out because the runner prints a
     * closing block of its own after this, and a verdict buried thirty lines
     * above the last line of the output is a verdict nobody reads.
     */
    public void runChecks() {
        System.out.println("=== LocalNet probe, " + strHost + " ===");
        probePortsRequired();
        probePortsObserved();
        probeHealth();
        probeJsonLedgerApi();
        probeAuth();
        probeDatabases();
        probeDars();
        probeConnections();
    }


    /**
     * A node that came up and then fell over between the gates and here shows
     * up as a port the runner reported UP and this reports closed.
     */
    private void probePortsRequired() {
        System.out.println("--- ports required");
        int cntOpen = 0;
        Map<String, Integer> mapPort = ports.mapPortRequired();
        for (Map.Entry<String, Integer> entry : mapPort.entrySet()) {
            boolean flagOpen = tcp(entry.getValue());
            if (flagOpen) {
                cntOpen++;
            }
            else {
                lstFailure.add("closed: " + entry.getKey() + " (" + entry.getValue() + ")");
            }
            System.out.println("  " + (flagOpen ? "open  " : "CLOSED") + "  "
                    + entry.getValue() + "  " + entry.getKey());
        }
        System.out.println("  " + cntOpen + " of " + mapPort.size() + " open");
    }


    /**
     * THE TWELVE ARE ASSERTED SINCE 2026-09-23 - `todo.md` L-3. The per-role
     * health and JSON ports, the app-sequencer's two and the app-mediator's
     * one were open on three runs and only reported; a closed one is now a
     * failure like a closed required port. D-201 is why three agreeing runs
     * were not taken as a property on their own - the assertion is what turns
     * a fourth run that disagrees into a finding.
     *
     * THE WEB UI PORTS STAY REPORTED. `--no-ui` leaves them closed by design,
     * and `LocalNetWeb.lstCheck` is what asserts a page.
     */
    private void probePortsObserved() {
        System.out.println("--- ports asserted since L-3, and the web UI ports reported");
        for (Map.Entry<String, Integer> entry : ports.mapPortObserved().entrySet()) {
            boolean flagOpen = tcp(entry.getValue());
            boolean flagUi = entry.getKey().endsWith(" UI");
            if (!flagOpen && !flagUi)
                lstFailure.add("closed: " + entry.getKey() + " (" + entry.getValue() + ")");
            System.out.println("  " + (flagOpen ? "open  " : flagUi ? "closed" : "CLOSED")
                    + "  " + entry.getValue() + "  " + entry.getKey());
        }
    }


    /**
     * The readyz endpoints are the only HTTP paths this project has measured.
     * They answer only once a node has actually initialised, which is why the
     * runner gates on them and why they are worth re-asking here.
     */
    private void probeHealth() {
        System.out.println("--- health endpoints");
        for (Map.Entry<String, String> entry : ports.mapReadyz(strHost).entrySet()) {
            int nStatus = http(entry.getValue());
            if (nStatus < 200 || nStatus >= 300)
                lstFailure.add("readyz " + entry.getKey() + " returned " + nStatus);
            System.out.println("  " + nStatus + "  " + entry.getKey() + "  "
                    + entry.getValue());
        }
    }


    /**
     * THE LEDGER, ASKED AS A USER. Auth is `unsafe-jwt-hmac-256` with the secret
     * `unsafe`, measured out of conf/canton/&lt;role&gt;/app-auth.conf, and the token
     * is minted the way the bundle's own console mints it - LocalNetToken.
     *
     * `/v2/authenticated-user` is the one endpoint here that is ASSERTED, and it
     * is asserted in both directions. Its whole job is to report who the token
     * says you are, so it cannot answer without one: anonymous MUST be 401 and
     * authenticated MUST be 200 naming the subject. An anonymous 200 would mean
     * the participant is not enforcing at all, which is a worse finding than a
     * refusal and would otherwise pass unnoticed.
     *
     * The rest are READ endpoints off the route table this build publishes, and
     * they are reported. Method is not asserted either: a path that wants POST
     * answers 405 and that is information, not a defect.
     */
    private void probeJsonLedgerApi() {
        System.out.println("--- json ledger-api, authenticated");
        for (String strRole : ARR_ROLE) {
            String strBase = "http://" + strHost + ":" + ports.nPortJson(strRole);
            String strSubject = env("AUTH_" + envRole(strRole) + "_VALIDATOR_USER_NAME",
                    LocalNetToken.STR_SUBJECT_LEDGER);
            String strAudience = env("AUTH_" + envRole(strRole) + "_AUDIENCE",
                    LocalNetToken.STR_AUDIENCE);
            String strToken = LocalNetToken.mint(strSubject, strAudience);
            System.out.println("  " + strRole + "  sub=" + strSubject + " aud=" + strAudience);

            String strAnon = describe(strBase + STR_PATH_WHOAMI, null);
            String strAuth = describe(strBase + STR_PATH_WHOAMI, strToken);
            if (!strAnon.startsWith("401"))
                lstFailure.add(strRole + " " + STR_PATH_WHOAMI
                        + " answered an ANONYMOUS caller with " + strAnon
                        + " - the participant is not enforcing auth");
            if (!strAuth.startsWith("200") || !strAuth.contains(strSubject))
                lstFailure.add(strRole + " " + STR_PATH_WHOAMI
                        + " refused the minted token: " + strAuth);
            System.out.println("    " + STR_PATH_WHOAMI + "  anon " + strAnon);
            System.out.println("    " + STR_PATH_WHOAMI + "  auth " + strAuth);

            for (String strPath : ARR_JSON_READ) {
                System.out.println("    " + strPath + "  auth "
                        + describe(strBase + strPath, strToken));
            }
        }
    }


    /**
     * @param strKey an environment name the bundle's env files may carry
     * @param strFallback the value measured out of the 0.7.4 bundle
     * @return what the stager resolved, or the fallback when there is no stager
     */
    private String env(String strKey, String strFallback) {
        if (stager == null)
            return strFallback;
        String strValue = stager.mapEnv("splice").get(strKey);
        return strValue == null || strValue.isBlank() ? strFallback : strValue;
    }


    private static String envRole(String strRole) {
        return strRole.toUpperCase(Locale.ROOT).replace('-', '_');
    }


    /**
     * @param strUrl the endpoint
     * @param strToken a bearer token, or null to ask anonymously
     * @return the status and the first of whatever it answered with
     */
    private String describe(String strUrl, String strToken) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT_HTTP).build();
            HttpRequest.Builder bld = HttpRequest.newBuilder(URI.create(strUrl))
                    .timeout(TIMEOUT_HTTP).GET();
            if (strToken != null)
                bld.header("Authorization", "Bearer " + strToken);
            HttpResponse<String> response =
                    client.send(bld.build(), HttpResponse.BodyHandlers.ofString());
            String strBody = response.body() == null ? "" : response.body().strip();
            strBody = strBody.replace('\n', ' ');
            if (strBody.length() > N_BODY_SHOWN)
                strBody = strBody.substring(0, N_BODY_SHOWN) + "...";
            return response.statusCode() + " " + strBody;
        }
        catch (IOException ex) {
            return "no answer: " + ex.getClass().getSimpleName();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
    }


    /**
     * HOW LOCALNET AUTHENTICATES, READ RATHER THAN ASSUMED. An authenticated
     * call needs a token, and nothing in this workspace has established where
     * LocalNet's tokens come from - whether an issuer runs inside the compose
     * set that is absent natively, whether the env files carry static tokens, or
     * whether a client-credentials endpoint is expected somewhere. Guessing that
     * is how a session spends an afternoon on the wrong mechanism.
     *
     * The stager already resolves the bundle's own env files the way compose
     * does, so the answer is sitting in a map this process holds. This prints
     * the auth-shaped keys of it.
     *
     * SECRETS ARE NOT PRINTED. A key naming a secret, password, token or private
     * key is reported by name and length only - the length is what distinguishes
     * "set" from "resolved to empty", which is the failure that reads as a bad
     * credential.
     */
    private void probeAuth() {
        System.out.println("--- auth, as the bundle's own env files resolve it");
        if (stager == null) {
            System.out.println("  no bundle given - re-run with the splice version"
                    + " as the third argument to include this");
            return;
        }
        for (String strNamespace : new String[] { "canton", "splice" }) {
            Map<String, String> mapEnv;
            try {
                mapEnv = stager.mapEnv(strNamespace);
            }
            catch (RuntimeException ex) {
                System.out.println("  " + strNamespace + ": unreadable: " + ex.getMessage());
                continue;
            }
            int cntShown = 0;
            for (Map.Entry<String, String> entry : new TreeMap<>(mapEnv).entrySet()) {
                if (!isAuthShaped(entry.getKey()))
                    continue;
                cntShown++;
                System.out.println("  " + strNamespace + "  " + entry.getKey() + " = "
                        + show(entry.getKey(), entry.getValue()));
            }
            if (cntShown == 0)
                System.out.println("  " + strNamespace + ": no auth-shaped key at all");
        }
    }


    private static boolean isAuthShaped(String strKey) {
        for (String strMark : ARR_AUTH_MARK) {
            if (strKey.contains(strMark))
                return true;
        }
        return false;
    }


    /**
     * @return the value, or its length when the key names a credential
     */
    private static String show(String strKey, String strValue) {
        String strSafe = strValue == null ? "" : strValue;
        for (String strMark : ARR_SECRET_MARK) {
            if (strKey.contains(strMark))
                return "<" + strSafe.length() + " chars>";
        }
        return strSafe.isEmpty() ? "<empty>" : strSafe;
    }


    /**
     * A node that never migrated leaves an EMPTY database rather than a missing
     * one - the runner creates all twelve before anything starts, so their
     * existence proves nothing. The table count is what separates them.
     */
    private void probeDatabases() {
        System.out.println("--- databases");
        for (String strDb : LocalNetSpec.lstAllDatabases()) {
            try (Connection conn = connect(strDb);
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT (SELECT count(*) FROM information_schema.tables"
                                    + " WHERE table_type = 'BASE TABLE'"
                                    + " AND table_schema NOT IN ('pg_catalog','information_schema'))"
                                    + " AS tables,"
                                    + " pg_size_pretty(pg_database_size(current_database()))"
                                    + " AS size")) {
                if (!rs.next()) {
                    lstFailure.add("no answer from " + strDb);
                    continue;
                }
                int cntTable = rs.getInt("tables");
                // PQS IS OPTIONAL. Its database is created at every start and
                // stays empty when scribe is not run - the Sandbox's PQS OFF -
                // so an empty `pqs` is reported, never a failure. Measured at
                // his console 2026-09-23: `EMPTY pqs` was the probe's only
                // problem on a healthy stack with PQS off.
                if (cntTable == 0 && !LocalNetSpec.STR_DB_PQS.equals(strDb))
                    lstFailure.add("no tables in " + strDb + " - that node never migrated");
                System.out.println("  " + (cntTable == 0 ? "EMPTY " : "ok    ") + strDb
                        + "  tables " + cntTable + "  size " + rs.getString("size"));
            }
            catch (SQLException ex) {
                lstFailure.add("unreadable database " + strDb + ": " + ex.getMessage());
                System.out.println("  ERROR " + strDb + "  " + ex.getMessage());
            }
        }
    }


    /**
     * WHAT DAML CODE IS ACTUALLY REACHABLE, rather than what is assumed to be.
     * Canton ships DARs inside its own jar - measured on 3.5.11 - and whether
     * the Splice bundle stages any beside it has never been looked at here. The
     * answer decides whether exercising this LocalNet needs a DAR built from an
     * SDK or only one that is already on the disk.
     *
     * Listed, not uploaded. Upload is an admin-API call behind auth, and this
     * module has no client for it.
     */
    private void probeDars() {
        System.out.println("--- daml archives on disk");
        Path fileJar = CantonImage.fromEnvironment().fileJar();
        if (fileJar == null) {
            System.out.println("  no canton jar to look inside");
        }
        else {
            int cntDar = 0;
            try (ZipFile zip = new ZipFile(fileJar.toFile())) {
                for (ZipEntry entry : zip.stream().toList()) {
                    if (!entry.getName().endsWith(".dar"))
                        continue;
                    cntDar++;
                    System.out.println("  canton jar: " + entry.getName() + "  "
                            + entry.getSize() + " bytes");
                }
            }
            catch (IOException ex) {
                System.out.println("  canton jar unreadable: " + ex.getMessage());
            }
            if (cntDar == 0)
                System.out.println("  canton jar: none");
        }

        Path dirSplice = SpliceInstallations.dirRoot();
        if (!Files.isDirectory(dirSplice)) {
            System.out.println("  " + dirSplice + ": not there");
            return;
        }
        int cntBundle = 0;
        try (Stream<Path> strm = Files.walk(dirSplice, N_DEPTH_DAR)) {
            for (Path fileDar : strm.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".dar")).toList()) {
                cntBundle++;
                if (cntBundle <= N_DAR_SHOWN)
                    System.out.println("  bundle: " + dirSplice.relativize(fileDar));
            }
        }
        catch (IOException ex) {
            System.out.println("  " + dirSplice + " unreadable: " + ex.getMessage());
            return;
        }
        if (cntBundle == 0) {
            System.out.println("  bundle: none under " + dirSplice);
        }
        else if (cntBundle > N_DAR_SHOWN) {
            System.out.println("  bundle: " + (cntBundle - N_DAR_SHOWN) + " more");
        }
    }


    private void probeConnections() {
        System.out.println("--- connections");
        try (Connection conn = connect("postgres");
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT count(*) AS used,"
                        + " current_setting('max_connections') AS cap"
                        + " FROM pg_stat_activity")) {
            if (rs.next()) {
                System.out.println("  " + rs.getInt("used") + " of " + rs.getString("cap"));
            }
        }
        catch (SQLException ex) {
            System.out.println("  unreadable: " + ex.getMessage());
        }
    }


    /**
     * @return whether every check that is expected to hold did hold
     */
    public boolean printVerdict() {
        if (lstFailure.isEmpty()) {
            System.out.println("=== PROBE OK");
            return true;
        }
        System.out.println("=== PROBE FOUND " + lstFailure.size() + " PROBLEMS");
        for (String strFailure : lstFailure) {
            System.out.println("  " + strFailure);
        }
        return false;
    }


    private Connection connect(String strDb) throws SQLException {
        return DriverManager.getConnection(
                "jdbc:postgresql://" + strHost + ":" + nPortPg + "/" + strDb,
                "postgres", "postgres");
    }


    private boolean tcp(int nPort) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(strHost, nPort), N_TIMEOUT_TCP_MS);
            return true;
        }
        catch (IOException ex) {
            return false;
        }
    }


    /**
     * @param strUrl the endpoint
     * @return the status code, or 0 when nothing answered
     */
    private int http(String strUrl) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT_HTTP).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(strUrl))
                    .timeout(TIMEOUT_HTTP).GET().build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode();
        }
        catch (IOException | InterruptedException ex) {
            return 0;
        }
    }
}
