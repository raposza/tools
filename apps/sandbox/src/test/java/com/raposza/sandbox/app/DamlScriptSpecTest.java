// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command line is the whole of what this class is for, so the test is
 * against the argv rather than against the fields.
 *
 * Author Claude/bentzn
 */
class DamlScriptSpecTest {

    @TempDir
    static Path DIR_TMP;

    private static Path DIR_PROJ;

    private static final Path FILE_DAR = Path.of("/tmp/petshop/petshop-0.0.1.dar");


    /**
     * A project whose `daml.yaml` names a dpm bundle, which is what
     * `publish.py` writes for every 3.x install since 2026-08-21.
     */
    private static Path dirProjectFor(String strSdk) throws IOException {
        Path dir = DIR_TMP.resolve("proj-" + strSdk);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(DamlScriptSpec.STR_FILE_YAML),
                "sdk-version: " + strSdk + "\nname: petshop\n");
        return dir;
    }


    @BeforeAll
    static void projectIsA35Bundle() throws IOException {
        DIR_PROJ = dirProjectFor("3.5.5");
    }


    @Test
    void theRunnerIsDpmForA3xProject() throws IOException {
        DamlScriptSpec spec = DamlScriptSpec.of(dirProjectFor("3.4.4"), FILE_DAR,
                "Main:setup", 22215);
        assertEquals("dpm", spec.strBin());
        assertEquals("dpm", spec.lstCommand().get(0));
    }


    @Test
    void theRunnerIsTheAssistantForA2xProject() throws IOException {
        DamlScriptSpec spec = DamlScriptSpec.of(dirProjectFor("2.9.6"), FILE_DAR,
                "Main:setup", 22215);
        assertEquals("daml", spec.strBin());
        assertEquals("daml", spec.lstCommand().get(0));
    }


    /**
     * NOT the assistant. Defaulting to the toolchain being removed would fail
     * further from the cause than defaulting to the one that is not.
     */
    @Test
    void aProjectWithNoYamlReadsDpm() {
        DamlScriptSpec spec = DamlScriptSpec.of(DIR_TMP.resolve("absent"), FILE_DAR,
                "Main:setup", 22215);
        assertEquals("dpm", spec.strBin());
    }


    @Test
    void commandIsTheBankedLine() {
        DamlScriptSpec spec = DamlScriptSpec.of(DIR_PROJ, FILE_DAR, "Main:setup", 22215);
        List<String> lstCmd = spec.lstCommand();

        assertEquals(List.of("dpm", "script", "--dar", FILE_DAR.toString(), "--script-name",
                "Main:setup", "--ledger-host", "localhost", "--ledger-port", "22215"), lstCmd);
    }


    @Test
    void uploadIsOmittedRatherThanWrittenAsNo() {
        DamlScriptSpec spec = DamlScriptSpec.of(DIR_PROJ, FILE_DAR, "Main:setup", 22215);
        assertFalse(spec.lstCommand().contains("--upload-dar"));

        DamlScriptSpec specUp = new DamlScriptSpec(DIR_PROJ, FILE_DAR, "Main:setup",
                "localhost", 22215, true, 600);
        List<String> lstCmd = specUp.lstCommand();
        int idx = lstCmd.indexOf("--upload-dar");
        assertTrue(idx > 0);
        assertEquals("yes", lstCmd.get(idx + 1));
    }


    @Test
    void yamlIsUnderTheProject() {
        DamlScriptSpec spec = DamlScriptSpec.of(DIR_PROJ, FILE_DAR, "Main:setup", 22215);
        assertEquals(DIR_PROJ.resolve("daml.yaml"), spec.fileYaml());
    }


    /** Absent by default, so `Main:setup` is byte-identical to what ran. */
    @Test
    void inputIsOmittedWhenThereIsNone() {
        DamlScriptSpec spec = DamlScriptSpec.of(DIR_PROJ, FILE_DAR, "Main:setup", 22215);
        assertFalse(spec.lstCommand().contains("--input-file"));
    }


    /**
     * The position is `probes/auth/fixture.sh`'s: after the script name and
     * before the ledger.
     */
    @Test
    void inputSitsBetweenTheScriptNameAndTheLedger() {
        Path fileInput = DIR_TMP.resolve("parties.json");
        DamlScriptSpec spec = DamlScriptSpec.of(DIR_PROJ, FILE_DAR, "Main:verify", 22215)
                .withInput(fileInput);
        List<String> lstCmd = spec.lstCommand();

        assertEquals(List.of("dpm", "script", "--dar", FILE_DAR.toString(), "--script-name",
                "Main:verify", "--input-file", fileInput.toString(), "--ledger-host",
                "localhost", "--ledger-port", "22215"), lstCmd);
    }


    /** Both travel, so a token is not lost by adding an argument. */
    @Test
    void theTokenAndTheInputCoexist() {
        Path fileInput = DIR_TMP.resolve("parties.json");
        Path fileToken = DIR_TMP.resolve("admin.token");
        List<String> lstCmd = DamlScriptSpec.of(DIR_PROJ, FILE_DAR, "Main:verify", 22215)
                .withToken(fileToken).withInput(fileInput).lstCommand();

        assertTrue(lstCmd.contains("--input-file"));
        assertEquals(fileToken.toString(), lstCmd.get(lstCmd.indexOf("--access-token-file") + 1));
        assertEquals(DamlScriptSpec.STR_USER_ID_DEFAULT,
                lstCmd.get(lstCmd.indexOf("--user-id") + 1));
    }


    @Test
    void anUnusableSpecIsRefusedAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> DamlScriptSpec.of(null, FILE_DAR, "Main:setup", 22215));
        assertThrows(IllegalArgumentException.class,
                () -> DamlScriptSpec.of(DIR_PROJ, null, "Main:setup", 22215));
        assertThrows(IllegalArgumentException.class,
                () -> DamlScriptSpec.of(DIR_PROJ, FILE_DAR, "  ", 22215));
        assertThrows(IllegalArgumentException.class,
                () -> DamlScriptSpec.of(DIR_PROJ, FILE_DAR, "Main:setup", 0));
        assertThrows(IllegalArgumentException.class, () -> new DamlScriptSpec(DIR_PROJ,
                FILE_DAR, "Main:setup", "localhost", 22215, false, 1));
    }


    @Test
    void aReadinessFailureIsTheOnlyOneRetried() {
        assertTrue(DamlScriptRun.flagReadiness(
                List.of("ok", "GrpcError: CANNOT_AUTODETECT_SYNCHRONIZER(...)")));
        assertTrue(DamlScriptRun.flagReadiness(List.of("NOT_CONNECTED_TO_ANY_DOMAIN")));
        assertFalse(DamlScriptRun.flagReadiness(List.of("Assertion failed: Pet/PetShop")));
        assertFalse(DamlScriptRun.flagReadiness(List.of()));
    }
}
