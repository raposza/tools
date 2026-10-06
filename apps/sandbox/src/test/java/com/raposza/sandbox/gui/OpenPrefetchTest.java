// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * The window's first read takes what the prefetch read, and reads the disk
 * itself only when there is nothing to take - A-63 (b).
 *
 * THE CONTROLS CAN FAIL: the supplier counts its calls, so a read made BESIDE
 * a prefetched value - the cost this exists to remove - reads 1 instead of 0;
 * and a prefetch that failed completes with null, which must not reach the
 * window as an answer.
 *
 * Author Claude/bentzn
 */
class OpenPrefetchTest {

    @Test
    void aPrefetchedValueIsTakenAndNothingIsReadAgain() {
        AtomicInteger cntRead = new AtomicInteger();
        String strGot = OpenPrefetch.joinOr(CompletableFuture.completedFuture("prefetched"),
                () -> {
                    cntRead.incrementAndGet();
                    return "read now";
                });
        assertEquals("prefetched", strGot);
        assertEquals(0, cntRead.get());
    }


    @Test
    void withNoPrefetchTheDiskIsReadNow() {
        assertEquals("read now", OpenPrefetch.joinOr(null, () -> "read now"));
    }


    @Test
    void aFailedPrefetchIsReadAgainNotHandedOver() {
        CompletableFuture<String> futureFailed = CompletableFuture.completedFuture(null);
        assertEquals("read now", OpenPrefetch.joinOr(futureFailed, () -> "read now"));
    }

}
