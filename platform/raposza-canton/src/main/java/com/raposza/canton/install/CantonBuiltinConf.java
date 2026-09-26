// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Configuration files the Canton jar carries, and the one that matters.
 *
 * `sandbox/sandbox.conf` IS the 3.x topology: a participant named `sandbox`, a
 * reference sequencer named `sequencer1`, a mediator named `mediator1`, all on
 * memory storage. The `sandbox` subcommand processes it before any `-c` file,
 * which is why {@link com.raposza.canton.topology.StorageOverlay}
 * restates only storage and why those three node names are its constants.
 *
 * `daemon` processes NO default configuration, so anything launched that way
 * has to be handed this file explicitly. Extracting it is what keeps the
 * finding alive under the daemon launcher: the vendor ships the topology, and
 * this project still does not hand-write one for 3.x.
 *
 * The two files are byte-identical in 3.4.11 and 3.5.11 - measured, not
 * assumed - so an extraction from either is the same topology. That is a
 * property of those two versions and not a promise about the next one, which
 * is why the file is extracted per installation rather than committed here.
 *
 * Author Claude/bentzn
 */
public final class CantonBuiltinConf {

    /** The 3.x sandbox topology, spelled as the jar spells it. */
    public static final String STR_ENTRY_SANDBOX = "sandbox/sandbox.conf";

    private static final String STR_SUFFIX = ".conf";

    private CantonBuiltinConf() {
    }


    /**
     * @param fileJar the Canton runtime jar
     * @return every `*.conf` entry, sorted, spelled as the jar spells them
     * @throws InstallException when the jar cannot be read
     */
    public static List<String> list(Path fileJar) {
        if (fileJar == null)
            throw new IllegalArgumentException("fileJar is required");

        List<String> lstEntry = new ArrayList<>();
        try (ZipFile zip = new ZipFile(fileJar.toFile())) {
            Enumeration<? extends ZipEntry> enumEntry = zip.entries();
            while (enumEntry.hasMoreElements()) {
                ZipEntry entry = enumEntry.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(STR_SUFFIX))
                    lstEntry.add(entry.getName());
            }
        }
        catch (IOException ex) {
            throw new InstallException("cannot read " + fileJar, ex);
        }
        Collections.sort(lstEntry);
        return lstEntry;
    }


    /**
     * @param installation the Canton whose jar to read
     * @param dirTo where to put it; created if absent
     * @return the extracted `sandbox.conf`
     * @throws InstallException when the installation has no runtime jar or the
     *         jar carries no such entry
     */
    public static Path extractSandboxConf(CantonInstallation installation, Path dirTo) {
        if (installation == null)
            throw new IllegalArgumentException("installation is required");
        if (!installation.hasRuntime())
            throw new InstallException(
                    "no Canton runtime jar was found under " + installation.dirHome());
        return extract(installation.fileRuntime(), STR_ENTRY_SANDBOX, dirTo);
    }


    /**
     * @param fileJar the Canton runtime jar
     * @param strEntry an entry name from {@link #list}, spelled exactly
     * @param dirTo where to put it; created if absent
     * @return the extracted file, named after the entry's base name
     * @throws InstallException when the entry is absent or the copy fails
     */
    public static Path extract(Path fileJar, String strEntry, Path dirTo) {
        if (fileJar == null)
            throw new IllegalArgumentException("fileJar is required");
        if (strEntry == null || strEntry.isBlank())
            throw new IllegalArgumentException("strEntry is required");
        if (dirTo == null)
            throw new IllegalArgumentException("dirTo is required");

        int nSlash = strEntry.lastIndexOf('/');
        String strBase = nSlash >= 0 ? strEntry.substring(nSlash + 1) : strEntry;
        Path fileOut = dirTo.resolve(strBase);

        try (ZipFile zip = new ZipFile(fileJar.toFile())) {
            ZipEntry entry = zip.getEntry(strEntry);
            if (entry == null) {
                throw new InstallException("no entry " + strEntry + " in " + fileJar.getFileName()
                        + "; it carries " + list(fileJar));
            }
            Files.createDirectories(dirTo);
            try (InputStream in = zip.getInputStream(entry)) {
                Files.copy(in, fileOut, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException ex) {
            throw new InstallException("cannot extract " + strEntry + " from " + fileJar, ex);
        }
        return fileOut.toAbsolutePath().normalize();
    }
}
