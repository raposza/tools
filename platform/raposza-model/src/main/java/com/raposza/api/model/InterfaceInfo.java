// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.util.List;

/**
 * A Daml interface as the UI needs it.
 *
 * NOTE: the Ledger API mechanics for interface views and interface filters are
 * listed as an open question in the design. This record closes the structural
 * gap in the model; the retrieval path is not yet designed.
 *
 * @param idInterface the interface identifier
 * @param lstChoice choices declared by the interface
 * @param lstRequires interfaces this one requires, empty when none. Present
 *                    because the decoded AST carries it; neither fixture
 *                    exercises it, so it is modelled and unverified
 * @param typeView the view type, empty when the interface declares none
 *
 * Author Claude/bentzn
 */
public record InterfaceInfo(DataId idInterface, List<ChoiceInfo> lstChoice,
        List<DataId> lstRequires, java.util.Optional<DamlType> typeView) {}
