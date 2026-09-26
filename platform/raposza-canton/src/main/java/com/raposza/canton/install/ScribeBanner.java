// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The three numbers scribe prints for `--version`, which move independently of
 * each other and of Canton.
 *
 * <pre>
 * scribe, version: v3.5.7
 * daml-sdk.version: 3.5.2
 * postgres-document.schema: 041
 * </pre>
 *
 * The schema revision is kept as text, not an int: it is written with leading
 * zeros - 034, 035, 041 - and it is a database identity, not a quantity. It
 * moves at PATCH level, so 3.4.1 and 3.4.3 write different revisions and
 * cannot share a database.
 *
 * @param version the scribe version, e.g. 3.5.7
 * @param strSchemaRevision the postgres-document schema revision, or null when
 *        the banner omitted it
 * @param strDamlSdkVersion the daml-sdk.version line, which is neither the SDK
 *        bundle that delivered the jar nor the Canton version, or null when
 *        omitted
 *
 * Author Claude/bentzn
 */
public record ScribeBanner(VersionId version, String strSchemaRevision, String strDamlSdkVersion) {

    private static final Pattern PAT_VERSION =
            Pattern.compile("(?m)^\\s*scribe\\s*,\\s*version\\s*:\\s*(\\S+)\\s*$");

    private static final Pattern PAT_SCHEMA =
            Pattern.compile("(?m)^\\s*postgres-document\\.schema\\s*:\\s*(\\S+)\\s*$");

    private static final Pattern PAT_SDK =
            Pattern.compile("(?m)^\\s*daml-sdk\\.version\\s*:\\s*(\\S+)\\s*$");


    public ScribeBanner {
        if (version == null)
            throw new IllegalArgumentException("version is required");
    }


    /**
     * @param strText raw `--version` output, or the VERSION.txt that records it
     * @return the parsed banner
     * @throws InstallException when no scribe version line is present
     */
    public static ScribeBanner parse(String strText) {
        if (strText == null)
            throw new InstallException("no scribe banner to parse");

        Matcher mtcVersion = PAT_VERSION.matcher(strText);
        if (!mtcVersion.find())
            throw new InstallException("no scribe version line in: " + firstLine(strText));

        VersionId version = VersionId.parse(mtcVersion.group(1));
        return new ScribeBanner(version, group(PAT_SCHEMA, strText), group(PAT_SDK, strText));
    }


    /**
     * DERIVED, and the derivation is a 3.x RULE rather than a general one.
     * PQS major.minor equals Canton major.minor on that generation and does not
     * on 2.x, where scribe is versioned 0.x: v0.5.5 pairs with Canton 2.10 and
     * derives to "0.5", which resolves for no Canton at all.
     *
     * Prefer the line DECLARED in SOURCE.txt, which is what
     * {@link PqsInstallations} reads; this is its fallback.
     *
     * @return the Canton minor line derived from the scribe version
     */
    public String cantonLine() {
        return version.line();
    }


    private static String group(Pattern pat, String strText) {
        Matcher mtc = pat.matcher(strText);
        return mtc.find() ? mtc.group(1) : null;
    }


    private static String firstLine(String strText) {
        int idxBreak = strText.indexOf('\n');
        String str = idxBreak < 0 ? strText : strText.substring(0, idxBreak);
        return str.length() > 120 ? str.substring(0, 120) : str;
    }
}
