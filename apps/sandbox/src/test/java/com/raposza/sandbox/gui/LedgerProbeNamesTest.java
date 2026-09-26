// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * The two splits an unattended harness's ledger assertion rests on.
 *
 * Neither needs a database, and both are the place the assertion goes wrong
 * silently: a substring match would pass on the wrong template, and a party id
 * compared whole would never match at all because the fingerprint half is
 * different on every start.
 *
 * Author Claude/bentzn
 */
class LedgerProbeNamesTest {

    @Test
    void aTemplateIsItsLastSegment() {
        assertEquals("Pet", LedgerProbe.strSimpleName("1220abc:Main:Pet"));
        assertEquals("AdoptionRequest", LedgerProbe.strSimpleName("1220abc:Main:AdoptionRequest"));
        assertEquals("Checkup", LedgerProbe.strSimpleName("Checkup"));
        assertEquals("", LedgerProbe.strSimpleName(null));
    }


    /**
     * THE REASON THIS IS A SPLIT AND NOT A `contains`. `Pet` is a substring of
     * `PetShopAccount`, so a contains-based assertion would pass on a ledger
     * carrying a template the fixture never defined.
     */
    @Test
    void aTemplateThatMerelyStartsTheSameIsNotAMatch() {
        assertNotEquals("Pet", LedgerProbe.strSimpleName("1220abc:Main:PetShopAccount"));
    }


    @Test
    void aPartyIsTheHintBeforeTheFingerprint() {
        assertEquals("PetShop", LedgerProbe.strPartyHint("PetShop::1220f5d830e74ecf484e"));
        assertEquals("Alice", LedgerProbe.strPartyHint("Alice::1220aaaa"));
        assertEquals("Vet", LedgerProbe.strPartyHint("Vet"));
        assertEquals("", LedgerProbe.strPartyHint(null));
    }
}
