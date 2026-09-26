// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.caps;

/**
 * The things a sandbox may or may not have, one per Canton version.
 *
 * <h2>Why a named feature rather than a version test at the call site</h2>
 *
 * A window that writes `if (version.major() == 2)` beside a tab has put a fact
 * about Canton into a layout method, where nobody looking for it will find it
 * and where the next surface will write its own copy. A feature is the fact,
 * named once; {@link SandboxCapabilities} is the only place that answers for
 * it, and every surface asks the same question.
 *
 * <h2>Two kinds of feature, and they are answered differently</h2>
 *
 * <b>Stack features</b> - {@link #JSON_API_PROCESS}, {@link #PQS} - are facts
 * about what THIS APPLICATION runs for a generation. `Sandbox2xStack` starts a
 * JSON API process and `SandboxStack` does not, so the answer is exact for
 * majors 2 and 3 and UNKNOWN for anything else, and it does not become truer
 * by being extrapolated.
 *
 * <b>Binary features</b> - the rest - are facts about the vendor's own command
 * line, read from `CantonLaunchTable`, which was MEASURED per binary. Those are
 * the ones extrapolation applies to: a version nobody has run is answered from
 * the nearest one that was, and the answer says so.
 *
 * Author Claude/bentzn
 */
public enum SandboxFeature {

    /**
     * The HTTP JSON API is a PROCESS OF ITS OWN, with its own output, its own
     * lifecycle and its own way of failing.
     *
     * True on 2.x, where it is `daml json-api` out of the SDK beside the
     * Canton being run. False on 3.x, where the participant serves the HTTP
     * Ledger API itself and there is nothing separate to watch, log or
     * restart - which is why a surface that shows it as a component on 3.x is
     * showing the participant twice under two names.
     */
    JSON_API_PROCESS("JSON API process",
            "the JSON API runs as a separate process with its own log"),

    /** The `sandbox` subcommand exists on the binary. From 3.4.4. */
    SANDBOX_SUBCOMMAND("sandbox subcommand",
            "the binary offers `sandbox` as a subcommand"),

    /** `--json-api-port` under the `sandbox` subcommand. */
    JSON_API_PORT("JSON API port flag",
            "the sandbox subcommand takes --json-api-port"),

    /** `--dev`: the unstable protocol version. */
    DEV_PROTOCOL("development protocol",
            "the sandbox subcommand takes --dev"),

    /** `--static-time`: time advances only through the time service. */
    STATIC_TIME("static time",
            "the sandbox subcommand takes --static-time"),

    /** `--multi-sync`. Measured on 3.5 and on nothing older. */
    MULTI_SYNC("multiple synchronizers",
            "the sandbox subcommand takes --multi-sync"),

    /**
     * PQS can be run against this stack. Both generations - the binary
     * that serves a line is resolved by `PqsSpec` and is a property of the
     * machine rather than of the version.
     */
    PQS("PQS", "PQS can be run against this stack");

    private final String strLabel;

    private final String strAbout;


    SandboxFeature(String strLabelNew, String strAboutNew) {
        this.strLabel = strLabelNew;
        this.strAbout = strAboutNew;
    }


    /**
     * @return the short name a surface shows
     */
    public String strLabel() {
        return strLabel;
    }


    /**
     * @return one line saying what the feature IS, for a tooltip
     */
    public String strAbout() {
        return strAbout;
    }
}
