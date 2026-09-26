// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.process;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.topology.Canton3xBootstrap;
import com.raposza.runtime.process.JvmCommand;
import com.raposza.runtime.process.ManagedProcess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A short-lived Canton console attached to a running 3.x sandbox, used to run
 * one script and exit.
 *
 * <h2>Why this exists as a second process</h2>
 *
 * The `sandbox` subcommand REFUSES `--bootstrap`, and not by ignoring it:
 *
 * <pre>
 * Error: bootstrap script cannot be defined together with the 'sandbox' command
 * </pre>
 *
 * Measured on 3.5.11. The option is declared globally in `--help` and
 * excluded per-subcommand with nothing in the help text saying so, which is
 * how it came to be inferred as available.
 *
 * `sandbox-console` is where the console lives. Its own help, which appears
 * only when an invocation fails:
 *
 * <pre>
 * Command: sandbox-console [options]
 * Start a canton console connected to a remote participant and synchronizer
 *   --host, --port, --admin-api-port,
 *   --sequencer-public-port, --sequencer-admin-port, --mediator-admin-port
 * </pre>
 *
 * So it LAUNCHES NOTHING. It connects to nodes that are already up, which makes
 * this a second process beside `CantonSandboxProcess` rather than a
 * replacement for it: the vendor topology, the readiness signals and the
 * synchronizer bootstrap all stay where they are.
 *
 * <h2>Configured with -c, not with flags</h2>
 *
 * `--host` and the port flags connect, and every Ledger API call then arrives
 * with NO credential - UNAUTHENTICATED against any participant with
 * `auth-services`. The token is a field of `RemoteParticipantConfig` and has no
 * flag, so the endpoints and the credential travel together in one file.
 * `sandbox-console` accepting `-c` is measured, not assumed.
 *
 * <h2>It has to be told to leave</h2>
 *
 * The console is interactive. A script that ends without exiting leaves the
 * process at a prompt reading standard input, which in a test harness is a
 * process that never returns. {@link Canton3xBootstrap} emits the exit call as
 * its last line, and this class waits for the completion the script prints
 * rather than for a port or a log phrase.
 *
 * Author Claude/bentzn
 */
public final class CantonConsoleProcess extends ManagedProcess {

    private static final int N_MAJOR_MIN = 3;

    private static final String STR_HOST_DEFAULT = "localhost";

    private final CantonInstallation installation;
    private final Path fileScript;
    private final Path fileConf;
    private final Path dirWork;


    /**
     * @param installation the Canton whose jar to run; must be 3.x
     * @param fileScript the console script
     * @param fileConf the remote-participant configuration, which carries the
     *        endpoints AND the token; see
     *        {@link com.raposza.canton.topology.RemoteConsoleOverlay}
     * @param dirWork where the console runs
     */
    public CantonConsoleProcess(CantonInstallation installation, Path fileScript, Path fileConf,
            Path dirWork) {
        super("canton-console");

        if (installation == null || fileScript == null || fileConf == null || dirWork == null)
            throw new IllegalArgumentException(
                    "installation, fileScript, fileConf and dirWork are required");
        if (installation.version().major() < N_MAJOR_MIN)
            throw new IllegalArgumentException("sandbox-console is a 3.x subcommand; "
                    + installation.version() + " uses the 2.x bootstrap instead");
        if (!installation.hasRuntime())
            throw new IllegalArgumentException(
                    "no Canton runtime jar was found under " + installation.dirHome());

        this.installation = installation;
        this.fileScript = fileScript;
        this.fileConf = fileConf;
        this.dirWork = dirWork.toAbsolutePath().normalize();
    }


    public Path fileScript() {
        return fileScript;
    }


    public Path fileConf() {
        return fileConf;
    }


    @Override
    protected Path workingDir() {
        return dirWork;
    }


    /**
     * The script's own last line, rather than anything Canton prints. A console
     * that connected and then failed halfway still prints a banner.
     */
    @Override
    protected boolean isReadyLine(String strLine) {
        return strLine.contains(Canton3xBootstrap.STR_DONE);
    }


    @Override
    protected List<String> buildCommand() throws IOException {
        if (!Files.isRegularFile(fileScript))
            throw new IOException("console script not found: " + fileScript);
        if (!Files.isRegularFile(fileConf))
            throw new IOException("console configuration not found: " + fileConf);

        return JvmCommand.ofJar(installation.fileRuntime())
                .arg("sandbox-console")
                // -c rather than --host and the port flags. Those connect, and
                // their Ledger API calls arrive with no credential; the token
                // is a field of RemoteParticipantConfig and there is no flag
                // for it.
                .arg("-c", fileConf)
                .arg("--bootstrap", fileScript)
                .arg("--no-tty")
                .build();
    }


}
