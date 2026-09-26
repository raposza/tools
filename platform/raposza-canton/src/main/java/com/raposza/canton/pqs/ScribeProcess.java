// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.pqs;

import com.raposza.canton.install.PqsInstallation;
import com.raposza.runtime.db.PostgresCoordinates;
import com.raposza.runtime.process.ManagedProcess;
import com.raposza.runtime.process.ProcessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * PQS, in one of two modes, chosen by whether a scribe binary was resolved.
 *
 * REAL when a {@link PqsInstallation} is given: scribe streams the Ledger API
 * into PostgreSQL exactly as in production.
 *
 * MOCK when it is not: the schema is provisioned in-process and nothing
 * streams. That is not a lesser version of the same thing and must not be read
 * as one - it exercises OUR half (the argument vector, the ports, the database,
 * the lifecycle, the token) and says nothing about scribe's. Its value is that
 * every machine can run the stack, and the machine with the entitlement runs
 * the same code path with the jar present.
 *
 * {@link #command()} renders the real command in both modes, so a mock run
 * still yields something to paste into a shell on a licensed box. THAT LINE IS
 * A CLAIM ABOUT A GENERATION, so the generation is a constructor argument and
 * not something read off the binary: with no binary there is nothing to read
 * it off, and a mock that rendered `--target-postgres-schema` on the 2.x
 * column would name a flag vendor scribe v0.5.5 accepts and drops.
 * Use {@link #of(PqsSpec, PostgresCoordinates, String, int, Path)} rather than
 * the constructor: the spec knows the line, and a caller that has to remember
 * to pass it is the same defect one layer up.
 *
 * ONE DATABASE PER SCRIBE BINARY, not per line. 3.4.1 writes schema revision
 * 034 and 3.4.3 writes 035; pointing both at one database is the same error as
 * sharing one across minor lines. {@link PqsInstallation#databaseName(String)}
 * derives it.
 *
 * Author Claude/bentzn
 */
public final class ScribeProcess extends ManagedProcess {

    private static final Logger log = LoggerFactory.getLogger(ScribeProcess.class);

    public static final String STR_DEFAULT_SCHEMA = "pqs";

    /**
     * Where scribe's health server listens.
     *
     * Measured on scribe v3.5.7 by reading the `health` block of the
     * effective configuration scribe prints at start-up, which is the only
     * oracle this binary offers: it does not implement `--help`, and it does
     * not reject an unknown flag either. `--health-port=9999` moved it,
     * `--health.port=9999` left it at 8080 WITHOUT A WORD. `-Dhealth.port` and
     * `SCRIBE_HEALTH_PORT` both work and are not used here, because a flag on
     * the command line is visible in {@link #commandForShell} and a system
     * property is not.
     *
     * The default is a fixed 8080 and a bind failure on it is FATAL: scribe
     * logs `Address already in use` from `zio.http.Server.ServerLive`, then
     * `Failed to start health server`, then exits. It can print one of the
     * ready markers first, because the health server and the pipeline are
     * separate ZIO fibers - which is how an earlier measurement recorded
     * `pqs reported ready` for a process that was already dying.
     */
    public static final String STR_FLAG_HEALTH_PORT = "--health-port=";

    /**
     * Where scribe puts its tables when nothing names a schema: the connection's
     * default, which is `public` on a stock PostgreSQL. Measured on the 2.10.4
     * gate. Named here because the mock's own schema is a
     * different place and the two must not be conflated in a log line.
     */
    public static final String STR_SCHEMA_UNNAMED = "public";

    /**
     * The oauth flags, spelled ONCE and always with the trailing `=`.
     *
     * Measured on scribe v3.5.7, and this is not a style choice.
     * Handed the endpoint as a SEPARATE argument, scribe's parser split that
     * argument on the first `=` INSIDE THE VALUE and kept only what followed:
     * a URL of
     * `http://127.0.0.1:32002/oauth/token?ttl_seconds=86400&shape=AUDIENCE&...`
     * arrived in the applied configuration as
     * `endpoint="86400&shape=AUDIENCE&..."`, and scribe died with
     * `Expected URL scheme 'http' or 'https' but no scheme was found`.
     *
     * The same parser honours `--health-port=` and every `--target-postgres-*=`
     * this class already renders, so the equals form is the one that survives a
     * value carrying its own `=` - which any query string does.
     */
    public static final String STR_FLAG_OAUTH_ENDPOINT = "--pipeline-oauth-endpoint=";

    public static final String STR_FLAG_OAUTH_CLIENT_ID = "--pipeline-oauth-clientid=";

    public static final String STR_FLAG_OAUTH_CLIENT_SECRET = "--pipeline-oauth-clientsecret=";

    public static final String STR_FLAG_OAUTH_SCOPE = "--pipeline-oauth-scope=";

    /** The target database password, elided wherever the command is shown. */
    public static final String STR_FLAG_TARGET_PASSWORD = "--target-postgres-password=";

    /**
     * scribe logs this from DocumentPostgres once Flyway has finished and it
     * owns the schema, which is the first moment it is really ingesting.
     */
    private static final String STR_READY_MAPPINGS = "Applying mappings";

    private static final String STR_READY_SEEDING = "Seeding from ACS";

    private static final String STR_READY_PROCESSING = "Processing transactions";

    /**
     * The pipeline is connected and consuming the Ledger API.
     *
     * Measured on the first 2.10.4 live gate. The three sentinels
     * above all require DATA: against an EMPTY ledger scribe migrates its
     * schema, retrieves GENESIS and end offset 0, and then prints this line
     * every 30 s forever without ever printing any of them. The run failed
     * after PT3M on a scribe that was working exactly as it should.
     *
     * That is a readiness assertion that cannot
     * fire in the case it is being asked about. The 3.x gates never caught it
     * because a 3.x run has a DAR and contracts by the time PQS starts.
     */
    private static final String STR_READY_STREAMING = "Received transactions responses";

    /**
     * The offsets are read once, before streaming begins, and the line lands
     * within a second of start-up rather than at the first poll interval.
     * Kept as the earlier of the two signals so a stack with a long poll does
     * not wait for a tick it does not need.
     */
    private static final String STR_READY_OFFSET = "Retrieved ledger end offset";

    private final PqsInstallation installation;
    private final String strCantonLine;
    private final PostgresCoordinates pgTarget;
    private final String strSchema;
    private final String strLedgerHost;
    private final int nPortLedger;
    private final String strToken;
    private final PqsOAuth oauth;
    private final Path dirWork;
    private final int nHeapMb;
    private final int nPortHealth;

    private volatile boolean flagMockRunning;


    /**
     * @param installation the scribe binary, or null to run the mock
     * @param strCantonLine the Canton minor line this PQS faces, e.g. "2.10";
     *        ignored when an installation is given, because that carries its
     *        own DECLARED line, and required by the mock, which has no other
     *        way to know which column it is standing in for
     * @param pgTarget the database scribe writes into; one per binary version
     * @param strSchema the schema inside it, or null for the default
     * @param strLedgerHost the participant's host
     * @param nPortLedger the participant's gRPC Ledger API port
     * @param strToken the access token, or null when the participant has no
     *        auth-services; scribe takes a STATIC token on the command line, so
     *        this one cannot be renewed without a restart
     * @param oauth credentials to re-mint from, or null; when set they replace
     *        the token entirely, because a scribe holding an endpoint asks for
     *        a new one before each expiry and a scribe holding a string cannot
     * @param dirWork where scribe runs
     * @param nHeapMb the JVM heap, or 0 for the JVM default
     * @param nPortHealth where scribe's health server listens, or 0 to leave
     *        the flag out and take scribe's fixed 8080. See
     *        {@link #STR_FLAG_HEALTH_PORT} for why 0 is not a good default for
     *        anything that starts more than one stack.
     */
    public ScribeProcess(PqsInstallation installation, String strCantonLine,
            PostgresCoordinates pgTarget, String strSchema, String strLedgerHost, int nPortLedger,
            String strToken, PqsOAuth oauth, Path dirWork, int nHeapMb, int nPortHealth) {
        super("pqs");

        if (pgTarget == null || dirWork == null)
            throw new IllegalArgumentException("pgTarget and dirWork are required");
        if (strLedgerHost == null || strLedgerHost.isBlank())
            throw new IllegalArgumentException("a ledger host is required");
        if (nPortLedger < 1 || nPortLedger > 65535)
            throw new IllegalArgumentException("ledger port out of range: " + nPortLedger);
        if (nHeapMb < 0)
            throw new IllegalArgumentException("heap must not be negative: " + nHeapMb);
        if (nPortHealth < 0 || nPortHealth > 65535)
            throw new IllegalArgumentException("health port out of range: " + nPortHealth);

        this.installation = installation;
        if (installation != null) {
            this.strCantonLine = installation.strCantonLine();
        }
        else {
            this.strCantonLine = strCantonLine == null || strCantonLine.isBlank() ? null
                    : strCantonLine.trim();
        }
        this.pgTarget = pgTarget;
        this.strSchema = strSchema == null || strSchema.isBlank() ? STR_DEFAULT_SCHEMA : strSchema;
        this.strLedgerHost = strLedgerHost;
        this.nPortLedger = nPortLedger;
        this.strToken = strToken;
        this.oauth = oauth;
        this.dirWork = dirWork.toAbsolutePath().normalize();
        this.nHeapMb = nHeapMb;
        this.nPortHealth = nPortHealth;
    }


    /**
     * The normal way to build one. The spec already resolved the binary and the
     * generation together, so nothing here can supply one without the other.
     *
     * @param spec what PQS to run and where it writes
     * @param pgTarget the database, already created
     * @param strLedgerHost the participant's host
     * @param nPortLedger the participant's gRPC Ledger API port
     * @param dirWork where scribe runs
     * @param nPortHealth where scribe's health server listens; the stack's own
     *        port block decides it, so two stacks never share one
     * @return the process, started by the caller
     */
    public static ScribeProcess of(PqsSpec spec, PostgresCoordinates pgTarget,
            String strLedgerHost, int nPortLedger, Path dirWork, int nPortHealth) {
        if (spec == null)
            throw new IllegalArgumentException("a spec is required");
        return new ScribeProcess(spec.installation(), spec.strCantonLine(), pgTarget,
                spec.strSchema(), strLedgerHost, nPortLedger, spec.strToken(), spec.oauth(),
                dirWork, spec.nHeapMb(), nPortHealth);
    }


    /**
     * @return whether a scribe binary was resolved, and therefore whether this
     *         run measures scribe or only the wiring around it
     */
    public boolean isReal() {
        return installation != null;
    }


    public String strSchema() {
        return strSchema;
    }


    /**
     * @return the Canton minor line this PQS faces, or null when nothing said
     */
    public String strCantonLine() {
        return strCantonLine;
    }


    /**
     * @return whether this PQS faces a Canton 2.x participant; false when
     *         nothing said, which renders the 3.x-shaped command
     */
    public boolean isCanton2x() {
        return installation != null ? installation.isCanton2x()
                : PqsInstallation.isCanton2xLine(strCantonLine);
    }


    /**
     * Whether the schema is named on the command line and pre-created.
     *
     * ONE FLAG, THREE BEHAVIOURS, measured:
     * `--target-postgres-schema` is honoured on 3.4 and 3.5, ACCEPTED AND
     * SILENTLY DROPPED by vendor scribe v0.5.5, and REFUSED as an unknown
     * option by the 2.x stand-in.
     *
     * So on the 2.x column it buys nothing where it is accepted and
     * manufactures a defect where it is not, and per-run isolation there is
     * the DATABASE - which {@link PqsSpec} already derives per binary - rather
     * than the schema.
     *
     * IT IS THE GENERATION THAT DECIDES, NOT THE MODE. Until 2026-08-16 this
     * read `installation == null || !installation.isCanton2x()`, so the mock
     * kept the flag on every column - and a 2.x mock run therefore printed, as
     * a line to paste on a licensed machine, the one form that generation
     * accepts and ignores. The mock still provisions and uses its OWN schema
     * in-process; that is a different question from what the rendered command
     * claims, and conflating the two is what produced the wrong line.
     *
     * @return whether this run may name its schema
     */
    public boolean isSchemaNamed() {
        return !isCanton2x();
    }


    public PostgresCoordinates pgTarget() {
        return pgTarget;
    }


    /**
     * @return where scribe's health server listens, or 0 when nothing said and
     *         scribe's fixed 8080 stands
     */
    public int nPortHealth() {
        return nPortHealth;
    }


    /**
     * The scribe command, rendered in both modes.
     *
     * In mock mode the jar is named as a placeholder, so the result is a shape
     * to fill in rather than a line that silently points at nothing. Everything
     * else about it is the real thing for the generation named at construction,
     * because the point of rendering it at all is that it gets pasted.
     *
     * @return the command, one element per argument
     */
    public List<String> command() {
        List<String> lstCommand = new ArrayList<>();
        lstCommand.add("java");
        if (nHeapMb > 0)
            lstCommand.add("-Xmx" + nHeapMb + "m");
        lstCommand.add("-jar");
        lstCommand.add(isReal()
                ? installation.fileJar().toAbsolutePath().normalize().toString()
                : "<scribe.jar>");
        lstCommand.add("pipeline");
        lstCommand.add("ledger");
        lstCommand.add("postgres-document");
        lstCommand.add("--source-ledger-host=" + strLedgerHost);
        lstCommand.add("--source-ledger-port=" + nPortLedger);

        if (oauth != null) {
            // THE ENDPOINT AND NOT A TOKEN. scribe holds a client id and a
            // secret and asks the mint itself, again `preemptexpiry` before
            // each token expires - PT1M by default - so the 240 s ceiling a
            // participant below 3.5.6 enforces renews with three
            // minutes still on the clock instead of ending the run.
            //
            // The SHAPE, the AUDIENCE and the LIFETIME are in the endpoint's
            // query string: they are not OAuth parameters, and
            // `--pipeline-oauth-parameters` is typed only as `map` with no
            // syntax this project has measured.
            //
            // ONE ARGUMENT EACH, and see STR_FLAG_OAUTH_ENDPOINT for the
            // measurement that says why the two-argument form cannot carry a
            // value with an `=` in it.
            lstCommand.add("--source-ledger-auth");
            lstCommand.add("OAuth");
            lstCommand.add(STR_FLAG_OAUTH_ENDPOINT + oauth.strUrlToken());
            lstCommand.add(STR_FLAG_OAUTH_CLIENT_ID + oauth.strClientId());
            lstCommand.add(STR_FLAG_OAUTH_CLIENT_SECRET + oauth.strClientSecret());
            if (oauth.strScope() != null)
                lstCommand.add(STR_FLAG_OAUTH_SCOPE + oauth.strScope());
        }
        else if (strToken == null || strToken.isBlank()) {
            lstCommand.add("--source-ledger-auth");
            lstCommand.add("NoAuth");
        }
        else {
            lstCommand.add("--source-ledger-auth");
            lstCommand.add("OAuth");
            lstCommand.add("--pipeline-oauth-accesstoken");
            lstCommand.add(strToken);
        }

        lstCommand.add("--target-postgres-host=" + pgTarget.strHost());
        lstCommand.add("--target-postgres-port=" + pgTarget.nPort());
        lstCommand.add("--target-postgres-database=" + pgTarget.strDatabase());
        lstCommand.add("--target-postgres-username=" + pgTarget.strUser());
        lstCommand.add(STR_FLAG_TARGET_PASSWORD + pgTarget.strPassword());
        if (isSchemaNamed())
            lstCommand.add("--target-postgres-schema=" + strSchema);
        if (nPortHealth > 0)
            lstCommand.add(STR_FLAG_HEALTH_PORT + nPortHealth);
        lstCommand.add("--pipeline-filter-parties=*");
        lstCommand.add("--pipeline-datasource=TransactionStream");
        lstCommand.add("--pipeline-ledger-start=Oldest");
        return lstCommand;
    }


    /**
     * @return the command as a shell line, wrapped, with every credential in it
     *         elided - the bearer token, the client secret, and any shared
     *         secret riding in the token endpoint's query. The point of this
     *         string is that it gets copied around, and a secret is a secret
     *         there even when it is the word `unused`.
     */
    public String commandForShell() {
        StringBuilder sb = new StringBuilder();
        List<String> lstCommand = command();
        for (int idxArg = 0; idxArg < lstCommand.size(); idxArg++) {
            sb.append(quote(strMasked(lstCommand.get(idxArg))));
            if (idxArg < lstCommand.size() - 1)
                sb.append(" \\\n  ");
        }
        return sb.toString();
    }


    /**
     * The started command as {@link ManagedProcess} logs it and shows it in a
     * pane - the same elisions as {@link #commandForShell}, one line.
     */
    @Override
    protected List<String> lstCommandForLog(List<String> lstCommand) {
        List<String> lstOut = new ArrayList<>(lstCommand.size());
        for (String strArg : lstCommand) {
            lstOut.add(strMasked(strArg));
        }
        return lstOut;
    }


    /**
     * @param strArg one argument of the command
     * @return the argument, or its flag with the credential replaced by a
     *         placeholder - the bearer token, the client secret, a shared
     *         secret in the token endpoint's query, and the target database
     *         password
     */
    private String strMasked(String strArg) {
        if (strToken != null && !strToken.isBlank() && strArg.equals(strToken))
            return "<access-token>";
        if (strArg.startsWith(STR_FLAG_OAUTH_CLIENT_SECRET))
            return STR_FLAG_OAUTH_CLIENT_SECRET + "<client-secret>";
        if (strArg.startsWith(STR_FLAG_OAUTH_ENDPOINT)) {
            return STR_FLAG_OAUTH_ENDPOINT + PqsOAuth.strMasked(
                    strArg.substring(STR_FLAG_OAUTH_ENDPOINT.length()));
        }
        if (strArg.startsWith(STR_FLAG_TARGET_PASSWORD))
            return STR_FLAG_TARGET_PASSWORD + "<password>";
        return strArg;
    }


    @Override
    public synchronized void start() {
        if (isReal()) {
            log.info("PQS: real scribe from {}", installation.fileJar());
            if (isSchemaNamed()) {
                ensureSchema();
            }
            else {
                // DISCOVER, never name. Pre-creating `pqs` here would
                // leave an empty schema beside whatever scribe builds, and a
                // reader asking the catalogue would then find two with no way
                // to tell which one is scribe's. Nothing is created, so
                // ScribeTables.schemas() returns scribe's and only scribe's.
                log.info("PQS: 2.x column - the schema is neither named nor"
                        + " pre-created, because the flag naming it is inert"
                        + " there. Isolation is the database {}.",
                        pgTarget.strDatabase());
            }
            super.start();
            return;
        }

        if (flagMockRunning)
            throw new ProcessException("the PQS mock is already running");

        log.info("PQS: mock, because no scribe binary was resolved (canton {})",
                strCantonLine == null ? "line UNKNOWN" : strCantonLine);
        provisionMockSchema();
        flagMockRunning = true;
        log.info("PQS mock ready in schema {} of {}", strSchema, pgTarget.strDatabase());

        if (strCantonLine == null) {
            log.warn("PQS: the Canton generation is UNKNOWN here, so the command below is"
                    + " rendered in its 3.x form and names a schema. On 2.x vendor scribe"
                    + " v0.5.5 accepts that flag and DROPS it, so check the"
                    + " generation before pasting this anywhere.");
        }
        else if (isCanton2x()) {
            log.info("PQS: the command below names NO schema, because the flag naming it is"
                    + " inert on the 2.x column - vendor scribe v0.5.5 accepts and drops it,"
                    + " Run as printed it lands in {}, not in {}, which is where this"
                    + " mock's own table is.", STR_SCHEMA_UNNAMED, strSchema);
        }
        log.info("the equivalent scribe command is:\n{}", commandForShell());
    }


    @Override
    public synchronized void stop(Duration timeoutGraceful) {
        if (isReal()) {
            super.stop(timeoutGraceful);
            return;
        }
        flagMockRunning = false;
    }


    @Override
    public boolean isRunning() {
        return isReal() ? super.isRunning() : flagMockRunning;
    }


    @Override
    protected Path workingDir() {
        return dirWork;
    }


    @Override
    protected boolean isReadyLine(String strLine) {
        return strLine.contains(STR_READY_MAPPINGS)
                || strLine.contains(STR_READY_SEEDING)
                || strLine.contains(STR_READY_PROCESSING)
                || strLine.contains(STR_READY_STREAMING)
                || strLine.contains(STR_READY_OFFSET);
    }


    @Override
    protected boolean isReadyOutOfBand() {
        return !isReal() && flagMockRunning;
    }


    @Override
    protected List<String> buildCommand() throws IOException {
        if (!isReal())
            throw new IllegalStateException("the mock does not run a process");
        return command();
    }


    /**
     * scribe migrates its own schema but does not create it. The failure when
     * it is missing arrives from Flyway and reads as a migration problem rather
     * than as an absent schema.
     */
    private void ensureSchema() {
        execute("CREATE SCHEMA IF NOT EXISTS " + strSchema);
    }


    /**
     * The smallest thing that lets a caller assert PQS ran: the schema, and a
     * table shaped like the one scribe's document model creates. It is NOT
     * scribe's schema and must not be queried as though it were.
     *
     * It is created on BOTH columns, unlike a real 2.x run, because this one is
     * the thing that creates it and can therefore name it. What the rendered
     * command claims is a separate question and is answered by
     * {@link #isSchemaNamed()}.
     */
    private void provisionMockSchema() {
        ensureSchema();
        execute("CREATE TABLE IF NOT EXISTS " + strSchema + ".raposza_mock_meta ("
                + "key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        execute("INSERT INTO " + strSchema + ".raposza_mock_meta (key, value)"
                + " VALUES ('mode', 'mock') ON CONFLICT (key) DO UPDATE SET value = 'mock'");
    }


    private void execute(String strSql) {
        try (Connection conn = DriverManager.getConnection(pgTarget.jdbcUrl(), pgTarget.strUser(),
                pgTarget.strPassword());
                Statement stmt = conn.createStatement()) {
            stmt.execute(strSql);
        }
        catch (SQLException ex) {
            throw new ProcessException("PQS could not prepare " + pgTarget.strDatabase(), ex);
        }
    }


    private static String quote(String strArg) {
        if (strArg.isEmpty())
            return "''";
        boolean flagNeeds = false;
        for (int idxChar = 0; idxChar < strArg.length(); idxChar++) {
            if (!isShellSafe(strArg.charAt(idxChar))) {
                flagNeeds = true;
                break;
            }
        }
        if (!flagNeeds)
            return strArg;
        return "'" + strArg.replace("'", "'\\''") + "'";
    }


    /**
     * A WHITELIST, and it used to be a blacklist naming seven characters.
     *
     * The endpoint argument brought `&` and `?` into a string whose entire
     * purpose is that it gets pasted into a shell, and the blacklist named
     * neither: the line would have run, backgrounded itself at the first `&`,
     * and executed the remainder as separate commands. A character missing
     * from a blacklist is a line that does something else; one missing from a
     * whitelist is a pair of quotes nobody needed.
     *
     * @param chHere a character of an argument
     * @return whether it can stand unquoted in every POSIX shell
     */
    private static boolean isShellSafe(char chHere) {
        return (chHere >= 'a' && chHere <= 'z') || (chHere >= 'A' && chHere <= 'Z')
                || (chHere >= '0' && chHere <= '9') || chHere == '-' || chHere == '_'
                || chHere == '.' || chHere == '/' || chHere == ':' || chHere == '='
                || chHere == ',' || chHere == '@' || chHere == '+' || chHere == '%';
    }
}
