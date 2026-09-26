// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the armed path only. The unarmed path calls `JOptionPane` and needs a
 * display, and a test that needed one would be a test that never runs on the
 * machine an unattended run happens on.
 *
 * Author Claude/bentzn
 */
class ModalsTest {

    private final List<String> lstReport = new ArrayList<>();


    @AfterEach
    void disarm() {
        Modals.disarm();
    }


    @Test
    void nothingIsArmedUntilItIsArmed() {
        assertFalse(Modals.isArmed());
        assertFalse(Modals.isFired());
    }


    @Test
    void aSuppressedConfirmIsNo() {
        Modals.arm(lstReport::add);

        assertFalse(Modals.isConfirmed(null, "Stop it and close?", "t",
                JOptionPane.QUESTION_MESSAGE));
        assertEquals(1, lstReport.size());
    }


    @Test
    void aSuppressedInputIsNull() {
        Modals.arm(lstReport::add);

        assertNull(Modals.strInput(null, "Name for this snapshot.", "t"));
        assertEquals(1, lstReport.size());
    }


    @Test
    void aSuppressedOptionIsMinusOneAndNeverTheFirstButton() {
        Modals.arm(lstReport::add);
        Object[] arrOption = {"Remove", "Cancel"};

        int idx = Modals.idxOption(null, "Remove this DAR?", arrOption, arrOption[1]);

        assertEquals(-1, idx);
        assertFalse(idx == 0, "a suppressed dialog must never read as the affirmative");
    }


    @Test
    void aSuppressedExpandableOptionIsMinusOne() {
        Modals.arm(lstReport::add);
        Object[] arrOption = {"Yes", "Not now", "Never"};

        int idx = Modals.idxOptionExpandable(null, "Install Aviation test fixture?",
                "Two stories: seven roles, three airframes.", "Expand", arrOption, "Yes");

        assertEquals(-1, idx);
        assertEquals(1, lstReport.size());
    }


    @Test
    void theExpandableReportCarriesTheHiddenDetail() {
        Modals.arm(lstReport::add);

        Modals.idxOptionExpandable(null, "Install Aviation test fixture?",
                "Two stories: seven roles, three airframes.", "Expand",
                new Object[] {"Yes", "Never"}, "Yes");

        String strReport = lstReport.get(0);
        assertTrue(strReport.contains("Install Aviation test fixture?"));
        assertTrue(strReport.contains("seven roles"), "an abort line has no link to press");
        assertFalse(strReport.contains("\n"), "the abort report must be greppable as one line");
    }


    @Test
    void aSuppressedWarnAndErrorBothReport() {
        Modals.arm(lstReport::add);

        Modals.warn(null, "the ports are held");

        assertEquals(1, lstReport.size());
        assertTrue(lstReport.get(0).startsWith(Modals.STR_ABORT));
        assertTrue(lstReport.get(0).contains("the ports are held"));
    }


    @Test
    void onlyTheFirstSuppressionReports() {
        Modals.arm(lstReport::add);

        Modals.warn(null, "first");
        Modals.warn(null, "second");
        Modals.isConfirmed(null, "third", "t", JOptionPane.QUESTION_MESSAGE);
        Modals.strInput(null, "fourth", "t");

        assertEquals(1, lstReport.size());
        assertTrue(lstReport.get(0).contains("first"));
        assertTrue(Modals.isFired());
    }


    @Test
    void theReportNamesTheKindOfDialog() {
        Modals.arm(lstReport::add);
        Modals.strInput(null, "Name for this snapshot.", "t");

        assertTrue(lstReport.get(0).contains("input"));
    }


    @Test
    void theReportIsOneLine() {
        Modals.arm(lstReport::add);
        Modals.idxOption(null, "Remove this DAR?\n\npetshop.dar\nVetted: yes\n",
                new Object[] {"Remove", "Cancel"}, "Cancel");

        String strReport = lstReport.get(0);
        assertFalse(strReport.contains("\n"), "the abort report must be greppable as one line");
        assertTrue(strReport.contains("petshop.dar"));
    }


    @Test
    void aNullMessageDoesNotBreakTheReport() {
        Modals.arm(lstReport::add);
        Modals.warn(null, null);

        assertEquals(1, lstReport.size());
        assertTrue(lstReport.get(0).contains("<no message>"));
    }


    @Test
    void armingAgainClearsTheFiredFlag() {
        Modals.arm(lstReport::add);
        Modals.warn(null, "first");
        assertTrue(Modals.isFired());

        Modals.arm(lstReport::add);
        assertFalse(Modals.isFired());

        Modals.warn(null, "second");
        assertEquals(2, lstReport.size());
    }


    @Test
    void disarmingRestoresTheOrdinaryPath() {
        Modals.arm(lstReport::add);
        Modals.disarm();

        assertFalse(Modals.isArmed());
        assertFalse(Modals.isFired());
    }


    @Test
    void armingWithoutASinkIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Modals.arm(null));
    }


    @Test
    void carriageReturnsAreFlattenedToo() {
        assertFalse(Modals.strOneLine("a\r\nb").contains("\r"));
        assertFalse(Modals.strOneLine("a\rb").contains("\r"));
        assertEquals("<empty message>", Modals.strOneLine("   "));
    }

}
