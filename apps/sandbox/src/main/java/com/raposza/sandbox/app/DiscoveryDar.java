// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One DAR as the discovery document lists it: what it is, where it came from
 * and whether the participant has it.
 *
 * Read off the DAR's manifest only - no Daml-LF decode - so the list is cheap
 * and present while nothing runs. The full description of the same DARs is
 * `GET /dars`, which decodes.
 *
 * @param strName the package name the manifest carries, or null
 * @param strVersion its version, or null
 * @param strPkgId the main package id
 * @param strOrigin {@link #STR_ORIGIN_USER} or {@link #STR_ORIGIN_SPLICE}
 * @param strStatus {@link #STR_STATUS_SELECTED} - the next start uploads it -
 *        or {@link #STR_STATUS_UPLOADED} - the participant has it now
 * @param fileDar the DAR file
 *
 * Author Claude/bentzn
 */
public record DiscoveryDar(String strName, String strVersion, String strPkgId, String strOrigin,
        String strStatus, Path fileDar) {

    /** A DAR the person staged. */
    public static final String STR_ORIGIN_USER = "user";

    /** A DAR that comes with the Splice bundle. */
    public static final String STR_ORIGIN_SPLICE = "splice";

    /** Ticked in the store: the next start uploads it. */
    public static final String STR_STATUS_SELECTED = "selected";

    /** On the participant, as it last answered. */
    public static final String STR_STATUS_UPLOADED = "uploaded";


    /**
     * WHAT IS NOT KNOWN IS NULL, NOT ABSENT: a reader that sees a missing key
     * has to guess at it.
     *
     * @return the entry as the document writes it, in a fixed key order
     */
    public Map<String, Object> map() {
        Map<String, Object> mapOut = new LinkedHashMap<>();
        mapOut.put("name", strName);
        mapOut.put("version", strVersion);
        mapOut.put("packageId", strPkgId);
        mapOut.put("origin", strOrigin);
        mapOut.put("status", strStatus);
        mapOut.put("file", fileDar == null ? null : fileDar.toString());
        return mapOut;
    }

}
