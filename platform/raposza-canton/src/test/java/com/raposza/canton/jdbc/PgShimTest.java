// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.jdbc;

import com.raposza.canton.install.HostPlatform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Author Claude/bentzn
 */
class PgShimTest {

    /** The first four bytes of every class file ever compiled. */
    private static final int N_MAGIC = 0xCAFEBABE;


    @Test
    void itIsForWindowsAndNowhereElse() {
        assertTrue(PgShim.flagNeeded(HostPlatform.WINDOWS_X64));
        assertFalse(PgShim.flagNeeded(HostPlatform.LINUX_X64));
        assertThrows(IllegalArgumentException.class, () -> PgShim.flagNeeded(null));
    }


    @Test
    void theDataSourceClassFollowsThePlatform() {
        assertEquals("com.raposza.canton.jdbc.WinPgDataSource",
                PgShim.strDataSourceClass(HostPlatform.WINDOWS_X64));
        assertEquals(PgShim.STR_CLASS_STOCK, PgShim.strDataSourceClass(HostPlatform.LINUX_X64));
    }


    @Test
    void extractWritesEveryClassFileTheShimNeeds(@TempDir Path dirWork) throws IOException {
        Path dirShim = PgShim.extract(dirWork);

        assertEquals(dirWork.toAbsolutePath().normalize().resolve("pgshim"), dirShim);
        Path dirPackage = dirShim.resolve("com").resolve("raposza").resolve("canton")
                .resolve("jdbc");
        for (String strName : new String[] { "WinPgDataSource.class",
                "WinPgDataSource$HandlerCall.class" }) {
            Path file = dirPackage.resolve(strName);
            assertTrue(Files.isRegularFile(file), strName);
            byte[] arrByte = Files.readAllBytes(file);
            assertTrue(arrByte.length > 4, strName);
            assertEquals(N_MAGIC, ByteBuffer.wrap(arrByte).getInt(), strName);
        }
    }


    @Test
    void extractCanBeRunTwice(@TempDir Path dirWork) throws IOException {
        PgShim.extract(dirWork);
        assertEquals(PgShim.dirShim(dirWork), PgShim.extract(dirWork));
    }


    @Test
    void nothingIsActiveUntilTheClassFilesArePlaced(@TempDir Path dirWork) {
        // No jar and no extracted directory, so no launcher may take the
        // classpath form - on any platform.
        assertFalse(PgShim.flagActive(dirWork, dirWork.resolve("canton.jar")));
    }


    @Test
    void theShimComesFirstOnTheClasspath(@TempDir Path dirWork) {
        Path fileJar = dirWork.resolve("canton-open-source.jar");
        String strClasspath = PgShim.strClasspath(dirWork, fileJar);

        assertTrue(strClasspath.startsWith(PgShim.dirShim(dirWork).toString()));
        assertTrue(strClasspath.endsWith(fileJar.toAbsolutePath().normalize().toString()));
        assertEquals(2, strClasspath.split(File.pathSeparator, -1).length);
    }
}
