// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * A poll is shown once and hidden while it keeps polling; anything that
 * differs - a Tap, a changed outcome - is shown; a poll that went quiet for
 * the window is shown again.
 *
 * THE CONTROL CAN FAIL: the balance poll arrives every second for a minute,
 * longer than the window, so a filter that measured from the FIRST sighting
 * instead of the last would let it through again at 30 s.
 *
 * Author Claude/bentzn
 */
class WebRepeatsTest {

    private static final String STR_POLL =
            "wallet.localhost  GET /api/validator/v0/wallet/balance  proxy http://127.0.0.1:30013  ok";


    @Test
    void aPollIsShownOnceAndHiddenWhileItPolls() {
        AtomicLong refNow = new AtomicLong(1_000_000L);
        WebRepeats repeats = new WebRepeats(refNow::get);
        assertTrue(repeats.isNew(STR_POLL));
        for (int cntSecond = 1; cntSecond <= 60; cntSecond++) {
            refNow.addAndGet(1000L);
            assertFalse(repeats.isNew(STR_POLL), "second " + cntSecond);
        }
    }


    @Test
    void aTapAndAChangedOutcomeAreShown() {
        AtomicLong refNow = new AtomicLong(1_000_000L);
        WebRepeats repeats = new WebRepeats(refNow::get);
        repeats.isNew(STR_POLL);
        refNow.addAndGet(500L);
        assertTrue(repeats.isNew(
                "wallet.localhost  POST /api/validator/v0/wallet/tap  proxy http://127.0.0.1:30013  ok"));
        assertTrue(repeats.isNew(STR_POLL.replace("  ok", "  FAILED 502")));
    }


    @Test
    void aPollQuietForTheWindowIsShownAgain() {
        AtomicLong refNow = new AtomicLong(1_000_000L);
        WebRepeats repeats = new WebRepeats(refNow::get);
        repeats.isNew(STR_POLL);
        refNow.addAndGet(WebRepeats.N_MS_WINDOW);
        assertFalse(repeats.isNew(STR_POLL));
        refNow.addAndGet(WebRepeats.N_MS_WINDOW + 1L);
        assertTrue(repeats.isNew(STR_POLL));
    }

}
