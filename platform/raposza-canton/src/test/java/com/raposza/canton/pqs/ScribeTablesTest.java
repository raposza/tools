// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.pqs;

import com.raposza.runtime.db.DatabaseException;
import com.raposza.runtime.db.PostgresCoordinates;
import com.raposza.runtime.db.SandboxPostgres;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The identifier rule and the aggregation are checked on every run; the
 * catalogue read needs a server and is opt-in:
 *
 * <pre>
 * RAPOSZA_IT_PG=1 ./test.sh
 * </pre>
 *
 * The live case does NOT use scribe. It makes its own schema and its own two
 * tables, because what is under test is whether the catalogue is read
 * correctly - and a fixture that needed scribe would be untestable on a machine
 * with no jar staged, which is most of them.
 *
 * Author Claude/bentzn
 */
class ScribeTablesTest {

    @Test
    void plainIdentifiersAreWhatPostgresWouldAcceptUnquoted() {
        assertTrue(ScribeTables.isPlainIdentifier("__contracts"));
        assertTrue(ScribeTables.isPlainIdentifier("public"));
        assertTrue(ScribeTables.isPlainIdentifier("_creates$1"));
        assertFalse(ScribeTables.isPlainIdentifier(null));
        assertFalse(ScribeTables.isPlainIdentifier(""));
        assertFalse(ScribeTables.isPlainIdentifier("has space"));
        assertFalse(ScribeTables.isPlainIdentifier("has\"quote"));
        assertFalse(ScribeTables.isPlainIdentifier("a;DROP TABLE x"));
        assertFalse(ScribeTables.isPlainIdentifier("a".repeat(64)));
    }


    @Test
    void aSchemaNameThatIsNotAnIdentifierIsRefusedBeforeConnecting() {
        // No server is running in this test. Reaching the refusal proves the
        // check happens before the connection, which is the point of it.
        PostgresCoordinates pg =
                new PostgresCoordinates("localhost", 1, "db", "user", "");
        assertThrows(DatabaseException.class, () -> ScribeTables.read(pg, "a\"; DROP SCHEMA b"));
    }


    @Test
    void nullCoordinatesAreAProgrammingError() {
        assertThrows(IllegalArgumentException.class, () -> ScribeTables.schemas(null));
    }


    @Test
    void nonEmptyAndTotalAgreeWithTheEntries() {
        List<ScribeTables.TableCount> lstCount = List.of(
                new ScribeTables.TableCount("pqs", "a", 0L),
                new ScribeTables.TableCount("pqs", "b", 3L),
                new ScribeTables.TableCount("pqs", "c", 4L));

        assertEquals(7L, ScribeTables.cntRowsTotal(lstCount));
        assertEquals(2, ScribeTables.nonEmpty(lstCount).size());
        assertEquals(0L, ScribeTables.cntRowsTotal(List.of()));
        assertTrue(ScribeTables.nonEmpty(List.of()).isEmpty());
    }


    @Test
    void describeNamesEveryTableAndItsCount() {
        String strText = ScribeTables.describe(List.of(
                new ScribeTables.TableCount("pqs", "__contracts", 0L),
                new ScribeTables.TableCount("pqs", "__transactions", 2L)));

        assertTrue(strText.contains("pqs.__contracts = 0"), strText);
        assertTrue(strText.contains("pqs.__transactions = 2"), strText);
        assertEquals("(no tables)", ScribeTables.describe(List.of()));
        assertEquals("(no tables)", ScribeTables.describe(null));
    }


    @Test
    void aNegativeCountIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScribeTables.TableCount("pqs", "t", -1L));
    }


    @Test
    @EnabledIfEnvironmentVariable(named = "RAPOSZA_IT_PG", matches = ".+")
    void readsTheCatalogueAndCountsExactly() throws Exception {
        try (SandboxPostgres postgres = new SandboxPostgres()) {
            postgres.start();
            PostgresCoordinates pg = postgres.ensureDatabase("scribetables_it");

            try (Connection conn = DriverManager.getConnection(pg.jdbcUrl(), pg.strUser(),
                    pg.strPassword());
                    Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE SCHEMA probe");
                stmt.execute("CREATE TABLE probe.empty_one (n int)");
                stmt.execute("CREATE TABLE probe.two_rows (n int)");
                stmt.execute("INSERT INTO probe.two_rows VALUES (1), (2)");
                // A view is not a base table and must not be counted.
                stmt.execute("CREATE VIEW probe.a_view AS SELECT * FROM probe.two_rows");
            }

            assertTrue(ScribeTables.schemas(pg).contains("probe"),
                    ScribeTables.schemas(pg).toString());

            List<ScribeTables.TableCount> lstCount = ScribeTables.read(pg, "probe");
            assertEquals(2, lstCount.size(), ScribeTables.describe(lstCount));
            assertEquals("empty_one", lstCount.get(0).strTable());
            assertEquals(0L, lstCount.get(0).cntRows());
            assertEquals("two_rows", lstCount.get(1).strTable());
            assertEquals(2L, lstCount.get(1).cntRows());
            assertEquals(2L, ScribeTables.cntRowsTotal(lstCount));
            assertEquals(1, ScribeTables.nonEmpty(lstCount).size());

            // A schema that does not exist is empty, not an error: "scribe has
            // not migrated yet" and "scribe migrated and wrote nothing" are
            // both legitimate states to observe.
            assertTrue(ScribeTables.read(pg, "no_such_schema").isEmpty());
        }
    }


    @Test
    void countingNamedTablesIgnoresTheBookkeepingOnes() {
        // The shape that produced a false green: five bookkeeping tables hold
        // rows the moment a migration finishes, so a schema total is always
        // non-zero and answers the wrong question.
        List<ScribeTables.TableCount> lstCount = List.of(
                new ScribeTables.TableCount("pqs", "flyway_schema_history", 42L),
                new ScribeTables.TableCount("pqs", "__exercise_tpe", 18L),
                new ScribeTables.TableCount("pqs", "__watermark", 1L),
                new ScribeTables.TableCount("pqs", "__transactions", 0L),
                new ScribeTables.TableCount("pqs", "__contracts", 0L));

        assertEquals(61L, ScribeTables.cntRowsTotal(lstCount));
        assertEquals(0L, ScribeTables.cntRowsIn(lstCount,
                Set.of("__transactions", "__contracts", "__events", "__exercises")));
    }


    @Test
    void anAbsentNamedTableContributesNothingRatherThanFailing() {
        List<ScribeTables.TableCount> lstCount =
                List.of(new ScribeTables.TableCount("pqs", "__transactions", 3L));

        assertEquals(3L, ScribeTables.cntRowsIn(lstCount, Set.of("__transactions", "__nope")));
        assertEquals(0L, ScribeTables.cntRowsIn(null, Set.of("__transactions")));
        assertEquals(0L, ScribeTables.cntRowsIn(lstCount, null));
    }


    @Test
    void dumpRefusesNamesItWouldHaveToInterpolate() {
        PostgresCoordinates pg = new PostgresCoordinates("localhost", 1, "db", "user", "");
        assertThrows(DatabaseException.class, () -> ScribeTables.dump(pg, "a b", "t", 1));
        assertThrows(DatabaseException.class, () -> ScribeTables.dump(pg, "s", "a\"b", 1));
        assertThrows(IllegalArgumentException.class, () -> ScribeTables.dump(pg, "s", "t", 0));
    }

}
