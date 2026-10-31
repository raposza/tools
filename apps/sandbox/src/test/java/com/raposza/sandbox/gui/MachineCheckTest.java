// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.Edition;
import com.raposza.canton.install.InstallSource;
import com.raposza.canton.install.ToolchainRoots;
import com.raposza.canton.install.VersionId;
import com.raposza.runtime.localnet.SpliceInstallations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * R1's report and its buttons, from facts made by hand - the machines of his
 * scenarios, 2026-10-30.
 *
 * Author Claude/bentzn
 */
class MachineCheckTest {

    private static final Path DIR = Path.of("/home", "x");

    private static final Path FILE_JAVA = DIR.resolve("jdk/bin/java");

    @TempDir
    Path dirTmp;


    private static CantonInstallation inst(String strVersion, InstallSource source,
            boolean flagRuntime) {
        return new CantonInstallation(VersionId.parse(strVersion), Edition.OPEN_SOURCE, source,
                DIR.resolve(strVersion), flagRuntime ? DIR.resolve(strVersion + ".jar") : null);
    }


    private static ToolchainRoots roots(boolean flagDaml, boolean flagDpm) {
        return new ToolchainRoots(DIR.resolve(".daml"), DIR.resolve(".dpm"),
                flagDaml ? DIR.resolve(".daml/bin/daml") : null,
                flagDpm ? DIR.resolve(".dpm/bin/dpm") : null);
    }


    private static MachineCheck.Facts facts(ToolchainRoots roots,
            List<CantonInstallation> lstCanton, Map<String, SpliceCanton.Choice> mapChoice,
            List<MachineCheck.Found> lstFound, Path fileJava) {
        return new MachineCheck.Facts(roots, ToolchainRoots.STR_FROM_DEFAULT,
                ToolchainRoots.STR_FROM_PATH, lstCanton, DIR.resolve(".splice"),
                new ArrayList<>(mapChoice.keySet()), mapChoice, lstFound, fileJava);
    }


    @Test
    void anEmptyMachineIsOfferedDpmBothWays() {
        MachineCheck.Report report = MachineCheck.reportOf(facts(roots(false, false), List.of(),
                Map.of(), List.of(), FILE_JAVA), false);
        assertEquals(List.of(MachineCheck.Action.INSTALL_DPM,
                MachineCheck.Action.INSTALL_DPM_FILE, MachineCheck.Action.CLOSE),
                report.lstAction());
        assertFalse(report.flagReady());
        assertTrue(report.strText().contains("Sandbox: no - no Canton is installed"),
                report.strText());
    }


    @Test
    void anEmptyLocalNetMachineIsOfferedSpliceToo() {
        MachineCheck.Report report = MachineCheck.reportOf(facts(roots(false, false), List.of(),
                Map.of(), List.of(), FILE_JAVA), true);
        assertTrue(report.lstAction().contains(MachineCheck.Action.INSTALL_SPLICE));
        assertTrue(report.strText().contains("no Splice 0.8 or later"), report.strText());
    }


    @Test
    void dpmWithoutAnSdkIsSaidAndOfferedAgain() {
        MachineCheck.Report report = MachineCheck.reportOf(facts(roots(false, true), List.of(),
                Map.of(), List.of(), FILE_JAVA), false);
        assertTrue(report.strText().contains("holds no SDK"), report.strText());
        assertTrue(report.lstAction().contains(MachineCheck.Action.INSTALL_DPM));
    }


    /** The tester's machine: three Assistant SDKs, DPM with one. */
    @Test
    void aMachineWithBothToolchainsIsReadyAndOfferedNothing() {
        List<CantonInstallation> lstCanton = List.of(inst("3.5.12", InstallSource.DPM, true),
                inst("3.4.11", InstallSource.DAML_ASSISTANT, true),
                inst("2.10.4", InstallSource.DAML_ASSISTANT, true),
                inst("2.9.6", InstallSource.DAML_ASSISTANT, true));
        MachineCheck.Report report = MachineCheck.reportOf(facts(roots(true, true), lstCanton,
                Map.of(), List.of(), FILE_JAVA), false);
        assertEquals(List.of(MachineCheck.Action.CLOSE), report.lstAction());
        assertTrue(report.flagReady());
        assertTrue(report.strText().contains("3.4.11, 2.10.4, 2.9.6"), report.strText());
        assertTrue(report.strText().contains("Sandbox: yes"), report.strText());
    }


    @Test
    void noJavaOnPathIsNotReady() {
        List<CantonInstallation> lstCanton = List.of(inst("3.5.12", InstallSource.DPM, true));
        MachineCheck.Report report = MachineCheck.reportOf(facts(roots(false, true), lstCanton,
                Map.of(), List.of(), null), false);
        assertFalse(report.flagReady());
        assertTrue(report.strText().contains("no java on PATH"), report.strText());
    }


    @Test
    void aSpliceOnItsCantonIsReady() {
        List<CantonInstallation> lstCanton = List.of(inst("3.6.3", InstallSource.DPM, true));
        Map<String, SpliceCanton.Choice> mapChoice = new LinkedHashMap<>();
        mapChoice.put("0.9.4", SpliceCanton.choose("0.9.4",
                new SpliceInstallations.Versions("3.6.4", "3.5.2", "3.5.2"), lstCanton));
        MachineCheck.Report report = MachineCheck.reportOf(facts(roots(false, true), lstCanton,
                mapChoice, List.of(), FILE_JAVA), true);
        assertTrue(report.flagReady());
        assertTrue(report.strText().contains("0.9.4: starts on Canton 3.6.3"), report.strText());
        assertEquals(List.of(MachineCheck.Action.CLOSE), report.lstAction());
    }


    @Test
    void aSpliceWithoutItsCantonSaysItIsOfferedAtStart() {
        List<CantonInstallation> lstCanton = List.of(inst("3.5.12", InstallSource.DPM, true));
        Map<String, SpliceCanton.Choice> mapChoice = new LinkedHashMap<>();
        mapChoice.put("0.9.4", SpliceCanton.choose("0.9.4",
                new SpliceInstallations.Versions("3.6.4", "3.5.2", "3.5.2"), lstCanton));
        MachineCheck.Report report = MachineCheck.reportOf(facts(roots(false, true), lstCanton,
                mapChoice, List.of(), FILE_JAVA), true);
        assertFalse(report.flagReady());
        assertTrue(report.strText().contains("needs a Canton 3.6"), report.strText());
        assertFalse(report.lstAction().contains(MachineCheck.Action.INSTALL_SPLICE));
    }


    /** The WinVM: 0.7.4 under %APPDATA%\splice, nothing under ~/.splice. */
    @Test
    void anOldSpliceElsewhereIsReportedNotOffered() {
        MachineCheck.Found found = new MachineCheck.Found(MachineCheck.Kind.SPLICE,
                DIR.resolve("AppData/Roaming/splice"), List.of("0.7.4"));
        MachineCheck.Report report = MachineCheck.reportOf(facts(roots(false, false), List.of(),
                Map.of(), List.of(found), FILE_JAVA), true);
        assertTrue(report.strText().contains("0.7.4"), report.strText());
        assertNull(report.foundOffered());
        assertTrue(report.lstAction().contains(MachineCheck.Action.INSTALL_SPLICE));
    }


    @Test
    void aRunnableSpliceElsewhereIsOffered() {
        MachineCheck.Found found = new MachineCheck.Found(MachineCheck.Kind.SPLICE,
                DIR.resolve("AppData/Roaming/splice"), List.of("0.9.4"));
        MachineCheck.Report report = MachineCheck.reportOf(facts(roots(false, false), List.of(),
                Map.of(), List.of(found), FILE_JAVA), true);
        assertEquals(found, report.foundOffered());
        assertTrue(report.lstAction().contains(MachineCheck.Action.USE_FOUND));
    }


    @Test
    void anAssistantRootElsewhereIsOfferedOnlyWhenTheOneInUseIsEmpty() {
        MachineCheck.Found found = new MachineCheck.Found(MachineCheck.Kind.DAML,
                DIR.resolve("tools/daml"), List.of());
        MachineCheck.Report reportEmpty = MachineCheck.reportOf(facts(roots(false, false),
                List.of(), Map.of(), List.of(found), FILE_JAVA), false);
        assertEquals(found, reportEmpty.foundOffered());

        List<CantonInstallation> lstCanton = List.of(
                inst("2.10.4", InstallSource.DAML_ASSISTANT, true));
        MachineCheck.Report reportUsed = MachineCheck.reportOf(facts(roots(true, false),
                lstCanton, Map.of(), List.of(found), FILE_JAVA), false);
        assertNull(reportUsed.foundOffered());
        assertTrue(reportUsed.strText().contains(found.dir().toString()), reportUsed.strText());
    }


    @Test
    void rootsAreFoundByWhatTheyHoldAndTheOneInUseIsSkipped() throws IOException {
        Path dirUsed = Files.createDirectories(dirTmp.resolve("used").resolve("sdk")).getParent();
        Path dirOther = Files.createDirectories(dirTmp.resolve("other").resolve("sdk"))
                .getParent();
        Path dirEmpty = Files.createDirectories(dirTmp.resolve("empty"));
        List<MachineCheck.Found> lstFound = MachineCheck.lstFound(MachineCheck.Kind.DAML,
                Arrays.asList(dirUsed, null, dirOther, dirEmpty, dirOther), dirUsed);
        assertEquals(1, lstFound.size());
        assertEquals(dirOther, lstFound.get(0).dir());

        Path dirDpm = Files.createDirectories(dirTmp.resolve("dpm").resolve("cache")).getParent();
        assertEquals(1, MachineCheck.lstFound(MachineCheck.Kind.DPM, List.of(dirDpm, dirOther),
                null).size());
    }


    @Test
    void aSpliceRootIsFoundByAStagedBundle() throws IOException {
        Path dirRoot = dirTmp.resolve("splice");
        Path dirBundle = dirRoot.resolve("0.9.4").resolve(SpliceInstallations.STR_BUNDLE);
        boolean flagWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path fileLaunch = flagWindows ? dirBundle.resolve("lib").resolve("splice-node.jar")
                : dirBundle.resolve("bin").resolve("splice-node");
        Files.createDirectories(fileLaunch.getParent());
        Files.writeString(fileLaunch, "");
        fileLaunch.toFile().setExecutable(true);
        Files.createDirectories(dirTmp.resolve("nothing"));

        List<MachineCheck.Found> lstFound = MachineCheck.lstFound(MachineCheck.Kind.SPLICE,
                List.of(dirRoot, dirTmp.resolve("nothing")), dirTmp.resolve("elsewhere"));
        assertEquals(1, lstFound.size());
        assertEquals(List.of("0.9.4"), lstFound.get(0).lstSplice());
    }


    /**
     * MEASURED ON THE WINVM, 2026-10-31: DPM under its root's `bin` and not on
     * PATH was reported "not found" and offered again.
     */
    @Test
    void aLauncherUnderItsRootIsFoundAndNotOfferedAgain() throws IOException {
        Path dirDpm = dirTmp.resolve("dpm");
        Path fileDpm = Files.createDirectories(dirDpm.resolve("bin")).resolve("dpm.exe");
        Files.writeString(fileDpm, "");
        ToolchainRoots rootsRead = new ToolchainRoots(dirTmp.resolve("daml"), dirDpm, null, null);

        ToolchainRoots roots = MachineCheck.rootsResolved(rootsRead, true);

        assertEquals(fileDpm, roots.fileDpm());
        assertNull(roots.fileDaml());
        MachineCheck.Report report = MachineCheck.reportOf(facts(roots,
                List.of(inst("3.6.3", InstallSource.DPM, true)), Map.of(), List.of(), FILE_JAVA),
                false);
        assertEquals(List.of(MachineCheck.Action.CLOSE), report.lstAction());
        assertTrue(report.strText().contains("launcher: " + fileDpm), report.strText());
    }
}
