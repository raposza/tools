// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.dar;

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
 * The DARs Canton ships inside its own jar.
 *
 * <h2>Why this exists</h2>
 *
 * The ingest measurement - a contract created, a row read back out of a scribe
 * table - needs a package on the ledger, and every way of getting one so far
 * assumed something obtained beforehand: a Daml project to build, a DAR
 * committed to this repository, an SDK on the path. The jar already carries
 * three, measured on 3.5.11:
 *
 * <pre>
 * canton-builtin-admin-workflow-party-replication-alpha.dar
 * canton-builtin-admin-workflow-ping.dar
 * dar/canton-builtin-admin-workflow-party-replication-alpha.dar
 * dar/canton-builtin-admin-workflow-ping.dar
 * model-tests.dar
 * </pre>
 *
 * So a stack can be given a package with nothing staged, which is the premise
 * the sandbox is supposed to satisfy.
 *
 * <h2>The two copies are NOT the same file</h2>
 *
 * `canton-builtin-admin-workflow-ping.dar` is 431 943 bytes at the top level
 * and 432 557 under `dar/`. They differ, and by more than a timestamp. Nothing
 * here knows why, so nothing here picks one: {@link #list} returns entry names
 * exactly as the jar spells them and {@link #extract} takes an entry name, not
 * a base name. Silently preferring one would be a choice made by whoever wrote
 * the deduplication rather than by whoever needs the package.
 *
 * <h2>Extraction, not classpath loading</h2>
 *
 * The DAR has to reach the participant as a path - `--dar` on the sandbox
 * subcommand and `dars.upload` on the console both take one - so it is copied
 * out to a working directory. It is not read as a resource, because this module
 * does not have Canton on its classpath and must not acquire it.
 *
 * Author Claude/bentzn
 */
public final class CantonBuiltinDars {

    /** The ping workflow, top-level copy. A create plus a choice, no payload. */
    public static final String STR_ENTRY_PING = "canton-builtin-admin-workflow-ping.dar";

    /** Canton's own test model. The only one of the three with ordinary templates. */
    public static final String STR_ENTRY_MODEL_TESTS = "model-tests.dar";

    private static final String STR_SUFFIX = ".dar";


    private CantonBuiltinDars() {
    }


    /**
     * @param fileJar the Canton runtime jar
     * @return every `*.dar` entry, spelled as the jar spells it, sorted; empty
     *         when the jar carries none
     * @throws DarException when the jar cannot be read
     */
    public static List<String> list(Path fileJar) {
        if (fileJar == null)
            throw new IllegalArgumentException("fileJar is required");

        List<String> lstEntry = new ArrayList<>();
        try (ZipFile zip = new ZipFile(fileJar.toFile())) {
            Enumeration<? extends ZipEntry> enm = zip.entries();
            while (enm.hasMoreElements()) {
                ZipEntry entry = enm.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(STR_SUFFIX))
                    lstEntry.add(entry.getName());
            }
        }
        catch (IOException ex) {
            throw new DarException("cannot read jar " + fileJar, ex);
        }
        Collections.sort(lstEntry);
        return Collections.unmodifiableList(lstEntry);
    }


    /**
     * @param fileJar the Canton runtime jar
     * @param strEntry an entry name from {@link #list}, spelled exactly
     * @param dirTo where to put it; created if absent
     * @return the extracted file, named after the entry's base name
     * @throws DarException when the entry is absent or the copy fails
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
                throw new DarException("no entry " + strEntry + " in " + fileJar.getFileName()
                        + "; it carries " + list(fileJar));
            }
            Files.createDirectories(dirTo);
            try (InputStream in = zip.getInputStream(entry)) {
                Files.copy(in, fileOut, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException ex) {
            throw new DarException("cannot extract " + strEntry + " from " + fileJar, ex);
        }
        return fileOut.toAbsolutePath().normalize();
    }
}
