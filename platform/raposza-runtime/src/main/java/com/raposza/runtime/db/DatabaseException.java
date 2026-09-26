// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.db;

/**
 * The embedded server would not start, or a database could not be created.
 *
 * Author Claude/bentzn
 */
public class DatabaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;


    public DatabaseException(String strMessage) {
        super(strMessage);
    }


    public DatabaseException(String strMessage, Throwable cause) {
        super(strMessage, cause);
    }
}
