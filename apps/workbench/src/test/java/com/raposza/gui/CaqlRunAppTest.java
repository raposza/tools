// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The argument handling, which is everything this class does before it needs a
 * participant.
 *
 * The distinction being pinned is between CODE_UNUSABLE and CODE_FAILED:
 * headless means something else reads the exit code, and "you invoked me
 * wrongly" is not "the fixture did not apply".
 *
 * Author Claude/bentzn
 */
class CaqlRunAppTest {

    private static Path script(Path dir, String strBody) throws Exception {
        Path file = dir.resolve("fixture.caql");
        Files.writeString(file, strBody);
        return file;
    }


    @Test
    void aMissingScriptArgumentIsUnusable() {
        assertEquals(CaqlRunApp.CODE_UNUSABLE, CaqlRunApp.run(new String[0]));
    }


    @Test
    void aMissingTranscriptArgumentIsUnusable(@TempDir Path dir) throws Exception {
        Path file = script(dir, "a = ALLOCATE PARTY \"A\";\n");
        assertEquals(CaqlRunApp.CODE_UNUSABLE,
                CaqlRunApp.run(new String[] {"--script", file.toString()}));
    }


    @Test
    void aScriptThatDoesNotExistIsUnusable(@TempDir Path dir) {
        assertEquals(CaqlRunApp.CODE_UNUSABLE, CaqlRunApp.run(new String[] {
                "--script", dir.resolve("nope.caql").toString(),
                "--transcript", dir.resolve("out.json").toString()}));
    }


    @Test
    void anUnknownOptionIsUnusable(@TempDir Path dir) throws Exception {
        Path file = script(dir, "a = ALLOCATE PARTY \"A\";\n");
        assertEquals(CaqlRunApp.CODE_UNUSABLE, CaqlRunApp.run(new String[] {
                "--script", file.toString(),
                "--transcript", dir.resolve("out.json").toString(),
                "--wat"}));
    }


    /**
     * A syntax error must not cost a connection, and must never leave a
     * participant half changed because statement 9 would not have parsed. So
     * the script is parsed BEFORE the catalogue is read.
     */
    @Test
    void aSyntaxErrorIsUnusableAndNothingIsWritten(@TempDir Path dir) throws Exception {
        Path file = script(dir, "a = ALLOCATE PARTY \"A\";\nb = THIS IS NOT CAQL;\n");
        Path fileOut = dir.resolve("out.json");

        assertEquals(CaqlRunApp.CODE_UNUSABLE, CaqlRunApp.run(new String[] {
                "--script", file.toString(), "--transcript", fileOut.toString()}));
        assertFalse(Files.exists(fileOut), "a script that did not parse produced a transcript");
    }

}
