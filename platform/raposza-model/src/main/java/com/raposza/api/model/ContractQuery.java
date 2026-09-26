// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.util.List;
import java.util.Optional;

/**
 * Filter for an active contract search.
 *
 * @param lstPartyRead parties to read as; must be non-empty
 * @param lstTemplate templates to include, empty means all visible
 * @param lstInterface interfaces to include, empty means none. Retrieval
 *                     mechanics for interface filters are an open design
 *                     question; the field exists so the model is not reshaped
 *                     later
 * @param strFilter free text matched against the rendered payload, empty means
 *                  no text filter
 * @param cntLimit maximum contracts to return
 * @param offsetAt ledger offset to read at, empty means current ledger end
 *
 * Author Claude/bentzn
 */
public record ContractQuery(List<String> lstPartyRead, List<DataId> lstTemplate,
        List<DataId> lstInterface, String strFilter, int cntLimit, Optional<String> offsetAt) {

    public static ContractQuery of(List<String> lstPartyRead, int cntLimit) {
        return new ContractQuery(lstPartyRead, List.of(), List.of(), "", cntLimit,
                Optional.empty());
    }

}
