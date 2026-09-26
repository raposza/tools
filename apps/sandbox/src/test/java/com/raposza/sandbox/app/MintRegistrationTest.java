// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.runtime.localnet.LocalNetAuth;
import com.raposza.runtime.localnet.LocalNetPorts;
import com.raposza.runtime.localnet.LocalNetUi;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * What the window registers at the provider, read off the same pages the stack
 * serves. No provider is started.
 *
 * Author Claude/bentzn
 */
class MintRegistrationTest {

    private static List<LocalNetUi.Page> lstPageDefault() {
        LocalNetPorts ports = LocalNetPorts.ofDefaults();
        return LocalNetUi.lstPage(LocalNetUi.lstSite("127.0.0.1", ports, ports.mapPortUi(),
                Map.of(), Map.of(), LocalNetAuth.ofUnsafe()));
    }


    /** SIX ORIGINS: wallet and ans on the two validators, sv and wallet on sv. */
    @Test
    void theOriginsAreEveryPageWithALoginOnTheUiBlock() {
        List<String> lstRedirect = MintRegistration.lstRedirect(lstPageDefault());

        assertEquals(6, lstRedirect.size(), String.valueOf(lstRedirect));
        for (String strUri : lstRedirect) {
            assertTrue(strUri.matches("http://[a-z]+\\.localhost:310[0-2]0"), strUri);
        }
        assertTrue(lstRedirect.contains("http://wallet.localhost:31000"));
        assertTrue(lstRedirect.contains("http://sv.localhost:31000"));
        assertTrue(lstRedirect.contains("http://ans.localhost:31010"));
        assertTrue(lstRedirect.contains("http://wallet.localhost:31020"));
        // SCAN HAS NO LOGIN BOX and nobody is sent back to it.
        assertTrue(lstRedirect.stream().noneMatch(strUri -> strUri.contains("scan.")));
    }


    @Test
    void theUsersAreTheRoleLoginsOnce() {
        assertEquals(List.of("sv", "app-provider", "app-user"),
                MintRegistration.lstUser(lstPageDefault()));
    }


    @Test
    void theBodiesCarryThePasswordAndAPublicClient() {
        assertEquals("{\"name\":\"sv\",\"password\":\"123456\"}",
                MintRegistration.strBodyUser("sv"));
        assertEquals("{\"client_id\":\"localnet-ui\",\"secret\":\"\","
                + "\"redirect_uris\":\"http://a:1 http://b:2\"}",
                MintRegistration.strBodyClient(List.of("http://a:1", "http://b:2")));
        assertEquals("a\\\"b\\\\c\\u000a", MintRegistration.esc("a\"b\\c\n"));
    }


    @Test
    void theCredentialIsTheAdminPair() {
        String strAuth = MintRegistration.strAuthBasic();

        assertTrue(strAuth.startsWith("Basic "));
        assertEquals("admin:123456", new String(Base64.getDecoder().decode(
                strAuth.substring(6)), StandardCharsets.UTF_8));
    }
}
