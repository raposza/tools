// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import com.raposza.runtime.port.PortClass;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The port classes on the LocalNetND numbering: nodes in 30xxx, the web UIs in
 * 31xxx, PostgreSQL in 32xxx, and no block leaving its thousand.
 *
 * Author Claude/bentzn
 */
class LocalNetPortsTest {

    @Test
    void theDefaultMapIsTheOneTheOperatorDecided() {
        LocalNetPorts ports = LocalNetPorts.ofDefaults();

        assertEquals(30010, ports.nPortFirst());
        assertEquals(30010, ports.nPortLedger("sv"));
        assertEquals(30015, ports.nPortGrpcHealth("sv"));
        assertEquals(30020, ports.nPortLedger("app-provider"));
        assertEquals(30032, ports.nPortJson("app-user"));
        assertEquals(30040, ports.nPortOf("sequencer.public"));
        assertEquals(30051, ports.nPortSvApp());
        assertEquals(31000, ports.nPortUi("sv"));
        assertEquals(31010, ports.nPortUi("app-provider"));
        assertEquals(31020, ports.nPortUi("app-user"));
        assertEquals(32101, ports.nPortPostgres());
    }


    /** THE THOUSAND SAYS WHAT A PORT IS, for every port the map carries. */
    @Test
    void everyPortIsInItsClass() {
        for (Map.Entry<String, Integer> entry : LocalNetPorts.ofDefaults().mapPort().entrySet()) {
            PortClass portClass = entry.getKey().endsWith("." + LocalNetPorts.STR_KEY_UI)
                    ? PortClass.UI : PortClass.NODE;
            assertTrue(portClass.contains(entry.getValue()), entry.toString());
        }
        assertTrue(PortClass.ADMIN.contains(LocalNetPorts.ofDefaults().nPortPostgres()));
    }


    /** THE CAPS ARE WHERE THE LAST SLOT OF EACH BLOCK MEETS THE END OF ITS THOUSAND. */
    @Test
    void noBlockLeavesItsThousand() {
        assertEquals(30957, LocalNetPorts.N_PORT_FIRST_MAX);
        assertEquals(31979, LocalNetPorts.N_PORT_UI_FIRST_MAX);

        LocalNetPorts portsTop = LocalNetPorts.ofFirst(LocalNetPorts.N_PORT_FIRST_MAX,
                LocalNetPorts.N_PORT_UI_FIRST_MAX, LocalNetPorts.N_PORT_POSTGRES_DEFAULT);
        for (Map.Entry<String, Integer> entry : portsTop.mapPort().entrySet()) {
            PortClass portClass = entry.getKey().endsWith("." + LocalNetPorts.STR_KEY_UI)
                    ? PortClass.UI : PortClass.NODE;
            assertTrue(portClass.contains(entry.getValue()), entry.toString());
        }
        assertEquals(30999, LocalNetPorts.N_PORT_FIRST_MAX + LocalNetPorts.N_SPAN_NODE);
        assertEquals(31999, portsTop.nPortUi("app-user"));

        assertThrows(IllegalArgumentException.class, () -> LocalNetPorts.ofFirst(30958,
                LocalNetPorts.N_PORT_POSTGRES_DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> LocalNetPorts.ofFirst(22010,
                LocalNetPorts.N_PORT_POSTGRES_DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> LocalNetPorts.ofFirst(30010, 31980,
                LocalNetPorts.N_PORT_POSTGRES_DEFAULT));
    }


    /** RULE 5 - PostgreSQL is never in a block, and a node port is refused for it. */
    @Test
    void postgresIsAnAdministrativePort() {
        assertThrows(IllegalArgumentException.class, () -> LocalNetPorts.ofFirst(30010, 30100));
    }


    /** THE BUNDLE'S OWN NUMBERING IS UNTOUCHED - the Windows runner still takes it. */
    @Test
    void theBundleNumberingStillBuilds() {
        assertTrue(LocalNetPorts.ofBundle().nPortLedger("sv") > 0);
    }
}
