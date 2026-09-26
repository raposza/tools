// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.db;

/**
 * Where one database is, and who to be when connecting to it.
 *
 * One record per database rather than one per server, because the stack's
 * databases are not interchangeable: each Canton node owns its own, and PQS
 * needs one per scribe BINARY - 3.4.1 writes schema revision 034 and 3.4.3
 * writes 035, so sharing between them corrupts as surely as sharing across
 * minor lines would.
 *
 * @param strHost the server host
 * @param nPort the server port
 * @param strDatabase the database name
 * @param strUser the role to connect as
 * @param strPassword the password; the embedded server uses trust auth, so any
 *        value is accepted there, and this still has to be a real value
 *        because Canton and scribe both put it in their configuration
 *
 * Author Claude/bentzn
 */
public record PostgresCoordinates(String strHost, int nPort, String strDatabase, String strUser,
        String strPassword) {

    public PostgresCoordinates {
        if (strHost == null || strHost.isBlank())
            throw new IllegalArgumentException("host is required");
        if (nPort < 1 || nPort > 65535)
            throw new IllegalArgumentException("port out of range: " + nPort);
        if (strDatabase == null || strDatabase.isBlank())
            throw new IllegalArgumentException("database is required");
        if (strUser == null || strUser.isBlank())
            throw new IllegalArgumentException("user is required");
        if (strPassword == null)
            throw new IllegalArgumentException("password is required, even when it is ignored");
    }


    public String jdbcUrl() {
        return "jdbc:postgresql://" + strHost + ":" + nPort + "/" + strDatabase;
    }


    public PostgresCoordinates withDatabase(String strDatabaseNew) {
        return new PostgresCoordinates(strHost, nPort, strDatabaseNew, strUser, strPassword);
    }


    /** The password is deliberately not in here. */
    @Override
    public String toString() {
        return strUser + "@" + strHost + ":" + nPort + "/" + strDatabase;
    }
}
