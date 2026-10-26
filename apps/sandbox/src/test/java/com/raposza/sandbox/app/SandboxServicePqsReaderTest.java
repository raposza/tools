// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The PQS reader reaches the 3.x bootstrap when PQS is on, under both
 * launchers, and not otherwise - raposza/tools#4.
 *
 * Author Claude/bentzn
 */
class SandboxServicePqsReaderTest {

    private static final String STR_USER = "raposza-pqs";


    private static SandboxOptions parse(String... arrArg) {
        return SandboxOptions.parse(arrArg);
    }


    @Test
    void pqsOnCarriesTheReaderUnderBothLaunchers() {
        SandboxOptions optSub = parse("--pqs", "on");
        SandboxOptions optDaemon = parse("--launcher", "daemon", "--pqs", "on");

        assertEquals(STR_USER, SandboxService.bootstrapFor(optSub, STR_USER).strUserPqs());
        assertEquals(STR_USER,
                SandboxService.bootstrapDaemonFor(optDaemon, STR_USER).strUserPqs());
    }


    @Test
    void pqsOffOrNoReaderCarriesNone() {
        SandboxOptions optOff = parse("--pqs", "off");
        SandboxOptions optOn = parse("--pqs", "on");

        assertNull(SandboxService.bootstrapFor(optOff, STR_USER).strUserPqs());
        assertNull(SandboxService.bootstrapFor(optOn, null).strUserPqs());
        assertNull(SandboxService.bootstrapFor(optOn).strUserPqs());
    }
}
