// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.Edition;
import com.raposza.canton.install.InstallSource;
import com.raposza.canton.install.VersionId;
import com.raposza.runtime.localnet.SpliceInstallations;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The Canton a Splice release runs inside: the line its bundle names, the
 * newest patch installed, never 2.x - his rule, 2026-10-30.
 *
 * THE CONTROL CAN FAIL: before 2026-10-30 the window took the newest Canton
 * of any line, which on the 2.9.6-only machine of 2026-10-29 was 2.9.6.
 *
 * Author Claude/bentzn
 */
class SpliceCantonTest {

    private static CantonInstallation inst(String strVersion, boolean flagJar) {
        Path dir = Path.of("/canton", strVersion);
        return new CantonInstallation(VersionId.parse(strVersion), Edition.OPEN_SOURCE,
                InstallSource.DPM, dir, flagJar ? dir.resolve("canton.jar") : null);
    }


    private static SpliceInstallations.Versions named(String strCanton) {
        return new SpliceInstallations.Versions(strCanton, "3.5.2", "3.5.2");
    }


    @Test
    void theNewestOfTheLineIsTakenAndTheGapIsSaid() {
        SpliceCanton.Choice choice = SpliceCanton.choose("0.9.4", named("3.6.4"),
                List.of(inst("2.9.6", true), inst("3.5.19", true), inst("3.6.2", true),
                        inst("3.6.3", true), inst("3.6.9", false)));

        assertEquals("3.6.3", choice.inst().version().toString());
        assertNull(choice.strRefusal());
        assertTrue(choice.strNote().startsWith("Splice 0.9.4 on Canton 3.6.3 open-source"),
                choice.strNote());
        assertTrue(choice.strNote().contains("released on 3.6.4; the newest 3.6 installed"),
                choice.strNote());
    }


    @Test
    void theExactCantonIsSaidAsSuch() {
        SpliceCanton.Choice choice = SpliceCanton.choose("0.9.4", named("3.6.4"),
                List.of(inst("3.6.4", true), inst("3.6.3", true)));

        assertEquals("3.6.4", choice.inst().version().toString());
        assertTrue(choice.strNote().contains("the Canton it is released on"), choice.strNote());
    }


    /** The machine of 2026-10-29: 2.9.6 and nothing of the 3.6 line. */
    @Test
    void noCantonOfTheLineIsARefusalThatNamesTheSdk() {
        SpliceCanton.Choice choice = SpliceCanton.choose("0.9.4", named("3.6.4"),
                List.of(inst("2.9.6", true), inst("3.5.1", true)));

        assertNull(choice.inst());
        assertNull(choice.strNote());
        assertTrue(choice.strRefusal().contains("needs a Canton of the 3.6 line"),
                choice.strRefusal());
        assertTrue(choice.strRefusal().contains("released on 3.6.4"), choice.strRefusal());
        assertTrue(choice.strRefusal().contains("Daml SDK 3.6"), choice.strRefusal());
        // AN INSTALL WOULD HELP, and the window offers one for this line.
        assertEquals("3.6", choice.strLineNeeded());
    }


    @Test
    void aSnapshotNamesItsLine() {
        SpliceCanton.Choice choice = SpliceCanton.choose("0.8.0",
                named("3.5.14-snapshot.20260815.19176.0.v65fa04f6"),
                List.of(inst("3.5.16", true), inst("3.6.1", true)));

        assertEquals("3.5.16", choice.inst().version().toString());
    }


    @Test
    void anUnreadablePageTakesTheNewest3xAndSaysItIsUnchecked() {
        SpliceCanton.Choice choice = SpliceCanton.choose("0.8.9", null,
                List.of(inst("2.10.4", true), inst("3.4.11", true), inst("3.5.12", true)));

        assertEquals("3.5.12", choice.inst().version().toString());
        assertTrue(choice.strNote().contains("UNCHECKED"), choice.strNote());

        SpliceCanton.Choice choice2x = SpliceCanton.choose("0.8.9", null,
                List.of(inst("2.10.4", true), inst("2.9.6", true)));
        assertNull(choice2x.inst());
        assertTrue(choice2x.strRefusal().contains("needs a 3.x Canton"), choice2x.strRefusal());
    }


    /** 0.5.10 on 3.4.11, 2026-10-30: Canton refused LocalNetND's configuration. */
    @Test
    void belowSplice08IsRefusedWhateverIsInstalled() {
        SpliceCanton.Choice choice = SpliceCanton.choose("0.5.10",
                named("3.4.11-snapshot.20260206.17558.0.v8c896100"),
                List.of(inst("3.4.11", true), inst("3.6.3", true)));

        assertNull(choice.inst());
        assertTrue(choice.strRefusal().startsWith("LocalNetND runs Splice 0.8 and later; 0.5.10"),
                choice.strRefusal());
        assertNull(choice.strLineNeeded());
        assertTrue(SpliceCanton.isBelowFloor("0.7.5"));
        assertTrue(!SpliceCanton.isBelowFloor("0.8.0"));
        assertTrue(!SpliceCanton.isBelowFloor("0.9.4"));
        assertTrue(!SpliceCanton.isBelowFloor("1.0.0"));
    }


    @Test
    void theLineIsReadOffWhatTheBundleStates() {
        assertEquals("3.6", SpliceCanton.strLineOf("3.6.4"));
        assertEquals("3.5", SpliceCanton.strLineOf("3.5.1-snapshot.20260423.18760.0.v0d74e51b"));
        assertEquals("3.10", SpliceCanton.strLineOf("3.10.0"));
        assertNull(SpliceCanton.strLineOf("snapshot"));
        assertNull(SpliceCanton.strLineOf(null));
    }


    /** Install Splice offers what LocalNetND runs - the WinVM, 2026-10-30. */
    @Test
    void theInstallListStopsAtTheFloor() {
        assertEquals(List.of("0.9.4", "0.8.0", "1.0.0"), SpliceCanton.lstFromFloor(
                List.of("0.9.4", "0.8.0", "0.7.5", "0.5.10", "1.0.0")));
        assertEquals(List.of(), SpliceCanton.lstFromFloor(null));
    }
}
