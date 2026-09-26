// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.process;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercised against the JVM itself, which is the one external process a build
 * can rely on being present. The long-lived case is a single-file source
 * launch, so nothing has to be compiled or shipped for it.
 *
 * Author Claude/bentzn
 */
class ManagedProcessTest {

    private static final String STR_SLEEPER = """
            public class Sleeper {

                public static void main(String[] arrArg) throws Exception {
                    System.out.println("sleeper is ready");
                    System.out.flush();
                    Thread.sleep(120000L);
                }
            }
            """;

    @TempDir
    Path dirTemp;

    private Path fileSleeper;


    @BeforeEach
    void setUp() throws IOException {
        fileSleeper = dirTemp.resolve("Sleeper.java");
        Files.writeString(fileSleeper, STR_SLEEPER, StandardCharsets.UTF_8);
    }


    private static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }


    /** Runs `java -version`, whose output goes to stderr and is merged in. */
    private static class VersionProcess extends ManagedProcess {

        private final Path dirWork;


        VersionProcess(Path dirWork) {
            super("java-version");
            this.dirWork = dirWork;
        }


        @Override
        protected List<String> buildCommand() {
            List<String> lstCommand = new ArrayList<>();
            lstCommand.add(javaExecutable());
            lstCommand.add("-version");
            return lstCommand;
        }


        @Override
        protected Path workingDir() {
            return dirWork;
        }


        @Override
        protected boolean isReadyLine(String strLine) {
            return strLine.contains("version");
        }
    }


    /** Prints one line, then stays up until it is stopped. */
    private static final class SleeperProcess extends ManagedProcess {

        private final Path dirWork;
        private final Path fileSource;


        SleeperProcess(Path dirWork, Path fileSource) {
            super("sleeper");
            this.dirWork = dirWork;
            this.fileSource = fileSource;
        }


        @Override
        protected List<String> buildCommand() {
            List<String> lstCommand = new ArrayList<>();
            lstCommand.add(javaExecutable());
            lstCommand.add(fileSource.toAbsolutePath().toString());
            return lstCommand;
        }


        @Override
        protected Path workingDir() {
            return dirWork;
        }


        @Override
        protected boolean isReadyLine(String strLine) {
            return strLine.contains("sleeper is ready");
        }
    }


    private static final class BrokenProcess extends ManagedProcess {

        private final Path dirWork;


        BrokenProcess(Path dirWork) {
            super("broken");
            this.dirWork = dirWork;
        }


        @Override
        protected List<String> buildCommand() throws IOException {
            throw new IOException("deliberate");
        }


        @Override
        protected Path workingDir() {
            return dirWork;
        }


        @Override
        protected boolean isReadyLine(String strLine) {
            return true;
        }
    }


    @Test
    void capturesOutputAndReportsReady() {
        List<String> lstSeen = new CopyOnWriteArrayList<>();
        try (VersionProcess process = new VersionProcess(dirTemp)) {
            process.addOutputListener(lstSeen::add);
            process.start();
            assertTrue(process.awaitReady(Duration.ofSeconds(60)),
                    "no version line within 60 s: " + process.tail());
            assertFalse(process.tail().isEmpty());
            assertFalse(lstSeen.isEmpty());
        }
    }


    @Test
    void createsTheWorkingDirectory() {
        Path dirWork = dirTemp.resolve("absent").resolve("deeper");
        try (VersionProcess process = new VersionProcess(dirWork)) {
            process.start();
            process.awaitReady(Duration.ofSeconds(60));
            assertTrue(Files.isDirectory(dirWork));
        }
    }


    @Test
    void staysUpUntilStopped() {
        try (SleeperProcess process = new SleeperProcess(dirTemp, fileSleeper)) {
            process.start();
            assertTrue(process.awaitReady(Duration.ofSeconds(120)),
                    "the sleeper never reported ready: " + process.tail());
            assertTrue(process.isRunning());

            process.stop(Duration.ofSeconds(20));
            assertFalse(process.isRunning());
        }
    }


    @Test
    void refusesToStartTwice() {
        try (SleeperProcess process = new SleeperProcess(dirTemp, fileSleeper)) {
            process.start();
            assertTrue(process.awaitReady(Duration.ofSeconds(120)));
            assertThrows(ProcessException.class, process::start);
        }
    }


    @Test
    void stoppingIsIdempotentAndSoIsNeverStarting() {
        SleeperProcess process = new SleeperProcess(dirTemp, fileSleeper);
        process.stop(Duration.ofSeconds(1));
        assertFalse(process.isRunning());

        process.start();
        assertTrue(process.awaitReady(Duration.ofSeconds(120)));
        process.stop(Duration.ofSeconds(20));
        process.stop(Duration.ofSeconds(20));
        assertFalse(process.isRunning());
    }


    @Test
    void awaitReadyRefusesToWaitForSomethingThatDied() {
        // `java -version` prints and exits. Once it has, a readiness question
        // that never matched has to fail rather than run out the clock.
        try (VersionProcess process = new VersionProcess(dirTemp) {

            @Override
            protected boolean isReadyLine(String strLine) {
                return false;
            }
        }) {
            process.start();
            assertThrows(ProcessException.class, () -> process.awaitReady(Duration.ofSeconds(60)));
            assertNotNull(process.exitCode());
        }
    }


    @Test
    void aCommandThatCannotBeAssembledFailsBeforeAnythingRuns() {
        BrokenProcess process = new BrokenProcess(dirTemp);
        ProcessException ex = assertThrows(ProcessException.class, process::start);
        assertTrue(ex.getMessage().contains("broken"));
        assertFalse(process.isRunning());
    }


    @Test
    void theTailIsBoundedToWhatWasAskedFor() {
        try (VersionProcess process = new VersionProcess(dirTemp)) {
            process.start();
            process.awaitReady(Duration.ofSeconds(60));
            assertTrue(process.tail(1).size() <= 1);
            assertEquals(0, process.tail(0).size());
        }
    }
}
