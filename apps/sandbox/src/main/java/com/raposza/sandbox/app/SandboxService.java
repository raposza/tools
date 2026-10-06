// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.CantonInstallations;
import com.raposza.canton.install.PqsInstallation;
import com.raposza.canton.install.PqsInstallations;
import com.raposza.canton.dar.DarCatalog;
import com.raposza.canton.process.CantonProcess;
import com.raposza.canton.pqs.ScribeProcess;
import com.raposza.canton.pqs.PqsOAuth;
import com.raposza.canton.pqs.PqsSpec;
import com.raposza.canton.topology.Canton3xBootstrap;
import com.raposza.canton.topology.Canton3xDaemonBootstrap;
import com.raposza.canton.topology.Canton2xConfig;
import com.raposza.canton.topology.Sandbox2xPorts;
import com.raposza.canton.topology.SandboxPorts;
import com.raposza.canton.topology.StorageOverlay;
import com.raposza.runtime.db.PostgresCoordinates;
import com.raposza.runtime.process.ManagedProcess;
import com.raposza.runtime.db.SandboxPostgres;
import com.raposza.runtime.lifecycle.StackService_i;
import com.raposza.sandbox.StackComponent;
import com.raposza.sandbox.Sandbox2xStack;
import com.raposza.canton.topology.AdminTokenOverlay;
import com.raposza.canton.topology.AuthOverlay;
import com.raposza.sandbox.SandboxStack;
import com.raposza.sandbox.topology.SandboxSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import com.raposza.runtime.port.PortGuard;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Starting and stopping one sandbox, with every line it would print handed to
 * a consumer instead.
 *
 * This class is the whole of what {@link SandboxApp} used to do between parsing
 * and waiting. It was extracted rather than copied because the window and the
 * terminal must not be able to disagree about what a set of options starts: a
 * second implementation of this sequence would drift on the first flag either
 * one gained, and the drift would be invisible until a live run.
 *
 * <h2>Two things a caller gets that a terminal does not need</h2>
 *
 * {@link #state()} rather than an exit code, because a window has to show
 * STARTING for the minute the stack takes and cannot represent that as a
 * number; and {@link #stop()} without a shutdown hook, because a window stops a
 * stack while the JVM keeps running. The exit codes are still here - a
 * {@link StartException} carries the one the headless entry point would have
 * returned, so that behaviour is unchanged.
 *
 * <h2>Threading</h2>
 *
 * {@link #start()} blocks for as long as the stack takes and is not safe to
 * call on an event dispatch thread. The consumer is called from whichever
 * thread is inside `start()` or `stop()`, so a GUI consumer has to hop to the
 * EDT itself; that is left to the caller rather than assumed here, because the
 * headless caller must not pay for it.
 *
 * Author Claude/bentzn
 */
public final class SandboxService implements StackService_i {

    private final SandboxOptions options;

    /**
     * WHAT THE START UPLOADED, said in the pane a reader watches and said WHERE
     * IT HAPPENED.
     *
     * The upload runs inside the participant's own bootstrap - on the daemon
     * launcher it is a line in `bootstrap.canton`, on the subcommand it is
     * `--dar` on the command line - so it happens DURING the participant phase
     * and finishes when the stack reports ready. A line printed while the spec
     * was being assembled sat above `Starting postgres`, which said the DARs
     * had been loaded before there was a participant to load them into.
     *
     * `uploading 1 DAR(s)` went to the verbose narration, which is the
     * Participant tab, so the one pane anybody watches during a start could not
     * answer `did the DAR load` either way - and an absent line reads the same
     * whether nothing was selected or nothing was reported.
     */
    private static final String STR_LOADING_DAR = "Loaded DAR '";

    /**
     * And the other half of it. A start with no DARs says so, rather than
     * saying nothing and leaving the reader to decide which kind of silence it
     * was.
     */
    private static final String STR_NO_DARS = "No DARs loaded";

    private final Consumer<String> outLine;

    private volatile State state = State.STOPPED;

    private volatile SandboxStack stack;

    /**
     * What the Ledger API is to verify, or null for an unauthenticated
     * participant.
     *
     * A SETTER rather than a field on SandboxOptions, deliberately. The
     * headless entry point has no mint to point a JWKS url at, so it has
     * nothing to put here, and an option it could not fill would be an
     * option that reads as available and is not.
     */
    private transient AuthOverlay auth;

    /**
     * The pinned admin token. Canton MINTS one every five minutes when
     * nothing sets it, which is not something a second process can be
     * handed and not something a session can be debugged against.
     */
    private transient String strTokenAdmin;

    /**
     * An override for what PQS presents, or null to use the pinned admin token.
     *
     * The admin token is the right answer for every auth mode, because Canton
     * compares it as a fixed string BEFORE any JWT verification - so it works
     * against an HMAC participant and a certificate one without this process
     * holding the key either was configured with. This field stays for a caller
     * that has a better token, which is a headless caller with its own identity
     * provider.
     */
    private transient String strTokenPqs;

    /**
     * What PQS re-mints from, which WINS over {@link #strTokenPqs} wherever
     * both are set.
     *
     * A static token is a lifetime, and below `AuthOverlay.VER_FLOOR_TOKEN_LIFE`
     * the participant caps that lifetime at 240 s, so the stack that
     * needs this most is the one where the alternative stops after four
     * minutes. The token stays beside it because the mock path and the live
     * test mint one directly.
     */
    private transient PqsOAuth oauthPqs;

    /**
     * Where the pinned admin token is written for other processes to read.
     *
     * A FILE, because that is what `daml script --access-token-file` takes and
     * what a curl line can point at. It lands in the work directory, which is
     * wiped and rewritten on every start, so a token never outlives the stack
     * it belonged to.
     */
    public static final String STR_FILE_TOKEN = "admin-token.txt";

    /**
     * The 3.x node names, in the order `StorageOverlay.databaseNames` returns
     * their databases. They are the KEYS the report writes each database under,
     * and they carry no prefix: a consumer walking `db.names` asks for a node,
     * not for whatever the cluster happens to have called its database.
     */
    private static final String[] ARR_NODE_3X =
            { "participant", "sequencer", "sequencer_driver", "mediator" };

    /** 2.x has a domain where 3.x has a sequencer and a mediator. */
    private static final String STR_NODE_DOMAIN = "domain";

    /** The 2.x stack, when a 2.x installation was selected. */
    private volatile Sandbox2xStack stack2x;

    private volatile ReadyReport report;

    private volatile CantonInstallation installation;

    private volatile Path fileStatus;

    private final Path dirWork;

    private volatile BiConsumer<String, String> sinkComponent;

    /**
     * The half-dozen lines a reader watches, separate from `outLine`, which
     * carries everything. A caller that wants only one of the two streams
     * gets only one; the terminal takes both.
     */
    private volatile Milestones milestones = new Milestones(strLine -> {
        // discarded until a caller asks for them
    });


    /**
     * @param optionsNew what to start; never null
     * @param outLineNew where every line goes, or null to discard them
     */
    public SandboxService(SandboxOptions optionsNew, Consumer<String> outLineNew) {
        if (optionsNew == null)
            throw new IllegalArgumentException("options are required");
        Consumer<String> outLineHere = outLineNew;
        if (outLineHere == null) {
            outLineHere = strLine -> {
                // discarded
            };
        }
        this.options = optionsNew;
        this.outLine = outLineHere;
        this.dirWork = optionsNew.dirWork().toAbsolutePath().normalize();
    }


    public SandboxOptions options() {
        return options;
    }


    /**
     * Where each process's own output goes, passed straight to whichever stack
     * this service starts.
     *
     * @param sinkNew called with a {@link com.raposza.sandbox.StackComponent}
     *        name and one line, from a process pump thread; a GUI caller has
     *        to hop to the EDT itself
     */
    @Override
    public void useComponentSink(BiConsumer<String, String> sinkNew) {
        this.sinkComponent = sinkNew;
    }


    /**
     * @param outLineNew where the milestones go, or null to discard them
     */
    @Override
    public void useMilestoneSink(Consumer<String> outLineNew) {
        this.milestones = new Milestones(outLineNew == null ? strLine -> {
            // discarded
        } : outLineNew);
    }


    @Override
    public State state() {
        return state;
    }


    @Override
    public boolean isRunning() {
        return state == State.RUNNING;
    }


    /**
     * @return what the started stack is, or null before a successful start
     */
    public ReadyReport report() {
        return report;
    }


    /**
     * @return the status file written at start, or null
     */
    @Override
    public Path fileStatus() {
        return fileStatus;
    }


    /**
     * @return the Canton this service started, or null
     */
    public CantonInstallation installation() {
        return installation;
    }


    /**
     * @return the running stack, or null
     */
    /**
     * @param authNew what the Ledger API is to verify, or null for none
     * @param strTokenAdminNew the admin token to pin beside it; ignored when
     *        the overlay is null
     */
    public void useAuth(AuthOverlay authNew, String strTokenAdminNew) {
        this.auth = authNew;
        this.strTokenAdmin = strTokenAdminNew;
    }


    /**
     * The token PQS presents.
     *
     * WITHOUT THIS, PQS AGAINST AN AUTHENTICATED PARTICIPANT FAILS, and it
     * fails as `UNAUTHENTICATED` on `PackageService/ListPackages` followed by
     * scribe retrying for ever - a stack that looks like it is starting and
     * never finishes. Only the 3.x arm uses it; the 2.x stack this application
     * starts leaves its Ledger API unauthenticated.
     *
     * @param strTokenNew a token the participant will verify, or null for none
     */
    public void usePqsToken(String strTokenNew) {
        this.strTokenPqs = strTokenNew;
    }


    /**
     * The credentials PQS re-mints from.
     *
     * PREFERRED OVER {@link #usePqsToken} when both are set. scribe asks the
     * endpoint for a token at start-up and again `preemptexpiry` before each
     * one expires, so the run survives a lifetime the participant capped rather
     * than ending at it.
     *
     * @param oauthNew credentials the participant's own mint will honour, or
     *        null to fall back to the static token
     */
    public void usePqsOAuth(PqsOAuth oauthNew) {
        this.oauthPqs = oauthNew;
    }


    /**
     * What any client should present to this participant's Ledger API, or null
     * when nothing checks.
     *
     * <h2>WILDCARD IS NOT AUTHENTICATION, and `auth != null` used to mean it
     * was</h2>
     *
     * This read `auth == null ? null : strTokenAdmin`. That was correct while
     * the window left the overlay unset for an unauthenticated run - PQS then
     * got no token and scribe ran `NoAuth`, which is how it worked. Since the
     * auth row reached the form the window ALWAYS calls {@link #useAuth}, and
     * for `none (wildcard)` it passes {@link AuthOverlay#ofWildcard} rather
     * than null, because a wildcard participant states the empty check rather
     * than omitting it. So `auth` stopped being null on runs where nothing
     * checks, and every one of them started handing scribe a credential.
     *
     * MEASURED: the credential is a UUID with no `sub`, and scribe's
     * first call is `UserManagementService/ListUserRights`, which Canton
     * refuses with `INVALID_TOKEN(8): requests with an empty user-id are only
     * supported if there is an authenticated user`. It gets there only after
     * Flyway has applied all 42 migrations, so the schema appears and the
     * stream never starts.
     *
     * The TYPE is the test and not the presence. A wildcard overlay means the
     * participant checks nothing, which is exactly the case that must send
     * nothing.
     *
     * @return the token, or null when nothing checks
     */
    public String strTokenLedger() {
        if (strTokenPqs != null)
            return strTokenPqs;
        if (auth == null || AuthOverlay.STR_TYPE_WILDCARD.equals(auth.strType()))
            return null;
        return strTokenAdmin;
    }


    /**
     * @return the file holding {@link #strTokenLedger}, or null when there is
     *         no token or it has not been written yet
     */
    public Path fileTokenAdmin() {
        Path fileToken = dirWork.resolve(STR_FILE_TOKEN);
        return Files.isRegularFile(fileToken) ? fileToken : null;
    }


    /**
     * @param strToken what to write
     */
    private void writeTokenFile(String strToken) {
        Path fileToken = dirWork.resolve(STR_FILE_TOKEN);
        try {
            Files.createDirectories(dirWork);
            Files.writeString(fileToken, strToken);
            outLine.accept("ledger api token written to " + fileToken);
        }
        catch (IOException ex) {
            // NOT FATAL. The stack itself does not read this file; only the
            // Scripts tab and whoever is at a shell do, and refusing to start
            // over a file nothing in the participant needs would be the wrong
            // trade.
            outLine.accept("could not write " + fileToken + ": " + ex.getMessage());
        }
    }


    public SandboxStack stack() {
        return stack;
    }


    /**
     * WHICHEVER GENERATION IS UP, as the type both columns share. A surface
     * that wants the command and the overlays needs neither to know which
     * stack class it got nor to hold two code paths to ask the same question.
     *
     * @return the participant's process, or null when nothing is up
     */
    public CantonProcess processParticipant() {
        SandboxStack stackHere = stack;
        if (stackHere != null)
            return stackHere.process();

        Sandbox2xStack stack2xHere = stack2x;
        return stack2xHere == null ? null : stack2xHere.process();
    }


    /**
     * @return the scribe process, or null when PQS is not part of this stack
     */
    public ScribeProcess processPqs() {
        SandboxStack stackHere = stack;
        if (stackHere != null)
            return stackHere.pqs();

        Sandbox2xStack stack2xHere = stack2x;
        return stack2xHere == null ? null : stack2xHere.pqs();
    }


    /**
     * The machine, read once. Discovery walks the whole dpm cache - stat and
     * readdir, file by file - and the window asked for it on EVERY lamp tick,
     * 500 ms apart, on the event thread: A-63, measured 2026-10-04, about one
     * sample in twenty of every stall. Nothing installs a Canton behind this
     * application's back except the install dialogs, and they rescan.
     */
    private static volatile List<CantonInstallation> lstCantonCached;


    /**
     * @return every Canton on this machine, newest first is not promised;
     *         read ONCE and kept until {@link #rescanCanton()}
     */
    public static List<CantonInstallation> lstCanton() {
        List<CantonInstallation> lstHere = lstCantonCached;
        if (lstHere == null) {
            lstHere = List.copyOf(CantonInstallations.ofDefaults().discover());
            lstCantonCached = lstHere;
        }
        return lstHere;
    }


    /**
     * Reads the machine again, for a caller that knows it changed.
     *
     * @return every Canton on this machine, as {@link #lstCanton()}
     */
    public static List<CantonInstallation> rescanCanton() {
        lstCantonCached = null;
        return lstCanton();
    }


    /**
     * @return every scribe on this machine
     */
    public static List<PqsInstallation> lstPqs() {
        return PqsInstallations.ofDefaults().discover();
    }


    /**
     * Resolves, checks the ports, starts PostgreSQL and Canton, and returns
     * once the stack serves.
     *
     * Blocks. Every refusal is a {@link StartException} carrying the headless
     * exit code, so the caller never has to map a message back onto one.
     *
     * @throws StartException when anything refuses or fails
     */
    @Override
    public void start() {
        if (state == State.RUNNING || state == State.STARTING)
            throw new StartException(SandboxApp.N_EXIT_USAGE, "a stack is already up");

        state = State.STARTING;
        try {
            startInner();
            state = State.RUNNING;
        }
        catch (RuntimeException ex) {
            state = State.FAILED;
            throw ex;
        }
    }


    private void startInner() {
        CantonInstallation installationNew = resolveOrRefuse();
        this.installation = installationNew;

        if (installationNew.version().major() == 2) {
            start2x(installationNew);
            return;
        }
        SandboxPorts ports = SandboxPorts.ofDefaultsOffsetBy(options.nPortOffset());

        SandboxSpec spec = new SandboxSpec(ports, List.of(), options.flagStaticTime(),
                options.flagDev(), options.nHeapMb());
        List<Path> lstFileDarHere = List.of();
        if (!options.lstFileDar().isEmpty()) {
            List<Path> lstFileDar;
            try {
                // THE CATALOGUE, not the list as given. It is what rejects two
                // files carrying one package id and what fixes the upload
                // order, and both of those are properties of a set of DARs
                // rather than of the directory they happened to come from.
                lstFileDar = DarCatalog.ofFiles(options.lstFileDar()).lstFiles();
            }
            catch (RuntimeException ex) {
                throw new StartException(SandboxApp.N_EXIT_USAGE,
                        "the DARs were refused: " + ex.getMessage());
            }
            spec = spec.withDars(lstFileDar);
            lstFileDarHere = lstFileDar;
        }

        outLine.accept("starting canton " + installationNew.version() + " "
                + installationNew.edition() + " from " + installationNew.dirHome()
                + " on the " + options.launcher().name().toLowerCase() + " launcher");
        outLine.accept("work directory " + dirWork);
        if (options.isPersistent()) {
            outLine.accept("PERSISTENT cluster at " + options.dirData());
            if (options.isDaemon()) {
                outLine.accept("  the ledger survives this process AND this stack can be"
                        + " started against it again: the same participant, the same party,"
                        + " the same packages.");
            }
            else {
                outLine.accept("  the ledger survives this process. THIS LAUNCHER cannot"
                        + " start against it a second time: Canton's own generated bootstrap"
                        + " re-proposes a topology mapping that already exists and exits."
                        + " Use --launcher daemon for a stack that can.");
            }
        }

        // NO WAIT. Operator decision, 2026-08-22: a port that is held is
        // something else listening, and there is nothing to wait for.
        // `PortWait` waited up to two minutes for the TIME_WAIT sockets a
        // stopped PostgreSQL leaves on its own port, and those never made
        // the port busy in the first
        // place. The guard's own `setReuseAddress(false)` did, and it is
        // fixed. The wait was answering a question nothing asks.
        try {
            ports.requireFree(options.nPortPostgres());
        }
        catch (IllegalStateException ex) {
            throw new StartException(SandboxApp.N_EXIT_PORTS, ex.getMessage());
        }

        SandboxPostgres postgres = new SandboxPostgres(options.nPortPostgres(),
                SandboxPostgres.TIMEOUT_START_DEFAULT, options.dirData());
        // BOTH SIDES FROM ONE OBJECT. The participant's auth-services block
        // and every token the mint issues are rendered from the same plan, so
        // they cannot disagree - which they did once, and it cost three probes
        // and six starts to find.
        AuthOverlay authHere = auth;
        if (authHere != null)
            outLine.accept("ledger api auth: " + authHere.describe());

        SandboxStack stackNew = new SandboxStack(installationNew, spec, dirWork, postgres,
                options.strDbPrefix(), authHere);
        if (authHere != null && strTokenAdmin != null) {
            // NO ACT-AS-ANY-PARTY, and it took a fixture change to get here.
            //
            // It used to be taken, because `Main:setup` called `allocateParty`
            // and then submitted as the party it had just allocated - no ledger
            // user could hold `actAs` for an id that did not exist when the
            // token was minted, so one token acting for anybody was the only
            // way the Scripts tab ran at all.
            //
            // The fixture no longer asks for that. `Main:setupParties`
            // allocates and creates the users under an admin token, and
            // `Main:setupLedger` submits under a token minted for a user
            // that now holds `actAs` on the four. What was a 3.x-only escape
            // hatch is now unnecessary on both lines, which is also why the
            // 2.x column can run authenticated at all.
            //
            // THE SHORTCUT IS STILL AVAILABLE and is still declined: a token
            // that acts for anybody is a shared bearer secret, and a CaQL run
            // measuring `alice cannot see Bob's receipt` must not be sitting on
            // a participant that would have let her.
            stackNew.useAdminToken(AdminTokenOverlay.ofAdmin(strTokenAdmin));
            writeTokenFile(strTokenAdmin);
        }
        stackNew.useLauncher(options.launcher());
        stackNew.useOutputSink(sinkComponent);
        stackNew.usePhaseSink(milestones);

        // Two kinds of script for two launchers, and the stack refuses the
        // wrong one before it starts anything. The DARs are NOT attached here
        // under either: the subcommand renders them as `--dar` and the daemon
        // bootstrap picks them off the same spec.
        if (options.isDaemon())
            stackNew.useBootstrap(bootstrapDaemonFor(options));
        else
            stackNew.useBootstrap(bootstrapFor(options));

        PqsSpec specPqs = pqsFor(options, installationNew);
        // THE MINTED TOKEN WHEN THERE IS ONE, on this line as well as 2.x.
        // `strTokenLedger` prefers it; see that method for why the pinned admin
        // token cannot serve scribe on either line.
        String strTokenLedger = strTokenLedger();
        if (specPqs != null && strTokenLedger != null)
            specPqs = specPqs.withToken(strTokenLedger);
        // AND THE ENDPOINT WHEN THE WINDOW SUPPLIED ONE, which wins: the
        // static token above is then only the proof that the mint answered
        // and signed with the key this participant was configured for.
        if (specPqs != null && oauthPqs != null)
            specPqs = specPqs.withOAuth(oauthPqs);
        if (specPqs != null) {
            stackNew.usePqs(specPqs);
            outLine.accept("pqs: " + specPqs.describe());
            // NO NOTE HERE. What used to print - that PQS had never been run
            // under the daemon launcher and was neither refused nor measured -
            // is a fact about this project's own test coverage, and a person
            // starting a sandbox has no use for it and no way to act on it.
        }

        this.stack = stackNew;
        try {
            stackNew.start(options.timeoutReady());
        }
        catch (RuntimeException ex) {
            // The stack already stopped whatever it had started. The message
            // carries Canton's own log problems and the tail of standard
            // output, which is the whole diagnosis and is not summarised here.
            this.stack = null;
            ReadyReport.removeFrom(dirWork);
            throw new StartException(SandboxApp.N_EXIT_START, ex.getMessage());
        }

        noteDars(lstFileDarHere);
        this.report = buildReport(installationNew, stackNew, ports, specPqs);
        this.fileStatus = report.writeTo(dirWork);
    }


    /**
     * The 2.x arm.
     *
     * A SEPARATE PATH, not a branch inside the 3.x one. The two generations
     * differ in the stack class, the port record, the topology - a domain
     * rather than a sequencer and a mediator - and in what the launcher and
     * bootstrap options even mean. Threading that through one method would put
     * a version test on every second line and would be the module-boundary
     * defect the capability rules name, one layer up.
     *
     * <h2>What 2.x DOES NOT take, said out loud rather than dropped</h2>
     *
     * `--launcher`, `--dev`, `--static-time`, `--party`, `--user` and `--ping`
     * belong to the 3.x launchers and their bootstrap. On this column they are
     * REPORTED as ignored rather than silently discarded: an option that is
     * accepted and dropped is the silently-wrong category measured on
     * scribe's own flag, and it is not going to be reproduced here.
     *
     * @param installationNew the 2.x Canton to start
     */
    private void start2x(CantonInstallation installationNew) {
        Sandbox2xPorts ports = Sandbox2xPorts.ofDefaultsOffsetBy(options.nPortOffset());

        List<Path> lstFileDar = List.of();
        if (!options.lstFileDar().isEmpty()) {
            try {
                lstFileDar = DarCatalog.ofFiles(options.lstFileDar()).lstFiles();
            }
            catch (RuntimeException ex) {
                throw new StartException(SandboxApp.N_EXIT_USAGE,
                        "the DARs were refused: " + ex.getMessage());
            }
        }

        outLine.accept("starting canton " + installationNew.version() + " "
                + installationNew.edition() + " from " + installationNew.dirHome()
                + " - the 2.x stack: participant and DOMAIN, no sequencer/mediator split");
        outLine.accept("work directory " + dirWork);
        AuthOverlay authHere = auth;
        outLine.accept("protocol version " + Canton2xConfig.N_PROTOCOL_VERSION_DEFAULT
                + ", ledger api auth: "
                + (authHere == null ? "none" : authHere.describe()));
        for (String strIgnored : lstIgnoredOn2x()) {
            outLine.accept("  IGNORED on 2.x: " + strIgnored);
        }

        Map<String, Integer> mapPort = new LinkedHashMap<>();
        mapPort.put("ledger-api", ports.nPortLedgerApi());
        mapPort.put("admin-api", ports.nPortAdminApi());
        mapPort.put("json-api", ports.nPortJsonApi());
        mapPort.put("domain-public", ports.nPortDomainPublic());
        mapPort.put("domain-admin", ports.nPortDomainAdmin());
        mapPort.put("postgres", options.nPortPostgres());
        try {
            PortGuard.requireFree(mapPort);
        }
        catch (IllegalStateException ex) {
            throw new StartException(SandboxApp.N_EXIT_PORTS, ex.getMessage());
        }

        SandboxPostgres postgres = new SandboxPostgres(options.nPortPostgres(),
                SandboxPostgres.TIMEOUT_START_DEFAULT, options.dirData());
        // THE OVERLAY IS PASSED. Until 2026-08-19 this argument was a literal
        // null while the 3.x arm three methods up configured its participant
        // from the same field, so the auth row on the form did nothing at all
        // on this column and the run printed UNAUTHENTICATED whatever it was
        // set to. `Sandbox2xStack` has taken an overlay since it was written
        // and `Canton2xConfig` names its participant node after the 3.x one so
        // that one overlay serves both lines; nothing needed adding but this.
        Sandbox2xStack stackNew = new Sandbox2xStack(installationNew, ports, lstFileDar,
                dirWork, postgres, options.strDbPrefix(), authHere, 0, options.nHeapMb());

        PqsSpec specPqs = pqsFor(options, installationNew);
        // THE MINTED TOKEN AND NOT strTokenLedger(). 2.x has no
        // `admin-token-config`, so the admin token this service holds is a
        // UUID the participant has never heard of. What the window minted is
        // the only credential this column has.
        //
        // CORRECTED 2026-08-20: the note here used to say the pinned admin
        // token works on 3.x. It does not. `admin-token-config` makes Canton
        // accept it for ADMIN calls, and scribe's first call is
        // `ListUserRights`, which needs a user-id the UUID does not carry. The
        // 3.x arm mints one too now, and this arm is unchanged.
        String strTokenPqsHere = strTokenPqs;
        if (specPqs != null && strTokenPqsHere != null)
            specPqs = specPqs.withToken(strTokenPqsHere);
        if (specPqs != null && oauthPqs != null)
            specPqs = specPqs.withOAuth(oauthPqs);
        if (specPqs != null) {
            stackNew.usePqs(specPqs);
            outLine.accept("pqs: " + specPqs.describe());
        }

        stackNew.useOutputSink(sinkComponent);
        stackNew.usePhaseSink(milestones);
        this.stack2x = stackNew;
        try {
            stackNew.start(options.timeoutReady());
        }
        catch (RuntimeException ex) {
            this.stack2x = null;
            ReadyReport.removeFrom(dirWork);
            throw new StartException(SandboxApp.N_EXIT_START, ex.getMessage());
        }

        noteDars(lstFileDar);
        ReadyReport reportNew = new ReadyReport(installationNew.version().toString(),
                installationNew.edition().toString(), ports, stackNew.postgres().port(),
                stackNew.postgres().coordinatesFor(
                        Canton2xConfig.databaseName(options.strDbPrefix())),
                dirWork, options.dirData());
        reportNew.with(ReadyReport.KEY_PARTICIPANT_ID, stackNew.participantId());
        // EVERY DATABASE, not only the participant's. A consumer handed over to
        // by the discovery endpoint has to be able to reach the domain's too,
        // and 2.x is a DOMAIN where 3.x is a sequencer and a mediator - so the
        // list is written rather than inferred from a Canton generation.
        reportNew.withDatabase(Canton2xConfig.STR_DATABASE_PARTICIPANT,
                stackNew.postgres().coordinatesFor(
                        Canton2xConfig.databaseName(options.strDbPrefix())));
        reportNew.withDatabase(STR_NODE_DOMAIN, stackNew.postgres().coordinatesFor(
                Canton2xConfig.databaseNameDomain(options.strDbPrefix())));
        if (specPqs != null) {
            reportNew.with(ReadyReport.KEY_PQS, specPqs.describe());
            PostgresCoordinates coordPqs =
                    stackNew.postgres().coordinatesFor(specPqs.databaseName());
            reportNew.with(ReadyReport.KEY_PQS_JDBC, coordPqs.jdbcUrl());
            reportNew.with(ReadyReport.KEY_PQS_USER, coordPqs.strUser());
            reportNew.with(ReadyReport.KEY_PQS_PASSWORD, coordPqs.strPassword());
        }

        this.report = reportNew;
        this.fileStatus = reportNew.writeTo(dirWork);
    }


    /**
     * @return the options that mean nothing on the 2.x column, so the run can
     *         say so instead of dropping them
     */
    private List<String> lstIgnoredOn2x() {
        List<String> lstOut = new ArrayList<>();
        if (options.launcher() != SandboxOptions.LAUNCHER_DEFAULT)
            lstOut.add("--launcher, which selects between the two 3.x launchers");
        if (options.flagDev())
            lstOut.add("--dev");
        if (options.flagStaticTime())
            lstOut.add("--static-time");
        if (options.flagProvisions())
            lstOut.add("--party and --user: the 2.x stack has no bootstrap script here");
        if (options.flagPing())
            lstOut.add("--ping");
        return lstOut;
    }


    /**
     * Stops whatever is up and removes the status file. Safe to call when
     * nothing was ever started, which is what a shutdown hook and a window
     * close both need.
     */
    @Override
    public void stop() {
        Sandbox2xStack stack2xHere = stack2x;
        if (stack2xHere != null) {
            state = State.STOPPING;
            try {
                stack2xHere.stop();
            }
            finally {
                stack2x = null;
                report = null;
                fileStatus = null;
                ReadyReport.removeFrom(dirWork);
                state = State.STOPPED;
            }
            return;
        }

        SandboxStack stackHere = stack;
        if (stackHere == null) {
            state = State.STOPPED;
            return;
        }

        state = State.STOPPING;
        try {
            stackHere.stop();
        }
        finally {
            stack = null;
            report = null;
            fileStatus = null;
            ReadyReport.removeFrom(dirWork);
            state = State.STOPPED;
        }
    }


    private ReadyReport buildReport(CantonInstallation installationHere, SandboxStack stackHere,
            SandboxPorts ports, PqsSpec specPqs) {
        String[] arrDb = StorageOverlay.databaseNames(options.strDbPrefix());
        String strDbParticipant = arrDb[0];
        ReadyReport reportNew = new ReadyReport(installationHere.version().toString(),
                installationHere.edition().toString(), ports, stackHere.postgres().port(),
                stackHere.postgres().coordinatesFor(strDbParticipant), dirWork, options.dirData());

        // EVERY DATABASE, in the order `StorageOverlay` names them, which is
        // the order the nodes are. The names carry the prefix and the NODE
        // names do not, so the key stays `url.jdbc.sequencer` whatever the
        // cluster calls the database.
        for (int cntDb = 0; cntDb < arrDb.length && cntDb < ARR_NODE_3X.length; cntDb++) {
            reportNew.withDatabase(ARR_NODE_3X[cntDb],
                    stackHere.postgres().coordinatesFor(arrDb[cntDb]));
        }
        reportNew.with(ReadyReport.KEY_PARTICIPANT_ID, stackHere.participantId());
        reportNew.with(ReadyReport.KEY_PARTY_ID,
                readIfPresent(dirWork.resolve(Canton3xBootstrap.STR_PARTY_ID_FILE)));
        reportNew.with(ReadyReport.KEY_USER_ID,
                readIfPresent(dirWork.resolve(Canton3xBootstrap.STR_USER_ID_FILE)));
        if (specPqs != null) {
            reportNew.with(ReadyReport.KEY_PQS, specPqs.describe());
            PostgresCoordinates coordPqs =
                    stackHere.postgres().coordinatesFor(specPqs.databaseName());
            reportNew.with(ReadyReport.KEY_PQS_JDBC, coordPqs.jdbcUrl());
            reportNew.with(ReadyReport.KEY_PQS_USER, coordPqs.strUser());
            reportNew.with(ReadyReport.KEY_PQS_PASSWORD, coordPqs.strPassword());
        }
        return reportNew;
    }


    private CantonInstallation resolveOrRefuse() {
        Optional<CantonInstallation> optInstallation = resolve(options);
        if (optInstallation.isEmpty()) {
            throw new StartException(SandboxApp.N_EXIT_NOT_INSTALLED,
                    "no Canton matches " + describeWanted(options));
        }

        CantonInstallation installationNew = optInstallation.get();
        if (!installationNew.hasRuntime()) {
            throw new StartException(SandboxApp.N_EXIT_NOT_INSTALLED,
                    "no runtime jar was found under " + installationNew.dirHome()
                            + ", so there is nothing to launch");
        }
        if (installationNew.version().major() != 2
                && installationNew.version().major() != 3) {
            // 2.x and 3.x take different configurations and different
            // stack classes; anything else takes neither.
            throw new StartException(SandboxApp.N_EXIT_NOT_INSTALLED,
                    "this entry point starts 2.x and 3.x only; "
                            + installationNew.version()
                            + " is neither");
        }
        return installationNew;
    }


    /**
     * Which console script an argument list produces. Package-private on
     * purpose: it is asserted without a Canton, and a live start is a poor
     * place to find out it produced the other one.
     *
     * @param optionsHere what was asked for
     * @return the console script for the subcommand launcher
     */
    static Canton3xBootstrap bootstrapFor(SandboxOptions optionsHere) {
        // Always a script, even with nothing asked for: it writes the marker
        // and the participant id, and the id is the one fact about a running
        // sandbox that cannot be derived from the arguments.
        Canton3xBootstrap bootstrapNew = new Canton3xBootstrap();
        if (optionsHere.flagProvisions())
            bootstrapNew = bootstrapNew.withPartyAndUser(optionsHere.strPartyHint(),
                    optionsHere.strUserId());
        if (optionsHere.flagPing())
            bootstrapNew = bootstrapNew.withPing();
        return bootstrapNew;
    }


    /**
     * The same three questions, answered for the launcher that runs its script
     * inside the node JVM.
     *
     * Its party and user calls are GUARDED where the subcommand's are not,
     * which is what lets a `--data-dir` stack on this launcher start twice.
     * That difference lives in the bootstrap class rather than here.
     *
     * @param optionsHere what was asked for
     * @return the in-JVM script for the daemon launcher
     */
    static Canton3xDaemonBootstrap bootstrapDaemonFor(SandboxOptions optionsHere) {
        Canton3xDaemonBootstrap bootstrapNew = new Canton3xDaemonBootstrap();
        if (optionsHere.flagProvisions())
            bootstrapNew = bootstrapNew.withPartyAndUser(optionsHere.strPartyHint(),
                    optionsHere.strUserId());
        if (optionsHere.flagPing())
            bootstrapNew = bootstrapNew.withPing();
        return bootstrapNew;
    }


    /**
     * @return what PQS to run, or null for none
     */
    private static PqsSpec pqsFor(SandboxOptions optionsHere,
            CantonInstallation installationHere) {
        switch (optionsHere.pqs()) {
            case OFF:
                return null;
            case ON:
            default:
                // The token is attached by the caller through usePqsToken
                // rather than here: this method knows WHAT to run, and whether
                // a credential is needed is a property of how the participant
                // was configured.
                return PqsSpec.resolveFor(installationHere);
        }
    }


    /**
     * Names every DAR the start uploaded, as a MILESTONE, AFTER the participant
     * is up.
     *
     * The verbose narration keeps the full path, because that is the thing to
     * copy into a `psql` or a `daml script` line. The milestone carries the file
     * name alone: it sits in a pane eight lines high beside `Starting
     * participant`, and a column of absolute paths there would push the phases
     * off the top.
     *
     * <b>It reports what was HANDED to the start, not what the ledger
     * accepted.</b> The upload happens inside the participant's own bootstrap,
     * and nothing here reads the package back off the Ledger API afterwards, so
     * a DAR the participant rejects is named by this line and refused in
     * Canton's own log.
     *
     * @param lstFileDar the DARs, in upload order
     */
    private void noteDars(List<Path> lstFileDar) {
        if (lstFileDar.isEmpty()) {
            milestones.note(STR_NO_DARS);
            return;
        }

        for (Path fileDar : lstFileDar) {
            milestones.note(STR_LOADING_DAR + fileDar.getFileName() + "'");
            outLine.accept("  " + fileDar);
        }
    }


    /**
     * Which installation a start would select, decided by the one rule that
     * decides it.
     *
     * PUBLISHED FOR `--fixture`, which has to know the Canton BEFORE the stack
     * starts: the fixture DAR is built for that version and is uploaded during
     * the start, so a second copy of this rule would build for one Canton and
     * run on another.
     *
     * @param optionsHere what was asked for
     * @return the installation, or empty when nothing matches
     */
    public static Optional<CantonInstallation> optInstallFor(SandboxOptions optionsHere) {
        return resolve(optionsHere);
    }


    private static Optional<CantonInstallation> resolve(SandboxOptions optionsHere) {
        CantonInstallations installations = CantonInstallations.ofDefaults();
        if (optionsHere.version() != null)
            return installations.find(optionsHere.version(), optionsHere.edition());
        return installations.newestOfLine(optionsHere.strLine(), optionsHere.edition());
    }


    private static String describeWanted(SandboxOptions optionsHere) {
        String strWhich = optionsHere.version() != null
                ? "canton " + optionsHere.version()
                : "the newest canton on line " + optionsHere.strLine();
        return strWhich + (optionsHere.edition() == null ? "" : " " + optionsHere.edition());
    }


    /**
     * @param strDatabase a database on the embedded server
     * @return where it is and who to be, or null when nothing is running
     */
    public PostgresCoordinates coordinatesFor(String strDatabase) {
        SandboxPostgres postgresHere = postgres();
        return postgresHere == null ? null : postgresHere.coordinatesFor(strDatabase);
    }


    /**
     * Where Canton's real log is.
     *
     * Standard output carries five lines under `--log-file-name`; everything
     * else - every node, every retry, every stack trace - is in this file, and
     * a window that showed only the pump was showing the smaller half.
     *
     * @return the Canton log of whichever stack is up, or null
     */
    public Path fileCantonLog() {
        SandboxStack stackHere = stack;
        if (stackHere != null && stackHere.process() != null)
            return stackHere.process().fileLog();

        Sandbox2xStack stack2xHere = stack2x;
        if (stack2xHere != null && stack2xHere.process() != null)
            return stack2xHere.process().fileLog();
        return null;
    }


    /**
     * THE FOUR ARE FIXED AND IN LAMP ORDER, which is `LampBar`'s own order of
     * construction. A component this configuration does not start is still
     * listed and reports OFF, so the row keeps its width when the options
     * change.
     *
     * @return the component names
     */
    @Override
    public List<String> lstComponent() {
        return List.of(StackComponent.STR_POSTGRES, StackComponent.STR_PARTICIPANT,
                StackComponent.STR_JSON_API, StackComponent.STR_PQS);
    }


    /**
     * The four health calls under one name, which is what a caller holding the
     * interface can use. The four stay public because they are what every
     * existing caller already names, and dispatching here rather than
     * duplicating their bodies keeps one answer per component.
     *
     * @param strComponent one of {@link #lstComponent()}
     * @return that component's health, OFF for anything else
     */
    @Override
    public Health healthOf(String strComponent) {
        if (StackComponent.STR_POSTGRES.equals(strComponent))
            return healthPostgres();
        if (StackComponent.STR_PARTICIPANT.equals(strComponent))
            return healthParticipant();
        if (StackComponent.STR_JSON_API.equals(strComponent))
            return healthJsonApi();
        if (StackComponent.STR_PQS.equals(strComponent))
            return healthPqs();
        return Health.OFF;
    }


    public Health healthPostgres() {
        SandboxPostgres postgresHere = postgres();
        if (postgresHere == null)
            return flagStarting() ? Health.PENDING : Health.OFF;
        return health(postgresHere.isRunning());
    }


    public Health healthParticipant() {
        SandboxStack stackHere = stack;
        if (stackHere != null)
            return healthServing(stackHere.process());

        Sandbox2xStack stack2xHere = stack2x;
        if (stack2xHere != null)
            return healthServing(stack2xHere.process());
        // NEITHER STACK EXISTS YET. On a start that is the window between the
        // ports being checked and the stack being constructed, and every start
        // this application makes has a participant in it.
        return flagStarting() ? Health.PENDING : Health.OFF;
    }


    /**
     * MEASURED from the two stacks rather than assumed from the generation,
     * and it runs the opposite way to the intuition.
     *
     * On 3.x there is no separate JSON API process: the participant serves the
     * HTTP Ledger API itself, started with `--json-api-port` under the
     * subcommand launcher and configured as `http-ledger-api` under the
     * daemon. So it is up exactly when the participant is.
     *
     * On 2.x it IS a separate process - `daml json-api` from the SDK beside
     * the Canton being run - so this is that process's own health, and OFF
     * when no SDK was found to start one from.
     *
     * @return the JSON API's health on 2.x, the participant's on 3.x
     */
    public Health healthJsonApi() {
        Sandbox2xStack stack2xHere = stack2x;
        if (stack2xHere != null) {
            return healthServing(stack2xHere.jsonApi());
        }
        if (stack != null)
            return healthParticipant();
        return flagStarting() ? Health.PENDING : Health.OFF;
    }


    public Health healthPqs() {
        SandboxStack stackHere = stack;
        if (stackHere != null) {
            if (stackHere.specPqs() == null)
                return Health.OFF;
            return healthServing(stackHere.pqs());
        }

        Sandbox2xStack stack2xHere = stack2x;
        if (stack2xHere != null) {
            if (stack2xHere.specPqs() == null)
                return Health.OFF;
            return healthServing(stack2xHere.pqs());
        }
        // NO STACK YET, so the OPTIONS are the only thing that knows whether
        // one is coming. `pqsFor` reads the same field when it decides.
        if (options.pqs() == SandboxOptions.PqsMode.OFF)
            return Health.OFF;
        return flagStarting() ? Health.PENDING : Health.OFF;
    }


    /**
     * @return the embedded server of whichever stack is up, or null
     */
    private SandboxPostgres postgres() {
        SandboxStack stackHere = stack;
        if (stackHere != null)
            return stackHere.postgres();

        Sandbox2xStack stack2xHere = stack2x;
        return stack2xHere == null ? null : stack2xHere.postgres();
    }


    /**
     * A component that is not up is AMBER while the stack is starting and RED
     * afterwards. Both are "not serving"; only the second is a defect, and a
     * lamp that could not tell them apart would be red for the whole minute a
     * normal start takes.
     *
     * @param flagUp whether the component is running
     * @return the lamp colour, as a state
     */
    /**
     * For a component whose PROCESS being alive is not the same as it serving.
     *
     * MEASURED: the participant lamp went green about a second after Start and
     * stayed green through the 21 s the ledger takes to come up, because the
     * OS process is alive from the moment it is spawned. What decides here is
     * the SERVICE state - `start()` returns when the stack serves, and nothing
     * before that is green.
     *
     * @param proc the component's process, or null before it is created
     * @return the lamp colour, as a state
     */
    private Health healthServing(ManagedProcess proc) {
        // BLUE until the component's own start begins, and the process object
        // existing IS that moment - each stack creates one immediately before
        // starting it. Grey afterwards, which is a stack that came up without
        // this component in it: the 2.x JSON API with no SDK beside Canton.
        if (proc == null)
            return flagStarting() ? Health.PENDING : Health.OFF;
        if (!proc.isRunning())
            return state == State.STARTING ? Health.STARTING : Health.DOWN;
        // The PROCESS decides, not the stack. Gating green on the service
        // reaching RUNNING made every component amber until the LAST one was
        // up, so a participant that was demonstrably serving - the JSON API
        // had already been started against it - still read as starting.
        return proc.isReady() ? Health.UP : Health.STARTING;
    }


    /**
     * @return whether a start is in flight, which is what turns `not here yet`
     *         from OFF into PENDING
     */
    private boolean flagStarting() {
        return state == State.STARTING;
    }


    private Health health(boolean flagUp) {
        if (flagUp)
            return Health.UP;
        return state == State.STARTING ? Health.STARTING : Health.DOWN;
    }


    private static String readIfPresent(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file).trim() : null;
        }
        catch (IOException ex) {
            return null;
        }
    }


    /**
     * @return the report's own map, empty before a start
     */
    @Override
    public Map<String, String> mapReach() {
        ReadyReport reportHere = report();
        return reportHere == null ? Map.of() : reportHere.map();
    }


    /**
     * @return the report's own block, empty before a start
     */
    @Override
    public List<String> lstReachLine() {
        ReadyReport reportHere = report();
        return reportHere == null ? List.of() : reportHere.lstLines();
    }


    /**
     * PQS OWNS NO FILE. scribe writes to the output this process pumps and
     * `ScribeProcess` exposes no path for it, which is the same thing
     * `LaunchText` tells the operator; a path invented here would name a file
     * nobody writes.
     *
     * @param strComponent one of {@link #lstComponent()}
     * @return the Canton log for the participant, null for anything else
     */
    @Override
    public Path fileLogOf(String strComponent) {
        if (StackComponent.STR_PARTICIPANT.equals(strComponent))
            return fileCantonLog();
        return null;
    }


    /**
     * @param strComponent one of {@link #lstComponent()}
     * @return that process's command line, empty when it is not up
     */
    @Override
    public List<String> lstCommandOf(String strComponent) {
        if (StackComponent.STR_PARTICIPANT.equals(strComponent)) {
            CantonProcess procHere = processParticipant();
            return procHere == null ? List.of() : procHere.lstCommandStarted();
        }
        if (StackComponent.STR_PQS.equals(strComponent)) {
            ScribeProcess procHere = processPqs();
            return procHere == null ? List.of() : procHere.lstCommandStarted();
        }
        return List.of();
    }


    /**
     * scribe takes no configuration file - every setting of it is on the
     * command line above.
     *
     * @param strComponent one of {@link #lstComponent()}
     * @return the participant's overlays, empty for anything else
     */
    @Override
    public List<Path> lstFileConfOf(String strComponent) {
        if (StackComponent.STR_PARTICIPANT.equals(strComponent)) {
            CantonProcess procHere = processParticipant();
            return procHere == null ? List.of() : procHere.lstFileConf();
        }
        return List.of();
    }


    /**
     * ONE NODE, which is what a Sandbox is. The ports are read from the report
     * rather than recomputed, so a generation that moves a port moves this
     * with it.
     *
     * @return the participant, or empty before a start
     */
    @Override
    public List<StackNode> lstNode() {
        ReadyReport reportHere = report();
        if (reportHere == null)
            return List.of();
        String strHost = reportHere.value(ReadyReport.KEY_HOST);
        return List.of(new StackNode(StackComponent.STR_PARTICIPANT,
                strHost == null || strHost.isBlank() ? "127.0.0.1" : strHost,
                nPortOf(reportHere, ReadyReport.KEY_LEDGER_API),
                nPortOf(reportHere, ReadyReport.KEY_ADMIN_API),
                nPortOf(reportHere, ReadyReport.KEY_JSON_API)));
    }


    /**
     * @param reportHere the report to read
     * @param strKey which port
     * @return the port, or 0 when the report does not carry a number there
     */
    private static int nPortOf(ReadyReport reportHere, String strKey) {
        String strPort = reportHere.value(strKey);
        if (strPort == null || strPort.isBlank())
            return 0;
        try {
            return Integer.parseInt(strPort.trim());
        }
        catch (NumberFormatException ex) {
            return 0;
        }
    }

}
