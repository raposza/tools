// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.raposza.runtime.localnet.LocalNetUi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * `todo.md` A-40: the rows of the Web UIs tab, with no window.
 *
 * THE PASSWORD IS SHOWN ONLY WHERE THE WINDOW SET ONE. The control can fail: a
 * pane that printed `123456` whatever the provider would pass the first case
 * and fail the second.
 *
 * Author Claude/bentzn
 */
class WebUiPaneTest {

    private static final List<LocalNetUi.Page> LST_PAGE = List.of(
            new LocalNetUi.Page("http://wallet.localhost:31010", "Wallet (app-provider)",
                    "app-provider"),
            new LocalNetUi.Page("http://scan.localhost:31000", "Scan", null));


    @Test
    void theWindowsOwnProviderShowsItsPassword() {
        List<String[]> lstRow = WebUiPane.lstRow(LST_PAGE, mapJson(), "123456");

        assertEquals(3, lstRow.size());
        assertArrayEquals(new String[] { "Wallet (app-provider)",
                "http://wallet.localhost:31010", "app-provider", "123456" }, lstRow.get(0));
        // A PAGE WITH NO LOGIN BOX HAS NO USER AND NO PASSWORD.
        assertArrayEquals(new String[] { "Scan", "http://scan.localhost:31000",
                WebUiPane.STR_NO_LOGIN, WebUiPane.STR_NO_LOGIN }, lstRow.get(1));
        assertEquals("http://127.0.0.1:30022", lstRow.get(2)[1]);
    }


    @Test
    void aProviderTheWindowDidNotWriteShowsNoPassword() {
        List<String[]> lstRow = WebUiPane.lstRow(LST_PAGE, mapJson(), null);
        assertEquals(WebUiPane.STR_NO_PASSWORD, lstRow.get(0)[3]);
    }


    private static Map<String, String> mapJson() {
        Map<String, String> mapOut = new LinkedHashMap<>();
        mapOut.put("app-provider", "http://127.0.0.1:30022");
        return mapOut;
    }
}
