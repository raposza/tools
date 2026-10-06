// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The OIDC tab lists every user the server holds, with the password out of the
 * server's own file - his instructions, 2026-10-04 - and the node the window
 * knows for the ones it registered.
 *
 * THE CONTROLS CAN FAIL: `treasury` was added at the server and not by the
 * window, so a tab built from the window's list alone - the first defect -
 * would not show it; its password is in the file only, so a tab that took
 * passwords from the window would not show `t0p`; and `app-user` was
 * registered by the window and is NOT at the server, so a merge that kept
 * every known row would list a user who cannot sign in.
 *
 * Author Claude/bentzn
 */
class UsersPaneTest {

    @Test
    void everyServerUserIsListedWithTheFilesPassword() {
        List<UsersPane.Row> lstKnown = List.of(
                new UsersPane.Row("app-provider", "app-provider", "123456"),
                new UsersPane.Row("app-user", "app-user", "123456"));
        List<UsersPane.Row> lstRow = UsersPane.lstRowProvider(List.of("app-provider", "treasury"),
                Map.of("app-provider", "123456", "treasury", "t0p"), lstKnown);
        assertEquals(List.of(
                new UsersPane.Row("app-provider", "app-provider", "123456"),
                new UsersPane.Row("treasury", UsersPane.STR_NODE_UNKNOWN, "t0p")),
                lstRow);
    }


    @Test
    void anUnreadFileSaysSo() {
        List<UsersPane.Row> lstRow = UsersPane.lstRowProvider(List.of("treasury"), Map.of(),
                List.of());
        assertEquals(UsersPane.STR_PASSWORD_UNREAD, lstRow.get(0).strPassword());
    }

}
