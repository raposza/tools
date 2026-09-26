// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

/**
 * Which Ledger API generation an implementation speaks. The GUI never branches
 * on this; it exists for display and for refusing an obviously mismatched
 * profile before the first call.
 *
 * Author Claude/bentzn
 */
public enum ApiGeneration {

    /** Canton 2.x, Ledger API v1, domains. */
    V1,

    /** Canton 3.x, Ledger API v2, synchronizers. */
    V2

}
