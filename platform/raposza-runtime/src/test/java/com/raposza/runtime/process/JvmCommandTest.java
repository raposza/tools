// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.process;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Author Claude/bentzn
 */
class JvmCommandTest {

    @Test
    void theHeapFlagGoesBeforeTheJar() {
        // After -jar it is an APPLICATION argument, silently, which is the
        // mistake this builder exists to remove.
        List<String> lstCmd = JvmCommand.ofJar(Path.of("canton.jar"))
                .heapMb(512)
                .arg("daemon")
                .build();

        assertEquals("java", lstCmd.get(0));
        assertEquals("-Xmx512m", lstCmd.get(1));
        assertEquals("-jar", lstCmd.get(2));
        assertEquals("daemon", lstCmd.get(4));
    }


    @Test
    void theClasspathFormNamesTheMainClassAndNoJar() {
        List<String> lstCmd = JvmCommand
                .ofClasspath(List.of(Path.of("pgshim"), Path.of("canton.jar")), "com.example.App")
                .heapMb(512)
                .arg("sandbox")
                .build();

        assertEquals("java", lstCmd.get(0));
        assertEquals("-Xmx512m", lstCmd.get(1));
        assertEquals("-cp", lstCmd.get(2));
        assertTrue(lstCmd.get(3).contains(File.pathSeparator));
        assertEquals("com.example.App", lstCmd.get(4));
        assertEquals("sandbox", lstCmd.get(5));
        assertFalse(lstCmd.contains("-jar"));
    }


    @Test
    void everyClasspathEntryIsAbsolute() {
        String strClasspath = JvmCommand.strClasspath(List.of(Path.of("pgshim"),
                Path.of("canton.jar")));

        for (String strEntry : strClasspath.split(File.pathSeparator, -1)) {
            assertTrue(Path.of(strEntry).isAbsolute(), strEntry);
        }
    }


    @Test
    void refusesAClasspathWithNothingOnItOrNoMainClass() {
        assertThrows(IllegalArgumentException.class,
                () -> JvmCommand.ofClasspath(List.of(), "com.example.App"));
        assertThrows(IllegalArgumentException.class,
                () -> JvmCommand.ofClasspath(List.of(Path.of("canton.jar")), " "));
        assertThrows(IllegalArgumentException.class, () -> JvmCommand.ofJar(null));
    }
}
