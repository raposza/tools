// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Runs one external command to completion and streams what it says.
 *
 * <h2>Why this is not `ManagedProcess`</h2>
 *
 * That class supervises a node: it starts something that stays up, watches for
 * a readiness line, and is stopped later. An installer is the opposite shape -
 * it runs, prints, and exits, and its exit code is the whole answer. Fitting
 * one into the other would mean a readiness predicate that is never satisfied
 * and a stop that never happens.
 *
 * <h2>The two streams are merged</h2>
 *
 * The vendor installers write progress to one and errors to the other, and the
 * interleaving is the story. A window showing only one of them shows either a
 * progress bar with no failure or a failure with no context.
 *
 * <h2>Nothing here decides WHAT to run</h2>
 *
 * The command is composed elsewhere and handed in whole, so this class carries
 * no knowledge of archives, versions or vendors and can be asserted against any
 * program at all.
 *
 * Author Claude/bentzn
 */
public final class InstallRunner {

    private static final Logger log = LoggerFactory.getLogger(InstallRunner.class);


    private InstallRunner() {
    }


    /**
     * Blocks until the command exits.
     *
     * @param lstCommand the command and its arguments; never null or empty
     * @param dirWorking the working directory, or null for this process's own
     * @param platform the platform, for the environment overrides; never null
     * @param dirStage a directory the installer may expand into, or null
     * @param dirDpmHome the DPM root to install into, or null
     * @param lineOut called once per output line, both streams merged, or null
     * @param onStart called with the live process as soon as it starts, so a
     *        caller holding it can destroy it; or null
     * @return the exit code
     * @throws IOException when the program cannot be started or the wait is
     *         interrupted
     */
    public static int nRun(List<String> lstCommand, Path dirWorking, HostPlatform platform,
            Path dirStage, Path dirDpmHome, Consumer<String> lineOut,
            Consumer<Process> onStart) throws IOException {
        if (lstCommand == null || lstCommand.isEmpty())
            throw new IllegalArgumentException("a command is required");

        ProcessBuilder builder = new ProcessBuilder(lstCommand);
        builder.redirectErrorStream(true);
        if (dirWorking != null)
            builder.directory(dirWorking.toFile());
        InstallEnv.apply(builder, platform, dirStage, dirDpmHome);

        log.info("running {}", String.join(" ", lstCommand));
        Process process = builder.start();
        if (onStart != null)
            onStart.accept(process);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String strLine = reader.readLine();
            while (strLine != null) {
                if (lineOut != null)
                    lineOut.accept(strLine);
                strLine = reader.readLine();
            }
        }

        try {
            return process.waitFor();
        }
        catch (InterruptedException ex) {
            // THE CHILD IS KILLED RATHER THAN ORPHANED. An installer left
            // running after its caller has given up writes into the very tree
            // the caller is about to report as failed.
            process.destroy();
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while running "
                    + String.join(" ", lstCommand), ex);
        }
    }

}
