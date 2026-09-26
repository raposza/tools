// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.process;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.Edition;
import com.raposza.canton.install.InstallSource;
import com.raposza.canton.install.VersionId;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The overlay half of the command line. Nothing is launched.
 *
 * Author Claude/bentzn
 */
class CantonSandboxProcessConfTest {

    @TempDir
    Path dirTemp;

    private CantonInstallation install35;
    private Path dirWork;


    @BeforeEach
    void setUp() throws IOException {
        dirWork = dirTemp.resolve("work");
        Path dirHome = dirTemp.resolve("3.5.11");
        Path dirLib = dirHome.resolve("lib");
        Files.createDirectories(dirLib);
        Path fileJar = dirLib.resolve("canton-open-source-3.5.11.jar");
        Files.write(fileJar, "not really a jar".getBytes(StandardCharsets.UTF_8));
        install35 = new CantonInstallation(VersionId.of(3, 5, 11), Edition.OPEN_SOURCE,
                InstallSource.DPM, dirHome, fileJar);
    }


    @Test
    void noOverlayByDefault() throws IOException {
        List<String> lstCommand = new CantonSandboxProcess(install35, SandboxSpec.ofDefaults(),
                dirWork).buildCommand();
        assertEquals(-1, lstCommand.indexOf("-c"));
    }


    @Test
    void overlaysComeAfterTheSubcommandAndKeepTheirOrder() throws IOException {
        Path fileFirst = writeConf("first.conf");
        Path fileSecond = writeConf("second.conf");

        List<String> lstCommand = new CantonSandboxProcess(install35, SandboxSpec.ofDefaults(),
                dirWork, List.of(fileFirst, fileSecond)).buildCommand();

        int idxSubcommand = lstCommand.indexOf("sandbox");
        int idxFirst = lstCommand.indexOf(absolute(fileFirst));
        int idxSecond = lstCommand.indexOf(absolute(fileSecond));

        assertTrue(idxFirst > idxSubcommand);
        assertTrue(idxSecond > idxFirst);
        assertEquals("-c", lstCommand.get(idxFirst - 1));
        assertEquals("-c", lstCommand.get(idxSecond - 1));
    }


    @Test
    void overlayPathsAreAbsolute() throws IOException {
        // A relative -c is resolved against the process's working directory
        // rather than the caller's, which produces CANNOT_READ_CONFIG_FILES
        // before anything is parsed.
        Path fileConf = writeConf("storage.conf");
        CantonSandboxProcess process = new CantonSandboxProcess(install35,
                SandboxSpec.ofDefaults(), Path.of("work"), List.of(fileConf));

        int cntConf = 0;
        for (String strArg : process.buildCommand()) {
            if (!strArg.endsWith(".conf"))
                continue;
            cntConf++;
            assertTrue(Path.of(strArg).isAbsolute(), "not absolute: " + strArg);
        }
        assertEquals(1, cntConf);
    }


    @Test
    void refusesAnOverlayThatIsNotThere() {
        CantonSandboxProcess process = new CantonSandboxProcess(install35,
                SandboxSpec.ofDefaults(), dirWork, List.of(dirTemp.resolve("absent.conf")));
        assertThrows(IOException.class, process::buildCommand);
    }


    private Path writeConf(String strName) throws IOException {
        Path file = dirTemp.resolve(strName);
        Files.writeString(file, "canton {}\n", StandardCharsets.UTF_8);
        return file;
    }


    private static String absolute(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }
}
