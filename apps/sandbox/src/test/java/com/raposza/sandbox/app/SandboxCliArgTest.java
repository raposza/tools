// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `--cli` is read before the parser and removed from what it sees; without it
 * the window opens.
 *
 * Asserted here rather than found in the window, and deliberately WITHOUT
 * touching Swing: this test runs in the same reactor as every other, on
 * machines and in containers with no display, and a test that opened a toolkit
 * to check an argument would fail for a reason that has nothing to do with the
 * argument.
 *
 * The pair is the point. `--cli` reaching {@link SandboxOptions#parse} is an
 * "unknown argument" and a usage exit; `--cli` swallowing the rest of the line
 * is a stack that ignores what it was asked for. Both are silent from the
 * outside.
 *
 * Author Claude/bentzn
 */
class SandboxCliArgTest {

    @Test
    void theSwitchIsRecognisedAnywhereOnTheLine() {
        assertTrue(SandboxApp.flagCli(new String[] { "--cli" }));
        assertTrue(SandboxApp.flagCli(new String[] { "--launcher", "daemon", "--cli" }));
        assertFalse(SandboxApp.flagCli(new String[] { "--launcher", "daemon" }));
        assertFalse(SandboxApp.flagCli(new String[0]));
        assertFalse(SandboxApp.flagCli(null));
    }


    @Test
    void theWindowIsTheDefault() {
        assertFalse(SandboxApp.flagCli(new String[] { "--pqs", "on" }));
    }


    @Test
    void helpAndListAreTerminalRunsWithoutTheSwitch() {
        assertTrue(SandboxApp.flagCli(new String[] { "--help" }));
        assertTrue(SandboxApp.flagCli(new String[] { "-h" }));
        assertTrue(SandboxApp.flagCli(new String[] { "--list" }));
    }


    @Test
    void theOldSwitchIsGone() {
        assertFalse(SandboxApp.flagCli(new String[] { "--gui" }));
        assertArrayEquals(new String[] { "--gui" },
                SandboxApp.withoutCli(new String[] { "--gui" }));
    }


    @Test
    void everythingElseSurvivesAndStillParses() {
        String[] arrArg = { "--cli", "--launcher", "daemon", "--pqs", "on" };

        assertArrayEquals(new String[] { "--launcher", "daemon", "--pqs", "on" },
                SandboxApp.withoutCli(arrArg));

        // The window pre-fills from exactly this, so it has to be something the
        // ordinary parser accepts rather than a second dialect.
        SandboxOptions options = SandboxOptions.parse(SandboxApp.withoutCli(arrArg));
        assertTrue(options.isDaemon());
        assertEquals(SandboxOptions.PqsMode.ON, options.pqs());
    }


    @Test
    void aBareCliLineParsesAsTheDefaults() {
        SandboxOptions options = SandboxOptions.parse(
                SandboxApp.withoutCli(new String[] { "--cli" }));

        assertEquals(SandboxOptions.strLineDefault(), options.strLine());
        assertFalse(options.flagHelp());
    }

}
