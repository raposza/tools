// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Author Claude/bentzn
 */
class DpmBootstrapTest {

    private static final String STR_VERSION = "3.5.7";


    /**
     * The name that answers. `linux-x86_64` does not exist for this artefact,
     * and it is the name the Daml SDK uses on the very same version - so the
     * two are easy to cross and the crossing 404s.
     */
    @Test
    void theLinuxArchiveIsAmd64AndATarball() {
        assertEquals("dpm-3.5.7-linux-amd64.tar.gz",
                DpmBootstrap.strArchive(STR_VERSION, HostPlatform.LINUX_X64));
    }


    @Test
    void windowsIsAZipAndMacIsDarwin() {
        assertEquals("dpm-3.5.7-windows-amd64.zip",
                DpmBootstrap.strArchive(STR_VERSION, HostPlatform.WINDOWS_X64));
        assertEquals("dpm-3.5.7-darwin-amd64.tar.gz",
                DpmBootstrap.strArchive(STR_VERSION, HostPlatform.MACOS_X64));
    }


    /**
     * DPM publishes an ARM Linux build, and the Daml SDK's 2.x line publishes
     * no ARM asset at all - so an ARM machine can hold a Canton and no
     * assistant.
     */
    @Test
    void armLinuxIsPublishedHereEvenThoughTheSdkLineIsNot() {
        assertEquals("dpm-3.5.7-linux-arm64.tar.gz",
                DpmBootstrap.strArchive(STR_VERSION, HostPlatform.LINUX_ARM64));
        assertTrue(SdkCatalogue.lstVersion(HostPlatform.LINUX_ARM64).isEmpty());
    }


    @Test
    void theUrlIsTheHostPlusTheArchiveName() {
        assertEquals("https://get.digitalasset.com/install/dpm-sdk/dpm-3.5.7-linux-amd64.tar.gz",
                DpmBootstrap.strUrl(STR_VERSION, HostPlatform.LINUX_X64));
    }


    @Test
    void theExtractedTreeInstallsItself() {
        Path dirExtracted = Path.of("/tmp", "dpm", "extracted");

        List<String> lstCmd = DpmBootstrap.lstCommand(dirExtracted, HostPlatform.LINUX_X64);

        assertEquals(3, lstCmd.size());
        assertEquals(dirExtracted.resolve("bin").resolve("dpm").toString(), lstCmd.get(0));
        assertEquals("bootstrap", lstCmd.get(1));
        assertEquals(dirExtracted.toString(), lstCmd.get(2));
    }


    @Test
    void theWindowsLauncherCarriesItsExtension() {
        Path dirExtracted = Path.of("C:", "work", "dpm");

        assertEquals(dirExtracted.resolve("bin").resolve("dpm.exe"),
                DpmBootstrap.fileLauncher(dirExtracted, HostPlatform.WINDOWS_X64));
    }


    /** One top directory, removed on the way in for a tarball. */
    @Test
    void theArchiveCarriesOneTopDirectory() {
        assertEquals(1, DpmBootstrap.N_STRIP_COMPONENTS);
    }

}
