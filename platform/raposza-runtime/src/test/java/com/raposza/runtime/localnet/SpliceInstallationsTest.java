// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * The version page a Splice bundle carries, read the way the dropdown reads it.
 * The markup is the shape of a Sphinx list-table, not a copy of the vendor's
 * page; the labels are the vendor's, as measured on 22 bundles.
 *
 * Author Claude/bentzn
 */
class SpliceInstallationsTest {

    private static String strRow(String strLabel, String strValue) {
        return "<tr class=\"row-odd\"><td><p>" + strLabel + "</p></td>\n<td><p>" + strValue
                + "</p></td>\n</tr>\n";
    }


    private static final String STR_HTML = "<html><body><p>The following versions of Canton and the"
            + " Daml SDK were used to build this Splice release:</p><table><tbody>\n"
            + strRow("Canton version used for validator and SV nodes", "3.5.14")
            + strRow("Daml SDK version used to compile <code class=\"docutils literal notranslate\">"
                    + "<span class=\"pre\">.dars</span></code>", "3.5.2")
            + strRow("Daml SDK version used for Java and TS codegens", "3.5.3")
            + "</tbody></table><h2>Installing a Compatible Daml SDK</h2></body></html>";


    @Test
    void theThreeValuesAreTheTokensAfterTheirLabels() {
        SpliceInstallations.Versions versions = SpliceInstallations.versionsOfHtml(STR_HTML);

        assertEquals("3.5.14", versions.strCanton());
        assertEquals("3.5.2", versions.strSdkDar());
        assertEquals("3.5.3", versions.strSdkCodegen());
    }


    /** A SNAPSHOT CANTON IS ONE TOKEN, as 0.6.0 to 0.6.5 and 0.7.3 state theirs. */
    @Test
    void aSnapshotVersionIsKeptWhole() {
        String strHtml = STR_HTML.replace(">3.5.14<", ">3.5.1-snapshot.20260423.18760.0.v0d74e51b<");

        assertEquals("3.5.1-snapshot.20260423.18760.0.v0d74e51b",
                SpliceInstallations.versionsOfHtml(strHtml).strCanton());
    }


    /** NEVER A GUESS: a page without the SDK row answers nothing. */
    @Test
    void aPageWithoutTheSdkAnswersNull() {
        assertNull(SpliceInstallations.versionsOfHtml(
                strRow("Canton version used for validator and SV nodes", "3.5.14")));
        assertNull(SpliceInstallations.versionsOfHtml(""));
    }


    @Test
    void aMissingCodegenRowLeavesTheOtherTwo() {
        String strHtml = strRow("Canton version used for validator and SV nodes", "3.5.14")
                + strRow("Daml SDK version used to compile .dars", "3.5.2");

        assertEquals("3.5.2", SpliceInstallations.versionsOfHtml(strHtml).strSdkDar());
        assertNull(SpliceInstallations.versionsOfHtml(strHtml).strSdkCodegen());
    }


    /** A BUNDLE WITH NO PAGE SHOWS ITS VERSION ALONE. */
    @Test
    void aVersionWithNoPageIsShownAlone() {
        assertEquals("0.0.0-absent", SpliceInstallations.strShown("0.0.0-absent"));
    }
}
