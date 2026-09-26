// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import com.raposza.runtime.port.PortGuard;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The six ports a 3.x sandbox stack listens on.
 *
 * The defaults are Canton's own, read from `sandbox/sandbox.conf` inside the
 * 3.4.11 and 3.5.11 jars - the two files are byte-identical. They are restated
 * here rather than left implicit because the launcher passes every one of them
 * on the command line: a stack that took some ports from a default and some
 * from us would move under a Canton patch release without anything saying so.
 *
 * @param nPortLedgerApi the participant's gRPC Ledger API
 * @param nPortAdminApi the participant's admin API
 * @param nPortJsonApi the participant's built-in HTTP Ledger API - in 3.x this
 *        is part of the participant, not the separate process 2.x needs
 * @param nPortSequencerPublic the sequencer's public API
 * @param nPortSequencerAdmin the sequencer's admin API
 * @param nPortMediatorAdmin the mediator's admin API
 *
 * Author Claude/bentzn
 */
public record SandboxPorts(int nPortLedgerApi, int nPortAdminApi, int nPortJsonApi,
        int nPortSequencerPublic, int nPortSequencerAdmin, int nPortMediatorAdmin) {

    /** Canton's own sandbox defaults, from sandbox/sandbox.conf in the jar. */
    public static final int N_DEFAULT_JSON_API = 6864;

    public static final int N_DEFAULT_LEDGER_API = 6865;

    public static final int N_DEFAULT_ADMIN_API = 6866;

    public static final int N_DEFAULT_SEQUENCER_PUBLIC = 6867;

    public static final int N_DEFAULT_SEQUENCER_ADMIN = 6868;

    public static final int N_DEFAULT_MEDIATOR_ADMIN = 6869;


    public SandboxPorts {
        PortGuard.requireInRange(nPortLedgerApi, "ledger-api");
        PortGuard.requireInRange(nPortAdminApi, "admin-api");
        PortGuard.requireInRange(nPortJsonApi, "json-api");
        PortGuard.requireInRange(nPortSequencerPublic, "sequencer public-api");
        PortGuard.requireInRange(nPortSequencerAdmin, "sequencer admin-api");
        PortGuard.requireInRange(nPortMediatorAdmin, "mediator admin-api");
    }


    public static SandboxPorts ofDefaults() {
        return new SandboxPorts(N_DEFAULT_LEDGER_API, N_DEFAULT_ADMIN_API, N_DEFAULT_JSON_API,
                N_DEFAULT_SEQUENCER_PUBLIC, N_DEFAULT_SEQUENCER_ADMIN, N_DEFAULT_MEDIATOR_ADMIN);
    }


    /**
     * The default block moved by a fixed amount, which keeps a second stack off
     * the first one's ports without renumbering anything by hand.
     *
     * @param nOffset how far to move every port
     * @return the shifted block
     */
    public static SandboxPorts ofDefaultsOffsetBy(int nOffset) {
        return new SandboxPorts(N_DEFAULT_LEDGER_API + nOffset, N_DEFAULT_ADMIN_API + nOffset,
                N_DEFAULT_JSON_API + nOffset, N_DEFAULT_SEQUENCER_PUBLIC + nOffset,
                N_DEFAULT_SEQUENCER_ADMIN + nOffset, N_DEFAULT_MEDIATOR_ADMIN + nOffset);
    }


    /**
     * scribe's health server is NOT a Canton port and is derived here because
     * this record owns the block it has to stay clear of.
     *
     * Measured. `health.port` defaults to a FIXED 8080 in scribe v3.5.7 and a
     * bind failure there is fatal to the whole process - it logs
     * `Address already in use` and exits. Every stack this project starts would
     * therefore share one number, so two stacks at once, or one stack beside
     * any other scribe, kills the second. `--health-port` is the accepted
     * spelling; `--health.port` is SILENTLY IGNORED, which is why this is
     * asserted by a test on the rendered command rather than trusted.
     *
     * @return the port scribe's health server takes beside this block
     */
    public int nPortPqsHealth() {
        return PortGuard.nAbove(nPortLedgerApi, nPortAdminApi, nPortJsonApi,
                nPortSequencerPublic, nPortSequencerAdmin, nPortMediatorAdmin);
    }


    /**
     * Refuse to start when any port this stack needs is already held.
     *
     * This lives on the port record and not on PortGuard.
     * Knowing that a 3.x stack binds a ledger API, an admin API, a JSON API, a
     * sequencer pair and a mediator admin port is knowledge of a CANTON NODE
     * SHAPE; finding a free port and refusing a collision is not. PortGuard
     * carried both until the module split made the difference load-bearing.
     *
     * @param nPortPostgres the embedded server's port
     * @throws IllegalStateException naming every occupied port, so one run
     *         reports all of them rather than one per attempt
     */
    public void requireFree(int nPortPostgres) {
        Map<String, Integer> mapPort = new LinkedHashMap<>();
        mapPort.put("postgres", nPortPostgres);
        mapPort.put("ledger-api", nPortLedgerApi());
        mapPort.put("admin-api", nPortAdminApi());
        mapPort.put("json-api", nPortJsonApi());
        mapPort.put("sequencer public-api", nPortSequencerPublic());
        mapPort.put("sequencer admin-api", nPortSequencerAdmin());
        mapPort.put("mediator admin-api", nPortMediatorAdmin());
        PortGuard.requireFree(mapPort);
    }

}
