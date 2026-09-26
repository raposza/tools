// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api;

/**
 * Transport or protocol failure. A command the ledger deliberately rejected is
 * NOT an exception - see SubmitResult.
 *
 * Author Claude/bentzn
 */
public class LedgerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String codeError;


    public LedgerException(String strMessage) {
        this(strMessage, null, null);
    }


    public LedgerException(String strMessage, Throwable cause) {
        this(strMessage, null, cause);
    }


    public LedgerException(String strMessage, String codeError, Throwable cause) {
        super(strMessage, cause);
        this.codeError = codeError;
    }


    /**
     * @return the Canton error code where one was reported, null otherwise
     */
    public String getCodeError() {
        return codeError;
    }

}
