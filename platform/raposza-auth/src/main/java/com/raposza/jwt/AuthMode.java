// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.jwt;

/**
 * How a profile obtains its bearer token.
 *
 * NONE is the default and is a real answer rather than a missing one: a local
 * sandbox accepts unauthenticated calls, and the alternative - inferring "no
 * auth" from an absent setting - makes an unreadable settings file and a
 * deliberately open participant indistinguishable.
 *
 * Author Claude/bentzn
 */
public enum AuthMode {

    /** No authorization header is sent at all. */
    NONE,

    /** A token the operator pasted or stored, sent verbatim. */
    TOKEN,

    /** Tokens minted locally from a JWKS file holding private key material. */
    JWKS

}
