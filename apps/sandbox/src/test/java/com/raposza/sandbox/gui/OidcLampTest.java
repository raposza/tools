// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.raposza.sandbox.app.SandboxService;

import org.junit.jupiter.api.Test;

/**
 * The OIDC lamp is green only once the server holds every user - his
 * instruction, 2026-10-04.
 *
 * THE CONTROL CAN FAIL: a running built-in server whose users are not all
 * read is the case that showed green before, and it must now be amber.
 *
 * Author Claude/bentzn
 */
class OidcLampTest {

    @Test
    void aRunningServerIsGreenOnlyOnceEveryUserIsRead() {
        assertEquals(SandboxService.Health.STARTING, LampBar.healthOidc(true, false, false));
        assertEquals(SandboxService.Health.UP, LampBar.healthOidc(true, false, true));
    }


    @Test
    void aServerThatIsNotUpIsOffWhenBuiltInAndDownWhenExternal() {
        assertEquals(SandboxService.Health.OFF, LampBar.healthOidc(false, false, true));
        assertEquals(SandboxService.Health.DOWN, LampBar.healthOidc(false, true, true));
        assertEquals(SandboxService.Health.UP, LampBar.healthOidc(true, true, false));
    }

}
