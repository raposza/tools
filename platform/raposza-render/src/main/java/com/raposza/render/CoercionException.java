// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.render;

/**
 * A JSON document that cannot be read as the declared type.
 *
 * THE PATH IS THE POINT. A fixture script fails on one field of one nested
 * record inside one list element, and "expected Int64" without a location sends
 * the operator hunting through a payload the tool already knows the shape of.
 * The path is in the message as well as available separately, because the
 * message is what reaches a transcript.
 *
 * Author Claude/bentzn
 */
public final class CoercionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String strPath;
    private final String strDetail;


    /**
     * @param strPath JSONPath-like location, e.g. "$.owner.address[0].zip"
     * @param strDetail what was wrong there
     */
    public CoercionException(String strPath, String strDetail) {
        super(strPath + ": " + strDetail);
        this.strPath = strPath;
        this.strDetail = strDetail;
    }


    /** @return the location of the offending value */
    public String path() {
        return strPath;
    }


    /** @return what was wrong, without the location */
    public String detail() {
        return strDetail;
    }

}
