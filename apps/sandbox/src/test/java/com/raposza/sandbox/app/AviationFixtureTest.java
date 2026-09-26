// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>The resource is asserted, because a jar without it is a jar that offers
 * a fixture it cannot build</h2>
 *
 * The source is a resource of this module rather than a file the developer has
 * on disk, so "is it in the build" is a question only a test answers. The rest
 * is path and command-line arithmetic, which is the whole of what this class
 * decides.
 *
 * Author Claude/bentzn
 */
class AviationFixtureTest {

    @Test
    void theSourceIsInTheBuild() throws IOException {
        String strSource = AviationFixture.strSource();

        assertTrue(strSource.contains("module Main where"));
        // The two-phase split is what the authenticated path runs, so its
        // absence is a fixture that cannot be driven with auth on.
        assertTrue(strSource.contains("setupParties : Script MroParties"));
        assertTrue(strSource.contains("setupLedger : MroParties -> Script ()"));
    }


    @Test
    void stagingWritesAProjectTheCompilerCanResolveAnSdkFrom(@TempDir Path dirTemp)
            throws IOException {
        Path dirProject = AviationFixture.dirProject(dirTemp, "3.5.14-open_source");

        AviationFixture.stage(dirProject, "3.5.7");

        Path fileYaml = dirProject.resolve(DamlScriptSpec.STR_FILE_YAML);
        String strYaml = new String(Files.readAllBytes(fileYaml), StandardCharsets.UTF_8);
        assertTrue(strYaml.contains("sdk-version: 3.5.7"));
        assertFalse(strYaml.contains(AviationFixture.STR_TOKEN_SDK));
        assertTrue(strYaml.contains("name: " + AviationFixture.STR_NAME));
        assertTrue(Files.isRegularFile(dirProject.resolve("daml").resolve("Main.daml")));
    }


    @Test
    void stagingReplacesWhatWasThere(@TempDir Path dirTemp) throws IOException {
        Path dirProject = AviationFixture.dirProject(dirTemp, "3.5.14-open_source");
        AviationFixture.stage(dirProject, "3.5.7");
        Files.write(dirProject.resolve("daml").resolve("Main.daml"),
                "-- edited in place".getBytes(StandardCharsets.UTF_8));

        AviationFixture.stage(dirProject, "3.5.7");

        String strSource = new String(
                Files.readAllBytes(dirProject.resolve("daml").resolve("Main.daml")),
                StandardCharsets.UTF_8);
        assertTrue(strSource.contains("module Main where"));
    }


    @Test
    void theDarIsWhereTheCompilerPutsIt(@TempDir Path dirTemp) {
        Path dirProject = AviationFixture.dirProject(dirTemp, "3.4.11-enterprise");

        assertEquals(dirProject.resolve(".daml").resolve("dist").resolve("aviation-0.0.1.dar"),
                AviationFixture.fileDar(dirProject));
    }


    @Test
    void theSdkIsTheBundleOnThreeAndTheVersionItselfOnTwo() {
        Map<String, String> mapBundle = Map.of("3.5.12", "3.5.5");

        // The two diverge, and the project has to name the bundle.
        assertEquals("3.5.5", AviationFixture.strSdkFor("3.5.12", mapBundle));
        assertEquals("2.9.6", AviationFixture.strSdkFor("2.9.6", mapBundle));
        // A machine with the component and no bundle cannot build for it, and
        // that is answered rather than approximated.
        assertNull(AviationFixture.strSdkFor("3.5.16", mapBundle));
        assertNull(AviationFixture.strSdkFor("3.5.12", Map.of()));
    }


    @Test
    void theLineChoosesTheToolchain() {
        assertEquals(AviationFixture.STR_BIN_ASSISTANT, AviationFixture.strBin("2.10.2"));
        assertEquals(AviationFixture.STR_BIN_DPM, AviationFixture.strBin("3.5.7"));
        // An absent sdk reads dpm, because the assistant is the toolchain the
        // vendor is removing.
        assertEquals(AviationFixture.STR_BIN_DPM, AviationFixture.strBin(null));

        assertEquals(List.of("dpm", "build"), AviationFixture.lstBuild("3.5.7"));
        assertEquals(List.of("daml", "build"), AviationFixture.lstBuild("2.9.6"));
    }


    @Test
    void thePhasesRunInTheOrderTheyAreListed() {
        assertEquals(List.of(AviationFixture.STR_SCRIPT_PARTIES, AviationFixture.STR_SCRIPT_LEDGER),
                AviationFixture.lstScript());
    }

}
