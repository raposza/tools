// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>The machine with no toolchain is the case this exists for</h2>
 *
 * It cannot be produced on the machine the tests run on, so every input is an
 * argument and nothing here reads the environment.
 *
 * Author Claude/bentzn
 */
class AviationOfferTest {

    @Test
    void offersWhenTheSettingIsOnAndTheMachineCanBuild() {
        assertTrue(AviationOffer.flagOffer(true, "3.5.7", false));
        assertNull(AviationOffer.strWhyNot(true, "3.5.7", false));
    }


    @Test
    void theSettingIsTestedFirst() {
        // Off, no toolchain and already built all at once: the answer names the
        // developer's own decision rather than a machine gap they are not being
        // asked about.
        assertEquals(AviationOffer.STR_WHY_SETTING, AviationOffer.strWhyNot(false, null, true));
        assertFalse(AviationOffer.flagOffer(false, "3.5.7", false));
    }


    @Test
    void aMachineThatCannotBuildIsNotAsked() {
        assertEquals(AviationOffer.STR_WHY_TOOLCHAIN, AviationOffer.strWhyNot(true, null, false));
        assertEquals(AviationOffer.STR_WHY_TOOLCHAIN, AviationOffer.strWhyNot(true, "  ", false));
    }


    /**
     * A DAR from an earlier session is OFFERED. It used to suppress the
     * question, and the caller staged it regardless - which is how a start
     * came to upload test data nobody had agreed to.
     */
    @Test
    void anExistingDarIsOfferedRatherThanStagedSilently() {
        assertNull(AviationOffer.strWhyNot(true, "3.5.7", true));
    }


    /**
     * THE GATE IS A DAML SDK, BUILT OR NOT - his instruction of 2026-10-13,
     * `todo.md` A-75. A DAR on disk on a machine with no SDK is not offered.
     */
    @Test
    void noSdkIsNotAskedEvenWithTheDarBuilt() {
        assertEquals(AviationOffer.STR_WHY_TOOLCHAIN, AviationOffer.strWhyNot(true, null, true));
        assertFalse(AviationOffer.flagOffer(true, null, true));
        assertEquals(AviationOffer.STR_WHY_SETTING, AviationOffer.strWhyNot(false, null, true));
    }


    /** `Never` stops the staging too, not only the build. */
    @Test
    void anExistingDarIsNotOfferedWhenTheSettingIsOff() {
        assertEquals(AviationOffer.STR_WHY_SETTING, AviationOffer.strWhyNot(false, "3.5.7", true));
    }

}
