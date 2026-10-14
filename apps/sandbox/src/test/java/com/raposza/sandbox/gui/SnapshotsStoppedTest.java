// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A snapshot is copied only once PostgreSQL has let go of the cluster - the
 * founding copy of 2026-10-14 on Windows started while it had not.
 *
 * Author Claude/bentzn
 */
class SnapshotsStoppedTest {

    @TempDir
    Path dirData;


    @Test
    void noLockIsStopped() {
        assertFalse(Snapshots.isServerAlive(dirData));
        assertDoesNotThrow(() -> Snapshots.awaitStopped(dirData, Duration.ofMillis(10)));
    }


    @Test
    void aLiveProcessHoldsItAndTheWaitRefuses() throws IOException {
        writeLock(ProcessHandle.current().pid());

        assertTrue(Snapshots.isServerAlive(dirData));
        assertThrows(IllegalStateException.class,
                () -> Snapshots.awaitStopped(dirData, Duration.ofMillis(300)));
    }


    @Test
    void aDeadProcessIsALeftoverAndNotAServer() throws IOException, InterruptedException {
        // A process that has exited: its id names nothing alive.
        String strJava = ProcessHandle.current().info().command().orElseThrow();
        Process process = new ProcessBuilder(strJava, "-version").redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertTrue(process.waitFor(60, TimeUnit.SECONDS));
        writeLock(process.pid());

        assertFalse(Snapshots.isServerAlive(dirData));
    }


    @Test
    void anUnreadableLockIsHeld() throws IOException {
        Files.writeString(dirData.resolve(Snapshots.STR_FILE_POSTMASTER_PID), "");
        assertTrue(Snapshots.isServerAlive(dirData));

        Files.writeString(dirData.resolve(Snapshots.STR_FILE_POSTMASTER_PID), "not a pid\n");
        assertTrue(Snapshots.isServerAlive(dirData));
    }


    @Test
    void theWaitEndsWhenTheLockGoes() throws IOException {
        Path fileLock = writeLock(ProcessHandle.current().pid());
        Thread threadGone = new Thread(() -> {
            try {
                Thread.sleep(400L);
                Files.delete(fileLock);
            }
            catch (IOException | InterruptedException ex) {
                throw new IllegalStateException(ex);
            }
        });
        threadGone.start();

        assertDoesNotThrow(() -> Snapshots.awaitStopped(dirData, Duration.ofSeconds(30)));
    }


    @Test
    void saveCopiesAClusterNoServerHolds(@TempDir Path dirRoot) throws IOException {
        // No lock: the wait passes at once and the copy happens.
        Files.writeString(dirData.resolve(Snapshots.STR_FILE_PG_VERSION), "17\n");
        Snapshots snapshots = new Snapshots(dirRoot);
        snapshots.save("founding", dirData, new java.util.Properties());

        assertTrue(snapshots.lstNames().contains("founding"));
    }


    private Path writeLock(long nPid) throws IOException {
        Path fileLock = dirData.resolve(Snapshots.STR_FILE_POSTMASTER_PID);
        Files.writeString(fileLock, nPid + "\n" + dirData + "\n");
        return fileLock;
    }

}
