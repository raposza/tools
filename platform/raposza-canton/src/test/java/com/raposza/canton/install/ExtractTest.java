// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Author Claude/bentzn
 */
class ExtractTest {

    @Test
    void theCommandNamesTheArchiveTheDestinationAndTheStrip() {
        List<String> lstCmd = Extract.lstCommand(Path.of("/tmp", "a.tar.gz"),
                Path.of("/tmp", "into"));

        assertEquals(List.of("tar", "-xf", Path.of("/tmp", "a.tar.gz").toString(),
                "-C", Path.of("/tmp", "into").toString(), "--strip-components", "1"), lstCmd);
    }


    /**
     * Naming the codec is what would make this two command lines - the flag
     * that is right for a tarball is wrong for the zip, and the tool detects
     * either.
     */
    @Test
    void noCompressionFlagIsPassedForEitherArchiveKind() {
        List<String> lstTar = Extract.lstCommand(Path.of("a.tar.gz"), Path.of("into"));
        List<String> lstZip = Extract.lstCommand(Path.of("a.zip"), Path.of("into"));

        assertFalse(lstTar.contains("-z"));
        assertFalse(lstTar.contains("-xzf"));
        assertEquals(lstTar.size(), lstZip.size());
        assertEquals(lstTar.get(1), lstZip.get(1));
    }

}
