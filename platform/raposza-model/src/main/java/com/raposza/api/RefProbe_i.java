// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api;

import com.raposza.api.model.Resolved;

import java.util.List;
import java.util.Optional;

/**
 * One interpretation attempt in the resolver chain.
 *
 * A probe that recognises the syntax but is not certain returns a result and
 * lets the resolver collect candidates; silently picking the wrong
 * interpretation is worse than asking the operator.
 *
 * The confidence is per FINDING, not per probe. The same probe can be certain
 * about one string and guessing about another - a contract id that the ledger
 * hands back a contract for is a different claim from one that merely has the
 * right shape - and collapsing the two into a property of the probe loses
 * exactly the distinction the chain is built on.
 *
 * Author Claude/bentzn
 */
public interface RefProbe_i {

    /**
     * What a probe found, and on what evidence.
     *
     * @param resolved the interpretation
     * @param flagConfirmed true when the LEDGER returned the object, false when
     *        the string merely has the right shape. Only a confirmed finding
     *        ends the chain
     */
    record Finding(Resolved resolved, boolean flagConfirmed) {

        /** @param resolved an interpretation the ledger returned */
        public static Finding confirmed(Resolved resolved) {
            return new Finding(resolved, true);
        }


        /** @param resolved an interpretation based on the string's shape alone */
        public static Finding syntactic(Resolved resolved) {
            return new Finding(resolved, false);
        }

    }


    /**
     * Cheap syntactic pre-filter. Must not call the ledger.
     *
     * @param strRef the pasted identifier
     * @return true when this probe could plausibly handle it
     */
    boolean accepts(String strRef);


    /**
     * @param strRef the pasted identifier
     * @param lstPartyRead parties to read as
     * @return the finding, empty when this probe found nothing
     */
    Optional<Finding> probe(String strRef, List<String> lstPartyRead);


    /**
     * @return ordering weight; lower runs first. Order the CONFIRMING probes
     *         cheapest first, since the chain stops at the first confirmation;
     *         probes that can only ever guess pay the full chain anyway
     */
    int order();

}
