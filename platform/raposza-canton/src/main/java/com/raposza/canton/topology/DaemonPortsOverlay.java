// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

/**
 * The six ports as configuration, because `daemon` has none as flags.
 *
 * This is the one real cost of the daemon launcher and it is worth stating
 * plainly. `CantonSandboxProcess` passes `--ledger-api-port` and its five
 * siblings on the command line; `daemon --help` on 3.5.11 offers `-c`, `-C`,
 * `--bootstrap`, `--manual-start` and logging options, and NO ports. So a
 * stack launched with `daemon` takes its ports from a file, and this renders
 * it.
 *
 * The node names are `sandbox.conf`'s own - the same three
 * {@link StorageOverlay} restates storage for - and the key paths mirror that
 * file's shape: `http-ledger-api` for the participant's HTTP port, `public-api`
 * and `admin-api` on the sequencer, `admin-api` on the mediator. Read off the
 * extracted file rather than recalled; see
 * {@link com.raposza.canton.install.CantonBuiltinConf}.
 *
 * Author Claude/bentzn
 */
public final class DaemonPortsOverlay {

    private final SandboxPorts ports;


    public DaemonPortsOverlay(SandboxPorts ports) {
        if (ports == null)
            throw new IllegalArgumentException("ports are required");
        this.ports = ports;
    }


    public SandboxPorts ports() {
        return ports;
    }


    /**
     * @return HOCON restating the six ports over the vendor topology
     */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("canton {\n");
        sb.append("  participants {\n");
        sb.append("    ").append(StorageOverlay.STR_NODE_PARTICIPANT).append(" {\n");
        sb.append("      ledger-api { port = ").append(ports.nPortLedgerApi()).append(" }\n");
        sb.append("      admin-api { port = ").append(ports.nPortAdminApi()).append(" }\n");
        sb.append("      http-ledger-api { port = ").append(ports.nPortJsonApi()).append(" }\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("  sequencers {\n");
        sb.append("    ").append(StorageOverlay.STR_NODE_SEQUENCER).append(" {\n");
        sb.append("      public-api { port = ").append(ports.nPortSequencerPublic()).append(" }\n");
        sb.append("      admin-api { port = ").append(ports.nPortSequencerAdmin()).append(" }\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("  mediators {\n");
        sb.append("    ").append(StorageOverlay.STR_NODE_MEDIATOR).append(" {\n");
        sb.append("      admin-api { port = ").append(ports.nPortMediatorAdmin()).append(" }\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }


    @Override
    public String toString() {
        return "daemon ports: ledger-api " + ports.nPortLedgerApi() + ", admin-api "
                + ports.nPortAdminApi() + ", json-api " + ports.nPortJsonApi();
    }
}
