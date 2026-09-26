// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * DOES THIS BUNDLE'S `splice-node` START AT ALL - asked once, after an install
 * and before a first start.
 *
 * <h2>Why it exists</h2>
 *
 * Splice 0.8.2 was published with a `logback.xml` its own executable cannot
 * load - canton-network/splice issue 7385, fixed by PR 7383 on the release
 * line - and it is still listed on the release page with no marker. The
 * window installed it from there on 2026-09-23, and the first start failed
 * thirteen seconds in with every gate DOWN and a `splice.log` that was never
 * written. Nothing in the release API says a release is broken, so the only
 * way to know is to run it.
 *
 * <h2>What is run, and why that and nothing cheaper</h2>
 *
 * `bin/splice-node daemon -c &lt;empty file&gt; --log-file-name &lt;file&gt;`,
 * in a temporary directory that is removed afterwards. MEASURED at his
 * console, 2026-09-23, on both bundles:
 *
 * <pre>
 *   0.8.1  exit 1    2154 ms  log file written   GENERIC_CONFIG_ERROR - Key not found: 'canton'
 *   0.8.2  exit 255   667 ms  no log file        Unable to load log configuration.
 * </pre>
 *
 * A healthy bundle gets as far as reading its configuration, refuses the empty
 * one, and has written its log by then; the broken one never configures
 * logging. `--version` and `--help` were measured first and CANNOT tell the
 * two apart - both exit 0 in about 0.4 s on either bundle, because neither
 * loads the log configuration.
 *
 * THE EXIT CODE IS NOT THE VERDICT. Both exit non-zero, for different reasons.
 * The verdict is the log file written AND the vendor's own failure line
 * absent.
 *
 * <h2>Remembered per version</h2>
 *
 * A pass writes {@link #STR_FILE_CHECKED} beside the bundle, so the two
 * seconds are spent once per version rather than on every start. A failure
 * writes nothing and is asked again next time, which is what a re-download of
 * a fixed archive needs.
 *
 * Author Claude/bentzn
 */
public final class SpliceCheck {

    /** What a bundle that passed carries in its version directory. */
    public static final String STR_FILE_CHECKED = "CHECKED.txt";

    /** The vendor's own line, verbatim from the 0.8.2 run. */
    static final String STR_LOG_FAILURE = "Unable to load log configuration";

    /** How long the check may take. 0.8.1 answered in 2.2 s. */
    private static final long N_SECONDS_TIMEOUT = 60;

    private SpliceCheck() {
    }


    /**
     * The verdict, from what the run left behind.
     *
     * @param strOutput everything the process printed
     * @param flagLogWritten whether the log file it was given exists and is
     *        not empty
     * @return null when the bundle starts, else why not
     */
    static String strWhyNot(String strOutput, boolean flagLogWritten) {
        if (strOutput != null && strOutput.contains(STR_LOG_FAILURE)) {
            return "its splice-node cannot load its own log configuration"
                    + " (\"" + STR_LOG_FAILURE + "\") - the defect Splice 0.8.2 was"
                    + " published with, canton-network/splice issue 7385";
        }
        if (!flagLogWritten)
            return "its splice-node wrote no log file, so it did not get as far as"
                    + " reading a configuration";
        return null;
    }


    /**
     * Runs the check, or answers from {@link #STR_FILE_CHECKED}.
     *
     * @param dirBundle a `splice-node` directory
     * @return null when the bundle starts, else why not
     */
    public static String strWhyNotStarts(Path dirBundle) {
        Path dirVersion = dirBundle.toAbsolutePath().normalize().getParent();
        if (dirVersion != null && Files.isRegularFile(dirVersion.resolve(STR_FILE_CHECKED)))
            return null;

        String strWhy = strWhyNotRun(dirBundle);
        if (strWhy == null && dirVersion != null) {
            try {
                Files.writeString(dirVersion.resolve(STR_FILE_CHECKED),
                        "splice-node started and wrote its log - "
                                + Instant.now().truncatedTo(ChronoUnit.SECONDS) + "\n",
                        StandardCharsets.UTF_8);
            }
            catch (IOException ex) {
                // A directory that cannot be written is checked again next time.
            }
        }
        return strWhy;
    }


    /**
     * @param dirBundle a `splice-node` directory
     * @return null when the bundle starts, else why not
     */
    static String strWhyNotRun(Path dirBundle) {
        Path fileLauncher = dirBundle.resolve("bin").resolve(SpliceInstallations.STR_BUNDLE);
        if (!Files.isExecutable(fileLauncher))
            return "there is no executable " + fileLauncher;

        Path dirTmp = null;
        try {
            dirTmp = Files.createTempDirectory("splice-check");
            Path fileConf = dirTmp.resolve("empty.conf");
            Files.writeString(fileConf, "", StandardCharsets.UTF_8);
            Path fileLog = dirTmp.resolve("check.log");
            Path fileOut = dirTmp.resolve("out.txt");

            ProcessBuilder bld = new ProcessBuilder(List.of(fileLauncher.toString(), "daemon",
                    "-c", fileConf.toString(), "--log-file-name", fileLog.toString()));
            bld.directory(dirTmp.toFile());
            bld.redirectErrorStream(true);
            bld.redirectOutput(fileOut.toFile());
            Process process = bld.start();
            if (!process.waitFor(N_SECONDS_TIMEOUT, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "its splice-node did not answer within " + N_SECONDS_TIMEOUT + " s";
            }

            String strOut = Files.isRegularFile(fileOut)
                    ? Files.readString(fileOut, StandardCharsets.ISO_8859_1) : "";
            boolean flagLog = Files.isRegularFile(fileLog) && Files.size(fileLog) > 0;
            return strWhyNot(strOut, flagLog);
        }
        catch (IOException ex) {
            return "its splice-node could not be run: " + ex.getMessage();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "the check was interrupted";
        }
        finally {
            deleteQuietly(dirTmp);
        }
    }


    private static void deleteQuietly(Path dir) {
        if (dir == null)
            return;
        try (Stream<Path> strm = Files.walk(dir)) {
            for (Path path : strm.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
        catch (IOException ex) {
            // a temporary directory the OS will clear
        }
    }
}
