// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * How far a `dpm install` has got, read off the disk - dpm prints one line per
 * component and nothing while the bytes move.
 *
 * <h2>Where the bytes land - MEASURED 2026-09-23</h2>
 *
 * Watched at his console through the pull of `canton-open-source 3.5.17`:
 * the component grows as ONE file under
 * `&lt;dpm home&gt;/cache/oci-layout/ingest/&lt;digest&gt;_&lt;n&gt;`, 10 MB to
 * 268 MB in 20 s steps, and is moved to `blobs/sha256/&lt;digest&gt;` - 283,652,117
 * bytes - when it is whole. A copy grows in `/tmp/oras_file_*` beside it; the
 * ingest file is read because it sits under the root this project already
 * knows.
 *
 * <h2>Only a file being written counts</h2>
 *
 * An ingest file left behind by an interrupted pull is as large as it got and
 * never grows again. A file is counted only when it was written within
 * {@link #N_MS_FRESH}, so a leftover cannot pose as progress.
 *
 * <h2>No total</h2>
 *
 * The component's size is not printed by dpm and was not measured anywhere
 * else, so the figure is how much has arrived and nothing more.
 *
 * Author Claude/bentzn
 */
final class DpmPullWatch {

    /** A file written longer ago than this is not being pulled. */
    static final long N_MS_FRESH = 5000L;

    static final long N_MS_POLL = 1000L;

    private DpmPullWatch() {
    }


    /**
     * @param dirDpm the dpm home
     * @return where a component grows while it is pulled
     */
    static Path dirIngest(Path dirDpm) {
        return dirDpm.resolve("cache").resolve("oci-layout").resolve("ingest");
    }


    /**
     * @param dirDpm the dpm home
     * @param nMsNow the clock, in milliseconds
     * @return the size of the largest ingest file written within
     *         {@link #N_MS_FRESH}, or 0 when there is none
     */
    static long nBytesPulling(Path dirDpm, long nMsNow) {
        Path dirIngest = dirIngest(dirDpm);
        if (!Files.isDirectory(dirIngest))
            return 0L;

        long nMax = 0L;
        try (Stream<Path> strm = Files.list(dirIngest)) {
            for (Path file : strm.toList()) {
                try {
                    if (!Files.isRegularFile(file)
                            || nMsNow - Files.getLastModifiedTime(file).toMillis() > N_MS_FRESH)
                        continue;
                    nMax = Math.max(nMax, Files.size(file));
                }
                catch (IOException ex) {
                    // MOVED TO blobs/ BETWEEN THE LISTING AND THE READ - done, not growing.
                }
            }
        }
        catch (IOException ex) {
            return 0L;
        }
        return nMax;
    }


    /**
     * @param strPulling the pull line dpm printed
     * @param nBytes how much has arrived
     * @return the line with the figure after it
     */
    static String strLine(String strPulling, long nBytes) {
        return strPulling + " " + nBytes / ToolchainInstall.N_BYTES_MIB + " MB";
    }


    /**
     * Polls until interrupted, telling `lineProgress` the current pull's figure.
     *
     * @param dirDpm the dpm home
     * @param pulling the pull line in progress, or null between pulls
     * @param lineProgress told the figure
     * @return the thread, started
     */
    static Thread start(Path dirDpm, Supplier<String> pulling, Consumer<String> lineProgress) {
        Thread thread = new Thread(() -> {
            long nLast = -1L;
            while (!Thread.currentThread().isInterrupted()) {
                String strPulling = pulling.get();
                long nBytes = strPulling == null ? 0L : nBytesPulling(dirDpm, System.currentTimeMillis());
                long nMb = nBytes / ToolchainInstall.N_BYTES_MIB;
                if (strPulling != null && nBytes > 0 && nMb != nLast) {
                    nLast = nMb;
                    lineProgress.accept(strLine(strPulling, nBytes));
                }
                try {
                    Thread.sleep(N_MS_POLL);
                }
                catch (InterruptedException ex) {
                    return;
                }
            }
        }, "raposza-dpm-pull-watch");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
