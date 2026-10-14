// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * The staging root as HOCON gets it - `todo.md` W-14, D-601. A Windows path
 * written with backslashes into a quoted HOCON string turns `\n` into a newline
 * and fails on `\U`; written with forward slashes it parses and Windows opens
 * it unchanged.
 *
 * Author Claude/bentzn
 */
class LocalNetStagerSlashTest {

    /** A Windows root: every backslash goes, nothing else moves. */
    @Test
    void aWindowsRootIsWrittenWithForwardSlashes() {
        assertEquals("C:/Users/rztest/.splice/native-localnet/canton/app",
                LocalNetStager.strForwardSlashes(
                        Path.of("C:\\Users\\rztest\\.splice\\native-localnet\\canton\\app")));
    }


    /** A Linux root is written exactly as it was. */
    @Test
    void aLinuxRootIsUnchanged() {
        assertEquals("/home/bentzn/.splice/native-localnet/canton/app",
                LocalNetStager.strForwardSlashes(
                        Path.of("/home/bentzn/.splice/native-localnet/canton/app")));
    }

}
