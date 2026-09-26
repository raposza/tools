// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.jdbc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Author Claude/bentzn
 */
class WinPgDataSourceTest {

    @Test
    void rewritesTheBareInteger() {
        // The shape the migration lock sends, and the one the postmaster
        // refuses on Windows.
        assertEquals("SET client_connection_check_interval TO 0",
                WinPgDataSource.strRewrite("SET client_connection_check_interval TO 5000"));
    }


    @Test
    void rewritesTheQuotedValueWithItsUnit() {
        assertEquals("SET client_connection_check_interval TO 0",
                WinPgDataSource.strRewrite("SET client_connection_check_interval TO '5000 ms'"));
    }


    @Test
    void rewritesTheEqualsForm() {
        assertEquals("set client_connection_check_interval = 0",
                WinPgDataSource.strRewrite("set client_connection_check_interval = 5000"));
    }


    @Test
    void rewritesEveryOccurrence() {
        assertEquals("SET client_connection_check_interval TO 0;"
                + " SET client_connection_check_interval TO 0",
                WinPgDataSource.strRewrite("SET client_connection_check_interval TO 5000;"
                        + " SET client_connection_check_interval TO 250ms"));
    }


    @Test
    void everyOtherStatementComesBackAsTheSameReference() {
        // Identity, not equality: a statement that does not mention the
        // parameter must come back as the same reference.
        String strSql = "SELECT 1 FROM pg_catalog.pg_class";
        assertSame(strSql, WinPgDataSource.strRewrite(strSql));
    }


    @Test
    void nullStaysNull() {
        assertNull(WinPgDataSource.strRewrite(null));
    }


    @Test
    void isADataSourceTheDriverAccepts() {
        // Constructing it proves the driver is where this class expects it,
        // which is the half of the fix a rewrite test cannot see.
        assertNotNull(new WinPgDataSource());
    }
}
