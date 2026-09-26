// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.wire;

/**
 * A wire-layer failure that is not the caller's argument error.
 *
 * Unchecked on purpose. Every path that raises it is a participant answering
 * something the protocol does not allow, or not answering at all, and there is
 * nothing a caller can do about either except report it - a checked exception
 * would be propagated unhandled through every frame between here and the
 * operator.
 *
 * Author Claude/bentzn
 */
public class WireException extends RuntimeException {

    private static final long serialVersionUID = 1L;


    public WireException(String strMessage) {
        super(strMessage);
    }


    public WireException(String strMessage, Throwable thrCause) {
        super(strMessage, thrCause);
    }
}
