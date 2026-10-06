// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kills a child process that runs past its deadline WHILE ITS OUTPUT IS STILL
 * BEING READ - 2026-10-05.
 *
 * <h2>Why a thread of its own</h2>
 *
 * The caller reads the child's merged output to the end and only then asks
 * `waitFor(timeout)`. The end of that stream is the child exiting, so a child
 * that hangs holds the reader for ever and the timeout after it is never
 * reached: "killed after N s" could not happen. This waits on the side and
 * kills when the deadline passes, which closes the stream and lets the reader
 * finish.
 *
 * <h2>The descendants first</h2>
 *
 * The toolchain launchers are scripts that start a JVM. Killing the script
 * alone leaves the JVM holding the pipe, and the reader still never ends.
 *
 * Author Claude/bentzn
 */
public final class ProcessWatchdog implements AutoCloseable {

    private final Thread thread;

    private final AtomicBoolean flagFired = new AtomicBoolean(false);


    private ProcessWatchdog(Process proc, long nSeconds) {
        this.thread = new Thread(() -> watch(proc, nSeconds), "process-watchdog");
        this.thread.setDaemon(true);
    }


    /**
     * @param proc the child, already started
     * @param nSeconds how long it may run
     * @return the watchdog, running; close it once the child's output has ended
     */
    public static ProcessWatchdog start(Process proc, long nSeconds) {
        if (proc == null)
            throw new IllegalArgumentException("a process is required");
        ProcessWatchdog watchdog = new ProcessWatchdog(proc, nSeconds);
        watchdog.thread.start();
        return watchdog;
    }


    private void watch(Process proc, long nSeconds) {
        try {
            if (proc.waitFor(nSeconds, TimeUnit.SECONDS))
                return;
        }
        catch (InterruptedException ex) {
            return;
        }
        flagFired.set(true);
        kill(proc);
    }


    /**
     * The process and everything it started, forcibly.
     *
     * @param proc the child
     */
    public static void kill(Process proc) {
        proc.descendants().forEach(ProcessHandle::destroyForcibly);
        proc.destroyForcibly();
    }


    /**
     * @return true once the deadline passed and the child was killed
     */
    public boolean isFired() {
        return flagFired.get();
    }


    /** Stops watching. A child that has not exited is left running. */
    @Override
    public void close() {
        thread.interrupt();
    }
}
