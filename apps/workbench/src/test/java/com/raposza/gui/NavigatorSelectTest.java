// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.model.Contract;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;
import com.raposza.api.model.PartyInfo;
import com.raposza.api.model.UserInfo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

/**
 * What a node stands for, asserted without a display.
 *
 * The rule under test is that a node is matched on its IDENTITY and never on
 * its label: contract ids and party ids are truncated in the tree, a template
 * label may carry a package prefix, and a party label is usually a display
 * name. Matching any of those would select the wrong node or none.
 *
 * Author Claude/bentzn
 */
class NavigatorSelectTest {

    private static final String ID_LONG =
            "0011223344556677889900112233445566778899aabbccddeeff00112233445566";

    private static final DataId ID_ACCOUNT = new DataId("pkg1", "Main", "Account");


    private static Contract contract() {
        DamlValue.Rec payload = new DamlValue.Rec(null,
                List.of(new DamlValue.Rec.Field("owner", new DamlValue.Text("v"))));
        return new Contract(ID_LONG, "#ev", ID_ACCOUNT, payload, List.of("bank"), List.of(),
                Optional.empty(), "0000001", Optional.empty());
    }


    @Test
    void aContractMatchesOnItsWholeIdAndNotOnTheTruncatedLabel() {
        NavItem item = new NavItem.Ct(contract());

        assertTrue(NavigatorPanel.standsFor(item, ID_LONG));
        assertFalse(NavigatorPanel.standsFor(item, ID_LONG.substring(0, 20)),
                "the label is truncated; matching it would select the wrong contract");
    }


    @Test
    void aPartyMatchesOnItsIdAndNotOnItsDisplayName() {
        NavItem item = new NavItem.Party(new PartyInfo("party-ff84::12", "BankBulk", true));

        assertTrue(NavigatorPanel.standsFor(item, "party-ff84::12"));
        assertFalse(NavigatorPanel.standsFor(item, "BankBulk"));
    }


    @Test
    void aTemplateMatchesQualifiedOrShort() {
        NavItem item = new NavItem.Template(new LedgerSnapshot.TemplateGroup(ID_ACCOUNT,
                "Main:Account", List.of(contract())));

        assertTrue(NavigatorPanel.standsFor(item, "pkg1:Main:Account"));
        assertTrue(NavigatorPanel.standsFor(item, "Main:Account"));
        assertFalse(NavigatorPanel.standsFor(item, "Main:Iou"));
    }


    @Test
    void aUserMatchesAndAHeadingNeverDoes() {
        assertTrue(NavigatorPanel.standsFor(
                new NavItem.User(new UserInfo("participant_admin", null, true, List.of(),
                        List.of())),
                "participant_admin"));

        assertFalse(NavigatorPanel.standsFor(new NavItem.Group("Contracts (61)", null),
                "Contracts (61)"));
    }

}
