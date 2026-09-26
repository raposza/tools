// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.CantonInstallations;
import com.raposza.canton.install.Edition;
import com.raposza.canton.install.VersionId;
import com.raposza.runtime.process.ProcessException;
import com.raposza.sandbox.process.CantonSandboxProcess;
import com.raposza.sandbox.topology.SandboxSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * A headless 3.x sandbox: start it, wait for it, use it, stop it.
 *
 * This is the class that has to work with no window open. The GUI is a consumer
 * of it and never a prerequisite.
 *
 * Resolution is by version AND edition, because a version alone does not
 * identify an installation - the same 3.4.11 exists here as an open-source
 * build under the Daml Assistant and an enterprise one in the DPM cache, and
 * they differ in capability and licence.
 *
 * Author Claude/bentzn
 */
public final class CantonSandbox implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CantonSandbox.class);

    private static final Duration TIMEOUT_READY = Duration.ofMinutes(5);

    private static final Duration TIMEOUT_STOP = Duration.ofSeconds(60);

    private final CantonSandboxProcess process;


    public CantonSandbox(CantonInstallation installation, SandboxSpec spec, Path dirWork) {
        this.process = new CantonSandboxProcess(installation, spec, dirWork);
    }


    /**
     * @param version the exact Canton version
     * @param edition the required edition, or null for any
     * @param spec what to launch
     * @param dirWork where the port file, the log and Canton's scratch go
     * @return a sandbox that has not been started
     * @throws ProcessException when that version is not installed
     */
    public static CantonSandbox of(VersionId version, Edition edition, SandboxSpec spec,
            Path dirWork) {
        Optional<CantonInstallation> optInstallation =
                CantonInstallations.ofDefaults().find(version, edition);
        if (optInstallation.isEmpty())
            throw new ProcessException("Canton " + version
                    + (edition == null ? "" : " " + edition) + " is not installed");
        return new CantonSandbox(optInstallation.get(), spec, dirWork);
    }


    public CantonSandboxProcess process() {
        return process;
    }


    public int portLedgerApi() {
        return process.portLedgerApi();
    }


    public int portJsonApi() {
        return process.portJsonApi();
    }


    public boolean isRunning() {
        return process.isRunning();
    }


    /**
     * Starts the stack and waits for it to serve.
     *
     * @param timeout how long to wait for readiness
     * @throws ProcessException when it dies or does not become ready in time
     */
    public void start(Duration timeout) {
        process.start();
        if (!process.awaitReady(timeout)) {
            String strTail = String.join("\n", process.tail(40));
            process.stop(TIMEOUT_STOP);
            throw new ProcessException("the sandbox was not ready within " + timeout
                    + "; last output:\n" + strTail);
        }
        // Readiness answers "did it say so", which a process that printed the
        // line and then fell over also does.
        if (!process.isRunning())
            throw new ProcessException("the sandbox reported ready and then exited; last output:\n"
                    + String.join("\n", process.tail(40)));

        log.info("sandbox ready: ledger-api {}, json-api {}", portLedgerApi(), portJsonApi());
    }


    public void start() {
        start(TIMEOUT_READY);
    }


    public void stop() {
        process.stop(TIMEOUT_STOP);
    }


    @Override
    public void close() {
        stop();
    }
}
