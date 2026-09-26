// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.VersionId;
import com.raposza.canton.topology.StorageOverlay;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * `--discovery <port>` on the headless Sandbox.
 *
 * <h2>Why the headless stack needs one at all</h2>
 *
 * {@link DiscoveryServer} was the WINDOW'S endpoint and nothing else started
 * it, so a stack started with `--fixture` served no document and every client
 * that discovers rather than being told could not find it. The CaQL harness is
 * one: `SandboxSession` takes a discovery port and throws without one, and
 * `Discovery.fetch` is its only route to the ledger address, the users and the
 * token source. A matrix cell died on exactly that, 2026-09-15 - the fixture
 * landed, the harness had nothing to read, and the cell reported FAIL after
 * three minutes of retries.
 *
 * <h2>OFF UNLESS ASKED FOR</h2>
 *
 * A port bound by default is a second way to reach a stack that nobody asked
 * for. `--discovery` is stripped before {@link SandboxOptions} parses, the same
 * shape `--fixture` and `--cli` have: whether an endpoint is published is not a
 * property of the stack.
 *
 * <h2>What the document says here, and what it leaves out</h2>
 *
 * The state, the discovery port, the Canton version and edition, the work
 * directory and the ready report - which is what a consumer needs to CONNECT.
 * There is no window, so there is no mint and no auth overlay: both are null,
 * and a consumer reading `auth.mode` finds nothing and uses no token, which is
 * correct for a headless stack that checks nothing.
 *
 * Author Claude/bentzn
 */
public final class DiscoveryStart {

    /** The switch, with its port as the next argument. */
    public static final String STR_ARG = "--discovery";

    private DiscoveryStart() {
    }


    /**
     * @param arrArg the command line
     * @return the port asked for, or 0 when the switch is absent or its value
     *         is not a number
     */
    public static int nPortIn(String[] arrArg) {
        if (arrArg == null)
            return 0;

        for (int idx = 0; idx < arrArg.length - 1; idx++) {
            if (!STR_ARG.equals(arrArg[idx]))
                continue;
            try {
                return Integer.parseInt(arrArg[idx + 1].trim());
            }
            catch (NumberFormatException ex) {
                return 0;
            }
        }
        return 0;
    }


    /**
     * The line with the switch and its value taken out, for the ordinary
     * parser, which refuses what it does not recognise.
     *
     * @param arrArg the command line
     * @return what is left
     */
    public static String[] without(String[] arrArg) {
        if (arrArg == null)
            return new String[0];

        List<String> lstArg = new ArrayList<>();
        for (int idx = 0; idx < arrArg.length; idx++) {
            if (!STR_ARG.equals(arrArg[idx])) {
                lstArg.add(arrArg[idx]);
                continue;
            }
            if (idx + 1 < arrArg.length)
                idx++;
        }
        return lstArg.toArray(new String[0]);
    }


    /**
     * Binds the endpoint, and says so or says why not.
     *
     * A PORT THAT WILL NOT BIND DOES NOT STOP THE STACK. The ledger is up and
     * usable by anything that was told where it is; refusing to serve is worth
     * a line and not an exit.
     *
     * @param discovery the server to bind
     * @param service the stack the document describes
     * @param nPort the port asked for
     * @param auth what the participant checks, or null for none
     * @param version the Canton running
     */
    public static void serve(DiscoveryServer discovery, SandboxService service, int nPort,
            AuthSettings auth, VersionId version) {
        try {
            discovery.start(nPort, () -> docOf(service, nPort, auth, version));
        }
        catch (IOException ex) {
            System.out.println("the discovery endpoint could not bind " + nPort + ": "
                    + ex.getMessage());
            return;
        }
        System.out.println("discovery on " + DiscoveryServer.strUrlOf(nPort));
    }


    /**
     * A SNAPSHOT TAKEN PER REQUEST, which is why this is a supplier and not a
     * document built once: the state moves, and a document built at bind time
     * would report STARTING for the life of the process.
     *
     * THE REPORT IS GATED ON RUNNING, as it is in the window: the service keeps
     * its last report after a stop, so a document that took it whenever it
     * existed would publish the ports of a stack that is gone.
     *
     * @param service the stack
     * @param nPort the port this document is served from
     * @param auth what the participant checks, or null for none
     * @param version the Canton running
     * @return never null
     */
    static DiscoveryDoc docOf(SandboxService service, int nPort, AuthSettings auth,
            VersionId version) {
        boolean flagUp = service.isRunning();
        ReadyReport report = flagUp ? service.report() : null;
        CantonInstallation inst = service.installation();
        // A NODE'S `auth.token` IS WHAT MAKES A CONSUMER ABLE TO CHOOSE.
        // Without it `Discovery.tokenSource` hands back one credential for
        // every user named, and nothing that depends on WHO is asking can be
        // measured.
        boolean flagProvider = auth != null && auth.mode() != AuthSettings.Mode.NONE;
        List<DiscoveryNode> lstNode = report == null ? List.of()
                : List.of(new DiscoveryNode(StorageOverlay.STR_NODE_PARTICIPANT,
                        DiscoveryNode.STR_ROLE_APP_PROVIDER, auth,
                        flagProvider ? JwtMintProcess.strUrlJwks() : null,
                        AuthStart.strUrlTokenFor(auth, version, AviationRun.STR_USER_ADMIN),
                        AuthStart.mapOidcFor(auth, AviationRun.STR_USER_ADMIN,
                                StorageOverlay.STR_NODE_PARTICIPANT),
                        report));

        return new DiscoveryDoc(service.state().name(), nPort,
                DiscoveryDoc.STR_TOPOLOGY_SANDBOX,
                inst == null ? null : inst.version().toString(),
                inst == null ? null : inst.edition().name(),
                service.options().dirWork(), null,
                flagProvider,
                JwtMintProcess.isExternal() ? DiscoveryDoc.STR_MODE_EXTERNAL
                        : DiscoveryDoc.STR_MODE_EMBEDDED,
                JwtMintProcess.mapProvider(), lstNode);
    }
}
