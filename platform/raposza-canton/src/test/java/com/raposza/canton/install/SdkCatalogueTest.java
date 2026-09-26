// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The asset names here were read off the published releases. Every assertion is
 * a name that exists rather than a name a rule would produce.
 *
 * Author Claude/bentzn
 */
class SdkCatalogueTest {

    @Test
    void theTwoLinesNameTheirAssetsDifferently() {
        assertEquals("daml-sdk-2.10.4-linux.tar.gz",
                SdkCatalogue.strAsset(VersionId.of(2, 10, 4), HostPlatform.LINUX_X64));
        assertEquals("daml-sdk-3.4.11-linux-x86_64.tar.gz",
                SdkCatalogue.strAsset(VersionId.of(3, 4, 11), HostPlatform.LINUX_X64));
    }


    /**
     * The rename that makes `daml install &lt;3.x&gt;` 404 on Windows and
     * succeed on Linux: 3.x publishes no plain `-windows` asset, and the 2.x
     * assistant constructs one.
     */
    @Test
    void windowsIsQualifiedOnThreeAndBareOnTwo() {
        assertEquals("daml-sdk-2.9.6-windows.tar.gz",
                SdkCatalogue.strAsset(VersionId.of(2, 9, 6), HostPlatform.WINDOWS_X64));
        assertEquals("daml-sdk-3.4.11-windows-x86_64.tar.gz",
                SdkCatalogue.strAsset(VersionId.of(3, 4, 11), HostPlatform.WINDOWS_X64));
    }


    /** 2.x published no ARM asset, so there is nothing to name. */
    @Test
    void theTwoLineHasNoArmAsset() {
        assertNull(SdkCatalogue.strAsset(VersionId.of(2, 10, 4), HostPlatform.LINUX_ARM64));
        assertEquals("daml-sdk-3.4.11-linux-aarch64.tar.gz",
                SdkCatalogue.strAsset(VersionId.of(3, 4, 11), HostPlatform.LINUX_ARM64));
    }


    @Test
    void theUrlCarriesTheTagAndTheAsset() {
        assertEquals("https://github.com/digital-asset/daml/releases/download/"
                        + "v2.10.6/daml-sdk-2.10.6-linux.tar.gz",
                SdkCatalogue.strUrl(VersionId.of(2, 10, 6), HostPlatform.LINUX_X64));
    }


    @Test
    void anArmMachineHasNothingToInstallFromThisCatalogue() {
        assertTrue(SdkCatalogue.lstVersion(HostPlatform.LINUX_ARM64).isEmpty());
        assertNull(SdkCatalogue.strUrl(VersionId.of(2, 9, 6), HostPlatform.LINUX_ARM64));
    }


    @Test
    void theListStartsAtTheOldestAgreedVersionAndIsNewestFirst() {
        List<VersionId> lstVersion = SdkCatalogue.lstVersion(HostPlatform.LINUX_X64);

        assertEquals(13, lstVersion.size());
        assertEquals(VersionId.of(2, 10, 6), lstVersion.get(0));
        assertEquals(VersionId.of(2, 8, 12), lstVersion.get(lstVersion.size() - 1));
    }


    /**
     * The gaps are the published set, not an oversight. A version added here
     * that was never released fails at download time with a 404 rather than
     * anywhere the catalogue could report it.
     */
    @Test
    void theGapsInTheTableAreDeliberate() {
        assertTrue(SdkCatalogue.flagKnown(VersionId.of(2, 9, 1)));
        assertFalse(SdkCatalogue.flagKnown(VersionId.of(2, 9, 2)));
        assertTrue(SdkCatalogue.flagKnown(VersionId.of(2, 9, 3)));
        assertTrue(SdkCatalogue.flagKnown(VersionId.of(2, 10, 4)));
        assertFalse(SdkCatalogue.flagKnown(VersionId.of(2, 10, 5)));
        assertTrue(SdkCatalogue.flagKnown(VersionId.of(2, 10, 6)));
    }


    /** Nothing below the agreed floor, and no 3.x - that channel is DPM's. */
    @Test
    void theCatalogueStopsAtTheFloorAndAtTheLine() {
        assertFalse(SdkCatalogue.flagKnown(VersionId.of(2, 8, 11)));
        assertFalse(SdkCatalogue.flagKnown(VersionId.of(3, 4, 11)));
    }


    /**
     * The Windows `.exe` installer reports a normal completion having installed
     * nothing, so the tarball's own installer is the only path.
     */
    @Test
    void theInstallerIsTheOneInsideTheTarball() {
        assertEquals("install.bat", SdkCatalogue.strInstaller(HostPlatform.WINDOWS_X64));
        assertEquals("install.sh", SdkCatalogue.strInstaller(HostPlatform.LINUX_X64));
        assertEquals("install.sh", SdkCatalogue.strInstaller(HostPlatform.MACOS_X64));
    }

}
