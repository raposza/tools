// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The token is the point of this file, and its absence is the failure it
 * exists to prevent.
 *
 * A remote console configured with endpoints and no token connects perfectly
 * well. Its ADMIN API calls succeed, because `auth-services` does not guard
 * that API, and only its Ledger API calls are refused. So an overlay that
 * silently drops the token produces a run that looks authenticated right up
 * until the first party allocation.
 *
 * Author Claude/bentzn
 */
class RemoteConsoleOverlayTest {

    private static final SandboxPorts PORTS =
            new SandboxPorts(7865, 7866, 7864, 7867, 7868, 7869);

    private static final String STR_TOKEN = "raposza-admin-token";


    @Test
    void theTokenIsRenderedWhenThereIsOne() {
        String strConf = RemoteConsoleOverlay.of(PORTS, STR_TOKEN).render();

        assertTrue(strConf.contains("token = \"" + STR_TOKEN + "\""));
        assertTrue(RemoteConsoleOverlay.of(PORTS, STR_TOKEN).flagAuthenticated());
    }


    /** No token at all rather than an empty one, which would parse and fail later. */
    @Test
    void theKeyIsAbsentWhenThereIsNoToken() {
        assertFalse(RemoteConsoleOverlay.of(PORTS, null).render().contains("token"));
        assertFalse(RemoteConsoleOverlay.of(PORTS, "  ").render().contains("token"));
        assertFalse(RemoteConsoleOverlay.of(PORTS, null).flagAuthenticated());
    }


    /**
     * Both endpoints, and the Ledger API is not the admin API. Swapping them
     * gives a console that answers admin calls and refuses everything else,
     * which reads like an auth fault.
     */
    @Test
    void bothEndpointsAreRenderedAndNotSwapped() {
        String strConf = RemoteConsoleOverlay.of(PORTS, null).render();

        assertTrue(strConf.contains("ledger-api { address = \"localhost\", port = "
                + PORTS.nPortLedgerApi() + " }"));
        assertTrue(strConf.contains("admin-api { address = \"localhost\", port = "
                + PORTS.nPortAdminApi() + " }"));
    }


    /**
     * The console binds the participant under this name and the bootstrap
     * script refers to it by that name, so the two must agree.
     */
    @Test
    void theNodeNameMatchesWhatTheScriptCallsIt() {
        assertTrue(RemoteConsoleOverlay.of(PORTS, null).render()
                .contains("canton.remote-participants." + RemoteConsoleOverlay.STR_NODE_DEFAULT));
        assertEquals(RemoteConsoleOverlay.STR_NODE_DEFAULT,
                Canton3xBootstrap.STR_NODE_PARTICIPANT);
    }


    /**
     * And it must NOT be the participant's own name. `sandbox-console` builds
     * a `remote-participants.sandbox` entry from its port flag DEFAULTS, and
     * that entry shadows a configured one of the same name - so a stack on
     * offset ports gets a console pointed at 6866.
     */
    @Test
    void theNodeNameIsNotTheOneTheConsoleGenerates() {
        assertNotEquals(StorageOverlay.STR_NODE_PARTICIPANT,
                RemoteConsoleOverlay.STR_NODE_DEFAULT);
        assertFalse(RemoteConsoleOverlay.of(PORTS, null).render()
                .contains("remote-participants." + StorageOverlay.STR_NODE_PARTICIPANT + " "));
    }


    @Test
    void describeCarriesNoToken() {
        assertFalse(RemoteConsoleOverlay.of(PORTS, STR_TOKEN).describe().contains(STR_TOKEN));
        assertFalse(RemoteConsoleOverlay.of(PORTS, STR_TOKEN).toString().contains(STR_TOKEN));
        assertTrue(RemoteConsoleOverlay.of(PORTS, STR_TOKEN).describe().contains("token present"));
    }


    @Test
    void theArgumentsAreRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> new RemoteConsoleOverlay(" ", "localhost", 1, 2, null));
        assertThrows(IllegalArgumentException.class,
                () -> new RemoteConsoleOverlay("sandbox", " ", 1, 2, null));
        assertThrows(IllegalArgumentException.class,
                () -> new RemoteConsoleOverlay("sandbox", "localhost", 0, 2, null));
        assertThrows(IllegalArgumentException.class, () -> RemoteConsoleOverlay.of(null, null));
    }
}
