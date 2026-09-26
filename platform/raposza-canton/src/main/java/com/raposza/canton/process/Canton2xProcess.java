// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.process;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.topology.Canton2xConfig;
import com.raposza.runtime.process.JvmCommand;
import com.raposza.runtime.process.ProcessException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A Canton 2.x stack: one participant and one embedded domain, from
 * `daemon -c <conf> --bootstrap <script>`.
 *
 * The 2.x usage line offers daemon, run and generate and nothing else, so
 * there is no subcommand to lean on and no vendor configuration to overlay.
 * The configuration and the console script are written by
 * {@link Canton2xConfig} and handed here already on disk, because the
 * participant's PostgreSQL coordinates are not known until the embedded server
 * has started.
 *
 * Readiness is the bootstrap's OWN sentinel, and this is the one place the 2.x
 * launcher is better than the 3.x one. The prototype this was ported from also
 * accepted four log greps, one of them the Canton banner - which is printed
 * before the domain starts, before the participant connects and before any DAR
 * is uploaded. A stack reported ready there is ready in name only, and the
 * first client call fails wearing a disguise. Only the sentinel and the
 * participant id file count here.
 *
 * Author Claude/bentzn
 */
public class Canton2xProcess extends CantonProcess {

    private static final int N_MAJOR = 2;

    private final CantonInstallation installation;
    private final List<Path> lstFileConf;
    private final Path fileBootstrap;
    private final Path fileParticipantId;
    private final int nHeapMb;


    /**
     * @param installation the Canton to launch; must be 2.x
     * @param dirWork where the log, the script and Canton's own scratch go
     * @param lstFileConf the configuration files, in order; the first is the
     *        topology and any later one overlays it
     * @param fileBootstrap the console script to run on start
     * @param nHeapMb the JVM heap, or 0 to leave the JVM default alone
     * @throws IllegalArgumentException when the installation is not 2.x or
     *         carries no runtime jar
     */
    public Canton2xProcess(CantonInstallation installation, Path dirWork, List<Path> lstFileConf,
            Path fileBootstrap, int nHeapMb) {
        super("canton-2x", dirWork);

        if (installation == null)
            throw new IllegalArgumentException("installation is required");
        if (installation.version().major() != N_MAJOR)
            throw new IllegalArgumentException("this is the 2.x daemon launcher and "
                    + installation.version() + " is not 2.x; 3.x uses the sandbox subcommand");
        if (!installation.hasRuntime())
            throw new IllegalArgumentException(
                    "no Canton runtime jar was found under " + installation.dirHome());
        if (lstFileConf == null || lstFileConf.isEmpty())
            throw new IllegalArgumentException("2.x has no configuration of its own, so at least"
                    + " the topology file is required");
        if (fileBootstrap == null)
            throw new IllegalArgumentException("fileBootstrap is required");
        if (nHeapMb < 0)
            throw new IllegalArgumentException("heap must not be negative: " + nHeapMb);

        this.installation = installation;
        this.lstFileConf = List.copyOf(new ArrayList<>(lstFileConf));
        this.fileBootstrap = fileBootstrap.toAbsolutePath().normalize();
        this.fileParticipantId =
                workingDir().resolve(Canton2xConfig.STR_PARTICIPANT_ID_FILE);
        this.nHeapMb = nHeapMb;
    }


    public List<Path> lstFileConf() {
        return lstFileConf;
    }


    public Path fileBootstrap() {
        return fileBootstrap;
    }


    public Path fileParticipantId() {
        return fileParticipantId;
    }


    public CantonInstallation installation() {
        return installation;
    }


    /**
     * @return the participant id the bootstrap wrote, or null before it has
     */
    public String participantId() {
        try {
            if (!Files.isRegularFile(fileParticipantId))
                return null;
            String strId = Files.readString(fileParticipantId).strip();
            return strId.isEmpty() ? null : strId;
        }
        catch (IOException ex) {
            return null;
        }
    }


    @Override
    public synchronized void start() {
        // The previous run's id file would report this one onboarded before the
        // domain had started.
        try {
            Files.deleteIfExists(fileParticipantId);
        }
        catch (IOException ex) {
            throw new ProcessException("could not clear the stale participant id file "
                    + fileParticipantId, ex);
        }
        super.start();
    }


    @Override
    protected boolean isReadyLine(String strLine) {
        return strLine.contains(Canton2xConfig.STR_READY);
    }


    @Override
    protected boolean isReadyOutOfBand() {
        return participantId() != null;
    }


    @Override
    protected List<String> buildCommand() throws IOException {
        for (Path fileConf : lstFileConf) {
            if (!Files.isRegularFile(fileConf))
                throw new IOException("configuration file not found: " + fileConf);
        }
        if (!Files.isRegularFile(fileBootstrap))
            throw new IOException("bootstrap script not found: " + fileBootstrap);

        JvmCommand cmd = JvmCommand.ofJar(installation.fileRuntime())
                .heapMb(nHeapMb)
                .arg("daemon");

        for (Path fileConf : lstFileConf) {
            cmd.arg("-c", fileConf);
        }

        cmd.arg("--bootstrap", fileBootstrap);
        // 2.x KEEPS --no-tty. See Canton3xDaemonProcess for why the 3.x daemon
        // does not: the two are different decisions about different binaries,
        // not one setting with an exception.
        cmd.arg("--no-tty");
        cmd.arg("--log-file-name", fileLog());
        return cmd.build();
    }
}
