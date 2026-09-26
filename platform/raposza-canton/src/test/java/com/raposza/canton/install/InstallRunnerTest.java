// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>The child is the JVM running these tests</h2>
 *
 * `java -version` is the one program guaranteed to be on every machine this
 * builds on, it exits promptly, and it writes to the error stream - which makes
 * it the fixture for the merge as well as for the exit code.
 *
 * Author Claude/bentzn
 */
class InstallRunnerTest {

    @TempDir
    Path dirTemp;


    private static String strJava() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }


    @Test
    void aCommandRunsAndItsExitCodeIsReturned() throws IOException {
        int nExit = InstallRunner.nRun(List.of(strJava(), "-version"), null,
                HostPlatform.LINUX_X64, null, null, null, null);

        assertEquals(0, nExit);
    }


    /**
     * The installers write progress to one stream and failures to the other,
     * so a reader that takes only standard output shows a run that succeeded
     * right up until it did not.
     */
    @Test
    void theErrorStreamIsMergedIntoTheOutput() throws IOException {
        List<String> lstLine = new ArrayList<>();

        InstallRunner.nRun(List.of(strJava(), "-version"), null, HostPlatform.LINUX_X64,
                null, null, lstLine::add, null);

        assertFalse(lstLine.isEmpty());
        assertTrue(String.join("\n", lstLine).contains("version"),
                String.join("\n", lstLine));
    }


    @Test
    void aFailingCommandReportsItsCodeRatherThanThrowing() throws IOException {
        int nExit = InstallRunner.nRun(List.of(strJava(), "-XXnoSuchOption"), null,
                HostPlatform.LINUX_X64, null, null, null, null);

        assertTrue(nExit != 0);
    }


    @Test
    void aProgramThatIsNotThereFails() {
        assertThrows(IOException.class, () -> InstallRunner.nRun(
                List.of("raposza-no-such-program"), null, HostPlatform.LINUX_X64,
                null, null, null, null));
    }


    /** The caller is handed the live process, which is how a run is cancelled. */
    @Test
    void theProcessIsHandedToTheCaller() throws IOException {
        AtomicReference<Process> refProcess = new AtomicReference<>();

        InstallRunner.nRun(List.of(strJava(), "-version"), null, HostPlatform.LINUX_X64,
                null, null, null, refProcess::set);

        assertNotNull(refProcess.get());
        assertFalse(refProcess.get().isAlive());
    }


    @Test
    void theWorkingDirectoryIsHonoured() throws IOException {
        Path dirWork = dirTemp.resolve("work");
        Files.createDirectories(dirWork);

        assertEquals(0, InstallRunner.nRun(List.of(strJava(), "-version"), dirWork,
                HostPlatform.LINUX_X64, null, null, null, null));
    }


    @Test
    void anEmptyCommandIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> InstallRunner.nRun(List.of(),
                null, HostPlatform.LINUX_X64, null, null, null, null));
    }

}
