// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * `--fixture` as an ARGUMENT: what it is, what the parser is given instead,
 * and what the DAR appends. Nothing here builds a DAR or starts a stack.
 *
 * Author Claude/bentzn
 */
class FixtureStartTest {

    @Test
    void itIsRecognisedAndStripped() {
        String[] arrArg = { "--canton", "3.5.14", "--fixture", "--ping" };

        assertTrue(FixtureStart.flagIn(arrArg));
        assertArrayEquals(new String[] { "--canton", "3.5.14", "--ping" },
                FixtureStart.without(arrArg));
    }


    @Test
    void anOrdinaryLineIsUntouched() {
        String[] arrArg = { "--canton", "3.5.14" };

        assertFalse(FixtureStart.flagIn(arrArg));
        assertArrayEquals(arrArg, FixtureStart.without(arrArg));
    }


    @Test
    void aNullLineIsNotAFixture() {
        assertFalse(FixtureStart.flagIn(null));
        assertEquals(0, FixtureStart.without(null).length);
    }


    /**
     * THE PARSER REFUSES WHAT IT DOES NOT KNOW, which is the whole reason the
     * switch is stripped rather than carried in the record.
     */
    @Test
    void theParserWouldRefuseTheSwitch() {
        assertThrows(IllegalArgumentException.class,
                () -> SandboxOptions.parse(new String[] { "--fixture" }));

        SandboxOptions options = SandboxOptions.parse(
                FixtureStart.without(new String[] { "--fixture", "--ping" }));
        assertTrue(options.flagPing());
    }


    @Test
    void theDarIsAppendedAsAnOrdinaryUpload() {
        Path fileDar = Paths.get("/tmp/aviation-0.0.1.dar");

        assertArrayEquals(new String[] { "--ping", "--dar", fileDar.toString() },
                FixtureStart.withDar(new String[] { "--ping" }, fileDar));
        assertArrayEquals(new String[] { "--dar", fileDar.toString() },
                FixtureStart.withDar(null, fileDar));
    }


    @Test
    void aDarIsRequiredToAppendOne() {
        assertThrows(IllegalArgumentException.class,
                () -> FixtureStart.withDar(new String[0], null));
    }


    @Test
    void nothingIsResolvedForAnAbsentInstallation() {
        assertNull(FixtureStart.strSdkFor(null));
        assertThrows(IllegalArgumentException.class, () -> FixtureStart.dirProjectFor(null));
    }

}
