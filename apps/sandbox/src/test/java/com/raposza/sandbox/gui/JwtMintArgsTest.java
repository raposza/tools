// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.sandbox.app.JwtMintProcess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.runtime.settings.RaposzaSettings;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * What the mint's child JVM is told.
 *
 * THE ISSUER IS THE ONE WORTH ASSERTING. The mint resolves it from the
 * machine's routable address when it is not told one, and the Sandbox publishes
 * a loopback url - so a token said `iss: http://<LAN address>:33301` while the
 * discovery document beside it said `127.0.0.1`. Nothing rejected it, because
 * Canton with JWKS does not check `iss`; an OIDC-aware client does, and that is
 * exactly the client the discovery block exists for.
 *
 * No process is started. The list is built by a static method for this reason:
 * an argument list assembled inside `start` can only be checked by launching a
 * JVM, which is not a check anybody runs.
 *
 * Author Claude/bentzn
 */
class JwtMintArgsTest {

    /** One string, in the token, in the discovery document, and here. */
    @Test
    void the_issuer_is_the_url_the_window_publishes() {
        List<String> lstArg = JwtMintProcess.lstArgSpring();

        assertTrue(lstArg.contains("--" + JwtMintProcess.STR_PROP_ISSUER + "="
                + JwtMintProcess.strUrlBase()), String.valueOf(lstArg));
    }


    /** The port the child binds is the port the window says it is on. */
    @Test
    void the_port_is_the_setting() {
        List<String> lstArg = JwtMintProcess.lstArgSpring();

        assertTrue(lstArg.contains("--server.port="
                + RaposzaSettings.current().nPortMint()), String.valueOf(lstArg));
    }


    /** A moved key directory has to reach the child, which reads no file. */
    @Test
    void the_key_directory_is_passed_through() {
        List<String> lstArg = JwtMintProcess.lstArgSpring();

        assertTrue(lstArg.contains("--" + JwtMintProcess.STR_PROP_DIR_KEYS + "="
                + RaposzaSettings.current().dirMintKeys()), String.valueOf(lstArg));
    }


    /** THE SANDBOX IS FOR TEST: the admin password is the one every user has. */
    @Test
    void the_admin_password_is_the_sandbox_password() {
        List<String> lstArg = JwtMintProcess.lstArgSpring();

        assertEquals("123456", JwtMintProcess.STR_PASSWORD);
        assertTrue(lstArg.contains("--raposza.oidc.admin.password=123456"),
                String.valueOf(lstArg));
    }


    /** Loopback: `/mint` answers anyone who reaches the port. */
    @Test
    void the_child_binds_loopback_only() {
        List<String> lstArg = JwtMintProcess.lstArgSpring();

        assertTrue(lstArg.contains("--server.address=127.0.0.1"), String.valueOf(lstArg));
    }


    /** Five, and a sixth added without a test here is a sixth untested. */
    @Test
    void there_are_five_of_them() {
        assertEquals(5, JwtMintProcess.lstArgSpring().size());
    }
}
