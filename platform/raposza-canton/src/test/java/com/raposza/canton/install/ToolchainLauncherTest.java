// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * <h2>The case that matters cannot be produced on the machine that runs this</h2>
 *
 * A Windows layout with `.cmd` launchers is the whole reason the class exists,
 * so the platform is an argument here as it is in {@link ToolchainRootsTest}
 * and nothing reads the real environment.
 *
 * Author Claude/bentzn
 */
class ToolchainLauncherTest {

    @TempDir
    Path dirTemp;


    /**
     * PATH names the launcher's directory, and the answer has to carry the
     * extension - the bare name is what fails there.
     */
    @Test
    void aWindowsLauncherOnPathAnswersWithItsCmd() throws IOException {
        Path dirBin = Files.createDirectories(dirTemp.resolve("Roaming")
                .resolve("dpm").resolve("bin"));
        Files.createDirectories(dirTemp.resolve("Roaming").resolve("dpm").resolve("cache"));
        Path fileCmd = Files.createFile(dirBin.resolve("dpm.cmd"));

        Map<String, String> mapEnv = new HashMap<>();
        mapEnv.put("APPDATA", dirTemp.resolve("Roaming").toString());
        ToolchainRoots roots = ToolchainRoots.of(dirBin.toString(), mapEnv, true, dirTemp);

        assertEquals(fileCmd.toString(), ToolchainLauncher.strLauncher("dpm", roots, true));
    }


    /**
     * Installed and NOT on PATH, which is the ordinary state of a fresh
     * Windows machine: the root still holds the launcher and it is the answer.
     */
    @Test
    void anInstalledLauncherIsFoundUnderTheRootWithNothingOnPath() throws IOException {
        Path dirBin = Files.createDirectories(dirTemp.resolve("Roaming")
                .resolve("daml").resolve("bin"));
        Path fileCmd = Files.createFile(dirBin.resolve("daml.cmd"));

        Map<String, String> mapEnv = new HashMap<>();
        mapEnv.put("APPDATA", dirTemp.resolve("Roaming").toString());
        ToolchainRoots roots = ToolchainRoots.of("", mapEnv, true, dirTemp);

        assertEquals(fileCmd.toString(), ToolchainLauncher.strLauncher("daml", roots, true));
    }


    /**
     * A machine with neither toolchain gets the bare name back, so the caller's
     * failure message is the one it always was.
     */
    @Test
    void nothingInstalledAnswersWithTheBareName() {
        Map<String, String> mapEnv = new HashMap<>();
        mapEnv.put("APPDATA", dirTemp.resolve("Roaming").toString());
        ToolchainRoots roots = ToolchainRoots.of("", mapEnv, true, dirTemp);

        assertEquals("dpm", ToolchainLauncher.strLauncher("dpm", roots, true));
    }


    /**
     * The Linux side keeps its extensionless launcher, and an install off PATH
     * is found there too.
     */
    @Test
    void aUnixLauncherUnderTheRootIsFoundWithoutAnExtension() throws IOException {
        Path dirBin = Files.createDirectories(dirTemp.resolve(".dpm").resolve("bin"));
        Path fileBin = Files.createFile(dirBin.resolve("dpm"));

        ToolchainRoots roots = ToolchainRoots.of("", new HashMap<>(), false, dirTemp);

        assertEquals(fileBin.toString(), ToolchainLauncher.strLauncher("dpm", roots, false));
    }


    /**
     * A name this class knows nothing about is handed straight back rather than
     * being resolved against a root it has no business naming.
     */
    @Test
    void anUnknownNameIsHandedBackUnchanged() {
        ToolchainRoots roots = ToolchainRoots.of("", new HashMap<>(), false, dirTemp);

        assertEquals("scribe", ToolchainLauncher.strLauncher("scribe", roots, false));
    }

}
