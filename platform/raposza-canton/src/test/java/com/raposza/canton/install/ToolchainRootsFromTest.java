// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where a root with no setting comes from, in the words a blank Settings field
 * shows - R3, his handover of 2026-10-30. The platform is an argument, as in
 * {@link ToolchainRootsTest}.
 *
 * Author Claude/bentzn
 */
class ToolchainRootsFromTest {

    @TempDir
    Path dirTemp;


    private Path launcher(String strRoot, String strMarker, String strName) throws IOException {
        Path dirRoot = dirTemp.resolve(strRoot);
        Files.createDirectories(dirRoot.resolve(strMarker));
        Path fileLauncher = dirRoot.resolve("bin").resolve(strName);
        Files.createDirectories(fileLauncher.getParent());
        Files.writeString(fileLauncher, "");
        return fileLauncher;
    }


    @Test
    void nothingIsThePlatformDefault() {
        assertEquals(ToolchainRoots.STR_FROM_DEFAULT,
                ToolchainRoots.strFromDpm("", new HashMap<>(), false));
        assertEquals(ToolchainRoots.STR_FROM_DEFAULT, ToolchainRoots.strFromDaml(null, false));
    }


    @Test
    void aLauncherOnPathInARootIsPath() throws IOException {
        Path fileDpm = launcher("dpmroot", ToolchainRoots.STR_MARKER_DPM, "dpm");
        Path fileDaml = launcher("damlroot", ToolchainRoots.STR_MARKER_DAML, "daml");
        String strPath = fileDpm.getParent() + ":" + fileDaml.getParent();
        assertEquals(ToolchainRoots.STR_FROM_PATH,
                ToolchainRoots.strFromDpm(strPath, new HashMap<>(), false));
        assertEquals(ToolchainRoots.STR_FROM_PATH, ToolchainRoots.strFromDaml(strPath, false));
    }


    /** A launcher outside a root does not name one - {@link ToolchainRoots}. */
    @Test
    void aLauncherOutsideARootIsTheDefault() throws IOException {
        Path fileDpm = dirTemp.resolve("tools").resolve("dpm");
        Files.createDirectories(fileDpm.getParent());
        Files.writeString(fileDpm, "");
        assertEquals(ToolchainRoots.STR_FROM_DEFAULT,
                ToolchainRoots.strFromDpm(fileDpm.getParent().toString(), new HashMap<>(), false));
    }


    @Test
    void dpmHomeOutranksPath() throws IOException {
        Path fileDpm = launcher("dpmroot", ToolchainRoots.STR_MARKER_DPM, "dpm");
        Map<String, String> mapEnv = new HashMap<>();
        mapEnv.put(ToolchainRoots.STR_ENV_DPM_HOME, dirTemp.resolve("elsewhere").toString());
        assertEquals(ToolchainRoots.STR_FROM_DPM_HOME,
                ToolchainRoots.strFromDpm(fileDpm.getParent().toString(), mapEnv, false));
    }


    @Test
    void theWordsMatchTheRootsOfReturns() throws IOException {
        Path fileDpm = launcher("dpmroot", ToolchainRoots.STR_MARKER_DPM, "dpm");
        ToolchainRoots roots = ToolchainRoots.of(fileDpm.getParent().toString(), new HashMap<>(),
                false, dirTemp);
        assertEquals(dirTemp.resolve("dpmroot"), roots.dirDpm());
        assertEquals(ToolchainRoots.STR_FROM_PATH,
                ToolchainRoots.strFromDpm(fileDpm.getParent().toString(), new HashMap<>(), false));
    }
}
