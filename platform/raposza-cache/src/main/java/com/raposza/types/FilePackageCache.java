// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.types;

import com.raposza.api.LedgerException;
import com.raposza.api.PackageCache_i;
import com.raposza.api.model.PackageShape;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Package cache on the filesystem.
 *
 *   &lt;root&gt;/&lt;package-id&gt;.dalf   raw archive exactly as fetched
 *   &lt;root&gt;/&lt;package-id&gt;.json   the decoded PackageShape
 *
 * A package id is a content hash, so an entry is immutable and there is NO
 * per-package invalidation path. Do not add one; a cache that can go stale
 * needs a correctness argument this one does not.
 *
 * The raw archive is kept beside the decoded form so a decoder fix can
 * re-decode offline - that is what invalidateDecoded exists for.
 *
 * Author Claude/bentzn
 */
public final class FilePackageCache implements PackageCache_i {

    private static final String EXT_ARCHIVE = ".dalf";
    private static final String EXT_DECODED = ".json";
    private static final int LEN_ID_MAX = 128;

    private final Path dirRoot;
    private final ObjectMapper mapper;


    /**
     * Default location: ~/.raposza/packages
     */
    public FilePackageCache() {
        this(Path.of(System.getProperty("user.home"), ".raposza", "packages"));
    }


    /**
     * The directory is NOT created here. A tool that is only ever pointed at a
     * participant it cannot reach should leave no trace on disk.
     *
     * @param dirRoot cache root
     */
    public FilePackageCache(Path dirRoot) {
        if (dirRoot == null)
            throw new IllegalArgumentException("cache root is required");
        this.dirRoot = dirRoot;
        this.mapper = PackageJson.mapper();
    }


    /**
     * Derived from the presence of .dalf files rather than from an index.
     * An index would be a second source of truth and would drift.
     */
    @Override
    public Set<String> ids() {
        if (!Files.isDirectory(dirRoot))
            return Set.of();

        Set<String> setId = new TreeSet<>();
        try (Stream<Path> str = Files.list(dirRoot)) {
            str.forEach(p -> {
                String name = p.getFileName().toString();
                if (name.endsWith(EXT_ARCHIVE))
                    setId.add(name.substring(0, name.length() - EXT_ARCHIVE.length()));
            });
        }
        catch (IOException ex) {
            throw new UncheckedIOException("cannot list package cache: " + dirRoot, ex);
        }
        return Collections.unmodifiableSet(setId);
    }


    /**
     * A decoded form that cannot be read is reported as ABSENT rather than as
     * an error. The archive is still there, so the caller can simply decode it
     * again; failing hard would strand the whole cache on one bad file.
     */
    @Override
    public Optional<PackageShape> get(String idPackage) {
        Path file = fileFor(idPackage, EXT_DECODED);
        if (!Files.isReadable(file))
            return Optional.empty();
        try {
            return Optional.of(mapper.readValue(file.toFile(), PackageShape.class));
        }
        catch (IOException ex) {
            return Optional.empty();
        }
    }


    @Override
    public Optional<byte[]> archive(String idPackage) {
        Path file = fileFor(idPackage, EXT_ARCHIVE);
        if (!Files.isReadable(file))
            return Optional.empty();
        try {
            return Optional.of(Files.readAllBytes(file));
        }
        catch (IOException ex) {
            return Optional.empty();
        }
    }


    /**
     * Both files are written atomically. A half-written .json read by another
     * process would look like a corrupt entry, and while that degrades safely
     * it costs a needless re-decode.
     */
    @Override
    public void put(String idPackage, byte[] arrArchive, PackageShape shape) {
        Path fileArchive = fileFor(idPackage, EXT_ARCHIVE);
        Path fileDecoded = fileFor(idPackage, EXT_DECODED);

        try {
            Files.createDirectories(dirRoot);
            if (arrArchive != null)
                writeAtomic(fileArchive, arrArchive);
            if (shape != null)
                writeAtomic(fileDecoded, mapper.writeValueAsBytes(shape));
        }
        catch (IOException ex) {
            throw new UncheckedIOException("cannot write package " + idPackage
                    + " to " + dirRoot, ex);
        }
    }


    @Override
    public void invalidateDecoded() {
        if (!Files.isDirectory(dirRoot))
            return;
        try (Stream<Path> str = Files.list(dirRoot)) {
            str.filter(p -> p.getFileName().toString().endsWith(EXT_DECODED))
               .forEach(p -> {
                   try {
                       Files.deleteIfExists(p);
                   }
                   catch (IOException ex) {
                       throw new UncheckedIOException("cannot delete " + p, ex);
                   }
               });
        }
        catch (IOException ex) {
            throw new UncheckedIOException("cannot list package cache: " + dirRoot, ex);
        }
    }


    /**
     * @return the cache root, whether or not it exists
     */
    public Path root() {
        return dirRoot;
    }


    /**
     * A package id arrives from the network and becomes a filename, so it is
     * validated BEFORE any path is constructed from it. Rejecting late - after
     * resolve - is too late: the path has already escaped the root.
     */
    private Path fileFor(String idPackage, String strExt) {
        if (idPackage == null || idPackage.isBlank())
            throw new LedgerException("empty package id");
        if (idPackage.length() > LEN_ID_MAX)
            throw new LedgerException("package id too long: " + idPackage.length() + " chars");

        for (int idx = 0; idx < idPackage.length(); idx++) {
            char ch = idPackage.charAt(idx);
            boolean flagOk = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_' || ch == '.';
            if (!flagOk)
                throw new LedgerException("illegal character in package id at " + idx);
        }
        if (idPackage.equals(".") || idPackage.equals("..") || idPackage.startsWith("."))
            throw new LedgerException("illegal package id: " + idPackage);

        return dirRoot.resolve(idPackage + strExt);
    }


    private static void writeAtomic(Path file, byte[] arrData) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, arrData);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

}
