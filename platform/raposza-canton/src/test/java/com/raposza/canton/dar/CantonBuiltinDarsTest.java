// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.dar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fixture jar carries the five entries measured in the 3.5.11 runtime, with
 * the two copies of each admin workflow deliberately holding DIFFERENT bytes -
 * which is what the real jar does and what any deduplication here would hide.
 *
 * Author Claude/bentzn
 */
class CantonBuiltinDarsTest {

    private static final String STR_TOP_PING = "canton-builtin-admin-workflow-ping.dar";

    private static final String STR_NESTED_PING = "dar/canton-builtin-admin-workflow-ping.dar";


    @Test
    void listsEveryDarSpelledAsTheJarSpellsIt(@TempDir Path dir) throws IOException {
        Path fileJar = writeJar(dir.resolve("canton.jar"));

        List<String> lstEntry = CantonBuiltinDars.list(fileJar);
        assertEquals(5, lstEntry.size());
        assertTrue(lstEntry.contains(STR_TOP_PING), lstEntry.toString());
        assertTrue(lstEntry.contains(STR_NESTED_PING), lstEntry.toString());
        assertTrue(lstEntry.contains(CantonBuiltinDars.STR_ENTRY_MODEL_TESTS),
                lstEntry.toString());
        // Sorted, so two runs on two machines agree.
        assertEquals(lstEntry.stream().sorted().toList(), lstEntry);
    }


    @Test
    void classFilesAndOtherEntriesAreNotDars(@TempDir Path dir) throws IOException {
        Path fileJar = writeJar(dir.resolve("canton.jar"));

        for (String strEntry : CantonBuiltinDars.list(fileJar))
            assertTrue(strEntry.endsWith(".dar"), strEntry);
    }


    @Test
    void theTwoCopiesAreNotCollapsed(@TempDir Path dir) throws IOException {
        // The whole reason extract() takes an entry name and not a base name.
        Path fileJar = writeJar(dir.resolve("canton.jar"));

        Path dirTop = Files.createDirectories(dir.resolve("top"));
        Path dirNested = Files.createDirectories(dir.resolve("nested"));
        Path fileTop = CantonBuiltinDars.extract(fileJar, STR_TOP_PING, dirTop);
        Path fileNested = CantonBuiltinDars.extract(fileJar, STR_NESTED_PING, dirNested);

        assertEquals("canton-builtin-admin-workflow-ping.dar", fileTop.getFileName().toString());
        assertEquals("canton-builtin-admin-workflow-ping.dar",
                fileNested.getFileName().toString());
        assertTrue(Files.size(fileTop) != Files.size(fileNested),
                "the fixture is meant to hold two different files");
    }


    @Test
    void extractCreatesTheTargetDirectory(@TempDir Path dir) throws IOException {
        Path fileJar = writeJar(dir.resolve("canton.jar"));

        Path dirTo = dir.resolve("does/not/exist");
        Path fileOut = CantonBuiltinDars.extract(fileJar,
                CantonBuiltinDars.STR_ENTRY_MODEL_TESTS, dirTo);

        assertTrue(Files.isRegularFile(fileOut));
        assertTrue(fileOut.isAbsolute());
    }


    @Test
    void extractIsRepeatable(@TempDir Path dir) throws IOException {
        Path fileJar = writeJar(dir.resolve("canton.jar"));
        Path dirTo = dir.resolve("out");

        Path first = CantonBuiltinDars.extract(fileJar, STR_TOP_PING, dirTo);
        Path second = CantonBuiltinDars.extract(fileJar, STR_TOP_PING, dirTo);

        assertEquals(first, second);
        assertTrue(Files.size(second) > 0L);
    }


    @Test
    void anAbsentEntryListsWhatThereIs(@TempDir Path dir) throws IOException {
        Path fileJar = writeJar(dir.resolve("canton.jar"));

        DarException ex = assertThrows(DarException.class,
                () -> CantonBuiltinDars.extract(fileJar, "no-such.dar", dir.resolve("out")));
        assertTrue(ex.getMessage().contains("no-such.dar"), ex.getMessage());
        assertTrue(ex.getMessage().contains(STR_TOP_PING), ex.getMessage());
    }


    @Test
    void aJarWithNoDarsIsEmptyRatherThanAFailure(@TempDir Path dir) throws IOException {
        Path fileJar = dir.resolve("plain.jar");
        try (OutputStream out = Files.newOutputStream(fileJar);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "com/example/Thing.class", "x");
        }

        assertTrue(CantonBuiltinDars.list(fileJar).isEmpty());
    }


    @Test
    void anUnreadableJarIsNamedInTheFailure(@TempDir Path dir) throws IOException {
        Path fileJar = dir.resolve("broken.jar");
        Files.writeString(fileJar, "not a zip");

        DarException ex = assertThrows(DarException.class, () -> CantonBuiltinDars.list(fileJar));
        assertTrue(ex.getMessage().contains("broken.jar"), ex.getMessage());
    }


    private static Path writeJar(Path fileJar) throws IOException {
        try (OutputStream out = Files.newOutputStream(fileJar);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "com/digitalasset/canton/Thing.class", "not a dar");
            put(zip, "canton-builtin-admin-workflow-party-replication-alpha.dar", "pra-top");
            put(zip, STR_TOP_PING, "ping-top");
            put(zip, "dar/canton-builtin-admin-workflow-party-replication-alpha.dar",
                    "pra-nested-longer");
            put(zip, STR_NESTED_PING, "ping-nested-longer");
            put(zip, CantonBuiltinDars.STR_ENTRY_MODEL_TESTS, "model-tests");
        }
        return fileJar;
    }


    private static void put(ZipOutputStream zip, String strEntry, String strBody)
            throws IOException {
        zip.putNextEntry(new ZipEntry(strEntry));
        zip.write(strBody.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

}
