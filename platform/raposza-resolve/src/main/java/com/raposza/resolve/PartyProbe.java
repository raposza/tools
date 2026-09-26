// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.resolve;

import com.raposza.api.RefProbe_i;
import com.raposza.api.model.PartyInfo;
import com.raposza.api.model.Resolved;
import com.raposza.spi.LedgerClient_i;

import java.util.List;
import java.util.Optional;

/**
 * Recognises a party id.
 *
 * accepts() tests for "something::fingerprint" and NOTHING else. The party id
 * shape depends on how the party was allocated, not on the participant version,
 * and one 2.x participant holds several shapes at once - a hint-uuid form and a
 * bare-name form side by side. Any rule tighter than the namespace separator is
 * a guess that will reject a real party.
 *
 * Runs first because parties() is one cheap call and the separator is a strong
 * marker; nothing else in the chain looks like this.
 *
 * Never guesses. A party the participant does not know cannot be turned into a
 * PartyInfo without inventing its display name and locality, so an unknown one
 * yields no finding at all.
 *
 * Author Claude/bentzn
 */
public final class PartyProbe implements RefProbe_i {

    private static final String SEP_NAMESPACE = "::";

    private final LedgerClient_i client;


    /** @param client the ledger to confirm against */
    public PartyProbe(LedgerClient_i client) {
        this.client = client;
    }


    @Override
    public boolean accepts(String strRef) {
        int posSep = strRef.indexOf(SEP_NAMESPACE);
        return posSep > 0 && posSep < strRef.length() - SEP_NAMESPACE.length();
    }


    @Override
    public Optional<Finding> probe(String strRef, List<String> lstPartyRead) {
        for (PartyInfo party : client.parties()) {
            if (strRef.equals(party.idParty()))
                return Optional.of(Finding.confirmed(new Resolved.AsParty(party)));
        }
        return Optional.empty();
    }


    @Override
    public int order() {
        return 10;
    }

}
