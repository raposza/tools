// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import com.raposza.runtime.db.PostgresCoordinates;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Author Claude/bentzn
 */
class StorageOverlayTest {

    private static final PostgresCoordinates PG_BASE =
            new PostgresCoordinates("localhost", 33321, "postgres", "postgres", "postgres");


    @Test
    void namesFourSeparateDatabases() {
        assertArrayEquals(
                new String[] { "raposza_participant", "raposza_sequencer",
                        "raposza_sequencer_driver", "raposza_mediator" },
                StorageOverlay.databaseNames("raposza"));
    }


    @Test
    void everyNodeGetsItsOwnDatabase() {
        String strConf = StorageOverlay.of("raposza", PG_BASE).render();

        assertTrue(strConf.contains("databaseName = \"raposza_participant\""));
        assertTrue(strConf.contains("databaseName = \"raposza_sequencer\""));
        assertTrue(strConf.contains("databaseName = \"raposza_sequencer_driver\""));
        assertTrue(strConf.contains("databaseName = \"raposza_mediator\""));

        // Four storage blocks, one per database, and no fifth.
        assertEquals(4, count(strConf, "type = postgres"));
    }


    @Test
    void theNodeNamesAreTheBundledConfigsNames() {
        // participant `sandbox`, `sequencer1`, `mediator1` - from
        // sandbox/sandbox.conf inside the Canton jar. An overlay that invented
        // its own names would define new nodes beside the real ones rather
        // than reconfigure them.
        String strConf = StorageOverlay.of("raposza", PG_BASE).render();
        assertTrue(strConf.contains("participants {"));
        assertTrue(strConf.contains("sandbox {"));
        assertTrue(strConf.contains("sequencers {"));
        assertTrue(strConf.contains("sequencer1 {"));
        assertTrue(strConf.contains("mediators {"));
        assertTrue(strConf.contains("mediator1 {"));
    }


    @Test
    void theSequencerDriverIsNested() {
        // The reference sequencer keeps its own store under sequencer.config,
        // separately from the node's own storage.
        String strConf = StorageOverlay.of("raposza", PG_BASE).render();
        int idxSequencerBlock = strConf.indexOf("type = \"reference\"");
        int idxDriverDb = strConf.indexOf("raposza_sequencer_driver");
        assertTrue(idxSequencerBlock > 0);
        assertTrue(idxDriverDb > idxSequencerBlock);
    }


    @Test
    void carriesTheMeasuredDataSourceClass() {
        String strConf = StorageOverlay.of("raposza", PG_BASE).render();
        assertTrue(strConf.contains("dataSourceClass = \"org.postgresql.ds.PGSimpleDataSource\""));
        assertTrue(strConf.contains("serverName = \"localhost\""));
        assertTrue(strConf.contains("portNumber = 33321"));
        assertTrue(strConf.contains("user = \"postgres\""));
    }


    @Test
    void theNamedDataSourceClassReplacesTheStockOneEverywhere() {
        // All four storage blocks, the reference sequencer's driver included:
        // every one of them opens a migration-lock connection.
        String strConf = StorageOverlay.of("raposza", PG_BASE)
                .render("com.raposza.canton.jdbc.WinPgDataSource");

        assertEquals(4, count(strConf,
                "dataSourceClass = \"com.raposza.canton.jdbc.WinPgDataSource\""));
        assertEquals(0, count(strConf, "org.postgresql.ds.PGSimpleDataSource"));
    }


    @Test
    void bracesBalance() {
        String strConf = StorageOverlay.of("raposza", PG_BASE).render();
        assertEquals(count(strConf, "{"), count(strConf, "}"));
    }


    @Test
    void refusesAnIncompleteSetOfDatabases() {
        assertThrows(IllegalArgumentException.class,
                () -> new StorageOverlay(PG_BASE, PG_BASE, PG_BASE, null));
    }


    private static int count(String strText, String strNeedle) {
        int cntHit = 0;
        int idxFrom = strText.indexOf(strNeedle);
        while (idxFrom >= 0) {
            cntHit++;
            idxFrom = strText.indexOf(strNeedle, idxFrom + strNeedle.length());
        }
        return cntHit;
    }
}
