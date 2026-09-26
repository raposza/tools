// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.process;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.Edition;
import com.raposza.canton.install.InstallSource;
import com.raposza.canton.install.VersionId;
import com.raposza.canton.topology.Canton3xDaemonBootstrap;
import com.raposza.runtime.process.ProcessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command line is built and inspected; nothing is launched.
 *
 * Author Claude/bentzn
 */
class Canton3xDaemonProcessTest {

    @TempDir
    Path dirTemp;

    private Path dirWork;
    private Path fileConf;
    private Path fileBootstrap;
    private CantonInstallation install35;


    @BeforeEach
    void setUp() throws IOException {
        dirWork = dirTemp.resolve("work");
        Files.createDirectories(dirWork);
        fileConf = write(dirTemp.resolve("sandbox.conf"), "canton {}");
        fileBootstrap = write(dirTemp.resolve("bootstrap.canton"), "println(\"hi\")");
        install35 = installation("3.5.11");
    }


    @Test
    void buildsTheDaemonSubcommandWithEveryConfigAndTheScript() throws IOException {
        Path fileOverlay = write(dirTemp.resolve("ports.conf"), "canton {}");
        Canton3xDaemonProcess process = new Canton3xDaemonProcess(install35,
                List.of(fileConf, fileOverlay), fileBootstrap, dirWork, 0);
        List<String> lstCommand = process.buildCommand();

        assertEquals("java", lstCommand.get(0));
        assertEquals("-jar", lstCommand.get(1));
        assertEquals("daemon", lstCommand.get(3));
        assertEquals(fileBootstrap.toAbsolutePath().toString(),
                valueOf(lstCommand, "--bootstrap"));
        assertFalse(lstCommand.contains("--no-tty"),
                "the flag is off pending a measurement; what ended the daemon"
                        + " was the script's own sys.exit(0): " + lstCommand);

        // ORDER, and it is Canton's rather than ours: a later -c wins where
        // two set the same key, so the vendor topology has to come first and
        // every overlay after it.
        int idxVendor = lstCommand.indexOf(fileConf.toAbsolutePath().toString());
        int idxOverlay = lstCommand.indexOf(fileOverlay.toAbsolutePath().toString());
        assertTrue(idxVendor > 0 && idxOverlay > idxVendor,
                "the overlay does not follow the vendor file: " + lstCommand);
    }


    /**
     * No `--ledger-api-port` and no siblings. `daemon --help` on 3.5.11 offers
     * no port options at all, so a command carrying one would be rejected
     * before anything started.
     */
    @Test
    void noPortReachesTheCommandLine() throws IOException {
        Canton3xDaemonProcess process =
                new Canton3xDaemonProcess(install35, List.of(fileConf), fileBootstrap, dirWork, 0);

        for (String strArg : process.buildCommand()) {
            assertFalse(strArg.contains("-port"), "a port option reached daemon: " + strArg);
        }
    }


    @Test
    void theHeapIsPassedOnlyWhenAskedFor() throws IOException {
        Canton3xDaemonProcess plain =
                new Canton3xDaemonProcess(install35, List.of(fileConf), fileBootstrap, dirWork, 0);
        assertFalse(plain.buildCommand().stream().anyMatch(str -> str.startsWith("-Xmx")));

        Canton3xDaemonProcess heavy = new Canton3xDaemonProcess(install35, List.of(fileConf),
                fileBootstrap, dirWork, 4096);
        assertTrue(heavy.buildCommand().contains("-Xmx4096m"));
    }


    @Test
    void aMissingFileIsNamedRatherThanLaunched() {
        Canton3xDaemonProcess process = new Canton3xDaemonProcess(install35,
                List.of(dirTemp.resolve("absent.conf")), fileBootstrap, dirWork, 0);
        assertThrows(IOException.class, process::buildCommand);

        Canton3xDaemonProcess noScript = new Canton3xDaemonProcess(install35, List.of(fileConf),
                dirTemp.resolve("absent.canton"), dirWork, 0);
        assertThrows(IOException.class, noScript::buildCommand);
    }


    /**
     * `daemon` processes no default configuration, so an empty list is a stack
     * with no topology at all rather than a stack with defaults.
     */
    @Test
    void aTopologyIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new Canton3xDaemonProcess(install35,
                List.of(), fileBootstrap, dirWork, 0));
        assertThrows(IllegalArgumentException.class, () -> new Canton3xDaemonProcess(install35,
                null, fileBootstrap, dirWork, 0));
    }


    @Test
    void aTwoXInstallationIsRefused() throws IOException {
        CantonInstallation install29 = installation("2.9.7");
        assertThrows(IllegalArgumentException.class, () -> new Canton3xDaemonProcess(install29,
                List.of(fileConf), fileBootstrap, dirWork, 0));
    }


    /**
     * The ready phrase is this project's own, printed by the script it wrote,
     * so no patch release can reword it.
     */
    @Test
    void readinessIsTheScriptsOwnLastLine() {
        Canton3xDaemonProcess process =
                new Canton3xDaemonProcess(install35, List.of(fileConf), fileBootstrap, dirWork, 0);

        assertTrue(process.isReadyLine(Canton3xDaemonBootstrap.STR_DONE));
        assertFalse(process.isReadyLine("Canton sandbox is ready."));
    }


    /**
     * The READY sentinel is what readiness asks, so it is what a stale
     * one has to be written as. The other four are written beside it because
     * `start()` must clear all of them: a left-over `participant-id.txt` is
     * not readiness, but it IS a diagnostic that would describe the PREVIOUS
     * run as though it belonged to this one - and `party-id.txt` and
     * `user-id.txt` name things that genuinely survive a restart, so nothing
     * about their content says which start wrote them.
     */
    @Test
    void staleSentinelsWouldNotReportTheNextRunReady() throws IOException {
        Canton3xDaemonProcess process = new Canton3xDaemonProcess(install35,
                List.of(dirTemp.resolve("absent.conf")), fileBootstrap, dirWork, 0);

        Files.write(process.fileReady(),
                Canton3xDaemonBootstrap.STR_READY.getBytes(StandardCharsets.UTF_8));
        Files.write(process.fileParticipantId(), "PAR::old".getBytes(StandardCharsets.UTF_8));
        Files.write(process.fileMarker(), "old".getBytes(StandardCharsets.UTF_8));
        Files.write(process.filePartyId(), "alice::old".getBytes(StandardCharsets.UTF_8));
        Files.write(process.fileUserId(), "old-user".getBytes(StandardCharsets.UTF_8));
        assertTrue(process.isReadyOutOfBand());

        // buildCommand throws on the missing config, so start() fails after it
        // has cleared the sentinels and before it launches anything.
        assertThrows(ProcessException.class, process::start);
        assertFalse(Files.exists(process.fileReady()));
        assertFalse(Files.exists(process.fileParticipantId()));
        assertFalse(Files.exists(process.fileMarker()));
        assertFalse(Files.exists(process.filePartyId()));
        assertFalse(Files.exists(process.fileUserId()));
        assertFalse(process.isReadyOutOfBand());
    }


    /**
     * The participant id is NOT readiness, and this is the assertion that says
     * so. It is the inverse of the one above, and it is the case that
     * mattered: writing this file alone was once enough to
     * report a stack ready with its whole provisioning block still to run.
     */
    @Test
    void aParticipantIdAloneIsNotReadiness() throws IOException {
        Canton3xDaemonProcess process = new Canton3xDaemonProcess(install35,
                List.of(dirTemp.resolve("absent.conf")), fileBootstrap, dirWork, 0);

        Files.write(process.fileParticipantId(), "PAR::mid".getBytes(StandardCharsets.UTF_8));
        Files.write(process.fileMarker(), "mid".getBytes(StandardCharsets.UTF_8));

        assertFalse(process.isReadyOutOfBand(),
                "a script that has written its participant id is in the MIDDLE of its run");
    }


    private CantonInstallation installation(String strVersion) throws IOException {
        Path dirHome = dirTemp.resolve(strVersion);
        Path dirLib = dirHome.resolve("lib");
        Files.createDirectories(dirLib);
        Path fileJar = dirLib.resolve("canton-open-source-" + strVersion + ".jar");
        Files.write(fileJar, "not really a jar".getBytes(StandardCharsets.UTF_8));
        return new CantonInstallation(VersionId.parse(strVersion), Edition.OPEN_SOURCE,
                InstallSource.DPM, dirHome, fileJar);
    }


    private static Path write(Path file, String strContent) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, strContent.getBytes(StandardCharsets.UTF_8));
        return file;
    }


    private static String valueOf(List<String> lstCommand, String strFlag) {
        int idxFlag = lstCommand.indexOf(strFlag);
        assertTrue(idxFlag >= 0, "flag absent: " + strFlag);
        return lstCommand.get(idxFlag + 1);
    }
}
