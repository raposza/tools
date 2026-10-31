// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.InstallSource;
import com.raposza.canton.install.ToolchainLauncher;
import com.raposza.canton.install.ToolchainRoots;
import com.raposza.runtime.localnet.SpliceInstallations;
import com.raposza.runtime.settings.RaposzaSettings;
import com.raposza.sandbox.app.SandboxService;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What this machine has, and what it needs before the Sandbox can start
 * anything - R1, his handover of 2026-10-30.
 *
 * <h2>His list, 2026-10-30</h2>
 *
 * "Does it have DPM installed? Alternative location? Does it have DAML
 * Assistant installed? Alternative location? Does it have versions of DAML
 * 2.x/early 3.x installed? Help the user set up the Sandbox so that at least
 * something can be tested. If no DPM, suggest downloading. If no download,
 * suggest install from file. If no Splice version, suggest downloading. If no
 * download, suggest install from file."
 *
 * Download-then-file is already how both installs behave - a DPM download that
 * fails offers the archive from file, and Install Splice carries both - so the
 * check offers the installs as they are and adds DPM from file directly, for
 * the machine that is known to have no internet.
 *
 * <h2>Two halves</h2>
 *
 * {@link #scan} reads the disk and is called OFF the event thread - his
 * instruction of 2026-10-30, "I do not want the UI to EVER freeze."
 * {@link #reportOf} turns what was read into words and buttons, touches
 * nothing, and is what the tests hold.
 *
 * <h2>An alternative location is only reported, never adopted silently</h2>
 *
 * A root elsewhere that looks like one - `sdk` for the assistant, `cache` for
 * DPM, a staged `splice-node` for Splice - is listed. It is offered for use
 * only where the one in use has nothing of its kind, since adopting it would
 * otherwise hide what the machine already runs.
 *
 * <h2>And one more thing that holds a first start up</h2>
 *
 * The Sandbox's Canton is started with the `java` on PATH, not this JVM's -
 * `CantonSandboxProcess`. A machine whose Java is not on PATH runs this window
 * and then cannot start a participant, so the check says so.
 *
 * Author Claude/bentzn
 */
final class MachineCheck {

    /** Which toolchain a directory belongs to. */
    enum Kind {

        DAML("DAML Assistant"),

        DPM("DPM"),

        SPLICE("Splice");

        private final String strLabel;


        Kind(String strLabelNew) {
            this.strLabel = strLabelNew;
        }


        String strLabel() {
            return strLabel;
        }
    }


    /** What the reader can do from the check. */
    enum Action {

        INSTALL_DPM("Download DPM"),

        INSTALL_DPM_FILE("Install DPM from file..."),

        INSTALL_SPLICE("Install Splice..."),

        USE_FOUND("Use the one found"),

        CLOSE("Close");

        private final String strLabel;


        Action(String strLabelNew) {
            this.strLabel = strLabelNew;
        }


        String strLabel() {
            return strLabel;
        }
    }


    /**
     * A root somewhere other than where this window reads.
     *
     * @param kind which toolchain
     * @param dir the directory
     * @param lstSplice for Splice, the versions staged under it; empty otherwise
     */
    record Found(Kind kind, Path dir, List<String> lstSplice) {
    }


    /**
     * What {@link #scan} read.
     *
     * @param roots the roots in force
     * @param strFromDpm where the DPM root comes from, in words
     * @param strFromDaml where the assistant root comes from, in words
     * @param lstCanton every Canton found under those roots
     * @param dirSplice where Splice is read from
     * @param lstSplice the versions staged there, newest first
     * @param mapChoice for each of them, the Canton a start would run on
     * @param lstFound roots elsewhere
     * @param fileJava the `java` on PATH, or null
     */
    record Facts(ToolchainRoots roots, String strFromDpm, String strFromDaml,
            List<CantonInstallation> lstCanton, Path dirSplice, List<String> lstSplice,
            Map<String, SpliceCanton.Choice> mapChoice, List<Found> lstFound, Path fileJava) {
    }


    /**
     * What the dialog says and offers.
     *
     * @param strText the report
     * @param lstAction the buttons, in order, ending with {@link Action#CLOSE}
     * @param foundOffered what {@link Action#USE_FOUND} would use, or null
     * @param flagReady whether this window can start something as it stands
     */
    record Report(String strText, List<Action> lstAction, Found foundOffered,
            boolean flagReady) {
    }


    static final String STR_FROM_SETTINGS = "set in Settings";

    private static final String STR_NAME_JAVA = "java";

    private static final String STR_ENV_DAML_HOME = "DAML_HOME";

    private static final String STR_DIR_SPLICE_WINDOWS = "splice";

    private static final String STR_INDENT = "   ";


    private MachineCheck() {
    }


    /**
     * Reads the machine. NOT ON THE EVENT THREAD.
     *
     * @return what is there
     */
    static Facts scan() {
        RaposzaSettings settings = RaposzaSettings.current();
        ToolchainRoots roots = rootsResolved(ToolchainRoots.ofDefaults(),
                ToolchainRoots.flagWindowsHere());
        String strPath = System.getenv("PATH");
        Map<String, String> mapEnv = System.getenv();
        boolean flagWindows = ToolchainRoots.flagWindowsHere();
        Path dirUser = Path.of(System.getProperty("user.home", "."));

        String strFromDpm = settings.dirDpm() != null ? STR_FROM_SETTINGS
                : ToolchainRoots.strFromDpm(strPath, mapEnv, flagWindows);
        String strFromDaml = settings.dirDaml() != null ? STR_FROM_SETTINGS
                : ToolchainRoots.strFromDaml(strPath, flagWindows);

        List<CantonInstallation> lstCanton = SandboxService.rescanCanton();
        Path dirSplice = SpliceInstallations.dirRoot();
        List<String> lstSplice = SpliceInstallations.lstVersion();
        Map<String, SpliceCanton.Choice> mapChoice = new LinkedHashMap<>();
        for (String strVersion : lstSplice) {
            mapChoice.put(strVersion, SpliceCanton.choose(strVersion,
                    SpliceInstallations.versionsOf(strVersion), lstCanton));
        }

        // THE PLACES A ROOT IS KNOWN TO BE KEPT, whichever of them is in use.
        ToolchainRoots rootsBare = ToolchainRoots.of("", mapAppDataOnly(mapEnv), flagWindows,
                dirUser);
        ToolchainRoots rootsUnset = ToolchainRoots.ofUnset();
        List<Found> lstFound = new ArrayList<>();
        lstFound.addAll(lstFound(Kind.DAML, Arrays.asList(rootsBare.dirDaml(),
                rootsUnset.dirDaml(), dirOrNull(mapEnv.get(STR_ENV_DAML_HOME))),
                roots.dirDaml()));
        lstFound.addAll(lstFound(Kind.DPM, Arrays.asList(rootsBare.dirDpm(),
                rootsUnset.dirDpm(), dirOrNull(mapEnv.get(ToolchainRoots.STR_ENV_DPM_HOME))),
                roots.dirDpm()));
        List<Path> lstSpliceAt = new ArrayList<>();
        lstSpliceAt.add(SpliceInstallations.dirRootDefault());
        if (flagWindows) {
            // MEASURED ON THE WINVM, 2026-10-30: a Splice provisioned by hand
            // under %APPDATA%\splice, which this window does not read.
            Path dirAppData = dirOrNull(mapEnv.get(ToolchainRoots.STR_ENV_APPDATA));
            if (dirAppData != null)
                lstSpliceAt.add(dirAppData.resolve(STR_DIR_SPLICE_WINDOWS));
        }
        lstFound.addAll(lstFound(Kind.SPLICE, lstSpliceAt, dirSplice));

        Path fileJava = ToolchainRoots.fileOnPathOf(strPath, STR_NAME_JAVA, flagWindows);
        return new Facts(roots, strFromDpm, strFromDaml, lstCanton, dirSplice, lstSplice,
                mapChoice, lstFound, fileJava);
    }


    /**
     * THE LAUNCHER WHERE THE WINDOW RUNS IT FROM - on PATH, or under its
     * root's `bin` - `todo.md` A-75, {@link ToolchainLauncher}. MEASURED ON THE
     * WINVM, 2026-10-31: DPM installed by the window, from the download and
     * from file, lands in `%APPDATA%\dpm\bin`, which is not on this JVM's
     * PATH; {@link ToolchainRoots#flagDpm()} answered false, the check said
     * "launcher: not found" and offered DPM a second time.
     *
     * @param rootsRead the roots as {@link ToolchainRoots} read them
     * @param flagWindows which launcher extensions to try
     * @return the same roots, each launcher resolved the way the window runs it
     */
    static ToolchainRoots rootsResolved(ToolchainRoots rootsRead, boolean flagWindows) {
        return new ToolchainRoots(rootsRead.dirDaml(), rootsRead.dirDpm(),
                fileLauncher(rootsRead, ToolchainRoots.STR_NAME_DAML, flagWindows),
                fileLauncher(rootsRead, ToolchainRoots.STR_NAME_DPM, flagWindows));
    }


    /**
     * @param rootsRead the roots as read
     * @param strName `daml` or `dpm`
     * @param flagWindows which launcher extensions to try
     * @return the launcher, or null when there is none on this machine
     */
    private static Path fileLauncher(ToolchainRoots rootsRead, String strName,
            boolean flagWindows) {
        String strHere = ToolchainLauncher.strLauncher(strName, rootsRead, flagWindows);
        return strHere == null || strHere.equals(strName) ? null : Path.of(strHere);
    }


    /**
     * @param kind which toolchain
     * @param lstCandidate directories to look at; nulls and repeats are skipped
     * @param dirInUse the one this window reads, which is not reported again
     * @return those that look like a root of that kind
     */
    static List<Found> lstFound(Kind kind, List<Path> lstCandidate, Path dirInUse) {
        Set<Path> setSeen = new LinkedHashSet<>();
        if (dirInUse != null)
            setSeen.add(dirInUse.toAbsolutePath().normalize());
        List<Found> lstOut = new ArrayList<>();
        for (Path dir : lstCandidate) {
            if (dir == null || !setSeen.add(dir.toAbsolutePath().normalize()))
                continue;
            switch (kind) {
                case DAML -> {
                    if (Files.isDirectory(dir.resolve(ToolchainRoots.STR_MARKER_DAML)))
                        lstOut.add(new Found(kind, dir, List.of()));
                }
                case DPM -> {
                    if (Files.isDirectory(dir.resolve(ToolchainRoots.STR_MARKER_DPM)))
                        lstOut.add(new Found(kind, dir, List.of()));
                }
                case SPLICE -> {
                    List<String> lstVersion = SpliceInstallations.lstVersionIn(dir);
                    if (!lstVersion.isEmpty())
                        lstOut.add(new Found(kind, dir, lstVersion));
                }
            }
        }
        return lstOut;
    }


    /**
     * @param facts what {@link #scan} read
     * @param flagLocalNet whether this is the LocalNetND window
     * @return the report and its buttons
     */
    static Report reportOf(Facts facts, boolean flagLocalNet) {
        StringBuilder sb = new StringBuilder();
        ToolchainRoots roots = facts.roots();
        List<CantonInstallation> lstDpm = lstOfSource(facts.lstCanton(), InstallSource.DPM);
        List<CantonInstallation> lstDaml = lstOfSource(facts.lstCanton(),
                InstallSource.DAML_ASSISTANT);

        sb.append("DPM\n");
        sb.append(STR_INDENT).append("launcher: ").append(roots.flagDpm()
                ? roots.fileDpm().toString() : "not found").append('\n');
        sb.append(STR_INDENT).append("directory: ").append(roots.dirDpm()).append(" (")
                .append(facts.strFromDpm()).append(")\n");
        boolean flagDpmEmpty = roots.flagDpm() && lstDpm.isEmpty();
        sb.append(STR_INDENT).append("SDKs: ").append(lstDpm.isEmpty()
                ? (roots.flagDpm() ? "none - DPM is there but holds no SDK" : "none")
                : strVersions(lstDpm)).append('\n');

        sb.append("DAML Assistant\n");
        sb.append(STR_INDENT).append("launcher: ").append(roots.flagDaml()
                ? roots.fileDaml().toString() : "not found").append('\n');
        sb.append(STR_INDENT).append("directory: ").append(roots.dirDaml()).append(" (")
                .append(facts.strFromDaml()).append(")\n");
        sb.append(STR_INDENT).append("SDKs: ").append(lstDaml.isEmpty() ? "none"
                : strVersions(lstDaml)).append('\n');

        sb.append("Splice\n");
        sb.append(STR_INDENT).append("directory: ").append(facts.dirSplice()).append('\n');
        boolean flagSpliceRunnable = false;
        boolean flagSpliceStartable = false;
        if (facts.lstSplice().isEmpty())
            sb.append(STR_INDENT).append("versions: none\n");
        for (String strVersion : facts.lstSplice()) {
            SpliceCanton.Choice choice = facts.mapChoice().get(strVersion);
            sb.append(STR_INDENT).append(strVersion).append(": ");
            if (SpliceCanton.isBelowFloor(strVersion)) {
                sb.append("older than ").append(SpliceCanton.STR_SPLICE_MIN)
                        .append(" - LocalNetND does not run it\n");
                continue;
            }
            flagSpliceRunnable = true;
            if (choice != null && choice.inst() != null) {
                flagSpliceStartable = true;
                sb.append("starts on Canton ").append(choice.inst().version()).append('\n');
            }
            else if (choice != null && choice.strLineNeeded() != null) {
                sb.append("needs a Canton ").append(choice.strLineNeeded())
                        .append(" - offered when it starts\n");
            }
            else {
                sb.append(choice == null || choice.strRefusal() == null ? "cannot start"
                        : choice.strRefusal()).append('\n');
            }
        }

        Found foundOffered = null;
        if (!facts.lstFound().isEmpty()) {
            sb.append("Also on this machine, not in use\n");
            for (Found found : facts.lstFound()) {
                sb.append(STR_INDENT).append(found.kind().strLabel()).append(": ")
                        .append(found.dir());
                if (found.kind() == Kind.SPLICE)
                    sb.append(" - ").append(strSplice(found.lstSplice()));
                sb.append('\n');
                if (foundOffered == null && isWorthOffering(found, lstDpm, lstDaml,
                        flagSpliceRunnable, flagLocalNet))
                    foundOffered = found;
            }
        }

        sb.append("Java\n");
        sb.append(STR_INDENT).append("java on PATH: ").append(facts.fileJava() == null
                ? "none - the Sandbox starts its participant with the java on PATH"
                : facts.fileJava().toString()).append('\n');

        boolean flagSandbox = facts.fileJava() != null && hasRuntime(facts.lstCanton());
        sb.append("\nCan start now\n");
        sb.append(STR_INDENT).append("Sandbox: ").append(flagSandbox ? "yes"
                : !hasRuntime(facts.lstCanton()) ? "no - no Canton is installed"
                : "no - there is no java on PATH").append('\n');
        sb.append(STR_INDENT).append("LocalNetND: ").append(flagSpliceStartable ? "yes"
                : !flagSpliceRunnable ? "no - no Splice " + SpliceCanton.STR_SPLICE_MIN
                        + " or later is installed"
                : "no - its Canton is offered when it starts").append('\n');

        List<Action> lstAction = new ArrayList<>();
        if (!roots.flagDpm() || flagDpmEmpty) {
            lstAction.add(Action.INSTALL_DPM);
            lstAction.add(Action.INSTALL_DPM_FILE);
        }
        if (flagLocalNet && !flagSpliceRunnable)
            lstAction.add(Action.INSTALL_SPLICE);
        if (foundOffered != null)
            lstAction.add(Action.USE_FOUND);
        lstAction.add(Action.CLOSE);

        return new Report(sb.toString(), Collections.unmodifiableList(lstAction), foundOffered,
                flagLocalNet ? flagSpliceStartable : flagSandbox);
    }


    /**
     * ONLY WHERE THE ONE IN USE HAS NOTHING OF ITS KIND - see the type
     * comment.
     */
    private static boolean isWorthOffering(Found found, List<CantonInstallation> lstDpm,
            List<CantonInstallation> lstDaml, boolean flagSpliceRunnable, boolean flagLocalNet) {
        return switch (found.kind()) {
            case DPM -> lstDpm.isEmpty();
            case DAML -> lstDaml.isEmpty();
            case SPLICE -> flagLocalNet && !flagSpliceRunnable
                    && !SpliceCanton.lstFromFloor(found.lstSplice()).isEmpty();
        };
    }


    private static List<CantonInstallation> lstOfSource(List<CantonInstallation> lstCanton,
            InstallSource source) {
        List<CantonInstallation> lstOut = new ArrayList<>();
        for (CantonInstallation inst : lstCanton == null ? List.<CantonInstallation>of()
                : lstCanton) {
            if (inst.source() == source)
                lstOut.add(inst);
        }
        return lstOut;
    }


    private static boolean hasRuntime(List<CantonInstallation> lstCanton) {
        for (CantonInstallation inst : lstCanton == null ? List.<CantonInstallation>of()
                : lstCanton) {
            if (inst.hasRuntime())
                return true;
        }
        return false;
    }


    /**
     * @param lstInst installations, newest first
     * @return their versions, once each, with those that cannot start marked
     */
    private static String strVersions(List<CantonInstallation> lstInst) {
        Set<String> setOut = new LinkedHashSet<>();
        for (CantonInstallation inst : lstInst) {
            setOut.add(inst.version() + (inst.hasRuntime() ? "" : " (no runtime)"));
        }
        return String.join(", ", setOut);
    }


    /**
     * @param lstVersion Splice versions
     * @return them, with those LocalNetND does not run marked
     */
    private static String strSplice(List<String> lstVersion) {
        List<String> lstOut = new ArrayList<>();
        for (String strVersion : lstVersion) {
            lstOut.add(SpliceCanton.isBelowFloor(strVersion)
                    ? strVersion + " (older than " + SpliceCanton.STR_SPLICE_MIN + ")"
                    : strVersion);
        }
        return String.join(", ", lstOut);
    }


    private static Map<String, String> mapAppDataOnly(Map<String, String> mapEnv) {
        Map<String, String> mapOut = new LinkedHashMap<>();
        String strAppData = mapEnv.get(ToolchainRoots.STR_ENV_APPDATA);
        if (strAppData != null)
            mapOut.put(ToolchainRoots.STR_ENV_APPDATA, strAppData);
        return mapOut;
    }


    private static Path dirOrNull(String strDir) {
        if (strDir == null || strDir.isBlank())
            return null;
        try {
            return Path.of(strDir.trim());
        }
        catch (InvalidPathException ex) {
            return null;
        }
    }
}
