// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.util.Optional;

/**
 * A three-part Canton or PQS version, and the minor line it belongs to.
 *
 * Parsing is deliberately strict: exactly three numeric segments, with an
 * optional leading "v" because that is how the scribe banner writes it. A
 * directory name that is not a version - "daml3.4", "bin", "line" - does not
 * parse, and discovery relies on that to walk a cache without a whitelist.
 *
 * Ordering is numeric per segment, so 2.10.4 sorts above 2.9.7. String
 * ordering gets that backwards, and both versions are live compatibility
 * targets.
 *
 * Author Claude/bentzn
 */
public final class VersionId implements Comparable<VersionId> {

    private final int nMajor;
    private final int nMinor;
    private final int nPatch;


    private VersionId(int nMajor, int nMinor, int nPatch) {
        this.nMajor = nMajor;
        this.nMinor = nMinor;
        this.nPatch = nPatch;
    }


    public static VersionId of(int nMajor, int nMinor, int nPatch) {
        if (nMajor < 0 || nMinor < 0 || nPatch < 0)
            throw new IllegalArgumentException("version segments must not be negative");
        return new VersionId(nMajor, nMinor, nPatch);
    }


    /**
     * @param strText a version such as "3.5.11" or "v3.5.7"
     * @return the parsed version
     * @throws InstallException when the text is not a three-part version
     */
    public static VersionId parse(String strText) {
        return tryParse(strText).orElseThrow(
                () -> new InstallException("not a three-part version: " + strText));
    }


    /**
     * @param strText candidate text, may be null
     * @return the parsed version, or empty when the text is not one
     */
    public static Optional<VersionId> tryParse(String strText) {
        if (strText == null)
            return Optional.empty();

        String str = strText.trim();
        if (str.startsWith("v") || str.startsWith("V"))
            str = str.substring(1);

        String[] arrSeg = str.split("\\.");
        if (arrSeg.length != 3)
            return Optional.empty();

        int[] arrNum = new int[3];
        for (int idxSeg = 0; idxSeg < 3; idxSeg++) {
            String strSeg = arrSeg[idxSeg];
            if (strSeg.isEmpty() || strSeg.length() > 9)
                return Optional.empty();
            for (int idxChar = 0; idxChar < strSeg.length(); idxChar++) {
                char ch = strSeg.charAt(idxChar);
                if (ch < '0' || ch > '9')
                    return Optional.empty();
            }
            arrNum[idxSeg] = Integer.parseInt(strSeg);
        }
        return Optional.of(new VersionId(arrNum[0], arrNum[1], arrNum[2]));
    }


    public int major() {
        return nMajor;
    }


    public int minor() {
        return nMinor;
    }


    public int patch() {
        return nPatch;
    }


    /**
     * @return the minor line, e.g. "3.5" - the key PQS is resolved by
     */
    public String line() {
        return nMajor + "." + nMinor;
    }


    public boolean isLine(String strLine) {
        return line().equals(strLine);
    }


    @Override
    public int compareTo(VersionId other) {
        if (nMajor != other.nMajor)
            return Integer.compare(nMajor, other.nMajor);
        if (nMinor != other.nMinor)
            return Integer.compare(nMinor, other.nMinor);
        return Integer.compare(nPatch, other.nPatch);
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof VersionId other))
            return false;
        return nMajor == other.nMajor && nMinor == other.nMinor && nPatch == other.nPatch;
    }


    @Override
    public int hashCode() {
        return (nMajor * 31 + nMinor) * 31 + nPatch;
    }


    @Override
    public String toString() {
        return nMajor + "." + nMinor + "." + nPatch;
    }
}
