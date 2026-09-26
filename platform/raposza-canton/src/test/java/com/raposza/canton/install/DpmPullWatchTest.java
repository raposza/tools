// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The pull figure read off the ingest directory, laid out as measured on
 * 2026-09-23.
 *
 * Author Claude/bentzn
 */
class DpmPullWatchTest {

    @Test
    void theGrowingIngestFileIsTheFigure(@TempDir Path dirDpm) throws Exception {
        Path dirIngest = DpmPullWatch.dirIngest(dirDpm);
        Files.createDirectories(dirIngest);
        Files.write(dirIngest.resolve("eca3_3850372465"), new byte[3 * 1024 * 1024]);

        assertEquals(3L * 1024 * 1024, DpmPullWatch.nBytesPulling(dirDpm, System.currentTimeMillis()));
    }


    /** A LEFTOVER FROM AN INTERRUPTED PULL IS NOT PROGRESS. */
    @Test
    void aStaleIngestFileIsNotCounted(@TempDir Path dirDpm) throws Exception {
        Path dirIngest = DpmPullWatch.dirIngest(dirDpm);
        Files.createDirectories(dirIngest);
        Path fileOld = Files.write(dirIngest.resolve("old_1"), new byte[2048]);
        long nNow = System.currentTimeMillis();
        Files.setLastModifiedTime(fileOld, FileTime.fromMillis(nNow - DpmPullWatch.N_MS_FRESH - 1000));

        assertEquals(0L, DpmPullWatch.nBytesPulling(dirDpm, nNow));
        assertEquals(0L, DpmPullWatch.nBytesPulling(dirDpm.resolve("absent"), nNow));
    }


    @Test
    void theLineIsDpmsWithTheFigureAfterIt() {
        assertEquals("Pulling sdk component canton-open-source 3.5.17... 120 MB",
                DpmPullWatch.strLine("Pulling sdk component canton-open-source 3.5.17...", 120L * 1024 * 1024 + 5));
    }
}
