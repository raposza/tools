// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.resolve;

import com.raposza.api.RefProbe_i;
import com.raposza.api.model.Resolved;
import com.raposza.api.model.TxTree;
import com.raposza.spi.LedgerClient_i;

import java.util.List;
import java.util.Optional;

/**
 * Recognises a transaction (update) id.
 *
 * accepts() is deliberately loose: a LedgerString has no shape this code can
 * rely on, and the observed ids differ between generations and between how a
 * transaction was submitted. The confirmation does the work, not the filter.
 *
 * Runs before ContractIdProbe because a lookup by id is one round trip while a
 * contract lookup scans the active set. Both are confirming probes, so the
 * cheaper one goes first - and no contract id resolves as a transaction, since
 * they are different id spaces on the participant.
 *
 * Author Claude/bentzn
 */
public final class UpdateIdProbe implements RefProbe_i {

    private static final int CNT_MIN = 8;

    private final LedgerClient_i client;


    /** @param client the ledger to confirm against */
    public UpdateIdProbe(LedgerClient_i client) {
        this.client = client;
    }


    @Override
    public boolean accepts(String strRef) {
        if (strRef.length() < CNT_MIN)
            return false;
        for (int cntLoop = 0; cntLoop < strRef.length(); cntLoop++) {
            if (Character.isWhitespace(strRef.charAt(cntLoop)))
                return false;
        }
        return true;
    }


    @Override
    public Optional<Finding> probe(String strRef, List<String> lstPartyRead) {
        Optional<TxTree> tree = client.tree(strRef, lstPartyRead);
        return tree.map(t -> Finding.confirmed(new Resolved.AsUpdate(t)));
    }


    @Override
    public int order() {
        return 20;
    }

}
