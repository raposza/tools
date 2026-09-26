// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.DpmBundles;
import com.raposza.canton.install.ToolchainRoots;
import com.raposza.canton.install.VersionId;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.nio.file.Files;

/**
 * `--fixture` on the released headless Sandbox: the Aviation fixture built,
 * uploaded and run without a window.
 *
 * <h2>It is a switch this application scans for, NOT a field of
 * {@link SandboxOptions}</h2>
 *
 * The same shape `--cli` has in {@link SandboxApp}. The option record describes
 * the STACK - which Canton, which ports, which DARs - and whether test data is
 * put on the ledger afterwards is not one of those. The parser refuses an
 * argument it does not know, so the switch is stripped before the line reaches
 * it.
 *
 * <h2>The DAR reaches the ledger by ONE route</h2>
 *
 * The build produces it in the fixture store and this class appends
 * `--dar &lt;that file&gt;` to the command line, so it is uploaded by the same
 * path every other DAR takes. No spec carries `--upload-dar`: a package that
 * could arrive by two routes is a package nobody can say which route put
 * there.
 *
 * <h2>The build happens BEFORE the stack starts</h2>
 *
 * The upload is part of starting, so a DAR that appears afterwards is a DAR
 * nothing uploads. That ordering is also why a machine whose toolchain cannot
 * build for the selected Canton is refused here rather than after a start that
 * would then hold an empty ledger.
 *
 * <h2>AUTH IS OFF ON THIS PATH</h2>
 *
 * The released headless Sandbox has no auth switch, so the participant checks
 * nothing and `Main:setup` composes both halves - {@link AviationRun#lstSpec}
 * returns the one spec. The two-phase form exists for the authenticated window
 * and is not reached from here.
 *
 * Author Claude/bentzn
 */
public final class FixtureStart {

    /** The switch, stripped before {@link SandboxOptions#parse} sees the line. */
    public static final String STR_ARG = "--fixture";

    /** What `--dar` is called, so the appended pair is written in one place. */
    public static final String STR_ARG_DAR = "--dar";

    /** Said when the machine cannot build for the Canton that was selected. */
    public static final String STR_WHY_TOOLCHAIN =
            "no toolchain on this machine builds for that Canton version";


    private FixtureStart() {
    }


    /**
     * @param arrArg the command line, may be null
     * @return whether the fixture was asked for
     */
    public static boolean flagIn(String[] arrArg) {
        if (arrArg == null)
            return false;

        for (String strArg : arrArg) {
            if (STR_ARG.equals(strArg))
                return true;
        }
        return false;
    }


    /**
     * @param arrArg the command line, may be null
     * @return the same line with every `--fixture` removed; never null
     */
    public static String[] without(String[] arrArg) {
        if (arrArg == null)
            return new String[0];

        List<String> lstArg = new ArrayList<>();
        for (String strArg : arrArg) {
            if (!STR_ARG.equals(strArg))
                lstArg.add(strArg);
        }
        return lstArg.toArray(new String[0]);
    }


    /**
     * @param arrArg the command line, may be null
     * @param fileDar what the build produced
     * @return the line with `--dar &lt;file&gt;` appended; never null
     */
    public static String[] withDar(String[] arrArg, Path fileDar) {
        if (fileDar == null)
            throw new IllegalArgumentException("a dar is required");

        List<String> lstArg = new ArrayList<>();
        if (arrArg != null) {
            for (String strArg : arrArg) {
                lstArg.add(strArg);
            }
        }
        lstArg.add(STR_ARG_DAR);
        lstArg.add(fileDar.toString());
        return lstArg.toArray(new String[0]);
    }


    /**
     * What `daml.yaml` will name for an installation, or null when this
     * machine has nothing that builds for it.
     *
     * @param inst the Canton that was selected
     * @return the SDK version, or null
     */
    public static String strSdkFor(CantonInstallation inst) {
        if (inst == null)
            return null;
        return AviationFixture.strSdkFor(inst.version().toString(),
                DpmBundles.mapBundle(DpmBundles.dirSdk(ToolchainRoots.ofDefaults())));
    }


    /**
     * Stages the project out of the jar and builds it.
     *
     * REGENERATED EVERY TIME, which is {@link AviationFixture#stage}'s own
     * rule: a store whose DARs came from three revisions of the source still
     * looks like a store.
     *
     * @param dirProject where the project is staged
     * @param strSdk what `daml.yaml` names
     * @param outLine where the compiler's output goes
     * @return the DAR, or null when the build did not produce one
     * @throws IOException when the project cannot be written
     */
    public static Path fileBuild(Path dirProject, String strSdk, Consumer<String> outLine)
            throws IOException {
        AviationFixture.stage(dirProject, strSdk);

        int nExit = AviationRun.nBuild(dirProject, strSdk, outLine);
        if (nExit != 0) {
            outLine.accept("the Aviation build exited " + nExit);
            return null;
        }
        return AviationFixture.fileDar(dirProject);
    }


    /**
     * Where the project for one installation is staged.
     *
     * @param inst the Canton that was selected
     * @return its staging project under the fixture store
     */
    public static Path dirProjectFor(CantonInstallation inst) {
        if (inst == null)
            throw new IllegalArgumentException("an installation is required");
        // THE KEY IS `VersionKey`'S AND NOT A SECOND COPY OF THE RULE. It
        // sits in the GUI package and carries no Swing: naming it here costs
        // nothing and a store keyed two ways would be two stores.
        return AviationFixture.dirProject(AviationFixture.dirRootDefault(),
                com.raposza.sandbox.gui.VersionKey.strOf(inst));
    }


    /**
     * Runs what the fixture needs against a ledger that is already up.
     *
     * @param dirProject the staged project, whose `daml.yaml` selects the SDK
     * @param fileDar the DAR the scripts live in
     * @param nPortLedger the Ledger API port
     * @param outLine where each script's output goes
     * @return 0 when every phase exited zero, otherwise the first non-zero
     *         exit
     */
    public static int nRun(Path dirProject, Path fileDar, int nPortLedger,
            Consumer<String> outLine) {
        return nRun(dirProject, fileDar, nPortLedger, null, null, outLine);
    }


    /**
     * The same, against a participant that verifies tokens.
     *
     * <b>AN AUTHENTICATED RUN IS TWO SCRIPTS, NOT ONE - D-548.</b> A token
     * carries `actAs` for the parties that existed when it was minted, so
     * `Main:setupParties` allocates them under `participant_admin` and writes
     * the ids out, and `Main:setupLedger` submits under `superuser` reading
     * them back in. The unauthenticated path stays one script and is what
     * `Main:setup` is for.
     *
     * @param dirProject the staged project, whose `daml.yaml` selects the SDK
     * @param fileDar the DAR the scripts live in
     * @param nPortLedger the Ledger API port
     * @param auth what the participant checks, or null for none
     * @param version the Canton running, which caps the token lifetime
     * @param outLine where each script's output goes
     * @return 0 when every phase exited zero, otherwise the first non-zero
     *         exit
     */
    public static int nRun(Path dirProject, Path fileDar, int nPortLedger,
            AuthSettings auth, VersionId version, Consumer<String> outLine) {
        boolean flagAuth = auth != null && auth.mode() != AuthSettings.Mode.NONE;
        Path fileTokenAdmin = null;
        Path fileTokenSuper = null;
        Path fileParties = null;

        try {
            if (flagAuth) {
                fileTokenAdmin = AuthStart.fileTokenFor(auth, version,
                        AviationRun.STR_USER_ADMIN);
                fileTokenSuper = AuthStart.fileTokenFor(auth, version,
                        AviationRun.STR_USER_SUPER);
                fileParties = Files.createTempFile("aviation-parties", ".json");
            }

            List<DamlScriptSpec> lstSpec = AviationRun.lstSpec(dirProject, fileDar, nPortLedger,
                    flagAuth, fileTokenAdmin, fileTokenSuper, fileParties);

            for (DamlScriptSpec spec : lstSpec) {
                outLine.accept("running " + spec.strName() + " against port " + nPortLedger);
                int nExit = new DamlScriptRun().nRun(spec, outLine);
                if (nExit != 0) {
                    outLine.accept("Aviation " + spec.strName() + " exited " + nExit);
                    return nExit;
                }
            }
            return 0;
        }
        catch (IOException ex) {
            outLine.accept("the fixture could not be given a credential: " + ex.getMessage());
            return N_EXIT_TOKEN;
        }
        finally {
            deleteQuietly(fileTokenAdmin);
            deleteQuietly(fileTokenSuper);
            deleteQuietly(fileParties);
        }
    }


    /** What {@link #nRun} returns when the mint would not give it a token. */
    public static final int N_EXIT_TOKEN = 4;


    private static void deleteQuietly(Path file) {
        if (file == null)
            return;
        try {
            Files.deleteIfExists(file);
        }
        catch (IOException ex) {
            // A temporary file left behind is not a reason to fail a run that
            // otherwise worked, and the directory is the OS's to clear.
        }
    }

}
