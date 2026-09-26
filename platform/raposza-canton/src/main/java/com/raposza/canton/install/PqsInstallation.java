// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.nio.file.Path;

/**
 * One staged scribe.jar, with the facts read from its own banner rather than
 * inferred from anything around it.
 *
 * PQS availability is an INDEPENDENT AXIS from the Canton version. A Canton
 * installation being present says nothing about whether a PQS exists for it.
 *
 * The Canton line is DECLARED, not derived. It equals the scribe major.minor
 * on the 3.x generation and does not on the 2.x one, where scribe versions are
 * 0.x - v0.5.5 pairs with Canton 2.10. Deriving would yield "0.5".
 *
 * @param version the scribe version, e.g. 3.4.3
 * @param strCantonLine the Canton minor line it pairs with, e.g. "3.4"
 * @param fileJar the absolute path to scribe.jar
 * @param strSchemaRevision the database schema revision this binary writes;
 *        unique per binary, and reported rather than used to name anything
 * @param strDamlSdkVersion the banner's daml-sdk.version, informational only
 * @param source where the jar came from; {@link PqsSource#MOCK} marks a
 *        stand-in, which resolves and runs like any other and proves nothing
 *        about scribe
 *
 * Author Claude/bentzn
 */
public record PqsInstallation(VersionId version, String strCantonLine, Path fileJar,
        String strSchemaRevision, String strDamlSdkVersion, PqsSource source)
        implements Comparable<PqsInstallation> {

    /** Used when a caller names no prefix at all. */
    public static final String STR_DEFAULT_DATABASE = "pqs";


    public PqsInstallation {
        if (version == null)
            throw new IllegalArgumentException("version is required");
        if (fileJar == null)
            throw new IllegalArgumentException("fileJar is required");
        if (strCantonLine == null || strCantonLine.isBlank())
            strCantonLine = version.line();
        if (source == null)
            source = PqsSource.UNKNOWN;
    }


    /**
     * @return whether this jar is vendor scribe; false for a local stand-in.
     *         A result measured against a non-vendor binary measures the
     *         wiring, and any report carrying it says so.
     */
    public boolean isVendor() {
        return source != PqsSource.MOCK;
    }


    /**
     * ONE database name, the same for every binary and for the mock: the
     * prefix as given. A stack that switches scribe versions points the new
     * binary at the database the previous one wrote, and scribe migrates it
     * or refuses it on its own terms.
     *
     * @param strPrefix the caller's database prefix, e.g. "pqs"
     * @return a lower-case identifier safe to use unquoted
     */
    public String databaseName(String strPrefix) {
        if (strPrefix == null || strPrefix.isBlank())
            return STR_DEFAULT_DATABASE;
        return strPrefix;
    }


    /**
     * The GENERATION, read off the declared Canton line rather than off the
     * scribe version.
     *
     * 2.x scribe is versioned 0.x - v0.5.5 pairs with Canton 2.10 - so a major
     * taken from {@link #version} would be 0 and every branch on it would be
     * wrong on both columns. The line is the field that was DECLARED for
     * exactly this reason.
     *
     * @return whether this binary pairs with a Canton 2.x participant
     */
    public boolean isCanton2x() {
        return isCanton2xLine(strCantonLine);
    }


    /**
     * The same question asked of a bare line, for the case where there is no
     * binary to read it off: a PQS mock stands in front of a generation and
     * has no jar to declare one. ONE implementation rather than two, because
     * the second one was a default rather than an answer - the mock rendered
     * the 3.x command shape on the 2.x column.
     *
     * @param strLine a Canton minor line such as "2.10", or null
     * @return whether it is a 2.x line; false for null, blank or unparseable
     */
    public static boolean isCanton2xLine(String strLine) {
        if (strLine == null || strLine.isBlank())
            return false;
        String strTrim = strLine.trim();
        int idxDot = strTrim.indexOf('.');
        String strMajor = idxDot < 0 ? strTrim : strTrim.substring(0, idxDot);
        return "2".equals(strMajor);
    }


    /** Newest first. */
    @Override
    public int compareTo(PqsInstallation other) {
        return other.version.compareTo(version);
    }


    @Override
    public String toString() {
        return "scribe " + version + (isVendor() ? "" : " STAND-IN") + " (canton " + strCantonLine
                + ", schema " + strSchemaRevision + ") " + fileJar;
    }
}
