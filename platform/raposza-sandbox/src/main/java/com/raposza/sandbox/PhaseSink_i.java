// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox;

import java.time.Duration;

/**
 * When a component of a stack begins coming up, and how long it took.
 *
 * <h2>Why this is not the output sink</h2>
 *
 * `useOutputSink` carries what a PROCESS printed - thousands of lines, in the
 * vendor's words, at the vendor's level of detail. A reader watching a start
 * wants four facts and no more: which component is coming up, and how long each
 * one took. Deriving those from the output stream would mean matching on
 * vendor strings, which this project has already learned costs more than it
 * saves: a readiness sentinel stops firing when a binary changes one word.
 *
 * The stack knows when it calls `start()` and when `awaitReady` returns. That
 * is the signal, and it is stated rather than inferred.
 *
 * <h2>It reports what HAPPENED, not what went wrong</h2>
 *
 * There is no `failed`. A failure leaves the stack by the exception path, which
 * carries the diagnosis, and a sink that reported it as well would put the same
 * failure on screen twice in two wordings.
 *
 * @see StackComponent for the names passed here
 *
 * Author Claude/bentzn
 */
public interface PhaseSink_i {

    /**
     * @param strComponent one of {@link StackComponent}
     */
    void starting(String strComponent);


    /**
     * @param strComponent one of {@link StackComponent}
     * @param duration wall clock from {@link #starting} to serving
     */
    void started(String strComponent, Duration duration);
}
