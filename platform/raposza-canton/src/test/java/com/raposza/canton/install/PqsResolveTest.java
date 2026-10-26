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
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PqsInstallations#resolve}: the staged alias, then the DPM cache, then
 * the jar the settings name - raposza/tools#4.
 *
 * The banner is a map rather than a JVM launch, so the order is asserted on a
 * machine with no scribe at all.
 *
 * Author Claude/bentzn
 */
class PqsResolveTest {

    @TempDir
    Path dirTemp;

    private Path dirPqs;
    private Path dirDpm;
    private Path dirScribe;
    private final Map<Path, String> mapBanner = new HashMap<>();


    @BeforeEach
    void setUp() {
        dirPqs = dirTemp.resolve(".pqs");
        dirDpm = dirTemp.resolve("AppData/Roaming/dpm");
        dirScribe = dirDpm.resolve("cache/components/scribe");
    }


    /** The layout DPM left on the Windows guest, 2026-10-24: two names, two lines. */
    @Test
    void theDpmCacheAnswersEachLineWhateverTheJarIsCalled() throws IOException {
        Path fileJar35 = jar(dirScribe.resolve("3.5.8/scribe.jar"), "3.5.8");
        Path fileJar36 = jar(dirScribe.resolve("3.6.2/pqs.jar"), "3.6.2");
        PqsInstallations installations = installations(null);

        PqsResolution res35 = installations.resolve("3.5");
        assertTrue(res35.isFound());
        assertEquals(fileJar35, res35.installation().fileJar());
        assertEquals(VersionId.of(3, 5, 8), res35.installation().version());
        assertEquals(PqsSource.DPM, res35.installation().source());
        assertEquals("041", res35.installation().strSchemaRevision());

        PqsResolution res36 = installations.resolve("3.6");
        assertEquals(fileJar36, res36.installation().fileJar());
    }


    @Test
    void theNewestOfTheLineInTheDpmCacheWins() throws IOException {
        jar(dirScribe.resolve("3.5.7/scribe.jar"), "3.5.7");
        Path fileNewer = jar(dirScribe.resolve("3.5.8/scribe.jar"), "3.5.8");
        assertEquals(fileNewer, installations(null).resolve("3.5").installation().fileJar());
    }


    @Test
    void aDpmJarWithoutABannerIsTakenAtItsDirectoryVersion() throws IOException {
        Path fileJar = dirScribe.resolve("daml3.4/3.4.3/scribe.jar");
        Files.createDirectories(fileJar.getParent());
        Files.writeString(fileJar, "not a jar", StandardCharsets.UTF_8);

        PqsResolution res = installations(null).resolve("3.4");
        assertEquals(fileJar, res.installation().fileJar());
        assertNull(res.installation().strSchemaRevision());
    }


    @Test
    void aDpmJarWhoseBannerNamesAnotherLineIsPassedOver() throws IOException {
        jar(dirScribe.resolve("3.5.8/scribe.jar"), "3.6.2");
        assertFalse(installations(null).resolve("3.5").isFound());
    }


    @Test
    void theStagedAliasStillWinsOverTheDpmCache() throws IOException {
        jar(dirScribe.resolve("3.4.1/scribe.jar"), "3.4.1");
        Path dirAlias = dirPqs.resolve("line/3.4");
        Files.createDirectories(dirAlias);
        Files.writeString(dirAlias.resolve("scribe.jar"), "staged", StandardCharsets.UTF_8);
        Files.writeString(dirAlias.resolve("VERSION.txt"), banner("3.4.3"),
                StandardCharsets.UTF_8);

        PqsResolution res = installations(null).resolve("3.4");
        assertEquals(dirAlias.resolve("scribe.jar"), res.installation().fileJar());
        assertEquals(VersionId.of(3, 4, 3), res.installation().version());
    }


    @Test
    void nothingFoundSaysWhereItLooked() {
        PqsResolution res = installations(null).resolve("3.5");
        assertFalse(res.isFound());
        assertTrue(res.strWhyNone().contains(dirScribe.toString()), res.strWhyNone());
        assertTrue(res.strWhyNone().contains("no PQS scribe.jar is set in Settings"),
                res.strWhyNone());
    }


    /** Any name, any directory: the jar the settings name answers for its own line. */
    @Test
    void theSetJarAnswersForItsOwnLine() throws IOException {
        Path fileSet = jar(dirTemp.resolve("vendor/pqs-3.5.7-all.jar"), "3.5.7");
        PqsResolution res = installations(fileSet).resolve("3.5");
        assertEquals(fileSet, res.installation().fileJar());
        assertEquals(PqsSource.MANUAL, res.installation().source());
    }


    @Test
    void theDpmCacheWinsOverTheSetJar() throws IOException {
        Path fileDpm = jar(dirScribe.resolve("3.5.8/scribe.jar"), "3.5.8");
        Path fileSet = jar(dirTemp.resolve("vendor/scribe.jar"), "3.5.7");
        assertEquals(fileDpm, installations(fileSet).resolve("3.5").installation().fileJar());
    }


    /** Set once, for one SDK, and tried for every other: a mismatch is said, not run. */
    @Test
    void theSetJarOnAnotherLineIsAMismatch() throws IOException {
        Path fileSet = jar(dirTemp.resolve("vendor/scribe.jar"), "3.6.2");
        PqsResolution res = installations(fileSet).resolve("3.5");
        assertFalse(res.isFound());
        assertTrue(res.strWhyNone().startsWith("PQS mismatched: "), res.strWhyNone());
        assertTrue(res.strWhyNone().contains("scribe 3.6.2, for Canton 3.6"), res.strWhyNone());
        assertTrue(res.strWhyNone().contains("runs Canton 3.5"), res.strWhyNone());
    }


    /** 2.x scribe is versioned 0.x: its banner names the generation, not the line. */
    @Test
    void aZeroDotScribeIsTheTwoDotGeneration() throws IOException {
        Path fileSet = jar(dirTemp.resolve("vendor/scribe-2x.jar"), "0.5.5");
        PqsInstallations installations = installations(fileSet);

        PqsResolution res210 = installations.resolve("2.10");
        assertEquals(fileSet, res210.installation().fileJar());
        assertEquals("2.10", res210.installation().strCantonLine());
        assertTrue(res210.installation().isCanton2x());

        PqsResolution res35 = installations.resolve("3.5");
        assertTrue(res35.strWhyNone().contains("for Canton 2.x"), res35.strWhyNone());
    }


    @Test
    void aSetJarThatIsNotThereIsSaid() {
        Path fileSet = dirTemp.resolve("gone/scribe.jar");
        PqsResolution res = installations(fileSet).resolve("3.5");
        assertTrue(res.strWhyNone().contains(fileSet + ", is not a file"), res.strWhyNone());
    }


    @Test
    void aSetJarWithNoBannerIsSaid() throws IOException {
        Path fileSet = dirTemp.resolve("vendor/silent.jar");
        Files.createDirectories(fileSet.getParent());
        Files.writeString(fileSet, "not a jar", StandardCharsets.UTF_8);
        PqsResolution res = installations(fileSet).resolve("3.5");
        assertTrue(res.strWhyNone().contains("did not report a scribe version"),
                res.strWhyNone());
    }


    private PqsInstallations installations(Path fileSet) {
        return new PqsInstallations(dirPqs, dirDpm, fileSet, mapBanner::get);
    }


    private Path jar(Path fileJar, String strBannerVersion) throws IOException {
        Files.createDirectories(fileJar.getParent());
        Files.writeString(fileJar, "not really a jar", StandardCharsets.UTF_8);
        mapBanner.put(fileJar, banner(strBannerVersion));
        return fileJar;
    }


    private static String banner(String strVersion) {
        return "Picked up JAVA_TOOL_OPTIONS: -Dx=y\n"
                + "scribe, version: v" + strVersion + "\n"
                + "daml-sdk.version: 3.5.2\n"
                + "postgres-document.schema: 041\n";
    }
}
