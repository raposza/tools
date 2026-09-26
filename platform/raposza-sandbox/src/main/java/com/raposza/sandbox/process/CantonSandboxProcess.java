// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.process;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.CantonLaunchTable;
import com.raposza.canton.jdbc.PgShim;
import com.raposza.canton.process.CantonProcess;
import com.raposza.canton.topology.SandboxPorts;
import com.raposza.runtime.process.ProcessException;
import com.raposza.sandbox.topology.SandboxSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A Canton 3.x sandbox stack: participant, sequencer and mediator, bootstrapped
 * and serving, from one process.
 *
 * The launcher is the Canton jar's own `sandbox` subcommand. It exists in 3.4
 * and 3.5 and NOT in 2.x, whose usage line offers only daemon, run and
 * generate - which is why this class refuses anything below 3 rather than
 * producing a command that would fail obscurely. The 2.x column is
 * {@link com.raposza.canton.process.Canton2xProcess}, which uses `daemon -c conf --bootstrap script` and a
 * topology this project writes.
 *
 * Every path handed to Canton is absolute. Relative ones are resolved against
 * the process's working directory rather than the caller's, and the resulting
 * CANNOT_READ_CONFIG_FILES arrives before anything is parsed - a failure that
 * reads like a verdict on the configuration when it is a verdict on the path.
 *
 * <h2>No `--bootstrap` here</h2>
 *
 * The subcommand REFUSES it - "bootstrap script cannot be defined together
 * with the 'sandbox' command", measured on 3.5.11 - because it has no console
 * to run one in. The option is declared globally in `--help` and excluded per
 * subcommand with nothing in the help text saying so, which is exactly how it
 * came to be added here and then removed again.
 *
 * Console work belongs to {@link com.raposza.canton.process.CantonConsoleProcess}, a second, short-lived
 * process that attaches once this one is serving.
 *
 * Readiness has two independent signals. Canton prints a ready line, and it
 * writes the port file only once the stack is serving. The file is checked
 * because a log phrase is a wording that can change between patch releases.
 *
 * Author Claude/bentzn
 */
public class CantonSandboxProcess extends CantonProcess {

    /** Printed by 3.4.11 and 3.5.11 when the stack is up. */
    private static final String STR_READY = "Canton sandbox is ready";

    /** The line that precedes it, kept as a fallback across rewordings. */
    private static final String STR_LISTENING = "sandbox listening at ports";

    private static final String STR_PORT_FILE = "ports.json";

    private static final int N_MAJOR_MIN = 3;

    /** The subcommand this launcher is built on. */
    private static final String STR_COMMAND = "sandbox";

    private final CantonInstallation installation;
    private final SandboxSpec spec;
    private final List<Path> lstFileConf;


    /**
     * @param installation the Canton to launch; must be 3.x
     * @param spec what to launch
     * @param dirWork where the port file, the log and Canton's own scratch go
     * @throws IllegalArgumentException when the installation is below 3.0 or
     *         carries no runtime jar
     */
    public CantonSandboxProcess(CantonInstallation installation, SandboxSpec spec, Path dirWork) {
        this(installation, spec, dirWork, List.of());
    }


    /**
     * @param installation the Canton to launch; must be 3.x
     * @param spec what to launch
     * @param dirWork where the port file, the log and Canton's own scratch go
     * @param lstFileConf overlays, applied in order over the subcommand's own
     *        configuration; a later file wins where two set the same key
     * @throws IllegalArgumentException when the installation is below 3.0 or
     *         carries no runtime jar
     */
    public CantonSandboxProcess(CantonInstallation installation, SandboxSpec spec, Path dirWork,
            List<Path> lstFileConf) {
        super("canton-sandbox", dirWork);

        if (installation == null || spec == null)
            throw new IllegalArgumentException("installation and spec are required");
        if (!hasSandbox(installation))
            throw new IllegalArgumentException(
                    "the sandbox subcommand does not exist on " + installation.version()
                            + "; it needs the daemon launcher and a hand-written topology");
        if (!installation.hasRuntime())
            throw new IllegalArgumentException(
                    "no Canton runtime jar was found under " + installation.dirHome());

        this.installation = installation;
        this.spec = spec;
        this.lstFileConf = lstFileConf == null
                ? List.of()
                : List.copyOf(new ArrayList<>(lstFileConf));
    }


    /**
     * Whether this installation has the subcommand, MEASURED where possible.
     *
     * The guard was {@code major() < 3} and that is wrong at the bottom of the
     * line: {@link CantonLaunchTable} records the subcommand appearing at
     * 3.4.4, so a 3.0 install would have been accepted here and would have
     * failed later against a usage message. Where the table has no row - a
     * version nobody has asked for its own usage text - the major-version rule
     * is kept, because an unmeasured version is UNKNOWN rather than refused.
     *
     * @param installation the installation to test
     * @return whether the sandbox subcommand can be expected
     */
    private static boolean hasSandbox(CantonInstallation installation) {
        return CantonLaunchTable.hasCommand(installation.version(), STR_COMMAND)
                .orElseGet(() -> installation.version().major() >= N_MAJOR_MIN);
    }


    public List<Path> lstFileConf() {
        return lstFileConf;
    }


    public CantonInstallation installation() {
        return installation;
    }


    public SandboxSpec spec() {
        return spec;
    }


    public Path filePorts() {
        return workingDir().resolve(STR_PORT_FILE);
    }


    public int portLedgerApi() {
        return spec.ports().nPortLedgerApi();
    }


    public int portJsonApi() {
        return spec.ports().nPortJsonApi();
    }


    @Override
    public synchronized void start() {
        // A port file left by the previous run would report the new one ready
        // before it had bound anything.
        try {
            Files.deleteIfExists(filePorts());
        }
        catch (IOException ex) {
            throw new ProcessException("could not clear the stale port file " + filePorts(), ex);
        }
        super.start();
    }


    @Override
    protected boolean isReadyLine(String strLine) {
        return strLine.contains(STR_READY) || strLine.contains(STR_LISTENING);
    }


    @Override
    protected boolean isReadyOutOfBand() {
        try {
            Path file = filePorts();
            return Files.isRegularFile(file) && Files.size(file) > 0L;
        }
        catch (IOException ex) {
            return false;
        }
    }


    @Override
    protected List<String> buildCommand() throws IOException {
        for (Path fileDar : spec.lstFileDar()) {
            if (!Files.isRegularFile(fileDar))
                throw new IOException("DAR not found: " + fileDar);
        }
        for (Path fileConf : lstFileConf) {
            if (!Files.isRegularFile(fileConf))
                throw new IOException("configuration overlay not found: " + fileConf);
        }

        SandboxPorts ports = spec.ports();
        List<String> lstCommand = new ArrayList<>();

        lstCommand.add("java");
        if (spec.nHeapMb() > 0)
            lstCommand.add("-Xmx" + spec.nHeapMb() + "m");

        // WINDOWS ONLY, and only once the class files are extracted: the
        // storage overlay names a DataSource of this project's, and `-jar`
        // IGNORES `-cp`, so a jar launched that way cannot load it. The main
        // class is read off the jar's own manifest rather than named here.
        // Everywhere else this stays the plain executable-jar form.
        if (PgShim.flagActive(workingDir(), installation.fileRuntime())) {
            lstCommand.add("-cp");
            lstCommand.add(PgShim.strClasspath(workingDir(), installation.fileRuntime()));
            lstCommand.add(PgShim.strMainClass(installation.fileRuntime()));
        }
        else {
            lstCommand.add("-jar");
            lstCommand.add(absolute(installation.fileRuntime()));
        }
        lstCommand.add("sandbox");

        // Order matters and is Canton's, not ours: for the sandbox subcommand
        // its own configuration is processed first and -c files after it, with
        // the last value winning.
        for (Path fileConf : lstFileConf) {
            lstCommand.add("-c");
            lstCommand.add(absolute(fileConf));
        }

        add(lstCommand, "--ledger-api-port", ports.nPortLedgerApi());
        add(lstCommand, "--admin-api-port", ports.nPortAdminApi());
        add(lstCommand, "--json-api-port", ports.nPortJsonApi());
        add(lstCommand, "--sequencer-public-port", ports.nPortSequencerPublic());
        add(lstCommand, "--sequencer-admin-port", ports.nPortSequencerAdmin());
        add(lstCommand, "--mediator-admin-port", ports.nPortMediatorAdmin());

        for (Path fileDar : spec.lstFileDar()) {
            lstCommand.add("--dar");
            lstCommand.add(absolute(fileDar));
        }

        if (spec.flagStaticTime())
            lstCommand.add("--static-time");
        if (spec.flagDev())
            lstCommand.add("--dev");

        lstCommand.add("--canton-port-file");
        lstCommand.add(absolute(filePorts()));
        lstCommand.add("--no-tty");
        lstCommand.add("--log-file-name");
        lstCommand.add(absolute(fileLog()));

        return lstCommand;
    }


    private static void add(List<String> lstCommand, String strFlag, int nValue) {
        lstCommand.add(strFlag);
        lstCommand.add(Integer.toString(nValue));
    }
}
