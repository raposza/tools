// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.rawar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * A RAWAR's tree: which files are part of it, and the ONE hash that identifies
 * it wherever it is - a directory on a workstation, a `.rawar` file in
 * transit, an unpacked tree on a Member.
 *
 * <h2>The hash is over the directory, never over the archive file</h2>
 *
 * A zip's bytes depend on entry order, timestamps and compressor, so an
 * archive's digest is not the tree's. The identity is sha256 over, for every
 * file in sorted relative-path order, the path as UTF-8, one zero byte, and
 * the file's own sha256 as 32 raw bytes. `rawar.json` is excluded, so that the
 * descriptor can carry the hash of what it describes - `rawar.md` section 3.
 *
 * <h2>What is not part of the tree</h2>
 *
 * Any entry whose name starts with a dot - `.git`, `.DS_Store` - and
 * `__pycache__`, at any depth. They are neither hashed nor archived. The list
 * is here and only here; the document says so.
 *
 * Author Claude/bentzn
 */
public final class RawarTree {

    /** The descriptor's fixed name at the root of every RAWAR. */
    public static final String STR_DESCRIPTOR = "rawar.json";

    /** The entry page, required at the root, served at the mount. */
    public static final String STR_INDEX = "index.html";

    /** The ledger forward the host provides under every mount; reserved. */
    public static final String STR_RESERVED_LEDGER = "_ledger";

    /** The sign-in environment the host provides under every mount; reserved. */
    public static final String STR_RESERVED_ENV = "_env.json";

    private static final String STR_PYCACHE = "__pycache__";


    private RawarTree() {
    }


    /**
     * every file of the tree, as relative paths with `/` separators, sorted
     *
     * @param dirRoot the RAWAR directory
     * @return the paths, `rawar.json` and excluded entries left out
     * @throws IOException when the directory cannot be walked
     */
    public static List<String> lstFile(Path dirRoot) throws IOException {
        if (!Files.isDirectory(dirRoot))
            throw new IOException("not a directory: " + dirRoot);
        List<String> lstOut = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dirRoot)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(file))
                    continue;
                Path rel = dirRoot.relativize(file);
                if (flagExcluded(rel))
                    continue;
                String strRel = strRelative(rel);
                if (strRel.equals(STR_DESCRIPTOR))
                    continue;
                lstOut.add(strRel);
            }
        }
        lstOut.sort(null);
        return lstOut;
    }


    /**
     * the canonical hash of the tree, lowercase hex
     *
     * @param dirRoot the RAWAR directory
     * @return 64 hex characters
     * @throws IOException when a file cannot be read
     */
    public static String strHash(Path dirRoot) throws IOException {
        MessageDigest digestTree = digest();
        for (String strRel : lstFile(dirRoot)) {
            digestTree.update(strRel.getBytes(StandardCharsets.UTF_8));
            digestTree.update((byte) 0);
            digestTree.update(arrSha256(dirRoot.resolve(strRel)));
        }
        return HexFormat.of().formatHex(digestTree.digest());
    }


    /**
     * the sha256 of one file, raw
     *
     * @param file the file
     * @return 32 bytes
     * @throws IOException when the file cannot be read
     */
    public static byte[] arrSha256(Path file) throws IOException {
        MessageDigest digestFile = digest();
        try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
            byte[] arrBuf = new byte[65536];
            int nRead;
            while ((nRead = in.read(arrBuf)) > 0) {
                digestFile.update(arrBuf, 0, nRead);
            }
        }
        return digestFile.digest();
    }


    /**
     * whether a RAWAR directory may be exported: `index.html` at the root, no
     * reserved name anywhere
     *
     * @param dirRoot the RAWAR directory
     * @return every objection, empty when it may be exported
     * @throws IOException when the directory cannot be walked
     */
    public static List<String> lstObjection(Path dirRoot) throws IOException {
        List<String> lstOut = new ArrayList<>();
        List<String> lstRel = lstFile(dirRoot);
        if (!lstRel.contains(STR_INDEX))
            lstOut.add("no " + STR_INDEX + " at the root - a mount that answers 404 is not publishable");
        for (String strRel : lstRel) {
            for (String strPart : strRel.split("/")) {
                if (strPart.equals(STR_RESERVED_LEDGER) || strPart.equals(STR_RESERVED_ENV))
                    lstOut.add(strRel + " - " + strPart + " is reserved for the host");
            }
        }
        return lstOut;
    }


    /**
     * whether a name may be a RAWAR's: lower case, digits and `-`, so it can
     * be a DNS label and a file name
     *
     * @param strName the candidate
     * @return true when it may
     */
    public static boolean flagNameValid(String strName) {
        return strName != null && strName.matches("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");
    }


    static String strRelative(Path rel) {
        StringBuilder sb = new StringBuilder();
        for (Path part : rel) {
            if (sb.length() > 0)
                sb.append('/');
            sb.append(part.toString());
        }
        return sb.toString();
    }


    static boolean flagExcluded(Path rel) {
        for (Path part : rel) {
            String strPart = part.toString();
            if (strPart.startsWith(".") || strPart.equals(STR_PYCACHE))
                return true;
        }
        return false;
    }


    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory in every JDK", e);
        }
    }
}
