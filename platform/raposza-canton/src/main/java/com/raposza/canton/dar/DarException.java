// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.dar;

/**
 * A DAR on disk could not be read or indexed.
 *
 * Unchecked, for the same reason as
 * {@link com.raposza.canton.install.InstallException}: every caller is
 * answering one question - what is staged here - and a corrupt or ambiguous
 * staging directory is a condition to report to the operator, not one a caller
 * recovers from mid-scan.
 *
 * Author Claude/bentzn
 */
public class DarException extends RuntimeException {

    private static final long serialVersionUID = 1L;


    public DarException(String strMessage) {
        super(strMessage);
    }


    public DarException(String strMessage, Throwable cause) {
        super(strMessage, cause);
    }
}
