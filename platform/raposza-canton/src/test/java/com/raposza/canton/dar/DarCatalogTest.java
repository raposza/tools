// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.dar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixtures are built here rather than committed, and that is the point: a DAR
 * is a zip with a manifest, so a real one adds several megabytes and proves
 * nothing this cannot. The one thing a synthetic fixture must get right is the
 * 72-byte fold, because that is where hand-parsing breaks.
 *
 * Author Claude/bentzn
 */
class DarCatalogTest {

    private static final String STR_ID_A =
            "aaaa1111bbbb2222cccc3333dddd4444eeee5555ffff6666aaaa7777bbbb8888";

    private static final String STR_ID_B =
            "1111aaaa2222bbbb3333cccc4444dddd5555eeee6666ffff7777aaaa8888bbbb";


    @Test
    void readsPackageIdNameAndVersion(@TempDir Path dir) throws IOException {
        writeDar(dir.resolve("model.dar"), "model-1.0.0-" + STR_ID_A + ".dalf");

        DarCatalog catalog = DarCatalog.scan(dir);
        assertEquals(1, catalog.cntDars());
        DarInfo info = catalog.lstDars().get(0);
        assertEquals(STR_ID_A, info.strPkgId());
        assertEquals("model", info.strName());
        assertEquals("1.0.0", info.strVersion());
        assertEquals("model.dar", info.strFileName());
        assertEquals("model-1.0.0", info.strLabel());
    }


    @Test
    void theNameKeepsItsOwnDashes(@TempDir Path dir) throws IOException {
        // Greedy split, so `my-model` is the name and `2.1.0` the version.
        writeDar(dir.resolve("m.dar"), "my-model-2.1.0-" + STR_ID_A + ".dalf");

        DarInfo info = DarCatalog.scan(dir).lstDars().get(0);
        assertEquals("my-model", info.strName());
        assertEquals("2.1.0", info.strVersion());
    }


    @Test
    void aMainDalfUnderAPathStillResolves(@TempDir Path dir) throws IOException {
        // Daml writes the main dalf inside a directory named after itself.
        writeDar(dir.resolve("m.dar"),
                "model-1.0.0-" + STR_ID_A + "/model-1.0.0-" + STR_ID_A + ".dalf");

        DarInfo info = DarCatalog.scan(dir).lstDars().get(0);
        assertEquals(STR_ID_A, info.strPkgId());
        assertEquals("model", info.strName());
    }


    @Test
    void aFoldedManifestLineIsNotTruncated(@TempDir Path dir) throws IOException {
        // The whole reason java.util.jar.Manifest is used. This value is well
        // past 72 bytes, so it arrives as a continuation line.
        String strMainDalf = "a-very-long-daml-project-name-that-forces-the-fold-1.0.0-"
                + STR_ID_A + ".dalf";
        writeDarFolded(dir.resolve("long.dar"), strMainDalf);

        DarInfo info = DarCatalog.scan(dir).lstDars().get(0);
        assertEquals(STR_ID_A, info.strPkgId());
        assertEquals("a-very-long-daml-project-name-that-forces-the-fold", info.strName());
        assertEquals("1.0.0", info.strVersion());
    }


    @Test
    void scanIsAlphabeticalAndNotFilesystemOrder(@TempDir Path dir) throws IOException {
        writeDar(dir.resolve("zulu.dar"), "zulu-1.0.0-" + STR_ID_A + ".dalf");
        writeDar(dir.resolve("alpha.dar"), "alpha-1.0.0-" + STR_ID_B + ".dalf");

        DarCatalog catalog = DarCatalog.scan(dir);
        assertEquals("alpha.dar", catalog.lstDars().get(0).strFileName());
        assertEquals("zulu.dar", catalog.lstDars().get(1).strFileName());
        assertEquals(2, catalog.lstFiles().size());
    }


    @Test
    void nonDarFilesAreIgnored(@TempDir Path dir) throws IOException {
        writeDar(dir.resolve("model.dar"), "model-1.0.0-" + STR_ID_A + ".dalf");
        Files.writeString(dir.resolve("notes.txt"), "not a dar");
        Files.writeString(dir.resolve("model.dar.bak"), "not a dar either");

        assertEquals(1, DarCatalog.scan(dir).cntDars());
    }


    @Test
    void twoVersionsOfOneNameAreLegitimate(@TempDir Path dir) throws IOException {
        // Exactly what Smart Contract Upgrades stage. Not a duplicate.
        writeDar(dir.resolve("model-1.dar"), "model-1.0.0-" + STR_ID_A + ".dalf");
        writeDar(dir.resolve("model-2.dar"), "model-2.0.0-" + STR_ID_B + ".dalf");

        assertEquals(2, DarCatalog.scan(dir).cntDars());
    }


    @Test
    void onePackageStagedTwiceNamesBothFiles(@TempDir Path dir) throws IOException {
        writeDar(dir.resolve("model.dar"), "model-1.0.0-" + STR_ID_A + ".dalf");
        writeDar(dir.resolve("model-copy.dar"), "model-1.0.0-" + STR_ID_A + ".dalf");

        DarException ex = assertThrows(DarException.class, () -> DarCatalog.scan(dir));
        assertTrue(ex.getMessage().contains("model.dar"), ex.getMessage());
        assertTrue(ex.getMessage().contains("model-copy.dar"), ex.getMessage());
    }


    @Test
    void aVersionlessMainDalfKeepsItsPackageIdAndNothingElse(@TempDir Path dir)
            throws IOException {
        // daml-prim and friends do not follow name-version-hash. An absent
        // name is the right answer; a name of `daml-prim-DA-Internal` with a
        // version of `Down` would be a field that looks like an answer.
        writeDar(dir.resolve("prim.dar"), "daml-prim-DA-Internal-Down-" + STR_ID_A + ".dalf");

        DarCatalog catalog = DarCatalog.scan(dir);
        DarInfo info = catalog.lstDars().get(0);
        assertEquals(STR_ID_A, info.strPkgId());
        assertNull(info.strName());
        assertNull(info.strVersion());
        assertEquals("prim.dar", info.strLabel());
        assertNotNull(catalog.byPackageId(STR_ID_A));
    }


    @Test
    void aDarWithNoManifestIsNamedInTheFailure(@TempDir Path dir) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(dir.resolve("empty.dar")))) {
            zip.putNextEntry(new ZipEntry("nothing.txt"));
            zip.write("x".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        DarException ex = assertThrows(DarException.class, () -> DarCatalog.scan(dir));
        assertTrue(ex.getMessage().contains("empty.dar"), ex.getMessage());
    }


    @Test
    void aManifestWithoutMainDalfIsNamedInTheFailure(@TempDir Path dir) throws IOException {
        writeZip(dir.resolve("nomain.dar"), "Manifest-Version: 1.0\r\nFormat: daml-lf\r\n\r\n");

        DarException ex = assertThrows(DarException.class, () -> DarCatalog.scan(dir));
        assertTrue(ex.getMessage().contains("Main-Dalf"), ex.getMessage());
        assertTrue(ex.getMessage().contains("nomain.dar"), ex.getMessage());
    }


    @Test
    void aMainDalfWithNoHashIsNamedInTheFailure(@TempDir Path dir) throws IOException {
        writeDar(dir.resolve("bad.dar"), "model-1.0.0.dalf");

        DarException ex = assertThrows(DarException.class, () -> DarCatalog.scan(dir));
        assertTrue(ex.getMessage().contains("bad.dar"), ex.getMessage());
    }


    @Test
    void anAbsentDirectoryIsRefusedRatherThanReportedEmpty(@TempDir Path dir) {
        // A configured-but-missing staging directory is an operator error. The
        // caller decides whether one was configured at all; this never guesses.
        Path dirMissing = dir.resolve("nope");
        assertThrows(DarException.class, () -> DarCatalog.scan(dirMissing));
    }


    @Test
    void anEmptyDirectoryIsAnEmptyCatalogue(@TempDir Path dir) {
        DarCatalog catalog = DarCatalog.scan(dir);
        assertTrue(catalog.flagEmpty());
        assertEquals(0, catalog.cntDars());
        assertNull(catalog.byPackageId(STR_ID_A));
        assertTrue(catalog.toString().contains("0"));
    }


    private static void writeDar(Path fileDar, String strMainDalf) throws IOException {
        writeZip(fileDar, "Manifest-Version: 1.0\r\n"
                + "Format: daml-lf\r\n"
                + "Main-Dalf: " + strMainDalf + "\r\n"
                + "\r\n");
    }


    /**
     * Folds the `Main-Dalf` value the way the jar tooling does: continuation
     * lines start with a single space and the reader is expected to rejoin
     * them.
     */
    private static void writeDarFolded(Path fileDar, String strMainDalf) throws IOException {
        StringBuilder sb = new StringBuilder("Manifest-Version: 1.0\r\nMain-Dalf: ");
        String strHead = "Main-Dalf: ";
        int nRoom = 70 - strHead.length();
        sb.append(strMainDalf, 0, nRoom).append("\r\n");
        int nPos = nRoom;
        while (nPos < strMainDalf.length()) {
            int nEnd = Math.min(nPos + 69, strMainDalf.length());
            sb.append(' ').append(strMainDalf, nPos, nEnd).append("\r\n");
            nPos = nEnd;
        }
        sb.append("\r\n");
        writeZip(fileDar, sb.toString());
    }


    private static void writeZip(Path fileDar, String strManifest) throws IOException {
        try (OutputStream out = Files.newOutputStream(fileDar);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write(strManifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

}
