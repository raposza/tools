// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox;

import com.raposza.canton.install.CantonBuiltinConf;
import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.pqs.PqsSpec;
import com.raposza.canton.pqs.ScribeLaunch;
import com.raposza.canton.pqs.ScribeProcess;
import com.raposza.canton.process.Canton3xDaemonProcess;
import com.raposza.canton.process.CantonConsoleProcess;
import com.raposza.canton.process.CantonProcess;
import com.raposza.canton.install.HostPlatform;
import com.raposza.canton.topology.AdminTokenOverlay;
import com.raposza.canton.jdbc.PgShim;
import com.raposza.canton.topology.ClientCheckOverlay;
import com.raposza.canton.topology.AuthOverlay;
import com.raposza.canton.topology.Canton3xBootstrap;
import com.raposza.canton.topology.Canton3xDaemonBootstrap;
import com.raposza.canton.topology.DaemonPortsOverlay;
import com.raposza.canton.topology.RemoteConsoleOverlay;
import com.raposza.canton.topology.StorageOverlay;
import com.raposza.runtime.db.PostgresCoordinates;
import com.raposza.runtime.db.SandboxPostgres;
import com.raposza.runtime.process.ManagedProcess;
import com.raposza.runtime.process.ProcessException;
import com.raposza.runtime.process.ProcessSettle;
import com.raposza.sandbox.process.CantonSandboxProcess;
import com.raposza.sandbox.process.SandboxLauncher;
import com.raposza.sandbox.topology.SandboxSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * A persistent 3.x sandbox: embedded PostgreSQL underneath, Canton on top.
 *
 * The order is not arranged for tidiness. Canton creates its own schemas on
 * first connection and retries a refused one on a backoff, so a participant
 * started before its database exists reaches it eventually and buries the real
 * failure - a wrong port, a name that does not match - under a minute of
 * warnings that look like ordinary start-up noise. Every port is checked, the
 * server is started, the four databases are created, the overlay is written,
 * and only then is Canton launched.
 *
 * Shutdown reverses that, and PostgreSQL goes last: Canton holds connections
 * to it and taking the server away first turns a clean stop into a pile of
 * connection errors that have to be read before they can be dismissed.
 *
 * A console script, when one is asked for, runs as a SECOND process once the
 * stack is serving - `sandbox-console`, because the `sandbox` subcommand
 * refuses `--bootstrap`. See {@link CantonConsoleProcess}.
 *
 * <h2>PQS</h2>
 *
 * Optional, and owned here when it is asked for: {@link #usePqs(PqsSpec)}
 * before {@link #start()}. It starts AFTER Canton is ready, because it connects
 * to the Ledger API, and it stops BEFORE Canton, because a scribe left
 * streaming from a participant that is going away logs a connection failure
 * that reads like a fault. Its database is a fifth, created from the spec's
 * derived name rather than from the stack's prefix, so two scribe versions on
 * one server never share a schema.
 *
 * <h2>Which launcher</h2>
 *
 * Two, and {@link #useLauncher(SandboxLauncher)} chooses. The subcommand is
 * the default and is the one measured with auth, a pinned admin token and
 * start-time DARs; `daemon` is the one that can be started twice against the
 * same storage, and under it those three are REFUSED at {@link #start()}
 * rather than quietly dropped. A stack that was asked for auth and came up
 * without it is a worse outcome than one that would not start.
 *
 * Author Claude/bentzn
 */
public final class SandboxStack implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SandboxStack.class);

    /** NONE. The databases are `participant`, `sequencer` and so on. */
    public static final String STR_DEFAULT_PREFIX = "";

    private static final Duration TIMEOUT_READY = Duration.ofMinutes(5);

    private static final Duration TIMEOUT_STOP = Duration.ofSeconds(60);

    /**
     * The console compiles its script with Ammonite before it runs a line, and
     * that alone took several seconds in the probe that measured this path.
     */
    private static final Duration TIMEOUT_BOOTSTRAP = Duration.ofMinutes(3);

    private static final String STR_OVERLAY_FILE = "storage.conf";

    private static final String STR_AUTH_FILE = "auth.conf";

    private static final String STR_BOOTSTRAP_FILE = "bootstrap.canton";

    private static final String STR_ADMIN_TOKEN_FILE = "admin-token.conf";

    /** Written on Windows only; see {@link ClientCheckOverlay}. */
    private static final String STR_CLIENT_CHECK_FILE = "client-check.conf";

    private static final String STR_CONSOLE_FILE = "console.conf";

    /**
     * The six ports as configuration rather than as flags, because `daemon`
     * has no port options at all. Only written under that launcher.
     */
    private static final String STR_DAEMON_PORTS_FILE = "ports.conf";

    /**
     * Where {@link CantonBuiltinConf} puts the vendor's own topology, named
     * after the jar entry it comes out of. Only written under `daemon`, which
     * processes no default configuration.
     */
    private static final String STR_VENDOR_CONF_FILE = "sandbox.conf";

    /**
     * The raw tail is short on purpose. When a node fails, Canton dumps every
     * thread into the same log, so a long raw tail is a long thread dump and
     * the reason has already scrolled past it.
     */
    private static final int CNT_LOG_TAIL = 15;

    private static final int CNT_PROBLEM_FIRST = 40;

    private static final int CNT_PROBLEM_LAST = 10;

    /**
     * Standard output is where a rejected command line lands, and it never
     * reaches the log because Canton has not opened one yet.
     */
    private static final int CNT_STDOUT_TAIL = 30;

    /**
     * What PQS is told to connect to. The participant is in this process
     * tree.
     *
     * THE LITERAL, not `localhost`. Scribe resolves the name to ::1 and
     * Canton's 3.x daemon binds IPv4 only - it sets no address and takes its
     * own default - so every gRPC call is refused while the database probe on
     * the same host succeeds, because Zonky binds both families. Scribe
     * retries a recoverable gRPC failure with exponential backoff and no
     * attempt limit, so it never gives up and the window shows a hang.
     */
    private static final String STR_HOST_LEDGER = "127.0.0.1";

    private final CantonInstallation installation;
    private final SandboxSpec spec;
    private final Path dirWork;
    private final String strPrefix;
    private final SandboxPostgres postgres;
    private final AuthOverlay auth;

    private PqsSpec specPqs;

    private Canton3xBootstrap bootstrap;

    private AdminTokenOverlay adminToken;

    private SandboxLauncher launcher = SandboxLauncher.SUBCOMMAND;

    private Canton3xDaemonBootstrap bootstrapDaemon;

    private CantonProcess process;

    private ScribeProcess scribe;

    private BiConsumer<String, String> sinkOutput;

    private PhaseSink_i sinkPhase;


    /**
     * @param installation the Canton to launch; must be 3.x
     * @param spec what to launch
     * @param dirWork where the overlay, the port file and the log go
     * @param postgres the embedded server, not yet started
     * @param strPrefix prefix for the four database names, so a second stack on
     *        one server does not collide with the first
     * @param auth what to put on the Ledger API, or null to leave the
     *        subcommand's own auth-services alone
     */
    public SandboxStack(CantonInstallation installation, SandboxSpec spec, Path dirWork,
            SandboxPostgres postgres, String strPrefix, AuthOverlay auth) {
        if (installation == null || spec == null || dirWork == null || postgres == null)
            throw new IllegalArgumentException(
                    "installation, spec, dirWork and postgres are required");
        // Normalised, not refused. Blank is the DEFAULT now and means the
        // databases carry their node names and nothing else.
        strPrefix = strPrefix == null ? "" : strPrefix.trim();

        this.installation = installation;
        this.spec = spec;
        this.dirWork = dirWork.toAbsolutePath().normalize();
        this.postgres = postgres;
        this.strPrefix = strPrefix;
        this.auth = auth;
    }


    public SandboxStack(CantonInstallation installation, SandboxSpec spec, Path dirWork,
            SandboxPostgres postgres, String strPrefix) {
        this(installation, spec, dirWork, postgres, strPrefix, null);
    }


    public SandboxStack(CantonInstallation installation, SandboxSpec spec, Path dirWork) {
        this(installation, spec, dirWork, new SandboxPostgres(), STR_DEFAULT_PREFIX, null);
    }


    /**
     * Where each process's own output goes, in addition to the bounded tail
     * this class already keeps.
     *
     * A process's output does NOT reach standard output. `ManagedProcess`
     * pumps it to its listeners and to a tail and nowhere else, so a caller
     * that wants Canton's own log - a window with a pane per component, for
     * instance - has to be handed it here, and has to be handed it BEFORE the
     * process starts. Attaching afterwards loses the start-up, which is the
     * half worth reading.
     *
     * @param sinkNew called with a {@link StackComponent} name and one line,
     *        from the pump thread of whichever process produced it; null to
     *        send nothing
     * @return this stack
     */
    public SandboxStack useOutputSink(BiConsumer<String, String> sinkNew) {
        this.sinkOutput = sinkNew;
        return this;
    }


    /**
     * @param sinkNew told which component is coming up and how long it took,
     *        or null for none
     * @return this
     */
    public SandboxStack usePhaseSink(PhaseSink_i sinkNew) {
        this.sinkPhase = sinkNew;
        return this;
    }


    /**
     * @param proc the process to listen to, or null
     * @param strComponent which {@link StackComponent} its lines belong to
     */
    private void attach(ManagedProcess proc, String strComponent) {
        BiConsumer<String, String> sinkHere = sinkOutput;
        if (sinkHere == null || proc == null)
            return;
        proc.addOutputListener(strLine -> sinkHere.accept(strComponent, strLine));
    }


    /**
     * @param strComponent which {@link StackComponent} the line belongs to
     * @param strLine one line, without its terminator
     */
    /**
     * @param strComponent which one is coming up
     * @return the clock reading to hand back to {@link #phaseStarted}
     */
    private long phaseStarting(String strComponent) {
        PhaseSink_i sinkHere = sinkPhase;
        if (sinkHere != null)
            sinkHere.starting(strComponent);
        // AFTER the sink, so a slow listener is not counted as start-up time.
        return System.nanoTime();
    }


    private void phaseStarted(String strComponent, long nNanoFrom) {
        PhaseSink_i sinkHere = sinkPhase;
        if (sinkHere != null) {
            sinkHere.started(strComponent,
                    Duration.ofNanos(System.nanoTime() - nNanoFrom));
        }
    }


    private void emit(String strComponent, String strLine) {
        BiConsumer<String, String> sinkHere = sinkOutput;
        if (sinkHere != null)
            sinkHere.accept(strComponent, strLine);
    }


    public SandboxPostgres postgres() {
        return postgres;
    }


    public Path fileOverlay() {
        return dirWork.resolve(STR_OVERLAY_FILE);
    }


    public Path fileAuth() {
        return dirWork.resolve(STR_AUTH_FILE);
    }


    /**
     * @return what is on the Ledger API, or null when the subcommand's own
     *         auth-services was left alone
     */
    public AuthOverlay auth() {
        return auth;
    }


    /**
     * Asks the stack to run PQS as part of itself.
     *
     * MUTATES and returns this, unlike the `withX` methods on the records
     * around it: the stack is not a value, and a copy of a half-configured
     * launcher is not a thing worth having.
     *
     * @param specPqsNew what to run, or null to run none
     * @return this stack
     * @throws ProcessException when the stack is already running
     */
    public SandboxStack usePqs(PqsSpec specPqsNew) {
        if (isRunning())
            throw new ProcessException("PQS has to be configured before the stack starts");
        this.specPqs = specPqsNew;
        return this;
    }


    /**
     * @return what PQS this stack will run, or null for none
     */
    public PqsSpec specPqs() {
        return specPqs;
    }


    /**
     * Asks the stack to run a console script once the nodes are up.
     *
     * MUTATES and returns this, for the same reason {@link #usePqs(PqsSpec)}
     * does.
     *
     * @param bootstrapNew the script to render, or null for none
     * @return this stack
     * @throws ProcessException when the stack is already running
     */
    public SandboxStack useBootstrap(Canton3xBootstrap bootstrapNew) {
        if (isRunning())
            throw new ProcessException("the bootstrap has to be set before the stack starts");
        this.bootstrap = bootstrapNew;
        return this;
    }


    /**
     * @return the console script, or null for none
     */
    public Canton3xBootstrap bootstrap() {
        return bootstrap;
    }


    /**
     * Asks the stack to run a script inside the node JVM at start.
     *
     * The `daemon` counterpart of {@link #useBootstrap(Canton3xBootstrap)},
     * and a different mechanism rather than a different spelling: this one is
     * a launch argument and runs before the stack is ready, where the console
     * script is a second process that attaches after. Setting the wrong one
     * for the chosen launcher is refused at {@link #start()}.
     *
     * Under `daemon` there is ALWAYS a script, because the launcher prints no
     * readiness phrase of its own and the script's last line is the signal -
     * so leaving this unset gets a plain {@link Canton3xDaemonBootstrap}
     * rather than no script.
     *
     * @param bootstrapNew the script to render, or null for the plain one
     * @return this stack
     * @throws ProcessException when the stack is already running
     */
    public SandboxStack useBootstrap(Canton3xDaemonBootstrap bootstrapNew) {
        if (isRunning())
            throw new ProcessException("the bootstrap has to be set before the stack starts");
        this.bootstrapDaemon = bootstrapNew;
        return this;
    }


    /**
     * @return the in-JVM script, or null when the plain one will be used
     */
    public Canton3xDaemonBootstrap bootstrapDaemon() {
        return bootstrapDaemon;
    }


    /**
     * Chooses the launcher.
     *
     * MUTATES and returns this, for the same reason {@link #usePqs(PqsSpec)}
     * does.
     *
     * @param launcherNew which launcher to start on
     * @return this stack
     * @throws ProcessException when the stack is already running
     */
    public SandboxStack useLauncher(SandboxLauncher launcherNew) {
        if (isRunning())
            throw new ProcessException("the launcher has to be chosen before the stack starts");
        if (launcherNew == null)
            throw new IllegalArgumentException("a launcher is required");
        this.launcher = launcherNew;
        return this;
    }


    /**
     * @return which launcher this stack starts on; never null
     */
    public SandboxLauncher launcher() {
        return launcher;
    }


    /**
     * Pins the participant's admin token.
     *
     * Worth doing whenever auth is on: Canton mints one itself otherwise, and
     * the minted one rotates every five minutes and carries NEITHER the admin
     * claim nor act-as-any-party - both measured on 3.5.11. A console script
     * that allocates a party may then succeed while a `users.create` beside it
     * is refused, because only the second goes through the Ledger API.
     *
     * MUTATES and returns this, like {@link #usePqs(PqsSpec)}.
     *
     * @param adminTokenNew the overlay, or null for Canton's own default
     * @return this stack
     * @throws ProcessException when the stack is already running
     */
    public SandboxStack useAdminToken(AdminTokenOverlay adminTokenNew) {
        if (isRunning())
            throw new ProcessException("the admin token has to be set before the stack starts");
        this.adminToken = adminTokenNew;
        return this;
    }


    /**
     * @return what pins the admin token, or null when Canton's own is used
     */
    public AdminTokenOverlay adminToken() {
        return adminToken;
    }


    /**
     * <b>Windows only.</b> Canton sends a client-connection check that
     * PostgreSQL for Windows refuses, on a migration-lock connection that no
     * configuration path reaches, and retries for ever when it is refused.
     * {@link PgShim} answers it in the DataSource instead: the class files are
     * placed under the work directory here, the class is named in the storage
     * overlay, and each launcher puts that directory on Canton's classpath.
     *
     * A jar whose manifest declares no main class cannot be launched in the
     * form that carries a classpath, so that case keeps the stock DataSource
     * rather than naming a class Canton would not find.
     *
     * @return the class every storage block names
     */
    private String strDataSourceClass() {
        if (!PgShim.flagNeeded(HostPlatform.ofDefaults()))
            return PgShim.STR_CLASS_STOCK;
        if (PgShim.strMainClass(installation.fileRuntime()) == null) {
            log.warn("{} declares no main class, so the Windows data source cannot be placed"
                    + " on Canton's classpath; starting on the stock driver",
                    installation.fileRuntime());
            return PgShim.STR_CLASS_STOCK;
        }
        try {
            log.info("windows: data source classes extracted to {}", PgShim.extract(dirWork));
        }
        catch (IOException ex) {
            throw new ProcessException("could not place the data source classes under "
                    + dirWork, ex);
        }
        return PgShim.strDataSourceClass(HostPlatform.ofDefaults());
    }


    /**
     * <b>Windows only.</b> The two ledger-side datasources that send a check
     * PostgreSQL refuses there, and that a configuration can reach; the
     * migration locks are answered in the DataSource by {@link PgShim}. BOTH
     * halves are required - each covers senders the other cannot see.
     *
     * A file of its own, like the others, so a stack that will not start is
     * diagnosed by removing one overlay at a time.
     *
     * @param lstFileConf the -c list this appends to
     */
    private void writeClientCheck(List<Path> lstFileConf) {
        if (!ClientCheckOverlay.flagNeeded(HostPlatform.ofDefaults()))
            return;
        writeConf(fileClientCheck(),
                ClientCheckOverlay.render(StorageOverlay.STR_NODE_PARTICIPANT),
                "client connection check");
        lstFileConf.add(fileClientCheck());
    }


    public Path fileClientCheck() {
        return dirWork.resolve(STR_CLIENT_CHECK_FILE);
    }


    public Path fileAdminToken() {
        return dirWork.resolve(STR_ADMIN_TOKEN_FILE);
    }


    /**
     * @return where the console's own configuration goes - the endpoints it
     *         connects to and the token it presents
     */
    public Path fileConsole() {
        return dirWork.resolve(STR_CONSOLE_FILE);
    }


    public Path fileBootstrap() {
        return dirWork.resolve(STR_BOOTSTRAP_FILE);
    }


    /**
     * @return where `daemon` is handed the six ports, since it has no port
     *         options; unused under the subcommand
     */
    public Path fileDaemonPorts() {
        return dirWork.resolve(STR_DAEMON_PORTS_FILE);
    }


    /**
     * @return where the vendor's own topology is extracted for `daemon`, which
     *         processes no default configuration; unused under the subcommand
     */
    public Path fileVendorConf() {
        return dirWork.resolve(STR_VENDOR_CONF_FILE);
    }


    /**
     * The two scripts write two DIFFERENT marker files, so this follows the
     * launcher rather than naming one of them. A single name here would report
     * a daemon stack un-bootstrapped for as long as the subcommand's marker
     * was absent, which it always is.
     *
     * @return the file the script writes before it makes any console call;
     *         present means the script was reached
     */
    public Path fileBootstrapMarker() {
        return dirWork.resolve(launcher == SandboxLauncher.DAEMON
                ? Canton3xDaemonBootstrap.STR_MARKER_FILE
                : Canton3xBootstrap.STR_MARKER_FILE);
    }


    /**
     * Launcher-aware for the same reason {@link #fileBootstrapMarker()} is,
     * although the two scripts DO happen to agree on this filename today. That
     * agreement is two constants that match, not one definition shared, and a
     * class depending on it silently would break at the first divergence with
     * a null participant id rather than a compile error.
     *
     * @return where the running script writes the participant id
     */
    public Path fileParticipantId() {
        return dirWork.resolve(launcher == SandboxLauncher.DAEMON
                ? Canton3xDaemonBootstrap.STR_PARTICIPANT_ID_FILE
                : Canton3xBootstrap.STR_PARTICIPANT_ID_FILE);
    }


    /**
     * @return the participant id the script wrote, or null when it has not -
     *         which is a different fact from the stack not being ready, because
     *         the port file and the script are independent signals
     */
    public String participantId() {
        Path file = fileParticipantId();
        try {
            return Files.isRegularFile(file) ? Files.readString(file).trim() : null;
        }
        catch (IOException ex) {
            return null;
        }
    }


    /**
     * @return the running PQS, or null when none was asked for or the stack has
     *         not started; {@link ScribeProcess#isReal()} says whether it
     *         measures scribe or only the wiring
     */
    public ScribeProcess pqs() {
        return scribe;
    }


    public int portLedgerApi() {
        return spec.ports().nPortLedgerApi();
    }


    public int portJsonApi() {
        return spec.ports().nPortJsonApi();
    }


    public boolean isRunning() {
        return process != null && process.isRunning();
    }


    /**
     * @return the running Canton process, or null before {@link #start()}; its
     *         concrete class follows {@link #launcher()}
     */
    public CantonProcess process() {
        return process;
    }


    public void start() {
        start(TIMEOUT_READY);
    }


    /**
     * @param timeout how long to wait for Canton to serve
     * @throws ProcessException when the stack does not come up
     */
    public void start(Duration timeout) {
        if (isRunning())
            throw new ProcessException("the stack is already running");

        // FIRST, before a port is probed or a server is started: what this
        // launcher cannot carry. Refusing here costs nothing and leaves
        // nothing running.
        requireLauncherCanCarry();

        // Every port at once, so one run names all the clashes rather than one
        // per attempt.
        spec.ports().requireFree(postgres.port());

        long nNanoPg = phaseStarting(StackComponent.STR_POSTGRES);
        emit(StackComponent.STR_POSTGRES,
                "starting the embedded server on port " + postgres.port());
        postgres.start();
        emit(StackComponent.STR_POSTGRES,
                "up on " + postgres.host() + ":" + postgres.port());
        phaseStarted(StackComponent.STR_POSTGRES, nNanoPg);
        try {
            StorageOverlay overlay = ensureDatabases();
            writeConf(fileOverlay(), overlay.render(strDataSourceClass()), "storage");

            process = launcher == SandboxLauncher.DAEMON
                    ? buildDaemon()
                    : buildSubcommand();
            attach(process, StackComponent.STR_PARTICIPANT);
            long nNanoNode = phaseStarting(StackComponent.STR_PARTICIPANT);
            process.start();

            boolean flagReady;
            try {
                flagReady = process.awaitReady(timeout);
            }
            catch (ProcessException ex) {
                // The log is the half that says why WHEN THERE IS ONE. Canton
                // rejects a bad command line before it opens the file, and on
                // 2026-08-12 that produced a failure reporting only "no Canton
                // log" - the parse error was on standard output, in the
                // exception being discarded here. Both, now, and chained.
                throw failure("the sandbox died during start-up", ex);
            }

            if (!flagReady)
                throw failure("the sandbox was not ready within " + timeout);
            requireSettled();
            phaseStarted(StackComponent.STR_PARTICIPANT, nNanoNode);

            // The console attaches to nodes that are already up, so it can
            // only run now. It is a second process rather than a flag on the
            // first: the sandbox subcommand refuses --bootstrap.
            if (bootstrap != null)
                runBootstrap();

            // Last, and inside the same try: PQS connects to a Ledger API that
            // has to be serving already, and a failure here has to take the
            // rest of the stack down with it.
            if (specPqs != null) {
                long nNanoPqs = phaseStarting(StackComponent.STR_PQS);
                startPqs();
                phaseStarted(StackComponent.STR_PQS, nNanoPqs);
            }
        }
        catch (RuntimeException ex) {
            stop();
            throw ex;
        }

        log.info("stack ready on {}: ledger-api {}, json-api {}, postgres {}, auth {},"
                + " admin-token {}, pqs {}, participant {}",
                launcher, portLedgerApi(), portJsonApi(), postgres.port(),
                auth == null ? "none" : auth.describe(),
                adminToken == null ? "canton's own" : adminToken.describe(),
                specPqs == null ? "none" : specPqs.describe(),
                participantId() == null ? "id not written" : participantId());
    }


    /**
     * What each launcher cannot carry, refused before anything is started.
     *
     * One remains per launcher, and both are the same kind of thing: a
     * bootstrap of the wrong kind, which is a mechanism mismatch that would
     * otherwise be silently ignored.
     *
     * DARs are not refused here. They are carried by the daemon bootstrap,
     * which is why there is no refusal rather than a relaxed one - the reason
     * one would exist is that a stack asked for DARs and coming up with none
     * is a passing start and a wrong sandbox, and that reason is satisfied by
     * uploading them rather than by refusing.
     *
     * Auth and the admin token are not refused either, and the reason a
     * refusal would be defensible - that neither overlay had been rendered
     * over the vendor's own `sandbox.conf` - no longer holds, because both
     * now are.
     *
     * Package-private rather than private so the refusals can be asserted
     * without a Canton and without a PostgreSQL: through `start()` they fire
     * before the port guard, but their ABSENCE cannot be tested that way -
     * a start that gets past here goes on to probe ports and boot a cluster.
     */
    void requireLauncherCanCarry() {
        if (launcher == SandboxLauncher.SUBCOMMAND) {
            if (bootstrapDaemon != null) {
                throw new ProcessException("a daemon bootstrap was set on a stack launched with"
                        + " the sandbox subcommand, which refuses --bootstrap and runs a console"
                        + " script as a second process instead. Use"
                        + " useBootstrap(Canton3xBootstrap), or useLauncher(DAEMON)");
            }
            return;
        }

        if (bootstrap != null) {
            throw new ProcessException("a console bootstrap was set on a stack launched with"
                    + " daemon, which runs its script inside the node JVM. Use"
                    + " useBootstrap(Canton3xDaemonBootstrap)");
        }
    }


    /**
     * The subcommand's configuration: the overlays this project writes, over a
     * topology and a set of ports the subcommand supplies itself.
     *
     * Two files rather than one, and separate on purpose: a stack that will not
     * start is diagnosed by removing one overlay at a time, and a combined file
     * cannot be halved.
     */
    private CantonProcess buildSubcommand() {
        List<Path> lstFileConf = new ArrayList<>();
        lstFileConf.add(fileOverlay());
        if (auth != null) {
            writeConf(fileAuth(), auth.render(), "auth");
            lstFileConf.add(fileAuth());
        }
        if (adminToken != null) {
            // A third file for the same reason as the second: a stack that
            // will not start is diagnosed by removing one overlay at a time,
            // and this one carries INFERRED key names, so it is the first
            // candidate when a parse fails.
            writeConf(fileAdminToken(), adminToken.render(), "admin token");
            lstFileConf.add(fileAdminToken());
        }
        writeClientCheck(lstFileConf);
        return new CantonSandboxProcess(installation, spec, dirWork, lstFileConf);
    }


    /**
     * The daemon's configuration, which has to carry everything the subcommand
     * supplied: `daemon` processes NO default configuration and has NO port
     * options.
     *
     * The topology is still not hand-written: it is extracted
     * from the jar. Order is Canton's: a later `-c` wins where two set the same
     * key, so the vendor file goes first and the overlays after it.
     *
     * Auth and the admin token are the same two files the subcommand writes,
     * rendered by the same two overlays and handed over in the same order.
     * What is different here is only what they land ON: the subcommand
     * processes `sandbox.conf` from inside the jar and never names it, and this
     * launcher passes it as the first `-c`. Both are the same HOCON merge, and
     * that argument is why the composition is shaped this way - the START is
     * what says Canton agrees, because it refuses a key it does not know.
     */
    private CantonProcess buildDaemon() {
        List<Path> lstFileConf = new ArrayList<>();
        lstFileConf.add(CantonBuiltinConf.extractSandboxConf(installation, dirWork));
        log.info("vendor topology extracted to {}", fileVendorConf());
        lstFileConf.add(fileOverlay());

        writeConf(fileDaemonPorts(), new DaemonPortsOverlay(spec.ports()).render(), "daemon ports");
        lstFileConf.add(fileDaemonPorts());

        // One file each, for the reason buildSubcommand() gives: a stack that
        // will not start is diagnosed by removing one overlay at a time, and a
        // combined file cannot be halved. Under this launcher there is a
        // fourth file to remove as well - the vendor's own - which is what
        // separates "the overlay is wrong" from "the topology is".
        if (auth != null) {
            writeConf(fileAuth(), auth.render(), "auth");
            lstFileConf.add(fileAuth());
        }
        if (adminToken != null) {
            writeConf(fileAdminToken(), adminToken.render(), "admin token");
            lstFileConf.add(fileAdminToken());
        }

        writeClientCheck(lstFileConf);

        // There is always a script under this launcher, set or not: `daemon`
        // prints no readiness phrase of its own, so the script's last line is
        // the signal and a stack without one never reports ready.
        Canton3xDaemonBootstrap bootstrapHere = bootstrapDaemon == null
                ? new Canton3xDaemonBootstrap()
                : bootstrapDaemon;

        // The DARs come off the SPEC, not off the bootstrap, so a caller says
        // it once and the same way under both launchers: the subcommand turns
        // `spec.lstFileDar()` into `--dar`, and this turns it into upload
        // calls. A bootstrap handed in carrying DARs of its own keeps them
        // only when the spec names none.
        if (!spec.lstFileDar().isEmpty()) {
            for (Path fileDar : spec.lstFileDar()) {
                // Before anything starts. Inside the node JVM a missing path
                // is a CommandFailure with no code, and costs a full
                // start to read.
                if (!Files.isRegularFile(fileDar))
                    throw new ProcessException("DAR not found: " + fileDar);
            }
            bootstrapHere = bootstrapHere.withDars(spec.lstFileDar());
        }

        writeConf(fileBootstrap(), bootstrapHere.render(dirWork), "daemon bootstrap");

        // Its own sentinels are cleared by the process, not here: it clears
        // five of them and this class knows about two.
        return new Canton3xDaemonProcess(installation, lstFileConf, fileBootstrap(), dirWork,
                spec.nHeapMb());
    }


    /**
     * Runs the console script against the serving stack, and waits for it to
     * finish rather than for it to serve: this process is supposed to exit.
     */
    private void runBootstrap() {
        // Both sentinels first. Left from a previous run they would report this
        // one bootstrapped before the script had run, which is the same defect
        // the port file is cleared for.
        deleteIfPresent(fileBootstrapMarker());
        deleteIfPresent(fileParticipantId());
        writeConf(fileBootstrap(), bootstrap.render(dirWork), "bootstrap");

        // The console is a REMOTE one and knows nothing the participant was
        // configured with. Without the token here its Ledger API calls arrive
        // with no credential, and the admin API ones still work - which is how
        // a run can look like it authenticated when it never did.
        RemoteConsoleOverlay overlayConsole = RemoteConsoleOverlay.of(spec.ports(),
                adminToken == null ? null : adminToken.strToken());
        writeConf(fileConsole(), overlayConsole.render(), "console");
        log.info("console connects to {}", overlayConsole.describe());

        CantonConsoleProcess console = new CantonConsoleProcess(installation, fileBootstrap(),
                fileConsole(), dirWork);
        attach(console, StackComponent.STR_PARTICIPANT);
        try {
            console.start();

            boolean flagDone;
            try {
                flagDone = console.awaitReady(TIMEOUT_BOOTSTRAP);
            }
            catch (ProcessException ex) {
                throw new ProcessException("the console died before the script finished;"
                        + " last output:\n" + String.join("\n", console.tail(40)), ex);
            }

            if (!flagDone)
                throw new ProcessException("the console script did not finish within "
                        + TIMEOUT_BOOTSTRAP + "; last output:\n"
                        + String.join("\n", console.tail(40)));
        }
        finally {
            // It exits on its own after the script. This is the case where it
            // did not, and a console left at a prompt holds the work directory
            // and a connection to a participant that is about to go away.
            console.stop(TIMEOUT_STOP);
        }

        log.info("bootstrap complete: participant {}", participantId());
    }


    /**
     * The database is created here rather than beside the participant's four,
     * because its name comes from the scribe binary and not from this stack's
     * prefix. A stack started twice against two scribe versions therefore ends
     * up with two databases, which is the point.
     */
    private void startPqs() {
        PostgresCoordinates pgPqs = postgres.ensureDatabase(specPqs.databaseName());
        log.info("starting PQS: {}", specPqs.describe());

        ScribeProcess scribeNew = ScribeProcess.of(specPqs, pgPqs, STR_HOST_LEDGER,
                portLedgerApi(), dirWork, spec.ports().nPortPqsHealth());
        scribe = scribeNew;
        attach(scribeNew, StackComponent.STR_PQS);
        ScribeLaunch.startAndSettle(scribeNew, specPqs.timeoutReady(), SETTLE_AFTER_READY,
                CNT_LOG_TAIL);
    }


    /**
     * How long ready has to hold before the stack believes it.
     *
     * See {@link ProcessSettle} for the failure that put it there. The cost is
     * this much added to every successful start; it buys a started stack that
     * is actually serving, and - because failure() dumps the exit code, the log
     * problems, the log tail and standard output - the reason on the way past.
     */
    private static final Duration SETTLE_AFTER_READY = ProcessSettle.SETTLE_DEFAULT;


    /**
     * Ready is a line the process printed. Running is a state, and only the
     * second one is worth reporting.
     *
     * @throws ProcessException when the process exits inside the window
     */
    private void requireSettled() {
        if (!ProcessSettle.holds(process::isRunning, SETTLE_AFTER_READY))
            throw failure("the sandbox reported ready and then exited");
    }


    private ProcessException failure(String strWhat) {
        return failure(strWhat, null);
    }


    /**
     * Both records of a failed start, in the order they are useful.
     *
     * Canton's log first, because that is where a node says why it stopped.
     * Standard output second, because on a bad start it is often a thread dump
     * - a hundred parked threads that say nothing about the cause and, at any
     * sensible tail length, push the cause out of view. Reporting only that is
     * how a failure gets described by its symptom.
     *
     * @param strWhat what went wrong
     * @param cause what to wrap, or null
     * @return what to throw
     */
    private ProcessException failure(String strWhat, Throwable cause) {
        CantonProcess processHere = process;
        if (processHere == null)
            return new ProcessException(strWhat, cause);

        Integer nExit = processHere.exitCode();
        String strMessage = strWhat
                + " (exit code " + (nExit == null ? "none, still running" : nExit) + ")"
                + "\n--- problems in " + processHere.fileLog() + " ---\n"
                + String.join("\n", processHere.logProblems(CNT_PROBLEM_FIRST, CNT_PROBLEM_LAST))
                + "\n--- " + processHere.fileLog() + " (last " + CNT_LOG_TAIL + " lines, raw) ---\n"
                + String.join("\n", processHere.logTail(CNT_LOG_TAIL))
                + "\n--- standard output (last " + CNT_STDOUT_TAIL + " lines) ---\n"
                + String.join("\n", processHere.tail(CNT_STDOUT_TAIL));
        return new ProcessException(strMessage, cause);
    }


    /**
     * PQS first, then Canton, then the server both were connected to.
     *
     * scribe goes ahead of the participant it streams from for the same reason
     * the participant goes ahead of PostgreSQL: a client left connected to a
     * server that is being taken away reports the shutdown as a fault, and a
     * clean stop then has to be read past several of those.
     */
    public void stop() {
        ScribeProcess scribeHere = scribe;
        if (scribeHere != null) {
            scribeHere.stop(TIMEOUT_STOP);
            scribe = null;
        }

        CantonProcess processHere = process;
        if (processHere != null) {
            processHere.stop(TIMEOUT_STOP);
            process = null;
        }
        postgres.stop();
    }


    @Override
    public void close() {
        stop();
    }


    private StorageOverlay ensureDatabases() {
        List<PostgresCoordinates> lstCoordinates = new ArrayList<>();
        for (String strName : StorageOverlay.databaseNames(strPrefix)) {
            lstCoordinates.add(postgres.ensureDatabase(strName));
        }
        return new StorageOverlay(lstCoordinates.get(0), lstCoordinates.get(1),
                lstCoordinates.get(2), lstCoordinates.get(3));
    }


    private static void deleteIfPresent(Path file) {
        try {
            Files.deleteIfExists(file);
        }
        catch (IOException ex) {
            throw new ProcessException("could not clear the stale sentinel " + file, ex);
        }
    }


    private void writeConf(Path fileConf, String strContent, String strWhat) {
        try {
            if (!Files.isDirectory(dirWork))
                Files.createDirectories(dirWork);
            Files.writeString(fileConf, strContent, StandardCharsets.UTF_8);
        }
        catch (IOException ex) {
            throw new ProcessException("could not write " + fileConf, ex);
        }
        log.info("{} overlay written to {}", strWhat, fileConf);
    }
}
