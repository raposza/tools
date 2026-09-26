// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.resolve;

import com.raposza.api.RefProbe_i;
import com.raposza.api.model.Contract;
import com.raposza.api.model.Resolved;
import com.raposza.spi.LedgerClient_i;

import java.util.List;
import java.util.Optional;

/**
 * Recognises a contract id.
 *
 * accepts() checks for a long hex string and no more. The exact Canton contract
 * id format is an OPEN QUESTION in the design, not a settled fact - the sampled
 * ids begin "00", but encoding a prefix nobody has confirmed would silently
 * reject ids from a participant configured differently. Being permissive is
 * safe here precisely because a finding requires the ledger to hand back a
 * contract.
 *
 * Runs after UpdateIdProbe: on Ledger API v1 there is no point lookup, so this
 * scans the active set, which is the most expensive step in the chain.
 *
 * A contract that is archived or not visible yields no finding. That is not the
 * same as "no such contract", and v1 cannot tell the difference.
 *
 * Author Claude/bentzn
 */
public final class ContractIdProbe implements RefProbe_i {

    private static final int CNT_MIN_HEX = 40;

    private final LedgerClient_i client;


    /** @param client the ledger to confirm against */
    public ContractIdProbe(LedgerClient_i client) {
        this.client = client;
    }


    @Override
    public boolean accepts(String strRef) {
        if (strRef.length() < CNT_MIN_HEX)
            return false;
        for (int cntLoop = 0; cntLoop < strRef.length(); cntLoop++) {
            if (Character.digit(strRef.charAt(cntLoop), 16) < 0)
                return false;
        }
        return true;
    }


    @Override
    public Optional<Finding> probe(String strRef, List<String> lstPartyRead) {
        Optional<Contract> contract = client.contract(strRef, lstPartyRead);
        return contract.map(c -> Finding.confirmed(new Resolved.AsContract(c)));
    }


    @Override
    public int order() {
        return 30;
    }

}
