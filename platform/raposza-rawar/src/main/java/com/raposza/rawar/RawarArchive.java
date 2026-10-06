// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.rawar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The `.rawar` file: a zip of the RAWAR directory with the directory as its
 * single top-level entry, written so that two exports of the same tree are
 * byte-identical on the same JDK - sorted entries, one fixed timestamp, no
 * extra fields - and verified on unpack against the hash its descriptor
 * carries. `rawar.md` section 2.
 *
 * <h2>The archive's bytes are not the identity</h2>
 *
 * Byte-identity of the file is a convenience for an artifact store; the
 * identity that is compared is `treeSha256`, recomputed from the unpacked
 * tree. A `.rawar` whose tree does not hash to what its descriptor says is a
 * different artifact and is refused - that is the one hard refusal in the
 * whole scheme, because a mismatch is not an incompatibility.
 *
 * Author Claude/bentzn
 */
public final class RawarArchive {

    /** The file suffix. `.jar` and `.war` have their own for the same reason. */
    public static final String STR_SUFFIX = ".rawar";

    /** The one timestamp every entry carries: DOS time's epoch. */
    private static final LocalDateTime TIME_FIXED = LocalDateTime.of(1980, 1, 1, 0, 0, 0);


    private RawarArchive() {
    }


    /**
     * exports a RAWAR directory: hashes the tree, writes the descriptor with
     * that hash into the directory, and writes `<name>.rawar` beside or
     * wherever asked
     *
     * @param dirRoot the RAWAR directory - its `rawar.json` is REPLACED
     * @param meta the descriptor without its hash; `treeSha256` is ignored
     * @param dirOut where the archive is written
     * @return the archive written
     * @throws IOException when the tree is not exportable or cannot be written
     */
    public static Path export(Path dirRoot, RawarDescriptor meta, Path dirOut) throws IOException {
        List<String> lstObjection = RawarTree.lstObjection(dirRoot);
        RawarDescriptor desc = meta.withTreeSha256(RawarTree.strHash(dirRoot));
        lstObjection.addAll(desc.lstObjection());
        if (!lstObjection.isEmpty())
            throw new RawarException("not exportable: " + String.join("; ", lstObjection));
        desc.write(dirRoot);
        Files.createDirectories(dirOut);
        Path fileOut = dirOut.resolve(desc.strName() + STR_SUFFIX);
        write(dirRoot, desc, fileOut);
        return fileOut;
    }


    /**
     * writes the archive of a directory whose descriptor is already in place
     *
     * @param dirRoot the RAWAR directory
     * @param desc its descriptor, naming the top-level entry
     * @param fileOut the archive
     * @throws IOException when it cannot be written
     */
    static void write(Path dirRoot, RawarDescriptor desc, Path fileOut) throws IOException {
        String strTop = desc.strName() + "/";
        try (OutputStream out = Files.newOutputStream(fileOut);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.setLevel(9);
            putEntry(zip, strTop + RawarTree.STR_DESCRIPTOR, desc.arrJson());
            for (String strRel : RawarTree.lstFile(dirRoot)) {
                putEntry(zip, strTop + strRel, Files.readAllBytes(dirRoot.resolve(strRel)));
            }
        }
    }


    /**
     * unpacks an archive into a directory and VERIFIES it: one top-level
     * entry named as the descriptor says, every path inside it safe, and the
     * unpacked tree hashing to the descriptor's `treeSha256`
     *
     * @param fileRawar the archive
     * @param dirInto where the RAWAR directory is created, as `<name>/`
     * @return the unpacked RAWAR directory
     * @throws RawarException when the archive is refused - nothing is left
     *         behind in that case
     * @throws IOException when it cannot be read or written
     */
    public static Path unpack(Path fileRawar, Path dirInto) throws IOException {
        String strTop = null;
        Path dirRoot = null;
        try (InputStream in = Files.newInputStream(fileRawar);
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String strName = entry.getName();
                int nSlash = strName.indexOf('/');
                if (nSlash <= 0)
                    throw refuse(dirRoot, "entry outside a top-level directory: " + strName);
                String strThisTop = strName.substring(0, nSlash);
                if (strTop == null) {
                    if (!RawarTree.flagNameValid(strThisTop))
                        throw refuse(dirRoot, "top-level entry is not a RAWAR name: " + strThisTop);
                    strTop = strThisTop;
                    dirRoot = dirInto.resolve(strTop);
                    if (Files.exists(dirRoot))
                        throw new RawarException("already present: " + dirRoot);
                    Files.createDirectories(dirRoot);
                }
                else if (!strThisTop.equals(strTop)) {
                    throw refuse(dirRoot, "a second top-level entry: " + strThisTop);
                }
                String strRel = strName.substring(nSlash + 1);
                if (strRel.isEmpty() || entry.isDirectory())
                    continue;
                if (strRel.contains("..") || strRel.startsWith("/"))
                    throw refuse(dirRoot, "unsafe path: " + strName);
                Path file = dirRoot.resolve(strRel).normalize();
                if (!file.startsWith(dirRoot))
                    throw refuse(dirRoot, "path escapes the directory: " + strName);
                Files.createDirectories(file.getParent());
                Files.copy(zip, file);
            }
        }
        if (dirRoot == null)
            throw new RawarException("empty archive: " + fileRawar);
        try {
            verify(dirRoot, strTop);
        }
        catch (RawarException e) {
            deleteTree(dirRoot);
            throw e;
        }
        return dirRoot;
    }


    /**
     * verifies an unpacked or on-disk RAWAR directory against its descriptor
     *
     * @param dirRoot the RAWAR directory
     * @param strNameExpected the name it must carry, or null for any
     * @return the descriptor
     * @throws RawarException when it does not verify
     * @throws IOException when it cannot be read
     */
    public static RawarDescriptor verify(Path dirRoot, String strNameExpected) throws IOException {
        if (!Files.isRegularFile(dirRoot.resolve(RawarTree.STR_DESCRIPTOR)))
            throw new RawarException("no " + RawarTree.STR_DESCRIPTOR + " in " + dirRoot);
        RawarDescriptor desc = RawarDescriptor.read(dirRoot);
        List<String> lstObjection = desc.lstObjection();
        if (!lstObjection.isEmpty())
            throw new RawarException("descriptor refused: " + String.join("; ", lstObjection));
        if (strNameExpected != null && !strNameExpected.equals(desc.strName()))
            throw new RawarException("descriptor names " + desc.strName() + ", the directory is " + strNameExpected);
        String strHash = RawarTree.strHash(dirRoot);
        if (!strHash.equals(desc.strTreeSha256()))
            throw new RawarException("TREE HASH MISMATCH - the descriptor says " + desc.strTreeSha256()
                    + ", the tree hashes to " + strHash + ": this is not the RAWAR the descriptor describes");
        return desc;
    }


    private static void putEntry(ZipOutputStream zip, String strName, byte[] arrBody) throws IOException {
        ZipEntry entry = new ZipEntry(strName);
        entry.setMethod(ZipEntry.DEFLATED);
        entry.setTimeLocal(TIME_FIXED);
        zip.putNextEntry(entry);
        zip.write(arrBody);
        zip.closeEntry();
    }


    private static RawarException refuse(Path dirRoot, String strWhy) throws IOException {
        if (dirRoot != null)
            deleteTree(dirRoot);
        return new RawarException("archive refused: " + strWhy);
    }


    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir))
            return;
        try (Stream<Path> stream = Files.walk(dir)) {
            List<Path> lstPath = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path path : lstPath) {
                Files.delete(path);
            }
        }
    }
}
