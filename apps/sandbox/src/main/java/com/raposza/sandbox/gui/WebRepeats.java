// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Which lines of the Web tab's Log are a page polling - his question,
 * 2026-10-04: "In the log there are many automated calls that come with short
 * interval. Can we filter them?"
 *
 * <h2>A repeat is the SAME line within {@link #N_MS_WINDOW}</h2>
 *
 * Measured in his paste of 2026-10-04: the wallet asks the same ten paths
 * about once a second for as long as it is open. A line is the host, the
 * method, the path, what answered and the outcome, so the same line again
 * inside the window is a poll and is hidden; the first of them is shown, so
 * the reader still sees that the page asks it. A line that differs in any
 * part is new - a Tap, a status that changed from ok to FAILED, another
 * party's path. Every sighting moves the window, so a poll stays hidden for as
 * long as it keeps polling, and shows again once it has been quiet that long.
 *
 * WHAT IT COSTS: a request a person repeats inside the window - a second Tap
 * with the same outcome - is hidden too. The box on the Log tab turns the
 * filter off.
 *
 * Safe from any thread: LocalNetND's log and the RAWAR server both feed it.
 *
 * Author Claude/bentzn
 */
final class WebRepeats {

    /** How long a line counts as a repeat of the one before it. */
    static final long N_MS_WINDOW = 30_000L;

    /** Beyond this many lines remembered, the quiet ones are forgotten. */
    private static final int CNT_PRUNE = 2000;

    private final Map<String, Long> mapLastMs = new HashMap<>();

    private final LongSupplier supNow;


    WebRepeats() {
        this(System::currentTimeMillis);
    }


    /**
     * @param supNowNew the clock, in milliseconds - a test's own
     */
    WebRepeats(LongSupplier supNowNew) {
        this.supNow = supNowNew;
    }


    /**
     * Records the line either way.
     *
     * @param strLine one worded request
     * @return whether it is not a repeat
     */
    synchronized boolean isNew(String strLine) {
        long nNow = supNow.getAsLong();
        Long nLast = mapLastMs.put(strLine, nNow);
        if (mapLastMs.size() > CNT_PRUNE)
            prune(nNow);
        return nLast == null || nNow - nLast > N_MS_WINDOW;
    }


    private void prune(long nNow) {
        Iterator<Map.Entry<String, Long>> it = mapLastMs.entrySet().iterator();
        while (it.hasNext()) {
            if (nNow - it.next().getValue() > N_MS_WINDOW)
                it.remove();
        }
    }

}
