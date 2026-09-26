// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * One directory the window is given, and the three the stack gets.
 *
 * <pre>
 * &lt;run&gt;/work    overlays, logs, the status file, the bootstrap
 * &lt;run&gt;/data    the PostgreSQL cluster, WIPED at the start of every run
 * &lt;run&gt;/dars    every *.dar here is uploaded at start
 * </pre>
 *
 * The window asks for one path because three were three ways to get a stack
 * that half matched itself - a work directory from one run beside a cluster
 * from another. They move together now or not at all.
 *
 * <h2>The wipe is at START, not at stop</h2>
 *
 * Same guarantee - a run never inherits the last one's ledger - and the data is
 * still on disk after the stack comes down, which is where anyone who wants to
 * look at it will look. Wiping at stop would delete it in the second between a
 * developer seeing a result and going to inspect it.
 *
 * It also makes the cluster path safe to hand a launcher that cannot start
 * twice against the same storage: after a wipe, every start is a first start.
 *
 * The cluster directory itself is NEVER created here. {@link
 * com.raposza.runtime.db.SandboxPostgres} says in its own comment that
 * which state of the data directory Zonky reads to decide on `initdb` is not
 * established, so it creates the PARENT and stops. A wipe that removed the
 * directory and then put an empty one back would be making exactly the bet that
 * class refuses to make.
 *
 * Author Claude/bentzn
 */
public final class RunDirectory {

    public static final String STR_DIR_WORK = "work";

    public static final String STR_DIR_DATA = "data";

    public static final String STR_DIR_DARS = "dars";

    private final Path dirRun;


    /**
     * @param dirRunNew the run directory; never null
     */
    public RunDirectory(Path dirRunNew) {
        if (dirRunNew == null)
            throw new IllegalArgumentException("a run directory is required");
        this.dirRun = dirRunNew.toAbsolutePath().normalize();
    }


    public Path dirRun() {
        return dirRun;
    }


    public Path dirWork() {
        return dirRun.resolve(STR_DIR_WORK);
    }


    public Path dirData() {
        return dirRun.resolve(STR_DIR_DATA);
    }


    public Path dirDars() {
        return dirRun.resolve(STR_DIR_DARS);
    }


    /**
     * Deletes the cluster and makes sure all three exist.
     *
     * @throws IllegalStateException when the data directory cannot be removed,
     *         because starting on top of a cluster that was meant to be gone
     *         fails later and further away
     */
    public void prepare() {
        wipeData();
        try {
            Files.createDirectories(dirWork());
            Files.createDirectories(dirDars());
        }
        catch (IOException ex) {
            throw new IllegalStateException("could not create the run directory at " + dirRun
                    + ": " + ex.getMessage(), ex);
        }
    }


    /**
     * @return whether there is at least one DAR to upload
     */
    public boolean hasDars() {
        try (Stream<Path> strmPath = Files.list(dirDars())) {
            return strmPath.anyMatch(path -> path.getFileName().toString().endsWith(".dar"));
        }
        catch (IOException ex) {
            return false;
        }
    }


    private void wipeData() {
        Path dirData = dirData();
        if (!Files.exists(dirData))
            return;

        try (Stream<Path> strmPath = Files.walk(dirData)) {
            // Deepest first, because a directory does not delete while it has
            // contents. Collected before deleting: the walk is lazy and a
            // stream that deletes what it is walking is undefined.
            for (Path path : strmPath.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
        catch (IOException ex) {
            throw new IllegalStateException("could not wipe the cluster at " + dirData + ": "
                    + ex.getMessage(), ex);
        }
    }
}
