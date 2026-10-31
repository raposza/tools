// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

/**
 * WHAT A FAILURE SAYS WHEN ITS MESSAGE IS EMPTY.
 *
 * `String.valueOf(ex.getMessage())` was the window's way of saying a failure,
 * and an exception need not carry a message: on the WinVM, 2026-10-30, a
 * download that failed with downloads blocked reached the main log as
 * "Install FAILED: null". The same shape had already thrown a
 * NullPointerException in the Splice install dialog on 2026-10-29.
 *
 * Author Claude/bentzn
 */
final class Failures {

    private Failures() {
    }


    /**
     * @param ex what was thrown
     * @return its message, or its class - and its cause's, when it has one -
     *         when it carries none
     */
    static String strWhy(Throwable ex) {
        if (ex == null)
            return "unknown failure";
        String strMessage = ex.getMessage();
        if (strMessage != null && !strMessage.isBlank())
            return strMessage;
        Throwable cause = ex.getCause();
        if (cause != null && cause != ex)
            return ex.getClass().getName() + ": " + strWhy(cause);
        return ex.getClass().getName();
    }
}
