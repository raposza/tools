// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.sandbox.app.RawarServer;

import java.util.Arrays;

/**
 * One request to a page this window serves, worded for the Web tab's Log -
 * his instruction, 2026-10-04: "We need a log pane on 'Web' tab as well to
 * show all access."
 *
 * Two sources, one shape - `where  METHOD path  what answered  ok|FAILED n`:
 *
 * <ul>
 *   <li>LocalNetND's UI server writes `web.log`, a line per request -
 *   `LocalNetWeb.log`: the instant, the method, the host, the path, the kind
 *   and the status, then the time taken, two spaces between;</li>
 *   <li>the RAWAR server tells a {@link RawarServer.Access} per request.</li>
 * </ul>
 *
 * NO TIME TAKEN, as on the OIDC Access log: "15 ms is useless". The pane puts
 * the local clock in front of each line, so the instant is dropped too.
 *
 * Author Claude/bentzn
 */
final class WebAccess {

    /** What a RAWAR line names as where, since the server has no host name. */
    static final String STR_WHERE_RAWAR = "rawar";

    /** Instant, method, host, path, kind, status, time - the kind may be empty. */
    private static final int CNT_FIELD_MIN = 7;


    private WebAccess() {
    }


    /**
     * @param strRaw one line of LocalNetND's `web.log`
     * @return the line for the pane, or null when it is not one
     */
    static String strLocalNet(String strRaw) {
        if (strRaw == null || strRaw.isBlank())
            return null;
        String[] arrField = strRaw.split("  ", -1);
        if (arrField.length < CNT_FIELD_MIN || !arrField[arrField.length - 1].endsWith("ms"))
            return null;
        int nStatus;
        try {
            nStatus = Integer.parseInt(arrField[arrField.length - 2].trim());
        }
        catch (NumberFormatException ex) {
            return null;
        }
        // THE KIND IS WHAT IS LEFT BETWEEN, so a kind with two spaces in it
        // cannot shift the status into the wrong field.
        String strKind = String.join("  ", Arrays.copyOfRange(arrField, 4, arrField.length - 2))
                .trim();
        return strLine(arrField[2].trim(), arrField[1].trim(), arrField[3].trim(), strKind,
                nStatus);
    }


    /**
     * @param access one request the RAWAR server answered
     * @return the line for the pane
     */
    static String strRawar(RawarServer.Access access) {
        return strLine(STR_WHERE_RAWAR, access.strMethod(), access.strPath(), access.strKind(),
                access.nStatus());
    }


    private static String strLine(String strWhere, String strMethod, String strPath,
            String strKind, int nStatus) {
        StringBuilder bld = new StringBuilder();
        bld.append(strWhere).append("  ").append(strMethod).append(' ').append(strPath);
        if (!strKind.isEmpty())
            bld.append("  ").append(strKind);
        bld.append("  ");
        if (nStatus < 0)
            bld.append("FAILED, no answer");
        else
            bld.append(nStatus < 400 ? "ok" : "FAILED " + nStatus);
        return bld.toString();
    }

}
