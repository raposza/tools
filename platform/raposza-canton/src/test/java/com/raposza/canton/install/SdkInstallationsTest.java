// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Author Claude/bentzn
 */
class SdkInstallationsTest {

    /** `bin` and a stray file are not versions and are not counted. */
    @Test
    void everyVersionDirectoryCountsAndNothingElseDoes(@TempDir Path dirTmp) throws IOException {
        Path dirSdk = Files.createDirectories(dirTmp.resolve(SdkInstallations.STR_DIR_SDK));
        Files.createDirectories(dirSdk.resolve("2.10.4"));
        Files.createDirectories(dirSdk.resolve("2.8.12"));
        Files.createDirectories(dirSdk.resolve("bin"));
        Files.createFile(dirSdk.resolve("2.9.9"));

        Set<VersionId> setFound = SdkInstallations.setUnder(dirSdk);

        assertEquals(2, setFound.size());
        assertTrue(setFound.contains(VersionId.parse("2.10.4")));
        assertTrue(setFound.contains(VersionId.parse("2.8.12")));
        assertFalse(setFound.contains(VersionId.parse("2.9.9")));
    }


    /** A machine with no assistant answers, rather than failing. */
    @Test
    void anAbsentRootIsEmpty(@TempDir Path dirTmp) {
        assertTrue(SdkInstallations.setUnder(dirTmp.resolve("nothing")).isEmpty());
    }


    /** The SDK directory is found under the root the environment describes. */
    @Test
    void theRootsFormReadsTheSdkDirectory(@TempDir Path dirTmp) throws IOException {
        Files.createDirectories(dirTmp.resolve(SdkInstallations.STR_DIR_SDK).resolve("2.10.6"));
        ToolchainRoots roots = new ToolchainRoots(dirTmp, dirTmp.resolve("dpm"), null, null);

        assertTrue(SdkInstallations.setInstalled(roots).contains(VersionId.parse("2.10.6")));
    }

}
