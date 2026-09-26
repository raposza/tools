// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.PqsInstallation;
import com.raposza.canton.topology.Canton3xBootstrap;
import com.raposza.canton.topology.Canton3xDaemonBootstrap;
import com.raposza.canton.install.VersionId;
import com.raposza.canton.topology.StorageOverlay;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Starts a sandbox and keeps it up until it is asked to stop.
 *
 * The thing this module owed. Until now the only thing that could start a stack
 * was a gated JUnit test that tore it down again, so every claim about the
 * Sandbox as a product rested on something no user could run.
 *
 * <h2>What is here and what moved</h2>
 *
 * This class is the TERMINAL: arguments, usage, what is installed, exit codes,
 * the shutdown hook and the wait. The sequence that resolves an installation
 * and brings a stack up is {@link SandboxService}, because the window needs the
 * same sequence and two copies of it would drift on the first flag either one
 * gained.
 *
 * <h2>The window is the default; `--cli` is the terminal</h2>
 *
 * A line without `--cli` opens {@link com.raposza.sandbox.gui.SandboxGui}
 * instead of starting anything here, and every other argument on it PRE-FILLS
 * the form. `--cli` starts the stack in this process and waits for Ctrl-C.
 * The switch is read before parsing, so `--cli` never reaches
 * {@link SandboxOptions#parse}, and a `--cli` run touches no Swing class at
 * all - on a machine with no display, initialising it would be the failure
 * rather than anything about the stack.
 *
 * `--help` and `--list` print and stop, so they are terminal runs with or
 * without `--cli`: a window opened for either would answer neither.
 *
 * <h2>The launcher, and what it changes</h2>
 *
 * `--launcher daemon` starts the restartable stack. On the default subcommand
 * a persistent stack still cannot be started twice - Canton's own generated
 * bootstrap re-proposes a synchronizer trust certificate that already exists
 * and the stack exits - and this entry point says so at start rather than
 * failing at it. The message names the launcher it is true OF, and names the
 * one it is not true of.
 *
 * The bootstrap differs with the launcher and is not a preference: the
 * subcommand refuses `--bootstrap` and gets a script run by a remote console,
 * `daemon` gets one that runs inside the node JVM. `SandboxStack` refuses the
 * wrong kind, so the choice is made once, in {@link SandboxService}.
 *
 * <h2>What it does not do</h2>
 *
 * It does not authenticate the Ledger API unless `--auth` is given. With it,
 * Raposza OIDC is started on `127.0.0.1` before Canton and the participant is
 * pointed at its key set - {@link AuthStart}.
 *
 * It does not run PQS under `daemon`. Nothing refuses it - `--pqs auto` with
 * `--launcher daemon` starts scribe - but nothing here has ever measured it,
 * and a line is printed saying so rather than letting a green start imply a
 * measurement.
 *
 * <h2>Stopping</h2>
 *
 * A shutdown hook, because there is nothing else: this process is stopped with
 * Ctrl-C or SIGTERM and both arrive as a hook. It stops PQS, then Canton, then
 * PostgreSQL - the order `SandboxStack` already owns - and removes the status
 * file, so a file present means a stack is up.
 *
 * Author Claude/bentzn
 */
public final class SandboxApp {

    public static final int N_EXIT_OK = 0;

    public static final int N_EXIT_USAGE = 2;

    public static final int N_EXIT_NOT_INSTALLED = 3;

    public static final int N_EXIT_PORTS = 4;

    public static final int N_EXIT_START = 5;

    /**
     * A modal dialog was raised with nobody to dismiss it.
     *
     * Reachable only once {@link com.raposza.sandbox.gui.Modals} has
     * been armed, which an unattended driver does for such a run. The
     * dialog is suppressed, its text is printed, and the whole run ends rather
     * than the one step: a run that reports hundreds of green steps and one
     * silently skipped dialog is read as a result.
     */
    public static final int N_EXIT_UNATTENDED_MODAL = 6;

    /** Starts the stack here, in the terminal, instead of opening the window. */
    public static final String STR_ARG_CLI = "--cli";

    private SandboxApp() {
    }


    /**
     * @param arrArg the command line
     */
    public static void main(String[] arrArg) {
        // BEFORE anything else, and before any Swing class is named: a
        // terminal run must not initialise a toolkit it has no display for.
        if (flagCli(arrArg)) {
            System.exit(run(withoutCli(arrArg)));
            return;
        }
        com.raposza.sandbox.gui.SandboxGui.open(withoutCli(arrArg));
    }


    /**
     * Whether this line runs in the terminal. `--cli` says so; `--help`, `-h`
     * and `--list` say so as well, because they print and stop and a window
     * would show neither.
     *
     * @param arrArg the command line
     * @return whether the stack is to start here rather than in the window
     */
    static boolean flagCli(String[] arrArg) {
        if (arrArg == null)
            return false;
        for (String strArg : arrArg) {
            if (STR_ARG_CLI.equals(strArg) || "--help".equals(strArg) || "-h".equals(strArg)
                    || "--list".equals(strArg))
                return true;
        }
        return false;
    }


    /**
     * The rest of the line. The window pre-fills its form from it, and a
     * terminal run parses it, so neither sees the switch. Kept separate from
     * {@link #flagCli} so that `--launcher daemon` opens a window already set
     * to the launcher that was asked for, rather than silently ignoring
     * everything on the line.
     *
     * @param arrArg the command line
     * @return the same arguments with every `--cli` removed
     */
    static String[] withoutCli(String[] arrArg) {
        if (arrArg == null)
            return new String[0];
        List<String> lstArg = new ArrayList<>();
        for (String strArg : arrArg) {
            if (!STR_ARG_CLI.equals(strArg))
                lstArg.add(strArg);
        }
        return lstArg.toArray(new String[0]);
    }


    /**
     * @param arrArg the command line
     * @return the process exit code
     */
    public static int run(String[] arrArg) {
        // `--fixture` IS STRIPPED BEFORE THE PARSER SEES IT, the same shape
        // `--cli` has above: the parser refuses an argument it does not know,
        // and whether test data lands on the ledger is not a property of the
        // stack. FixtureStart says why.
        boolean flagFixture = FixtureStart.flagIn(arrArg);
        int nPortDiscovery = DiscoveryStart.nPortIn(arrArg);
        AuthSettings auth = AuthStart.authIn(arrArg);
        String[] arrRest = AuthStart.without(
                DiscoveryStart.without(FixtureStart.without(arrArg)));

        SandboxOptions options;
        try {
            options = SandboxOptions.parse(arrRest);
        }
        catch (RuntimeException ex) {
            System.err.println(ex.getMessage());
            System.err.println();
            System.err.print(SandboxOptions.usage());
            return N_EXIT_USAGE;
        }

        if (options.flagHelp()) {
            System.out.print(SandboxOptions.usage());
            return N_EXIT_OK;
        }
        if (options.flagList()) {
            printInstalled();
            return N_EXIT_OK;
        }

        if (!flagFixture)
            return start(options, null, null, nPortDiscovery, auth);

        return startWithFixture(arrRest, options, nPortDiscovery, auth);
    }


    /**
     * The fixture is BUILT BEFORE THE STACK STARTS, because the upload is part
     * of starting: a DAR that appears afterwards is a DAR nothing uploads. So
     * the installation is resolved here, the DAR is built for it, and the line
     * is parsed a second time with `--dar` appended.
     *
     * @param arrRest the command line with `--fixture` already removed
     * @param options what that line parsed to
     * @param nPortDiscovery the discovery port asked for, or 0 for none
     * @param auth what the participant is to check, or null for none
     * @return the process exit code
     */
    private static int startWithFixture(String[] arrRest, SandboxOptions options,
            int nPortDiscovery, AuthSettings auth) {
        Optional<CantonInstallation> optInst = SandboxService.optInstallFor(options);
        if (optInst.isEmpty()) {
            System.err.println("no Canton matches what was asked for, so there is nothing to"
                    + " build the Aviation fixture for");
            return N_EXIT_NOT_INSTALLED;
        }

        CantonInstallation inst = optInst.get();
        String strSdk = FixtureStart.strSdkFor(inst);
        if (strSdk == null) {
            System.err.println(FixtureStart.STR_WHY_TOOLCHAIN + ": canton " + inst.version());
            return N_EXIT_NOT_INSTALLED;
        }

        Path dirProject = FixtureStart.dirProjectFor(inst);
        Path fileDar;
        try {
            System.out.println("building the Aviation fixture for canton " + inst.version()
                    + " with sdk " + strSdk + ". This takes a minute");
            fileDar = FixtureStart.fileBuild(dirProject, strSdk, System.out::println);
        }
        catch (IOException ex) {
            System.err.println("the Aviation build failed: " + ex.getMessage());
            return N_EXIT_START;
        }
        if (fileDar == null)
            return N_EXIT_START;

        System.out.println("Aviation fixture built: " + fileDar);

        SandboxOptions optionsDar;
        try {
            optionsDar = SandboxOptions.parse(FixtureStart.withDar(arrRest, fileDar));
        }
        catch (RuntimeException ex) {
            System.err.println(ex.getMessage());
            return N_EXIT_USAGE;
        }
        return start(optionsDar, dirProject, fileDar, nPortDiscovery, auth);
    }


    /**
     * @param options what was asked for
     * @param dirProject the staged fixture project, or null when no fixture
     *        was asked for
     * @param fileDar the fixture DAR, or null when no fixture was asked for
     * @param nPortDiscovery the discovery port asked for, or 0 for none
     * @param auth what the participant is to check, or null for none
     * @return the process exit code
     */
    private static int start(SandboxOptions options, Path dirProject, Path fileDar,
            int nPortDiscovery, AuthSettings auth) {
        SandboxService service = new SandboxService(options, System.out::println);
        DiscoveryServer discovery = new DiscoveryServer();
        // AN ARRAY BECAUSE THE SHUTDOWN HOOK IS A LAMBDA. The mint is assigned
        // after the hook is installed - it must be, since a failure to start it
        // is a failure of the whole start - and a local reassigned later is not
        // effectively final.
        JwtMintProcess[] arrMint = new JwtMintProcess[1];

        CountDownLatch latchStopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println();
            System.out.println("stopping...");
            discovery.stop();
            if (arrMint[0] != null)
                arrMint[0].stop();
            try {
                service.stop();
            }
            finally {
                latchStopped.countDown();
            }
            System.out.println("stopped");
        }, "sandbox-shutdown"));

        // THE MINT COMES UP BEFORE CANTON. The participant reads the JWKS while
        // it starts, so a mint started beside it is a participant that does not
        // come up - AuthStart says so at length.
        VersionId version = options.version() != null ? options.version()
                : SandboxService.optInstallFor(options)
                        .map(CantonInstallation::version).orElse(null);
        if (auth != null && auth.mode() != AuthSettings.Mode.NONE) {
            try {
                arrMint[0] = AuthStart.mintServing(auth, System.out::println);
            }
            catch (RuntimeException ex) {
                System.err.println(ex.getMessage());
                return N_EXIT_START;
            }
            service.useAuth(auth.overlay(version, StorageOverlay.STR_NODE_PARTICIPANT,
                    JwtMintProcess.strUrlJwks()), UUID.randomUUID().toString());
        }

        try {
            service.start();
        }
        catch (SandboxService.StartException ex) {
            System.err.println(ex.getMessage());
            return ex.nExit();
        }

        // BEFORE THE FIXTURE, so that a consumer reading the fixture's own
        // ready line finds the endpoint already serving rather than racing it.
        if (nPortDiscovery > 0)
            DiscoveryStart.serve(discovery, service, nPortDiscovery, auth, version);

        if (fileDar != null)
            runFixture(service, dirProject, fileDar, auth, version);

        System.out.println();
        for (String strLine : service.report().lstLines()) {
            System.out.println(strLine);
        }
        System.out.println();
        System.out.println("  " + service.fileStatus());
        System.out.println("  ctrl-c to stop");

        try {
            latchStopped.await();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return N_EXIT_OK;
    }


    /**
     * THE STACK STAYS UP WHEN THE SCRIPTS FAIL, and the failure is said out
     * loud. A ledger that is up with nothing on it is something to look at;
     * stopping it would remove the evidence and leave a reader with an exit
     * code.
     *
     * @param service the stack, already ready
     * @param dirProject the staged fixture project
     * @param fileDar the fixture DAR
     * @param auth what the participant checks, or null for none
     * @param version the Canton running
     */
    private static void runFixture(SandboxService service, Path dirProject, Path fileDar,
            AuthSettings auth, VersionId version) {
        String strPort = service.report() == null ? null
                : service.report().value(ReadyReport.KEY_LEDGER_API);
        int nPortLedger;
        try {
            nPortLedger = Integer.parseInt(strPort);
        }
        catch (NumberFormatException | NullPointerException ex) {
            System.out.println("AVIATION FIXTURE DID NOT LAND: the ready report carries no"
                    + " Ledger API port");
            return;
        }

        int nExit = FixtureStart.nRun(dirProject, fileDar, nPortLedger, auth, version,
                System.out::println);
        if (nExit != 0) {
            System.out.println("AVIATION FIXTURE DID NOT LAND: the scripts exited " + nExit
                    + ". The stack is up and the ledger holds whatever the DAR upload put"
                    + " there.");
            return;
        }
        System.out.println("Aviation fixture on the ledger.");
    }


    /**
     * Kept as a delegate rather than moved outright: the selection is asserted
     * against this class, and the assertion is about what the APPLICATION does
     * with an argument list rather than about where the method now lives.
     *
     * @param options what was asked for
     * @return the console script for the subcommand launcher
     */
    static Canton3xBootstrap bootstrapFor(SandboxOptions options) {
        return SandboxService.bootstrapFor(options);
    }


    /**
     * @param options what was asked for
     * @return the in-JVM script for the daemon launcher
     */
    static Canton3xDaemonBootstrap bootstrapDaemonFor(SandboxOptions options) {
        return SandboxService.bootstrapDaemonFor(options);
    }


    private static void printInstalled() {
        List<CantonInstallation> lstCanton = SandboxService.lstCanton();
        System.out.println("canton installations (" + lstCanton.size() + "):");
        for (CantonInstallation inst : lstCanton) {
            System.out.println("  " + inst + (inst.hasRuntime() ? "" : "  [no runtime jar]"));
        }

        List<PqsInstallation> lstPqs = SandboxService.lstPqs();
        System.out.println("pqs installations (" + lstPqs.size() + "):");
        for (PqsInstallation inst : lstPqs) {
            System.out.println("  " + inst + (inst.isVendor() ? "" : "  [not vendor scribe]"));
        }
    }

}
