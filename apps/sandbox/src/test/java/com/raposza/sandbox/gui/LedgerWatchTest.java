// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.raposza.sandbox.gui.LedgerProbe.Contract;
import com.raposza.sandbox.gui.LedgerProbe.Exercise;

import org.junit.jupiter.api.Test;

/**
 * Two reads of a ledger become the lines between them: a contract that
 * appeared is `created`, one that moved to the archive side is `archived`,
 * oldest first, and Splice's own templates stay out.
 *
 * THE CONTROL CAN FAIL: the archived contract below is ALSO in the create
 * list of both reads, so a diff that took the create list alone would miss
 * it, and the Splice round is new in the second read, so a diff without the
 * quiet list would report it.
 *
 * Author Claude/bentzn
 */
class LedgerWatchTest {

    private static final String STR_PKG = "17cbcb2b7c423ca47a15a2403051929f0f0c5af595a0e12e68930a1e6e3546fd";

    private static final Contract C_PLAN = new Contract("00aa11bb22cc33dd44ee55ff66",
            STR_PKG + ":Main:FlightMaintenance", "10", "");

    private static final Contract C_PLAN_DONE = new Contract("00aa11bb22cc33dd44ee55ff66",
            STR_PKG + ":Main:FlightMaintenance", "12", "");

    private static final Contract C_AIRCRAFT = new Contract("00ff00ff00ff00ff00ff00ff01",
            STR_PKG + ":Main:Aircraft", "11", "");

    private static final Contract C_ROUND = new Contract("00123456789abcdef0123456789",
            "deadbeef:Splice.Round:OpenMiningRound", "13", "");


    @Test
    void createdAndArchivedBetweenTwoReadsOldestFirstWithoutSplice() {
        LedgerWatch.Snapshot before = new LedgerWatch.Snapshot(List.of(C_PLAN), List.of());
        // Newest first, as the probe hands them over.
        LedgerWatch.Snapshot now = new LedgerWatch.Snapshot(List.of(C_ROUND, C_AIRCRAFT),
                List.of(C_PLAN_DONE));

        List<String> lstLine = LedgerWatch.lstLine("participant", before, now);
        assertEquals(List.of(
                "participant  created  Main:Aircraft  #ff00ff01",
                "participant  archived  Main:FlightMaintenance  #ee55ff66"),
                lstLine);
    }


    @Test
    void choicesAreLoggedConsumingAndNotInLedgerOrder() {
        Contract cDoneByChoice = new Contract(C_PLAN.strId(), C_PLAN.strTemplate(), "14", "",
                "Complete");
        Exercise exInspect = new Exercise("lapi_events_various_witnessed#13",
                C_AIRCRAFT.strTemplate(), "Inspect", "13", C_AIRCRAFT.strId());
        LedgerWatch.Snapshot before = new LedgerWatch.Snapshot(List.of(C_PLAN, C_AIRCRAFT),
                List.of(), List.of());
        LedgerWatch.Snapshot now = new LedgerWatch.Snapshot(List.of(C_AIRCRAFT),
                List.of(cDoneByChoice), List.of(exInspect));

        assertEquals(List.of(
                "app-provider  exercised  Main:Aircraft.Inspect  #ff00ff01",
                "app-provider  exercised  Main:FlightMaintenance.Complete  #ee55ff66"),
                LedgerWatch.lstLine("app-provider", before, now));
        // AND NOT AGAIN on the next read.
        assertTrue(LedgerWatch.lstLine("app-provider", now, now).isEmpty());
    }


    /**
     * THE CONTROL CAN FAIL: the round beside the tap is Splice too and new in
     * the same read, so a filter that let all of Splice through would report
     * it, and one that let none through would miss the amulet.
     */
    @Test
    void fundsAreShownAndTheRestOfSpliceIsNot() {
        Contract cAmulet = new Contract("00a1a2a3a4a5a6a7a8a9b0b1b2", "deadbeef:Splice.Amulet:Amulet",
                "20", "");
        Exercise exTap = new Exercise("lapi_events_various_witnessed#21",
                "deadbeef:Splice.AmuletRules:AmuletRules", "AmuletRules_DevNet_Tap", "21",
                "00c0c1c2c3c4c5c6c7c8c9d0d1");
        Exercise exRound = new Exercise("lapi_events_various_witnessed#22",
                "deadbeef:Splice.AmuletRules:AmuletRules", "AmuletRules_MiningRound_Archive",
                "22", "00c0c1c2c3c4c5c6c7c8c9d0d1");
        LedgerWatch.Snapshot before = new LedgerWatch.Snapshot(List.of(), List.of(), List.of());
        LedgerWatch.Snapshot now = new LedgerWatch.Snapshot(List.of(C_ROUND, cAmulet), List.of(),
                List.of(exTap, exRound));

        assertEquals(List.of(
                "sv  created  Splice.Amulet:Amulet  #a9b0b1b2",
                "sv  exercised  Splice.AmuletRules:AmuletRules.AmuletRules_DevNet_Tap  #c8c9d0d1"),
                LedgerWatch.lstLine("sv", before, now));
    }


    @Test
    void theSameReadTwiceSaysNothing() {
        LedgerWatch.Snapshot read = new LedgerWatch.Snapshot(List.of(C_AIRCRAFT),
                List.of(C_PLAN_DONE));
        assertTrue(LedgerWatch.lstLine("participant", read, read).isEmpty());
    }


    @Test
    void theTemplateIsShownWithoutItsPackage() {
        assertEquals("Main:FlightMaintenance", LedgerWatch.strTemplateShown(C_PLAN.strTemplate()));
        assertEquals("Main:Aircraft", LedgerWatch.strTemplateShown("Main:Aircraft"));
        assertEquals("?", LedgerWatch.strTemplateShown(""));
        assertTrue(LedgerWatch.isQuiet(C_ROUND.strTemplate()));
        assertFalse(LedgerWatch.isQuiet(C_PLAN.strTemplate()));
    }

}
