// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.db;

import com.raposza.runtime.port.PortGuard;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * The stack's PostgreSQL, embedded, with no container runtime involved.
 *
 * Zonky rather than Testcontainers, and that is not a preference: the sandbox
 * runs as ordinary OS processes on developer machines where Docker may not be
 * installed and often may not be permitted. Zonky unpacks a real server binary
 * and runs it directly. The trade is that the binaries are a platform-specific
 * artefact resolved at build time, so an unusual architecture is a dependency
 * problem rather than a pull.
 *
 * The port is fixed rather than drawn at random, and checked before the server
 * is asked for. A random port cannot be written into a configuration file
 * before it exists, and adopting whatever already listens is how a stack ends
 * up running against a database nobody described.
 *
 * Start-up is bounded. Zonky's start can block indefinitely when unpacking
 * fails part-way, and a hung build is harder to read than a failed one.
 *
 * <h2>Persistence, and what it does not settle</h2>
 *
 * With no data directory the cluster is a temporary one and Zonky removes it
 * on close: correct for a test, wrong for a developer who restarts the sandbox
 * and finds every contract gone. Passing a directory turns the cluster into
 * one that outlives the process - `setDataDirectory` and
 * `setCleanDataDirectory`, both read off the 2.2.2 jar rather than recalled.
 *
 * The data directory is NOT created here, and that is deliberate. Zonky
 * decides whether to run `initdb` from the state of the directory it is given,
 * and this class does not know which state it looks at. Creating an empty
 * directory first would be a bet on that branch. Only the PARENT is created,
 * which is the part Zonky has no reason to inspect.
 *
 * A persistent cluster keeps whatever a node wrote into it, which includes the
 * participant's crypto store. Whether the participant therefore keeps its
 * identity across a restart is a QUESTION, not a claim: it is what
 * a live stack test measures. If it holds, most of the value is here and the
 * namespace key remains for reproducibility across machines and across a
 * fresh cluster. If it does not, the key is the whole of it.
 *
 * <h2>A start that fails does not leave its postmaster behind</h2>
 *
 * `todo.md` A-34 (d). Zonky runs the server as a separate OS process, and
 * {@link #stop()} can only stop what {@link #start()} got back. A start that
 * timed out, was interrupted or threw after the server had been launched gave
 * nothing back, so the postmaster it launched ran on, held its port and its
 * data directory, and nothing in this process could reach it again.
 *
 * On a persistent cluster the server records itself in the data directory -
 * `postmaster.pid`, whose first line is the postmaster's process id, which is
 * PostgreSQL's own documented layout. A failed start reads it and stops that
 * process, and only that one: it must be alive, must not have been alive
 * before this attempt began - PostgreSQL refuses a second postmaster on one
 * data directory, so a live one found beforehand belongs to somebody else and
 * is left alone - and must be a PostgreSQL executable. The stop is SIGTERM,
 * which is PostgreSQL's smart shutdown. A temporary cluster has no known
 * directory to read, so this covers the persistent kind only, which is the
 * kind LocalNetND and the Sandbox run.
 *
 * Author Claude/bentzn
 */
public final class SandboxPostgres implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SandboxPostgres.class);

    public static final int N_DEFAULT_PORT = 32101;

    /** Where the binaries go when the temporary directory will not take them. */
    public static final String STR_DIR_BESIDE_JAR = "raposza-tmp";

    public static final Duration TIMEOUT_START_DEFAULT = Duration.ofMinutes(2);

    /** The role the embedded server creates. Trust auth, so any password works. */
    private static final String STR_USER = "postgres";

    private static final String STR_PASSWORD = "postgres";

    private static final String STR_HOST = "localhost";

    /** Always present, and therefore where CREATE DATABASE is issued from. */
    private static final String STR_DB_ADMIN = "postgres";

    /**
     * What Canton's sequencer writer demands. It accepts on or remote_write and
     * refuses anything else; the embedded server's default is off.
     */
    public static final String STR_SYNCHRONOUS_COMMIT = "on";

    /**
     * A database name is an identifier and cannot be bound as a parameter, so
     * it is validated instead of escaped. Lower case, because an unquoted
     * identifier is folded to lower case by the server and the name would then
     * not match what was written into Canton's configuration.
     */
    private static final Pattern PAT_DB_NAME = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    /** Where a running server records its process id - PostgreSQL's layout. */
    static final String STR_FILE_PID = "postmaster.pid";

    /** How long a left-behind postmaster is given to finish a smart shutdown. */
    private static final Duration TIMEOUT_STOP_LEFT = Duration.ofSeconds(30);

    /** How long an abandoned start is given to return before its pid is read. */
    private static final Duration TIMEOUT_START_ABANDON = Duration.ofSeconds(10);

    private final int nPort;
    private final Duration timeoutStart;
    private final Path dirData;
    private final Map<String, String> mapServerConfig;

    private volatile EmbeddedPostgres postgres;


    public SandboxPostgres() {
        this(N_DEFAULT_PORT, TIMEOUT_START_DEFAULT, null);
    }


    /**
     * A cluster that does not outlive the process.
     *
     * @param nPort the fixed port to listen on
     * @param timeoutStart how long to wait for the server to come up
     */
    public SandboxPostgres(int nPort, Duration timeoutStart) {
        this(nPort, timeoutStart, null);
    }


    /**
     * @param nPort the fixed port to listen on
     * @param timeoutStart how long to wait for the server to come up
     * @param dirData where the cluster lives, or null for a temporary one that
     *        is removed on {@link #close()}
     */
    public SandboxPostgres(int nPort, Duration timeoutStart, Path dirData) {
        this(nPort, timeoutStart, dirData, Map.of());
    }


    /**
     * Server settings are NOT a tuning knob here. Zonky tunes its cluster for a
     * test - one harness, a handful of connections - and a caller running a
     * whole topology against it needs settings that cluster was not started
     * with. `max_connections` is the one measured to bite: it is a postmaster
     * parameter, so it cannot be raised after the server is up, and the failure
     * arrives as PSQLException FATAL sorry, too many clients already, from
     * whichever node happened to be last rather than from whatever is holding
     * the connections.
     *
     * The settings reach initdb, so a cluster REUSED from an existing data
     * directory keeps whatever it was created with.
     *
     * @param nPort the fixed port to listen on
     * @param timeoutStart how long to wait for the server to come up
     * @param dirData where the cluster lives, or null for a temporary one that
     *        is removed on {@link #close()}
     * @param mapServerConfig postgresql.conf settings, empty for Zonky's own
     */
    public SandboxPostgres(int nPort, Duration timeoutStart, Path dirData,
            Map<String, String> mapServerConfig) {
        if (nPort < 1 || nPort > 65535)
            throw new IllegalArgumentException("port out of range: " + nPort);
        if (timeoutStart == null || timeoutStart.isZero() || timeoutStart.isNegative())
            throw new IllegalArgumentException("the start timeout must be positive");

        this.nPort = nPort;
        this.timeoutStart = timeoutStart;
        this.dirData = dirData == null ? null : dirData.toAbsolutePath().normalize();
        this.mapServerConfig = mapServerConfig == null
                ? Map.of()
                : Map.copyOf(mapServerConfig);
    }


    public boolean isRunning() {
        return postgres != null;
    }


    /**
     * @return whether this cluster outlives the process
     */
    public boolean isPersistent() {
        return dirData != null;
    }


    /**
     * @return where the cluster lives, or null when it is a temporary one
     */
    public Path dirData() {
        return dirData;
    }


    public int port() {
        return nPort;
    }


    public String host() {
        return STR_HOST;
    }


    /**
     * @param strDatabase the database name
     * @return coordinates for it; the database is not created by this call
     */
    public PostgresCoordinates coordinatesFor(String strDatabase) {
        requireValidName(strDatabase);
        return new PostgresCoordinates(STR_HOST, nPort, strDatabase, STR_USER, STR_PASSWORD);
    }


    /**
     * @throws DatabaseException when the port is taken or the server does not
     *         come up in time
     */
    public synchronized void start() {
        if (postgres != null)
            return;

        Map<String, Integer> mapPort = new LinkedHashMap<>();
        mapPort.put("postgres", nPort);
        PortGuard.requireFree(mapPort);

        if (dirData != null)
            requireParent();

        // READ BEFORE THE ATTEMPT, so that a live postmaster that was already
        // there is never taken for one this start left behind.
        long nPidBefore = nPidLive(dirData);

        log.info("starting embedded PostgreSQL on port {} (timeout {}, data {}, server config {})",
                nPort, timeoutStart, dirData == null ? "temporary" : dirData, mapServerConfig);

        ExecutorService exec = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "raposza-pg-start");
            thread.setDaemon(true);
            return thread;
        });
        try {
            // THE TEMPORARY DIRECTORY FIRST, then beside the jar - the operator's
            // instruction of 2026-09-26. A Windows guest has refused the unpack
            // into %TEMP%, and a user is not asked to set TMP to fix it.
            Callable<EmbeddedPostgres> task = () -> {
                try {
                    return builder().start();
                }
                catch (IOException | RuntimeException ex) {
                    Path dirBeside = dirBesideJar();
                    if (dirBeside == null)
                        throw ex;
                    log.warn("embedded PostgreSQL did not start from the temporary directory ({});"
                            + " trying {}", ex.getMessage(), dirBeside);
                    Files.createDirectories(dirBeside);
                    return builder().setOverrideWorkingDirectory(dirBeside.toFile()).start();
                }
            };
            Future<EmbeddedPostgres> future = exec.submit(task);
            postgres = future.get(timeoutStart.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException ex) {
            throw new DatabaseException("embedded PostgreSQL did not start within " + timeoutStart
                    + " on port " + nPort, ex);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DatabaseException("interrupted while starting embedded PostgreSQL", ex);
        }
        catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new DatabaseException("embedded PostgreSQL failed to start on port " + nPort
                    + ": " + cause.getMessage(), cause);
        }
        finally {
            exec.shutdownNow();
            if (postgres == null)
                stopLeftBehind(exec, nPidBefore);
        }

        log.info("PostgreSQL up on {}:{}", STR_HOST, nPort);
    }


    /**
     * A START THAT GAVE NOTHING BACK - see the class comment. The abandoned
     * start thread is given {@link #TIMEOUT_START_ABANDON} to return first, so
     * a server it was still bringing up has written its pid by the time it is
     * read.
     *
     * @param exec the start's executor, already shut down
     * @param nPidBefore the postmaster alive on this data directory before the
     *        attempt, or -1
     */
    private void stopLeftBehind(ExecutorService exec, long nPidBefore) {
        if (dirData == null)
            return;
        try {
            exec.awaitTermination(TIMEOUT_START_ABANDON.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        stopLeftBehind(dirData, nPidBefore);
    }


    /**
     * @param dirData a data directory, or null
     * @return the process id on the first line of its `postmaster.pid`, or -1
     *         when there is no such file or it does not start with a number
     */
    static long nPidOf(Path dirData) {
        if (dirData == null)
            return -1;
        Path file = dirData.resolve(STR_FILE_PID);
        if (!Files.isRegularFile(file))
            return -1;
        try {
            // ISO-8859-1 because it cannot refuse a byte: a torn file is a
            // number that does not parse, not an exception from the decoder.
            List<String> lstLine = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
            if (lstLine.isEmpty())
                return -1;
            long nPid = Long.parseLong(lstLine.get(0).trim());
            return nPid > 0 ? nPid : -1;
        }
        catch (IOException | NumberFormatException ex) {
            return -1;
        }
    }


    /**
     * @param dirData a data directory, or null
     * @return the process id its `postmaster.pid` names when that process is
     *         alive, else -1
     */
    static long nPidLive(Path dirData) {
        long nPid = nPidOf(dirData);
        if (nPid <= 0)
            return -1;
        return ProcessHandle.of(nPid).filter(ProcessHandle::isAlive).isPresent() ? nPid : -1;
    }


    /**
     * Stops the postmaster a failed start left on this data directory.
     *
     * NOTHING IS STOPPED THAT THIS ATTEMPT DID NOT START: the process must be
     * alive, must not be `nPidBefore`, must not be this JVM, and must be a
     * PostgreSQL executable where the platform reports the command at all.
     *
     * @param dirData the data directory the attempt ran on
     * @param nPidBefore what {@link #nPidLive} said before the attempt
     * @return whether a process was stopped
     */
    static boolean stopLeftBehind(Path dirData, long nPidBefore) {
        long nPid = nPidOf(dirData);
        if (nPid <= 0 || nPid == nPidBefore || nPid == ProcessHandle.current().pid())
            return false;
        Optional<ProcessHandle> optProc = ProcessHandle.of(nPid);
        if (optProc.isEmpty() || !optProc.get().isAlive())
            return false;

        ProcessHandle proc = optProc.get();
        String strCommand = proc.info().command().orElse("");
        if (!strCommand.isEmpty()
                && !strCommand.toLowerCase(Locale.ROOT).contains("postgres")) {
            log.warn("{} in {} names pid {}, which is not PostgreSQL ({}); left alone",
                    STR_FILE_PID, dirData, nPid, strCommand);
            return false;
        }

        log.warn("the failed start left a postmaster running on {}, pid {}; stopping it",
                dirData, nPid);
        proc.destroy();
        try {
            proc.onExit().get(TIMEOUT_STOP_LEFT.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        }
        catch (TimeoutException ex) {
            log.warn("postmaster pid {} did not stop within {}; it is still running",
                    nPid, TIMEOUT_STOP_LEFT);
            return false;
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
        catch (ExecutionException ex) {
            return false;
        }
    }


    /**
     * @return `raposza-tmp` beside the jar this class was loaded from, or null
     *         when it was not loaded from a jar
     */
    static Path dirBesideJar() {
        try {
            Path fileCode = Path.of(SandboxPostgres.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (!Files.isRegularFile(fileCode) || fileCode.getParent() == null)
                return null;
            return fileCode.getParent().resolve(STR_DIR_BESIDE_JAR);
        }
        catch (java.net.URISyntaxException | RuntimeException ex) {
            return null;
        }
    }


    /**
     * Three options, each measured off the embedded-postgres 2.2.2 jar rather
     * than recalled: `setDataDirectory(Path)`, `setCleanDataDirectory(boolean)`
     * - the one that stops close() taking the cluster with it - and
     * `setRegisterShutdownHook(boolean)`.
     *
     * <h2>Zonky's own shutdown hook is turned OFF, and that is not tidiness</h2>
     *
     * JVM shutdown hooks run in PARALLEL. Left registered, Zonky's hook races
     * whatever the owning process does on its way out, and on 2026-08-14 it
     * won: Ctrl-C on the headless sandbox produced `server stopped` between
     * "stopping pqs" and "stopping canton-sandbox", so the database went away
     * under a participant, a sequencer and a mediator that were still running.
     * The mediator lost its lock, the sequencer failed to close within its ten
     * seconds and then crashed the node to recover from an invalid state.
     *
     * {@link com.raposza.sandbox.SandboxStack} orders the stop for a
     * reason written into its own comment - scribe, then Canton, then this -
     * and an order that a second hook can overtake is not an order. So the
     * hook goes and the explicit stop stands alone.
     *
     * WHAT THIS COSTS: a JVM killed with SIGKILL, or one whose own hook fails,
     * leaves a postmaster running. That was already true of every process this
     * class starts, and it is the trade for a shutdown that cannot be
     * reordered by something outside it.
     */
    private EmbeddedPostgres.Builder builder() {
        EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder()
                .setPort(nPort)
                .setRegisterShutdownHook(false);
        for (Map.Entry<String, String> entry : mapServerConfig.entrySet()) {
            builder.setServerConfig(entry.getKey(), entry.getValue());
        }
        if (dirData != null)
            builder.setDataDirectory(dirData).setCleanDataDirectory(false);
        return builder;
    }


    /**
     * The parent only. See the class comment: which state of the data
     * directory Zonky reads to decide on `initdb` is not established here, so
     * creating the directory itself would be a bet on that branch.
     */
    private void requireParent() {
        Path dirParent = dirData.getParent();
        if (dirParent == null)
            return;
        try {
            Files.createDirectories(dirParent);
        }
        catch (IOException ex) {
            throw new DatabaseException("could not create " + dirParent, ex);
        }
    }


    /**
     * Creates the database when it is not there.
     *
     * @param strDatabase the database name
     * @return coordinates for it
     * @throws DatabaseException when the server is not running or the
     *         statement fails
     */
    public PostgresCoordinates ensureDatabase(String strDatabase) {
        requireValidName(strDatabase);
        if (postgres == null)
            throw new DatabaseException("PostgreSQL is not running; cannot create " + strDatabase);

        PostgresCoordinates admin = coordinatesFor(STR_DB_ADMIN);
        try (Connection conn = DriverManager.getConnection(admin.jdbcUrl(), STR_USER, STR_PASSWORD)) {
            if (exists(conn, strDatabase)) {
                log.debug("database {} already exists", strDatabase);
            }
            else {
                try (Statement stmt = conn.createStatement()) {
                    // Not a bind parameter: an identifier cannot be one. The
                    // name was matched against PAT_DB_NAME before reaching here.
                    stmt.execute("CREATE DATABASE " + strDatabase);
                }
                log.info("created database {}", strDatabase);
            }
        }
        catch (SQLException ex) {
            throw new DatabaseException("could not create database " + strDatabase, ex);
        }

        // Deliberately outside that branch. An existing database is the case a
        // restart hits, and skipping the setting there would make the failure
        // depend on whether this was the first run.
        requireDurableCommit(strDatabase);
        return coordinatesFor(strDatabase);
    }


    /**
     * Sets synchronous_commit for one database, because the embedded server's
     * default is a value Canton refuses to run against.
     *
     * Zonky tunes its server for test speed and starts it with
     * synchronous_commit off. Canton's sequencer writer checks the setting on
     * its own connection and stops:
     *
     * <pre>
     * Failed to startup sequencer writer: BadCommitMode(Postgres
     * 'synchronous_commit' setting is 'off' but expecting one of: on,remote_write)
     * </pre>
     *
     * That is one line, and it arrives ahead of the node crashing, the
     * synchronizer losing its sequencer, and a thread dump long enough to hide
     * all three.
     *
     * Set per database rather than per server on purpose. It applies to
     * connections opened afterwards - which is every connection Canton makes,
     * since the databases are created before it starts - it is visible in
     * `SHOW synchronous_commit`, and it needs no builder option whose exact
     * name would have to be recalled rather than read. The cost is nearly
     * nothing here: the embedded server also runs with fsync off, so a durable
     * commit still does not reach a platter.
     */
    private void requireDurableCommit(String strDatabase) {
        PostgresCoordinates admin = coordinatesFor(STR_DB_ADMIN);
        try (Connection conn = DriverManager.getConnection(admin.jdbcUrl(), STR_USER, STR_PASSWORD);
                Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER DATABASE " + strDatabase + " SET synchronous_commit = '"
                    + STR_SYNCHRONOUS_COMMIT + "'");
        }
        catch (SQLException ex) {
            throw new DatabaseException("could not set synchronous_commit on " + strDatabase, ex);
        }
        log.debug("synchronous_commit = {} on {}", STR_SYNCHRONOUS_COMMIT, strDatabase);
    }


    public synchronized void stop() {
        EmbeddedPostgres postgresHere = postgres;
        if (postgresHere == null)
            return;

        log.info("stopping embedded PostgreSQL on port {}", nPort);
        try {
            postgresHere.close();
        }
        catch (IOException ex) {
            log.warn("embedded PostgreSQL did not close cleanly: {}", ex.toString());
        }
        postgres = null;
    }


    @Override
    public void close() {
        stop();
    }


    private static boolean exists(Connection conn, String strDatabase) throws SQLException {
        try (PreparedStatement stmt =
                conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            stmt.setString(1, strDatabase);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }


    private static void requireValidName(String strDatabase) {
        if (strDatabase == null || !PAT_DB_NAME.matcher(strDatabase).matches())
            throw new IllegalArgumentException(
                    "not a usable database name (lower case, letters, digits and underscore, "
                            + "not starting with a digit): " + strDatabase);
    }
}
