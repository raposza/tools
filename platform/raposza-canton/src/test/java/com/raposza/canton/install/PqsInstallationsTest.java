// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Author Claude/bentzn
 */
class PqsInstallationsTest {

    @TempDir
    Path dirTemp;

    private Path dirPqs;
    private Path dirDpm;
    private PqsInstallations installations;


    @BeforeEach
    void setUp() {
        dirPqs = dirTemp.resolve(".pqs");
        dirDpm = dirTemp.resolve(".dpm");
        installations = new PqsInstallations(dirPqs, dirDpm);
    }


    @Test
    void noPqsIsANormalAnswer() {
        // Every 2.x Canton line reaches this branch. A caller skips; it does
        // not fail.
        assertTrue(installations.discover().isEmpty());
        assertTrue(installations.resolveLine("2.9").isEmpty());
        assertTrue(installations.forCantonLine("2.9").isEmpty());
    }


    @Test
    void readsTheBannerOfEachStagedBinary() throws IOException {
        stage("3.4.1", "3.4.9", "034");
        stage("3.4.3", "3.4.11", "035");
        stage("3.5.7", "3.5.2", "041");

        List<PqsInstallation> lstFound = installations.discover();
        assertEquals(3, lstFound.size());

        // Newest first.
        assertEquals(VersionId.of(3, 5, 7), lstFound.get(0).version());
        assertEquals("041", lstFound.get(0).strSchemaRevision());
        assertEquals("3.5", lstFound.get(0).strCantonLine());
        assertEquals("034", lstFound.get(2).strSchemaRevision());
    }


    @Test
    void twoBinariesOnOneLineShareOneDatabase() throws IOException {
        stage("3.4.1", "3.4.9", "034");
        stage("3.4.3", "3.4.11", "035");

        List<PqsInstallation> lstFound = installations.discover();
        PqsInstallation regression = lstFound.get(1);
        PqsInstallation canonical = lstFound.get(0);

        assertEquals("3.4", regression.strCantonLine());
        assertEquals("3.4", canonical.strCantonLine());
        assertNotEquals(regression.strSchemaRevision(), canonical.strSchemaRevision());
        // THE REVISION DIFFERS AND THE DATABASE DOES NOT. The name is the
        // prefix, on every version.
        assertEquals(regression.databaseName("pqs"), canonical.databaseName("pqs"));
        assertEquals("pqs", canonical.databaseName("pqs"));
    }


    @Test
    void resolvesThroughTheLineAlias() throws IOException {
        stage("3.4.1", "3.4.9", "034");
        stage("3.4.3", "3.4.11", "035");
        alias("3.4", "3.4.3");

        Optional<Path> optJar = installations.resolveLine("3.4");
        assertTrue(optJar.isPresent());

        Optional<PqsInstallation> optInst = installations.forCantonLine("3.4");
        assertTrue(optInst.isPresent());
        // The line points at the canonical binary, not at the highest patch by
        // accident and not at the defective 3.4.1.
        assertEquals(VersionId.of(3, 4, 3), optInst.get().version());
        assertEquals("035", optInst.get().strSchemaRevision());
    }


    @Test
    void walksTheDpmCacheInsteadOfComposingItsPath() throws IOException {
        // The 3.5 component is flat; the 3.4 one inserts a generation segment.
        // A path template built for either misses the other.
        cacheJar(dirDpm.resolve("cache/components/scribe/3.5.7"));
        cacheJar(dirDpm.resolve("cache/components/scribe/daml3.4/3.4.1"));

        List<Path> lstJar = installations.discoverDpmCacheJars();
        assertEquals(2, lstJar.size());
    }


    @Test
    void fallsBackToTheDirectoryNameWithoutABanner() throws IOException {
        Path dirVersion = dirPqs.resolve("3.5.7");
        Files.createDirectories(dirVersion);
        write(dirVersion.resolve("scribe.jar"));

        List<PqsInstallation> lstFound = installations.discover();
        assertEquals(1, lstFound.size());
        assertEquals(VersionId.of(3, 5, 7), lstFound.get(0).version());
        // No banner means no schema revision. It is not invented.
        assertNull(lstFound.get(0).strSchemaRevision());
    }


    @Test
    void skipsTheWrapperAndAliasDirectories() throws IOException {
        stage("3.5.7", "3.5.2", "041");
        Files.createDirectories(dirPqs.resolve("bin"));
        write(dirPqs.resolve("bin").resolve("scribe"));
        alias("3.5", "3.5.7");

        assertEquals(1, installations.discover().size());
    }


    private void stage(String strVersion, String strSdk, String strSchema) throws IOException {
        Path dirVersion = dirPqs.resolve(strVersion);
        Files.createDirectories(dirVersion);
        write(dirVersion.resolve("scribe.jar"));
        Files.writeString(dirVersion.resolve("VERSION.txt"),
                "scribe, version: v" + strVersion + "\n"
                        + "daml-sdk.version: " + strSdk + "\n"
                        + "postgres-document.schema: " + strSchema + "\n",
                StandardCharsets.UTF_8);
    }


    /**
     * A real directory rather than a symbolic link, because resolution must not
     * depend on which of the two the staging tool used.
     */
    private void alias(String strLine, String strVersion) throws IOException {
        Path dirAlias = dirPqs.resolve("line").resolve(strLine);
        Files.createDirectories(dirAlias);
        Files.copy(dirPqs.resolve(strVersion).resolve("scribe.jar"), dirAlias.resolve("scribe.jar"));
        Path fileVersion = dirPqs.resolve(strVersion).resolve("VERSION.txt");
        if (Files.exists(fileVersion))
            Files.copy(fileVersion, dirAlias.resolve("VERSION.txt"));
    }


    private static void cacheJar(Path dirVersion) throws IOException {
        Files.createDirectories(dirVersion);
        write(dirVersion.resolve("scribe.jar"));
    }


    private static void write(Path file) throws IOException {
        Files.write(file, "not really a jar".getBytes(StandardCharsets.UTF_8));
    }
}
