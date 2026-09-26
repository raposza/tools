// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Putting a shortened id back, and refusing to guess when it cannot be.
 *
 * Author Claude/bentzn
 */
class LongIdsTest {

    private static final String ID_PARTY =
            "PlantOperator-d4d95138::1220aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa8b1936";

    private static final String ID_CT = "00e7" + "a".repeat(130) + "d4cc";


    @Test
    void aScriptWithNoMarkerIsHandedBackUntouched() {
        LongIds.Result out = LongIds.expand("LIST PARTIES", List.of(ID_PARTY));

        assertEquals("LIST PARTIES", out.strText());
        assertNull(out.strProblem());
    }


    @Test
    void aShortPartyIsPutBackWhole() {
        String strShort = ShortIds.text("AS " + '"' + ID_PARTY + '"' + " QUERY Main:Batch");
        LongIds.Result out = LongIds.expand(strShort, List.of(ID_PARTY));

        assertNull(out.strProblem());
        assertEquals("AS " + '"' + ID_PARTY + '"' + " QUERY Main:Batch", out.strText());
    }


    @Test
    void aShortContractIdIsPutBackWhole() {
        String strShort = ShortIds.text("AS $p FETCH " + '"' + ID_CT + '"');
        LongIds.Result out = LongIds.expand(strShort, List.of(ID_CT));

        assertNull(out.strProblem());
        assertTrue(out.strText().contains(ID_CT), out.strText());
    }


    /**
     * Sending a wrong contract under a right-looking statement is the outcome
     * this refuses, so an unknown id stops the run rather than travelling.
     */
    @Test
    void anIdNothingHasReadStopsTheRun() {
        LongIds.Result out = LongIds.expand(ShortIds.text(ID_CT), List.of(ID_PARTY));

        assertNull(out.strText());
        assertNotNull(out.strProblem());
        assertTrue(out.strProblem().contains(ShortIds.STR_CUT), out.strProblem());
    }


    /**
     * The display toggle is not all or nothing: one unknown id must not leave
     * every other id in the script abbreviated.
     */
    @Test
    void whatCannotBePlacedIsLeftAloneAndTheRestIsPutBack() {
        String strShort = ShortIds.text("AS " + ID_PARTY + " FETCH " + '"' + ID_CT + '"');
        String strOut = LongIds.textBest(strShort, List.of(ID_PARTY));

        assertTrue(strOut.contains(ID_PARTY), strOut);
        assertTrue(strOut.contains(ShortIds.text(ID_CT)), strOut);
    }


    @Test
    void twoCandidatesAreRefusedRatherThanChosenBetween() {
        String idOther = "00e7" + "b".repeat(130) + "d4cc";
        LongIds.Result out = LongIds.expand(ShortIds.text(ID_CT), List.of(ID_CT, idOther));

        assertNull(out.strText());
        assertTrue(out.strProblem().contains("2 ids"), out.strProblem());
    }

}
