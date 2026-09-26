// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import com.raposza.runtime.port.PortGuard;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The ports a 2.x stack listens on.
 *
 * Four, where 3.x has six, and that is the topology rather than a subset: 2.x
 * has one embedded DOMAIN node carrying sequencer, mediator and topology
 * manager together, so there is no mediator admin port to name.
 *
 * The JSON API port is here and is not a Canton port at all. On 2.x the HTTP
 * JSON API is a separate process out of `daml-sdk.jar`, so the number is
 * reserved by this record and consumed by that launcher rather than by the
 * daemon. On 3.x the same API is inside the participant.
 *
 * The numbers deliberately sit on the same block as 3.x, so the domain's two
 * APIs take the numbers the 3.x sequencer's do and RAPOSZA_IT_* port
 * settings stay valid when a stack moves line.
 *
 * @param nPortLedgerApi the participant's gRPC Ledger API
 * @param nPortAdminApi the participant's admin API
 * @param nPortDomainPublic the domain's public API
 * @param nPortDomainAdmin the domain's admin API
 * @param nPortJsonApi the separate HTTP JSON API process
 *
 * Author Claude/bentzn
 */
public record Sandbox2xPorts(int nPortLedgerApi, int nPortAdminApi, int nPortDomainPublic,
        int nPortDomainAdmin, int nPortJsonApi) {

    public static final int N_DEFAULT_JSON_API = SandboxPorts.N_DEFAULT_JSON_API;

    public static final int N_DEFAULT_LEDGER_API = SandboxPorts.N_DEFAULT_LEDGER_API;

    public static final int N_DEFAULT_ADMIN_API = SandboxPorts.N_DEFAULT_ADMIN_API;

    public static final int N_DEFAULT_DOMAIN_PUBLIC = SandboxPorts.N_DEFAULT_SEQUENCER_PUBLIC;

    public static final int N_DEFAULT_DOMAIN_ADMIN = SandboxPorts.N_DEFAULT_SEQUENCER_ADMIN;


    public Sandbox2xPorts {
        PortGuard.requireInRange(nPortLedgerApi, "ledger-api");
        PortGuard.requireInRange(nPortAdminApi, "admin-api");
        PortGuard.requireInRange(nPortDomainPublic, "domain public-api");
        PortGuard.requireInRange(nPortDomainAdmin, "domain admin-api");
        PortGuard.requireInRange(nPortJsonApi, "json-api");
    }


    public static Sandbox2xPorts ofDefaults() {
        return new Sandbox2xPorts(N_DEFAULT_LEDGER_API, N_DEFAULT_ADMIN_API,
                N_DEFAULT_DOMAIN_PUBLIC, N_DEFAULT_DOMAIN_ADMIN, N_DEFAULT_JSON_API);
    }


    /**
     * @param nOffset how far to move every port
     * @return the default block shifted, which keeps a second stack off the
     *         first one's ports without renumbering anything by hand
     */
    public static Sandbox2xPorts ofDefaultsOffsetBy(int nOffset) {
        return new Sandbox2xPorts(N_DEFAULT_LEDGER_API + nOffset, N_DEFAULT_ADMIN_API + nOffset,
                N_DEFAULT_DOMAIN_PUBLIC + nOffset, N_DEFAULT_DOMAIN_ADMIN + nOffset,
                N_DEFAULT_JSON_API + nOffset);
    }


    /**
     * @return the port scribe's health server takes beside this block. See
     *         {@link SandboxPorts#nPortPqsHealth} for why it is derived rather
     *         than fixed; the reason is not line-specific
     */
    public int nPortPqsHealth() {
        return PortGuard.nAbove(nPortLedgerApi, nPortAdminApi, nPortDomainPublic,
                nPortDomainAdmin, nPortJsonApi);
    }


    /**
     * Refuse to start when any port this stack needs is already held.
     *
     * The JSON API is NOT among them: on 2.x it is a separate process out of
     * `daml-sdk.jar` with its own lifecycle, so reserving its port here would
     * refuse to start a participant because something unrelated held a port
     * the participant never binds.
     *
     * On the record rather than on PortGuard.
     *
     * @param nPortPostgres the embedded server's port
     * @throws IllegalStateException naming every occupied port
     */
    public void requireFree(int nPortPostgres) {
        Map<String, Integer> mapPort = new LinkedHashMap<>();
        mapPort.put("postgres", nPortPostgres);
        mapPort.put("ledger-api", nPortLedgerApi());
        mapPort.put("admin-api", nPortAdminApi());
        mapPort.put("domain public-api", nPortDomainPublic());
        mapPort.put("domain admin-api", nPortDomainAdmin());
        PortGuard.requireFree(mapPort);
    }

}
