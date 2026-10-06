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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.raposza.runtime.db.SandboxPostgres;
import com.raposza.runtime.lifecycle.StackService_i;

/**
 * Starts LocalNet natively: embedded PostgreSQL, then the two JVM invocations
 * that Docker runs as two containers.
 *
 * TWO BINARIES, NOT ONE - measured 2026-08-26. The splice namespace runs on
 * `bin/splice-node` from the Splice bundle. The canton namespace runs on stock
 * `canton-open-source-<v>.jar`, which the bundle does NOT contain, and against
 * `splice-node.jar` that same configuration produces no participant, sequencer
 * or mediator while still logging `Canton started`. Everything before this ran
 * both namespaces on the bundle jar, which is why the canton side was always
 * empty.
 *
 * WHY THE GATES ARE THE POINT. Every node in LocalNet's tree is
 * `identity.type = manual` with `generate-topology-transactions-and-keys =
 * false`, so it comes up uninitialised and binds nothing until the Splice app
 * layer drives it. The log, the config dump, thread names and start duration are
 * all identical whether the configuration contains five nodes or none, so none
 * of them is a detector. A TCP connect and the readyz endpoints in
 * docker/splice/health-check.sh are, because they answer only once
 * initialisation has actually happened.
 *
 * Author Claude/bentzn
 */
public final class LocalNetRunner implements AutoCloseable, StackService_i {

    /** PUBLIC since 2026-09-22: the discovery document states what to dial. */
    public static final String STR_HOST = "127.0.0.1";

    /** The canton namespace, which is also its component name. */
    public static final String STR_NS_CANTON = "canton";

    /** The splice namespace, which is also its component name. */
    public static final String STR_NS_SPLICE = "splice";

    /** The embedded PostgreSQL every node's storage points at. */
    public static final String STR_COMP_POSTGRES = "postgres";

    /** The bundle's web UIs, served in process. */
    public static final String STR_COMP_WEB = "web";

    /**
     * Written in the run directory while the stack serves and removed when it
     * stops, the way `ReadyReport` writes `sandbox.properties` beside a
     * Sandbox. It exists so a script can READ the ports instead of parsing a
     * log.
     */
    public static final String STR_FILE_STATUS = "localnet.properties";

    private static final String[] ARR_ROLE = { "sv", "app-provider", "app-user" };

    private static final int N_PORT_PG = LocalNetSpec.N_PORT_PG;

    /**
     * Zonky starts its cluster with settings chosen for a test harness, and a
     * whole LocalNet topology is not one: seven Canton nodes and five Splice
     * apps open pools against twelve databases on one server. `max_connections`
     * is a postmaster parameter, so it cannot be raised once the server is up,
     * and exhausting it surfaces as `FATAL: sorry, too many clients already`
     * from whichever node asked last rather than from whatever holds the
     * connections. The number is a ceiling, not a measurement - the run reports
     * what was actually in use so the next one can be sized rather than guessed.
     */
    private static final String STR_MAX_CONNECTIONS = "1000";

    private static final Duration TIMEOUT_GATE = Duration.ofMinutes(8);

    /** Not an exit status. See main. */
    private static final int N_RC_PARKED = -1;

    /** The cluster, under the run directory. */
    public static final String STR_DIR_PG = "pgdata";

    private final Path dirBundle;
    private final Path dirRun;
    private final CantonImage image;
    private final boolean flagFresh;
    private final boolean flagOnce;
    private final boolean flagUi;
    private final LocalNetStager stager;

    /** Lays a cluster down between the wipe and PostgreSQL, or null. */
    private Consumer<Path> runClusterPrepare;

    /** Every port this stack binds, and the rule that assigned them. */
    private final LocalNetPorts ports;

    /** What every participant verifies on its Ledger API. */
    private final LocalNetAuth auth;
    private final List<Process> lstProcess = new ArrayList<>();
    private final Map<String, Process> mapProcess = new LinkedHashMap<>();

    /** Component to the command line it was launched with, filled by launch. */
    private final Map<String, List<String>> mapCommand = new LinkedHashMap<>();

    /** Component to the conf files it was launched on, filled by launch. */
    private final Map<String, List<Path>> mapFileConf = new LinkedHashMap<>();

    private SandboxPostgres postgres;
    private LocalNetWeb web;
    private List<LocalNetUi.Site> lstSiteWeb = List.of();
    private volatile boolean flagClosed;
    private volatile State state = State.STOPPED;
    private LocalNetProbe probe;
    private BiConsumer<String, String> sinkComponent;
    private Consumer<String> sinkMilestone;

    /**
     * @param dirBundle the extracted splice-node bundle
     * @param dirRun where the staged conf, the logs and the cluster live
     * @param image where stock Canton and the image-only conf are banked
     * @param flagFresh delete the run directory, cluster included, first
     * @param flagOnce tear the stack down after the probe instead of staying up
     * @param flagUi serve the bundle's web-uis on 2000, 3000 and 4000
     */
    public LocalNetRunner(Path dirBundle, Path dirRun, CantonImage image, boolean flagFresh,
            boolean flagOnce, boolean flagUi) {
        this(dirBundle, dirRun, image, flagFresh, flagOnce, flagUi,
                LocalNetPorts.ofDefaults(), LocalNetAuth.ofUnsafe());
    }


    /**
     * @param dirBundle the extracted splice-node bundle
     * @param dirRun where the staged conf, the logs and the cluster live
     * @param image where stock Canton and the image-only conf are banked
     * @param flagFresh delete the run directory, cluster included, first
     * @param flagOnce tear the stack down after the probe instead of staying up
     * @param flagUi serve the bundle's web-uis
     * @param portsNew the numbering this stack binds
     */
    public LocalNetRunner(Path dirBundle, Path dirRun, CantonImage image, boolean flagFresh,
            boolean flagOnce, boolean flagUi, LocalNetPorts portsNew) {
        this(dirBundle, dirRun, image, flagFresh, flagOnce, flagUi, portsNew,
                LocalNetAuth.ofUnsafe());
    }


    /**
     * @param dirBundle the extracted splice-node bundle
     * @param dirRun where the staged conf, the logs and the cluster live
     * @param image where stock Canton and the image-only conf are banked
     * @param flagFresh delete the run directory, cluster included, first
     * @param flagOnce tear the stack down after the probe instead of staying up
     * @param flagUi serve the bundle's web-uis
     * @param portsNew the numbering this stack binds
     * @param authNew what every participant verifies
     */
    public LocalNetRunner(Path dirBundle, Path dirRun, CantonImage image, boolean flagFresh,
            boolean flagOnce, boolean flagUi, LocalNetPorts portsNew, LocalNetAuth authNew) {
        this.dirBundle = dirBundle.toAbsolutePath().normalize();
        this.dirRun = dirRun.toAbsolutePath().normalize();
        this.image = image;
        this.flagFresh = flagFresh;
        this.flagOnce = flagOnce;
        this.flagUi = flagUi;
        this.ports = portsNew;
        this.auth = authNew;
        this.stager = new LocalNetStager(this.dirBundle, this.dirRun, authNew);
    }


    /**
     * DO NOT CALL System.exit AFTER A PARK. The stop runs from a shutdown hook,
     * so by the time run() returns from a parked stack the JVM is already inside
     * Shutdown, and a second exit() from this thread blocks on it forever - the
     * process would hang with everything already stopped. N_RC_PARKED is the
     * runner saying "the hook is the terminator, leave the exit alone".
     */
    public static void main(String[] args) throws Exception {
        int nRc = nRun(args);
        if (nRc == N_RC_PARKED)
            return;
        System.exit(nRc);
    }


    /**
     * @param args the command line
     * @return 0 up and clean, 1 a gate or the probe failed, 2 nothing was
     *         started, N_RC_PARKED the stack is up and the hook owns the exit
     */
    private static int nRun(String[] args) throws Exception {
        List<String> lstArg = new ArrayList<>();
        boolean flagFresh = false;
        boolean flagOnce = false;
        boolean flagUi = true;
        Path fileJarPinned = null;
        for (int cntArg = 0; cntArg < args.length; cntArg++) {
            String strArg = args[cntArg];
            if ("--fresh".equals(strArg)) {
                flagFresh = true;
            }
            else if ("--once".equals(strArg)) {
                flagOnce = true;
            }
            else if ("--no-ui".equals(strArg)) {
                flagUi = false;
            }
            else if ("--keep".equals(strArg)) {
                System.out.println("--- --keep is the default now and is ignored");
            }
            else if ("--canton-jar".equals(strArg)) {
                if (cntArg + 1 >= args.length) {
                    System.out.println("--canton-jar needs a path");
                    return 2;
                }
                fileJarPinned = Path.of(args[++cntArg]);
            }
            else if (strArg.startsWith("--canton-jar=")) {
                fileJarPinned = Path.of(strArg.substring("--canton-jar=".length()));
            }
            else {
                lstArg.add(strArg);
            }
        }
        if (lstArg.isEmpty()) {
            System.out.println("usage: LocalNetRunner <splice-version|bundle-path> [rundir]"
                    + " [--fresh] [--once] [--no-ui] [--canton-jar <path>]");
            System.out.println("   eg: LocalNetRunner 0.7.4 --fresh");
            System.out.println("   the stack STAYS UP until Ctrl-C unless --once is given");
            System.out.println("   --fresh      deletes the run directory, cluster included");
            System.out.println("   --once       probes once, stops the stack, exits 0 or 1");
            System.out.println("   --no-ui      serves no web pages; the ledger is unaffected");
            System.out.println("   --canton-jar runs THAT Canton instead of the newest in "
                    + CantonImage.STR_ENV_DIR);
            return 2;
        }

        try (LocalNetRunner runner = of(lstArg.get(0),
                lstArg.size() > 1 ? Path.of(lstArg.get(1)) : null, fileJarPinned,
                flagFresh, flagOnce, flagUi)) {
            return runner.run();
        }
        catch (StartException ex) {
            System.out.println(ex.getMessage());
            return ex.nExit();
        }
    }


    /**
     * WHERE THE BUNDLE, THE CANTON JAR AND THE RUN DIRECTORY ARE RESOLVED, for
     * the command line and for a window alike.
     *
     * It was inline in nRun until there was a second caller. A window that
     * repeated the rules would drift on the first one either side gained, and
     * the drift would show as a stack that starts from the terminal and not
     * from the window, or the reverse.
     *
     * @param strBundle a Splice version, or a path to an extracted bundle
     * @param dirRunWanted where to run, or null for ~/.splice/native-localnet
     * @param fileJarPinned a stock Canton jar to run instead of the newest one
     *        the environment banks, or null
     * @param flagFresh delete the run directory, cluster included, first
     * @param flagOnce tear the stack down after the probe
     * @param flagUi serve the bundle's web-uis
     * @return a runner that has NOT been started
     * @throws StartException 2, naming what is missing, when anything does not
     *         resolve
     */
    public static LocalNetRunner of(String strBundle, Path dirRunWanted, Path fileJarPinned,
            boolean flagFresh, boolean flagOnce, boolean flagUi) {
        return of(strBundle, dirRunWanted, fileJarPinned, flagFresh, flagOnce, flagUi,
                LocalNetPorts.ofDefaults(), LocalNetAuth.ofUnsafe());
    }


    /**
     * The same resolution, on a numbering the caller chose.
     *
     * @param strBundle a Splice version, or a path to an extracted bundle
     * @param dirRunWanted where to run, or null for ~/.splice/native-localnet
     * @param fileJarPinned a stock Canton jar, or null
     * @param flagFresh delete the run directory, cluster included, first
     * @param flagOnce tear the stack down after the probe
     * @param flagUi serve the bundle's web-uis
     * @param portsNew the numbering this stack binds
     * @return a runner that has NOT been started
     */
    public static LocalNetRunner of(String strBundle, Path dirRunWanted, Path fileJarPinned,
            boolean flagFresh, boolean flagOnce, boolean flagUi, LocalNetPorts portsNew) {
        return of(strBundle, dirRunWanted, fileJarPinned, flagFresh, flagOnce, flagUi,
                portsNew, LocalNetAuth.ofUnsafe());
    }


    /**
     * The same resolution, on a numbering and an auth the caller chose.
     *
     * @param strBundle a Splice version, or a path to an extracted bundle
     * @param dirRunWanted where to run, or null for ~/.splice/native-localnet
     * @param fileJarPinned a stock Canton jar, or null
     * @param flagFresh delete the run directory, cluster included, first
     * @param flagOnce tear the stack down after the probe
     * @param flagUi serve the bundle's web-uis
     * @param portsNew the numbering this stack binds
     * @param authNew what every participant verifies
     * @return a runner that has NOT been started
     * @throws StartException 2, naming what is missing, when anything does not
     *         resolve
     */
    public static LocalNetRunner of(String strBundle, Path dirRunWanted, Path fileJarPinned,
            boolean flagFresh, boolean flagOnce, boolean flagUi, LocalNetPorts portsNew,
            LocalNetAuth authNew) {
        if (fileJarPinned != null && !Files.isRegularFile(fileJarPinned))
            throw new StartException(2, "NOT A FILE: " + fileJarPinned);

        Path dirBundle = Path.of(strBundle);
        if (!Files.isDirectory(dirBundle)) {
            dirBundle = SpliceInstallations.dirBundle(strBundle);
        }
        if (!Files.isExecutable(dirBundle.resolve("bin/splice-node")))
            throw new StartException(2, "NOT FOUND: " + dirBundle.resolve("bin/splice-node"));
        // A BUNDLE WHOSE OWN EXECUTABLE DOES NOT START IS REFUSED HERE, in two
        // seconds and once per version, rather than as eight DOWN gates and a
        // FATAL - Splice 0.8.2 on 2026-09-23. SpliceCheck says how it is told.
        String strWhyNot = SpliceCheck.strWhyNotStarts(dirBundle);
        if (strWhyNot != null)
            throw new StartException(2, "Splice at " + dirBundle + " cannot be started: "
                    + strWhyNot);

        CantonImage image = CantonImage.fromEnvironment();
        if (fileJarPinned != null)
            image = image.withJar(fileJarPinned);
        if (image.fileJar() == null) {
            throw new StartException(2, "NO STOCK CANTON JAR in " + image.dir()
                    + System.lineSeparator()
                    + "the canton namespace does not run on splice-node.jar;"
                    + System.lineSeparator()
                    + "put canton-open-source-<version>.jar there, or set "
                    + CantonImage.STR_ENV_DIR);
        }

        Path dirRun = dirRunWanted != null ? dirRunWanted
                : SpliceInstallations.dirRootDefault().resolve(SpliceInstallations.STR_DIR_RUN);
        return new LocalNetRunner(dirBundle, dirRun, image, flagFresh, flagOnce, flagUi,
                portsNew, authNew);
    }


    /**
     * THE TERMINAL'S RUN: {@link #start()} plus the three things only a
     * terminal wants - the summary, the verdict and the park. A window calls
     * start() and renders the same facts itself.
     *
     * @return 0, 1, 2 or N_RC_PARKED, per nRun
     */
    public int run() throws Exception {
        try {
            start();
        }
        catch (StartException ex) {
            return ex.nExit();
        }

        if (flagOnce) {
            boolean flagProbe = probe.printVerdict();
            close();
            return flagProbe ? 0 : 1;
        }
        park(probe);
        return N_RC_PARKED;
    }


    /**
     * Brings the stack up and returns once every gate has answered and the
     * probe has run. Blocks, for as long as eight minutes.
     *
     * <h2>What it deliberately does NOT do</h2>
     *
     * It does not park, print the summary, print the verdict, or STOP a stack
     * whose gates did not come up. The last of those is the load-bearing one
     * and run() has always relied on it: a failed start leaves every process
     * standing so it can be asked what is wrong, and close() stays the
     * caller's to make.
     *
     * @throws StartException 2 when nothing was started, 1 when a gate did not
     *         answer inside the timeout
     */
    @Override
    public void start() {
        if (state == State.RUNNING || state == State.STARTING)
            throw new StartException(2, "a LocalNet is already up in this process");
        state = State.STARTING;
        try {
            startGates();
            state = State.RUNNING;
            writeStatus();
        }
        catch (StartException ex) {
            state = State.FAILED;
            throw ex;
        }
        catch (Exception ex) {
            state = State.FAILED;
            throw new StartException(1, ex.toString());
        }
    }


    /**
     * The sequence itself, exactly as run() carried it, with every line going
     * to the milestone sink instead of the stream.
     *
     * @throws Exception whatever a step throws; start() states it
     */
    private void startGates() throws Exception {
        // A RUNNER STOPPED BEFORE IT STARTED STAYS STOPPED - `todo.md` A-34 (d).
        requireOpen();
        // THE JAR NAME IS FOR THE TERMINAL ONLY - a window prints the
        // installed VERSION instead, which is what it knows and what reads.
        outCli("Canton " + image.fileJar().getFileName());

        // BEFORE THE DELETE, NOT AFTER. --fresh once wiped the run directory of
        // a stack that was still running and only then failed on the port.
        Map<String, Integer> mapOpen = LocalNetStatus.mapOpen(STR_HOST, ports);
        if (!mapOpen.isEmpty()) {
            // ONE LINE, NOT ONE PER PORT - operator instruction, 2026-09-22.
            // Fifteen lines naming every port said the same thing fifteen
            // times, and in the Sandbox window they are fifteen milestones
            // about a start that did not happen.
            int nPortLow = Integer.MAX_VALUE;
            String strWho = "";
            for (Map.Entry<String, Integer> entry : mapOpen.entrySet()) {
                if (entry.getValue().intValue() < nPortLow) {
                    nPortLow = entry.getValue().intValue();
                    strWho = entry.getKey();
                }
            }
            out("another LocalNet is already running - nothing was touched");
            out("  " + mapOpen.size() + " ports are held, the lowest " + nPortLow
                    + " by " + strWho + "; status-localnet.sh lists them");
            LocalNetStatus.printHowToStop();
            throw new StartException(2, "another LocalNet is holding the ports");
        }

        if (flagFresh && Files.exists(dirRun)) {
            deleteTree(dirRun);
            out("Run directory cleared");
        }
        Files.createDirectories(dirRun);

        // BETWEEN THE WIPE AND POSTGRESQL, which is the only moment a caller
        // can lay a cluster down - see useClusterPrepare.
        if (runClusterPrepare != null)
            runClusterPrepare.accept(dirPgData());

        startPostgres();
        // ONE LINE FOR BOTH LAUNCHES. `Starting ` is what the window spins
        // on, so the staging and the two launches show dots rather than a
        // pause with nothing under it.
        out("Starting Splice");
        Path fileCanton = stageOne(STR_NS_CANTON, image.lstConf());
        Path fileSplice = stageOne(STR_NS_SPLICE, List.of());

        launch(STR_NS_CANTON, fileCanton);
        launch(STR_NS_SPLICE, fileSplice);

        boolean flagUp = awaitGates();
        report(flagUp);
        if (!flagUp)
            throw new StartException(1, "not every gate came up");

        requireOpen();
        startWeb();

        probe = new LocalNetProbe(STR_HOST, ports, stager);
        probe.runChecks();
        // AND A STOP THAT LANDED DURING THE PROBE IS A START THAT DID NOT
        // HAPPEN, not one reported RUNNING over a stack that is gone.
        requireOpen();
    }


    /**
     * THE STOP THAT LANDS WHILE THE START IS STILL RUNNING - `todo.md` A-34 (d).
     *
     * A window cancels a start by calling {@link #stop()} from another thread
     * while this one is inside {@link #startGates()} - blocked on a snapshot
     * restore, PostgreSQL, a gate. close() stops what EXISTS at that moment and
     * sets {@link #flagClosed}. Until 2026-09-23 nothing on the start thread
     * looked at that flag, so whatever the start went on to launch after the
     * close - PostgreSQL, Canton, Splice, the web UIs - was started by a runner
     * that had already closed, held by nobody, and outlived the window's stop,
     * because the second stop() the failure path makes returns at the
     * idempotency guard. That is one way to leave a postmaster behind with
     * `stop()` called on the failed start.
     *
     * Every component is therefore created under this object's monitor, the
     * same one close() holds, and only after this check. Either close() sees
     * the component and stops it, or the component is never created.
     *
     * @throws StartException 1, when this runner has been stopped
     */
    private void requireOpen() {
        if (flagClosed)
            throw new StartException(1, "LocalNet was stopped while it was starting");
    }


    /**
     * Stops the stack and leaves this runner reusable only in the sense that
     * close() is idempotent - a stopped LocalNetRunner does not start again,
     * because its processes and its probe are gone.
     */
    @Override
    public void stop() {
        // A SECOND stop() LEAVES THE STATE ALONE. close() returns at its
        // idempotency guard, so STOPPING set here would never be cleared and a
        // stopped runner would report itself stopping for good - which the
        // window reads as a stack whose numbering is still in force.
        if (!flagClosed)
            state = State.STOPPING;
        close();
    }


    @Override
    public State state() {
        return state;
    }


    /**
     * @return every page the web UIs serve, off the ports and login names this
     *         stack was started with; empty before they are up or when they are
     *         off
     */
    public List<LocalNetUi.Page> lstPageWeb() {
        return List.copyOf(LocalNetUi.lstPage(lstSiteWeb));
    }


    /**
     * @return the upstream names the same pages also answer under -
     *         `LocalNetUi` says why; empty before the web UIs are up
     */
    public List<LocalNetUi.Page> lstPageAliasWeb() {
        return List.copyOf(LocalNetUi.lstPageAlias(lstSiteWeb));
    }


    @Override
    public boolean isRunning() {
        return state == State.RUNNING;
    }


    /**
     * WHAT THIS SINK CARRIES, AND WHAT IT DOES NOT. Each namespace's own output
     * is redirected to a FILE by launch() - `&lt;ns&gt;.out` beside `&lt;ns&gt;.log` in
     * the run directory - so what arrives here is the component's lifecycle
     * lines, not its log. A window that wants the log tails those files.
     *
     * @param sinkNew called with a component name and one line
     */
    @Override
    public void useComponentSink(BiConsumer<String, String> sinkNew) {
        this.sinkComponent = sinkNew;
    }


    /**
     * @return where the embedded cluster lives, whether or not it is there yet
     */
    public Path dirPgData() {
        return dirRun.resolve(STR_DIR_PG);
    }


    /**
     * WHAT RUNS BETWEEN THE WIPE AND POSTGRESQL, and why a hook is needed at
     * all rather than the caller doing it before `start()`.
     *
     * `flagFresh` deletes the run directory INSIDE start(), and PostgreSQL is
     * started on the next line. So a caller that wants to lay a cluster down
     * has no moment of its own: before start() whatever it wrote is deleted
     * again, and after start() the server is already running on an empty one.
     *
     * Called with {@link #dirPgData}, which does not exist at that point. A
     * prepare that creates it makes the start a RESTORE; one that leaves it
     * absent makes the start a founding, which is what happens when no
     * prepare is registered at all.
     *
     * @param runNew called after the wipe and before PostgreSQL, or null for
     *        none
     */
    public void useClusterPrepare(Consumer<Path> runNew) {
        this.runClusterPrepare = runNew;
    }


    /**
     * @param outLineNew where the milestones go; null restores System.out,
     *        which is what the command line has always had
     */
    @Override
    public void useMilestoneSink(Consumer<String> outLineNew) {
        this.sinkMilestone = outLineNew;
    }


    /**
     * FOUR, IN START ORDER. PostgreSQL first because every node's storage
     * points at it, then the two namespaces, then the pages - which are in
     * process and are the one component whose failure the start survives.
     *
     * @return the component names
     */
    @Override
    public List<String> lstComponent() {
        return List.of(STR_COMP_POSTGRES, STR_NS_CANTON, STR_NS_SPLICE, STR_COMP_WEB);
    }


    /**
     * @param strComponent one of {@link #lstComponent()}
     * @return that component's health, OFF for anything else
     */
    @Override
    public Health healthOf(String strComponent) {
        // A STOPPED RUNNER REPORTS NOTHING RUNNING - his "Lamps are wrong",
        // 2026-09-23. The window keeps the last runner after a Stop, and its
        // lamps went on reading it: PostgreSQL and Splice red because their
        // processes were gone, Web green because the server object was still
        // held. After close() every component is simply off.
        if (flagClosed)
            return Health.OFF;
        if (STR_COMP_POSTGRES.equals(strComponent))
            return postgres == null ? healthAbsent()
                    : healthServing(postgres.isRunning());
        if (STR_COMP_WEB.equals(strComponent))
            return web == null ? healthAbsent() : Health.UP;
        // A NAME THIS STACK DOES NOT HAVE IS OFF, NEVER PENDING. PENDING says
        // "this start WILL bring it up and its turn has not come", which is a
        // promise about something nothing here starts: a PQS lamp on a
        // LocalNet sat blue for the whole start because an unknown name fell
        // through to healthAbsent. `StackService_i.healthOf` already says OFF
        // for a name the stack does not know; this is that, stated.
        if (!lstComponent().contains(strComponent))
            return Health.OFF;
        Process process = mapProcess.get(strComponent);
        return process == null ? healthAbsent() : healthAlive(process.isAlive());
    }


    /**
     * ALIVE IS NOT SERVING HERE, and the type comment says why: every node is
     * `identity.type = manual` and binds nothing until the Splice layer drives
     * it, so a live process during a start means the gate has not answered
     * yet. Green is withheld until the whole start has returned.
     *
     * @param flagAlive whether the component's process or server is up
     * @return the lamp colour, as a health
     */
    /**
     * POSTGRESQL IS SERVING THE MOMENT IT IS RUNNING, and it is the one
     * component here that is - his observation, 2026-09-22.
     *
     * {@link #healthAlive} withholds green until the whole start has returned,
     * and its reason is stated on it: every Canton and Splice node is
     * `identity.type = manual` and binds nothing until the Splice layer drives
     * it, so a live process mid-start is not one that answers. THAT REASON
     * DOES NOT REACH THE DATABASE. The embedded server is not driven by
     * anything later, `isRunning()` is the whole of the question, and the log
     * says `PostgreSQL ready` a second after the start begins - a lamp that
     * stays orange for the next minute after that line is reporting a doubt
     * nothing holds.
     *
     * @param flagAlive whether the server is up
     * @return UP when it is, and otherwise the same distinction the rest draw
     */
    private Health healthServing(boolean flagAlive) {
        if (flagAlive)
            return Health.UP;
        return state == State.STARTING ? Health.STARTING : Health.DOWN;
    }


    private Health healthAlive(boolean flagAlive) {
        if (!flagAlive)
            return state == State.STARTING ? Health.STARTING : Health.DOWN;
        return state == State.RUNNING ? Health.UP : Health.STARTING;
    }


    /**
     * @return PENDING while a start is in flight, OFF otherwise - the same
     *         distinction the Sandbox draws, for the same reason
     */
    private Health healthAbsent() {
        return state == State.STARTING ? Health.PENDING : Health.OFF;
    }


    /**
     * WHERE EVERY MILESTONE GOES. The sink REPLACES System.out rather than
     * teeing it, so a window does not print a second copy into the terminal it
     * was started from.
     *
     * @param strLine one line, already whole
     */
    private void out(String strLine) {
        Consumer<String> sinkHere = sinkMilestone;
        if (sinkHere == null) {
            System.out.println(strLine);
            return;
        }
        sinkHere.accept(strLine);
    }


    /**
     * FOR THE TERMINAL ONLY, and nothing the operator reads in a window.
     *
     * A jar file name and a connection count are worth having when this runs
     * headless and the terminal is the whole of the instrument. In the window
     * they are noise: the version is printed there from what the window knows,
     * and the connection count belongs to the memory work that asked for it -
     * W-18, D-765 - not to the running commentary a person watches.
     *
     * @param strLine one line, already whole
     */
    private void outCli(String strLine) {
        if (sinkMilestone == null)
            System.out.println(strLine);
    }


    /**
     * @param strComponent which component the line belongs to
     * @param strLine one line, already whole
     */
    private void component(String strComponent, String strLine) {
        BiConsumer<String, String> sinkHere = sinkComponent;
        if (sinkHere != null)
            sinkHere.accept(strComponent, strLine);
    }


    /**
     * WHY THE STOP LIVES IN A SHUTDOWN HOOK. Ctrl-C is the only signal a parked
     * process gets, and the JVM does not wait for the main thread once the hooks
     * have run - so a hook that merely releases the latch would let the JVM exit
     * with Canton, Splice and PostgreSQL still running. The hook therefore does
     * the stopping and this is the ONLY hook in the process: SandboxPostgres is
     * constructed with Zonky's own registration turned off, so nothing can
     * overtake the order in close().
     *
     * A FAILED PROBE STILL PARKS, deliberately. The gates are up, so every node
     * exists and can be asked what is wrong with it; tearing the stack down on
     * the way to reporting that would destroy the only thing worth looking at.
     * --once is where a verdict costs the stack.
     *
     * THE VERDICT IS PRINTED HERE, LAST. It is the one line that decides whether
     * to start working or start reading, and the terminal shows the bottom of
     * the output.
     *
     * @param probe the probe that has already run its checks
     */
    private void park(LocalNetProbe probe) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println();
            System.out.println("--- stopping");
            close();
            latch.countDown();
        }, "localnet-stop"));

        printSummary();
        System.out.println();
        probe.printVerdict();
        System.out.println("=== LocalNet is UP. Ctrl-C to stop.");
        System.out.println("    re-probe from another terminal:  ./probe-localnet.sh "
                + STR_HOST + " " + ports.nPortPostgres() + " " + strBundleArg()
                + strProbeFirstArgs());
        latch.await();
    }


    /**
     * THE PROBE IS TOLD THE BLOCK, or it dials the bundle's own numbering -
     * `LocalNetProbe.main`. Nothing is added for a stack on that numbering.
     *
     * @return the `--first` and `--ui-first` arguments, or ""
     */
    private String strProbeFirstArgs() {
        if (ports.nPortFirst() == LocalNetPorts.ofBundle().nPortFirst())
            return "";
        return " --first " + ports.nPortFirst() + " --ui-first "
                + ports.nPortUi(LocalNetPorts.LST_ROLE.get(0));
    }


    /**
     * probe-localnet.sh takes a version OR a bundle path. The version reads
     * better and is only correct when the bundle sits under the Splice root, so
     * anything else prints the path it was actually given.
     *
     * @return what to hand the probe as its third argument
     */
    private String strBundleArg() {
        Path dirSplice = SpliceInstallations.dirRoot();
        Path dirVersion = dirBundle.getParent();
        if ("splice-node".equals(dirBundle.getFileName().toString()) && dirVersion != null
                && dirSplice.equals(dirVersion.getParent()))
            return dirVersion.getFileName().toString();
        return dirBundle.toString();
    }


    /**
     * EVERYTHING NEEDED TO USE THE STACK, printed last, so nobody has to read
     * this source to find a port, a password or a token. Every value is the
     * constant this run actually used; nothing here is typed a second time.
     *
     * The token is printed in full ON PURPOSE. The secret is a literal in a
     * development bundle and the audience is public - see LocalNetToken - so
     * there is nothing here to protect, and pasting one into curl is how a
     * first call gets made.
     */
    private void printSummary() {
        System.out.println();
        System.out.println("=== HOW TO REACH IT ===");

        System.out.println("--- ports on " + STR_HOST);
        for (Map.Entry<String, Integer> entry : ports.mapPortRequired().entrySet()) {
            System.out.println("  " + entry.getValue() + "  " + entry.getKey());
        }
        for (String strRole : ARR_ROLE) {
            System.out.println("  " + ports.nPortJson(strRole)
                    + "  " + strRole + " json ledger-api");
        }

        printWeb();

        System.out.println("--- postgresql");
        System.out.println("  jdbc:postgresql://" + STR_HOST + ":"
                + ports.nPortPostgres() + "/<database>");
        System.out.println("  user postgres   password postgres   (trust auth, any password works)");
        System.out.println("  databases  " + String.join(", ", LocalNetSpec.lstAllDatabases()));

        Map<String, String> mapToken = mapToken();

        System.out.println("--- jwt");
        System.out.println("  HS256");
        System.out.println("  secret: '" + LocalNetToken.STR_SECRET + "'");
        System.out.println("  claims: sub and aud only");
        System.out.println("  no expiry");
        for (Map.Entry<String, String> entry : mapToken.entrySet()) {
            String[] arrClaim = entry.getKey().split(" ", 2);
            System.out.println("  sub: " + arrClaim[0]);
            System.out.println("  aud: " + arrClaim[1]);
        }

        System.out.println("--- one call that works");
        System.out.println("  curl -H \"Authorization: Bearer "
                + mapToken.values().iterator().next() + "\" http://" + STR_HOST + ":"
                + ports.nPortJson(ARR_ROLE[0])
                + "/v2/authenticated-user");
    }


    /**
     * @param strKey an environment name the bundle's env files may carry
     * @param strFallback what was measured out of the 0.7.4 bundle
     * @return what the stager resolved, or the fallback
     */
    private String strEnv(String strKey, String strFallback) {
        try {
            String strValue = stager.mapEnv(STR_NS_SPLICE).get(strKey);
            return strValue == null || strValue.isBlank() ? strFallback : strValue;
        }
        catch (RuntimeException ex) {
            return strFallback;
        }
    }


    /**
     * ONE TOKEN, unless the roles genuinely differ. The subject and the
     * audience come out of the bundle's env files per role, and on 0.7.4 all
     * three resolve to the same pair - so three identical tokens were noise.
     * The map is still built per role, so a bundle that does differ carries
     * every distinct one.
     *
     * @return "&lt;subject&gt; &lt;audience&gt;" to the token minted for it
     */
    private Map<String, String> mapToken() {
        Map<String, String> mapOut = new LinkedHashMap<>();
        for (String strRole : ARR_ROLE) {
            String strEnvRole = strRole.toUpperCase(Locale.ROOT).replace('-', '_');
            String strSubject = strEnv("AUTH_" + strEnvRole + "_VALIDATOR_USER_NAME",
                    LocalNetToken.STR_SUBJECT_LEDGER);
            String strAudience = strEnv("AUTH_" + strEnvRole + "_AUDIENCE",
                    LocalNetToken.STR_AUDIENCE);
            mapOut.putIfAbsent(strSubject + " " + strAudience,
                    LocalNetToken.mint(strSubject, strAudience));
        }
        return mapOut;
    }


    /**
     * EVERYTHING NEEDED TO REACH THE STACK, as data rather than as print.
     *
     * `printSummary` writes these facts to a terminal and a window renders
     * them in a table. Both read `LocalNetSpec`, so neither is a second source
     * of truth; what would be one is a window that hardcoded a port.
     *
     * @return key to value, in the order a reader wants them
     */
    @Override
    public Map<String, String> mapReach() {
        Map<String, String> mapOut = new LinkedHashMap<>();
        mapOut.put("host", STR_HOST);
        mapOut.put("run directory", dirRun.toString());
        for (Map.Entry<String, Integer> entry : ports.mapPortRequired().entrySet()) {
            mapOut.put("port." + entry.getKey(), String.valueOf(entry.getValue()));
        }
        for (String strRole : ARR_ROLE) {
            mapOut.put("port." + strRole + ".json-ledger-api",
                    String.valueOf(ports.nPortJson(strRole)));
        }
        if (web != null) {
            for (LocalNetUi.Page page : LocalNetUi.lstPage(lstSiteWeb)) {
                mapOut.put("ui. " + page.strTitle(), page.strUrl());
            }
        }
        mapOut.put("db.url", "jdbc:postgresql://" + STR_HOST + ":"
                + ports.nPortPostgres()
                + "/<database>");
        mapOut.put("db.user", LocalNetSpec.STR_DB_USER);
        mapOut.put("db.password", LocalNetSpec.STR_DB_PASSWORD);
        mapOut.put("db.names", String.join(", ", LocalNetSpec.lstAllDatabases()));
        mapOut.put("jwt.auth", auth.describe());
        if (auth.isJwks())
            mapOut.put("jwt.jwks", auth.strUrlJwks());
        mapOut.put("jwt.secret", LocalNetToken.STR_SECRET);
        for (Map.Entry<String, String> entry : mapToken().entrySet()) {
            String[] arrClaim = entry.getKey().split(" ", 2);
            mapOut.put("jwt.sub", arrClaim[0]);
            mapOut.put("jwt.aud", arrClaim.length > 1 ? arrClaim[1] : "");
            mapOut.put("jwt.token", entry.getValue());
        }
        return mapOut;
    }


    /**
     * @return where the staged conf, the logs and the cluster live
     */
    public Path dirRun() {
        return dirRun;
    }


    /**
     * @return the numbering this stack was given, which is what it binds
     */
    public LocalNetPorts ports() {
        return ports;
    }


    /**
     * A UI THAT CANNOT BIND DOES NOT TAKE THE STACK DOWN. The pages are the
     * least important thing in this process and the ledger is the most, so a
     * failure here is reported loudly and the run carries on without them.
     */
    private void startWeb() {
        if (!flagUi) {
            out("web UI off");
            return;
        }
        Path dirWebUis = dirBundle.resolve("web-uis");
        if (!Files.isDirectory(dirWebUis)) {
            out("web UI not started - the bundle carries no pages");
            return;
        }

        List<LocalNetUi.Site> lstSite = LocalNetUi.lstSite(STR_HOST, ports, mapPortUi(),
                mapAudience(),
                mapLogin(), auth);
        LocalNetWeb webNew = new LocalNetWeb(STR_HOST, dirWebUis, lstSite,
                dirRun.resolve("web.log"));
        List<String> lstMissing = webNew.lstBundleMissing();
        if (!lstMissing.isEmpty())
            out("--- web ui: MISSING bundles " + String.join(", ", lstMissing));

        StringBuilder bldPort = new StringBuilder();
        for (LocalNetUi.Site site : lstSite) {
            bldPort.append(bldPort.length() == 0 ? "" : ", ").append(site.nPort());
        }
        try {
            // UNDER THE MONITOR close() HOLDS - see requireOpen. A runner
            // closed by now serves no pages; the check after the probe then
            // reports the start as stopped.
            synchronized (this) {
                if (flagClosed)
                    return;
                webNew.start();
                web = webNew;
            }
            component(STR_COMP_WEB, "on " + bldPort);
            lstSiteWeb = lstSite;
            out("Web UI ready");
        }
        catch (RuntimeException ex) {
            out("web UI not started on " + bldPort + ": " + ex.getMessage());
        }
    }


    /**
     * EVERY PAGE IS MEASURED HERE, not just listed. A served page and an empty
     * 404 look identical in this table without it, and the operator finds out
     * in the browser.
     */
    private void printWeb() {
        if (web == null)
            return;
        System.out.println("--- web ui, open in a browser");
        System.out.println("  every page mints its own token in the browser: type the name");
        System.out.println("  below at the login box, no password - see the jwt section");
        List<LocalNetWeb.Check> lstCheck = web.lstCheck();
        int cntPage = 0;
        for (LocalNetUi.Page page : LocalNetUi.lstPage(lstSiteWeb)) {
            System.out.println("  " + strPad(page.strUrl(), 32) + strPad(page.strTitle(), 36)
                    + (page.strLogin() == null ? "no login" : "log in as  " + page.strLogin()));
            if (cntPage < lstCheck.size()) {
                LocalNetWeb.Check check = lstCheck.get(cntPage);
                System.out.println("      " + (check.flagOk() ? "OK     " : "FAULT  ")
                        + check.strNote());
            }
            cntPage++;
        }
        for (String strLine : LocalNetWeb.lstHostAdvice("/etc/hosts")) {
            System.out.println(strLine);
        }
    }


    /**
     * @return role to UI port, from this numbering
     */
    private Map<String, Integer> mapPortUi() {
        return ports.mapPortUi();
    }


    /**
     * @param strValue what to pad
     * @param nWidth the column it sits in
     * @return it, followed by enough spaces to reach the next column
     */
    private String strPad(String strValue, int nWidth) {
        StringBuilder bld = new StringBuilder(strValue);
        while (bld.length() < nWidth) {
            bld.append(' ');
        }
        return bld.toString();
    }


    /**
     * @return role to the name its pages want at the login box
     */
    private Map<String, String> mapLogin() {
        Map<String, String> mapLogin = new LinkedHashMap<>();
        for (String strRole : ARR_ROLE) {
            String strEnvRole = strRole.toUpperCase(Locale.ROOT).replace('-', '_');
            mapLogin.put(strRole,
                    strEnv("AUTH_" + strEnvRole + "_WALLET_ADMIN_USER_NAME", strRole));
        }
        return mapLogin;
    }


    /**
     * @return role to the audience its participant demands, which is what the
     *         browser has to put in the token it mints for itself
     */
    private Map<String, String> mapAudience() {
        Map<String, String> mapAudience = new LinkedHashMap<>();
        for (String strRole : ARR_ROLE) {
            String strEnvRole = strRole.toUpperCase(Locale.ROOT).replace('-', '_');
            mapAudience.put(strRole,
                    strEnv("AUTH_" + strEnvRole + "_AUDIENCE", LocalNetToken.STR_AUDIENCE));
        }
        return mapAudience;
    }


    private int nEnv(String strKey, int nFallback) {
        try {
            return Integer.parseInt(strEnv(strKey, String.valueOf(nFallback)).trim());
        }
        catch (NumberFormatException ex) {
            return nFallback;
        }
    }


    private void startPostgres() {
        SandboxPostgres postgresNew = new SandboxPostgres(ports.nPortPostgres(),
                Duration.ofMinutes(2), dirPgData(),
                Map.of("max_connections", STR_MAX_CONNECTIONS));
        // ASSIGNED BEFORE IT STARTS, under the monitor close() holds - see
        // requireOpen. A close() that lands during the start below finds it
        // here, and `SandboxPostgres.stop` waits on that object's own monitor
        // for the start to finish before stopping what it started.
        synchronized (this) {
            requireOpen();
            postgres = postgresNew;
        }
        postgresNew.start();
        component(STR_COMP_POSTGRES, "listening on " + postgresNew.host()
                + ":" + postgresNew.port());
        for (String strDb : LocalNetSpec.lstAllDatabases()) {
            postgresNew.ensureDatabase(strDb);
        }
        out("PostgreSQL ready");
    }


    /**
     * What the topology actually opened, so the ceiling above stops being a
     * guess. Reported rather than asserted: a number that is merely high is not
     * a defect, and a number close to the ceiling is the thing worth seeing.
     */
    private void reportConnections() {
        String strUrl = "jdbc:postgresql://" + STR_HOST + ":"
                + ports.nPortPostgres() + "/postgres";
        try (Connection conn = DriverManager.getConnection(strUrl, "postgres", "postgres");
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT count(*) AS n"
                        + " FROM pg_stat_activity")) {
            // ONE LINE, NOT FOURTEEN. The per-database breakdown was a
            // measurement for sizing the pool and it belongs to a probe, not
            // to the running commentary a person reads.
            if (rs.next())
                outCli("PostgreSQL connections: " + rs.getInt("n") + " of "
                        + STR_MAX_CONNECTIONS);
        }
        catch (SQLException ex) {
            outCli("PostgreSQL connections: unreadable: " + ex.getMessage());
        }
    }


    private Path stageOne(String strNamespace, List<Path> lstSeed) {
        Path fileConf = stager.stage(strNamespace, lstSeed);
        List<String> lstLeft = stager.lstAbsoluteLeft(fileConf);
        if (lstLeft.isEmpty())
            return fileConf;
        // THE REAL CHECK, and the reason the guess that used to sit at the top
        // of startGates is gone: this reads the STAGED file and names the
        // includes that will actually fail, rather than warning about image
        // files a given bundle may not include at all.
        out("the staged " + strNamespace + " configuration still has paths that"
                + " will not resolve:");
        for (String strLeft : lstLeft) {
            out("  " + strLeft);
        }
        return fileConf;
    }


    /**
     * The canton namespace gets `parameters.conf` alongside its own app.conf,
     * the way the container's entrypoint passes it. `monitoring.conf` is NOT
     * passed: it binds a Prometheus exporter on a fixed port and a second JVM
     * asking for the same one dies before any node starts.
     *
     * @param strNamespace canton or splice
     * @param fileConf that namespace's staged app.conf
     */
    private void launch(String strNamespace, Path fileConf) throws IOException {
        List<String> lstCmd = new ArrayList<>();
        List<Path> lstConf = new ArrayList<>();
        lstConf.add(fileConf);
        if (STR_NS_CANTON.equals(strNamespace)) {
            lstCmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            lstCmd.add("-jar");
            lstCmd.add(image.fileJar().toString());
            lstCmd.add("daemon");
            lstCmd.add("--no-tty");
            lstCmd.add("-c");
            lstCmd.add(fileConf.toString());
            Path fileParam = CantonImage.fileStaged(fileConf.getParent(), "parameters.conf");
            if (Files.isRegularFile(fileParam)) {
                lstCmd.add("-c");
                lstCmd.add(fileParam.toString());
                lstConf.add(fileParam);
            }
        }
        else {
            lstCmd.add(dirBundle.resolve("bin/splice-node").toString());
            lstCmd.add("daemon");
            lstCmd.add("-c");
            lstCmd.add(fileConf.toString());
        }
        for (Map.Entry<String, String> entry : mapOverrides(strNamespace).entrySet()) {
            lstCmd.add("-C");
            lstCmd.add(entry.getKey() + "=" + entry.getValue());
        }
        Path fileLog = dirRun.resolve(strNamespace + ".log");
        lstCmd.add("--log-file-name");
        lstCmd.add(fileLog.toString());
        // RECORDED AT THE CALL SITE, not reconstructed later: the conf list is
        // a fact this method holds and a second derivation of it would drift
        // the day a namespace gains an overlay.
        mapCommand.put(strNamespace, List.copyOf(lstCmd));
        mapFileConf.put(strNamespace, List.copyOf(lstConf));

        ProcessBuilder bld = new ProcessBuilder(lstCmd);
        bld.environment().putAll(stager.mapEnv(strNamespace));
        // AFTER the bundle's own env files, which is the only order that
        // works: the stager reads common.env last and would otherwise put
        // the vendor's UI ports and addresses back.
        bld.environment().putAll(ports.mapEnvOverride(STR_HOST));
        bld.environment().putAll(auth.mapEnvOverride());
        bld.redirectErrorStream(true);
        bld.redirectOutput(dirRun.resolve(strNamespace + ".out").toFile());
        // UNDER THE MONITOR close() HOLDS, and only while this runner is open
        // - see requireOpen. close() walks lstProcess under the same monitor,
        // so it either sees this process or the process is never started.
        Process process;
        synchronized (this) {
            requireOpen();
            process = bld.start();
            lstProcess.add(process);
            mapProcess.put(strNamespace, process);
        }
        Files.writeString(dirRun.resolve(strNamespace + ".cmd"),
                String.join(" ", lstCmd) + System.lineSeparator(), StandardCharsets.UTF_8);
        // NO PER-NAMESPACE LINE. `Starting Splice` above covers both, and
        // the component sink still carries each one for the lamps.
        component(strNamespace, "started");
    }


    /**
     * @param strNamespace canton or splice
     * @return every -C key/value for that process
     */
    private Map<String, String> mapOverrides(String strNamespace) {
        Map<String, String> mapConf = new LinkedHashMap<>();

        // The port is hardcoded to 5432 inside the _storage anchor; the rest of
        // storage comes from DB_SERVER, DB_USER and DB_PASSWORD in the env. An
        // override on the anchor reaches every node that substitutes it -
        // measured 2026-08-26.
        mapConf.put("_storage.config.properties.portNumber",
                String.valueOf(ports.nPortPostgres()));
        mapConf.putAll(LocalNetSpec.mapDatabases(strNamespace));
        if (STR_NS_SPLICE.equals(strNamespace))
            mapConf.putAll(LocalNetSpec.mapHostOverrides(STR_HOST));
        // AFTER the host overrides, so the sequencer's advertised url -
        // which both maps carry - is this numbering's and not the
        // bundle's.
        mapConf.putAll(ports.mapOverride(strNamespace, STR_HOST));
        return mapConf;
    }


    /**
     * @return whether every gate answered before the timeout
     */
    /**
     * WHAT THE WINDOW SPINS ON. `Milestones.isPending` matches the leading
     * `Waiting `, and the window replaces one pending line with the next
     * rather than appending, so the whole wait is one line with dots on it.
     * Naming each endpoint as it answered put eight lines in the pane and
     * left the reader watching a dead one through the two minutes where the
     * last three do not answer.
     */
    private static final String STR_WAIT_NETWORK = "Waiting for the network";


    private boolean awaitGates() throws InterruptedException {
        Map<String, String> mapTarget = mapGates();
        Map<String, Boolean> mapGate = new LinkedHashMap<>();
        for (String strLabel : mapTarget.keySet()) {
            mapGate.put(strLabel, Boolean.FALSE);
        }
        // `Waiting ` IS THE PREFIX THE WINDOW SPINS ON - Milestones.isPending.
        // The count is what changes; the line is replaced, not repeated.
        out(STR_WAIT_NETWORK);

        Instant instEnd = Instant.now().plus(TIMEOUT_GATE);
        int cntUp = 0;
        while (Instant.now().isBefore(instEnd)) {
            boolean flagAll = true;
            for (Map.Entry<String, Boolean> entry : mapGate.entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue()))
                    continue;
                if (probe(mapTarget.get(entry.getKey()))) {
                    entry.setValue(Boolean.TRUE);
                    cntUp++;
                    out(STR_WAIT_NETWORK + " - " + cntUp + " of " + mapGate.size());
                }
                else {
                    flagAll = false;
                }
            }
            if (flagAll)
                return true;
            if (!alive())
                break;
            Thread.sleep(5000L);
        }

        for (Map.Entry<String, Boolean> entry : mapGate.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue()))
                out("  DOWN  " + entry.getKey());
        }
        return false;
    }


    /**
     * Ledger APIs are TCP because a manual-identity participant binds nothing
     * until it is initialised; the validator, scan and sv gates are the readyz
     * endpoints the bundle's own health checks use.
     *
     * KEYED BY A NAME A PERSON READS. The value is what `probe` dials - a
     * `tcp:` port or a url - and it stays out of the log.
     *
     * @return label to target
     */
    private Map<String, String> mapGates() {
        Map<String, String> mapOut = new LinkedHashMap<>();
        for (String strRole : ARR_ROLE) {
            mapOut.put(strRole + " ledger-api", "tcp:" + ports.nPortLedger(strRole));
        }
        mapOut.putAll(ports.mapReadyz(STR_HOST));
        return mapOut;
    }


    private boolean probe(String strGate) {
        if (strGate.startsWith("tcp:")) {
            int nPort = Integer.parseInt(strGate.substring(4));
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(STR_HOST, nPort), 1000);
                return true;
            }
            catch (IOException ex) {
                return false;
            }
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(strGate))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        }
        catch (IOException | InterruptedException ex) {
            return false;
        }
    }


    private boolean alive() {
        for (Process process : lstProcess) {
            if (!process.isAlive()) {
                out("  a process exited early");
                return false;
            }
        }
        return true;
    }


    private void report(boolean flagUp) {
        reportConnections();
        out(flagUp ? "All endpoints answered" : "Not every endpoint answered");
        if (flagUp)
            return;
        for (String strNamespace : new String[] { STR_NS_CANTON, STR_NS_SPLICE }) {
            Path fileLog = dirRun.resolve(strNamespace + ".log");
            out(strNamespace + " log, last lines:");
            try {
                List<String> lstLine = Files.readAllLines(fileLog, StandardCharsets.UTF_8);
                int nFrom = Math.max(0, lstLine.size() - 25);
                for (int cntLine = nFrom; cntLine < lstLine.size(); cntLine++) {
                    out("  " + lstLine.get(cntLine));
                }
            }
            catch (IOException ex) {
                out("  unreadable: " + ex.getMessage());
            }
        }
    }


    private static void deleteTree(Path dirGone) throws IOException {
        try (Stream<Path> strm = Files.walk(dirGone)) {
            for (Path pathGone : strm.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .toList()) {
                Files.deleteIfExists(pathGone);
            }
        }
    }


    @Override
    public synchronized void close() {
        if (flagClosed)
            return;
        flagClosed = true;
        if (web != null)
            web.stop();
        for (Process process : lstProcess) {
            process.destroy();
        }
        for (Process process : lstProcess) {
            try {
                process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            if (process.isAlive())
                process.destroyForcibly();
        }
        removeStatus();
        if (postgres != null)
            postgres.stop();
        state = State.STOPPED;
    }


    /**
     * @return the reach map as the block a terminal prints, empty before a
     *         start
     */
    @Override
    public List<String> lstReachLine() {
        Map<String, String> mapOut = mapReach();
        if (state != State.RUNNING)
            return List.of();

        List<String> lstLine = new ArrayList<>();
        lstLine.add("localnet is up");
        int cntWidth = 0;
        for (String strKey : mapOut.keySet()) {
            cntWidth = Math.max(cntWidth, strKey.length());
        }
        for (Map.Entry<String, String> entry : mapOut.entrySet()) {
            StringBuilder sbPad = new StringBuilder(entry.getKey());
            while (sbPad.length() < cntWidth) {
                sbPad.append(' ');
            }
            lstLine.add("  " + sbPad + "  " + entry.getValue());
        }
        return lstLine;
    }


    /**
     * @return the status file, or null when none is on disk
     */
    @Override
    public Path fileStatus() {
        Path fileOut = dirRun.resolve(STR_FILE_STATUS);
        return Files.isRegularFile(fileOut) ? fileOut : null;
    }


    /**
     * THE TWO JVMS OWN FILES AND THE OTHER TWO DO NOT - D-791. postgres is
     * Zonky's embedded server and the pages are served in this process, so a
     * path for either would name a file nobody writes.
     *
     * @param strComponent one of {@link #lstComponent()}
     * @return that namespace's log, or null
     */
    @Override
    public Path fileLogOf(String strComponent) {
        if (STR_NS_CANTON.equals(strComponent) || STR_NS_SPLICE.equals(strComponent))
            return dirRun.resolve(strComponent + ".log");
        return null;
    }


    /**
     * @param strComponent one of {@link #lstComponent()}
     * @return what that namespace was launched with, empty for a component
     *         this process does not launch
     */
    @Override
    public List<String> lstCommandOf(String strComponent) {
        return mapCommand.getOrDefault(strComponent, List.of());
    }


    /**
     * @param strComponent one of {@link #lstComponent()}
     * @return the staged conf that namespace was launched on, empty otherwise
     */
    @Override
    public List<Path> lstFileConfOf(String strComponent) {
        return mapFileConf.getOrDefault(strComponent, List.of());
    }


    /**
     * THREE, AND THEY ARE KNOWN BEFORE A START, which is where this differs
     * from the Sandbox: the ports are the bundle's, fixed in `LocalNetSpec`
     * from `env/common.env`, not something a run decides. A caller that wants
     * to know whether they ANSWER asks {@link #healthOf} or the status file.
     *
     * @return the sv, app-provider and app-user participants
     */
    @Override
    public List<StackNode> lstNode() {
        List<StackNode> lstOut = new ArrayList<>();
        for (String strRole : ARR_ROLE) {
            lstOut.add(new StackNode(strRole, STR_HOST, ports.nPortLedger(strRole),
                    ports.nPortAdmin(strRole), ports.nPortJson(strRole)));
        }
        return lstOut;
    }


    /**
     * Properties by hand rather than through {@link java.util.Properties},
     * which writes a timestamp comment and reorders the keys - the same reason
     * `ReadyReport.writeTo` does it by hand.
     *
     * @throws IOException when the run directory refuses it
     */
    private void writeStatus() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# raposza localnet - written while the stack runs,"
                + " removed when it stops\n");
        for (Map.Entry<String, String> entry : mapReach().entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        Files.writeString(dirRun.resolve(STR_FILE_STATUS), sb.toString(),
                StandardCharsets.UTF_8);
    }


    /**
     * A status file left behind says a stack is up when it is not, which is
     * worth a swallowed exception rather than one thrown out of a stop.
     */
    private void removeStatus() {
        try {
            Files.deleteIfExists(dirRun.resolve(STR_FILE_STATUS));
        }
        catch (IOException ex) {
            out("--- could not remove " + STR_FILE_STATUS + ": " + ex.getMessage());
        }
    }
}
