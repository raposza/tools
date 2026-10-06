// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Author Claude/bentzn
 */
class LocalNetUiTest {

    private static final Map<String, Integer> MAP_PORT = Map.of("sv", 4000,
            "app-provider", 3000, "app-user", 2000);

    private static final Map<String, String> MAP_AUDIENCE = Map.of("sv", "https://sv.test",
            "app-provider", "https://provider.test", "app-user", "https://user.test");

    private static final Map<String, String> MAP_LOGIN = Map.of("sv", "sv",
            "app-provider", "app-provider", "app-user", "app-user");

    @Test
    void everyVhostOnAPortIsDistinct() {
        for (LocalNetUi.Site site : LocalNetUi.lstSite("127.0.0.1", MAP_PORT, MAP_AUDIENCE, MAP_LOGIN)) {
            Set<String> setHost = new HashSet<>();
            for (LocalNetUi.Vhost vhost : site.lstVhost()) {
                assertTrue(setHost.add(vhost.strHost()),
                        site.strRole() + " declares " + vhost.strHost() + " twice");
            }
        }
    }


    /**
     * The gRPC vhost cannot be served by a JDK HTTP server, so it must not be
     * in the table at all - a route that 502s on every call would be worse than
     * its absence.
     */
    @Test
    void theGrpcVhostIsAbsent() {
        for (LocalNetUi.Site site : LocalNetUi.lstSite("127.0.0.1", MAP_PORT, MAP_AUDIENCE, MAP_LOGIN)) {
            for (LocalNetUi.Vhost vhost : site.lstVhost()) {
                assertFalse(vhost.strHost().startsWith("grpc-"));
            }
        }
    }


    /**
     * Every page the browser can open has to carry a config, and every API url
     * in it has to be same-origin - that is the whole reason there is a proxy.
     */
    @Test
    void everyBundleIsConfiguredSameOrigin() {
        List<LocalNetUi.Site> lstSite = LocalNetUi.lstSite("127.0.0.1", MAP_PORT, MAP_AUDIENCE, MAP_LOGIN);
        int cntBundle = 0;
        for (LocalNetUi.Site site : lstSite) {
            for (LocalNetUi.Vhost vhost : site.lstVhost()) {
                if (vhost.strApp() == null)
                    continue;
                cntBundle++;
                assertNotNull(vhost.strConfigJs(), vhost.strHost() + " has no config");
                String strOrigin = "http://" + vhost.strHost() + ":" + site.nPort();
                assertTrue(vhost.strConfigJs().contains(strOrigin),
                        vhost.strHost() + " carries no url of its own");
                for (LocalNetUi.Route route : vhost.lstRoute()) {
                    if (!vhost.strConfigJs().contains(route.strPrefix()))
                        continue;
                    assertTrue(vhost.strConfigJs().contains(strOrigin + route.strPrefix()),
                            vhost.strHost() + " points " + route.strPrefix() + " elsewhere");
                }
            }
        }
        // SEVEN PAGES AND FIVE UPSTREAM NAMES FOR THEM: three wallets, two
        // name services.
        assertEquals(12, cntBundle);

        // Scan is the one page with no login, and every other page reports the
        // role's own wallet user - "sv sv" told nobody anything.
        List<LocalNetUi.Page> lstPage = LocalNetUi.lstPage(lstSite);
        assertEquals(7, lstPage.size());
        int cntNoLogin = 0;
        for (LocalNetUi.Page page : lstPage) {
            assertFalse(page.strTitle().isBlank());
            if (page.strLogin() == null)
                cntNoLogin++;
        }
        assertEquals(1, cntNoLogin);
    }


    /**
     * The page list names the node in every per-node page, and the upstream
     * name still answers, configured for its own origin.
     *
     * THE CONTROL CAN FAIL: the upstream wallet is on all three ports, so a
     * page list that kept the aliases would show it three times, and an alias
     * that shared the role-named config would point its browser at another
     * origin.
     */
    @Test
    void thePagesAreNamedForTheirNodeAndTheUpstreamNamesStillAnswer() {
        List<LocalNetUi.Site> lstSite = LocalNetUi.lstSite("127.0.0.1", MAP_PORT, MAP_AUDIENCE,
                MAP_LOGIN);
        List<String> lstUrl = LocalNetUi.lstPage(lstSite).stream().map(LocalNetUi.Page::strUrl)
                .toList();
        assertEquals(List.of("http://sv.localhost:4000", "http://scan.localhost:4000",
                "http://sv.wallet.localhost:4000", "http://app-provider.wallet.localhost:3000",
                "http://app-provider.ans.localhost:3000", "http://app-user.wallet.localhost:2000",
                "http://app-user.ans.localhost:2000"), lstUrl);

        List<String> lstAlias = LocalNetUi.lstPageAlias(lstSite).stream()
                .map(LocalNetUi.Page::strUrl).toList();
        assertEquals(List.of("http://wallet.localhost:4000", "http://wallet.localhost:3000",
                "http://ans.localhost:3000", "http://wallet.localhost:2000",
                "http://ans.localhost:2000"), lstAlias);

        for (LocalNetUi.Site site : lstSite) {
            for (LocalNetUi.Vhost vhost : site.lstVhost()) {
                if (!vhost.flagAlias())
                    continue;
                assertFalse(vhost.strConfigJs().contains("." + vhost.strHost()),
                        vhost.strHost() + " on " + site.nPort() + " points at a role-named origin");
            }
            // THE UNMATCHED HOST still reaches the role's own page.
            if (!"sv".equals(site.strRole()))
                assertEquals(site.strRole() + ".wallet.localhost",
                        site.lstVhost().get(0).strHost());
        }
        assertTrue(LocalNetUi.lstHostName().contains("app-user.ans.localhost"));
        assertTrue(LocalNetUi.lstHostName().contains("wallet.localhost"));
    }


    @Test
    void theAudienceReachesTheBrowserConfig() {
        for (LocalNetUi.Site site : LocalNetUi.lstSite("127.0.0.1", MAP_PORT, MAP_AUDIENCE, MAP_LOGIN)) {
            for (LocalNetUi.Vhost vhost : site.lstVhost()) {
                if (vhost.strConfigJs() == null || !vhost.strConfigJs().contains("auth:"))
                    continue;
                assertTrue(vhost.strConfigJs().contains(MAP_AUDIENCE.get(site.strRole())),
                        site.strRole() + "/" + vhost.strApp() + " carries the wrong audience");
                assertTrue(vhost.strConfigJs().contains(LocalNetToken.STR_SECRET));
            }
        }
    }


    @Test
    void theBackendsAreDialledOnTheGivenHost() {
        for (LocalNetUi.Site site : LocalNetUi.lstSite("10.1.2.3", MAP_PORT, MAP_AUDIENCE, MAP_LOGIN)) {
            for (LocalNetUi.Vhost vhost : site.lstVhost()) {
                for (LocalNetUi.Route route : vhost.lstRoute()) {
                    assertTrue(route.strUpstream().startsWith("http://10.1.2.3:"),
                            route.strUpstream() + " ignores the host");
                }
            }
        }
    }
}
