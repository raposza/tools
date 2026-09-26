// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

/**
 * Where one statement got to. Sec. 8.
 *
 * <h2>Six, not two</h2>
 *
 * "Worked" and "did not" is the lie this enum exists to refuse. Three of the
 * six are failures and they are not interchangeable: FAILED_LOCALLY never
 * reached the participant, REJECTED reached it and was definitively refused,
 * and OUTCOME_UNKNOWN reached it and nobody knows. Only the first is safe to
 * assume changed nothing.
 *
 * Author Claude/bentzn
 */
public enum RunStatus {

    /** Accepted before submission; nothing was sent. The VALIDATE outcome. */
    VALIDATED,

    /** Sent; no completion observed yet. A transient state, not a result. */
    SUBMITTED,

    /** Completion observed, the ledger changed. */
    COMMITTED,

    /** The ledger definitively refused it. */
    REJECTED,

    /**
     * Refused before submission: a bad type, a stale binding, an ambiguous
     * template, a read-only profile. Nothing reached the participant.
     */
    FAILED_LOCALLY,

    /**
     * Submitted, and whether it committed cannot be determined. A timeout after
     * submission, a connection lost while awaiting completion.
     *
     * A run reaching this STOPS and claims neither the pre-command nor the
     * post-command state. It is the one case where the honest answer is "you
     * must look".
     */
    OUTCOME_UNKNOWN;


    /** @return true when the run may continue past this statement */
    public boolean flagContinue() {
        return this == VALIDATED || this == COMMITTED;
    }


    /** @return the wire spelling used in a transcript */
    public String strJson() {
        return name().toLowerCase().replace('_', '-');
    }

}
