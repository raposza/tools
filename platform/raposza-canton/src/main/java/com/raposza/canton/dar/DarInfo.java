// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.dar;

import java.nio.file.Path;

/**
 * One DAR on disk, identified by what its own manifest says rather than by its
 * filename.
 *
 * <h2>Why the package id and not the filename</h2>
 *
 * A DAR can be renamed, staged twice, or copied out of a build directory under
 * a name that no longer matches the package inside it. The package id is the
 * ledger's identity for the package and the only field here that Canton will
 * agree with. The name and the version are read from the same string and are
 * for a human reading a list - nothing keys on them.
 *
 * <h2>Why name and version can be null</h2>
 *
 * `Main-Dalf` is `&lt;name&gt;-&lt;version&gt;-&lt;pkgid&gt;.dalf` for a DAR
 * built from a Daml project, but the SDK's own packages do not all follow it -
 * `daml-prim-DA-Internal-...-&lt;pkgid&gt;.dalf` carries no version segment.
 * Refusing those would make the catalogue reject inputs the participant accepts
 * happily, so an unparseable name is recorded as absent, not as an error. The
 * package id is the field that must always be there.
 *
 * @param fileDar the file the scan found it in, absolute and normalized
 * @param strPkgId the 64-character lowercase hex package id
 * @param strName the Daml project name, or null when the main dalf does not
 *        follow the name-version-hash convention
 * @param strVersion the package version, or null on the same condition
 *
 * Author Claude/bentzn
 */
public record DarInfo(Path fileDar, String strPkgId, String strName, String strVersion) {

    public DarInfo {
        if (fileDar == null)
            throw new IllegalArgumentException("fileDar is required");
        if (strPkgId == null || strPkgId.isBlank())
            throw new IllegalArgumentException("strPkgId is required");
    }


    /**
     * @return the file name alone, which is what a log line or a table column
     *         wants
     */
    public String strFileName() {
        return fileDar.getFileName().toString();
    }


    /**
     * @return name and version when both are known, otherwise the file name -
     *         never null, so a caller rendering a list needs no branch
     */
    public String strLabel() {
        if (strName == null)
            return strFileName();
        if (strVersion == null)
            return strName;
        return strName + "-" + strVersion;
    }


    @Override
    public String toString() {
        return strLabel() + " (" + strPkgId.substring(0, 8) + "\u2026) " + strFileName();
    }
}
