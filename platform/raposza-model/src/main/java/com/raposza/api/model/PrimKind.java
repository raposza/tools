// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

/**
 * Daml-LF primitive type kinds that a form widget must distinguish.
 * NUMERIC is absent: it carries a scale and lives in DamlType.Numeric.
 *
 * Author Claude/bentzn
 */
public enum PrimKind {
    UNIT,
    BOOL,
    INT64,
    TEXT,
    TIMESTAMP,
    DATE,
    PARTY,
    CONTRACT_ID
}
