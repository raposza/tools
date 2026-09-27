// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import com.raposza.runtime.settings.RaposzaSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The version page a Splice bundle carries, read the way the dropdown reads it.
 * The markup is the shape of a Sphinx list-table, not a copy of the vendor's
 * page; the labels are the vendor's, as measured on 22 bundles.
 *
 * Author Claude/bentzn
 */
class SpliceInstallationsTest {

    private String strWas;

    private boolean flagSet;


    @AfterEach
    void restore() {
        if (!flagSet)
            return;
        if (strWas == null)
            System.clearProperty(RaposzaSettings.STR_PROP_FILE);
        else
            System.setProperty(RaposzaSettings.STR_PROP_FILE, strWas);
        RaposzaSettings.reload();
    }


    /** `dir.splice` moves the bundles - todo.md A-45. */
    @Test
    void theRootFollowsTheSetting(@TempDir Path dirTmp) throws IOException {
        Path dirSet = dirTmp.resolve("corporate-splice");
        Path fileLauncher = dirSet.resolve("0.8.3").resolve(SpliceInstallations.STR_BUNDLE)
                .resolve("bin").resolve(SpliceInstallations.STR_BUNDLE);
        Files.createDirectories(fileLauncher.getParent());
        Files.createFile(fileLauncher);
        fileLauncher.toFile().setExecutable(true);
        use(dirTmp, dirSet.toString());

        assertEquals(dirSet, SpliceInstallations.dirRoot());
        assertEquals(dirSet.resolve("0.8.3").resolve(SpliceInstallations.STR_BUNDLE),
                SpliceInstallations.dirBundle("0.8.3"));
        assertEquals(List.of("0.8.3"), SpliceInstallations.lstVersion());
    }


    /** Blank is `~/.splice`, and the default root never moves with the setting. */
    @Test
    void blankIsTheDefault(@TempDir Path dirTmp) throws IOException {
        use(dirTmp, "");
        assertEquals(SpliceInstallations.dirRootDefault(), SpliceInstallations.dirRoot());
        assertEquals(Path.of(System.getProperty("user.home"), ".splice"),
                SpliceInstallations.dirRootDefault());
    }


    private void use(Path dirTmp, String strSplice) throws IOException {
        Properties props = new Properties();
        props.setProperty(RaposzaSettings.STR_KEY_DIR_SPLICE, strSplice);
        Path file = dirTmp.resolve("settings.properties");
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "test");
        }
        strWas = System.getProperty(RaposzaSettings.STR_PROP_FILE);
        flagSet = true;
        System.setProperty(RaposzaSettings.STR_PROP_FILE, file.toString());
        RaposzaSettings.reload();
    }


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
