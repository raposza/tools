// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.time.Duration;
import java.util.List;

/**
 * Who submits, and under what identity.
 *
 * The acting identity is a LIST, not a single party. Canton supports
 * multi-party submission and a create with two signatories cannot be expressed
 * without it - so a single field here would silently make one whole class of
 * template unreachable from both surfaces.
 *
 * @param lstPartyAct acting parties, must be non-empty
 * @param lstPartyRead additional read-as parties, may be empty
 * @param idCommand command id; generate one per submission so the result can be
 *                  found again in the Explore pane
 * @param idApplication application id reported to the ledger
 * @param timeout how long to wait for completion
 *
 * Author Claude/bentzn
 */
public record SubmitContext(List<String> lstPartyAct, List<String> lstPartyRead, String idCommand,
        String idApplication, Duration timeout) {

    /**
     * The single-party case, which is most of them, without inviting a
     * single-party field back into the record.
     *
     * @param idPartyAct the acting party
     * @param idCommand command id
     * @param idApplication application id
     * @param timeout completion timeout
     * @return a context acting as one party and reading as none
     */
    public static SubmitContext of(String idPartyAct, String idCommand, String idApplication,
            Duration timeout) {
        return new SubmitContext(List.of(idPartyAct), List.of(), idCommand, idApplication,
                timeout);
    }

}
