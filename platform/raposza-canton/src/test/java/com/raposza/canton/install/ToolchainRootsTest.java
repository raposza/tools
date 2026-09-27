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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>Every case is a machine this one is not</h2>
 *
 * The interesting states - no toolchain at all, a Windows layout, a relocated
 * DPM root - cannot be produced on the machine the tests run on, so nothing
 * here reads the real environment. The platform is an argument.
 *
 * Author Claude/bentzn
 */
class ToolchainRootsTest {

    @TempDir
    Path dirTemp;


    /**
     * The Windows guest before anything is installed: an empty PATH, and the
     * answer has to be the place an install WILL land rather than nothing.
     */
    @Test
    void anEmptyPathStillAnswersWithThePlatformDefaults() {
        Map<String, String> mapEnv = new HashMap<>();
        mapEnv.put("APPDATA", dirTemp.resolve("Roaming").toString());

        ToolchainRoots roots = ToolchainRoots.of("", mapEnv, true, dirTemp);

        assertEquals(dirTemp.resolve("Roaming").resolve("daml"), roots.dirDaml());
        assertEquals(dirTemp.resolve("Roaming").resolve("dpm"), roots.dirDpm());
        assertFalse(roots.flagDaml());
        assertFalse(roots.flagDpm());
        assertNull(roots.fileDaml());
    }


    /** And NO leading dot on Windows, which is the trap. */
    @Test
    void theWindowsDefaultsAreNotDotted() {
        Map<String, String> mapEnv = new HashMap<>();
        mapEnv.put("APPDATA", "C:\\Users\\x\\AppData\\Roaming");

        ToolchainRoots roots = ToolchainRoots.of(null, mapEnv, true, dirTemp);

        assertEquals("daml", roots.dirDaml().getFileName().toString());
        assertEquals("dpm", roots.dirDpm().getFileName().toString());
    }


    @Test
    void theUnixDefaultsAreDotted() {
        ToolchainRoots roots = ToolchainRoots.of(null, new HashMap<>(), false, dirTemp);

        assertEquals(dirTemp.resolve(".daml"), roots.dirDaml());
        assertEquals(dirTemp.resolve(".dpm"), roots.dirDpm());
    }


    /**
     * A Windows machine with no `APPDATA` is not one this project has seen, but
     * answering null would put a NullPointerException between the operator and
     * the reason nothing was found.
     */
    @Test
    void aMissingAppdataFallsBackUnderTheHomeDirectory() {
        ToolchainRoots roots = ToolchainRoots.of(null, new HashMap<>(), true, dirTemp);

        assertEquals(dirTemp.resolve("AppData").resolve("Roaming").resolve("daml"),
                roots.dirDaml());
    }


    @Test
    void theRootIsTheParentOfTheBinTheLauncherSitsIn() throws IOException {
        Path dirRoot = dirTemp.resolve(".daml");
        launcher(dirRoot, "daml", "sdk");

        ToolchainRoots roots = ToolchainRoots.of(dirRoot.resolve("bin").toString(),
                new HashMap<>(), false, dirTemp.resolve("elsewhere"));

        assertEquals(dirRoot, roots.dirDaml());
        assertTrue(roots.flagDaml());
        assertEquals(dirRoot.resolve("bin").resolve("daml"), roots.fileDaml());
    }


    /**
     * The Windows launcher is a `.cmd`, so a search for the bare name finds
     * nothing and the guest reports an install that is plainly there.
     */
    @Test
    void theWindowsLauncherIsFoundByItsExtension() throws IOException {
        Path dirRoot = dirTemp.resolve("Roaming").resolve("dpm");
        launcher(dirRoot, "dpm.cmd", "cache");

        ToolchainRoots roots = ToolchainRoots.of(dirRoot.resolve("bin").toString(),
                new HashMap<>(), true, dirTemp);

        assertEquals(dirRoot, roots.dirDpm());
        assertTrue(roots.flagDpm());
    }


    /**
     * A launcher copied onto PATH somewhere ordinary would otherwise make
     * `/usr/local` a toolchain root and every discovery under it empty.
     */
    @Test
    void aLauncherOutsideARootIsFoundButDoesNotDecideTheRoot() throws IOException {
        Path dirBin = dirTemp.resolve("usr").resolve("local").resolve("bin");
        Files.createDirectories(dirBin);
        Files.createFile(dirBin.resolve("daml"));

        ToolchainRoots roots = ToolchainRoots.of(dirBin.toString(), new HashMap<>(),
                false, dirTemp);

        assertTrue(roots.flagDaml());
        assertEquals(dirTemp.resolve(".daml"), roots.dirDaml());
    }


    /** The first PATH entry that carries a launcher wins, as the shell does. */
    @Test
    void theFirstEntryOnPathWins() throws IOException {
        Path dirFirst = dirTemp.resolve("first");
        Path dirSecond = dirTemp.resolve("second");
        launcher(dirFirst, "dpm", "cache");
        launcher(dirSecond, "dpm", "cache");

        String strPath = dirFirst.resolve("bin") + ":" + dirSecond.resolve("bin");
        ToolchainRoots roots = ToolchainRoots.of(strPath, new HashMap<>(), false, dirTemp);

        assertEquals(dirFirst, roots.dirDpm());
    }


    @Test
    void dpmHomeOutranksTheLauncherOnPath() throws IOException {
        Path dirRoot = dirTemp.resolve(".dpm");
        launcher(dirRoot, "dpm", "cache");
        Map<String, String> mapEnv = new HashMap<>();
        mapEnv.put("DPM_HOME", dirTemp.resolve("owned").toString());

        ToolchainRoots roots = ToolchainRoots.of(dirRoot.resolve("bin").toString(),
                mapEnv, false, dirTemp);

        assertEquals(dirTemp.resolve("owned"), roots.dirDpm());
        // and it says nothing about the assistant
        assertEquals(dirTemp.resolve(".daml"), roots.dirDaml());
    }


    /**
     * `DPM_HOME` names a directory that need not exist yet - that is the point
     * of it - so it is not required to look like a root the way a PATH answer
     * is.
     */
    @Test
    void dpmHomeIsTakenEvenWhenItIsNotThereYet() {
        Map<String, String> mapEnv = new HashMap<>();
        mapEnv.put("DPM_HOME", dirTemp.resolve("not-created").toString());

        ToolchainRoots roots = ToolchainRoots.of(null, mapEnv, false, dirTemp);

        assertEquals(dirTemp.resolve("not-created"), roots.dirDpm());
    }


    @Test
    void aBlankDpmHomeIsIgnoredRatherThanUsedAsAPath() {
        Map<String, String> mapEnv = new HashMap<>();
        mapEnv.put("DPM_HOME", "   ");

        ToolchainRoots roots = ToolchainRoots.of(null, mapEnv, false, dirTemp);

        assertEquals(dirTemp.resolve(".dpm"), roots.dirDpm());
    }


    /**
     * A `bin` on PATH that holds neither launcher, beside empty entries, which
     * a real PATH carries.
     */
    @Test
    void anUnusableOrEmptyPathEntryIsSkipped() throws IOException {
        Path dirRoot = dirTemp.resolve(".daml");
        launcher(dirRoot, "daml", "sdk");

        String strPath = "::" + dirTemp.resolve("gone") + "::" + dirRoot.resolve("bin") + ":";
        ToolchainRoots roots = ToolchainRoots.of(strPath, new HashMap<>(), false, dirTemp);

        assertEquals(dirRoot, roots.dirDaml());
    }


    /** Nothing set is exactly the four-argument answer. */
    @Test
    void nothingSetIsTheOldAnswer() throws IOException {
        Path dirRoot = dirTemp.resolve(".dpm");
        launcher(dirRoot, "dpm", "cache");
        String strPath = dirRoot.resolve("bin").toString();

        assertEquals(ToolchainRoots.of(strPath, new HashMap<>(), false, dirTemp),
                ToolchainRoots.of(strPath, new HashMap<>(), false, dirTemp, null, null));
    }


    /** "Setting wins" - over DPM_HOME and over the launcher on PATH. */
    @Test
    void aSetDpmRootWinsOverDpmHomeAndPath() throws IOException {
        Path dirOnPath = dirTemp.resolve("onpath");
        launcher(dirOnPath, "dpm", "cache");
        Path dirSet = dirTemp.resolve("corporate");
        launcher(dirSet, "dpm", "cache");
        Map<String, String> mapEnv = new HashMap<>();
        mapEnv.put("DPM_HOME", dirTemp.resolve("dpmhome").toString());

        ToolchainRoots roots = ToolchainRoots.of(dirOnPath.resolve("bin").toString(), mapEnv,
                false, dirTemp, null, dirSet);

        assertEquals(dirSet, roots.dirDpm());
        assertEquals(dirSet.resolve("bin").resolve("dpm"), roots.fileDpm());
        assertTrue(roots.flagDpm());
    }


    @Test
    void aSetDamlRootWinsOverPath() throws IOException {
        Path dirOnPath = dirTemp.resolve("onpath");
        launcher(dirOnPath, "daml", "sdk");
        Path dirSet = dirTemp.resolve("corporate");
        launcher(dirSet, "daml", "sdk");

        ToolchainRoots roots = ToolchainRoots.of(dirOnPath.resolve("bin").toString(),
                new HashMap<>(), false, dirTemp, dirSet, null);

        assertEquals(dirSet, roots.dirDaml());
        assertEquals(dirSet.resolve("bin").resolve("daml"), roots.fileDaml());
    }


    /** A set root with no launcher of its own still runs the one on PATH. */
    @Test
    void aSetRootWithoutALauncherKeepsThePathLauncher() throws IOException {
        Path dirOnPath = dirTemp.resolve("onpath");
        launcher(dirOnPath, "daml", "sdk");
        Path dirSet = dirTemp.resolve("corporate");
        Files.createDirectories(dirSet.resolve("sdk"));

        ToolchainRoots roots = ToolchainRoots.of(dirOnPath.resolve("bin").toString(),
                new HashMap<>(), false, dirTemp, dirSet, null);

        assertEquals(dirSet, roots.dirDaml());
        assertEquals(dirOnPath.resolve("bin").resolve("daml"), roots.fileDaml());
    }


    /** Said in the log, not replaced by the default. */
    @Test
    void aSetRootThatDoesNotLookLikeOneIsStillUsed() throws IOException {
        Path dirSet = Files.createDirectories(dirTemp.resolve("empty"));

        ToolchainRoots roots = ToolchainRoots.of(null, new HashMap<>(), false, dirTemp,
                dirSet, dirSet);

        assertEquals(dirSet, roots.dirDaml());
        assertEquals(dirSet, roots.dirDpm());
        assertFalse(roots.flagDaml());
        assertFalse(roots.flagDpm());
    }


    /**
     * WS-7, `DAML_DPM_Splice`: `dpm.exe` in a directory of its own, the cache
     * under `%APPDATA%\dpm` with no `bin`, nothing on PATH. The setting names
     * the launcher and the root is found as though nothing were set.
     */
    @Test
    void theDpmSettingMayNameTheLaunchersOwnDirectory() throws IOException {
        Path dirLauncher = Files.createDirectories(dirTemp.resolve("provision").resolve("bin"));
        Files.createFile(dirLauncher.resolve("dpm.exe"));
        Path dirAppData = dirTemp.resolve("Roaming");
        Files.createDirectories(dirAppData.resolve("dpm").resolve("cache"));
        Map<String, String> mapEnv = new HashMap<>();
        mapEnv.put("APPDATA", dirAppData.toString());

        ToolchainRoots roots = ToolchainRoots.of("", mapEnv, true, dirTemp, null, dirLauncher);

        assertTrue(roots.flagDpm());
        assertEquals(dirLauncher.resolve("dpm.exe"), roots.fileDpm());
        assertEquals(dirAppData.resolve("dpm"), roots.dirDpm());
    }


    /** With the launcher directory set, DPM_HOME still decides the root. */
    @Test
    void aLauncherDirectoryLeavesTheRootToDpmHome() throws IOException {
        Path dirLauncher = Files.createDirectories(dirTemp.resolve("tools"));
        Files.createFile(dirLauncher.resolve("dpm"));
        Map<String, String> mapEnv = new HashMap<>();
        mapEnv.put("DPM_HOME", dirTemp.resolve("dpmhome").toString());

        ToolchainRoots roots = ToolchainRoots.of(null, mapEnv, false, dirTemp, null,
                dirLauncher);

        assertEquals(dirLauncher.resolve("dpm"), roots.fileDpm());
        assertEquals(dirTemp.resolve("dpmhome"), roots.dirDpm());
    }

    /**
     * @param dirRoot the root to build
     * @param strLauncher the launcher's file name, extension included
     * @param strMarker the directory that makes the root recognisable
     */
    private static void launcher(Path dirRoot, String strLauncher, String strMarker)
            throws IOException {
        Files.createDirectories(dirRoot.resolve("bin"));
        Files.createDirectories(dirRoot.resolve(strMarker));
        Files.createFile(dirRoot.resolve("bin").resolve(strLauncher));
    }

}
