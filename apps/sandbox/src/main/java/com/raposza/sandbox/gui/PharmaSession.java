// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.DpmBundles;
import com.raposza.canton.install.ToolchainRoots;
import com.raposza.canton.install.VersionId;
import com.raposza.sandbox.app.AuthSettings;
import com.raposza.sandbox.app.AviationRun;
import com.raposza.sandbox.app.JwtMintProcess;
import com.raposza.sandbox.app.PharmaFixture;
import com.raposza.sandbox.app.PharmaLedger;
import com.raposza.sandbox.app.PharmaOffer;
import com.raposza.sandbox.app.PharmaStory;
import com.raposza.runtime.settings.RaposzaSettings;

import java.awt.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

/**
 * The Pharma fixture as the window uses it: the question at open, the build,
 * the upload to both participants and the Story 1 population afterwards.
 *
 * <h2>THE QUESTION IS ASKED AT OPEN, NOT AT START</h2>
 *
 * Aviation's reason, unchanged: a build is a compiler and takes minutes, and
 * asking at Start would put that inside the start path where the reader is
 * waiting for a participant. What differs is everything after the build.
 *
 * <h2>THE DAR REACHES THE LEDGER AFTER THE START, NOT DURING IT</h2>
 *
 * On the Sandbox topology Aviation's DAR is ticked in the DARs tab and uploaded
 * when Start is pressed. On LocalNetND there is no such route - the window's
 * admin port answers 0 there and the runner uploads nothing - so this fixture
 * waits for the stack to be UP and then uploads over the JSON Ledger API to
 * each organizational participant, which {@link PharmaLedger} says is the
 * measured one.
 *
 * <h2>NOTHING OF THE FIXTURE OUTLIVES THE WINDOW</h2>
 *
 * {@link #discard} removes the staged project and the DAR the compiler wrote.
 * The ledger it populated does not survive either: every start on this topology
 * wipes the cluster - D-799.
 *
 * Author Claude/bentzn
 */
public final class PharmaSession {

    /** THE WHOLE DIALOG until it is expanded. One line. */
    public static final String STR_QUESTION = "Install Pharma test fixture?";

    /** The same question where the fixture is already on disk. */
    public static final String STR_QUESTION_BUILT =
            "Use the Pharma test fixture built earlier?";

    /** What the link reveals. */
    public static final String STR_DETAIL =
            "A LocalNet that has just been founded holds no business data, so there\n"
            + "is nothing to look at and no way to tell a browser that works from one\n"
            + "that does not. This puts a small, known set of contracts on it.\n\n"
            + "WHAT IT IS. A pharmaceutical producer buying a regulated production\n"
            + "input from one of its suppliers. The two organizations sit on the two\n"
            + "organizational participants - the producer on app-provider, the\n"
            + "supplier on app-user - so the boundary in the story is a real boundary\n"
            + "in the network rather than two parties on one node.\n\n"
            + "STORY 1, THE CLEAN LOT. The producer orders 12000 vials against an\n"
            + "agreed specification. The supplier accepts the order, records lot\n"
            + "LOT-88213, tests it, issues a Certificate of Analysis and ships it.\n"
            + "The producer receives it, runs its OWN incoming tests, and releases it\n"
            + "for production. Both organizations assert what they measured and\n"
            + "neither can rewrite the other's assertions.\n\n"
            + "WHAT IT DEMONSTRATES. Every choice is controlled by one organization\n"
            + "and submitted from that organization's participant, so authorization\n"
            + "and privacy mean something: the Workbench shows a different ledger from\n"
            + "each side. Nested data - a list of quality tests, each with a measured\n"
            + "value and a permitted range inside it - is there for WHERE to reach\n"
            + "with a dotted path.\n\n"
            + "STORY 2, THE PROBLEM LOT, IS NOT BUILT YET, and neither are contract\n"
            + "keys. `fixture_pharma.md` is the narrative both come from.\n\n"
            + "IT IS REMOVED WHEN THE SANDBOX IS CLOSED.";

    /** The link that opens {@link #STR_DETAIL}. */
    public static final String STR_EXPAND = "Expand";

    public static final String STR_YES = "Yes";

    public static final String STR_NOT_NOW = "Not now";

    public static final String STR_NEVER = "Never";

    /** In the order the buttons appear, and the same three Aviation offers. */
    public static final Object[] ARR_OPTION = { STR_YES, STR_NOT_NOW, STR_NEVER };

    public static final int IDX_YES = 0;

    public static final int IDX_NEVER = 2;

    /** What every progress line of the run begins with; the spinning prefix. */
    public static final String STR_PHASE = "Creating the Pharma test data";

    /** The same wait the PQS token gets: a Spring Boot start, not a request. */
    public static final Duration TIMEOUT_MINT = Duration.ofMinutes(3);

    /**
     * How long a participant is given to join a synchronizer. MEASURED
     * 2026-09-22: a stack that answers `/v2/version` refuses every call that
     * needs one until it has, and a founding from nothing is about 72 s.
     */
    public static final int N_SECONDS_JOIN = 300;

    /** How much of a failed step's output is worth showing. */
    public static final int N_TAIL = 12;

    private final transient Consumer<String> say;

    /** The same pane, for a line that stays open with a spinner on it. */
    private final transient Consumer<String> sayBusy;

    /** Where a step of the run goes; always told ON THE EVENT THREAD. */
    private final transient Consumer<String> sayPhase;

    /** What the build produced, or null until it has. */
    private volatile Path fileDarBuilt;

    /** The project the DAR was built in. */
    private volatile Path dirProject;

    /**
     * Whether the story has been handed to a stack in this window.
     *
     * SET WHEN THE RUN STARTS, not when it succeeds: a failed run has already
     * allocated whatever it allocated, so a second attempt against the same
     * ledger is the same collision one step earlier. Aviation's rule.
     */
    private volatile boolean flagRan;

    /** Told when a setting this session wrote has to be shown again. */
    private transient Runnable runSettings;


    /**
     * @param sayNew where every line goes; never null
     * @param sayBusyNew where a line goes that should carry a spinner until the
     *        next one arrives; never null
     * @param sayPhaseNew where a step of the run goes, on the event thread;
     *        never null
     */
    public PharmaSession(Consumer<String> sayNew, Consumer<String> sayBusyNew,
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
     * THE MAIN TAB IS A SUMMARY, NOT A TRANSCRIPT. The compiler writes about
     * forty lines, of which none matters while it is going right.
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
     * @return whether a DAR is built and waiting for a stack
     */
    public boolean isStaged() {
        return fileDarBuilt != null;
    }


    /**
     * @return whether {@link #run} has been called since the last build
     */
    public boolean hasRun() {
        return flagRan;
    }


    /**
     * Asks, and on `Yes` builds. ON THE EVENT THREAD.
     *
     * @param owner the dialog's parent, or null
     * @param instStock the Canton the three participants run inside, or null
     *        when none carries a runtime jar
     * @param runBegun told, on the event thread, the moment a build starts
     * @param runReady told, on the event thread, once the DAR is there
     * @param runFailed told, on the event thread, when a build that began
     *        produced nothing; may be null
     */
    public void offer(Component owner, CantonInstallation instStock, Runnable runBegun,
            Runnable runReady, Runnable runFailed) {
        if (instStock == null) {
            say.accept("Pharma test data not offered: " + PharmaOffer.STR_WHY_NO_CANTON);
            return;
        }

        String strKey = VersionKey.strOf(instStock);
        String strSdk = PharmaFixture.strSdkFor(instStock.version().toString(),
                DpmBundles.mapBundle(DpmBundles.dirSdk(ToolchainRoots.ofDefaults())));
        Path dirProjectHere = PharmaFixture.dirProject(PharmaFixture.dirRootDefault(), strKey);
        Path fileBuilt = PharmaFixture.fileDar(dirProjectHere);
        boolean flagBuilt = Files.isRegularFile(fileBuilt);

        String strWhyNot = PharmaOffer.strWhyNot(
                RaposzaSettings.current().flagOfferPharma(), strSdk, flagBuilt);
        if (strWhyNot != null) {
            // ONLY THE MACHINE GAP IS WORTH A LINE. `switched off` is a state
            // the reader chose and can see on the Settings tab, and saying it
            // on every open is a line of noise per session - Aviation's rule.
            if (PharmaOffer.STR_WHY_TOOLCHAIN.equals(strWhyNot))
                say.accept("Pharma test data not offered: " + strWhyNot);
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

        // WHAT IS THERE IS USED RATHER THAN REBUILT. The DAR on disk is the
        // same artefact and the build is minutes of compiler.
        if (flagBuilt) {
            this.dirProject = dirProjectHere;
            this.fileDarBuilt = fileBuilt;
            say.accept("Pharma test data ready.");
            if (runReady != null)
                runReady.run();
            return;
        }
        build(dirProjectHere, strSdk, runBegun, runReady, runFailed);
    }


    /**
     * @param dirProjectHere where the project goes
     * @param strSdk what `daml.yaml` names
     * @param runBegun told, on the event thread, the moment a build starts
     * @param runReady told, on the event thread, once the DAR is there
     * @param runFailed told, on the event thread, when nothing was produced
     */
    /**
     * Builds the fixture because the operator asked for it on the Settings tab,
     * whatever they answered at open.
     *
     * NO DIALOG AND NO SETTING. The button IS the answer, and a machine that
     * cannot build is still told why. It REBUILDS rather than reusing what is
     * on disk, for the reason Aviation's does: the button exists to run the
     * thing, and a run wants the fixture fresh.
     *
     * @param instStock the Canton the three participants run inside, or null
     * @param runBegun told, on the event thread, the moment a build starts
     * @param runReady told, on the event thread, once the DAR is there
     * @param runFailed told, on the event thread, when nothing was produced
     */
    public void buildNow(CantonInstallation instStock, Runnable runBegun,
            Runnable runReady, Runnable runFailed) {
        if (instStock == null) {
            say.accept("Pharma test data cannot be built: " + PharmaOffer.STR_WHY_NO_CANTON);
            return;
        }

        String strSdk = PharmaFixture.strSdkFor(instStock.version().toString(),
                DpmBundles.mapBundle(DpmBundles.dirSdk(ToolchainRoots.ofDefaults())));
        if (strSdk == null) {
            say.accept("Pharma test data cannot be built: " + PharmaOffer.STR_WHY_TOOLCHAIN);
            return;
        }

        build(PharmaFixture.dirProject(PharmaFixture.dirRootDefault(),
                VersionKey.strOf(instStock)), strSdk, runBegun, runReady, runFailed);
    }


    /**
     * @param runNew told when this session has written a setting the Settings
     *        tab is showing; may be null
     */
    public void useSettingsSink(Runnable runNew) {
        this.runSettings = runNew;
    }


    private void build(Path dirProjectHere, String strSdk, Runnable runBegun,
            Runnable runReady, Runnable runFailed) {
        this.flagRan = false;
        if (runBegun != null)
            runBegun.run();
        // SPINNING, because the compiler says nothing for a minute and a line
        // that just sits there is indistinguishable from a window that has
        // stopped.
        sayBusy.accept("Building the Pharma test data. This takes a minute");

        Thread threadBuild = new Thread(() -> {
            Tail tail = new Tail();
            try {
                PharmaFixture.stage(dirProjectHere, strSdk);
                // THE BUILDER IS AVIATION'S AND IS CALLED RATHER THAN COPIED.
                // It resolves the launcher, runs it in the project directory
                // and drains both streams; nothing in it is about a model.
                int nExit = AviationRun.nBuild(dirProjectHere, strSdk, tail);
                if (nExit != 0) {
                    say.accept("the Pharma build exited " + nExit + "; no test data was built");
                    tail.drainTo(say);
                    ended(runFailed);
                    return;
                }
                Path fileBuilt = PharmaFixture.fileDar(dirProjectHere);
                if (!Files.isRegularFile(fileBuilt)) {
                    say.accept("the Pharma build exited 0 and produced no " + fileBuilt);
                    ended(runFailed);
                    return;
                }
                this.dirProject = dirProjectHere;
                this.fileDarBuilt = fileBuilt;
                say.accept("Pharma test data built.");
                ended(runReady);
            }
            catch (IOException | RuntimeException ex) {
                say.accept("the Pharma build failed: " + ex.getMessage());
                ended(runFailed);
            }
        }, "raposza-pharma-build");
        threadBuild.setDaemon(true);
        threadBuild.start();
    }


    /**
     * Puts the fixture on a stack that is up. NOT ON THE EVENT THREAD - this
     * starts a thread of its own.
     *
     * @param auth what the participants check
     * @param version the Canton they run inside, which caps a token's lifetime
     * @param strNode the participant node name the audience is built on
     * @param nPortFirst the lowest port of the block
     * @param sinkDone told, on the event thread, whether every step committed;
     *        may be null
     */
    public void run(AuthSettings auth, VersionId version, String strNode, int nPortFirst,
            Consumer<Boolean> sinkDone) {
        Path fileDar = fileDarBuilt;
        if (fileDar == null)
            return;

        this.flagRan = true;

        Thread threadRun = new Thread(() -> {
            boolean flagOk = false;
            try {
                flagOk = flagRun(auth, version, strNode, nPortFirst, fileDar);
            }
            catch (IOException | InterruptedException | RuntimeException ex) {
                if (ex instanceof InterruptedException)
                    Thread.currentThread().interrupt();
                say.accept("Pharma test data failed: " + ex.getMessage());
            }
            final boolean flagSaid = flagOk;
            SwingUtilities.invokeLater(() -> {
                if (sinkDone != null)
                    sinkDone.accept(Boolean.valueOf(flagSaid));
            });
        }, "raposza-pharma-run");
        threadRun.setDaemon(true);
        threadRun.start();
    }


    /**
     * @param auth what the participants check
     * @param version the Canton they run inside
     * @param strNode the node name the audience is built on
     * @param nPortFirst the lowest port of the block
     * @param fileDar the archive to upload
     * @return whether every step committed
     * @throws IOException when a participant refuses
     * @throws InterruptedException when a call is interrupted
     */
    private boolean flagRun(AuthSettings auth, VersionId version, String strNode,
            int nPortFirst, Path fileDar) throws IOException, InterruptedException {
        boolean flagAuth = auth != null && auth.mode() != AuthSettings.Mode.NONE;
        String strToken = null;
        if (flagAuth) {
            phase("minting the token the fixture submits under");
            // ONE TOKEN FOR BOTH. `LocalNetAuth` gives all three roles the same
            // audience, so a token minted for the node works against whichever
            // Ledger API it is presented to.
            strToken = JwtMintProcess.strMintBlocking(auth, version, PharmaLedger.STR_USER,
                    strNode, TIMEOUT_MINT);
            if (strToken == null) {
                say.accept("Pharma test data failed: the mint never answered");
                return false;
            }
        }

        PharmaLedger ledgerProducer = new PharmaLedger(PharmaLedger.strBaseOf(
                PharmaLedger.nPortJson(nPortFirst, PharmaLedger.N_OFFSET_PRODUCER)), strToken);
        PharmaLedger ledgerSupplier = new PharmaLedger(PharmaLedger.strBaseOf(
                PharmaLedger.nPortJson(nPortFirst, PharmaLedger.N_OFFSET_SUPPLIER)), strToken);

        // BEFORE ANYTHING ELSE. A participant that is listening has not
        // necessarily joined a synchronizer, and everything below needs one.
        phase("waiting for both participants to join the synchronizer");
        ledgerProducer.waitJoined(N_SECONDS_JOIN);
        ledgerSupplier.waitJoined(N_SECONDS_JOIN);

        phase("uploading the DAR to both participants");
        byte[] arrDar = Files.readAllBytes(fileDar);
        ledgerProducer.uploadDar(arrDar);
        ledgerSupplier.uploadDar(arrDar);

        // A PARTY PER ACTUAL USER - his instruction, 2026-09-22. The user comes
        // FIRST and the party after it: allocation naming a user that does not
        // exist is refused with `USER_NOT_FOUND`, measured the same day in
        // `probes/pharma/user_probe.py`.
        phase("creating the eight ledger users, one per person");
        ledgerProducer.createUser(PharmaStory.STR_USER_PROCUREMENT);
        ledgerProducer.createUser(PharmaStory.STR_USER_QA_PRODUCER);
        ledgerProducer.createUser(PharmaStory.STR_USER_RECEIVING);
        ledgerProducer.createUser(PharmaStory.STR_USER_MANUFACTURING);
        ledgerSupplier.createUser(PharmaStory.STR_USER_SALES);
        ledgerSupplier.createUser(PharmaStory.STR_USER_PRODUCTION);
        ledgerSupplier.createUser(PharmaStory.STR_USER_QA_SUPPLIER);
        ledgerSupplier.createUser(PharmaStory.STR_USER_SHIPPING);

        phase("allocating a party per user, on its own organization's node");
        PharmaStory.Cast cast = new PharmaStory.Cast(
                ledgerProducer.strAllocateFor(PharmaStory.STR_PARTY_PROCUREMENT,
                        PharmaStory.STR_USER_PROCUREMENT),
                ledgerProducer.strAllocateFor(PharmaStory.STR_PARTY_QA_PRODUCER,
                        PharmaStory.STR_USER_QA_PRODUCER),
                ledgerProducer.strAllocateFor(PharmaStory.STR_PARTY_RECEIVING,
                        PharmaStory.STR_USER_RECEIVING),
                ledgerProducer.strAllocateFor(PharmaStory.STR_PARTY_MANUFACTURING,
                        PharmaStory.STR_USER_MANUFACTURING),
                ledgerSupplier.strAllocateFor(PharmaStory.STR_PARTY_SALES,
                        PharmaStory.STR_USER_SALES),
                ledgerSupplier.strAllocateFor(PharmaStory.STR_PARTY_PRODUCTION,
                        PharmaStory.STR_USER_PRODUCTION),
                ledgerSupplier.strAllocateFor(PharmaStory.STR_PARTY_QA_SUPPLIER,
                        PharmaStory.STR_USER_QA_SUPPLIER),
                ledgerSupplier.strAllocateFor(PharmaStory.STR_PARTY_SHIPPING,
                        PharmaStory.STR_USER_SHIPPING));

        // THE POPULATION ACCOUNT IS NOT ONE OF THE EIGHT. Allocation gave each
        // party to its own person and to nobody else, so the account this
        // fixture submits under - the one the token was minted for - now holds
        // no right over any of them. It is granted them explicitly rather than
        // the story being submitted as somebody it should not be.
        phase("giving the population account the parties it submits as");
        for (String strParty : cast.lstProducer()) {
            ledgerProducer.grantActAs(PharmaLedger.STR_USER, strParty);
        }
        for (String strParty : cast.lstSupplier()) {
            ledgerSupplier.grantActAs(PharmaLedger.STR_USER, strParty);
        }

        // ONE PER ORGANIZATION, HOLDING ITS OWN FOUR PARTIES - his instruction,
        // 2026-09-22. This is what the Workbench's `work as` picks up: without
        // it a tab reads as one role and sees a quarter of its own
        // organization's story. It is NOT an any-party right - D-550 - so the
        // producer's superuser still cannot read the supplier's private
        // contracts, which is the boundary the fixture exists to show.
        phase("creating each organization's superuser");
        ledgerProducer.createUser(PharmaStory.STR_USER_SUPER, cast.strQaProducer(),
                cast.lstProducer(), cast.lstProducer());
        ledgerSupplier.createUser(PharmaStory.STR_USER_SUPER, cast.strQaSupplier(),
                cast.lstSupplier(), cast.lstSupplier());

        phase("Quality Assurance recording the specification, Procurement the order");
        ledgerProducer.strSubmit(cast.strQaProducer(), PharmaStory.strSpec(cast));
        String strOrder = PharmaLedger.strCreated(
                ledgerProducer.strSubmit(cast.strProcurement(), PharmaStory.strOrder(cast)),
                PharmaStory.STR_T_ORDER);

        phase("Sales accepting, Production making, supplier QA testing the lot");
        String strAccept = PharmaLedger.strCreated(
                ledgerSupplier.strSubmit(cast.strSales(),
                        PharmaStory.strAcceptOrder(strOrder)),
                PharmaStory.STR_T_ACCEPT);
        String strLot = PharmaLedger.strCreated(
                ledgerSupplier.strSubmit(cast.strProduction(),
                        PharmaStory.strRecordLot(cast, strAccept)),
                PharmaStory.STR_T_LOT);
        String strCoa = PharmaLedger.strCreated(
                ledgerSupplier.strSubmit(cast.strQaSupplier(),
                        PharmaStory.strIssueCoa(strLot)),
                PharmaStory.STR_T_COA);

        phase("Shipping sending the lot");
        String strShipment = PharmaLedger.strCreated(
                ledgerSupplier.strSubmit(cast.strShipping(), PharmaStory.strShip(strCoa)),
                PharmaStory.STR_T_SHIPMENT);

        phase("Receiving taking it in, Quality Assurance inspecting and releasing it");
        String strReceipt = PharmaLedger.strCreated(
                ledgerProducer.strSubmit(cast.strReceiving(),
                        PharmaStory.strReceive(strShipment)),
                PharmaStory.STR_T_RECEIPT);
        String strInspection = PharmaLedger.strCreated(
                ledgerProducer.strSubmit(cast.strQaProducer(),
                        PharmaStory.strInspect(cast, strReceipt)),
                PharmaStory.STR_T_INSPECTION);
        PharmaLedger.strCreated(
                ledgerProducer.strSubmit(cast.strQaProducer(),
                        PharmaStory.strRelease(strInspection)),
                PharmaStory.STR_T_RELEASE);

        // STORY 2, THE PROBLEM LOT - `fixture_pharma.md`. The same eight
        // people and the same nine steps up to the incoming test, which is
        // where the two organizations stop agreeing.
        phase("Story 2: a second order, made and certified the same way");
        String strOrder2 = PharmaLedger.strCreated(
                ledgerProducer.strSubmit(cast.strProcurement(),
                        PharmaStory.strOrderProblem(cast)),
                PharmaStory.STR_T_ORDER);
        String strAccept2 = PharmaLedger.strCreated(
                ledgerSupplier.strSubmit(cast.strSales(),
                        PharmaStory.strAcceptOrder(strOrder2)),
                PharmaStory.STR_T_ACCEPT);
        String strLot2 = PharmaLedger.strCreated(
                ledgerSupplier.strSubmit(cast.strProduction(),
                        PharmaStory.strRecordLotProblem(cast, strAccept2)),
                PharmaStory.STR_T_LOT);
        String strCoa2 = PharmaLedger.strCreated(
                ledgerSupplier.strSubmit(cast.strQaSupplier(),
                        PharmaStory.strIssueCoaProblem(strLot2)),
                PharmaStory.STR_T_COA);
        String strShipment2 = PharmaLedger.strCreated(
                ledgerSupplier.strSubmit(cast.strShipping(),
                        PharmaStory.strShipProblem(strCoa2)),
                PharmaStory.STR_T_SHIPMENT);

        phase("Story 2: the incoming test disagrees with the certificate");
        String strReceipt2 = PharmaLedger.strCreated(
                ledgerProducer.strSubmit(cast.strReceiving(),
                        PharmaStory.strReceive(strShipment2)),
                PharmaStory.STR_T_RECEIPT);
        String strInspection2 = PharmaLedger.strCreated(
                ledgerProducer.strSubmit(cast.strQaProducer(),
                        PharmaStory.strInspectProblem(cast, strReceipt2)),
                PharmaStory.STR_T_INSPECTION);

        // THE PRODUCER'S OWN, AND NOBODY ELSE'S. `Quarantine` names no
        // supplier party, so this submission is the one step of the fixture
        // whose result the other participant cannot see.
        phase("Story 2: the lot is quarantined - the producer's own record");
        String strQuarantine = PharmaLedger.strCreated(
                ledgerProducer.strSubmit(cast.strQaProducer(),
                        PharmaStory.strQuarantine(strInspection2)),
                PharmaStory.STR_T_QUARANTINE);

        phase("Story 2: the deviation, which is where the supplier hears of it");
        String strDeviation = PharmaLedger.strCreated(
                ledgerProducer.strSubmit(cast.strQaProducer(),
                        PharmaStory.strRaiseDeviation(cast, strQuarantine)),
                PharmaStory.STR_T_DEVIATION);
        String strResponse = PharmaLedger.strCreated(
                ledgerSupplier.strSubmit(cast.strQaSupplier(),
                        PharmaStory.strRespond(strDeviation)),
                PharmaStory.STR_T_RESPONSE);

        phase("Story 2: the retest, the disposition, and the quarantine closed");
        String strRetest = PharmaLedger.strCreated(
                ledgerProducer.strSubmit(cast.strQaProducer(),
                        PharmaStory.strRetest(strResponse)),
                PharmaStory.STR_T_RETEST);
        String strDisposition = PharmaLedger.strCreated(
                ledgerProducer.strSubmit(cast.strQaProducer(),
                        PharmaStory.strDispose(cast, strRetest)),
                PharmaStory.STR_T_DISPOSITION);
        ledgerProducer.strSubmit(cast.strQaProducer(),
                PharmaStory.strCloseQuarantine(strQuarantine, strDisposition));
        return true;
    }


    /**
     * Takes the fixture off disk: the staged project with the DAR under it.
     *
     * ON THE EVENT THREAD, at close.
     *
     * @param instStock the Canton selected, or null - used only when this
     *        window never built anything and an earlier session's project is
     *        still there
     */
    public void discard(CantonInstallation instStock) {
        Path dirHere = dirProject;
        if (dirHere == null && instStock != null) {
            dirHere = PharmaFixture.dirProject(PharmaFixture.dirRootDefault(),
                    VersionKey.strOf(instStock));
        }

        this.fileDarBuilt = null;
        this.dirProject = null;
        this.flagRan = false;
        if (dirHere == null)
            return;

        try {
            PharmaFixture.deleteProject(dirHere);
        }
        catch (IOException ex) {
            say.accept("the Pharma project could not be removed: " + ex.getMessage());
        }
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


    /** Records `Never`, so the question is not asked again on this machine. */
    private void rememberNever() {
        RaposzaSettings settingsNow = RaposzaSettings.current();
        RaposzaSettings settingsNew = new RaposzaSettings(settingsNow.dirHome(),
                settingsNow.nPortMint(), settingsNow.nPortDiscovery(), settingsNow.strLine(),
                settingsNow.strLauncher(), settingsNow.nPortFirst(),
                settingsNow.nPortPostgres(), settingsNow.nSecondsReady(),
                settingsNow.flagOfferAviation(), false, settingsNow.strUrlOidc(),
                settingsNow.nPortUiFirst(),
                settingsNow.dirDaml(), settingsNow.dirDpm(), settingsNow.dirSplice(),
                settingsNow.nPortRawar());
        try {
            RaposzaSettings.store(settingsNew);
            say.accept("The Pharma offer is off. The Settings tab turns it back on.");
            // AND THE TAB IS TOLD. It shows this setting, and nothing else
            // would make it re-read the file - measured at his console,
            // 2026-09-22: the log said off and the checkbox stayed ticked.
            ended(runSettings);
        }
        catch (IOException ex) {
            // A SAVE THAT FAILED IS SAID. The alternative is a `Never` that is
            // honoured this session and forgotten by the next one, which reads
            // as the application ignoring an answer.
            say.accept("the Pharma offer could not be switched off: " + ex.getMessage());
        }
    }

}
