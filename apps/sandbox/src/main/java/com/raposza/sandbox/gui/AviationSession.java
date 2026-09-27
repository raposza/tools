// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.DpmBundles;
import com.raposza.canton.install.ToolchainRoots;
import com.raposza.canton.install.VersionId;
import com.raposza.canton.topology.StorageOverlay;
import com.raposza.runtime.settings.RaposzaSettings;
import com.raposza.sandbox.app.AuthSettings;
import com.raposza.sandbox.app.JwtMintProcess;
import com.raposza.sandbox.app.DamlScriptRun;
import com.raposza.sandbox.app.DamlScriptSpec;
import com.raposza.sandbox.app.AviationFixture;
import com.raposza.sandbox.app.AviationOffer;
import com.raposza.sandbox.app.AviationRun;

import java.awt.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

/**
 * The Aviation fixture as the window uses it: the question, the build, the two
 * script runs afterwards, and the removal at close.
 *
 * <h2>THE QUESTION IS ASKED AT OPEN, NOT AT START</h2>
 *
 * A build is a compiler and takes minutes; asking at Start would put that
 * inside the start path, where the reader is waiting for a participant.
 * Asking at open means the DAR is staged and ticked long before Start is
 * pressed, and it then reaches the ledger by the ordinary upload with nothing
 * special about it.
 *
 * The cost, and it is real: a developer who answers and THEN selects a
 * different Canton is not asked again for that version until the window is
 * opened again. The alternative was worse.
 *
 * <h2>NOTHING OF THE FIXTURE OUTLIVES THE WINDOW</h2>
 *
 * {@link #discard} removes the staged project, the DAR the compiler wrote and
 * the copy in the run directory. The window calls it at close unless a
 * snapshot was taken, in which case the saved cluster holds contracts whose
 * packages that DAR declares and the two have to survive together.
 *
 * <h2>The dialog goes through {@link Modals}</h2>
 *
 * An unattended run has nobody to answer it. Suppressed, `idxOption` returns
 * -1, which is not `Yes` and not `Never` - so the run aborts on Modals' own
 * terms rather than hanging on a question about test data.
 *
 * <h2>Nothing here touches the event thread except where it says so</h2>
 *
 * {@link #offer} and the callbacks run on it. The build and the script runs
 * are threads of their own, and everything they have to say goes to the
 * window's log.
 *
 * Author Claude/bentzn
 */
public final class AviationSession {

    /** THE WHOLE DIALOG until it is expanded. One line. */
    public static final String STR_QUESTION = "Install Aviation test fixture?";

    /**
     * The same question where the fixture is already on disk. It is a different
     * act - a copy rather than a compiler - and a reader who is told `install`
     * about something that is already installed cannot tell what Yes will do.
     */
    public static final String STR_QUESTION_BUILT =
            "Use the Aviation test fixture built earlier?";

    /** What the link reveals, which is everything the question used to say. */
    public static final String STR_DETAIL =
            "A ledger that has just been bootstrapped holds nothing, so there is\n"
            + "nothing to look at and no way to tell a browser that works from one\n"
            + "that does not. This puts a small, known set of contracts on it.\n\n"
            + "WHAT IT IS. An aircraft operator's maintenance records: three\n"
            + "airframes, the components fitted to them, the defects found on them\n"
            + "and everything done about those defects. Seven parties hold the\n"
            + "authorities inside that one organisation - MaintenanceControl,\n"
            + "Technician, Inspector, Engineering, Quality, ConfigurationControl and\n"
            + "ReleaseAuthority - so signatories, observers and controllers mean\n"
            + "something on a single participant.\n\n"
            + "TWO STORIES. Story A is the troublesome aircraft, MSN-4711: a\n"
            + "scheduled inspection finds a hydraulic defect, DEF-104, which is\n"
            + "investigated, found nothing, deferred, recurs, has a transducer\n"
            + "replaced, fails its test, gets a wiring repair, passes an independent\n"
            + "inspection and is closed. Beside it DEF-091 closes in four steps.\n"
            + "Both end Closed; their histories are nothing alike. Story B is the\n"
            + "travelling engine, SN-88321: removed from MSN-5822, through a shop\n"
            + "visit and a certificate, and fitted to MSN-4711 - so the two stories\n"
            + "meet, and the same history reads differently from each end. A third\n"
            + "airframe, MSN-6190, is grounded with a defect still open.\n\n"
            + "TEMPLATES. Aircraft, Configuration, ComponentInstallation and\n"
            + "SerializedComponent carry the current state and are consumed and\n"
            + "recreated as it moves, so each has one active contract and a trail\n"
            + "behind it. MaintenanceEvent, Task and Defect are the work and its\n"
            + "state. Inspection, Investigation, EngineeringDecision,\n"
            + "CorrectiveAction, OperationalTest, TaskRecord, ComponentRemoval,\n"
            + "ShopVisitRecord, Certification and Release are facts and are never\n"
            + "consumed. About 35 contracts are active and as many are archived.\n\n"
            + "WHAT IT DEMONSTRATES. Nested data - records inside lists inside\n"
            + "records, optionals, variants with different payloads - chosen so\n"
            + "that WHERE has known answers: the cycle counts sit at 11999, 12000\n"
            + "and 12001. Only the Technician can perform maintenance, only the\n"
            + "Inspector can sign the independent inspection, only Engineering can\n"
            + "record a disposition, and only ReleaseAuthority can release the\n"
            + "aircraft. Each party sees a different part of the ledger.\n\n"
            + "IT IS REMOVED WHEN THE SANDBOX IS CLOSED, unless a snapshot was taken.";

    /** The link that opens {@link #STR_DETAIL}. */
    public static final String STR_EXPAND = "Expand";

    public static final String STR_YES = "Yes";

    public static final String STR_NOT_NOW = "Not now";

    public static final String STR_NEVER = "Never";

    /** In the order the buttons appear. */
    public static final Object[] ARR_OPTION = { STR_YES, STR_NOT_NOW, STR_NEVER };

    public static final int IDX_YES = 0;

    public static final int IDX_NEVER = 2;

    /**
     * What every progress line of the run begins with.
     *
     * IT GOES TO THE FOOTER AND NOT THE PANE - operator instruction,
     * 2026-09-09. The pane is the record of what a run did and a step of a
     * script is not one; the footer is where somebody watching a start is
     * looking.
     *
     * `Creating ` IS THE SPINNING PREFIX - {@link
     * com.raposza.sandbox.app.Milestones#isPending}. A run that said one line
     * and then went quiet for a minute of party allocation was
     * indistinguishable from a window that had stopped.
     */
    public static final String STR_PHASE = "Creating the Aviation test data";

    /** The same wait the PQS token gets: a Spring Boot start, not a request. */
    public static final Duration TIMEOUT_MINT = Duration.ofMinutes(3);

    /** How much of a failed step's output is worth showing. */
    public static final int N_TAIL = 12;

    private final transient Consumer<String> say;

    /** The same pane, for a line that stays open with a spinner on it. */
    private final transient Consumer<String> sayBusy;

    /** Where a step of the run goes; always told ON THE EVENT THREAD. */
    private final transient Consumer<String> sayPhase;

    /**
     * Told when this session has written a setting the Settings tab is
     * showing, so the tab re-reads the file.
     *
     * PHARMA HAD THIS AND AVIATION DID NOT - `todo.md` A-36. `Never` wrote the
     * setting, the log said the offer was off, and the checkbox stayed ticked
     * until the window was reopened: a pane disagreeing with the file it is a
     * view of.
     */
    private transient Runnable runSettings;

    /** What the build produced, or null until it has. */
    private volatile Path fileDarStaged;

    /** The project the scripts are run from, which is where the DAR was built. */
    private volatile Path dirProject;

    /**
     * Whether the scripts have been handed to a stack in this window.
     *
     * SET WHEN THE RUN STARTS, not when it succeeds. A failed run has already
     * allocated whatever it allocated before it failed, so a second attempt
     * against the same ledger is not a retry - it is the same collision one
     * phase earlier.
     */
    private volatile boolean flagRan;


    /**
     * @param sayNew where every line goes; never null
     * @param sayBusyNew where a line goes that should carry a spinner until the
     *        next one arrives; never null
     * @param sayPhaseNew where a step of the run goes, on the event thread;
     *        never null
     */
    public AviationSession(Consumer<String> sayNew, Consumer<String> sayBusyNew,
            Consumer<String> sayPhaseNew) {
        if (sayNew == null || sayBusyNew == null || sayPhaseNew == null)
            throw new IllegalArgumentException("all three line sinks are required");
        this.say = sayNew;
        this.sayBusy = sayBusyNew;
        this.sayPhase = sayPhaseNew;
    }


    /**
     * Swallows a tool's output, keeping the last {@link #N_TAIL} lines.
     *
     * <h2>THE MAIN TAB IS A SUMMARY, NOT A TRANSCRIPT</h2>
     *
     * The compiler and the script runner between them write about forty lines,
     * of which four matter and none matters while it is going right. They are
     * kept rather than dropped, and printed only when the step fails - which is
     * the one time somebody wants them and the one time they are short enough
     * to read.
     */
    private static final class Tail implements Consumer<String> {

        private final transient List<String> lstLine = new ArrayList<>();


        @Override
        public void accept(String strLine) {
            if (strLine == null || strLine.isBlank())
                return;
            lstLine.add(strLine);
            if (lstLine.size() > N_TAIL)
                lstLine.remove(0);
        }


        /**
         * @param say where the lines go
         */
        void drainTo(Consumer<String> say) {
            for (String strLine : lstLine) {
                say.accept(strLine);
            }
            lstLine.clear();
        }
    }


    /**
     * @return whether a DAR was built and staged in this window
     */
    public boolean isStaged() {
        return fileDarStaged != null;
    }


    /**
     * @return whether {@link #run} has been called since the last build
     */
    public boolean hasRun() {
        return flagRan;
    }


    /**
     * @return the staged DAR, or null when there is none
     */
    public Path fileStaged() {
        return fileDarStaged;
    }


    /**
     * Asks, and on `Yes` starts the build. ON THE EVENT THREAD.
     *
     * @param owner the dialog's parent, or null
     * @param inst which Canton is selected, or null when none is
     * @param dirDars the run directory's `dars`, where the DAR is staged
     * @param runBegun told, on the event thread, the moment a build starts
     * @param runTicked told, on the event thread, once the DAR is staged
     * @param runFailed told, on the event thread, when a build that began
     *        produced nothing; may be null
     */
    public void offer(Component owner, CantonInstallation inst, Path dirDars,
            Runnable runBegun, Runnable runTicked, Runnable runFailed) {
        if (inst == null || dirDars == null)
            return;

        String strKey = VersionKey.strOf(inst);
        String strSdk = AviationFixture.strSdkFor(inst.version().toString(),
                DpmBundles.mapBundle(DpmBundles.dirSdk(ToolchainRoots.ofDefaults())));
        Path dirRoot = AviationFixture.dirRootDefault();
        Path dirProjectHere = AviationFixture.dirProject(dirRoot, strKey);
        boolean flagBuilt = Files.isRegularFile(AviationFixture.fileDar(dirProjectHere));

        String strWhyNot = AviationOffer.strWhyNot(
                RaposzaSettings.current().flagOfferAviation(), strSdk, flagBuilt);
        if (strWhyNot != null) {
            // ONLY THE MACHINE GAP IS WORTH A LINE. `switched off` is a state
            // the reader chose and can see, and saying it on every open is a
            // line of noise per session; a toolchain that cannot build is not.
            //
            // AND NOTHING IS STAGED HERE. A DAR from an earlier session used to
            // be copied into the run directory and ticked at this point, so the
            // next start uploaded test data nobody had agreed to - including on
            // the path where the answer had been `Never`.
            if (AviationOffer.STR_WHY_TOOLCHAIN.equals(strWhyNot))
                say.accept("Aviation test data not offered: " + strWhyNot);
            return;
        }

        int idx = Modals.idxOptionExpandable(owner,
                flagBuilt ? STR_QUESTION_BUILT : STR_QUESTION, STR_DETAIL, STR_EXPAND,
                ARR_OPTION, STR_YES);
        if (idx == IDX_NEVER) {
            rememberNever();
            return;
        }
        if (idx != IDX_YES)
            return;

        // WHAT IS THERE IS STAGED RATHER THAN REBUILT. The DAR on disk is the
        // same artefact and the build is minutes of compiler.
        if (flagBuilt) {
            stageBuilt(dirProjectHere, dirDars, runTicked);
            return;
        }
        build(dirProjectHere, strSdk, dirDars, runBegun, runTicked, runFailed);
    }


    /**
     * Builds the fixture because the operator asked for it on the Settings tab,
     * whatever they answered at open.
     *
     * NO DIALOG AND NO SETTING. The button IS the answer, and a machine that
     * cannot build is still told why. It REBUILDS rather than reusing what is
     * on disk: the button exists to run the thing, and a run needs the fixture
     * to be fresh - see `SandboxWindow.aviationStaged`.
     *
     * @param inst which Canton is selected, or null when none is
     * @param dirDars the run directory's `dars`
     * @param runBegun told, on the event thread, the moment a build starts
     * @param runTicked told, on the event thread, once the DAR is staged
     * @param runFailed told, on the event thread, when a build that began
     *        produced nothing; may be null
     */
    public void buildNow(CantonInstallation inst, Path dirDars, Runnable runBegun,
            Runnable runTicked, Runnable runFailed) {
        if (inst == null || dirDars == null)
            return;

        String strSdk = AviationFixture.strSdkFor(inst.version().toString(),
                DpmBundles.mapBundle(DpmBundles.dirSdk(ToolchainRoots.ofDefaults())));
        if (strSdk == null) {
            say.accept("Aviation test data cannot be built: " + AviationOffer.STR_WHY_TOOLCHAIN);
            return;
        }

        build(AviationFixture.dirProject(AviationFixture.dirRootDefault(), VersionKey.strOf(inst)),
                strSdk, dirDars, runBegun, runTicked, runFailed);
    }


    /**
     * Runs the fixture against a stack that is up. NOT ON THE EVENT THREAD -
     * this starts a thread of its own.
     *
     * @param auth what the participant checks
     * @param version the Canton that is running, which caps a token's lifetime
     * @param nPortLedger the Ledger API port the stack reported
     * @param sinkDone told, on the event thread, whether every phase exited
     *        zero; may be null
     */
    public void run(AuthSettings auth, VersionId version, int nPortLedger,
            java.util.function.Consumer<Boolean> sinkDone) {
        Path fileDar = fileDarStaged;
        Path dirProjectHere = dirProject;
        if (fileDar == null || dirProjectHere == null)
            return;

        this.flagRan = true;

        Thread threadRun = new Thread(() -> {
            boolean flagOk = false;
            try {
                flagOk = flagRun(auth, version, nPortLedger, dirProjectHere, fileDar);
            }
            catch (IOException | RuntimeException ex) {
                say.accept("Aviation test data failed: " + ex.getMessage());
            }
            final boolean flagSaid = flagOk;
            SwingUtilities.invokeLater(() -> {
                if (sinkDone != null)
                    sinkDone.accept(Boolean.valueOf(flagSaid));
            });
        }, "raposza-aviation-run");
        threadRun.setDaemon(true);
        threadRun.start();
    }


    /**
     * Takes the fixture off disk: the staged project with the DAR under it,
     * and the copy in the run directory.
     *
     * ON THE EVENT THREAD, at close. It is a handful of files and a directory
     * tree of a few hundred kilobytes; a thread for it would outlive the
     * window that started it.
     *
     * @param inst which Canton is selected, or null - used only when this
     *        window never staged anything and there is still an earlier
     *        session's project to remove
     */
    public void discard(CantonInstallation inst) {
        Path dirHere = dirProject;
        if (dirHere == null && inst != null) {
            dirHere = AviationFixture.dirProject(AviationFixture.dirRootDefault(),
                    VersionKey.strOf(inst));
        }

        deleteQuietly(fileDarStaged);
        this.fileDarStaged = null;
        this.dirProject = null;
        this.flagRan = false;
        if (dirHere == null)
            return;

        try {
            AviationFixture.deleteProject(dirHere);
            deleteIfEmpty(AviationFixture.dirRootDefault());
        }
        catch (IOException | RuntimeException ex) {
            // THE WINDOW IS CLOSING. There is no pane left to read a line in,
            // and a fixture that could not be removed is a directory the next
            // build overwrites anyway.
        }
    }


    /**
     * @param dirProjectHere where to stage and build
     * @param strSdk what `daml.yaml` will name
     * @param dirDars where the DAR ends up
     * @param runBegun told the moment the build starts
     * @param runTicked told on the event thread when it is there
     * @param runFailed told on the event thread when it is not
     */
    private void build(Path dirProjectHere, String strSdk, Path dirDars, Runnable runBegun,
            Runnable runTicked, Runnable runFailed) {
        // A REBUILD IS A NEW FIXTURE. It is asked for to put the data onto a
        // ledger that has not got it, so whatever an earlier build did to an
        // earlier ledger does not gate this one.
        this.flagRan = false;
        if (runBegun != null)
            runBegun.run();
        // SPINNING, because the compiler says nothing for a minute and a line
        // that just sits there is indistinguishable from a window that has
        // stopped.
        sayBusy.accept("Building the Aviation test data. This takes a minute");

        Thread threadBuild = new Thread(() -> {
            Tail tail = new Tail();
            try {
                AviationFixture.stage(dirProjectHere, strSdk);
                int nExit = AviationRun.nBuild(dirProjectHere, strSdk, tail);
                if (nExit != 0) {
                    say.accept("the Aviation build exited " + nExit + "; no test data was staged");
                    tail.drainTo(say);
                    ended(runFailed);
                    return;
                }
                say.accept("Aviation test data built.");
                SwingUtilities.invokeLater(
                        () -> stageBuilt(dirProjectHere, dirDars, runTicked));
            }
            catch (IOException | RuntimeException ex) {
                say.accept("the Aviation build failed: " + ex.getMessage());
                ended(runFailed);
            }
        }, "raposza-aviation-build");
        threadBuild.setDaemon(true);
        threadBuild.start();
    }


    /**
     * Puts a DAR that exists where the next start uploads from. ON THE EVENT
     * THREAD, because it ends in a table.
     *
     * @param dirProjectHere the project the DAR was built in
     * @param dirDars the run directory's `dars`
     * @param runTicked told once it is there
     */
    private void stageBuilt(Path dirProjectHere, Path dirDars, Runnable runTicked) {
        Path fileBuilt = AviationFixture.fileDar(dirProjectHere);
        try {
            Path fileOut = AviationRun.filePlace(fileBuilt, dirDars, new Tail());
            this.dirProject = dirProjectHere;
            this.fileDarStaged = fileOut;
            if (runTicked != null)
                runTicked.run();
        }
        catch (IOException | RuntimeException ex) {
            say.accept("the Aviation test data could not be staged: " + ex.getMessage());
        }
    }


    /**
     * @param auth what the participant checks
     * @param version the Canton that is running
     * @param nPortLedger the Ledger API port
     * @param dirProjectHere the project whose `daml.yaml` selects the SDK
     * @param fileDar the DAR the scripts live in
     * @return whether every phase exited zero
     * @throws IOException when a token cannot be written
     */
    private boolean flagRun(AuthSettings auth, VersionId version, int nPortLedger,
            Path dirProjectHere, Path fileDar) throws IOException {
        boolean flagAuth = auth != null && auth.mode() != AuthSettings.Mode.NONE;
        Path fileTokenAdmin = null;
        Path fileTokenSuper = null;
        Path fileParties = null;

        try {
            if (flagAuth) {
                phase("minting the tokens the scripts submit under");
                fileTokenAdmin = fileToken(auth, version, AviationRun.STR_USER_ADMIN);
                // PHASE TWO'S USER DOES NOT EXIST YET, and the token for it is
                // minted anyway: the mint signs what it is asked for, and the
                // participant reads the rights when the submission arrives -
                // by which time phase one has created the user.
                fileTokenSuper = fileToken(auth, version, AviationRun.STR_USER_SUPER);
                fileParties = Files.createTempFile("aviation-parties", ".json");
            }

            List<DamlScriptSpec> lstSpec = AviationRun.lstSpec(dirProjectHere, fileDar,
                    nPortLedger, flagAuth, fileTokenAdmin, fileTokenSuper, fileParties);

            int cntStep = lstSpec.size();
            int idxStep = 0;
            for (DamlScriptSpec spec : lstSpec) {
                idxStep++;
                // WHAT THE SCRIPT IS DOING, not what it is called. A reader
                // watching a start has no reason to know that `Main:setupParties`
                // is where the five roles come from.
                phase(strStepOf(spec.strName())
                        + (cntStep > 1 ? " (" + idxStep + " of " + cntStep + ")" : ""));
                Tail tail = new Tail();
                int nExit = new DamlScriptRun().nRun(spec, tail);
                if (nExit != 0) {
                    say.accept("Aviation " + spec.strName() + " exited " + nExit);
                    tail.drainTo(say);
                    return false;
                }
            }
            return true;
        }
        finally {
            // THE TOKENS ARE BEARERS. They were written to be handed to a
            // process and there is no reason for them to outlive it.
            deleteQuietly(fileTokenAdmin);
            deleteQuietly(fileTokenSuper);
            deleteQuietly(fileParties);
        }
    }


    /**
     * @param strName the script, as `Module:name`
     * @return what that script does, in the reader's terms
     */
    private static String strStepOf(String strName) {
        if (AviationFixture.STR_SCRIPT_PARTIES.equals(strName))
            return "allocating the five plant parties and their ledger users";
        if (AviationFixture.STR_SCRIPT_LEDGER.equals(strName))
            return "recording the batches, the readings and the releases";
        return "allocating the parties and recording the batches";
    }


    /**
     * @param strStep what is happening now, without the standing prefix
     */
    private void phase(String strStep) {
        SwingUtilities.invokeLater(() -> sayPhase.accept(STR_PHASE + ": " + strStep));
    }


    /**
     * @param runEnded told on the event thread, or null
     */
    private static void ended(Runnable runEnded) {
        if (runEnded == null)
            return;
        SwingUtilities.invokeLater(runEnded);
    }


    /**
     * @param auth what the participant checks
     * @param version the Canton that is running
     * @param strUser the ledger user the token speaks for
     * @return the file holding the bearer
     * @throws IOException when the mint never answered
     */
    private static Path fileToken(AuthSettings auth, VersionId version, String strUser)
            throws IOException {
        String strToken = JwtMintProcess.strMintBlocking(auth, version, strUser,
                StorageOverlay.STR_NODE_PARTICIPANT, TIMEOUT_MINT);
        if (strToken == null) {
            throw new IOException("the JWT service did not mint a token for " + strUser
                    + " within " + TIMEOUT_MINT);
        }

        Path fileOut = Files.createTempFile("aviation-token-" + strUser, ".txt");
        Files.write(fileOut, strToken.getBytes(StandardCharsets.UTF_8));
        return fileOut;
    }


    /**
     * @param runNew told when this session has written a setting the Settings
     *        tab is showing; may be null
     */
    public void useSettingsSink(Runnable runNew) {
        this.runSettings = runNew;
    }


    /** Records `Never`, so the question is not asked again on this machine. */
    private void rememberNever() {
        RaposzaSettings settingsNow = RaposzaSettings.current();
        RaposzaSettings settingsNew = new RaposzaSettings(settingsNow.dirHome(),
                settingsNow.nPortMint(), settingsNow.nPortDiscovery(), settingsNow.strLine(),
                settingsNow.strLauncher(), settingsNow.nPortFirst(),
                settingsNow.nPortPostgres(), settingsNow.nSecondsReady(), false,
                settingsNow.flagOfferPharma(), settingsNow.strUrlOidc(),
                settingsNow.nPortUiFirst(),
                settingsNow.dirDaml(), settingsNow.dirDpm(), settingsNow.dirSplice());
        try {
            RaposzaSettings.store(settingsNew);
            say.accept("The Aviation offer is off. The Settings tab turns it back on.");
            // AND THE TAB IS TOLD, which is what Pharma's does and this
            // did not - `todo.md` A-36.
            ended(runSettings);
        }
        catch (IOException ex) {
            // A SAVE THAT FAILED IS SAID. The alternative is a `Never` that is
            // honoured this session and forgotten by the next one, which reads
            // as the application ignoring an answer.
            say.accept("the Aviation offer could not be switched off: " + ex.getMessage());
        }
    }


    /**
     * @param dir the directory to remove when it holds nothing
     * @throws IOException when it cannot be listed or removed
     */
    private static void deleteIfEmpty(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir))
            return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            if (stream.iterator().hasNext())
                return;
        }
        Files.deleteIfExists(dir);
    }


    /**
     * @param file what to remove, or null
     */
    private static void deleteQuietly(Path file) {
        if (file == null)
            return;
        try {
            Files.deleteIfExists(file);
        }
        catch (IOException ex) {
            // A leftover token in the temp directory is not worth a line in a
            // log somebody is reading for a fixture.
        }
    }

}
