// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Whether the Pharma question is asked, for a machine this test does not have.
 *
 * THE CASE THAT MATTERS is a machine with no toolchain, which a developer's own
 * machine can never be - so it is asserted here rather than found by somebody
 * who installed nothing.
 *
 * Author Claude/bentzn
 */
class PharmaOfferTest {

    @Test
    void aMachineThatCanBuildIsAsked() {
        assertTrue(PharmaOffer.flagOffer(true, "3.5.7", false));
        assertNull(PharmaOffer.strWhyNot(true, "3.5.7", false));
    }


    /**
     * THE SETTING IS TESTED FIRST. Somebody who switched the whole thing off is
     * not told about a toolchain gap in a question they are not being asked.
     */
    @Test
    void theSettingIsTheFirstAnswerAndTheOnlyOneTheDeveloperChose() {
        assertFalse(PharmaOffer.flagOffer(false, "3.5.7", false));
        assertEquals(PharmaOffer.STR_WHY_SETTING, PharmaOffer.strWhyNot(false, null, false));
        assertEquals(PharmaOffer.STR_WHY_SETTING, PharmaOffer.strWhyNot(false, "3.5.7", true));
    }


    @Test
    void aMachineThatCannotBuildIsNotAskedAndIsTold() {
        assertFalse(PharmaOffer.flagOffer(true, null, false));
        assertEquals(PharmaOffer.STR_WHY_TOOLCHAIN, PharmaOffer.strWhyNot(true, null, false));
        assertEquals(PharmaOffer.STR_WHY_TOOLCHAIN, PharmaOffer.strWhyNot(true, "  ", false));
    }


    /**
     * STAGING A BUILT DAR NEEDS NO COMPILER. Aviation learned this at cost: a
     * machine without a toolchain and with the fixture already on disk was
     * silently not asked.
     */
    @Test
    void aBuiltDarIsOfferedEvenWithNoToolchain() {
        assertTrue(PharmaOffer.flagOffer(true, null, true));
        assertNull(PharmaOffer.strWhyNot(true, null, true));
    }


    /**
     * The toolchain rule is Aviation's, called rather than copied - so a 2.x
     * Canton names itself and a 3.x one is looked up in the bundle map.
     */
    @Test
    void theSdkRuleIsTheOneAviationAlreadyUses() {
        Map<String, String> mapBundle = Map.of("3.5.13", "3.5.7");

        assertEquals("3.5.7", PharmaFixture.strSdkFor("3.5.13", mapBundle));
        assertEquals("2.10.4", PharmaFixture.strSdkFor("2.10.4", mapBundle));
        assertNull(PharmaFixture.strSdkFor("3.4.11", mapBundle));
    }

}
