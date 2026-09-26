// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.idp;

/**
 * The test identity provider could not be configured or could not serve.
 *
 * The message must never carry a client secret, a token or key material: it
 * reaches logs and test failures.
 *
 * Author Claude/bentzn
 */
public class IdpException extends RuntimeException {

    private static final long serialVersionUID = 1L;


    public IdpException(String strMessage) {
        super(strMessage);
    }


    public IdpException(String strMessage, Throwable cause) {
        super(strMessage, cause);
    }

}
