// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>No compiler and no ledger</h2>
 *
 * `nBuild` runs a process and is not driven here; what is asserted is the pair
 * of decisions that go wrong silently - which scripts run under which user, and
 * that staging a second build leaves ONE dar behind.
 *
 * Author Claude/bentzn
 */
class AviationRunTest {

    private static final int N_PORT = 6865;


    @Test
    void unauthenticatedIsOneComposedScript(@TempDir Path dirTemp) {
        List<DamlScriptSpec> lstSpec = AviationRun.lstSpec(dirTemp, dirTemp.resolve("aviation.dar"),
                N_PORT, false, null, null, null);

        assertEquals(1, lstSpec.size());
        assertEquals(AviationFixture.STR_SCRIPT_SETUP, lstSpec.get(0).strName());
        assertNoToken(lstSpec.get(0));
    }


    /**
     * The ids do not exist when the admin token is minted, so the two halves
     * cannot run under one credential.
     */
    @Test
    void authenticatedIsTwoPhasesUnderTwoUsers(@TempDir Path dirTemp) {
        Path fileAdmin = dirTemp.resolve("admin.txt");
        Path fileSuper = dirTemp.resolve("super.txt");
        Path fileParties = dirTemp.resolve(AviationRun.STR_FILE_PARTIES);

        List<DamlScriptSpec> lstSpec = AviationRun.lstSpec(dirTemp, dirTemp.resolve("aviation.dar"),
                N_PORT, true, fileAdmin, fileSuper, fileParties);

        assertEquals(2, lstSpec.size());

        List<String> lstOne = lstSpec.get(0).lstCommand();
        assertEquals(AviationFixture.STR_SCRIPT_PARTIES, lstSpec.get(0).strName());
        assertEquals(AviationRun.STR_USER_ADMIN, valueOf(lstOne, "--user-id"));
        assertEquals(fileParties.toString(), valueOf(lstOne, "--output-file"));
        assertEquals(fileAdmin.toString(), valueOf(lstOne, "--access-token-file"));

        List<String> lstTwo = lstSpec.get(1).lstCommand();
        assertEquals(AviationFixture.STR_SCRIPT_LEDGER, lstSpec.get(1).strName());
        assertEquals(AviationRun.STR_USER_SUPER, valueOf(lstTwo, "--user-id"));
        assertEquals(fileParties.toString(), valueOf(lstTwo, "--input-file"));
        assertEquals(fileSuper.toString(), valueOf(lstTwo, "--access-token-file"));

        // ONE ROUTE ONTO THE LEDGER. The DARs tab uploads when Start is
        // pressed; a spec that also uploaded would be a second one.
        assertFalse(lstOne.contains("--upload-dar"));
        assertFalse(lstTwo.contains("--upload-dar"));
    }


    @Test
    void anAuthenticatedRunWithoutItsTokensIsRefused(@TempDir Path dirTemp) {
        assertThrows(IllegalArgumentException.class,
                () -> AviationRun.lstSpec(dirTemp, dirTemp.resolve("aviation.dar"), N_PORT, true,
                        null, null, null));
    }


    /** Two builds of one package in one directory is an upload rejection. */
    @Test
    void stagingReplacesTheEarlierBuild(@TempDir Path dirTemp) throws IOException {
        Path dirDars = dirTemp.resolve("dars");
        Files.createDirectories(dirDars);
        Files.write(dirDars.resolve("aviation-0.0.1.dar"), "old".getBytes(StandardCharsets.UTF_8));
        Files.write(dirDars.resolve("petshop-0.0.1.dar"),
                "other".getBytes(StandardCharsets.UTF_8));
        Path fileBuilt = dirTemp.resolve("aviation-0.0.1.dar");
        Files.write(fileBuilt, "new".getBytes(StandardCharsets.UTF_8));

        List<String> lstSaid = new ArrayList<>();
        Path fileOut = AviationRun.filePlace(fileBuilt, dirDars, lstSaid::add);

        assertEquals(dirDars.resolve("aviation-0.0.1.dar"), fileOut);
        assertEquals("new", Files.readString(fileOut));
        // ONLY THIS FIXTURE'S BUILDS ARE REMOVED. The pet shop is somebody
        // else's package and is nothing to do with this one.
        assertTrue(Files.isRegularFile(dirDars.resolve("petshop-0.0.1.dar")));
        assertTrue(lstSaid.stream().anyMatch(strLine -> strLine.startsWith("removed ")));
    }


    @Test
    void anAbsentDarIsNamedRatherThanCopied(@TempDir Path dirTemp) {
        assertThrows(IOException.class, () -> AviationRun.filePlace(dirTemp.resolve("absent.dar"),
                dirTemp.resolve("dars"), strLine -> { }));
    }


    /**
     * @param lstCommand the argv
     * @param strFlag the flag to look for
     * @return what follows it, or null
     */
    private static String valueOf(List<String> lstCommand, String strFlag) {
        int idx = lstCommand.indexOf(strFlag);
        return idx < 0 || idx + 1 >= lstCommand.size() ? null : lstCommand.get(idx + 1);
    }


    /**
     * @param spec the spec to check
     */
    private static void assertNoToken(DamlScriptSpec spec) {
        assertFalse(spec.lstCommand().contains("--access-token-file"));
        assertFalse(spec.lstCommand().contains("--user-id"));
    }

}
