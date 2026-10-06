// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.rawar;

import java.io.IOException;

/**
 * A RAWAR that is refused: not exportable, not a descriptor, or - the one that
 * matters - a tree that does not hash to what its descriptor says. An
 * IOException so that callers handling the file handle this too, and a
 * distinct type so that a refusal is never mistaken for a disk fault.
 *
 * Author Claude/bentzn
 */
public final class RawarException extends IOException {

    private static final long serialVersionUID = 1L;


    public RawarException(String strMessage) {
        super(strMessage);
    }
}
