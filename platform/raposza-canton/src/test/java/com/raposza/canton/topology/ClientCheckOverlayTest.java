// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import com.raposza.canton.install.HostPlatform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Author Claude/bentzn
 */
class ClientCheckOverlayTest {

    @Test
    void itIsForWindowsAndNowhereElse() {
        assertTrue(ClientCheckOverlay.flagNeeded(HostPlatform.WINDOWS_X64));
        assertFalse(ClientCheckOverlay.flagNeeded(HostPlatform.LINUX_X64));
        assertThrows(IllegalArgumentException.class, () -> ClientCheckOverlay.flagNeeded(null));
    }


    @Test
    void bothDatasourcesAreNamedAndTheyAreDifferentObjects() {
        String strConf = ClientCheckOverlay.render("sandbox");

        assertTrue(strConf.contains("canton.participants.sandbox.ledger-api."
                + "postgres-data-source.client-connection-check-interval = 0s"));
        assertTrue(strConf.contains("canton.participants.sandbox.parameters."
                + "ledger-api-server.indexer.postgres-data-source."
                + "client-connection-check-interval = 0s"));
    }


    @Test
    void exactlyTwoKeysAreWritten() {
        // Silencing either alone leaves the other refusing, and a third key
        // would be one nothing has measured.
        assertEquals(2, ClientCheckOverlay.render("sandbox").lines()
                .filter(str -> str.contains(ClientCheckOverlay.STR_KEY_INTERVAL))
                .count());
    }


    @Test
    void theMigrationLockIsNOTAddressedHere() {
        // That one reaches no configuration path at all; it is answered in the
        // DataSource. A key written for it here would read as a fix.
        assertFalse(ClientCheckOverlay.render("sandbox").contains("replication"));
        assertFalse(ClientCheckOverlay.render("sandbox").contains("high-availability"));
    }


    @Test
    void theParticipantIsTheOneNamed() {
        assertTrue(ClientCheckOverlay.render("other").contains("canton.participants.other."));
        assertThrows(IllegalArgumentException.class, () -> ClientCheckOverlay.render(" "));
        assertThrows(IllegalArgumentException.class, () -> ClientCheckOverlay.render(null));
    }


    @Test
    void zeroIsWrittenAsADuration() {
        // Canton reads it as a NonNegativeFiniteDuration and refuses a bare 0.
        assertEquals("0s", ClientCheckOverlay.STR_VALUE_OFF);
    }
}
