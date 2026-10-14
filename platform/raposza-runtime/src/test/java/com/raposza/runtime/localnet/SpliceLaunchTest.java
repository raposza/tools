// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How a Splice bundle is started, on both platforms, from whichever one the
 * tests run on - `todo.md` W-14.
 *
 * THE WINDOWS FORM IS THE SCRIPT'S OWN LAST STATEMENT, D-601, and the
 * control can fail: a bundle carrying only the script is NOT installed on
 * Windows, and one carrying only the jar is not installed on Linux.
 *
 * Author Claude/bentzn
 */
class SpliceLaunchTest {

    private static final String STR_JAVA = "/opt/jdk/bin/java";


    @Test
    void windowsNeedsTheJarAndLinuxTheScript(@TempDir Path dirTmp) {
        Path dirBundle = dirTmp.resolve("0.9.1").resolve(SpliceInstallations.STR_BUNDLE);

        assertEquals(dirBundle.resolve("lib").resolve("splice-node.jar"),
                SpliceLaunch.fileRequired(dirBundle, true));
        assertEquals(dirBundle.resolve("bin").resolve("splice-node"),
                SpliceLaunch.fileRequired(dirBundle, false));
    }


    /** On Windows the jar decides, and the script alone is not enough. */
    @Test
    void onWindowsOnlyTheJarCounts(@TempDir Path dirTmp) throws IOException {
        Path dirScript = bundle(dirTmp, "script-only", true, false);
        Path dirJar = bundle(dirTmp, "jar-only", false, true);

        assertFalse(SpliceLaunch.isInstalled(dirScript, true));
        assertTrue(SpliceLaunch.isInstalled(dirJar, true));
    }


    /** On Linux the executable script decides, exactly as before W-14. */
    @Test
    void onLinuxOnlyTheExecutableScriptCounts(@TempDir Path dirTmp) throws IOException {
        assumeTrue(!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"));

        Path dirScript = bundle(dirTmp, "script-only", true, false);
        Path dirJar = bundle(dirTmp, "jar-only", false, true);

        assertTrue(SpliceLaunch.isInstalled(dirScript, false));
        assertFalse(SpliceLaunch.isInstalled(dirJar, false));
    }


    /** Linux runs the vendor's script and nothing else. */
    @Test
    void linuxRunsTheScript(@TempDir Path dirTmp) {
        Path dirBundle = dirTmp.resolve(SpliceInstallations.STR_BUNDLE);

        assertEquals(List.of(dirBundle.resolve("bin").resolve("splice-node").toString()),
                SpliceLaunch.lstCommand(dirBundle, List.of(dirTmp.resolve("shim")), false,
                        STR_JAVA));
    }


    /**
     * Windows runs the JVM the script ends in - D-601 - with anything asked
     * for put ahead of the jar.
     */
    @Test
    void windowsRunsTheScriptsJvm(@TempDir Path dirTmp) {
        Path dirBundle = dirTmp.resolve(SpliceInstallations.STR_BUNDLE);
        Path dirShim = dirTmp.resolve("shim");

        assertEquals(List.of(STR_JAVA, "-XX:+CrashOnOutOfMemoryError",
                "-Dprog.home=" + dirBundle,
                "-Dprog.version=splice-node.jar",
                "-cp", dirShim + File.pathSeparator + dirBundle.resolve("lib")
                        .resolve("splice-node.jar"),
                "org.lfdecentralizedtrust.splice.SpliceApp"),
                SpliceLaunch.lstCommand(dirBundle, List.of(dirShim), true, STR_JAVA));
    }


    /** Nothing asked for ahead of it, the jar is the whole classpath. */
    @Test
    void windowsWithNothingAheadRunsTheJarAlone(@TempDir Path dirTmp) {
        Path dirBundle = dirTmp.resolve(SpliceInstallations.STR_BUNDLE);

        List<String> lstCmd = SpliceLaunch.lstCommand(dirBundle, List.of(), true, STR_JAVA);

        assertEquals(dirBundle.resolve("lib").resolve("splice-node.jar").toString(),
                lstCmd.get(lstCmd.indexOf("-cp") + 1));
    }


    /**
     * @return `dirTmp/&lt;name&gt;/splice-node`, carrying the script, the jar or both
     */
    private static Path bundle(Path dirTmp, String strName, boolean flagScript, boolean flagJar)
            throws IOException {
        Path dirBundle = dirTmp.resolve(strName).resolve(SpliceInstallations.STR_BUNDLE);
        if (flagScript) {
            Path fileScript = dirBundle.resolve("bin").resolve(SpliceInstallations.STR_BUNDLE);
            Files.createDirectories(fileScript.getParent());
            Files.writeString(fileScript, "#!/bin/sh\n");
            if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"))
                Files.setPosixFilePermissions(fileScript,
                        PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        if (flagJar) {
            Path fileJar = dirBundle.resolve("lib").resolve("splice-node.jar");
            Files.createDirectories(fileJar.getParent());
            Files.writeString(fileJar, "not a jar");
        }
        return dirBundle;
    }

}
