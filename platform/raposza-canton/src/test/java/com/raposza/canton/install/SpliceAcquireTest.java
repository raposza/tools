// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Splice acquisition with nothing fetched: the names, the version list, and an
 * install of a small archive shaped like the vendor's - one top directory,
 * `splice-node/`, holding an executable `bin/splice-node`.
 *
 * Author Claude/bentzn
 */
class SpliceAcquireTest {

    @Test
    void theArchiveNameAndUrlAreTheVendorsTemplate() {
        assertEquals("0.7.4_splice-node.tar.gz", SpliceAcquire.strArchive("0.7.4"));
        assertEquals("https://github.com/digital-asset/decentralized-canton-sync/releases/download/"
                + "v0.7.4/0.7.4_splice-node.tar.gz", SpliceAcquire.strUrlArchive("0.7.4"));
    }


    /** THE VERSION IS THE NAME'S PREFIX, and a name without one is refused. */
    @Test
    void theVersionIsReadOffTheNameOrNothing() {
        assertEquals("0.8.1", SpliceAcquire.strVersionOfArchive("0.8.1_splice-node.tar.gz"));
        assertNull(SpliceAcquire.strVersionOfArchive("splice-node.tar.gz"));
        assertNull(SpliceAcquire.strVersionOfArchive("0.8.1_splice-node (1).tar.gz"));
        assertNull(SpliceAcquire.strVersionOfArchive(null));
    }


    /** THREE TRAPS, splice_inventory.md 2.1: non-version tags, gaps, date order. */
    @Test
    void theVersionListSkipsTagsThatAreNotVersionsAndSortsByNumber() {
        String strPage = "[{\"tag_name\": \"v0.5.3\"},{\"tag_name\": \"v0.4.25\"},"
                + "{\"tag_name\": \"token-standard-v2-upcoming\"},{\"tag_name\": \"v0.5.10\"},"
                + "{\"tag_name\":\"v0.4.24\"},{\"tag_name\": \"java-codegen\"}]";

        List<String> lstPage = SpliceAcquire.lstVersionOfPage(strPage);
        assertEquals(List.of("0.5.3", "0.4.25", "0.5.10", "0.4.24"), lstPage);
        assertEquals(List.of("0.5.10", "0.5.3", "0.4.25", "0.4.24"), SpliceAcquire.lstNewestFirst(lstPage));
    }


    @Test
    void aSizeIsShownInMegabytes() {
        assertEquals("733 MB", SpliceAcquire.strSize(768_686_758L));
        assertEquals("size unknown", SpliceAcquire.strSize(-1L));
    }


    /** ONE LINE, REWRITTEN: the subject stays put and only the figure moves. */
    @Test
    void theDownloadLineCarriesTheVersionAndTheFigure() {
        assertEquals("Downloading Splice 0.7.1: 10 of 730 MB",
                SpliceAcquire.strDownloading("0.7.1", 10L, 730L * 1024L * 1024L));
        assertEquals("Downloading Splice 0.7.1: 10 of ? MB", SpliceAcquire.strDownloading("0.7.1", 10L, -1L));
    }


    /**
     * A LAUNCHER THAT PASSES THE POST-INSTALL CHECK: it writes the log file it
     * is given and then refuses its configuration, which is what 0.8.1 does -
     * `SpliceCheck`. Before 2026-09-23 the fake was `#!/bin/sh` and nothing
     * else, which no check that can fail would pass.
     */
    static final String STR_LAUNCHER_HEALTHY = "#!/bin/sh\n"
            + "while [ $# -gt 0 ]; do\n"
            + "  if [ \"$1\" = \"--log-file-name\" ]; then echo GENERIC_CONFIG_ERROR > \"$2\"; fi\n"
            + "  shift\n"
            + "done\n"
            + "exit 1\n";

    /** What 0.8.2 does: the vendor's own line, no log file, exit 255. */
    static final String STR_LAUNCHER_BROKEN = "#!/bin/sh\n"
            + "echo 'Unable to load log configuration.'\n"
            + "exit 255\n";


    private static Path fileArchiveFake(Path dir, String strName) throws IOException, InterruptedException {
        return fileArchiveFake(dir, strName, STR_LAUNCHER_HEALTHY);
    }


    private static Path fileArchiveFake(Path dir, String strName, String strLauncher)
            throws IOException, InterruptedException {
        Path dirSrc = dir.resolve("src");
        Path fileLauncher = dirSrc.resolve("splice-node").resolve("bin").resolve("splice-node");
        Files.createDirectories(fileLauncher.getParent());
        Files.writeString(fileLauncher, strLauncher);
        Files.setPosixFilePermissions(fileLauncher, PosixFilePermissions.fromString("rwxr-xr-x"));
        Path fileArchive = dir.resolve(strName);
        Process process = new ProcessBuilder("tar", "-czf", fileArchive.toString(), "-C",
                dirSrc.toString(), "splice-node").inheritIO().start();
        assertEquals(0, process.waitFor());
        return fileArchive;
    }


    /** A HAND-FETCHED ARCHIVE LANDS WHERE THE BOX LOOKS, and is copied, not moved. */
    @Test
    void anArchiveInstallsIntoItsVersionDirectory(@TempDir Path dirTemp) throws Exception {
        Path fileArchive = fileArchiveFake(dirTemp, "9.9.9_splice-node.tar.gz");
        Path dirRoot = dirTemp.resolve(".splice");

        Path dirBundle = SpliceAcquire.install(fileArchive, dirRoot, "test", null);

        assertEquals(dirRoot.resolve("9.9.9").resolve("splice-node"), dirBundle);
        assertTrue(Files.isExecutable(dirBundle.resolve("bin").resolve("splice-node")));
        assertTrue(Files.isRegularFile(fileArchive), "the original stays where it was");
        assertTrue(Files.isRegularFile(dirRoot.resolve("9.9.9").resolve("9.9.9_splice-node.tar.gz")));
        String strSource = Files.readString(dirRoot.resolve("9.9.9").resolve("SOURCE.txt"));
        assertTrue(strSource.contains("sha256:   " + SpliceAcquire.strSha256(fileArchive)), strSource);
    }


    @Test
    void anInstalledVersionAndANamelessArchiveAreRefused(@TempDir Path dirTemp) throws Exception {
        Path fileArchive = fileArchiveFake(dirTemp, "9.9.9_splice-node.tar.gz");
        Path dirRoot = dirTemp.resolve(".splice");
        SpliceAcquire.install(fileArchive, dirRoot, "test", null);

        assertThrows(IOException.class, () -> SpliceAcquire.install(fileArchive, dirRoot, "test", null));

        Path fileNameless = Files.copy(fileArchive, dirTemp.resolve("bundle.tar.gz"));
        assertThrows(IOException.class, () -> SpliceAcquire.install(fileNameless, dirRoot, "test", null));
    }


    /**
     * THE POST-INSTALL CHECK - 2026-09-23, Splice 0.8.2. A bundle whose
     * launcher cannot load its log configuration is taken back out: the Splice
     * box lists nothing for it, no `SOURCE.txt` is written, and the archive is
     * kept.
     */
    @Test
    void aBundleThatCannotStartIsNotInstalled(@TempDir Path dirTemp) throws Exception {
        Path fileArchive = fileArchiveFake(dirTemp, "9.9.8_splice-node.tar.gz",
                STR_LAUNCHER_BROKEN);
        Path dirRoot = dirTemp.resolve(".splice");

        IOException ex = assertThrows(IOException.class,
                () -> SpliceAcquire.install(fileArchive, dirRoot, "test", null));

        assertTrue(ex.getMessage().contains("NOT installed"), ex.getMessage());
        assertTrue(ex.getMessage().contains("log configuration"), ex.getMessage());
        assertFalse(Files.exists(dirRoot.resolve("9.9.8").resolve("splice-node")));
        assertFalse(Files.exists(dirRoot.resolve("9.9.8").resolve("SOURCE.txt")));
        assertTrue(Files.isRegularFile(dirRoot.resolve("9.9.8").resolve("9.9.8_splice-node.tar.gz")));
    }
}
