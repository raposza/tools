// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.sandbox.app.DiscoveryDar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Which store DARs the discovery document lists, and as what.
 *
 * Author Claude/bentzn
 */
class DarsListingTest {

    private static final String STR_ID_A =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static final String STR_ID_B =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private static final String STR_ID_C =
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";


    @Test
    void tickedIsSelectedLiveIsUploadedTheRestIsLeftOut(@TempDir Path dir) throws IOException {
        Path fileA = writeDar(dir.resolve("a.dar"), "alpha", "1.0.0", STR_ID_A);
        Path fileB = writeDar(dir.resolve("b.dar"), "beta", "2.0.0", STR_ID_B);
        Path fileC = writeDar(dir.resolve("c.dar"), "gamma", "3.0.0", STR_ID_C);

        List<DiscoveryDar> lstDar = new DarsListing().lstOf(List.of(fileA, fileB, fileC),
                List.of(fileA, fileB), Set.of(STR_ID_B));

        assertEquals(2, lstDar.size());
        assertEquals("alpha", lstDar.get(0).strName());
        assertEquals(DiscoveryDar.STR_STATUS_SELECTED, lstDar.get(0).strStatus());
        assertEquals("beta", lstDar.get(1).strName());
        assertEquals(DiscoveryDar.STR_STATUS_UPLOADED, lstDar.get(1).strStatus(),
                "the participant having it wins over the tick");
        assertEquals(STR_ID_B, lstDar.get(1).strPkgId());
    }


    /** An unticked DAR the participant has is listed - LocalNetND ticks none. */
    @Test
    void anUntickedDarOnTheParticipantIsListed(@TempDir Path dir) throws IOException {
        Path fileC = writeDar(dir.resolve("c.dar"), "gamma", "3.0.0", STR_ID_C);

        List<DiscoveryDar> lstDar = new DarsListing().lstOf(DarsListing.lstDarsIn(dir), List.of(),
                Set.of(STR_ID_C));

        assertEquals(1, lstDar.size());
        assertEquals(fileC.toAbsolutePath().normalize(), lstDar.get(0).fileDar());
    }


    @Test
    void anUnreadableDarIsLeftOut(@TempDir Path dir) throws IOException {
        Path fileJunk = dir.resolve("junk.dar");
        Files.writeString(fileJunk, "not a zip");

        DarsListing listing = new DarsListing();

        assertNull(listing.infoOf(fileJunk));
        assertTrue(listing.lstOf(List.of(fileJunk), List.of(fileJunk), Set.of()).isEmpty());
    }


    /** A DAR rewritten under the same name is read again. */
    @Test
    void aChangedFileIsReadAgain(@TempDir Path dir) throws IOException {
        Path fileA = writeDar(dir.resolve("a.dar"), "alpha", "1.0.0", STR_ID_A);
        DarsListing listing = new DarsListing();
        assertEquals(STR_ID_A, listing.infoOf(fileA).strPkgId());

        writeDar(fileA, "alpha", "1.0.10", STR_ID_B);
        Files.setLastModifiedTime(fileA, java.nio.file.attribute.FileTime.fromMillis(
                Files.getLastModifiedTime(fileA).toMillis() + 2000));

        assertEquals(STR_ID_B, listing.infoOf(fileA).strPkgId());
    }


    @Test
    void anAbsentStoreListsNothing(@TempDir Path dir) {
        assertTrue(DarsListing.lstDarsIn(dir.resolve("gone")).isEmpty());
        assertTrue(DarsListing.lstDarsIn(null).isEmpty());
    }


    private static Path writeDar(Path fileDar, String strName, String strVersion, String strId)
            throws IOException {
        String strMain = strName + "-" + strVersion + "-" + strId + "/" + strName + "-" + strVersion
                + "-" + strId + ".dalf";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(fileDar))) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write(("Manifest-Version: 1.0\r\nMain-Dalf: " + strMain + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return fileDar;
    }

}
