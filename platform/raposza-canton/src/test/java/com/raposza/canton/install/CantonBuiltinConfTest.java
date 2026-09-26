// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

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
 * The vendor topology comes out of the jar, which is what keeps this project
 * from hand-writing a 3.x one under the daemon launcher.
 *
 * Author Claude/bentzn
 */
class CantonBuiltinConfTest {

    private static final String STR_BODY = "canton { participants { sandbox {} } }";


    @Test
    void listsEveryConfSpelledAsTheJarSpellsIt(@TempDir Path dir) throws IOException {
        Path fileJar = writeJar(dir.resolve("canton.jar"));

        List<String> lstEntry = CantonBuiltinConf.list(fileJar);
        assertTrue(lstEntry.contains(CantonBuiltinConf.STR_ENTRY_SANDBOX), lstEntry.toString());
        assertTrue(lstEntry.contains("application.conf"), lstEntry.toString());
        for (String strEntry : lstEntry) {
            assertTrue(strEntry.endsWith(".conf"), strEntry);
        }
        assertEquals(lstEntry.stream().sorted().toList(), lstEntry);
    }


    @Test
    void extractsTheSandboxTopologyUnderItsBaseName(@TempDir Path dir) throws IOException {
        Path fileJar = writeJar(dir.resolve("canton.jar"));

        Path fileConf = CantonBuiltinConf.extract(fileJar, CantonBuiltinConf.STR_ENTRY_SANDBOX,
                dir.resolve("out"));
        assertEquals("sandbox.conf", fileConf.getFileName().toString());
        assertEquals(STR_BODY, Files.readString(fileConf));
    }


    @Test
    void anAbsentEntryNamesWhatTheJarDoesCarry(@TempDir Path dir) throws IOException {
        Path fileJar = writeJar(dir.resolve("canton.jar"));

        InstallException ex = assertThrows(InstallException.class,
                () -> CantonBuiltinConf.extract(fileJar, "sandbox/nope.conf", dir.resolve("out")));
        assertTrue(ex.getMessage().contains(CantonBuiltinConf.STR_ENTRY_SANDBOX), ex.getMessage());
    }


    @Test
    void anInstallationWithNoRuntimeIsRefused(@TempDir Path dir) {
        CantonInstallation install = new CantonInstallation(VersionId.parse("3.5.11"),
                Edition.OPEN_SOURCE, InstallSource.DPM, dir, null);

        assertThrows(InstallException.class,
                () -> CantonBuiltinConf.extractSandboxConf(install, dir.resolve("out")));
    }


    private static Path writeJar(Path fileJar) throws IOException {
        try (OutputStream out = Files.newOutputStream(fileJar);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, CantonBuiltinConf.STR_ENTRY_SANDBOX, STR_BODY);
            put(zip, "application.conf", "pekko {}");
            put(zip, "com/digitalasset/canton/Foo.class", "not a conf");
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
