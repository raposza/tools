// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.runtime.localnet.LocalNetUi;
import com.raposza.sandbox.app.RawarSites;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The rows of the Web tab and of OIDC's Users tab, with no window: name, node
 * and URL on one; username, node and password on the other - his instruction,
 * 2026-10-02.
 *
 * THE PASSWORD IS SHOWN ONLY FOR A NAME A LOGIN BOX WANTS. The control can
 * fail: a Users tab that listed Scan, which has no login, would have three
 * rows where this asserts two.
 *
 * Author Claude/bentzn
 */
class WebPaneTest {

    private static final List<LocalNetUi.Page> LST_PAGE = List.of(
            new LocalNetUi.Page("http://wallet.localhost:31010", "Amulet Wallet - app-provider",
                    "app-provider", "app-provider"),
            new LocalNetUi.Page("http://ans.localhost:31010", "Amulet Name Service - app-provider",
                    "app-provider", "app-provider"),
            new LocalNetUi.Page("http://scan.localhost:31000", "Amulet Scan", null, "sv"),
            new LocalNetUi.Page("http://sv.localhost:31000", "Super Validator Operations", "sv",
                    "sv"));


    @Test
    void theWebTabNamesEachPageItsNodeAndItsUrlThenTheJsonLedgerApis() {
        Map<String, String> mapJson = new LinkedHashMap<>();
        mapJson.put("app-provider", "http://127.0.0.1:30022");
        List<WebPane.Row> lstRow = WebPane.lstRowLocalNet(LST_PAGE, mapJson);

        assertEquals(5, lstRow.size());
        assertEquals(new WebPane.Row("Amulet Wallet - app-provider", "app-provider",
                "http://wallet.localhost:31010"), lstRow.get(0));
        assertEquals(new WebPane.Row("Amulet Scan", "sv", "http://scan.localhost:31000"),
                lstRow.get(2));
        assertEquals(new WebPane.Row(WebPane.STR_NAME_JSON, "app-provider",
                "http://127.0.0.1:30022"), lstRow.get(4));
    }


    @Test
    void aRawarRowIsItsNameTheNodeItsLedgerIsAndItsMountUrl() {
        RawarSites.Scan scan = new RawarSites.Scan(List.of(
                new RawarSites.Site("usdcx", "/usdcx/", Path.of("/r/usdcx"), null),
                new RawarSites.Site("hello", "/", Path.of("/r/hello"), null)), List.of());
        List<WebPane.Row> lstRow = WebPane.lstRowRawar(scan, 31100, "sandbox");
        assertEquals(List.of(
                new WebPane.Row("RAWAR usdcx", "sandbox", "http://127.0.0.1:31100/usdcx/"),
                new WebPane.Row("RAWAR hello", "sandbox", "http://127.0.0.1:31100/")), lstRow);
    }


    /** A click copies a RAWAR's URL and nothing else's - the control is the JSON row. */
    @Test
    void onlyARawarRowIsCopiedOnAClick() {
        assertTrue(WebPane.isRawar("RAWAR hello"));
        assertFalse(WebPane.isRawar(WebPane.STR_NAME_JSON));
        assertFalse(WebPane.isRawar("Amulet Wallet - app-provider"));
        assertFalse(WebPane.isRawar(null));
    }


    @Test
    void theUsersTabHasOneRowPerLoginNameWithItsNodeAndPassword() {
        List<UsersPane.Row> lstRow = UsersPane.lstRowLocalNet(LST_PAGE, "123456");
        assertEquals(List.of(new UsersPane.Row("app-provider", "app-provider", "123456"),
                new UsersPane.Row("sv", "sv", "123456")), lstRow);

        assertEquals(List.of(new UsersPane.Row("participant_admin", "sandbox", "123456"),
                new UsersPane.Row("technician", "sandbox", "123456")),
                UsersPane.lstRowSandbox(List.of("participant_admin", "technician"), "sandbox",
                        "123456"));
    }
}
