// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

/**
 * Where a staged scribe.jar came from.
 *
 * Recorded rather than derived: PQS availability is an independent axis from
 * the Canton version, and a jar's provenance is a licensing fact.
 *
 * Author Claude/bentzn
 */
public enum PqsSource {

    DPM,

    OCI_IMAGE,

    MANUAL,

    /**
     * A locally built stand-in, NOT vendor scribe.
     *
     * It is a valid resolution target - it starts, it is wired to, and the 2.x
     * harness path runs against it - and it is not evidence about scribe. A
     * stand-in reproduces the banner it imitates, so the banner cannot be what
     * tells the two apart; only this constant can, and it comes from the
     * SOURCE.txt staged beside the jar.
     */
    MOCK,

    /** No provenance record was found alongside the jar. */
    UNKNOWN
}
