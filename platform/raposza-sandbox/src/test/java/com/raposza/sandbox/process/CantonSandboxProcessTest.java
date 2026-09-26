// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.process;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.Edition;
import com.raposza.canton.install.InstallSource;
import com.raposza.canton.install.VersionId;
import com.raposza.canton.topology.SandboxPorts;
import com.raposza.runtime.process.ProcessException;
import com.raposza.sandbox.topology.SandboxSpec;

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
class CantonSandboxProcessTest {

    @TempDir
    Path dirTemp;

    private Path dirWork;
    private CantonInstallation install35;


    @BeforeEach
    void setUp() throws IOException {
        dirWork = dirTemp.resolve("work");
        install35 = installation("3.5.11", Edition.OPEN_SOURCE, InstallSource.DPM);
    }


    @Test
    void buildsTheSandboxSubcommandWithEveryPort() throws IOException {
        CantonSandboxProcess process =
                new CantonSandboxProcess(install35, SandboxSpec.ofDefaults(), dirWork);
        List<String> lstCommand = process.buildCommand();

        assertEquals("java", lstCommand.get(0));
        assertEquals("-jar", lstCommand.get(1));
        assertEquals("sandbox", lstCommand.get(3));

        // Every port is passed. A stack taking some from a Canton default and
        // some from us would move under a patch release in silence.
        assertEquals("6865", valueOf(lstCommand, "--ledger-api-port"));
        assertEquals("6866", valueOf(lstCommand, "--admin-api-port"));
        assertEquals("6864", valueOf(lstCommand, "--json-api-port"));
        assertEquals("6867", valueOf(lstCommand, "--sequencer-public-port"));
        assertEquals("6868", valueOf(lstCommand, "--sequencer-admin-port"));
        assertEquals("6869", valueOf(lstCommand, "--mediator-admin-port"));
        assertTrue(lstCommand.contains("--no-tty"));
    }


    @Test
    void everyPathIsAbsolute() throws IOException {
        CantonSandboxProcess process =
                new CantonSandboxProcess(install35, SandboxSpec.ofDefaults(), Path.of("work"));

        for (String strArg : process.buildCommand()) {
            if (!strArg.contains(java.io.File.separator) || strArg.startsWith("--"))
                continue;
            assertTrue(Path.of(strArg).isAbsolute(), "not absolute: " + strArg);
        }
    }


    @Test
    void optionalFlagsAreAbsentUnlessAsked() throws IOException {
        List<String> lstPlain = new CantonSandboxProcess(install35, SandboxSpec.ofDefaults(),
                dirWork).buildCommand();
        assertFalse(lstPlain.contains("--static-time"));
        assertFalse(lstPlain.contains("--dev"));
        assertFalse(lstPlain.contains("--dar"));
        assertFalse(lstPlain.stream().anyMatch(s -> s.startsWith("-Xmx")));

        SandboxSpec spec = new SandboxSpec(SandboxPorts.ofDefaults(), List.of(), true, true, 2048);
        List<String> lstFull =
                new CantonSandboxProcess(install35, spec, dirWork).buildCommand();
        assertTrue(lstFull.contains("--static-time"));
        assertTrue(lstFull.contains("--dev"));
        assertTrue(lstFull.contains("-Xmx2048m"));
    }


    @Test
    void darsAreListedInOrder() throws IOException {
        Path fileFirst = dirTemp.resolve("first.dar");
        Path fileSecond = dirTemp.resolve("second.dar");
        Files.write(fileFirst, "dar".getBytes(StandardCharsets.UTF_8));
        Files.write(fileSecond, "dar".getBytes(StandardCharsets.UTF_8));

        SandboxSpec spec = SandboxSpec.ofDefaults().withDars(List.of(fileFirst, fileSecond));
        List<String> lstCommand =
                new CantonSandboxProcess(install35, spec, dirWork).buildCommand();

        int idxFirst = lstCommand.indexOf(fileFirst.toAbsolutePath().normalize().toString());
        int idxSecond = lstCommand.indexOf(fileSecond.toAbsolutePath().normalize().toString());
        assertTrue(idxFirst > 0);
        assertTrue(idxSecond > idxFirst);
        assertEquals("--dar", lstCommand.get(idxFirst - 1));
        assertEquals("--dar", lstCommand.get(idxSecond - 1));
    }


    @Test
    void refusesADarThatIsNotThere() {
        SandboxSpec spec = SandboxSpec.ofDefaults().withDars(List.of(dirTemp.resolve("absent.dar")));
        CantonSandboxProcess process = new CantonSandboxProcess(install35, spec, dirWork);
        assertThrows(IOException.class, process::buildCommand);
    }


    @Test
    void refusesTwoxBecauseTheSubcommandDoesNotExistThere() throws IOException {
        // 2.9.7's usage line is daemon|run|generate. Failing here is cheaper
        // than failing inside a launched process.
        CantonInstallation install29 =
                installation("2.9.7", Edition.OPEN_SOURCE, InstallSource.DAML_ASSISTANT);
        assertThrows(IllegalArgumentException.class,
                () -> new CantonSandboxProcess(install29, SandboxSpec.ofDefaults(), dirWork));
    }


    @Test
    void refusesAnInstallationWithNoRuntime() {
        CantonInstallation install = new CantonInstallation(VersionId.of(3, 5, 11),
                Edition.OPEN_SOURCE, InstallSource.DPM, dirTemp, null);
        assertThrows(IllegalArgumentException.class,
                () -> new CantonSandboxProcess(install, SandboxSpec.ofDefaults(), dirWork));
    }


    @Test
    void aStalePortFileWouldNotReportTheNextRunReady() throws IOException {
        // A missing DAR makes buildCommand throw, so start() fails after it has
        // cleared the file and before it launches anything. That is the whole
        // assertion: the file is gone by the time a launch could happen.
        SandboxSpec spec = SandboxSpec.ofDefaults().withDars(List.of(dirTemp.resolve("absent.dar")));
        CantonSandboxProcess process = new CantonSandboxProcess(install35, spec, dirWork);

        Files.createDirectories(dirWork);
        Files.write(process.filePorts(), "{}".getBytes(StandardCharsets.UTF_8));
        assertTrue(process.isReadyOutOfBand());

        assertThrows(ProcessException.class, process::start);
        assertFalse(Files.exists(process.filePorts()));
        assertFalse(process.isReadyOutOfBand());
    }


    @Test
    void readinessAcceptsEitherWording() {
        CantonSandboxProcess process =
                new CantonSandboxProcess(install35, SandboxSpec.ofDefaults(), dirWork);
        assertTrue(process.isReadyLine("Canton sandbox is ready."));
        assertTrue(process.isReadyLine("sandbox listening at ports: 6865(gRPC) and 6864(HTTP)"));
        assertFalse(process.isReadyLine("Starting Canton version 3.5.11"));
    }


    private CantonInstallation installation(String strVersion, Edition edition,
            InstallSource source) throws IOException {
        Path dirHome = dirTemp.resolve(strVersion);
        Path dirLib = dirHome.resolve("lib");
        Files.createDirectories(dirLib);
        Path fileJar = dirLib.resolve("canton-open-source-" + strVersion + ".jar");
        Files.write(fileJar, "not really a jar".getBytes(StandardCharsets.UTF_8));
        return new CantonInstallation(VersionId.parse(strVersion), edition, source, dirHome,
                fileJar);
    }


    private static String valueOf(List<String> lstCommand, String strFlag) {
        int idxFlag = lstCommand.indexOf(strFlag);
        assertTrue(idxFlag >= 0, "flag absent: " + strFlag);
        return lstCommand.get(idxFlag + 1);
    }
}
