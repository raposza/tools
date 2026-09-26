// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.util.Optional;

/**
 * Outcome of a submission. A rejection is a normal result, not an exception:
 * the whole point of the Act surface is to show the operator why the ledger
 * said no, with the error code intact.
 *
 * THE OUTCOME IS THREE-WAY, not a boolean. A submission whose completion
 * never arrived either committed or did not, and this tool cannot tell which; a
 * boolean forces it to claim one of them, and both claims are lies about the
 * same participant. UNKNOWN says so, and a caller that stops there is correct
 * to stop - continuing past it would act on a ledger nobody described.
 *
 * @param outcome what is known about the submission
 * @param idUpdate resulting update id, empty unless COMMITTED
 * @param tree resulting transaction tree, empty unless COMMITTED
 * @param valueResult exercise return value, empty for a create and unless
 *                    COMMITTED
 * @param codeError Canton error code on REJECTED, e.g. "CONTRACT_NOT_FOUND";
 *                  on UNKNOWN the transport code that ended the wait, e.g.
 *                  "DEADLINE_EXCEEDED"; empty on COMMITTED
 * @param strError human readable detail, empty on COMMITTED
 *
 * Author Claude/bentzn
 */
public record SubmitResult(Outcome outcome, Optional<String> idUpdate, Optional<TxTree> tree,
        Optional<DamlValue> valueResult, Optional<String> codeError, Optional<String> strError) {

    /** What is known about a submission after the wait ended. */
    public enum Outcome {

        /** The participant reported a completion and it was an acceptance. */
        COMMITTED,

        /** The participant reported a completion and it was a rejection. */
        REJECTED,

        /**
         * No completion was observed. The command may or may not have
         * committed, and the only honest next step is to go and look.
         */
        UNKNOWN
    }


    /**
     * NOT named isCommitted: an is-prefixed no-arg method on a record is a bean
     * getter to Jackson, which is the trap ChoiceInfo already documents.
     *
     * @return true when the participant accepted the command
     */
    public boolean flagCommitted() {
        return outcome == Outcome.COMMITTED;
    }


    /**
     * @param idUpdate the resulting update id
     * @param tree the resulting tree
     * @param valueResult the exercise result, null for a create
     * @return an accepted submission
     */
    public static SubmitResult committed(String idUpdate, TxTree tree, DamlValue valueResult) {
        return new SubmitResult(Outcome.COMMITTED, Optional.ofNullable(idUpdate),
                Optional.ofNullable(tree), Optional.ofNullable(valueResult), Optional.empty(),
                Optional.empty());
    }


    /**
     * @param codeError the Canton error code
     * @param strError the rejection detail
     * @return a rejected submission
     */
    public static SubmitResult rejected(String codeError, String strError) {
        return new SubmitResult(Outcome.REJECTED, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.ofNullable(codeError), Optional.ofNullable(strError));
    }


    /**
     * @param codeError the transport code that ended the wait
     * @param strError what was waited for and for how long
     * @return a submission whose outcome was never observed
     */
    public static SubmitResult unknown(String codeError, String strError) {
        return new SubmitResult(Outcome.UNKNOWN, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.ofNullable(codeError), Optional.ofNullable(strError));
    }

}
