// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The catalogue parser is the first thing a mistyped line hits, and the
 * READ_ONLY default is a safety control rather than a convenience, so both are
 * pinned here.
 *
 * Author Claude/bentzn
 */
class HostProfileStoreTest {

    @Test
    void legacySixFieldLineParsesAndIsReadOnly() {
        HostProfile profile = HostProfileStore.parse(
                "Backend GGH DEV  https  canton-dev  5011  7575  daml_ledger_api  -", 1);

        assertEquals("Backend GGH DEV", profile.nameDisplay());
        assertEquals("https", profile.strProtocol());
        assertEquals("canton-dev", profile.nameHost());
        assertEquals(5011, profile.portLedger());
        assertEquals(7575, profile.portJson());
        assertEquals("daml_ledger_api", profile.strScope());
        assertNull(profile.strAudience());

        assertEquals(AccessMode.READ_ONLY, profile.mode());
        assertFalse(profile.canSubmit());
    }


    @Test
    void eightFieldLineCarriesModeAndColour() {
        HostProfile profile = HostProfileStore.parse(
                "Local Sandbox  http  localhost  5011  7575  -  -  rw  #2e7d32", 1);

        assertEquals(AccessMode.READ_WRITE, profile.mode());
        assertTrue(profile.canSubmit());
        assertEquals("#2e7d32", profile.strColour());
        assertFalse(profile.isTls());
    }


    @Test
    void absentPortsBecomeMinusOne() {
        HostProfile profile = HostProfileStore.parse(
                "No JSON  https  host  5011  -  -  -", 1);

        assertTrue(profile.hasLedgerPort());
        assertFalse(profile.hasJsonPort());
        assertEquals(-1, profile.portJson());
        assertTrue(profile.isTls());
    }


    @Test
    void nameContainingProtocolWordStillParses() {
        HostProfile profile = HostProfileStore.parse(
                "the http gateway  https  host  5011  7575  -  -  ro  -", 1);

        assertEquals("the http gateway", profile.nameDisplay());
        assertEquals("host", profile.nameHost());
    }


    @Test
    void colourDefaultsFromMode() {
        HostProfile ro = HostProfileStore.parse("A  http  h  1  2  -  -  ro  -", 1);
        HostProfile rw = HostProfileStore.parse("B  http  h  1  2  -  -  rw  -", 1);

        assertEquals(HostProfileStore.defaultColour(AccessMode.READ_ONLY), ro.strColour());
        assertEquals(HostProfileStore.defaultColour(AccessMode.READ_WRITE), rw.strColour());
    }


    @Test
    void wrongFieldCountIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HostProfileStore.parse("Name  https  host  5011", 7));
    }


    /**
     * The failure this catches was seen in the window, not in a test: a line
     * written without its "-" placeholders parsed, swallowed "rw" as the scope
     * and connected READ-ONLY to a participant the operator had declared
     * writable.
     */
    @Test
    void eightFieldLineMissingPlaceholdersIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> HostProfileStore.parse("Local Sandbox  http  localhost  6865  7575"
                        + "  rw  #2e7d32", 4));

        assertTrue(ex.getMessage().contains("line 4"));
        assertTrue(ex.getMessage().contains("rw #2e7d32"));
    }


    @Test
    void colourInTheAudienceSlotIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HostProfileStore.parse("N  http  h  1  2  -  #c62828", 5));
    }


    @Test
    void badModeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HostProfileStore.parse("N  http  h  1  2  -  -  yes  -", 3));
    }

}
