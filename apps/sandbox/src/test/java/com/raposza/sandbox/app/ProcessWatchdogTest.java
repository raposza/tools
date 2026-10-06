// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A child that hangs is killed while its output is being read, and so is what
 * it started - 2026-10-05. Until then the deadline was checked only after the
 * output ended, which a hung child never lets happen.
 *
 * THE CONTROL CAN FAIL: the hung child's grandchild holds the same pipe, so a
 * watchdog that killed only the child would leave the read below blocked for
 * the full 30 s and the elapsed-time assertion would fail.
 *
 * Author Claude/bentzn
 */
class ProcessWatchdogTest {

    private static void requirePosixShell() {
        Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                .startsWith("windows"), "needs sh and sleep");
    }


    private static void drain(Process proc) throws IOException {
        try (InputStream in = proc.getInputStream()) {
            byte[] arrBuf = new byte[4096];
            while (in.read(arrBuf) >= 0) {
                // reading to the end is the point
            }
        }
    }


    @Test
    void aHungChildAndItsChildrenAreKilledWhileTheOutputIsRead() throws Exception {
        requirePosixShell();
        Process proc = new ProcessBuilder("sh", "-c", "sleep 30 & sleep 30").redirectErrorStream(true)
                .start();
        long nMsStart = System.nanoTime() / 1_000_000L;

        boolean flagFired;
        try (ProcessWatchdog watchdog = ProcessWatchdog.start(proc, 1)) {
            drain(proc);
            flagFired = watchdog.isFired();
        }
        long nMsTaken = System.nanoTime() / 1_000_000L - nMsStart;

        assertTrue(flagFired);
        assertTrue(nMsTaken < 15_000L, "the read ended after " + nMsTaken + " ms");
        assertTrue(proc.waitFor(5, TimeUnit.SECONDS));
    }


    @Test
    void aChildThatFinishesInTimeIsLeftAlone() throws Exception {
        requirePosixShell();
        Process proc = new ProcessBuilder("sh", "-c", "echo done").redirectErrorStream(true).start();

        boolean flagFired;
        try (ProcessWatchdog watchdog = ProcessWatchdog.start(proc, 30)) {
            drain(proc);
            assertTrue(proc.waitFor(5, TimeUnit.SECONDS));
            flagFired = watchdog.isFired();
        }

        assertFalse(flagFired);
        assertTrue(proc.exitValue() == 0);
    }

}
