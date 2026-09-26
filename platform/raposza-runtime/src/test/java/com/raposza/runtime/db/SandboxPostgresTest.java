// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The name rules and the refusals are checked on every run. Starting a real
 * server is opt-in, because Zonky unpacks and runs a PostgreSQL binary and
 * that is a different kind of cost from the rest of this module's tests:
 *
 * <pre>
 * RAPOSZA_IT_PG=1 ./test.sh
 * </pre>
 *
 * Author Claude/bentzn
 */
class SandboxPostgresTest {

    /** Under target, so a clean build takes the clusters with it. */
    private static final Path DIR_DATA = Path.of("target", "sandbox-pg-persist");


    @Test
    void refusesADatabaseNameThatWouldNotSurviveTheServer() {
        SandboxPostgres postgres = new SandboxPostgres();

        // Unquoted identifiers are folded to lower case, so an upper-case name
        // written into Canton's configuration would not match the database
        // that was actually created.
        assertThrows(IllegalArgumentException.class, () -> postgres.coordinatesFor("Raposza"));
        assertThrows(IllegalArgumentException.class, () -> postgres.coordinatesFor("2fast"));
        assertThrows(IllegalArgumentException.class, () -> postgres.coordinatesFor("drop db"));
        assertThrows(IllegalArgumentException.class,
                () -> postgres.coordinatesFor("x\"; DROP DATABASE postgres; --"));
        assertThrows(IllegalArgumentException.class, () -> postgres.coordinatesFor(null));

        assertEquals("raposza_participant",
                postgres.coordinatesFor("raposza_participant").strDatabase());
    }


    @Test
    void refusesToCreateADatabaseWhileNotRunning() {
        SandboxPostgres postgres = new SandboxPostgres();
        assertFalse(postgres.isRunning());
        assertThrows(DatabaseException.class, () -> postgres.ensureDatabase("raposza_participant"));
    }


    @Test
    void refusesAPortThatIsAlreadyTaken() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            SandboxPostgres postgres =
                    new SandboxPostgres(socket.getLocalPort(), Duration.ofSeconds(5));
            assertThrows(IllegalStateException.class, postgres::start);
        }
    }


    @Test
    void refusesAnImpossibleConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new SandboxPostgres(0, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> new SandboxPostgres(5432, Duration.ZERO));
    }


    /**
     * Temporary is the default, and it stays the default: every existing
     * caller gets a cluster that goes away with the process, which is what a
     * test wants and what a developer restarting a sandbox does not.
     */
    @Test
    void isTemporaryUnlessADirectoryIsGiven() {
        SandboxPostgres temporary = new SandboxPostgres();
        assertFalse(temporary.isPersistent());
        assertNull(temporary.dirData());

        SandboxPostgres persistent =
                new SandboxPostgres(5432, Duration.ofMinutes(1), Path.of("pgdata"));
        assertTrue(persistent.isPersistent());
        assertEquals(Path.of("pgdata").toAbsolutePath().normalize(), persistent.dirData());
    }


    @Test
    @EnabledIfEnvironmentVariable(named = "RAPOSZA_IT_PG", matches = ".+")
    void startsAndCreatesDatabasesThatCanBeConnectedTo() throws Exception {
        int nPort = freePort();
        try (SandboxPostgres postgres = new SandboxPostgres(nPort, Duration.ofMinutes(3))) {
            postgres.start();
            assertTrue(postgres.isRunning());

            PostgresCoordinates pg = postgres.ensureDatabase("raposza_participant");
            // Idempotent: the second call is the one a restart makes.
            assertEquals(pg, postgres.ensureDatabase("raposza_participant"));

            try (Connection conn = DriverManager.getConnection(pg.jdbcUrl(), pg.strUser(),
                    pg.strPassword());
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
                assertTrue(rs.next());
                assertEquals("raposza_participant", rs.getString(1));
            }

            // The embedded server defaults to off and Canton's sequencer
            // writer refuses to start against that. The setting is per
            // database and applies to connections opened after it, so this
            // reads it the way Canton will.
            try (Connection conn = DriverManager.getConnection(pg.jdbcUrl(), pg.strUser(),
                    pg.strPassword());
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SHOW synchronous_commit")) {
                assertTrue(rs.next());
                assertEquals(SandboxPostgres.STR_SYNCHRONOUS_COMMIT, rs.getString(1));
            }
        }
    }


    /**
     * A ROW read back, not a directory that still exists.
     *
     * The weak version of this test - assert the data directory survived
     * close() - is a readiness assertion that cannot fail: it passes the
     * moment a
     * directory is left on disk, whether or not the cluster inside it is
     * usable and whether or not the second server is the same cluster rather
     * than a fresh `initdb` over the top. Writing a row before the restart and
     * selecting it afterwards can only pass one way.
     *
     * The port is deliberately the same on both halves. A persistent cluster
     * on a port that moves is no use to a saved DBeaver connection, which is
     * one of the two reasons the port is fixed at all.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "RAPOSZA_IT_PG", matches = ".+")
    void keepsWhatWasWrittenAcrossARestart() throws Exception {
        int nPort = freePort();
        Path dirData = DIR_DATA.resolve("run-" + System.nanoTime());

        try (SandboxPostgres first = new SandboxPostgres(nPort, Duration.ofMinutes(3), dirData)) {
            assertTrue(first.isPersistent());
            first.start();

            PostgresCoordinates pg = first.ensureDatabase("raposza_persist");
            try (Connection conn = DriverManager.getConnection(pg.jdbcUrl(), pg.strUser(),
                    pg.strPassword());
                    Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE survives (n integer)");
                stmt.execute("INSERT INTO survives VALUES (42)");
            }
        }

        assertTrue(Files.isDirectory(dirData),
                "close() took the data directory with it, so setCleanDataDirectory(false) did"
                        + " not do what the builder surface says: " + dirData);

        try (SandboxPostgres second = new SandboxPostgres(nPort, Duration.ofMinutes(3), dirData)) {
            second.start();

            PostgresCoordinates pg = second.coordinatesFor("raposza_persist");
            try (Connection conn = DriverManager.getConnection(pg.jdbcUrl(), pg.strUser(),
                    pg.strPassword());
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT n FROM survives")) {
                assertTrue(rs.next(), "the table is there and empty, so the cluster came back and"
                        + " the write did not");
                assertEquals(42, rs.getInt(1));
            }
        }
    }


    /**
     * `postmaster.pid` - its first line is the postmaster's process id, and
     * anything else there is not a number to act on.
     */
    @Test
    void readsThePidOffTheFirstLineOfThePidFile(@TempDir Path dirTmp) throws IOException {
        assertEquals(-1, SandboxPostgres.nPidOf(null));
        assertEquals(-1, SandboxPostgres.nPidOf(dirTmp));

        Path file = dirTmp.resolve(SandboxPostgres.STR_FILE_PID);
        Files.writeString(file, "4242\n" + dirTmp + "\n1727100000\n32101\n",
                StandardCharsets.US_ASCII);
        assertEquals(4242, SandboxPostgres.nPidOf(dirTmp));

        Files.writeString(file, "", StandardCharsets.US_ASCII);
        assertEquals(-1, SandboxPostgres.nPidOf(dirTmp));
        Files.writeString(file, "not a pid\n", StandardCharsets.US_ASCII);
        assertEquals(-1, SandboxPostgres.nPidOf(dirTmp));
        Files.writeString(file, "-7\n", StandardCharsets.US_ASCII);
        assertEquals(-1, SandboxPostgres.nPidOf(dirTmp));
    }


    /**
     * NOTHING THAT IS NOT A LEFT-BEHIND POSTMASTER IS STOPPED - `todo.md`
     * A-34 (d).
     *
     * A live process that is NOT PostgreSQL is named in the pid file: a child
     * JVM sleeping. It must survive, whether or not it was alive before the
     * attempt. The control can fail: a cleanup that skipped the command check
     * would stop the child and answer true.
     */
    @Test
    void stopsNothingThatIsNotAPostmasterThisStartLeft(@TempDir Path dirTmp) throws Exception {
        assertFalse(SandboxPostgres.stopLeftBehind(dirTmp, -1));

        Path file = dirTmp.resolve(SandboxPostgres.STR_FILE_PID);
        Files.writeString(file, ProcessHandle.current().pid() + "\n",
                StandardCharsets.US_ASCII);
        assertFalse(SandboxPostgres.stopLeftBehind(dirTmp, -1));

        Path fileSleep = dirTmp.resolve("Sleep.java");
        Files.writeString(fileSleep, "public class Sleep { public static void main(String[] a)"
                + " throws Exception { Thread.sleep(120000); } }\n",
                StandardCharsets.US_ASCII);
        Process proc = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin",
                "java").toString(), fileSleep.toString()).start();
        try {
            assertTrue(proc.isAlive());
            Files.writeString(file, proc.pid() + "\n", StandardCharsets.US_ASCII);
            assertEquals(proc.pid(), SandboxPostgres.nPidLive(dirTmp));

            assertFalse(SandboxPostgres.stopLeftBehind(dirTmp, proc.pid()));
            assertFalse(SandboxPostgres.stopLeftBehind(dirTmp, -1));
            assertFalse(proc.waitFor(1, TimeUnit.SECONDS), "the child was stopped");
        }
        finally {
            proc.destroyForcibly();
        }
    }


    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
