// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * R1's definition of a first start, his words of 2026-10-30: no settings file,
 * or no Raposza directory.
 *
 * Author Claude/bentzn
 */
class FirstStartTest {

    @TempDir
    Path dirTmp;


    @Test
    void noSettingsFileIsAFirstStart() throws IOException {
        Path dirHome = Files.createDirectories(dirTmp.resolve("raposza"));
        String strWhy = FirstStart.strWhyFirst(dirTmp.resolve("settings.properties"), dirHome);
        assertNotNull(strWhy);
        assertTrue(strWhy.contains("settings file"), strWhy);
    }


    @Test
    void noRaposzaDirectoryIsAFirstStart() throws IOException {
        Path fileSettings = Files.writeString(dirTmp.resolve("settings.properties"), "");
        String strWhy = FirstStart.strWhyFirst(fileSettings, dirTmp.resolve("raposza"));
        assertNotNull(strWhy);
        assertTrue(strWhy.contains("Raposza directory"), strWhy);
    }


    @Test
    void bothThereIsNotAFirstStart() throws IOException {
        Path fileSettings = Files.writeString(dirTmp.resolve("settings.properties"), "");
        Path dirHome = Files.createDirectories(dirTmp.resolve("raposza"));
        assertNull(FirstStart.strWhyFirst(fileSettings, dirHome));
    }
}
