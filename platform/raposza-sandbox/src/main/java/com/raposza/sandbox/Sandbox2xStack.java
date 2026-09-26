// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.pqs.PqsSpec;
import com.raposza.canton.pqs.ScribeLaunch;
import com.raposza.canton.pqs.ScribeProcess;
import com.raposza.canton.process.Canton2xProcess;
import com.raposza.canton.process.JsonApiProcess;
import com.raposza.canton.topology.AuthOverlay;
import com.raposza.canton.topology.Canton2xConfig;
import com.raposza.canton.topology.Sandbox2xPorts;
import com.raposza.runtime.db.PostgresCoordinates;
import com.raposza.runtime.db.SandboxPostgres;
import com.raposza.runtime.process.ManagedProcess;
import com.raposza.runtime.process.ProcessException;
import com.raposza.runtime.process.ProcessSettle;

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
 * A 2.x sandbox: embedded PostgreSQL underneath, one participant and one
 * embedded domain on top.
 *
 * The counterpart to {@link SandboxStack}, and deliberately a sibling rather
 * than a branch inside it. The two lines share an embedded server and a
 * shutdown order and almost nothing else: 3.x overlays a topology the vendor
 * ships, 2.x generates one, plus a console script, plus - later - a second
 * process for the JSON API. Folding that into one class would have produced a
 * method with a major-version test in it every few lines.
 *
 * Nothing here survives a restart. The embedded server writes to a temporary
 * directory that goes with it, and the domain is on memory storage, so a
 * second run starts from an empty ledger. Stated because the 3.x sibling's
 * javadoc once claimed persistence it did not have.
 *
 * Order matters and is the same as 3.x: ports, then the server, then the
 * database, then the configuration, then Canton. Shutdown reverses it, and
 * PostgreSQL goes last, because taking it away first turns a clean stop into a
 * pile of connection errors that have to be read before they can be dismissed.
 *
 * <h2>PQS</h2>
 *
 * Optional, and owned here when it is asked for: {@link #usePqs(PqsSpec)}
 * before {@link #start()}. It starts after the bootstrap has onboarded the
 * participant and stops before Canton does.
 *
 * On this line it is normally the MOCK. PQS for 2.x needs a Daml Enterprise
 * entitlement this project does not hold, so what a 2.x run measures
 * is the argument vector, the database, the token and the lifecycle, and
 * nothing about scribe. {@link ScribeProcess#isReal()} is how a caller tells
 * the two apart, and it is worth asserting rather than assuming.
 *
 * Author Claude/bentzn
 */
public final class Sandbox2xStack implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Sandbox2xStack.class);

    /** NONE, as on the 3.x sibling. The database is `participant`. */
    public static final String STR_DEFAULT_PREFIX = "";

    private static final Duration TIMEOUT_READY = Duration.ofMinutes(6);

    private static final Duration TIMEOUT_STOP = Duration.ofSeconds(60);

    /** The SDK server is a JVM and a Ledger API client; it is not fast. */
    private static final Duration TIMEOUT_JSON_API = Duration.ofMinutes(2);

    private static final String STR_CONF_FILE = "canton.conf";

    private static final String STR_AUTH_FILE = "auth.conf";

    private static final String STR_BOOTSTRAP_FILE = "bootstrap.canton";

    private static final int CNT_LOG_TAIL = 15;

    /** How long PQS has to still be running after it says it is ready. */
    private static final Duration SETTLE_AFTER_READY = ProcessSettle.SETTLE_DEFAULT;

    private static final int CNT_PROBLEM_FIRST = 40;

    private static final int CNT_PROBLEM_LAST = 10;

    /** What PQS is told to connect to. The participant is in this process tree. */
    private static final String STR_HOST_LEDGER = "localhost";

    private final CantonInstallation installation;
    private final Sandbox2xPorts ports;
    private final List<Path> lstFileDar;
    private final Path dirWork;
    private final String strPrefix;
    private final SandboxPostgres postgres;
    private final AuthOverlay auth;
    private final int nProtocolVersion;
    private final int nHeapMb;

    private PqsSpec specPqs;

    private boolean flagPing;

    private Canton2xProcess process;

    private Canton2xConfig config;

    private ScribeProcess scribe;

    private JsonApiProcess jsonApi;

    private BiConsumer<String, String> sinkOutput;

    private PhaseSink_i sinkPhase;


    /**
     * @param installation the Canton to launch; must be 2.x
     * @param ports what to listen on
     * @param lstFileDar DARs the bootstrap uploads, in order; may be empty
     * @param dirWork where the configuration, the script, the id file and the
     *        log go
     * @param postgres the embedded server, not yet started
     * @param strPrefix prefix for the database name
     * @param auth what to put on the Ledger API, or null for none
     * @param nProtocolVersion the domain's protocol version, or 0 for the
     *        default
     * @param nHeapMb the JVM heap for Canton, or 0 for the JVM default
     */
    public Sandbox2xStack(CantonInstallation installation, Sandbox2xPorts ports,
            List<Path> lstFileDar, Path dirWork, SandboxPostgres postgres, String strPrefix,
            AuthOverlay auth, int nProtocolVersion, int nHeapMb) {
        if (installation == null || ports == null || dirWork == null || postgres == null)
            throw new IllegalArgumentException(
                    "installation, ports, dirWork and postgres are required");
        strPrefix = strPrefix == null ? "" : strPrefix.trim();
        if (nProtocolVersion < 0)
            throw new IllegalArgumentException("protocol version must not be negative: "
                    + nProtocolVersion);

        this.installation = installation;
        this.ports = ports;
        this.lstFileDar = lstFileDar == null ? List.of() : List.copyOf(new ArrayList<>(lstFileDar));
        this.dirWork = dirWork.toAbsolutePath().normalize();
        this.postgres = postgres;
        this.strPrefix = strPrefix;
        this.auth = auth;
        this.nProtocolVersion = nProtocolVersion == 0
                ? Canton2xConfig.N_PROTOCOL_VERSION_DEFAULT
                : nProtocolVersion;
        this.nHeapMb = nHeapMb;
    }


    public Sandbox2xStack(CantonInstallation installation, Sandbox2xPorts ports, Path dirWork) {
        this(installation, ports, List.of(), dirWork, new SandboxPostgres(), STR_DEFAULT_PREFIX,
                null, 0, 0);
    }


    /**
     * Where each process's own output goes. See the 3.x sibling: a process's
     * output never reaches standard output, and a listener attached after the
     * start has already lost the part worth reading.
     *
     * @param sinkNew called with a {@link StackComponent} name and one line,
     *        from the pump thread; null to send nothing
     * @return this stack
     */
    public Sandbox2xStack useOutputSink(BiConsumer<String, String> sinkNew) {
        this.sinkOutput = sinkNew;
        return this;
    }


    /**
     * @param sinkNew told which component is coming up and how long it took,
     *        or null for none
     * @return this
     */
    public Sandbox2xStack usePhaseSink(PhaseSink_i sinkNew) {
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
    private long phaseStarting(String strComponent) {
        PhaseSink_i sinkHere = sinkPhase;
        if (sinkHere != null)
            sinkHere.starting(strComponent);
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


    public Path fileConf() {
        return dirWork.resolve(STR_CONF_FILE);
    }


    public Path fileAuth() {
        return dirWork.resolve(STR_AUTH_FILE);
    }


    public Path fileBootstrap() {
        return dirWork.resolve(STR_BOOTSTRAP_FILE);
    }


    /**
     * @return the rendered topology, or null before {@link #start()}
     */
    public Canton2xConfig config() {
        return config;
    }


    /**
     * @return the running Canton process, or null before {@link #start()}
     */
    public Canton2xProcess process() {
        return process;
    }


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
    public Sandbox2xStack usePqs(PqsSpec specPqsNew) {
        if (isRunning())
            throw new ProcessException("PQS has to be configured before the stack starts");
        this.specPqs = specPqsNew;
        return this;
    }


    /**
     * Asks the bootstrap to ping the participant from itself.
     *
     * MUTATES and returns this, like {@link #usePqs(PqsSpec)}.
     *
     * The ping is what puts a transaction on the ledger before PQS starts. PQS
     * is launched last and with `--pipeline-ledger-start=Oldest`, so data
     * created by the bootstrap is inside the range scribe seeds from - which is
     * why this belongs in the console script rather than in a step after the
     * stack is up.
     *
     * @return this stack
     * @throws ProcessException when the stack is already running
     */
    public Sandbox2xStack usePing() {
        if (isRunning())
            throw new ProcessException("the ping has to be configured before the stack starts");
        this.flagPing = true;
        return this;
    }


    /**
     * @return whether the bootstrap will ping
     */
    public boolean isPinging() {
        return flagPing;
    }


    /**
     * @return where the bootstrap writes the ping round-trip duration; the file
     *         exists only after a ping that returned
     */
    public Path filePing() {
        return dirWork.resolve(Canton2xConfig.STR_PING_FILE);
    }


    /**
     * @return what PQS this stack will run, or null for none
     */
    public PqsSpec specPqs() {
        return specPqs;
    }


    /**
     * @return the running PQS, or null when none was asked for or the stack has
     *         not started; {@link ScribeProcess#isReal()} says whether it
     *         measures scribe or only the wiring, and on 2.x it is normally the
     *         latter
     */
    /**
     * @return the JSON API process, or null when none is running - which is
     *         also what a DPM-sourced 2.x gets, having no SDK beside it
     */
    public JsonApiProcess jsonApi() {
        return jsonApi;
    }


    public ScribeProcess pqs() {
        return scribe;
    }


    public int portLedgerApi() {
        return ports.nPortLedgerApi();
    }


    public int portJsonApi() {
        return ports.nPortJsonApi();
    }


    /**
     * @return the participant id the bootstrap wrote, or null before it has
     */
    public String participantId() {
        Canton2xProcess processHere = process;
        return processHere == null ? null : processHere.participantId();
    }


    public boolean isRunning() {
        return process != null && process.isRunning();
    }


    public void start() {
        start(TIMEOUT_READY);
    }


    /**
     * @param timeout how long to wait for the bootstrap to finish onboarding
     * @throws ProcessException when the stack does not come up
     */
    public void start(Duration timeout) {
        if (isRunning())
            throw new ProcessException("the stack is already running");

        ports.requireFree(postgres.port());

        long nNanoPg = phaseStarting(StackComponent.STR_POSTGRES);
        emit(StackComponent.STR_POSTGRES,
                "starting the embedded server on port " + postgres.port());
        postgres.start();
        emit(StackComponent.STR_POSTGRES,
                "up on " + postgres.host() + ":" + postgres.port());
        phaseStarted(StackComponent.STR_POSTGRES, nNanoPg);
        try {
            PostgresCoordinates pgParticipant =
                    postgres.ensureDatabase(Canton2xConfig.databaseName(strPrefix));
            // THE DOMAIN GETS ONE TOO. It ran on memory, which makes a snapshot
            // of this column unrestorable: the copy is of the PostgreSQL
            // cluster, so it held the participant and not the domain the
            // participant remembers being onboarded to.
            PostgresCoordinates pgDomain =
                    postgres.ensureDatabase(Canton2xConfig.databaseNameDomain(strPrefix));
            config = new Canton2xConfig(Canton2xConfig.STR_NODE_PARTICIPANT,
                    Canton2xConfig.STR_NODE_DOMAIN, pgParticipant, pgDomain, ports,
                    nProtocolVersion, false);

            writeFile(fileConf(), config.renderConf(), "topology");

            List<Path> lstFileConf = new ArrayList<>();
            lstFileConf.add(fileConf());
            if (auth != null) {
                // Separate from the topology for the same reason as on 3.x: a
                // stack that will not start is diagnosed by removing one file
                // at a time, and a combined file cannot be halved.
                writeFile(fileAuth(), auth.render(config.strParticipant()), "auth");
                lstFileConf.add(fileAuth());
            }

            Path fileId = dirWork.resolve(Canton2xConfig.STR_PARTICIPANT_ID_FILE);
            writeFile(fileBootstrap(),
                    config.renderBootstrap(lstFileDar, fileId,
                            flagPing ? filePing() : null),
                    "bootstrap");

            process = new Canton2xProcess(installation, dirWork, lstFileConf, fileBootstrap(),
                    nHeapMb);
            attach(process, StackComponent.STR_PARTICIPANT);
            long nNanoNode = phaseStarting(StackComponent.STR_PARTICIPANT);
            process.start();

            boolean flagReady;
            try {
                flagReady = process.awaitReady(timeout);
            }
            catch (ProcessException ex) {
                throw failure("the sandbox died during start-up");
            }

            if (!flagReady)
                throw failure("the sandbox was not ready within " + timeout);
            if (!process.isRunning())
                throw failure("the sandbox reported ready and then exited");
            phaseStarted(StackComponent.STR_PARTICIPANT, nNanoNode);

            // Last, and inside the same try: PQS connects to a Ledger API that
            // has to be serving already, and a failure here has to take the
            // rest of the stack down with it.
            if (specPqs != null) {
                long nNanoPqs = phaseStarting(StackComponent.STR_PQS);
                startPqs();
                phaseStarted(StackComponent.STR_PQS, nNanoPqs);
            }

            // AFTER the participant, which it is a client of, and after PQS,
            // which is a client of the same Ledger API and no business of
            // this one's.
            long nNanoJson = phaseStarting(StackComponent.STR_JSON_API);
            startJsonApi();
            phaseStarted(StackComponent.STR_JSON_API, nNanoJson);
        }
        catch (RuntimeException ex) {
            stop();
            throw ex;
        }

        log.info("2.x stack ready: participant {}, ledger-api {}, postgres {}, auth {}, pqs {}",
                participantId(), portLedgerApi(), postgres.port(),
                auth == null ? "none" : auth.describe(),
                specPqs == null ? "none" : specPqs.describe());
    }


    /**
     * The database name comes from the scribe binary rather than from this
     * stack's prefix, so two scribe versions on one server never share a
     * schema. The mock gets a name of its own for the same reason.
     */
    /**
     * The HTTP JSON API, which on this line is `daml json-api` from the SDK
     * beside the Canton being run.
     *
     * A MISSING SDK IS NOT A FAILED START. The jar is only there for an
     * assistant install, and a stack that refused to come up because one
     * directory of a vendor bundle was absent would be refusing over something
     * the caller never asked for. It says so on the component's own log and
     * leaves the lamp dark.
     */
    private void startJsonApi() {
        Path fileSdkJar = JsonApiProcess.fileSdkJarFor(installation);
        if (fileSdkJar == null) {
            String strWhy = "no daml-sdk.jar beside " + installation.dirHome()
                    + "; the JSON API is NOT started and port "
                    + ports.nPortJsonApi() + " stays closed";
            log.warn(strWhy);
            emit(StackComponent.STR_JSON_API, strWhy);
            return;
        }

        JsonApiProcess processNew = new JsonApiProcess(fileSdkJar, dirWork, STR_HOST_LEDGER,
                ports.nPortLedgerApi(), ports.nPortJsonApi(), JsonApiProcess.N_HEAP_MB_DEFAULT);
        jsonApi = processNew;
        attach(processNew, StackComponent.STR_JSON_API);
        processNew.start();

        boolean flagReadyJson;
        try {
            flagReadyJson = processNew.awaitReady(TIMEOUT_JSON_API);
        }
        catch (ProcessException ex) {
            throw new ProcessException("the JSON API died during start-up; last output:\n"
                    + String.join("\n", processNew.tail(CNT_LOG_TAIL)), ex);
        }

        if (!flagReadyJson)
            throw new ProcessException("the JSON API was not listening on "
                    + ports.nPortJsonApi() + " within " + TIMEOUT_JSON_API
                    + "; last output:\n" + String.join("\n", processNew.tail(CNT_LOG_TAIL)));
    }


    private void startPqs() {
        PostgresCoordinates pgPqs = postgres.ensureDatabase(specPqs.databaseName());
        log.info("starting PQS: {}", specPqs.describe());

        ScribeProcess scribeNew = ScribeProcess.of(specPqs, pgPqs, STR_HOST_LEDGER,
                portLedgerApi(), dirWork, ports.nPortPqsHealth());
        scribe = scribeNew;
        attach(scribeNew, StackComponent.STR_PQS);
        ScribeLaunch.startAndSettle(scribeNew, specPqs.timeoutReady(), SETTLE_AFTER_READY,
                CNT_LOG_TAIL);
    }


    /**
     * PQS first, then Canton, then the server both were connected to. A client
     * left connected to a server that is going away reports the shutdown as a
     * fault, and a clean stop then has to be read past several of those.
     */
    public void stop() {
        ScribeProcess scribeHere = scribe;
        if (scribeHere != null) {
            scribeHere.stop(TIMEOUT_STOP);
            scribe = null;
        }

        // With PQS and before Canton, for the same reason: a client left
        // connected to a server that is going away reports the shutdown as a
        // fault, and a clean stop then has to be read past several of those.
        JsonApiProcess jsonApiHere = jsonApi;
        if (jsonApiHere != null) {
            jsonApiHere.stop(TIMEOUT_STOP);
            jsonApi = null;
        }

        Canton2xProcess processHere = process;
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


    /**
     * Canton's log first, because that is where a node says why it stopped.
     * Standard output second, because on a bad start it is often a thread dump
     * that says nothing about the cause and pushes it out of view.
     */
    private ProcessException failure(String strWhat) {
        Canton2xProcess processHere = process;
        if (processHere == null)
            return new ProcessException(strWhat);

        Integer nExit = processHere.exitCode();
        return new ProcessException(strWhat
                + " (exit code " + (nExit == null ? "none, still running" : nExit) + ")"
                + "\n--- problems in " + processHere.fileLog() + " ---\n"
                + String.join("\n", processHere.logProblems(CNT_PROBLEM_FIRST, CNT_PROBLEM_LAST))
                + "\n--- " + processHere.fileLog() + " (last " + CNT_LOG_TAIL + " lines, raw) ---\n"
                + String.join("\n", processHere.logTail(CNT_LOG_TAIL)));
    }


    private void writeFile(Path file, String strContent, String strWhat) {
        try {
            if (!Files.isDirectory(dirWork))
                Files.createDirectories(dirWork);
            Files.writeString(file, strContent, StandardCharsets.UTF_8);
        }
        catch (IOException ex) {
            throw new ProcessException("could not write " + file, ex);
        }
        log.info("{} written to {}", strWhat, file);
    }
}
