// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lf;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.LedgerException;
import com.raposza.spi.LfDecoder_i;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The class split, without a Canton jar: the test classpath carries the
 * build's reader, and an empty jar list makes every non-own class fall back to
 * it. What a Canton jar adds is measured by the private probe, not here.
 *
 * Author Claude/bentzn
 */
class LfReaderLoaderTest {

    /**
     * The decoder is defined in the child, and it is still the interface the
     * caller holds - the shared types come from the parent.
     */
    @Test
    void theDecoderIsDefinedInTheChildAndSharesTheInterface(@TempDir Path dir) throws IOException {
        Path fileJar = emptyJar(dir.resolve("empty.jar"));

        LfDecoder_i decoder = LfReaderLoader.decoderFrom(List.of(fileJar));

        assertNotSame(LfDecoder.class, decoder.getClass());
        assertNotSame(LfReaderLoaderTest.class.getClassLoader(), decoder.getClass().getClassLoader());
        assertSame(LfDecoder_i.class, decoder.getClass().getInterfaces()[0]);
    }


    /** LedgerException crosses the boundary as the caller's own class. */
    @Test
    void aRefusalIsTheCallersLedgerException(@TempDir Path dir) throws IOException {
        LfDecoder_i decoder = LfReaderLoader.decoderFrom(List.of(emptyJar(dir.resolve("e.jar"))));

        assertThrows(LedgerException.class, () -> decoder.decode(new byte[0]));
    }


    /**
     * CHILD FIRST: a class the parent also has is taken from the jar. The
     * bytes of the parent's own `scala.Tuple2` are put in a jar; loaded
     * through the child it is a different class.
     */
    @Test
    void aClassInTheJarWinsOverTheParents(@TempDir Path dir) throws IOException,
            ClassNotFoundException {
        Path fileJar = dir.resolve("tuple.jar");
        try (InputStream in = LfReaderLoaderTest.class.getClassLoader()
                .getResourceAsStream("scala/Tuple2.class");
                ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(fileJar))) {
            zip.putNextEntry(new ZipEntry("scala/Tuple2.class"));
            in.transferTo(zip);
            zip.closeEntry();
        }

        ClassLoader loader = LfReaderLoader.loaderOver(List.of(fileJar));
        Class<?> cls = loader.loadClass("scala.Tuple2");

        assertSame(loader, cls.getClassLoader());
        assertNotSame(scala.Tuple2.class, cls);
    }


    /** The model and SPI are never redefined, whatever the jar carries. */
    @Test
    void theSharedTypesComeFromTheParent(@TempDir Path dir) throws IOException,
            ClassNotFoundException {
        ClassLoader loader = LfReaderLoader.loaderOver(List.of(emptyJar(dir.resolve("e.jar"))));

        assertSame(LedgerException.class, loader.loadClass(LedgerException.class.getName()));
        assertSame(LfDecoder_i.class, loader.loadClass(LfDecoder_i.class.getName()));
    }


    @Test
    void aMissingJarIsRefusedByName(@TempDir Path dir) {
        Path fileGone = dir.resolve("gone.jar");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> LfReaderLoader.decoderFrom(List.of(fileGone)));
        assertTrue(ex.getMessage().contains("gone.jar"));
    }


    private static Path emptyJar(Path fileJar) throws IOException {
        try (OutputStream out = Files.newOutputStream(fileJar);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write("Manifest-Version: 1.0\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            zip.closeEntry();
        }
        return fileJar;
    }

}
