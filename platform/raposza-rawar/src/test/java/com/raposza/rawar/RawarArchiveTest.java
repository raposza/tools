// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.rawar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One identity for a directory, a `.rawar` and an unpacked tree; byte-identical
 * exports; and every refusal able to fire - a control that cannot fail proves
 * nothing.
 *
 * Author Claude/bentzn
 */
class RawarArchiveTest {

    @TempDir
    Path dirTmp;


    private Path dirRawar(String strName) throws IOException {
        Path dir = dirTmp.resolve("src").resolve(strName);
        Files.createDirectories(dir.resolve("css"));
        Files.createDirectories(dir.resolve(".git"));
        Files.createDirectories(dir.resolve("js/__pycache__"));
        Files.writeString(dir.resolve("index.html"), "<html><a href='./css/a.css'>x</a></html>");
        Files.writeString(dir.resolve("css/a.css"), "body{}");
        Files.writeString(dir.resolve("js/app.js"), "fetch('./_ledger/v2/version')");
        Files.writeString(dir.resolve(".git/HEAD"), "ref: refs/heads/main");
        Files.writeString(dir.resolve("js/__pycache__/x.pyc"), "junk");
        return dir;
    }


    private static RawarDescriptor meta(String strName) {
        return new RawarDescriptor(RawarDescriptor.N_FORMAT, strName, "0.1.0", null, "/",
                new RawarDescriptor.BuiltWith("3.5.11", "3.5.18", "0.8.3", "sandbox-localnetnd", "2026-10-02T12:00:00Z"),
                List.of(new RawarDescriptor.Dar("aviation", "1.2.0", "deadbeef")), null);
    }


    @Test
    void theTreeHashIgnoresOrderTimestampsHiddenEntriesAndTheDescriptor() throws IOException {
        Path dir = dirRawar("one");
        String strHash1 = RawarTree.strHash(dir);
        assertEquals(List.of("css/a.css", "index.html", "js/app.js"), RawarTree.lstFile(dir));

        Files.setLastModifiedTime(dir.resolve("index.html"), java.nio.file.attribute.FileTime.fromMillis(0));
        Files.writeString(dir.resolve("rawar.json"), "{\"anything\": true}");
        Files.writeString(dir.resolve(".DS_Store"), "mac");
        assertEquals(strHash1, RawarTree.strHash(dir), "mtime, rawar.json and hidden entries must not move the hash");

        Files.writeString(dir.resolve("css/a.css"), "body{color:red}");
        assertNotEquals(strHash1, RawarTree.strHash(dir), "one byte must move the hash");
    }


    @Test
    void exportUnpackVerifyIsOneIdentity() throws IOException {
        Path dir = dirRawar("desk");
        Path fileRawar = RawarArchive.export(dir, meta("desk"), dirTmp.resolve("out"));
        assertEquals("desk.rawar", fileRawar.getFileName().toString());

        RawarDescriptor descWritten = RawarDescriptor.read(dir);
        assertEquals(RawarTree.strHash(dir), descWritten.strTreeSha256());
        assertEquals("deadbeef", descWritten.lstDar().get(0).strPackageId());

        Path dirUnpacked = RawarArchive.unpack(fileRawar, dirTmp.resolve("host"));
        assertEquals(dirTmp.resolve("host/desk"), dirUnpacked);
        assertEquals(descWritten.strTreeSha256(), RawarTree.strHash(dirUnpacked));
        assertEquals(descWritten, RawarArchive.verify(dirUnpacked, "desk"));
        assertFalse(Files.exists(dirUnpacked.resolve(".git")), "hidden entries are not archived");
        assertEquals("body{}", Files.readString(dirUnpacked.resolve("css/a.css")));
    }


    @Test
    void twoExportsOfTheSameTreeAreByteIdentical() throws IOException {
        Path dirA = dirRawar("same");
        Path fileA = RawarArchive.export(dirA, meta("same"), dirTmp.resolve("outA"));
        byte[] arrA = Files.readAllBytes(fileA);

        Path dirB = dirTmp.resolve("copy/same");
        Files.createDirectories(dirB.getParent());
        copyTree(dirA, dirB);
        Files.setLastModifiedTime(dirB.resolve("index.html"), java.nio.file.attribute.FileTime.fromMillis(0));
        Path fileB = RawarArchive.export(dirB, meta("same"), dirTmp.resolve("outB"));
        assertArrayEquals(arrA, Files.readAllBytes(fileB));
    }


    @Test
    void aMutatedArchiveIsRefusedAndLeavesNothingBehind() throws IOException {
        Path dir = dirRawar("mut");
        Path fileRawar = RawarArchive.export(dir, meta("mut"), dirTmp.resolve("out"));
        Files.writeString(dir.resolve("css/a.css"), "body{color:red}");
        RawarArchive.write(dir, RawarDescriptor.read(dir), fileRawar);

        RawarException e = assertThrows(RawarException.class,
                () -> RawarArchive.unpack(fileRawar, dirTmp.resolve("host")));
        assertTrue(e.getMessage().contains("TREE HASH MISMATCH"), e.getMessage());
        assertFalse(Files.exists(dirTmp.resolve("host/mut")), "a refused unpack leaves no tree");
    }


    @Test
    void aTreeWithoutIndexOrWithAReservedNameIsNotExportable() throws IOException {
        Path dir = dirRawar("bad");
        Files.createDirectories(dir.resolve("_ledger"));
        Files.writeString(dir.resolve("_ledger/v2"), "x");
        Files.writeString(dir.resolve("_env.json"), "{}");
        Files.delete(dir.resolve("index.html"));
        RawarException e = assertThrows(RawarException.class,
                () -> RawarArchive.export(dir, meta("bad"), dirTmp.resolve("out")));
        assertTrue(e.getMessage().contains("no index.html"), e.getMessage());
        assertTrue(e.getMessage().contains("_ledger is reserved"), e.getMessage());
        assertTrue(e.getMessage().contains("_env.json is reserved"), e.getMessage());
        assertFalse(Files.exists(dirTmp.resolve("out/bad.rawar")));
    }


    @Test
    void aBadNameOrMountIsNotExportable() throws IOException {
        Path dir = dirRawar("Bad_Name");
        RawarException e = assertThrows(RawarException.class,
                () -> RawarArchive.export(dir, meta("Bad_Name"), dirTmp.resolve("out")));
        assertTrue(e.getMessage().contains("name Bad_Name"), e.getMessage());

        RawarDescriptor metaMount = new RawarDescriptor(1, "ok", "1", null, "usdcx", meta("ok").builtWith(), List.of(), null);
        Path dirOk = dirRawar("ok");
        RawarException e2 = assertThrows(RawarException.class,
                () -> RawarArchive.export(dirOk, metaMount, dirTmp.resolve("out")));
        assertTrue(e2.getMessage().contains("mount usdcx"), e2.getMessage());
    }


    @Test
    void anArchiveWithAnUnsafePathOrTwoTopsIsRefused() throws IOException {
        Path fileZip = dirTmp.resolve("evil.rawar");
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(fileZip))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("evil/../../escape.txt"));
            zip.write("x".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertThrows(RawarException.class, () -> RawarArchive.unpack(fileZip, dirTmp.resolve("host")));
        assertFalse(Files.exists(dirTmp.resolve("escape.txt")));

        Path fileTwo = dirTmp.resolve("two.rawar");
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(fileTwo))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("a/index.html"));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("b/index.html"));
            zip.closeEntry();
        }
        RawarException e = assertThrows(RawarException.class, () -> RawarArchive.unpack(fileTwo, dirTmp.resolve("host")));
        assertTrue(e.getMessage().contains("second top-level"), e.getMessage());
        assertFalse(Files.exists(dirTmp.resolve("host/a")));
    }


    @Test
    void theDescriptorRoundTripsAndRefusesUnknownFields() throws IOException {
        RawarDescriptor desc = meta("rt").withTreeSha256("0".repeat(64));
        assertEquals(desc, RawarDescriptor.parse(desc.arrJson()));
        String strJson = new String(desc.arrJson(), StandardCharsets.UTF_8);
        assertTrue(strJson.contains("\"rawar\" : 1"), strJson);
        assertTrue(strJson.contains("\"commit\" : null"), strJson);
        assertThrows(IOException.class,
                () -> RawarDescriptor.parse("{\"rawar\":1,\"issuer\":\"x\"}".getBytes(StandardCharsets.UTF_8)));
    }


    private static void copyTree(Path dirFrom, Path dirTo) throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.walk(dirFrom)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                Path dst = dirTo.resolve(dirFrom.relativize(path).toString());
                if (Files.isDirectory(path))
                    Files.createDirectories(dst);
                else
                    Files.copy(path, dst);
            }
        }
    }
}
