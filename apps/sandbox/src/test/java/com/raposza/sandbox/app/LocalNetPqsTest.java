// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.canton.install.VersionId;
import com.raposza.runtime.db.PostgresCoordinates;
import com.raposza.runtime.localnet.LocalNetPorts;
import com.raposza.runtime.localnet.LocalNetSpec;
import com.raposza.runtime.port.PortClass;

import org.junit.jupiter.api.Test;

/**
 * PQS on LocalNetND: the participant it reads, the ports it takes and the
 * database it writes, all off the stack's own block rather than off literals.
 *
 * Author Claude/bentzn
 */
class LocalNetPqsTest {

    @Test
    void itReadsTheAppProviderLedger() {
        // HIS INSTRUCTION, 2026-09-22, and the port is the block's own.
        LocalNetPorts ports = LocalNetPorts.ofDefaults();

        assertEquals("app-provider", LocalNetPqs.STR_ROLE);
        assertEquals(ports.nPortLedger("app-provider"), LocalNetPqs.nPortLedger(ports));
        // AND NOT sv's OR app-user's, which is the whole of the instruction.
        assertTrue(LocalNetPqs.nPortLedger(ports) != ports.nPortLedger("sv"));
        assertTrue(LocalNetPqs.nPortLedger(ports) != ports.nPortLedger("app-user"));
    }


    /**
     * THE HEALTH PORT MOVES WITH THE BLOCK. A literal here would address the
     * default stack from one that had been moved off it, and scribe would bind
     * a port the operator did not give this stack.
     */
    @Test
    void theHealthPortIsTheLastSlotOfTheNodeBlock() {
        LocalNetPorts ports = LocalNetPorts.ofDefaults();
        int nFirst = ports.nPortLedger("sv");
        int nHealth = LocalNetPqs.nPortHealth(ports);

        assertEquals(nFirst + LocalNetPqs.N_OFFSET_HEALTH, nHealth);
        // DIRECTLY above the infrastructure block, and the block's last slot.
        assertEquals(nFirst + LocalNetPorts.N_OFFSET_INFRA
                + LocalNetPorts.LST_SLOT_INFRA.size(), nHealth, "infra");
        assertEquals(nFirst + LocalNetPorts.N_SPAN_NODE, nHealth, "span");
        // A NODE PORT, and inside the thousand even at the highest first port.
        assertTrue(PortClass.NODE.contains(nHealth), String.valueOf(nHealth));
        assertTrue(PortClass.NODE.contains(LocalNetPqs.nPortHealth(LocalNetPorts.ofFirst(
                LocalNetPorts.N_PORT_FIRST_MAX, LocalNetPorts.N_PORT_POSTGRES_DEFAULT))), "cap");
        // AND IT COLLIDES WITH NOTHING THE STACK ITSELF BINDS.
        assertTrue(!ports.mapPort().containsValue(Integer.valueOf(nHealth)),
                String.valueOf(nHealth));
        assertTrue(nHealth != ports.nPortPostgres());
    }


    @Test
    void itWritesToTheStacksOwnCluster() {
        LocalNetPorts ports = LocalNetPorts.ofDefaults();
        PostgresCoordinates pg = LocalNetPqs.pgOf("127.0.0.1", ports);

        assertEquals(ports.nPortPostgres(), pg.nPort());
        assertEquals(LocalNetSpec.STR_DB_PQS, pg.strDatabase());
        // AND THE RUNNER CREATES IT. A database nothing ensures is a scribe
        // that starts and fails on its first connection.
        assertTrue(LocalNetSpec.lstAllDatabases().contains(LocalNetSpec.STR_DB_PQS),
                String.valueOf(LocalNetSpec.lstAllDatabases()));
        // It is its OWN database and not a participant's.
        assertTrue(!LocalNetSpec.STR_DB_PQS.equals(LocalNetSpec.strDbParticipant("app-provider")));
    }


    /** PQS pairs major.minor with Canton and nothing else - `pqs_inventory.md` 3. */
    @Test
    void theLineComesFromTheStockCanton() {
        assertEquals("3.5", LocalNetPqs.strCantonLineOf(
                VersionId.of(3, 5, 14)));
        assertEquals("3.4", LocalNetPqs.strCantonLineOf(
                VersionId.of(3, 4, 11)));
        assertThrows(IllegalArgumentException.class, () -> LocalNetPqs.strCantonLineOf(null));
    }


    /**
     * A SPEC IS ALWAYS PRODUCED. Where no scribe is staged for the line the
     * mock stands in, which is what lets the stack start on a machine that
     * holds no PQS binary at all - `pqs_inventory.md` section 2, where every
     * 2.x row is unobtainable without a Daml Enterprise entitlement.
     */
    @Test
    void aMissingBinaryIsTheMockAndNotARefusal() {
        assertNotNull(LocalNetPqs.specFor(
                VersionId.of(3, 5, 14), null, null));
        assertEquals(LocalNetSpec.STR_DB_PQS, LocalNetPqs.specFor(
                VersionId.of(3, 5, 14), null, null).strPrefix());
        // The token rides where one is given, and null leaves NoAuth.
        assertEquals("tok", LocalNetPqs.specFor(
                VersionId.of(3, 5, 14), "tok", null).strToken());
    }


    @Test
    void nothingTakesANullBlock() {
        assertThrows(IllegalArgumentException.class, () -> LocalNetPqs.nPortLedger(null));
        assertThrows(IllegalArgumentException.class, () -> LocalNetPqs.nPortHealth(null));
        assertThrows(IllegalArgumentException.class, () -> LocalNetPqs.pgOf("127.0.0.1", null));
    }

}
