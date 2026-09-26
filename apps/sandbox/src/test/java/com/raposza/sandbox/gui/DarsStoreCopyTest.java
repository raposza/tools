// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.admin.AdminPackages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An uploaded DAR lands in the version's store - his question of 2026-09-23,
 * "When they are uploaded, are they also copied to the dir of that version?"
 *
 * THE CONTROL CAN FAIL: a different file of the same name is already in the
 * store and must survive byte for byte, which a plain copy-with-replace would
 * not.
 *
 * Author Claude/bentzn
 */
class DarsStoreCopyTest {

    @Test
    void aNewDarIsCopiedAndASecondUploadWritesNothing(@TempDir Path dirTmp) throws IOException {
        Path fileDar = Files.write(dirTmp.resolve("aviation-0.0.1.dar"), new byte[] { 1, 2, 3 });
        Path dirStore = dirTmp.resolve("sandbox").resolve("3.5.18-open_source").resolve("dars");

        assertTrue(DarsLivePane.strCopyToStore(fileDar, dirStore).isEmpty());
        assertArrayEquals(new byte[] { 1, 2, 3 },
                Files.readAllBytes(dirStore.resolve("aviation-0.0.1.dar")));
        assertTrue(DarsLivePane.strCopyToStore(fileDar, dirStore).isEmpty());
    }


    /** The log names a DAR, not a 64-character id - his "clean up the log". */
    @Test
    void aDarIsNamedByNameAndVersion() {
        assertEquals("aviation 0.0.1", DarsLivePane.strDarLabel("3ff5c21555a9", "aviation",
                "0.0.1"));
        assertEquals("3ff5c21555a9...", DarsLivePane.strDarLabel(
                "3ff5c21555a92c2595139c4698e9d511", null, null));
    }


    /**
     * AN UNVET THAT DID NOT LAND IS NOT REPORTED AS DONE - his `unvetted pharma
     * 0.0.1` beside a Vetted column that still said yes, 2026-09-23.
     */
    @Test
    void aVettingChangeIsReportedByWhatTheParticipantThenSays() {
        assertEquals("pharma 0.0.1 unvetted", DarsLivePane.strOutcome(AdminPackages.STR_UNVET,
                "unvetted", "pharma 0.0.1", Set.of("other"), "main"));
        assertTrue(DarsLivePane.strOutcome(AdminPackages.STR_UNVET, "unvetted", "pharma 0.0.1",
                Set.of("main"), "main").startsWith("pharma 0.0.1 NOT unvetted"));
        assertTrue(DarsLivePane.strOutcome(AdminPackages.STR_VET, "vetted", "pharma 0.0.1",
                Set.of("other"), "main").startsWith("pharma 0.0.1 NOT vetted"));
        assertEquals("aviation 0.0.1 removed", DarsLivePane.strOutcome(
                AdminPackages.STR_REMOVE_DAR, "removed", "aviation 0.0.1", Set.of(), "main"));
    }


    @Test
    void aDifferentFileOfTheSameNameIsNeverOverwritten(@TempDir Path dirTmp) throws IOException {
        Path dirStore = Files.createDirectories(dirTmp.resolve("dars"));
        Files.write(dirStore.resolve("model.dar"), new byte[] { 9 });
        Path fileDar = Files.write(dirTmp.resolve("model.dar"), new byte[] { 1 });

        assertTrue(DarsLivePane.strCopyToStore(fileDar, dirStore).contains("NOT copied"));
        assertArrayEquals(new byte[] { 9 }, Files.readAllBytes(dirStore.resolve("model.dar")));
    }


    @Test
    void noStoreMeansNoCopyAndAFileAlreadyThereIsSaidSo(@TempDir Path dirTmp)
            throws IOException {
        Path fileDar = Files.write(dirTmp.resolve("a.dar"), new byte[] { 1 });
        assertEquals("", DarsLivePane.strCopyToStore(fileDar, null));
        assertTrue(DarsLivePane.strCopyToStore(fileDar, dirTmp).isEmpty());
    }
}
