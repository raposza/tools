// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Author Claude/bentzn
 */
class PostgresCoordinatesTest {

    private static final PostgresCoordinates PG =
            new PostgresCoordinates("localhost", 33321, "raposza_participant", "postgres",
                    "s3cret");


    @Test
    void buildsAJdbcUrl() {
        assertEquals("jdbc:postgresql://localhost:33321/raposza_participant", PG.jdbcUrl());
    }


    @Test
    void changingTheDatabaseKeepsTheServer() {
        PostgresCoordinates other = PG.withDatabase("raposza_mediator");
        assertEquals("raposza_mediator", other.strDatabase());
        assertEquals(PG.strHost(), other.strHost());
        assertEquals(PG.nPort(), other.nPort());
        assertEquals(PG.strUser(), other.strUser());
        assertEquals(PG.strPassword(), other.strPassword());
    }


    @Test
    void theStringFormDoesNotCarryThePassword() {
        assertFalse(PG.toString().contains("s3cret"));
    }


    @Test
    void refusesIncompleteCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> new PostgresCoordinates("", 33321, "db", "postgres", ""));
        assertThrows(IllegalArgumentException.class,
                () -> new PostgresCoordinates("localhost", 0, "db", "postgres", ""));
        assertThrows(IllegalArgumentException.class,
                () -> new PostgresCoordinates("localhost", 33321, " ", "postgres", ""));
        assertThrows(IllegalArgumentException.class,
                () -> new PostgresCoordinates("localhost", 33321, "db", "postgres", null));
    }
}
