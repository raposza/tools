// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>The fixtures are REAL JARS</h2>
 *
 * They used to be text files called `.jar`, which was enough while the edition
 * came off the file name. It is read out of the manifest now, so a fixture that
 * is not a jar can only ever answer UNKNOWN and every edition assertion here
 * would have passed for the wrong reason - or failed for one.
 *
 * Author Claude/bentzn
 */
class CantonInstallationsTest {

    private static final String STR_MAIN_COMMUNITY = CantonRuntimeJar.STR_MAIN_COMMUNITY;

    private static final String STR_MAIN_ENTERPRISE = CantonRuntimeJar.STR_MAIN_ENTERPRISE;


    /**
     * D-837, his machine: 3.5.16 flat with its jar and mirrored without. The
     * picker keeps the one that starts; a jar-less version nothing else covers
     * stays, so it can still be reported.
     */
    @Test
    void aJarlessTwinIsNotShownButAJarlessOnlyInstallIs() {
        Path dirHome = Path.of("/home", "x");
        CantonInstallation instFlat = new CantonInstallation(VersionId.of(3, 5, 16),
                Edition.OPEN_SOURCE, InstallSource.DPM, dirHome.resolve("flat"),
                dirHome.resolve("flat.jar"));
        CantonInstallation instMirror = new CantonInstallation(VersionId.of(3, 5, 16),
                Edition.OPEN_SOURCE, InstallSource.DPM, dirHome.resolve("mirror"), null);
        CantonInstallation instAlone = new CantonInstallation(VersionId.of(3, 5, 17),
                Edition.OPEN_SOURCE, InstallSource.DPM, dirHome.resolve("alone"), null);
        CantonInstallation instOtherEdition = new CantonInstallation(VersionId.of(3, 5, 16),
                Edition.ENTERPRISE, InstallSource.DPM, dirHome.resolve("ent"), null);

        List<CantonInstallation> lstShown = CantonInstallations.lstShown(
                List.of(instAlone, instMirror, instFlat, instOtherEdition));

        assertEquals(List.of(instAlone, instFlat, instOtherEdition), lstShown);
    }

    @TempDir
    Path dirTemp;

    private Path dirDaml;
    private Path dirDpm;
    private CantonInstallations installations;


    @BeforeEach
    void setUp() throws IOException {
        dirDaml = dirTemp.resolve(".daml");
        dirDpm = dirTemp.resolve(".dpm");
        installations = new CantonInstallations(dirDaml, dirDpm);
    }


    @Test
    void findsNothingWhenNothingIsInstalled() {
        assertTrue(installations.discover().isEmpty());
    }


    @Test
    void findsBothInstallationModels() throws IOException {
        assistant("2.9.7", "canton.jar", STR_MAIN_COMMUNITY);
        dpm("canton-open-source", "3.5.11", "canton-open-source-3.5.11.jar", STR_MAIN_COMMUNITY);

        List<CantonInstallation> lstFound = installations.discover();
        assertEquals(2, lstFound.size());

        // Newest first.
        assertEquals(VersionId.of(3, 5, 11), lstFound.get(0).version());
        assertEquals(InstallSource.DPM, lstFound.get(0).source());
        assertEquals(InstallSource.DAML_ASSISTANT, lstFound.get(1).source());
        assertTrue(lstFound.get(0).hasRuntime());
        assertTrue(lstFound.get(1).hasRuntime());
    }


    /**
     * The regression this guards. An SDK jar is `canton.jar` on every
     * version, so a name-based reader answered UNKNOWN for nine of the twelve
     * installations on this machine while each of those jars declared itself
     * plainly.
     */
    @Test
    void theSdkJarIsNamedCantonAndStillDeclaresItsEdition() throws IOException {
        assistant("3.4.11", "canton.jar", STR_MAIN_COMMUNITY);

        List<CantonInstallation> lstFound = installations.discover();
        assertEquals(1, lstFound.size());
        assertEquals(Edition.OPEN_SOURCE, lstFound.get(0).edition());
    }


    @Test
    void bothEditionsOfOneVersionAreDiscovered() throws IOException {
        // `dpm install 3.4.11` leaves an ENTERPRISE
        // 3.4.11 in the cache while the assistant's OPEN SOURCE 3.4.11 stays
        // under ~/.daml. Same version, two editions, BOTH discovered.
        //
        // This test asserted refusal between 2026-08-17 morning and afternoon.
        // The refusal rested on LICENSE-DA.txt being present in the enterprise
        // jar; it is present in the OPEN SOURCE jar too, so it never
        // discriminated. Acquisition is the operator's call and it has been
        // made. Do not flip this back.
        assistant("3.4.11", "canton.jar", STR_MAIN_COMMUNITY);
        dpm("canton-enterprise", "3.4.11", "canton-enterprise-3.4.11.jar", STR_MAIN_ENTERPRISE);

        List<CantonInstallation> lstFound = installations.discover();
        assertEquals(2, lstFound.size());

        assertTrue(installations.find(VersionId.of(3, 4, 11),
                Edition.OPEN_SOURCE).isPresent());
        assertTrue(installations.find(VersionId.of(3, 4, 11),
                Edition.ENTERPRISE).isPresent());
    }


    /**
     * A DPM jar that answers nothing falls back to the component name, which is
     * all that name is good for: where the binary came from.
     */
    @Test
    void theComponentNameIsTheFallbackWhenTheJarDeclaresNothing() throws IOException {
        dpm("canton-enterprise", "3.4.4", "canton-enterprise-3.4.4.jar", null);

        List<CantonInstallation> lstFound = installations.discover();
        assertEquals(1, lstFound.size());
        assertEquals(Edition.ENTERPRISE, lstFound.get(0).edition());
    }


    /**
     * And an SDK jar that answers nothing has no fallback to fall back to, so
     * it stays UNKNOWN rather than being defaulted to open source.
     */
    @Test
    void anSdkJarThatDeclaresNothingStaysUnknown() throws IOException {
        assistant("2.8.12", "canton.jar", null);

        List<CantonInstallation> lstFound = installations.discover();
        assertEquals(1, lstFound.size());
        assertEquals(Edition.UNKNOWN, lstFound.get(0).edition());
    }


    @Test
    void versionAloneDoesNotIdentifyAnInstallation() throws IOException {
        // The rule outlives the fixture that used to demonstrate it. Two
        // editions of one version can no longer both be discovered, but two
        // INSTALL MODELS of one version can - and find() still has to be given
        // more than a version number to answer.
        assistant("3.4.11", "canton.jar", STR_MAIN_COMMUNITY);
        dpm("canton-open-source", "3.4.11", "canton-open-source-3.4.11.jar", STR_MAIN_COMMUNITY);

        List<CantonInstallation> lstFound = installations.discover();
        assertEquals(2, lstFound.size());

        // Same version, same edition, so the sort falls through to the source.
        assertEquals(InstallSource.DAML_ASSISTANT, lstFound.get(0).source());
        assertEquals(InstallSource.DPM, lstFound.get(1).source());
        assertNotEquals(lstFound.get(0).dirHome(), lstFound.get(1).dirHome());
    }


    @Test
    void picksTheHighestPatchOnALine() throws IOException {
        assistant("2.9.6", "canton.jar", STR_MAIN_COMMUNITY);
        assistant("2.9.7", "canton.jar", STR_MAIN_COMMUNITY);
        assistant("2.10.4", "canton.jar", STR_MAIN_COMMUNITY);

        Optional<CantonInstallation> optFound = installations.newestOfLine("2.9", null);
        assertTrue(optFound.isPresent());
        assertEquals(VersionId.of(2, 9, 7), optFound.get().version());

        // And 2.10 is its own line, not a later patch of 2.1.
        assertEquals(VersionId.of(2, 10, 4), installations.newestOfLine("2.10", null).get().version());
    }


    @Test
    void ignoresDirectoriesThatAreNotVersions() throws IOException {
        Files.createDirectories(dirDaml.resolve("sdk").resolve("bin").resolve("canton"));
        Files.createDirectories(dirDpm.resolve("cache").resolve("components")
                .resolve("canton-open-source").resolve("latest"));
        assistant("3.4.11", "canton.jar", STR_MAIN_COMMUNITY);

        assertEquals(1, installations.discover().size());
    }


    @Test
    void anInstallationWithNoRuntimeIsReportedRatherThanDropped() throws IOException {
        Files.createDirectories(dirDaml.resolve("sdk").resolve("2.8.12").resolve("canton"));

        List<CantonInstallation> lstFound = installations.discover();
        assertEquals(1, lstFound.size());
        assertFalse(lstFound.get(0).hasRuntime());
        assertEquals(Edition.UNKNOWN, lstFound.get(0).edition());
    }


    /**
     * The largest canton*.jar wins, and it is the one whose manifest is read.
     * A generation shipping a small tool jar beside the runtime must not have
     * the tool answer for the installation.
     */
    @Test
    void theLargestJarIsTheOneThatAnswers() throws IOException {
        Path dirLib = dirDaml.resolve("sdk").resolve("3.5.11").resolve("canton").resolve("lib");
        Files.createDirectories(dirLib);
        writeJar(dirLib.resolve("canton-tool.jar"), null, 16);
        writeJar(dirLib.resolve("canton.jar"), STR_MAIN_COMMUNITY, 4096);

        List<CantonInstallation> lstFound = installations.discover();
        assertEquals(1, lstFound.size());
        assertEquals(Edition.OPEN_SOURCE, lstFound.get(0).edition());
    }


    private void assistant(String strVersion, String strJar, String strMain) throws IOException {
        Path dirLib = dirDaml.resolve("sdk").resolve(strVersion).resolve("canton").resolve("lib");
        Files.createDirectories(dirLib);
        writeJar(dirLib.resolve(strJar), strMain, 0);
    }


    /**
     * A component added by explicit OCI URI does not land beside the flat ones.
     * It is stored under the registry coordinates it was requested by, and
     * discovery that resolves `cache/components/canton-open-source` directly
     * cannot see it - the version is on disk, startable, and absent from the
     * window.
     */
    @Test
    void aComponentUnderTheMirroredRegistryPathIsDiscovered() throws IOException {
        dpmAt(dirDpm.resolve("cache").resolve("components")
                        .resolve("europe-docker.pkg.dev").resolve("da-images")
                        .resolve("public-all").resolve("components")
                        .resolve("canton-open-source").resolve("3.5.13"),
                "canton-open-source-3.5.13.jar", STR_MAIN_COMMUNITY);

        List<CantonInstallation> lstFound = installations.discover();
        assertEquals(1, lstFound.size());
        assertEquals(VersionId.of(3, 5, 13), lstFound.get(0).version());
        assertEquals(InstallSource.DPM, lstFound.get(0).source());
        assertEquals(Edition.OPEN_SOURCE, lstFound.get(0).edition());
        assertTrue(lstFound.get(0).hasRuntime());
    }


    /**
     * And both layouts at once, which is what a cache looks like after a bundle
     * install and one component pulled by URI.
     */
    @Test
    void theFlatAndTheMirroredLayoutsCoexist() throws IOException {
        dpm("canton-open-source", "3.5.11", "canton-open-source-3.5.11.jar", STR_MAIN_COMMUNITY);
        dpmAt(dirDpm.resolve("cache").resolve("components")
                        .resolve("europe-docker.pkg.dev").resolve("da-images")
                        .resolve("public-all").resolve("components")
                        .resolve("canton-open-source").resolve("3.5.13"),
                "canton-open-source-3.5.13.jar", STR_MAIN_COMMUNITY);

        List<CantonInstallation> lstFound = installations.discover();
        assertEquals(2, lstFound.size());
        assertEquals(VersionId.of(3, 5, 13), lstFound.get(0).version());
        assertEquals(VersionId.of(3, 5, 11), lstFound.get(1).version());
    }


    /**
     * The walk is bounded, so a component buried deeper than a registry path
     * can put it is NOT found. Stated as a test because the bound is the whole
     * of what stops the walk from descending into every version directory in
     * the cache.
     */
    @Test
    void theComponentWalkIsBounded() throws IOException {
        Path dirDeep = dirDpm.resolve("cache").resolve("components");
        for (int cntLevel = 0; cntLevel < 7; cntLevel++) {
            dirDeep = dirDeep.resolve("d" + cntLevel);
        }
        dpmAt(dirDeep.resolve("canton-open-source").resolve("3.5.13"),
                "canton-open-source-3.5.13.jar", STR_MAIN_COMMUNITY);

        assertTrue(installations.discover().isEmpty());
    }


    private void dpm(String strComponent, String strVersion, String strJar, String strMain)
            throws IOException {
        dpmAt(dirDpm.resolve("cache").resolve("components").resolve(strComponent)
                .resolve(strVersion), strJar, strMain);
    }


    /**
     * @param dirVersion the version directory, wherever under the cache it sits
     * @param strJar the runtime jar's name
     * @param strMain the Main-Class to declare, or null
     */
    private void dpmAt(Path dirVersion, String strJar, String strMain) throws IOException {
        Path dirLib = dirVersion.resolve("lib");
        Files.createDirectories(dirLib);
        writeJar(dirLib.resolve(strJar), strMain, 0);
    }


    /**
     * @param file where to write
     * @param strMain the Main-Class to declare, or null for a jar whose
     *        manifest names none
     * @param cntPad bytes of filler, so one fixture can be made larger than
     *        another
     */
    private static void writeJar(Path file, String strMain, int cntPad) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (strMain != null)
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, strMain);

        try (OutputStream out = Files.newOutputStream(file);
                JarOutputStream jar = new JarOutputStream(out, manifest)) {
            if (cntPad > 0) {
                // RANDOM rather than repeated: a jar deflates its entries, and
                // a filler of one character compresses to nothing, which would
                // leave the two fixtures the same size on disk and the test
                // asserting whatever the directory walk happened to reach
                // first.
                byte[] arrPad = new byte[cntPad];
                new Random(42L).nextBytes(arrPad);
                jar.putNextEntry(new ZipEntry("padding.bin"));
                jar.write(arrPad);
                jar.closeEntry();
            }
        }
    }
}
