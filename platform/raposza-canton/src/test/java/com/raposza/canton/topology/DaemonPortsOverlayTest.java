// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ports become configuration under `daemon`, which has none as flags.
 *
 * Author Claude/bentzn
 */
class DaemonPortsOverlayTest {

    @Test
    void everySixPortIsRestated() {
        SandboxPorts ports = SandboxPorts.ofDefaultsOffsetBy(20000);
        String strConf = new DaemonPortsOverlay(ports).render();

        assertTrue(strConf.contains("port = " + ports.nPortLedgerApi()), strConf);
        assertTrue(strConf.contains("port = " + ports.nPortAdminApi()), strConf);
        assertTrue(strConf.contains("port = " + ports.nPortJsonApi()), strConf);
        assertTrue(strConf.contains("port = " + ports.nPortSequencerPublic()), strConf);
        assertTrue(strConf.contains("port = " + ports.nPortSequencerAdmin()), strConf);
        assertTrue(strConf.contains("port = " + ports.nPortMediatorAdmin()), strConf);
    }


    /**
     * The node names are the vendor file's, not names of this project's
     * choosing: an overlay that renamed a node would add one rather than
     * configure it, and the stack would come up with both.
     */
    @Test
    void theNodeNamesAreTheVendorTopologysOwn() {
        String strConf = new DaemonPortsOverlay(SandboxPorts.ofDefaults()).render();

        assertTrue(strConf.contains(StorageOverlay.STR_NODE_PARTICIPANT + " {"), strConf);
        assertTrue(strConf.contains(StorageOverlay.STR_NODE_SEQUENCER + " {"), strConf);
        assertTrue(strConf.contains(StorageOverlay.STR_NODE_MEDIATOR + " {"), strConf);
    }


    /**
     * `http-ledger-api`, not `json-api`. The key is the one sandbox.conf uses
     * and Canton refuses an unknown key, so a stack that starts has confirmed
     * this spelling.
     */
    @Test
    void theHttpLedgerApiKeyIsSpeltAsTheVendorFileSpellsIt() {
        String strConf = new DaemonPortsOverlay(SandboxPorts.ofDefaults()).render();

        assertTrue(strConf.contains("http-ledger-api"), strConf);
        assertTrue(strConf.contains("public-api"), strConf);
    }


    @Test
    void portsAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new DaemonPortsOverlay(null));
    }


    @Test
    void theSameSpecRendersTheSameFile() {
        SandboxPorts ports = SandboxPorts.ofDefaults();

        assertEquals(new DaemonPortsOverlay(ports).render(),
                new DaemonPortsOverlay(ports).render());
    }
}
