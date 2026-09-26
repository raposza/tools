// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.model.UserInfo;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * What an authenticated participant offers the picker.
 *
 * Every entry is a user the participant named. The `*` breadth entry that
 * used to lead this list was dropped on 2026-09-09.
 *
 * Author Claude/bentzn
 */
class MintTokensTest {

    private static final String STR_URL =
            "http://localhost:33301/mint.txt?sub=participant_admin&ttlSeconds=3600";

    private static final List<UserInfo> LST_USER = List.of(
            new UserInfo("qualitycontrol", "QualityControl-1", false,
                    List.of("QualityControl-1"), List.of()),
            new UserInfo("inspector", null, false, List.of(),
                    List.of("QualityControl-1", "PlantOperator-1")),
            new UserInfo("participant_admin", null, true, List.of(), List.of()));


    @Test
    void theUsersAreOfferedSorted() {
        assertEquals(List.of("inspector", "participant_admin", "qualitycontrol"),
                new MintTokens(STR_URL, LST_USER).lstUser());
    }


    @Test
    void aParticipantWithNoUsersOffersNone() {
        assertEquals(List.of(), new MintTokens(STR_URL, List.of()).lstUser());
    }


    @Test
    void aNamedUserStillReplacesTheSub() {
        String strUrl = new MintTokens(STR_URL, LST_USER).strUrlFor("alice");

        assertTrue(strUrl.contains("sub=alice"), strUrl);
        assertTrue(strUrl.contains("ttlSeconds=3600"), strUrl);
    }

}
