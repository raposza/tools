// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.process;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Whether a process that has just reported ready is still running a moment
 * later.
 *
 * READY IS A LINE THE PROCESS PRINTED; RUNNING IS A STATE, and only the second
 * one is worth reporting. A daemon has printed its ready line and begun
 * shutting down 308 ms later, finishing 336 ms after that. A single sample
 * landed in that gap, so the stack logged "stack ready" and the window painted
 * RUNNING over a participant already on its way down. The lamps were red
 * against a green chip.
 *
 * The same failure has been seen from scribe for a different cause - its health
 * server takes a fixed 8080 unless told otherwise and a bind failure there ends
 * the process, but the pipeline fiber can print a ready marker first. Two
 * causes, one shape, and this is the mechanism both need.
 *
 * THIS KNOWS NOTHING ABOUT WHAT IT IS WATCHING. It reports; the caller decides
 * what a failure means and what to say about it, because the useful message is
 * a Canton log tail in one case and scribe's health port in the other.
 *
 * Author Claude/bentzn
 */
public final class ProcessSettle {

    /**
     * How long a poll waits between samples.
     */
    public static final long N_MS_POLL_DEFAULT = 200L;

    /**
     * How long ready has to hold before it is believed.
     *
     * The cost is this much added to every successful start. That buys a
     * started stack that is actually serving, and the reason on the way past
     * when it is not.
     */
    public static final Duration SETTLE_DEFAULT = Duration.ofSeconds(5);

    private ProcessSettle() {
    }


    /**
     * @param flagRunning asked repeatedly, whether the process is still up
     * @param window how long it has to stay up
     * @return whether it held for the whole window
     */
    public static boolean holds(BooleanSupplier flagRunning, Duration window) {
        return holds(flagRunning, window, N_MS_POLL_DEFAULT);
    }


    /**
     * An interrupt reports the window as held rather than as a failure. The
     * caller is being shut down, which is not evidence about the process being
     * watched, and turning it into one would report a stack as broken because
     * somebody pressed Stop.
     *
     * @param flagRunning asked repeatedly, whether the process is still up
     * @param window how long it has to stay up
     * @param nMsPoll how long to wait between samples
     * @return whether it held for the whole window
     */
    public static boolean holds(BooleanSupplier flagRunning, Duration window, long nMsPoll) {
        long nDeadline = System.nanoTime() + window.toNanos();
        while (System.nanoTime() < nDeadline) {
            if (!flagRunning.getAsBoolean())
                return false;
            try {
                Thread.sleep(nMsPoll);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return true;
            }
        }
        return flagRunning.getAsBoolean();
    }

}
