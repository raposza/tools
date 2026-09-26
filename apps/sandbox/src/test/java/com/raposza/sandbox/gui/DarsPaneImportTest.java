// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * `Import...` on Sandbox Simple's DARs tab - section 9's break 1, checked at
 * import rather than at the far end of a start.
 *
 * EACH REFUSAL HAS A CONTROL THAT CAN FAIL: the store is read back byte for
 * byte after every refusal, so a check that copied anyway is caught.
 *
 * Author Claude/bentzn
 */
class DarsPaneImportTest {

    private static final String STR_ID_A = "a".repeat(64);

    private static final String STR_ID_B = "b".repeat(64);


    @Test
    void aReadableDarIsCopiedAndNamedByItsManifest(@TempDir Path dirTmp) throws IOException {
        Path fileDar = writeDar(dirTmp.resolve("model.dar"), "model-1.0.0-" + STR_ID_A);
        Path dirStore = dirTmp.resolve("run").resolve("dars");

        DarsPane.Imported imported = DarsPane.importInto(fileDar, dirStore);

        assertTrue(imported.flagOk(), imported.strSaid());
        assertEquals("model.dar", imported.strFileName());
        assertEquals("model-1.0.0 imported and ticked", imported.strSaid());
        assertArrayEquals(Files.readAllBytes(fileDar),
                Files.readAllBytes(dirStore.resolve("model.dar")));
        // A SECOND IMPORT of the same file is not a refusal.
        assertTrue(DarsPane.importInto(fileDar, dirStore).flagOk());
    }


    @Test
    void aFileWithoutAManifestIsRefusedAndNotCopied(@TempDir Path dirTmp) throws IOException {
        Path fileBad = Files.write(dirTmp.resolve("junk.dar"), new byte[] { 1, 2, 3 });
        Path dirStore = Files.createDirectories(dirTmp.resolve("dars"));

        DarsPane.Imported imported = DarsPane.importInto(fileBad, dirStore);

        assertFalse(imported.flagOk());
        assertNull(imported.strFileName());
        assertTrue(imported.strSaid().startsWith("junk.dar NOT imported"), imported.strSaid());
        assertFalse(Files.exists(dirStore.resolve("junk.dar")));
    }


    /** A start refuses one package under two names, so the store must not hold one. */
    @Test
    void aPackageTheStoreHoldsUnderAnotherNameIsRefused(@TempDir Path dirTmp)
            throws IOException {
        Path dirStore = Files.createDirectories(dirTmp.resolve("dars"));
        writeDar(dirStore.resolve("kept.dar"), "model-1.0.0-" + STR_ID_A);
        Path fileDar = writeDar(dirTmp.resolve("renamed.dar"), "model-1.0.0-" + STR_ID_A);

        DarsPane.Imported imported = DarsPane.importInto(fileDar, dirStore);

        assertFalse(imported.flagOk());
        assertTrue(imported.strSaid().contains("already in the store as kept.dar"),
                imported.strSaid());
        assertFalse(Files.exists(dirStore.resolve("renamed.dar")));
    }


    @Test
    void aDifferentFileOfTheSameNameIsNeverOverwritten(@TempDir Path dirTmp)
            throws IOException {
        Path dirStore = Files.createDirectories(dirTmp.resolve("dars"));
        Path fileHeld = writeDar(dirStore.resolve("model.dar"), "model-1.0.0-" + STR_ID_A);
        byte[] arrHeld = Files.readAllBytes(fileHeld);
        Path dirOther = Files.createDirectories(dirTmp.resolve("other"));
        Path fileDar = writeDar(dirOther.resolve("model.dar"), "model-2.0.0-" + STR_ID_B);

        DarsPane.Imported imported = DarsPane.importInto(fileDar, dirStore);

        assertFalse(imported.flagOk());
        assertTrue(imported.strSaid().contains("NOT copied"), imported.strSaid());
        assertArrayEquals(arrHeld, Files.readAllBytes(dirStore.resolve("model.dar")));
    }


    @Test
    void noVersionSelectedIsSaidRatherThanGuessed(@TempDir Path dirTmp) throws IOException {
        Path fileDar = writeDar(dirTmp.resolve("model.dar"), "model-1.0.0-" + STR_ID_A);

        DarsPane.Imported imported = DarsPane.importInto(fileDar, null);

        assertFalse(imported.flagOk());
        assertTrue(imported.strSaid().contains("select a Canton first"), imported.strSaid());
    }


    /**
     * The shape `DarCatalogTest` writes: a zip holding a manifest whose
     * `Main-Dalf` is `name-version-pkgid.dalf`.
     */
    private static Path writeDar(Path fileDar, String strDalfStem) throws IOException {
        String strManifest = "Manifest-Version: 1.0\r\n"
                + "Format: daml-lf\r\n"
                + "Main-Dalf: " + strDalfStem + ".dalf\r\n"
                + "\r\n";
        try (OutputStream out = Files.newOutputStream(fileDar);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write(strManifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return fileDar;
    }

}
