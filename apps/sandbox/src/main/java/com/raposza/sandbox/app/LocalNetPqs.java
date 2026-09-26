// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.canton.install.VersionId;
import com.raposza.canton.pqs.PqsOAuth;
import com.raposza.canton.pqs.PqsSpec;
import com.raposza.canton.pqs.ScribeProcess;
import com.raposza.runtime.db.PostgresCoordinates;
import com.raposza.runtime.localnet.LocalNetPorts;
import com.raposza.runtime.localnet.LocalNetSpec;

import java.nio.file.Path;

/**
 * PQS on the LocalNetND topology: what it connects to, where it writes, and
 * which binary runs.
 *
 * <h2>IT CONNECTS TO app-provider - his instruction, 2026-09-22</h2>
 *
 * "It should connect to the app-provider ledger." One scribe, one participant.
 * The role is {@link #STR_ROLE} and the port is read off the stack's own block
 * - {@link LocalNetPorts#nPortLedger} - rather than written down, because the
 * block moves with `N_PORT_FIRST_DEFAULT` and a literal would address the
 * Sandbox's ports on a stack that had been moved off them.
 *
 * <h2>WHY THIS SITS IN `apps/sandbox` AND NOT IN THE RUNNER</h2>
 *
 * `LocalNetRunner` would be the natural owner - it owns PostgreSQL, canton and
 * splice - and it cannot be. `raposza-runtime` carries the enforcer rule
 * `s2-runtime-declares-nothing-above-it`, and `ScribeProcess` lives in
 * `raposza-canton`, which is above it. So scribe is owned BESIDE the stack by
 * the window that started it, the way the discovery endpoint already is.
 *
 * The consequence is visible and is not hidden: PQS is not a member of
 * {@link com.raposza.runtime.lifecycle.StackService_i#lstComponent}, so it has
 * no lamp and no row in the stack's own health. Its log tab is the window's,
 * which is where the Sandbox's PQS tab already is.
 *
 * <h2>The database is on the stack's own cluster</h2>
 *
 * `LocalNetRunner.startPostgres` ensures every name
 * {@link LocalNetSpec#lstAllDatabases} lists, so PQS takes one there rather
 * than standing up a second server. `PqsSpec`'s rule is ONE DATABASE PER
 * SCRIBE BINARY VERSION - `pqs_inventory.md` section 3, where three installed
 * binaries carry three schema revisions - and this is one stack running one
 * binary, so one database is the whole of it.
 *
 * <h2>The binary is resolved from the STOCK CANTON'S line</h2>
 *
 * PQS pairs `major.minor` with Canton and nothing else -
 * `pqs_inventory.md` section 3, where the patch, the schema revision and the
 * reported `daml-sdk.version` all move independently. The three participants
 * run inside the one stock Canton jar the window selected, so its version is
 * the line to resolve against. {@link PqsSpec#resolveForLine} returns the mock
 * where no binary is staged, which is the honest answer rather than a refusal
 * to start.
 *
 * <h2>The credential is the stack's own</h2>
 *
 * The caller mints it the way the Sandbox path does and hands it in. OAuth
 * WINS over a static token where both are given, for the reason `PqsSpec`
 * states: a static token cannot outlive its own lifetime and scribe does not
 * renew one.
 *
 * Author Claude/bentzn
 */
public final class LocalNetPqs {

    /** HIS INSTRUCTION, 2026-09-22. The one participant scribe reads. */
    public static final String STR_ROLE = "app-provider";

    /**
     * Where scribe's health server sits in the stack's port block.
     *
     * The block is documented in {@link LocalNetPorts}: roles take 0..25 and
     * infrastructure 30..41, and PQS health is the slot above them - the last
     * slot of the node block. It moves with the block rather than pinning
     * scribe to one stack, and the block's own first-port cap counts it.
     */
    public static final int N_OFFSET_HEALTH = LocalNetPorts.N_OFFSET_PQS_HEALTH;

    /** sv's ledger port IS the first port of the block - `LocalNetPorts`. */
    private static final String STR_ROLE_FIRST = "sv";


    private LocalNetPqs() {
    }


    /**
     * @param version the stock Canton the three participants run inside
     * @return its minor line, such as "3.5"
     */
    public static String strCantonLineOf(VersionId version) {
        if (version == null)
            throw new IllegalArgumentException("a version is required");
        return version.major() + "." + version.minor();
    }


    /**
     * @param version the stock Canton the three participants run inside
     * @param strToken a static access token, or null
     * @param oauth credentials to re-mint from, or null; these WIN
     * @return the spec, real where a scribe is staged for that line and the
     *         mock where none is
     */
    public static PqsSpec specFor(VersionId version, String strToken, PqsOAuth oauth) {
        PqsSpec spec = PqsSpec.resolveForLine(strCantonLineOf(version))
                .withPrefix(LocalNetSpec.STR_DB_PQS);
        if (strToken != null)
            spec = spec.withToken(strToken);
        if (oauth != null)
            spec = spec.withOAuth(oauth);
        return spec;
    }


    /**
     * @param strHost what the cluster is dialled on
     * @param ports the stack's port block
     * @return where scribe writes
     */
    public static PostgresCoordinates pgOf(String strHost, LocalNetPorts ports) {
        if (ports == null)
            throw new IllegalArgumentException("a port block is required");
        return new PostgresCoordinates(strHost, ports.nPortPostgres(),
                LocalNetSpec.STR_DB_PQS, LocalNetSpec.STR_DB_USER,
                LocalNetSpec.STR_DB_PASSWORD);
    }


    /**
     * @param ports the stack's port block
     * @return the app-provider Ledger API port scribe streams from
     */
    public static int nPortLedger(LocalNetPorts ports) {
        if (ports == null)
            throw new IllegalArgumentException("a port block is required");
        return ports.nPortLedger(STR_ROLE);
    }


    /**
     * @param ports the stack's port block
     * @return where scribe's own health server listens
     */
    public static int nPortHealth(LocalNetPorts ports) {
        if (ports == null)
            throw new IllegalArgumentException("a port block is required");
        return ports.nPortLedger(STR_ROLE_FIRST) + N_OFFSET_HEALTH;
    }


    /**
     * @param spec what to run and with which credential
     * @param strHost what the stack is dialled on
     * @param ports the stack's port block
     * @param dirWork where scribe runs
     * @return the process, NOT started - the caller starts it once the stack
     *         reports running, because scribe retries a refused Ledger API for
     *         ever and a stack that is still binding looks exactly like one
     *         that refused
     */
    public static ScribeProcess processFor(PqsSpec spec, String strHost,
            LocalNetPorts ports, Path dirWork) {
        if (spec == null)
            throw new IllegalArgumentException("a spec is required");
        return ScribeProcess.of(spec, pgOf(strHost, ports), strHost, nPortLedger(ports),
                dirWork, nPortHealth(ports));
    }

}
