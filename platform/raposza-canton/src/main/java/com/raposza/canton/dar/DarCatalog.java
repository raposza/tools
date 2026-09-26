// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.dar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The DARs staged in a directory, indexed by the package id inside each one.
 *
 * <h2>What it does not do</h2>
 *
 * It does not upload, it does not talk to a participant, and it does not decode
 * a DALF. A DAR is opened as what it is - a zip with a manifest - and only the
 * manifest is read. Template and interface discovery is the resolve layer's
 * job and needs the package service, not the file; a second decoder here would
 * be a copy of it that disagrees with the ledger the first time SCU renames
 * something.
 *
 * <h2>Alpha order, and why it is part of the contract</h2>
 *
 * The scan sorts by file name. Upload order decides which package a
 * participant sees first, and a directory listing is filesystem order, which
 * differs between two machines holding identical files. The init scripts take
 * the same rule for the same reason: a provisioning step that is not
 * reproducible across machines is not provisioning.
 *
 * <h2>Duplicate package ids are fatal, duplicate names are not</h2>
 *
 * Two files carrying the same package id are the same package staged twice -
 * a copy left behind by a build, in practice - and uploading both is at best
 * wasted work and at worst two names for one thing in every later diagnostic.
 * Two files carrying the same NAME with different versions are legitimate:
 * that is exactly what Smart Contract Upgrades stage, and rejecting it would
 * make the catalogue refuse the case it exists to serve.
 *
 * Ported from the prototype's `DarCatalog` as behaviour, not as source: the
 * manifest key, the `Main-Dalf` shape and the package-id extraction are its
 * measurements. Its PascalCase module-name inference is deliberately NOT
 * carried over - it was documented there as a best-effort hint, and a hint that
 * looks like an identifier is worse than an absent field.
 *
 * Author Claude/bentzn
 */
public final class DarCatalog {

    /** What a Daml DAR manifest calls the package that the DAR is about. */
    public static final String STR_ATTR_MAIN_DALF = "Main-Dalf";

    private static final String STR_MANIFEST = "META-INF/MANIFEST.MF";

    private static final String STR_GLOB_DAR = "*.dar";

    /** The package id is the last hash-shaped segment before the extension. */
    private static final Pattern PAT_PKG_ID = Pattern.compile("-([0-9a-f]{64})\\.dalf$");

    /**
     * Greedy on the name so the split falls at the LAST dash before the
     * version: a project called `my-model` yields name `my-model`, not `my`.
     *
     * The version segment must START WITH A DIGIT. Without that, the SDK's own
     * `daml-prim-DA-Internal-Down-&lt;hash&gt;.dalf` matches and yields version
     * `Down` - a field that is worse than absent, because it looks like an
     * answer.
     */
    private static final Pattern PAT_NAME_VERSION =
            Pattern.compile("^(.+)-([0-9][^-]*)-[0-9a-f]{64}\\.dalf$");

    private final Path dirDars;
    private final List<DarInfo> lstDars;
    private final Map<String, DarInfo> mapByPkgId;


    private DarCatalog(Path dirDars, List<DarInfo> lstDars, Map<String, DarInfo> mapByPkgId) {
        this.dirDars = dirDars;
        this.lstDars = Collections.unmodifiableList(lstDars);
        this.mapByPkgId = Collections.unmodifiableMap(mapByPkgId);
    }


    /**
     * @param dirDars a directory of `*.dar` files; subdirectories are not
     *        descended into, because a nested DAR is a build artefact that
     *        happened to be under the staging directory rather than something
     *        staged
     * @return the catalogue, possibly empty
     * @throws DarException when the path is not a directory, when a DAR cannot
     *         be read, or when two files carry one package id
     */
    public static DarCatalog scan(Path dirDars) {
        if (dirDars == null)
            throw new IllegalArgumentException("dirDars is required");
        if (!Files.isDirectory(dirDars))
            throw new DarException("not a directory: " + dirDars);

        List<Path> lstFile = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirDars, STR_GLOB_DAR)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file))
                    lstFile.add(file);
            }
        }
        catch (IOException ex) {
            throw new DarException("cannot list " + dirDars, ex);
        }

        lstFile.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));

        List<DarInfo> lstDars = new ArrayList<>();
        Map<String, DarInfo> mapByPkgId = new LinkedHashMap<>();
        for (Path file : lstFile) {
            DarInfo info = inspect(file);
            DarInfo infoPrev = mapByPkgId.put(info.strPkgId(), info);
            if (infoPrev != null) {
                throw new DarException("package " + info.strPkgId() + " is staged twice: "
                        + infoPrev.strFileName() + " and " + info.strFileName());
            }
            lstDars.add(info);
        }
        return new DarCatalog(dirDars.toAbsolutePath().normalize(), lstDars, mapByPkgId);
    }


    /**
     * The same catalogue over a NAMED SET of DARs rather than a directory.
     *
     * What the window's DAR tab produces is a subset of a directory, and a
     * subset cannot be expressed as a directory. The order and the duplicate
     * rule are the scan's, unchanged: sorted by file name, because upload order
     * decides which package a participant sees first and a caller's tick order
     * is not reproducible; and two files carrying one package id is fatal,
     * because uploading both is at best wasted work and at worst two names for
     * one thing in every later diagnostic.
     *
     * @param lstFileDar the DARs; may be empty, and an empty catalogue is not
     *        an error - it is what a start that uploads nothing looks like
     * @return the catalogue
     * @throws DarException when a DAR cannot be read, or when two of them carry
     *         one package id
     */
    public static DarCatalog ofFiles(List<Path> lstFileDar) {
        if (lstFileDar == null)
            throw new IllegalArgumentException("lstFileDar is required");

        List<Path> lstSorted = new ArrayList<>(lstFileDar);
        lstSorted.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));

        List<DarInfo> lstDars = new ArrayList<>();
        Map<String, DarInfo> mapByPkgId = new LinkedHashMap<>();
        for (Path file : lstSorted) {
            if (!Files.isRegularFile(file))
                throw new DarException("not a file: " + file);

            DarInfo info = inspect(file);
            DarInfo infoPrev = mapByPkgId.put(info.strPkgId(), info);
            if (infoPrev != null) {
                throw new DarException("package " + info.strPkgId() + " is staged twice: "
                        + infoPrev.strFileName() + " and " + info.strFileName());
            }
            lstDars.add(info);
        }
        // NO DIRECTORY. These files need not share one, and naming the first
        // one's parent would be a field that is right until somebody selects
        // across two directories.
        return new DarCatalog(null, lstDars, mapByPkgId);
    }


    /**
     * Reads one DAR without a directory around it.
     *
     * @param fileDar the DAR
     * @return what its manifest says it is
     * @throws DarException when it is not a readable zip, has no manifest, has
     *         no `Main-Dalf`, or has one from which no package id can be read
     */
    public static DarInfo inspect(Path fileDar) {
        if (fileDar == null)
            throw new IllegalArgumentException("fileDar is required");

        String strMainDalf = readMainDalf(fileDar);

        Matcher mtcId = PAT_PKG_ID.matcher(strMainDalf);
        if (!mtcId.find()) {
            throw new DarException("no package id in " + STR_ATTR_MAIN_DALF + " of "
                    + fileDar.getFileName() + ": " + strMainDalf);
        }
        String strPkgId = mtcId.group(1);

        int nSlash = strMainDalf.lastIndexOf('/');
        String strBase = nSlash >= 0 ? strMainDalf.substring(nSlash + 1) : strMainDalf;
        Matcher mtcName = PAT_NAME_VERSION.matcher(strBase);
        String strName = null;
        String strVersion = null;
        if (mtcName.matches()) {
            strName = mtcName.group(1);
            strVersion = mtcName.group(2);
        }
        return new DarInfo(fileDar.toAbsolutePath().normalize(), strPkgId, strName, strVersion);
    }


    /**
     * The manifest is read with {@link Manifest} rather than by splitting
     * lines: DAR manifests are folded at 72 bytes, so `Main-Dalf` values of any
     * realistic length arrive as a continuation line and hand-parsing them
     * silently truncates the package id.
     */
    private static String readMainDalf(Path fileDar) {
        try (ZipFile zip = new ZipFile(fileDar.toFile())) {
            ZipEntry entry = zip.getEntry(STR_MANIFEST);
            if (entry == null)
                throw new DarException("no " + STR_MANIFEST + " in " + fileDar.getFileName());
            try (InputStream in = zip.getInputStream(entry)) {
                Manifest manifest = new Manifest(in);
                Attributes attrs = manifest.getMainAttributes();
                String strValue = attrs.getValue(STR_ATTR_MAIN_DALF);
                if (strValue == null || strValue.isBlank()) {
                    throw new DarException("no " + STR_ATTR_MAIN_DALF + " in "
                            + fileDar.getFileName());
                }
                return strValue.trim();
            }
        }
        catch (IOException ex) {
            throw new DarException("cannot read DAR " + fileDar, ex);
        }
    }


    /**
     * @return the directory that was scanned, or null when this catalogue came
     *         from a named set of files rather than from a directory
     */
    public Path dirDars() {
        return dirDars;
    }


    /**
     * @return every DAR found, in the order they should be uploaded
     */
    public List<DarInfo> lstDars() {
        return lstDars;
    }


    /**
     * @return the files alone, in the same order
     */
    public List<Path> lstFiles() {
        List<Path> lstFile = new ArrayList<>(lstDars.size());
        for (DarInfo info : lstDars)
            lstFile.add(info.fileDar());
        return lstFile;
    }


    /**
     * @param strPkgId a 64-character package id
     * @return the DAR carrying it, or null
     */
    public DarInfo byPackageId(String strPkgId) {
        return mapByPkgId.get(strPkgId);
    }


    public int cntDars() {
        return lstDars.size();
    }


    public boolean flagEmpty() {
        return lstDars.isEmpty();
    }


    @Override
    public String toString() {
        return "dar catalog: " + lstDars.size() + " in " + dirDars;
    }
}
