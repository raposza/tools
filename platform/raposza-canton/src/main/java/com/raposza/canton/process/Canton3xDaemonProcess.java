// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.process;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.jdbc.PgShim;
import com.raposza.canton.topology.Canton3xDaemonBootstrap;
import com.raposza.runtime.process.JvmCommand;
import com.raposza.runtime.process.ProcessException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A Canton 3.x stack under `daemon`, with a bootstrap this project wrote.
 *
 * The alternative to `CantonSandboxProcess`, and the difference is one
 * property: this one can be started twice against the same storage. The
 * subcommand runs a generated bootstrap that re-proposes a synchronizer trust
 * certificate on every start and takes the stack down when it already exists;
 * `daemon` runs the script it is given.
 *
 * <h2>What has to be handed over that the subcommand supplied</h2>
 *
 * `daemon` processes NO default configuration and has NO port options, so both
 * arrive as `-c` files: the vendor's own `sandbox.conf`, extracted from the jar
 * by
 * {@link com.raposza.canton.install.CantonBuiltinConf}, and then
 * every overlay over it. The topology is still not hand-written,
 * but the ports are now configuration rather than flags, which is the real cost
 * of this launcher.
 *
 * Order is Canton's: a later `-c` wins where two set the same key, so the
 * vendor file goes first and the overlays after it.
 *
 * <h2>Readiness</h2>
 *
 * The subcommand prints a phrase of its own and writes a port file; neither is
 * available here. `daemon` starts nodes and then runs the script, so the
 * signal is the script's own last line, which this project chose and which
 * therefore cannot be reworded by a patch release. The participant-id file the
 * script writes is the out-of-band half, for the same reason
 * `CantonSandboxProcess` reads a port file rather than trusting a phrase.
 *
 * Author Claude/bentzn
 */
public class Canton3xDaemonProcess extends CantonProcess {

    private static final int N_MAJOR_MIN = 3;

    private final CantonInstallation installation;
    private final List<Path> lstFileConf;
    private final Path fileBootstrap;
    private final int nHeapMb;


    /**
     * @param installation the Canton to launch; must be 3.x
     * @param lstFileConf every configuration file, in order, the vendor's own
     *        `sandbox.conf` first
     * @param fileBootstrap the script to run once the nodes are up
     * @param dirWork where the log and the sentinels go
     * @param nHeapMb the JVM heap, or 0 for the default
     */
    public Canton3xDaemonProcess(CantonInstallation installation, List<Path> lstFileConf,
            Path fileBootstrap, Path dirWork, int nHeapMb) {
        super("canton-daemon", dirWork);

        if (installation == null)
            throw new IllegalArgumentException("installation is required");
        if (installation.version().major() < N_MAJOR_MIN)
            throw new IllegalArgumentException("this launcher is 3.x only; " + installation.version()
                    + " is a 2.x line and belongs to Canton2xProcess");
        if (!installation.hasRuntime())
            throw new IllegalArgumentException(
                    "no Canton runtime jar was found under " + installation.dirHome());
        if (lstFileConf == null || lstFileConf.isEmpty())
            throw new IllegalArgumentException(
                    "daemon has no default configuration; at least the vendor's sandbox.conf"
                            + " is required");
        if (fileBootstrap == null)
            throw new IllegalArgumentException("a bootstrap script is required");
        if (nHeapMb < 0)
            throw new IllegalArgumentException("heap must not be negative: " + nHeapMb);

        this.installation = installation;
        this.lstFileConf = List.copyOf(new ArrayList<>(lstFileConf));
        this.fileBootstrap = fileBootstrap;
        this.nHeapMb = nHeapMb;
    }


    public CantonInstallation installation() {
        return installation;
    }


    public List<Path> lstFileConf() {
        return lstFileConf;
    }


    public Path fileBootstrap() {
        return fileBootstrap;
    }


    public Path fileParticipantId() {
        return workingDir().resolve(Canton3xDaemonBootstrap.STR_PARTICIPANT_ID_FILE);
    }


    public Path fileMarker() {
        return workingDir().resolve(Canton3xDaemonBootstrap.STR_MARKER_FILE);
    }


    public Path filePartyId() {
        return workingDir().resolve(Canton3xDaemonBootstrap.STR_PARTY_ID_FILE);
    }


    public Path fileUserId() {
        return workingDir().resolve(Canton3xDaemonBootstrap.STR_USER_ID_FILE);
    }


    public Path filePing() {
        return workingDir().resolve(Canton3xDaemonBootstrap.STR_PING_FILE);
    }


    /**
     * @return the file carrying one main package id per uploaded DAR; present
     *         only when the script was given DARs
     */
    public Path fileDarPackageIds() {
        return workingDir().resolve(Canton3xDaemonBootstrap.STR_DAR_FILE);
    }


    /**
     * @return the file whose presence means the bootstrap script RAN TO THE
     *         END, which is a different question from any other sentinel here
     */
    public Path fileReady() {
        return workingDir().resolve(Canton3xDaemonBootstrap.STR_READY_FILE);
    }


    @Override
    public synchronized void start() {
        // Both sentinels, for the reason CantonSandboxProcess clears the port
        // file: left from a previous run they report this one ready before its
        // script has run.
        deleteIfPresent(fileMarker());
        deleteIfPresent(fileParticipantId());

        // The provisioning sentinels too, and for a sharper reason than the
        // two above: the party and the user SURVIVE a restart by design, so a
        // stale party-id.txt is indistinguishable from one this start wrote.
        // Clearing them is what lets a test assert that the second start
        // reached its guards rather than that a file from the first is still
        // lying there.
        deleteIfPresent(filePartyId());
        deleteIfPresent(fileUserId());
        deleteIfPresent(filePing());

        // And the DAR ids, for exactly the party's reason: an uploaded package
        // survives a restart, so a stale file is indistinguishable from one
        // this start wrote - and the idempotency of a repeat upload is a thing
        // a test has to be able to assert rather than assume.
        deleteIfPresent(fileDarPackageIds());

        // The readiness sentinel above all: left behind, it reports THIS start
        // ready before its script has run a line.
        deleteIfPresent(fileReady());
        super.start();
    }


    @Override
    protected boolean isReadyLine(String strLine) {
        return strLine.contains(Canton3xDaemonBootstrap.STR_DONE);
    }


    @Override
    protected boolean isReadyOutOfBand() {
        try {
            // The READY sentinel, not the participant id. The participant id
            // is written before the provisioning block, so waiting on it
            // reports a script that is still running - and the caller's next
            // move is usually to stop the process, which is how a live test
            // lost its user-id.txt.
            Path file = fileReady();
            return Files.isRegularFile(file) && Files.size(file) > 0L;
        }
        catch (IOException ex) {
            return false;
        }
    }


    @Override
    protected List<String> buildCommand() throws IOException {
        for (Path fileConf : lstFileConf) {
            if (!Files.isRegularFile(fileConf))
                throw new IOException("configuration not found: " + fileConf);
        }
        if (!Files.isRegularFile(fileBootstrap))
            throw new IOException("bootstrap script not found: " + fileBootstrap);

        // WINDOWS ONLY, and only once the class files are extracted; see
        // CantonSandboxProcess for why `-jar` cannot carry them.
        JvmCommand cmd = PgShim.flagActive(workingDir(), installation.fileRuntime())
                ? JvmCommand.ofClasspath(
                        List.of(PgShim.dirShim(workingDir()), installation.fileRuntime()),
                        PgShim.strMainClass(installation.fileRuntime()))
                : JvmCommand.ofJar(installation.fileRuntime());
        cmd.heapMb(nHeapMb).arg("daemon");

        for (Path fileConf : lstFileConf) {
            cmd.arg("-c", fileConf);
        }

        cmd.arg("--bootstrap", fileBootstrap);
        // NO --no-tty, and THE REASON RECORDED HERE ON 2026-08-18 WAS
        // WRONG. The exit-0 it blamed on this flag was the bootstrap
        // script's own trailing `sys.exit(0)` - System.exit inside this
        // JVM - which is why removing the flag changed nothing.
        // Canton3xDaemonBootstrap no longer emits it.
        //
        // The flag stays off because nothing has measured what this daemon
        // does with a console reading a pipe that never reaches EOF, and
        // one delivery changes one thing. The console launcher keeps the
        // flag: it runs a script and is MEANT to exit.
        cmd.arg("--log-file-name", fileLog());
        return cmd.build();
    }


    private static void deleteIfPresent(Path file) {
        try {
            Files.deleteIfExists(file);
        }
        catch (IOException ex) {
            throw new ProcessException("could not clear the stale sentinel " + file, ex);
        }
    }
}
