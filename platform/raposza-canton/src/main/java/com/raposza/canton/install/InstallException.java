// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

/**
 * A local installation could not be read, parsed or resolved.
 *
 * Unchecked, because every caller in this package is answering the same
 * question - what is installed here - and an unreadable cache is a condition
 * to report to the operator, not one a caller recovers from.
 *
 * Author Claude/bentzn
 */
public class InstallException extends RuntimeException {

    private static final long serialVersionUID = 1L;


    public InstallException(String strMessage) {
        super(strMessage);
    }


    public InstallException(String strMessage, Throwable cause) {
        super(strMessage, cause);
    }
}
