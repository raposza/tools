// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api;

/**
 * Supplies the bearer token for each call. Kept as an interface so a local
 * JWKS file, an OAuth client-credentials grant against a real provider, and a
 * token pasted by the operator are interchangeable.
 *
 * Implementations are responsible for their own caching and renewal; the
 * client calls this once per request and does not inspect the result.
 *
 * Author Claude/bentzn
 */
public interface TokenSource_i {

    /**
     * @return a bearer token valid for the ledger, without the "Bearer " prefix
     * @throws LedgerException when a token cannot be obtained
     */
    String token();


    /**
     * Human readable description of where the token comes from, for the status
     * bar. Never contains key material.
     *
     * @return e.g. "jwks-private.json, kid test-canton-stack, aud audience-based"
     */
    String describe();

}
