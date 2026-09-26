// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Does a bundle's `splice-node` start - the check that would have refused
 * Splice 0.8.2 on 2026-09-23.
 *
 * THE TWO FAKE LAUNCHERS ARE THE TWO MEASURED BUNDLES: the healthy one writes
 * the log file it is given and refuses the empty configuration, as 0.8.1 did;
 * the broken one prints the vendor's line and exits 255 with no log, as 0.8.2
 * did. The control can fail both ways: a check that trusted the exit code
 * would refuse the healthy one, and one that only looked for the line would
 * pass a launcher that wrote nothing at all.
 *
 * Author Claude/bentzn
 */
class SpliceCheckTest {

    private static final String STR_HEALTHY = "#!/bin/sh\n"
            + "while [ $# -gt 0 ]; do\n"
            + "  if [ \"$1\" = \"--log-file-name\" ]; then echo GENERIC_CONFIG_ERROR > \"$2\"; fi\n"
            + "  shift\n"
            + "done\n"
            + "exit 1\n";

    private static final String STR_BROKEN = "#!/bin/sh\n"
            + "echo 'Unable to load log configuration.'\n"
            + "exit 255\n";

    private static final String STR_SILENT = "#!/bin/sh\nexit 0\n";


    @Test
    void theVerdictIsTheLogAndTheVendorsLineNotTheExitCode() {
        assertNull(SpliceCheck.strWhyNot("GENERIC_CONFIG_ERROR(8,0)", true));
        assertNotNull(SpliceCheck.strWhyNot("Unable to load log configuration.", false));
        assertNotNull(SpliceCheck.strWhyNot("Unable to load log configuration.", true));
        assertNotNull(SpliceCheck.strWhyNot("", false));
    }


    @Test
    void theTwoMeasuredBundlesAreToldApart(@TempDir Path dirTmp) throws IOException {
        assumeTrue(!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"));

        assertNull(SpliceCheck.strWhyNotRun(bundle(dirTmp, "healthy", STR_HEALTHY)));
        String strWhy = SpliceCheck.strWhyNotRun(bundle(dirTmp, "broken", STR_BROKEN));
        assertNotNull(strWhy);
        assertTrue(strWhy.contains("log configuration"), strWhy);
        assertNotNull(SpliceCheck.strWhyNotRun(bundle(dirTmp, "silent", STR_SILENT)));
    }


    /** A PASS IS REMEMBERED, a failure is not. */
    @Test
    void aPassIsRememberedBesideTheBundle(@TempDir Path dirTmp) throws IOException {
        assumeTrue(!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"));

        Path dirGood = bundle(dirTmp, "good", STR_HEALTHY);
        assertNull(SpliceCheck.strWhyNotStarts(dirGood));
        assertTrue(Files.isRegularFile(dirGood.getParent().resolve(SpliceCheck.STR_FILE_CHECKED)));

        Path dirBad = bundle(dirTmp, "bad", STR_BROKEN);
        assertNotNull(SpliceCheck.strWhyNotStarts(dirBad));
        assertFalse(Files.exists(dirBad.getParent().resolve(SpliceCheck.STR_FILE_CHECKED)));
    }


    /**
     * @return `dirTmp/&lt;name&gt;/splice-node`, holding `bin/splice-node`
     */
    private static Path bundle(Path dirTmp, String strName, String strScript) throws IOException {
        Path dirBundle = dirTmp.resolve(strName).resolve(SpliceInstallations.STR_BUNDLE);
        Path fileLauncher = dirBundle.resolve("bin").resolve(SpliceInstallations.STR_BUNDLE);
        Files.createDirectories(fileLauncher.getParent());
        Files.writeString(fileLauncher, strScript);
        Files.setPosixFilePermissions(fileLauncher, PosixFilePermissions.fromString("rwxr-xr-x"));
        return dirBundle;
    }
}
