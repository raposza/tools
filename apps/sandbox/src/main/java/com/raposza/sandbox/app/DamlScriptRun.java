// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.canton.install.ToolchainLauncher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs one {@link DamlScriptSpec} to completion and reports what it did.
 *
 * <h2>Not a {@code ManagedProcess}</h2>
 *
 * Everything else this application starts is a server: it comes up, is watched
 * for a readiness line, and is stopped later. A script RUNS OUT. It has no
 * ready state, its exit code is the answer, and the thing worth keeping is its
 * output rather than a handle on it - so the process machinery that exists for
 * Canton and PQS would be carried here for none of the reasons it was built.
 *
 * <h2>The two readiness errors are retried, and only those two</h2>
 *
 * A participant answers its Ledger API port some seconds before it will accept
 * a script, and the failure it gives in between is one of two named strings -
 * measured by the test harness, which retries on exactly these and on nothing else. A
 * blanket retry would hide a genuine failure behind however many attempts were
 * configured; retrying two known strings turns a race into a wait.
 *
 * <h2>What the exit code means here</h2>
 *
 * The process's own code when it produced one. {@link #N_EXIT_TIMEOUT} when it
 * was killed for running past its timeout and {@link #N_EXIT_CANCELLED} when
 * the operator stopped it - both negative, because a negative number cannot
 * collide with an exit status and a caller that only tests for zero still gets
 * the right answer.
 *
 * Author Claude/bentzn
 */
public final class DamlScriptRun {

    /** Killed for running past its timeout. */
    public static final int N_EXIT_TIMEOUT = -1;

    /** Stopped by the operator. */
    public static final int N_EXIT_CANCELLED = -2;

    /** How many times a readiness failure is waited out. */
    public static final int N_TRY = 6;

    /** How long between attempts, matching the harness's own wait. */
    public static final int N_MS_RETRY = 5000;

    /** How long a killed process is given to die before it is forced. */
    private static final int N_MS_KILL = 3000;

    /**
     * The participant is up but not yet ready for a script. MEASURED - these
     * are the strings a run waits on, and nothing else is retried.
     */
    private static final List<String> LST_READINESS =
            List.of("CANNOT_AUTODETECT_SYNCHRONIZER", "NOT_CONNECTED_TO_ANY_DOMAIN");

    private volatile Process process;

    private volatile boolean flagCancelled;


    /**
     * @return whether {@link #cancel} has been called on this run
     */
    public boolean isCancelled() {
        return flagCancelled;
    }


    /**
     * Stops the run. Safe to call before it starts and after it has finished.
     */
    public void cancel() {
        this.flagCancelled = true;
        Process procHere = process;
        if (procHere == null)
            return;

        procHere.destroy();
        try {
            if (!procHere.waitFor(N_MS_KILL, TimeUnit.MILLISECONDS))
                procHere.destroyForcibly();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            procHere.destroyForcibly();
        }
    }


    /**
     * Runs it, retrying only the two readiness failures.
     *
     * Blocking, and never called on the event dispatch thread: a script against
     * a real ledger is seconds at best and minutes when it uploads.
     *
     * @param spec what to run; never null
     * @param outLine told every line the process wrote, plus this class's own
     *        narration; never null
     * @return the exit code, or one of the two negative constants
     * @throws IllegalStateException when `daml` is not on PATH, or the project
     *         directory is not one
     */
    public int nRun(DamlScriptSpec spec, Consumer<String> outLine) {
        if (spec == null)
            throw new IllegalArgumentException("a spec is required");
        if (outLine == null)
            throw new IllegalArgumentException("a line sink is required");

        requireUsable(spec);

        int nExit = N_EXIT_CANCELLED;
        for (int cntTry = 1; cntTry <= N_TRY; cntTry++) {
            if (flagCancelled) {
                outLine.accept("cancelled before attempt " + cntTry);
                return N_EXIT_CANCELLED;
            }

            List<String> lstLine = new ArrayList<>();
            outLine.accept("$ " + spec.strCommandLine());
            outLine.accept("  in " + spec.dirProject());
            nExit = nAttempt(spec, outLine, lstLine);
            if (nExit == 0 || nExit == N_EXIT_CANCELLED || nExit == N_EXIT_TIMEOUT)
                return nExit;
            if (!flagReadiness(lstLine))
                return nExit;
            if (cntTry == N_TRY)
                break;

            // A PARTICIPANT THAT IS UP BUT NOT READY. Its port answers before
            // its synchronizer connection is usable, so this is a wait rather
            // than a failure - and it is only ever this wait, because only
            // these two strings reach here.
            outLine.accept("the participant is not ready for a script yet; attempt " + cntTry
                    + " of " + N_TRY + ", waiting " + (N_MS_RETRY / 1000) + " s");
            try {
                Thread.sleep(N_MS_RETRY);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return N_EXIT_CANCELLED;
            }
        }
        return nExit;
    }


    /**
     * @param spec what is about to run
     * @throws IllegalStateException naming the thing that is missing
     */
    private static void requireUsable(DamlScriptSpec spec) {
        if (!Files.isDirectory(spec.dirProject())) {
            throw new IllegalStateException("not a directory: " + spec.dirProject()
                    + "\nThe project directory is what selects the SDK; it must hold a "
                    + DamlScriptSpec.STR_FILE_YAML + ".");
        }
        if (!Files.isRegularFile(spec.fileYaml())) {
            throw new IllegalStateException("no " + DamlScriptSpec.STR_FILE_YAML + " in "
                    + spec.dirProject()
                    + "\nWithout one the assistant runs whichever SDK is the default, which is"
                    + " not necessarily this Canton's.");
        }
        if (!Files.isRegularFile(spec.fileDar()))
            throw new IllegalStateException("no such DAR: " + spec.fileDar());
    }


    /**
     * @param spec what to run
     * @param outLine where the output goes
     * @param lstLine filled with the same lines, for the readiness test
     * @return the exit code, or one of the two negative constants
     */
    private int nAttempt(DamlScriptSpec spec, Consumer<String> outLine, List<String> lstLine) {
        // RESOLVED, for the reason ToolchainLauncher states: on Windows `dpm`
        // is a `.cmd` and a bare program name never finds one.
        List<String> lstCommand = new ArrayList<>(spec.lstCommand());
        lstCommand.set(0, ToolchainLauncher.strLauncher(lstCommand.get(0)));
        ProcessBuilder builder = new ProcessBuilder(lstCommand);
        builder.directory(spec.dirProject().toFile());
        // MERGED. The assistant writes its progress to one stream and its
        // errors to the other, and two panes' worth of interleaving would cost
        // the reader the order the lines actually happened in.
        builder.redirectErrorStream(true);

        Process procHere;
        try {
            procHere = builder.start();
        }
        catch (IOException ex) {
            throw new IllegalStateException("could not run `" + lstCommand.get(0)
                    + "`: " + ex.getMessage()
                    + "\nThat executable has to be on PATH for this tab. It is what resolves"
                    + " the SDK named in " + DamlScriptSpec.STR_FILE_YAML + ".", ex);
        }
        this.process = procHere;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                procHere.getInputStream(), StandardCharsets.UTF_8))) {
            String strLine = reader.readLine();
            while (strLine != null) {
                lstLine.add(strLine);
                outLine.accept(strLine);
                strLine = reader.readLine();
            }
        }
        catch (IOException ex) {
            // The stream broke, which on this path means the process was
            // killed under it. The exit code below is still the answer.
            outLine.accept("output ended: " + ex.getMessage());
        }

        boolean flagDone;
        try {
            flagDone = procHere.waitFor(spec.nSecondsTimeout(), TimeUnit.SECONDS);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            procHere.destroyForcibly();
            return N_EXIT_CANCELLED;
        }
        finally {
            this.process = null;
        }

        if (flagCancelled)
            return N_EXIT_CANCELLED;
        if (!flagDone) {
            outLine.accept("timed out after " + spec.nSecondsTimeout() + " s; killed");
            procHere.destroyForcibly();
            return N_EXIT_TIMEOUT;
        }
        return procHere.exitValue();
    }


    /**
     * @param lstLine what the attempt printed
     * @return whether it failed because the participant was not ready yet
     */
    static boolean flagReadiness(List<String> lstLine) {
        for (String strLine : lstLine) {
            for (String strMarker : LST_READINESS) {
                if (strLine.contains(strMarker))
                    return true;
            }
        }
        return false;
    }
}
