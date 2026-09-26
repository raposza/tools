// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

/**
 * What the client learned about the far end at connect time. Shown in the
 * status bar so the operator always knows which participant is answering.
 *
 * @param idLedger ledger id, empty string on Ledger API v2 where it is gone
 * @param idParticipant participant id
 * @param versionApi reported Ledger API version
 * @param generation which Ledger API generation the client is speaking
 *
 * Author Claude/bentzn
 */
public record LedgerInfo(String idLedger, String idParticipant, String versionApi,
        ApiGeneration generation) {}
