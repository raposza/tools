// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.canton.install.ToolchainLauncher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Building the Aviation fixture and saying what has to be run against the ledger
 * afterwards.
 *
 * <h2>Three steps, and the third is a LIST rather than an action</h2>
 *
 * Build the DAR, put it where the next start uploads from, and describe the
 * script runs. The runs are {@link DamlScriptRun}'s to execute and the window's
 * to schedule; what is decided here is WHAT runs, which is the half that
 * changes with the auth mode and the half worth asserting without a ledger.
 *
 * <h2>THE AUTH MODE DECIDES ONE SPEC OR TWO</h2>
 *
 * A token cannot carry `actAs` for a party allocated after it was minted, so on
 * an authenticated participant `Main:setupParties` runs under
 * `participant_admin` and writes the ids with `--output-file`, and
 * `Main:setupLedger` runs under `superuser` with `--input-file`. Unauthenticated,
 * `Main:setup` composes both and there is nothing to hand between them.
 *
 * <h2>The DAR reaches the ledger by ONE route</h2>
 *
 * It is copied into the run directory's `dars` and uploaded when Start is
 * pressed, which is what the DARs tab already does for everything else. No spec
 * here carries `--upload-dar`, because a package that could arrive by two
 * routes is a package nobody can say which route put there.
 *
 * <h2>And the copy REPLACES</h2>
 *
 * Every build of this fixture declares the same package name and version with
 * a different package id, and two of them in one directory are two revisions
 * of one package to a 3.x participant. The upload is then rejected at the far
 * end of a start.
 *
 * Author Claude/bentzn
 */
public final class AviationRun {

    /** The user phase one runs as. Canton creates it; the fixture does not. */
    public static final String STR_USER_ADMIN = DamlScriptSpec.STR_USER_ID_DEFAULT;

    /** The user phase two runs as. `Main:setupParties` creates it. */
    public static final String STR_USER_SUPER = AviationFixture.STR_USER_SUPER;

    /** What phase one writes and phase two reads. */
    public static final String STR_FILE_PARTIES = "parties.json";

    /** A build is a compiler, not a ledger: minutes at worst, and bounded. */
    public static final int N_SECONDS_BUILD = 900;

    private static final int N_MS_KILL = 3000;

    private static final String STR_GLOB_DAR = AviationFixture.STR_NAME + "*.dar";


    private AviationRun() {
    }


    /**
     * Runs the compiler in the staged project.
     *
     * BLOCKING, and never called on the event dispatch thread: a first build
     * resolves `daml-script` off the network and takes minutes.
     *
     * @param dirProject the staged project; its `daml.yaml` selects the SDK
     * @param strSdk what that file names, which also picks the executable
     * @param outLine told every line the compiler wrote; never null
     * @return the exit code
     * @throws IOException when the executable is not on PATH
     */
    public static int nBuild(Path dirProject, String strSdk, Consumer<String> outLine)
            throws IOException {
        if (dirProject == null)
            throw new IllegalArgumentException("a project directory is required");
        if (outLine == null)
            throw new IllegalArgumentException("a line sink is required");

        // THE LAUNCHER IS RESOLVED rather than assumed to be on PATH. On
        // Windows both toolchains are `.cmd` files and a bare program name
        // never finds one - ToolchainLauncher says why. The line printed below
        // is what actually ran, so a resolved path is visible in the log.
        List<String> lstCommand = new ArrayList<>(AviationFixture.lstBuild(strSdk));
        lstCommand.set(0, ToolchainLauncher.strLauncher(lstCommand.get(0)));
        outLine.accept("$ " + String.join(" ", lstCommand));
        outLine.accept("  in " + dirProject);

        ProcessBuilder builder = new ProcessBuilder(lstCommand);
        builder.directory(dirProject.toFile());
        // MERGED, as DamlScriptRun merges them: the compiler writes progress to
        // one stream and diagnostics to the other, and interleaving them by
        // hand costs the reader the order they happened in.
        builder.redirectErrorStream(true);

        Process procHere;
        try {
            procHere = builder.start();
        }
        catch (IOException ex) {
            throw new IOException("could not run `" + lstCommand.get(0) + "`: " + ex.getMessage()
                    + "\nThat executable has to be on PATH to build the fixture.", ex);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                procHere.getInputStream(), StandardCharsets.UTF_8))) {
            String strLine = reader.readLine();
            while (strLine != null) {
                outLine.accept(strLine);
                strLine = reader.readLine();
            }
        }
        catch (IOException ex) {
            outLine.accept("output ended: " + ex.getMessage());
        }

        try {
            if (procHere.waitFor(N_SECONDS_BUILD, TimeUnit.SECONDS))
                return procHere.exitValue();
            outLine.accept("the build ran past " + N_SECONDS_BUILD + " s; killed");
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        procHere.destroy();
        try {
            if (!procHere.waitFor(N_MS_KILL, TimeUnit.MILLISECONDS))
                procHere.destroyForcibly();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            procHere.destroyForcibly();
        }
        return DamlScriptRun.N_EXIT_TIMEOUT;
    }


    /**
     * Stages the built DAR where the next start uploads from, replacing any
     * earlier build of it.
     *
     * @param fileDar what the compiler produced
     * @param dirDars the run directory's `dars`; created if absent
     * @param outLine told what was removed and what was written; never null
     * @return where the DAR now is
     * @throws IOException when the directory cannot be written
     */
    public static Path filePlace(Path fileDar, Path dirDars, Consumer<String> outLine)
            throws IOException {
        if (fileDar == null || dirDars == null)
            throw new IllegalArgumentException("a DAR and a directory are required");
        if (outLine == null)
            throw new IllegalArgumentException("a line sink is required");
        if (!Files.isRegularFile(fileDar))
            throw new IOException("no such DAR: " + fileDar);

        Files.createDirectories(dirDars);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirDars, STR_GLOB_DAR)) {
            for (Path fileOld : stream) {
                if (!Files.isRegularFile(fileOld))
                    continue;
                Files.delete(fileOld);
                outLine.accept("removed " + fileOld.getFileName());
            }
        }

        Path fileOut = dirDars.resolve(fileDar.getFileName().toString());
        Files.copy(fileDar, fileOut, StandardCopyOption.REPLACE_EXISTING);
        outLine.accept("staged " + fileOut);
        return fileOut;
    }


    /**
     * What has to run against the ledger, in order.
     *
     * @param dirProject the staged project
     * @param fileDar the DAR the scripts live in - the one in the store, not
     *        the copy in `dars`: they are the same bytes and the store's is the
     *        one that cannot be deleted by a tab
     * @param nPortLedger the Ledger API port
     * @param flagAuth whether the participant checks tokens
     * @param fileTokenAdmin the `participant_admin` bearer, or null with auth
     *        off
     * @param fileTokenSuper the `superuser` bearer, or null with auth off
     * @param fileParties where phase one writes the ids and phase two reads
     *        them, or null with auth off
     * @return one spec unauthenticated, two authenticated; never null
     */
    public static List<DamlScriptSpec> lstSpec(Path dirProject, Path fileDar, int nPortLedger,
            boolean flagAuth, Path fileTokenAdmin, Path fileTokenSuper, Path fileParties) {
        DamlScriptSpec specBase = DamlScriptSpec.of(dirProject, fileDar,
                AviationFixture.STR_SCRIPT_SETUP, nPortLedger);
        if (!flagAuth)
            return List.of(specBase);

        if (fileTokenAdmin == null || fileTokenSuper == null || fileParties == null) {
            throw new IllegalArgumentException("an authenticated run needs both tokens and a"
                    + " file to hand the party ids over in");
        }

        List<DamlScriptSpec> lstOut = new ArrayList<>();
        lstOut.add(specBase.withName(AviationFixture.STR_SCRIPT_PARTIES)
                .withToken(fileTokenAdmin)
                .withUserId(STR_USER_ADMIN)
                .withOutput(fileParties));
        lstOut.add(specBase.withName(AviationFixture.STR_SCRIPT_LEDGER)
                .withToken(fileTokenSuper)
                .withUserId(STR_USER_SUPER)
                .withInput(fileParties));
        return List.copyOf(lstOut);
    }

}
