// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.model.UserInfo;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * What an unauthenticated participant offers the picker.
 *
 * Author Claude/bentzn
 */
class OpenUsersTest {

    private static final List<UserInfo> LST_USER = List.of(
            new UserInfo("qualitycontrol", "QualityControl-1", false, List.of(), List.of()),
            new UserInfo("inspector", null, false, List.of(), List.of()));


    @Test
    void theUsersAreOfferedSorted() {
        assertEquals(List.of("inspector", "qualitycontrol"),
                new OpenUsers(LST_USER).lstUser());
    }


    @Test
    void theChoiceIsOfferedEvenWithoutACredential() {
        assertTrue(new OpenUsers(LST_USER).canChoose());
    }


    @Test
    void noUserGetsAToken() {
        assertNull(new OpenUsers(LST_USER).sourceFor("inspector"));
    }


    /**
     * NOTHING IS INVENTED. The `*` entry that used to stand in for an empty
     * list is gone; the window reads such a participant as every party it
     * hosts instead.
     */
    @Test
    void aParticipantWithNoUsersOffersNone() {
        assertEquals(List.of(), new OpenUsers(List.of()).lstUser());
    }

}
