// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Author Claude/bentzn
 */
class Sandbox2xPortsTest {

    /**
     * The two lines share a port block on purpose: the 2.x domain's APIs take
     * the numbers the 3.x sequencer's do, so a RAPOSZA_IT_* port stays valid
     * when a stack moves line.
     */
    @Test
    void theDefaultsSitOnTheSameBlockAsThreeX() {
        Sandbox2xPorts ports = Sandbox2xPorts.ofDefaults();
        SandboxPorts ports3x = SandboxPorts.ofDefaults();

        assertEquals(ports3x.nPortLedgerApi(), ports.nPortLedgerApi());
        assertEquals(ports3x.nPortAdminApi(), ports.nPortAdminApi());
        assertEquals(ports3x.nPortJsonApi(), ports.nPortJsonApi());
        assertEquals(ports3x.nPortSequencerPublic(), ports.nPortDomainPublic());
        assertEquals(ports3x.nPortSequencerAdmin(), ports.nPortDomainAdmin());
    }


    @Test
    void anOffsetMovesEveryPort() {
        Sandbox2xPorts ports = Sandbox2xPorts.ofDefaultsOffsetBy(10000);

        assertEquals(Sandbox2xPorts.N_DEFAULT_LEDGER_API + 10000, ports.nPortLedgerApi());
        assertEquals(Sandbox2xPorts.N_DEFAULT_ADMIN_API + 10000, ports.nPortAdminApi());
        assertEquals(Sandbox2xPorts.N_DEFAULT_DOMAIN_PUBLIC + 10000, ports.nPortDomainPublic());
        assertEquals(Sandbox2xPorts.N_DEFAULT_DOMAIN_ADMIN + 10000, ports.nPortDomainAdmin());
        assertEquals(Sandbox2xPorts.N_DEFAULT_JSON_API + 10000, ports.nPortJsonApi());
    }


    @Test
    void aPortOutOfRangeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Sandbox2xPorts(0, 6866, 6867, 6868, 6864));
        assertThrows(IllegalArgumentException.class,
                () -> new Sandbox2xPorts(6865, 6866, 6867, 6868, 70000));
    }


    /**
     * The one string that differs between the families and that cannot be
     * discovered at runtime: Canton refuses an auth-service type it does not
     * know and says nothing about what it would have accepted.
     */
    @Test
    void theLineDecidesTheJwksTypeName() {
        assertEquals(AuthOverlay.STR_TYPE_JWKS_2X, CantonLine.ofMajor(2).strTypeJwks());
        assertEquals(AuthOverlay.STR_TYPE_JWKS, CantonLine.ofMajor(3).strTypeJwks());
        assertThrows(IllegalArgumentException.class, () -> CantonLine.ofMajor(4));
    }


    @Test
    void theTwoXOverlayNamesTheParticipantItGuards() {
        String strRendered = AuthOverlay
                .ofJwksAudience(CantonLine.V2X, "file:///tmp/jwks.json", "aud")
                .render(Canton2xConfig.STR_NODE_PARTICIPANT);

        assertTrue(strRendered.contains("canton.participants."
                + Canton2xConfig.STR_NODE_PARTICIPANT + ".ledger-api.auth-services"));
        assertTrue(strRendered.contains(AuthOverlay.STR_TYPE_JWKS_2X));
    }
}
