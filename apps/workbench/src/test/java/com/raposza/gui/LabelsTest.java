// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.model.PartyInfo;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The small things the window says, pinned so they stay said correctly.
 *
 * Headless: every method under test is static and touches no component.
 *
 * Author Claude/bentzn
 */
class LabelsTest {

    @Test
    void countsAgreeWithTheirNoun() {
        assertEquals("1 user", MainWindow.count(1, "user", "users"));
        assertEquals("0 users", MainWindow.count(0, "user", "users"));
        assertEquals("6 parties", MainWindow.count(6, "party", "parties"));
        assertEquals("1 party", MainWindow.count(1, "party", "parties"));
    }


    /**
     * A party row shows the HINT and drops the namespace fingerprint; the whole
     * id stays on the tooltip, which is what the filter matches against.
     *
     * NavigatorPanel gives back as much of the fingerprint as it takes when two
     * parties share a hint - mapLabelOf - so this is the out-of-context case,
     * where there is no list to collide with.
     */
    @Test
    void aPartyRowShowsTheHintAndKeepsTheWholeIdInTheTooltip() {
        String idParty = "party-3cd30f58-7cc1-46f2-b30c-14550a1b2c3d::1220578d91af6ad6";
        NavItem.Party item = new NavItem.Party(new PartyInfo(idParty, "Alice", true));

        assertTrue(item.label().startsWith("Alice   "));
        assertFalse(item.label().contains("::"));
        assertFalse(item.label().contains(idParty));
        assertEquals(idParty, item.tip());
    }


    @Test
    void aPartyRowWithNoDisplayNameIsJustTheHint() {
        NavItem.Party item = new NavItem.Party(new PartyInfo("alice::1", "", true));
        assertEquals("alice", item.label());
    }


    /**
     * Matched on equality with the participant id and on nothing else. The
     * party id format is not something this tool is entitled to assume.
     */
    /**
     * A fresh sandbox knows exactly one party and it is the admin one. Leaving
     * it unticked there produced "read as: 0 of 1" and a navigator that could
     * only report having no parties to read as - a tool that looks broken.
     */
    @Test
    void theAdminPreferenceYieldsRatherThanSelectNothing() {
        String idParticipant = "sandbox::1220fe8faf";
        PartyInfo partyAdmin = new PartyInfo(idParticipant, "", true);
        PartyInfo alice = new PartyInfo("alice::1", "Alice", true);

        assertEquals(List.of(Integer.valueOf(0)),
                PartyPicker.defaultSelection(List.of(partyAdmin), idParticipant));

        assertEquals(List.of(Integer.valueOf(1)),
                PartyPicker.defaultSelection(List.of(partyAdmin, alice), idParticipant));

        assertEquals(List.of(Integer.valueOf(0), Integer.valueOf(1)),
                PartyPicker.defaultSelection(List.of(partyAdmin, alice), null));

        assertEquals(List.of(), PartyPicker.defaultSelection(List.of(), idParticipant));
    }


    @Test
    void theParticipantsOwnPartyIsRecognisedByIdNotByShape() {
        String idParticipant = "sandbox::1220578d91af6ad6e334c5f9cb6bc4f865d34f421435";

        assertTrue(PartyPicker.isAdminParty(new PartyInfo(idParticipant, "", true),
                idParticipant));
        assertFalse(PartyPicker.isAdminParty(new PartyInfo("alice::1220578d91af", "Alice", true),
                idParticipant));
        assertFalse(PartyPicker.isAdminParty(new PartyInfo(idParticipant, "", true), null));
        assertFalse(PartyPicker.isAdminParty(new PartyInfo(idParticipant, "", true), "  "));
    }

}
