// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Asks a scribe.jar what it is, by running it.
 *
 * The authoritative answer to "which PQS is this" comes from the binary, not
 * from a filename, a directory or a paired component's version. Staged copies
 * record the banner in VERSION.txt so the common path needs no process at all;
 * this is what produced that file and what re-checks it.
 *
 * Author Claude/bentzn
 */
public final class ScribeProbe {

    private static final Duration TIMEOUT_DEFAULT = Duration.ofSeconds(60);


    private ScribeProbe() {
    }


    /**
     * @param fileJar the scribe.jar to run
     * @return its parsed banner
     * @throws InstallException when the jar is missing, the process fails to
     *         start, times out, or prints nothing recognisable
     */
    public static ScribeBanner probe(Path fileJar) {
        return probe(fileJar, TIMEOUT_DEFAULT, "java");
    }


    /**
     * @param fileJar the scribe.jar to run
     * @param timeout how long to wait before giving up and destroying it
     * @param strJavaExec the java executable to run it with
     * @return its parsed banner
     * @throws InstallException when the jar is missing, the process fails to
     *         start, times out, or prints nothing recognisable
     */
    public static ScribeBanner probe(Path fileJar, Duration timeout, String strJavaExec) {
        if (fileJar == null || !Files.isRegularFile(fileJar))
            throw new InstallException("no scribe jar at: " + fileJar);

        List<String> lstCommand = new ArrayList<>();
        lstCommand.add(strJavaExec);
        lstCommand.add("-jar");
        lstCommand.add(fileJar.toAbsolutePath().toString());
        lstCommand.add("--version");

        Process proc = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(lstCommand);
            builder.redirectErrorStream(true);
            proc = builder.start();

            String strOutput = readAll(proc.getInputStream());
            if (!proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly();
                throw new InstallException("scribe --version did not finish within " + timeout
                        + ": " + fileJar);
            }
            return ScribeBanner.parse(strOutput);
        }
        catch (IOException ex) {
            throw new InstallException("could not run scribe --version: " + fileJar, ex);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InstallException("interrupted while probing: " + fileJar, ex);
        }
        finally {
            if (proc != null && proc.isAlive())
                proc.destroyForcibly();
        }
    }


    private static String readAll(InputStream strm) throws IOException {
        return new String(strm.readAllBytes(), StandardCharsets.UTF_8);
    }
}
