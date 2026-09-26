// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.process;

/**
 * A managed process could not be started, refused to stop, or died while it
 * was being waited for.
 *
 * Author Claude/bentzn
 */
public class ProcessException extends RuntimeException {

    private static final long serialVersionUID = 1L;


    public ProcessException(String strMessage) {
        super(strMessage);
    }


    public ProcessException(String strMessage, Throwable cause) {
        super(strMessage, cause);
    }
}
