// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Author Claude/bentzn
 */
class SandboxPortsTest {

    @Test
    void defaultsAreCantonsOwn() {
        // From sandbox/sandbox.conf inside the 3.4.11 and 3.5.11 jars, which
        // are byte-identical at 1023 bytes.
        SandboxPorts ports = SandboxPorts.ofDefaults();
        assertEquals(6864, ports.nPortJsonApi());
        assertEquals(6865, ports.nPortLedgerApi());
        assertEquals(6866, ports.nPortAdminApi());
        assertEquals(6867, ports.nPortSequencerPublic());
        assertEquals(6868, ports.nPortSequencerAdmin());
        assertEquals(6869, ports.nPortMediatorAdmin());
    }


    @Test
    void anOffsetMovesTheWholeBlock() {
        SandboxPorts ports = SandboxPorts.ofDefaultsOffsetBy(10000);
        assertEquals(16864, ports.nPortJsonApi());
        assertEquals(16869, ports.nPortMediatorAdmin());
    }


    @Test
    void refusesAPortOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new SandboxPorts(0, 6866, 6864, 6867, 6868, 6869));
        assertThrows(IllegalArgumentException.class,
                () -> SandboxPorts.ofDefaultsOffsetBy(100000));
    }
}
