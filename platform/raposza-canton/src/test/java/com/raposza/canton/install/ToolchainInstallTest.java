// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>Nothing here downloads anything</h2>
 *
 * The fetcher writes a stub file and the runner records what it was asked to
 * run, answering the tag listing from a fixture. What is being held still is
 * the ORDER of the steps, the paths they use and the refusal to continue past a
 * failure - none of which is a property of the network.
 *
 * Author Claude/bentzn
 */
class ToolchainInstallTest {

    private static final String STR_TAGS = String.join("\n",
            "3.5", "3.5.14", "3.5.14.generic", "3.5.16", "3.5.16-snapshot.20260901.1.vabc",
            "3.6", "devnet");

    private static final String STR_CHATTER = "a line the window should see";

    @TempDir
    Path dirTemp;

    private List<String> lstFetched;

    private List<List<String>> lstRun;

    private List<String> lstSaid;

    private List<String> lstProgress;

    private int nExitNext;

    /** Whether an add leaves its jar, or only the metadata a failed pull left. */
    private boolean flagJarOnAdd;


    /**
     * What `dpm add component` leaves on disk, in the MIRRORED layout an
     * explicit OCI pull uses: `component.yaml` and `lib/`, and the jar inside
     * `lib/` only when the pull completed.
     *
     * @param roots where DPM keeps its cache
     * @param strRef the reference the add was given, ending `:version`
     */
    private void landComponent(ToolchainRoots roots, String strRef) throws IOException {
        String strVersion = strRef.substring(strRef.lastIndexOf(':') + 1);
        Path dirVersion = roots.dirDpm().resolve("cache").resolve("components")
                .resolve(DpmCatalogue.STR_REGISTRY).resolve("components")
                .resolve(DpmCatalogue.STR_COMPONENT_OPEN_SOURCE).resolve(strVersion);
        Path dirLib = dirVersion.resolve("lib");
        Files.createDirectories(dirLib);
        Files.writeString(dirVersion.resolve("component.yaml"), "stub");
        if (flagJarOnAdd)
            Files.writeString(dirLib.resolve("canton-open-source-" + strVersion + ".jar"), "stub");
    }


    @BeforeEach
    void setUp() {
        lstFetched = new ArrayList<>();
        lstRun = new ArrayList<>();
        lstSaid = new ArrayList<>();
        lstProgress = new ArrayList<>();
        nExitNext = 0;
        flagJarOnAdd = true;
    }


    /**
     * @param strLatest what the version endpoint answers
     * @param roots what is already present
     * @return an install whose fetcher writes a stub and whose runner records
     */
    private ToolchainInstall install(String strLatest, ToolchainRoots roots,
            HostPlatform platform) {
        ToolchainInstall.Fetcher fetcher = new ToolchainInstall.Fetcher() {

            @Override
            public void fetch(String strUrl, Path fileOut,
                    Download.Progress progress) throws IOException {
                if (progress != null) {
                    progress.onBytes(1L, 4L);
                    progress.onBytes(4L, 4L);
                }
                lstFetched.add(strUrl);
                Files.createDirectories(fileOut.getParent());
                Files.writeString(fileOut, "stub");
            }


            @Override
            public String strFetch(String strUrl) {
                lstFetched.add(strUrl);
                return strLatest;
            }
        };
        ToolchainInstall.Runner runner = (lstCommand, dirWorking, lineStep) -> {
            lstRun.add(lstCommand);
            if (lstCommand.contains(ToolchainInstall.STR_CMD_COMPONENT))
                landComponent(roots, lstCommand.get(lstCommand.size() - 1));
            if (lineStep == null)
                return nExitNext;

            if (lstCommand.contains(ToolchainInstall.STR_CMD_TAGS)) {
                for (String strLine : STR_TAGS.split("\n")) {
                    lineStep.accept(strLine);
                }
            }
            else {
                lineStep.accept(STR_CHATTER);
            }
            return nExitNext;
        };
        return new ToolchainInstall(fetcher, runner, platform, roots,
                lstSaid::add, lstProgress::add);
    }


    /** HIS LOG: dpm's lines start with a capital, and nothing else changes. */
    @Test
    void aDpmLineGainsACapitalAndNothingElse() {
        assertEquals("Pulling sdk component scribe 3.5.8...",
                ToolchainInstall.strCapital("pulling sdk component scribe 3.5.8..."));
        assertEquals("", ToolchainInstall.strCapital(""));
    }


    /** A machine with dpm already on it does not fetch dpm again. */
    private static ToolchainRoots rootsWithDpm(Path dirRoot) {
        return new ToolchainRoots(dirRoot.resolve(".daml"), dirRoot.resolve(".dpm"), null,
                dirRoot.resolve(".dpm").resolve("bin").resolve("dpm"));
    }


    private static ToolchainRoots rootsBare(Path dirRoot) {
        return new ToolchainRoots(dirRoot.resolve(".daml"), dirRoot.resolve(".dpm"), null, null);
    }


    @Test
    void theWholeSequenceIsBootstrapThenTagsThenInitThenAdd() throws IOException {
        Path dirWork = dirTemp.resolve("work");

        VersionId version = install("3.5.7", rootsBare(dirTemp), HostPlatform.LINUX_X64)
                .versionInstallLatest(dirWork);

        assertEquals(VersionId.of(3, 5, 16), version);
        assertEquals(List.of(DpmBootstrap.STR_URL_LATEST,
                DpmBootstrap.strUrl("3.5.7", HostPlatform.LINUX_X64)), lstFetched);
        assertEquals(5, lstRun.size());
        assertEquals("tar", lstRun.get(0).get(0));
        assertEquals("bootstrap", lstRun.get(1).get(1));
        assertEquals("tags", lstRun.get(2).get(1));
        assertEquals("init", lstRun.get(3).get(1));
        assertEquals(List.of("add", "component"), lstRun.get(4).subList(1, 3));
    }


    /** dpm already present: nothing is fetched and the bootstrap is skipped. */
    @Test
    void anExistingDpmIsUsedRatherThanReinstalled() throws IOException {
        install("3.5.7", rootsWithDpm(dirTemp), HostPlatform.LINUX_X64)
                .versionInstallLatest(dirTemp.resolve("work"));

        assertTrue(lstFetched.isEmpty(), lstFetched.toString());
        assertEquals(3, lstRun.size());
        assertEquals("tags", lstRun.get(0).get(1));
        assertEquals(dirTemp.resolve(".dpm").resolve("bin").resolve("dpm").toString(),
                lstRun.get(0).get(0));
    }


    /** The NEWEST stable, not a line tag, a snapshot or the first one listed. */
    @Test
    void theNewestStableVersionIsTheOneAdded() throws IOException {
        install("3.5.7", rootsWithDpm(dirTemp), HostPlatform.LINUX_X64)
                .versionInstallLatest(dirTemp.resolve("work"));

        assertTrue(lstRun.get(2).get(3).endsWith(":3.5.16"), lstRun.get(2).get(3));
    }


    /** The component is added inside a project of its own, not the work root. */
    @Test
    void theComponentIsAddedThroughAScratchProject() throws IOException {
        Path dirWork = dirTemp.resolve("work");

        install("3.5.7", rootsWithDpm(dirTemp), HostPlatform.LINUX_X64)
                .versionInstallLatest(dirWork);

        assertTrue(Files.isDirectory(dirWork.resolve(ToolchainInstall.STR_DIR_PROJECT)));
    }


    /**
     * An empty listing is a wrong path and an empty repository alike, so it is
     * a failure rather than "there is no Canton".
     */
    @Test
    void anEmptyTagListingFailsRatherThanAnsweringNothing() {
        ToolchainInstall install = new ToolchainInstall(new ToolchainInstall.Fetcher() {

            @Override
            public void fetch(String strUrl, Path fileOut,
                    Download.Progress progress) {
                throw new IllegalStateException("nothing should be fetched");
            }


            @Override
            public String strFetch(String strUrl) {
                throw new IllegalStateException("nothing should be read");
            }
        }, (lstCommand, dirWorking, lineStep) -> {
            lstRun.add(lstCommand);
            return 0;
        }, HostPlatform.LINUX_X64, rootsWithDpm(dirTemp), lstSaid::add,
                lstProgress::add);

        assertThrows(IOException.class,
                () -> install.versionInstallLatest(dirTemp.resolve("work")));
        assertEquals(1, lstRun.size());
    }


    @Test
    void aFailingStepStopsTheSequence() {
        nExitNext = 2;

        IOException ex = assertThrows(IOException.class,
                () -> install("3.5.7", rootsBare(dirTemp), HostPlatform.LINUX_X64)
                        .versionInstallLatest(dirTemp.resolve("work")));

        assertTrue(ex.getMessage().contains("2"), ex.getMessage());
        assertEquals(1, lstRun.size());
    }


    @Test
    void anEmptyVersionEndpointIsAFailureBeforeAnythingIsFetched() {
        assertThrows(IOException.class, () -> install("", rootsBare(dirTemp),
                HostPlatform.LINUX_X64).versionInstallLatest(dirTemp.resolve("work")));

        assertEquals(1, lstFetched.size());
        assertTrue(lstRun.isEmpty());
    }


    /**
     * The bootstrap answers the launcher inside the tree it just unpacked. The
     * one on PATH is not there until the shell is new, so the steps after it
     * cannot use a bare name.
     */
    @Test
    void theStepsAfterTheBootstrapUseTheUnpackedLauncher() throws IOException {
        Path dirWork = dirTemp.resolve("work");

        install("3.5.7", rootsBare(dirTemp), HostPlatform.LINUX_X64)
                .versionInstallLatest(dirWork);

        Path fileExpected = dirWork.resolve(ToolchainInstall.STR_DIR_UNPACKED)
                .resolve("bin").resolve("dpm");
        assertEquals(fileExpected.toString(), lstRun.get(2).get(0));
        assertEquals(fileExpected.toString(), lstRun.get(4).get(0));
    }


    @Test
    void theSdkPathStillWorksAndIsNotPartOfTheOffer() throws IOException {
        Path dirWork = dirTemp.resolve("work");
        ToolchainInstall install = install(null, rootsBare(dirTemp), HostPlatform.LINUX_X64);
        Path dirUnpacked = dirWork.resolve(ToolchainInstall.STR_DIR_UNPACKED);
        Files.createDirectories(dirUnpacked);
        Files.writeString(dirUnpacked.resolve("install.sh"), "#!/bin/sh\n");

        install.installSdk(VersionId.of(2, 10, 6), dirWork);

        assertEquals(List.of(SdkCatalogue.strUrl(VersionId.of(2, 10, 6),
                HostPlatform.LINUX_X64)), lstFetched);
        assertEquals(dirUnpacked.resolve("install.sh").toString(), lstRun.get(1).get(0));
    }


    @Test
    void anSdkWithNoInstallerInsideIsRefusedByName() {
        ToolchainInstall install = install(null, rootsBare(dirTemp), HostPlatform.LINUX_X64);

        IOException ex = assertThrows(IOException.class,
                () -> install.installSdk(VersionId.of(2, 10, 6), dirTemp.resolve("work")));

        assertTrue(ex.getMessage().contains("install.sh"), ex.getMessage());
    }


    /** A reader watching a long fetch is told how far it has got. */
    @Test
    void aFetchReportsItself() throws IOException {
        install("3.5.7", rootsBare(dirTemp), HostPlatform.LINUX_X64)
                .versionInstallLatest(dirTemp.resolve("work"));

        assertTrue(lstProgress.stream().anyMatch(str -> str.contains("25%")),
                lstProgress.toString());
        assertTrue(lstProgress.stream().anyMatch(str -> str.contains("100%")),
                lstProgress.toString());
    }


    @Test
    void aKnownSizeIsAPercentage() {
        assertEquals("dpm.tar.gz  25% of 4.0 MiB",
                ToolchainInstall.strProgress("dpm.tar.gz", ToolchainInstall.N_BYTES_MIB,
                        4 * ToolchainInstall.N_BYTES_MIB));
    }


    /**
     * A server that did not say how big it is gets no percentage: a bar that
     * cannot reach the end is worse than a number that never claimed to.
     */
    @Test
    void anUnknownSizeIsWhatHasArrived() {
        assertEquals("dpm.tar.gz  2.5 MiB",
                ToolchainInstall.strProgress("dpm.tar.gz",
                        (long) (2.5 * ToolchainInstall.N_BYTES_MIB), 0L));
    }


    @Test
    void theTagListingDoesNotReachTheWindow() throws IOException {
        install("3.5.7", rootsWithDpm(dirTemp), HostPlatform.LINUX_X64)
                .versionInstallLatest(dirTemp.resolve("work"));

        // The registry answers with several hundred lines, nearly all of them
        // snapshots, candidates and line tags.
        String strSaid = String.join("\n", lstSaid);
        assertFalse(strSaid.contains(".generic"), strSaid);
        assertFalse(strSaid.contains("-snapshot"), strSaid);
        assertFalse(strSaid.contains("devnet"), strSaid);
    }


    /** What the reader wants is the releases, and those are named. */
    @Test
    void theReleasesAreNamedInsteadNewestFirst() throws IOException {
        install("3.5.7", rootsWithDpm(dirTemp), HostPlatform.LINUX_X64)
                .versionInstallLatest(dirTemp.resolve("work"));

        assertTrue(lstSaid.contains("published: 3.5.16 3.5.14"), lstSaid.toString());
    }


    /** Every other step still prints where the reader can see it. */
    @Test
    void anOrdinaryStepStillReachesTheWindow() throws IOException {
        install("3.5.7", rootsWithDpm(dirTemp), HostPlatform.LINUX_X64)
                .versionInstallLatest(dirTemp.resolve("work"));

        assertTrue(lstSaid.contains(STR_CHATTER), lstSaid.toString());
    }


    @Test
    void theOperatorIsToldWhatIsHappening() throws IOException {
        install("3.5.7", rootsBare(dirTemp), HostPlatform.LINUX_X64)
                .versionInstallLatest(dirTemp.resolve("work"));

        String strSaid = String.join("\n", lstSaid);
        assertTrue(strSaid.contains("3.5.7"), strSaid);
        assertTrue(strSaid.contains("3.5.16"), strSaid);
        assertTrue(strSaid.contains("unpacking"), strSaid);
    }


    /**
     * THE MEASURED FAILURE: dpm exits 0 and leaves `component.yaml` beside an
     * empty `lib/`. That is a failed install, said as one, and "installed" is
     * never printed.
     */
    @Test
    void anAddThatLeavesNoJarIsAFailureNotAnInstall() {
        flagJarOnAdd = false;

        IOException ex = assertThrows(IOException.class,
                () -> install("3.5.7", rootsWithDpm(dirTemp), HostPlatform.LINUX_X64)
                        .versionInstallLatest(dirTemp.resolve("work")));

        assertTrue(ex.getMessage().contains("3.5.16"), ex.getMessage());
        assertTrue(ex.getMessage().contains("no runtime jar"), ex.getMessage());
        assertFalse(lstSaid.contains("Canton 3.5.16 installed"), lstSaid.toString());
    }


    /** The same add with its jar landed is an install, and says so. */
    @Test
    void anAddWhoseJarLandedSaysInstalled() throws IOException {
        VersionId version = install("3.5.7", rootsWithDpm(dirTemp), HostPlatform.LINUX_X64)
                .versionInstallLatest(dirTemp.resolve("work"));

        assertEquals(VersionId.of(3, 5, 16), version);
        assertTrue(lstSaid.contains("Canton 3.5.16 installed"), lstSaid.toString());
    }

}
