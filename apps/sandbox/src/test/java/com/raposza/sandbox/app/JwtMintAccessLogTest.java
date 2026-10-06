// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.runtime.settings.RaposzaSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The provider's access log carries a mint's query, and for an HMAC
 * participant that query carries the participant's secret. So the file lives
 * under the Raposza home, not in the shared temporary directory, and its
 * directory is the owner's alone - 2026-10-05.
 *
 * Author Claude/bentzn
 */
class JwtMintAccessLogTest {

    @Test
    void theLogIsUnderTheRaposzaHomeOnePerPort() {
        Path file = JwtMintProcess.fileAccessLog();
        Path dirAccess = RaposzaSettings.current().dirHome().resolve(JwtMintProcess.STR_DIR_ACCESS);

        assertTrue(file.startsWith(dirAccess), file.toString());
        assertEquals(JwtMintProcess.STR_FILE_ACCESS, file.getFileName().toString());
        assertEquals(dirAccess, file.getParent().getParent());
    }


    @Test
    void theDirectoryIsClosedToEveryoneButTheOwner(@TempDir Path dirTmp) throws IOException {
        Assumptions.assumeTrue(Files.getFileStore(dirTmp)
                .supportsFileAttributeView(PosixFileAttributeView.class), "no POSIX permissions here");
        Path dir = Files.createDirectories(dirTmp.resolve("oidc-access"));
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxrwxrwx"));

        JwtMintProcess.restrictToOwner(dir);

        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)));
    }

}
