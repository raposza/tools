// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.util.List;

/**
 * One node of a transaction tree. Created and Exercised are the only node kinds
 * the Ledger API exposes; fetches and lookups are not reported.
 *
 * Author Claude/bentzn
 */
public sealed interface TxNode {

    /** @return child nodes, empty for a create */
    List<TxNode> lstChild();


    /**
     * @param idEvent event id
     * @param idContract the created contract
     * @param idTemplate template identifier
     * @param payload create argument
     * @param lstSignatory signatories
     * @param lstObserver observers
     */
    record Created(String idEvent, String idContract, DataId idTemplate, DamlValue.Rec payload,
            List<String> lstSignatory, List<String> lstObserver) implements TxNode {

        @Override
        public List<TxNode> lstChild() {
            return List.of();
        }

    }


    /**
     * @param idEvent event id
     * @param idContract the contract exercised upon
     * @param idTemplate template identifier
     * @param nameChoice choice name
     * @param flagConsuming true when the choice archived the contract
     * @param argument choice argument
     * @param valueResult choice return value
     * @param lstActor acting parties
     * @param lstChild consequences, in ledger order
     */
    record Exercised(String idEvent, String idContract, DataId idTemplate, String nameChoice,
            boolean flagConsuming, DamlValue argument, DamlValue valueResult,
            List<String> lstActor, List<TxNode> lstChild) implements TxNode {}

}
