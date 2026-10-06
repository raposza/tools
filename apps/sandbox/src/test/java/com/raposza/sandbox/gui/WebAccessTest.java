// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.raposza.sandbox.app.RawarServer;

import org.junit.jupiter.api.Test;

/**
 * A request to a served page becomes one short line: where, what was asked,
 * what answered it and whether it failed - no instant and no time taken.
 *
 * THE CONTROL CAN FAIL: the `none` line below has its status where a kind
 * with a space in it would shift a field-counting parser, and both sources
 * carry a time that must not come through.
 *
 * Author Claude/bentzn
 */
class WebAccessTest {

    @Test
    void aLocalNetLineLosesItsInstantAndItsTime() {
        assertEquals("wallet.localhost  GET /api/validator/v0/wallet/balance  proxy validator  ok",
                WebAccess.strLocalNet("2026-10-04T07:07:52.123456Z  GET  wallet.localhost"
                        + "  /api/validator/v0/wallet/balance  proxy validator  200  15ms"));
        assertEquals("sv.localhost  GET /nope  none  FAILED 404",
                WebAccess.strLocalNet("2026-10-04T07:07:52Z  GET  sv.localhost  /nope  none  404"
                        + "  0ms"));
        assertEquals("wallet.localhost  OPTIONS /api/x  cors  ok",
                WebAccess.strLocalNet("2026-10-04T07:07:52Z  OPTIONS  wallet.localhost  /api/x"
                        + "  cors  204  1ms"));
    }


    @Test
    void aLineThatIsNotARequestIsDropped() {
        assertNull(WebAccess.strLocalNet("--- web ui: no request log at /x: denied"));
        assertNull(WebAccess.strLocalNet(""));
        assertNull(WebAccess.strLocalNet("2026-10-04T07:07:52Z  GET  a  /b  none  abc  1ms"));
    }


    @Test
    void aRawarRequestIsNamedForTheRawarServer() {
        assertEquals("rawar  GET /usdcx/_ledger/v2/version  ledger  FAILED 503",
                WebAccess.strRawar(new RawarServer.Access("GET", "/usdcx/_ledger/v2/version",
                        "ledger", 503)));
        assertEquals("rawar  GET /usdcx/  file  ok",
                WebAccess.strRawar(new RawarServer.Access("GET", "/usdcx/", "file", 200)));
    }

}
