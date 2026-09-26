// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.resolve;

import com.raposza.api.RefProbe_i;
import com.raposza.api.model.Resolved;

import java.util.List;
import java.util.Optional;

/**
 * Recognises something shaped like a ledger offset.
 *
 * This probe can NEVER confirm. Ledger API v1 offers no "is this a valid
 * offset" call, and an offset is a position rather than an object, so there is
 * nothing to fetch and compare. Every finding it makes is a guess about shape,
 * which is why it runs last and why it never ends the chain.
 *
 * The consequence is intended: a real contract id also looks like this, so a
 * paste that resolves to a contract returns the contract and this probe's guess
 * is discarded. A paste that resolves to nothing returns the guess - "this
 * looks like an offset" - which is the honest answer and all AsOffset ever
 * claimed, since it carries the string and nothing else.
 *
 * The v1 offset form is not pinned by any evidence in this workspace beyond
 * samples, so the filter stays loose deliberately.
 *
 * Author Claude/bentzn
 */
public final class OffsetProbe implements RefProbe_i {

    private static final int CNT_MIN = 6;

    @Override
    public boolean accepts(String strRef) {
        if (strRef.length() < CNT_MIN)
            return false;
        for (int cntLoop = 0; cntLoop < strRef.length(); cntLoop++) {
            if (Character.digit(strRef.charAt(cntLoop), 16) < 0)
                return false;
        }
        return true;
    }


    @Override
    public Optional<Finding> probe(String strRef, List<String> lstPartyRead) {
        return Optional.of(Finding.syntactic(new Resolved.AsOffset(strRef)));
    }


    @Override
    public int order() {
        return 90;
    }

}
