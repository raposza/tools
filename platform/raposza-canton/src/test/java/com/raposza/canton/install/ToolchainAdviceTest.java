// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Author Claude/bentzn
 */
class ToolchainAdviceTest {

    private static final Path DIR = Path.of("/home", "x");

    private static final CantonInstallation INST = new CantonInstallation(
            VersionId.of(3, 5, 16), Edition.OPEN_SOURCE, InstallSource.DPM,
            DIR.resolve("canton"), DIR.resolve("canton.jar"));


    private static ToolchainRoots roots(boolean flagDaml, boolean flagDpm) {
        return new ToolchainRoots(DIR.resolve(".daml"), DIR.resolve(".dpm"),
                flagDaml ? DIR.resolve(".daml").resolve("bin").resolve("daml") : null,
                flagDpm ? DIR.resolve(".dpm").resolve("bin").resolve("dpm") : null);
    }


    /** The machine the whole feature exists for. */
    @Test
    void aBareMachineIsOffered() {
        assertTrue(ToolchainAdvice.flagOffer(roots(false, false), List.of()));
    }


    /**
     * The assistant's presence says the developer manages their own toolchain,
     * and it silences the offer even with nothing discovered - installing the
     * older line is theirs to do.
     */
    @Test
    void theAssistantSilencesTheOfferEvenWithNothingInstalled() {
        assertFalse(ToolchainAdvice.flagOffer(roots(true, false), List.of()));
        assertFalse(ToolchainAdvice.flagOffer(roots(true, true), List.of()));
    }


    /** Something is installed, so the developer can start a stack. */
    @Test
    void anythingInstalledSilencesTheOffer() {
        assertFalse(ToolchainAdvice.flagOffer(roots(false, true), List.of(INST)));
        assertFalse(ToolchainAdvice.flagOffer(roots(false, false), List.of(INST)));
    }


    /**
     * dpm present and nothing installed IS offered: the tool is there and the
     * thing it manages is not, which is one click rather than a lesson.
     */
    @Test
    void dpmWithoutACantonIsStillOffered() {
        assertTrue(ToolchainAdvice.flagOffer(roots(false, true), List.of()));
    }

}
