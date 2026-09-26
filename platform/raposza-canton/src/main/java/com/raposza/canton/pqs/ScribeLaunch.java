// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.pqs;

import com.raposza.runtime.process.ProcessException;
import com.raposza.runtime.process.ProcessSettle;

import java.time.Duration;

/**
 * Start scribe, wait for it, and refuse a ready that does not hold.
 *
 * The sequence is the same whichever Canton generation is underneath, and it is
 * a fact about SCRIBE rather than about a topology: scribe attaches to a
 * participant's Ledger API and neither knows nor cares whether the thing
 * serving it has a sequencer and a mediator beside it or an embedded domain.
 * Both stacks carried an identical copy of it, including both failure messages,
 * until this class.
 *
 * WHAT STAYS WITH THE CALLER is everything a stack decides: creating the
 * database, choosing the health port out of its own block, attaching the
 * process to the stack's supervision, and the ORDER - scribe up after Canton is
 * serving, down before Canton goes. This class is handed a process that is
 * already built and already attached.
 *
 * Author Claude/bentzn
 */
public final class ScribeLaunch {

    private ScribeLaunch() {
    }


    /**
     * @param scribe a process that has been built and attached, not started
     * @param timeoutReady how long to wait for the ready marker
     * @param settle how long ready then has to hold
     * @param cntTail how many lines of its output a failure carries
     * @throws ProcessException when it dies starting, does not report ready in
     *         time, or reports ready and then exits
     */
    public static void startAndSettle(ScribeProcess scribe, Duration timeoutReady,
            Duration settle, int cntTail) {
        scribe.start();

        boolean flagReady;
        try {
            flagReady = scribe.awaitReady(timeoutReady);
        }
        catch (ProcessException ex) {
            throw new ProcessException("PQS died during start-up; last output:\n"
                    + String.join("\n", scribe.tail(cntTail)), ex);
        }

        if (!flagReady)
            throw new ProcessException("PQS was not ready within " + timeoutReady
                    + "; last output:\n" + String.join("\n", scribe.tail(cntTail)));

        // Ready is a line scribe printed; running is a state. The health server
        // takes a fixed 8080 unless told otherwise and a bind failure there
        // ends the process - but the pipeline fiber can print a ready marker
        // first, so a run can record "pqs reported ready" and then stop a
        // scribe that was already gone.
        if (!ProcessSettle.holds(scribe::isRunning, settle))
            throw new ProcessException("PQS reported ready and then exited"
                    + " (health port " + scribe.nPortHealth() + "); last output:\n"
                    + String.join("\n", scribe.tail(cntTail)));
    }

}
