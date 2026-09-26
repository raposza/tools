// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

/**
 * A script that cannot be read, or a statement that cannot be run.
 *
 * <h2>The line number is not decoration</h2>
 *
 * A fixture script is edited by hand and re-run, so the first thing an operator
 * does with a failure is go and look at the line. Carrying the number AND the
 * source text means the message stands alone in a transcript, where the file is
 * not to hand and may since have changed.
 *
 * The source is the statement's, not the file line's - a statement continues
 * across lines while a JSON object is open, and quoting only the first line of
 * one would point at the wrong place.
 *
 * Author Claude/bentzn
 */
public final class CaqlException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int numLine;
    private final String strSource;
    private final int numLineFix;


    /**
     * @param numLine one-based line where the statement STARTS
     * @param strSource the statement as written, may be empty when the failure
     *                  is not attached to one
     * @param strWhy what is wrong
     */
    public CaqlException(int numLine, String strSource, String strWhy) {
        this(numLine, strSource, strWhy, 0);
    }


    /**
     * A failure with a KNOWN PLACE for its fix: a missing terminator, whose
     * {@code ;} belongs at the end of one particular line. The editor puts
     * the caret there rather than at the start of the statement, because the
     * start is where the reading begins and the end is where the typing does.
     *
     * @param numLine one-based line where the statement STARTS
     * @param strSource the statement as written
     * @param strWhy what is wrong
     * @param numLineFix one-based line whose END is where the fix goes, or 0
     *                   when the failure has no such place
     */
    public CaqlException(int numLine, String strSource, String strWhy, int numLineFix) {
        super("line " + numLine + ": " + strWhy
                + (strSource == null || strSource.isBlank() ? "" : "\n  " + strSource));
        this.numLine = numLine;
        this.strSource = strSource == null ? "" : strSource;
        this.numLineFix = numLineFix;
    }


    /** @return one-based line where the offending statement starts */
    public int numLine() {
        return numLine;
    }


    /** @return the statement as written, empty when there is none */
    public String strSource() {
        return strSource;
    }


    /** @return the line whose end is where the fix goes, 0 when there is no such line */
    public int numLineFix() {
        return numLineFix;
    }

}
