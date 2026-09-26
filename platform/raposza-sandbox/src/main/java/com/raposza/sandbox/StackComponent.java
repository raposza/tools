// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox;

/**
 * The names a stack labels its process output with.
 *
 * Strings rather than an enum, and rather than a type in a module above this
 * one, because the consumer is a window in `apps/sandbox` and the producer is
 * a stack in `platform/raposza-sandbox`. An enum here would be a public type
 * this module has to keep for a caller it must not know about; a name is the
 * smaller commitment and is what a log line carries anyway.
 *
 * Author Claude/bentzn
 */
public final class StackComponent {

    /** The embedded PostgreSQL server. Lifecycle lines only - see below. */
    public static final String STR_POSTGRES = "postgres";

    /**
     * The Canton process: the participant on both generations, and on 3.x the
     * bootstrap console that runs against it.
     */
    public static final String STR_PARTICIPANT = "participant";

    /** scribe, the stand-in, or the in-process mock. */
    public static final String STR_PQS = "pqs";

    /** The 2.x HTTP JSON API, which is a process of its own. */
    public static final String STR_JSON_API = "json-api";


    private StackComponent() {
    }

}
