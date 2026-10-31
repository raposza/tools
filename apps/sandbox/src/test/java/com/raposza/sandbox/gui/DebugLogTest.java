// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ONE FILE WITH EVERYTHING - his instruction, 2026-10-30. The header, the
 * console, every pane by its tab name, in the order produced, and only the
 * newest files kept.
 *
 * THE CONTROL CAN FAIL: before 2026-10-30 no file existed, and a pane line
 * reached nothing but the pane.
 *
 * Author Claude/bentzn
 */
class DebugLogTest {

    @TempDir
    Path dirTmp;


    @AfterEach
    void forget() {
        DebugLog.closeForTest();
    }


    private Path openHere() throws Exception {
        Path fileSettings = dirTmp.resolve("settings.properties");
        Files.writeString(fileSettings, "dir.dpm=C:\\\\dpm\n", StandardCharsets.UTF_8);
        Path file = DebugLog.open(dirTmp.resolve("logs"), new String[] { "--canton", "2.9.6" },
                fileSettings);
        assertNotNull(file);
        return file;
    }


    private static String strRead(Path file) throws Exception {
        DebugLog.closeForTest();
        return Files.readString(file, StandardCharsets.UTF_8);
    }


    @Test
    void theHeaderSaysWhatRanWhereAndOn() throws Exception {
        Path file = openHere();

        String strOut = strRead(file);

        assertTrue(strOut.startsWith("Raposza Sandbox - debug log\n"), strOut);
        assertTrue(strOut.contains("java      " + System.getProperty("java.version")), strOut);
        assertTrue(strOut.contains("args      [--canton, 2.9.6]"), strOut);
        assertTrue(strOut.contains("env       PATH="), strOut);
        assertTrue(strOut.contains("dir.dpm=C:\\\\dpm"), strOut);
    }


    @Test
    void everySourceLandsInOrderAndTagged() throws Exception {
        DebugLog.line(DebugLog.SRC_CONSOLE, "before the file was open");
        Path file = openHere();
        DebugLog.line(DebugLog.SRC_CONSOLE, "Exception in thread \"AWT-EventQueue-0\"");
        LogPane paneMain = new LogPane(false).useDebugLog("Main");
        paneMain.append("Starting the Sandbox");
        paneMain.appendSpinning("Starting Canton");
        paneMain.replaceSpinning("Starting Canton 40 %");
        // THE TEE'S OWN LINES reach the file once, as console, not again
        // through the pane they are routed to.
        LogPane panePg = new LogPane().useDebugLog("PostGres");
        panePg.append("routed from the console", false);
        panePg.append("from the pane");

        String strOut = strRead(file);
        List<String> lstLine = new ArrayList<>();
        for (String strLine : strOut.split("\n")) {
            int idx = strLine.indexOf("Z [");
            if (idx > 0)
                lstLine.add(strLine.substring(idx + 2));
        }

        assertEquals(List.of("[console] before the file was open",
                "[console] Exception in thread \"AWT-EventQueue-0\"",
                "[Main] Starting the Sandbox", "[Main] Starting Canton",
                "[Main] Starting Canton 40 %", "[PostGres] from the pane"), lstLine);
    }


    @Test
    void onlyTheNewestAreKept() throws Exception {
        Path dirLogs = Files.createDirectories(dirTmp.resolve("logs"));
        for (int idx = 10; idx < 25; idx++) {
            Files.writeString(dirLogs.resolve(DebugLog.STR_PREFIX + "202610" + idx + "T000000Z-1"
                    + DebugLog.STR_SUFFIX), "x");
        }
        Files.writeString(dirLogs.resolve("other.log"), "not ours");

        Path file = DebugLog.open(dirLogs, new String[0], null);

        List<Path> lstLeft;
        try (Stream<Path> stream = Files.list(dirLogs)) {
            lstLeft = stream.filter(path -> path.getFileName().toString()
                    .startsWith(DebugLog.STR_PREFIX)).sorted().toList();
        }
        assertEquals(DebugLog.CNT_KEEP, lstLeft.size(), lstLeft.toString());
        assertTrue(lstLeft.contains(file), lstLeft.toString());
        assertTrue(lstLeft.contains(dirLogs.resolve(DebugLog.STR_PREFIX + "20261024T000000Z-1"
                + DebugLog.STR_SUFFIX)), lstLeft.toString());
        assertTrue(Files.exists(dirLogs.resolve("other.log")));
    }
}
