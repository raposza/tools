// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.topology;

import com.raposza.canton.dar.DarException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Author Claude/bentzn
 */
class SandboxSpecTest {

    private static final String STR_ID_A =
            "aaaa1111bbbb2222cccc3333dddd4444eeee5555ffff6666aaaa7777bbbb8888";

    private static final String STR_ID_B =
            "1111aaaa2222bbbb3333cccc4444dddd5555eeee6666ffff7777aaaa8888bbbb";


    @Test
    void defaultsCarryNoDars() {
        assertTrue(SandboxSpec.ofDefaults().lstFileDar().isEmpty());
    }


    @Test
    void theDarListIsCopiedAndNotShared() {
        List<Path> lstFile = new ArrayList<>();
        lstFile.add(Path.of("a.dar"));
        SandboxSpec spec = SandboxSpec.ofDefaults().withDars(lstFile);

        lstFile.add(Path.of("b.dar"));
        assertEquals(1, spec.lstFileDar().size());
        assertThrows(UnsupportedOperationException.class,
                () -> spec.lstFileDar().add(Path.of("c.dar")));
    }


    @Test
    void withDarsFromTakesTheWholeDirectoryInCatalogueOrder(@TempDir Path dir)
            throws IOException {
        writeDar(dir.resolve("zulu.dar"), "zulu-1.0.0-" + STR_ID_A + ".dalf");
        writeDar(dir.resolve("alpha.dar"), "alpha-1.0.0-" + STR_ID_B + ".dalf");

        SandboxSpec spec = SandboxSpec.ofDefaults().withDarsFrom(dir);

        assertEquals(2, spec.lstFileDar().size());
        assertEquals("alpha.dar", spec.lstFileDar().get(0).getFileName().toString());
        assertEquals("zulu.dar", spec.lstFileDar().get(1).getFileName().toString());
    }


    @Test
    void withDarsFromKeepsEverythingElse(@TempDir Path dir) {
        SandboxSpec spec = SandboxSpec.ofDefaults().withHeapMb(512).withDarsFrom(dir);

        assertEquals(512, spec.nHeapMb());
        assertTrue(spec.lstFileDar().isEmpty());
    }


    @Test
    void anAbsentDarDirectoryIsRefused(@TempDir Path dir) {
        // Not "no DARs". A configured directory that is not there is an error.
        Path dirMissing = dir.resolve("nope");
        assertThrows(DarException.class, () -> SandboxSpec.ofDefaults().withDarsFrom(dirMissing));
    }


    private static void writeDar(Path fileDar, String strMainDalf) throws IOException {
        String strManifest = "Manifest-Version: 1.0\r\n"
                + "Format: daml-lf\r\n"
                + "Main-Dalf: " + strMainDalf + "\r\n"
                + "\r\n";
        try (OutputStream out = Files.newOutputStream(fileDar);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write(strManifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

}
