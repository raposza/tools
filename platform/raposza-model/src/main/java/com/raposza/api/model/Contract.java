// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.util.List;
import java.util.Optional;

/**
 * An active or historic contract.
 *
 * @param idContract contract id
 * @param idEvent id of the create event that produced it, empty string when
 *                the participant did not report one. This is the handle back to
 *                the transaction that created the contract: the id is opaque
 *                and is passed to the ledger rather than parsed, because its
 *                internal shape is not something this tool is entitled to
 *                assume
 * @param idTemplate template identifier
 * @param payload the create argument
 * @param lstSignatory signatories
 * @param lstObserver observers
 * @param key contract key, empty when the template has none
 * @param offsetCreated ledger offset of the create
 * @param offsetArchived ledger offset of the archive, empty while active
 *
 * Author Claude/bentzn
 */
public record Contract(String idContract, String idEvent, DataId idTemplate,
        DamlValue.Rec payload, List<String> lstSignatory, List<String> lstObserver,
        Optional<DamlValue> key, String offsetCreated, Optional<String> offsetArchived) {

    public boolean isActive() {
        return offsetArchived.isEmpty();
    }


    /** @return true when this contract can be traced to its creating transaction */
    public boolean hasEvent() {
        return idEvent != null && !idEvent.isBlank();
    }

}
