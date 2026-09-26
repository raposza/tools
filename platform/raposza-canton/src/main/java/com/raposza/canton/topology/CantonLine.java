// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

/**
 * Which generation a stack belongs to, declared rather than inferred.
 *
 * There are two setup families and five targets, not five families. 2.8, 2.9
 * and 2.10 share a launcher, a hand-written topology, a separate JSON API
 * process and no PQS; 3.4 and 3.5 share the sandbox subcommand, an overlay
 * over vendor configuration, an in-participant JSON API and PQS. What varies
 * inside a family is values, not fields.
 *
 * The JWKS auth-service type name is the one string that differs and that
 * cannot be discovered at runtime, because Canton refuses to parse a type it
 * does not recognise and says nothing about what it would have accepted.
 *
 * Author Claude/bentzn
 */
public enum CantonLine {

    /** 2.8, 2.9, 2.10. */
    V2X(AuthOverlay.STR_TYPE_JWKS_2X),

    /** 3.4, 3.5. */
    V3X(AuthOverlay.STR_TYPE_JWKS);

    private final String strTypeJwks;


    CantonLine(String strTypeJwks) {
        this.strTypeJwks = strTypeJwks;
    }


    /**
     * @return the auth-service type name for a JWKS-verified participant on
     *         this line
     */
    public String strTypeJwks() {
        return strTypeJwks;
    }


    /**
     * @param nMajor the Canton major version
     * @return the family it belongs to
     * @throws IllegalArgumentException for anything outside 2 and 3, because a
     *         guess here produces a configuration Canton rejects at parse with
     *         no indication of why
     */
    public static CantonLine ofMajor(int nMajor) {
        if (nMajor == 2)
            return V2X;
        if (nMajor == 3)
            return V3X;
        throw new IllegalArgumentException("no setup family is known for Canton major version "
                + nMajor);
    }
}
