// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

/**
 * The configuration that tells a remote Canton console where the sandbox is,
 * and what credential to present to it.
 *
 * <h2>Why this replaced the console's own flags</h2>
 *
 * `sandbox-console --host --port ...` connects, and its Ledger API calls arrive
 * with NO credential. Against a participant with `auth-services` that is:
 *
 * <pre>
 * GrpcClientError: UNAUTHENTICATED
 * Request: AllocateParty(raposza,Map(),,None,)
 * </pre>
 *
 * Measured on 3.5.11. The admin token is configured on the
 * PARTICIPANT; an in-process console picks it up and a remote one cannot know
 * it exists. There is no token flag on the subcommand - the token is a field of
 * `RemoteParticipantConfig`, beside the two endpoints:
 *
 * <pre>
 * adminApi: FullClientConfig, ledgerApi: FullClientConfig,
 * ledgerJsonApi: Option[JsonClientConfig], token: Option[String]
 * </pre>
 *
 * read with javap, and `FullClientConfig` carries `address`. So the console is
 * configured with `-c` rather than with flags, which `sandbox-console` accepts -
 * measured, and NOT assumed after `--bootstrap` turned out to be excluded from
 * the `sandbox` subcommand despite appearing in its global help.
 *
 * <h2>The node name is load-bearing, twice over</h2>
 *
 * The console binds each remote participant under the name given here, and the
 * bootstrap script refers to it by that name. A mismatch is a script that fails
 * on an unknown identifier.
 *
 * It must also NOT be the participant's own name. See {@link #STR_NODE_DEFAULT}:
 * the console generates an entry called `sandbox` from its port flag defaults,
 * and that entry wins over one of the same name in a `-c` file.
 *
 * Author Claude/bentzn
 */
public final class RemoteConsoleOverlay {

    public static final String STR_HOST_DEFAULT = "localhost";

    /**
     * NOT the participant's own name, and that is the whole point.
     *
     * `sandbox-console` builds its own `remote-participants.sandbox` entry from
     * `--port` and `--admin-api-port`, which DEFAULT to 6865 and 6866. That
     * entry shadows one of the same name in a `-c` file, so a console
     * configured for a sandbox on offset ports connected to 6866 and reported
     * `Connection refused`, measured on 3.5.11, and it failed the
     * unauthenticated live test too, which is what showed it was about
     * endpoints rather than credentials.
     *
     * The shadowing is per NAME: a probe using this name reached a sandbox on
     * 16865/16866 through `-c` alone, ran an admin call and allocated a party.
     * So the fix is a name the console does not generate, which keeps the
     * endpoints and the token together in one file, instead of
     * splitting them across flags and configuration.
     *
     * The bootstrap script refers to the participant by THIS name.
     */
    public static final String STR_NODE_DEFAULT = "raposza";

    private final String strNode;
    private final String strHost;
    private final int nPortLedgerApi;
    private final int nPortAdminApi;
    private final String strToken;


    /**
     * @param strNode the name the console binds the participant under; must
     *        match what the bootstrap script calls it
     * @param strHost where the sandbox listens
     * @param nPortLedgerApi the participant's gRPC Ledger API
     * @param nPortAdminApi the participant's admin API
     * @param strToken the bearer token for the Ledger API, or null when the
     *        participant has no auth-services
     */
    public RemoteConsoleOverlay(String strNode, String strHost, int nPortLedgerApi,
            int nPortAdminApi, String strToken) {
        if (strNode == null || strNode.isBlank())
            throw new IllegalArgumentException("a node name is required");
        if (strHost == null || strHost.isBlank())
            throw new IllegalArgumentException("a host is required");
        check(nPortLedgerApi, "ledger-api");
        check(nPortAdminApi, "admin-api");

        this.strNode = strNode;
        this.strHost = strHost;
        this.nPortLedgerApi = nPortLedgerApi;
        this.nPortAdminApi = nPortAdminApi;
        this.strToken = strToken;
    }


    /**
     * @param ports the running sandbox's ports
     * @param strToken the bearer token, or null for none
     * @return an overlay for the bundled participant name
     */
    public static RemoteConsoleOverlay of(SandboxPorts ports, String strToken) {
        if (ports == null)
            throw new IllegalArgumentException("ports are required");
        return new RemoteConsoleOverlay(STR_NODE_DEFAULT, STR_HOST_DEFAULT,
                ports.nPortLedgerApi(), ports.nPortAdminApi(), strToken);
    }


    public String strNode() {
        return strNode;
    }


    public boolean flagAuthenticated() {
        return strToken != null && !strToken.isBlank();
    }


    /**
     * @return the HOCON to hand the console with -c
     */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("// SPDX-License-Identifier: Apache-2.0\n");
        sb.append("// Generated by raposza-canton: where the console connects.\n");
        sb.append("canton.remote-participants.").append(strNode).append(" {\n");
        sb.append("  admin-api { address = \"").append(escape(strHost)).append("\", port = ")
                .append(nPortAdminApi).append(" }\n");
        sb.append("  ledger-api { address = \"").append(escape(strHost)).append("\", port = ")
                .append(nPortLedgerApi).append(" }\n");
        if (flagAuthenticated())
            sb.append("  token = \"").append(escape(strToken)).append("\"\n");
        sb.append("}\n");
        return sb.toString();
    }


    /** Safe for a log line: where and whether, never the token. */
    public String describe() {
        return strNode + " at " + strHost + ":" + nPortLedgerApi + " (admin " + nPortAdminApi
                + "), token " + (flagAuthenticated() ? "present" : "none");
    }


    @Override
    public String toString() {
        return "remote console: " + describe();
    }


    private static String escape(String strValue) {
        return strValue.replace("\\", "\\\\").replace("\"", "\\\"");
    }


    private static void check(int nPort, String strWhat) {
        if (nPort < 1 || nPort > 65535)
            throw new IllegalArgumentException("port out of range for " + strWhat + ": " + nPort);
    }
}
