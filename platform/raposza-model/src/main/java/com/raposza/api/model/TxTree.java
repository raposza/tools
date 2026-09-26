// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A transaction as the "did it happen" pane renders it.
 *
 * @param idUpdate update (transaction) id
 * @param idCommand submitting command id, empty when not visible to the reader
 * @param idWorkflow workflow id, empty when unset
 * @param offset ledger offset
 * @param instEffective ledger effective time
 * @param lstRoot root nodes in ledger order
 *
 * Author Claude/bentzn
 */
public record TxTree(String idUpdate, Optional<String> idCommand, Optional<String> idWorkflow,
        String offset, Instant instEffective, List<TxNode> lstRoot) {}
