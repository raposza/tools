// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * What a Canton runtime jar says it is, read cheaply enough to do on every
 * rescan.
 *
 * <h2>The test</h2>
 *
 * The manifest's `Main-Class`, and nothing else:
 *
 * <pre>
 * com.digitalasset.canton.CantonCommunityApp    OPEN_SOURCE
 * com.digitalasset.canton.CantonEnterpriseApp   ENTERPRISE
 * anything else, or no manifest                 UNKNOWN
 * </pre>
 *
 * It is one string comparison against a line the vendor puts at the front of
 * the file, and it is BANKED for every installation on this machine:
 * `inventory/canton/&lt;version&gt;-&lt;edition&gt;/launch-surface.json` carries the
 * `Main-Class` of all twelve jars read on 2026-08-17. Eleven say Community and
 * the 3.4.4 in the enterprise DPM component says Enterprise, across 2.8.12 to
 * 3.5.11 and both installation models. The enterprise side rests on ONE binary,
 * so an unrecognised class answers UNKNOWN rather than guessing.
 *
 * The reader it replaced keyed on the FILE NAME, and a Daml Assistant SDK ships
 * `canton.jar` on every version - so nine of the twelve answered UNKNOWN while
 * each of those jars stated the answer plainly.
 *
 * <h2>Why this does not open the jar</h2>
 *
 * A Canton runtime jar is hundreds of megabytes with six figures of entries, and
 * {@link java.util.jar.JarFile} reads the whole central directory at the END of
 * the file before it will hand over a manifest. Streaming from the front stops
 * at the manifest instead, which a jar writes first, so the read is a few
 * kilobytes whatever the jar weighs. {@value #CNT_ENTRY_MAX} entries in, it
 * gives up rather than stream a whole archive looking for something that is not
 * there.
 *
 * The answer is then cached against the jar's path, size and modification time,
 * so a rescan of an unchanged machine reads no files at all.
 *
 * Author Claude/bentzn
 */
public final class CantonRuntimeJar {

    private static final Logger log = LoggerFactory.getLogger(CantonRuntimeJar.class);

    /** The application class of every open source Canton read here. */
    public static final String STR_MAIN_COMMUNITY = "com.digitalasset.canton.CantonCommunityApp";

    /** The application class of the one enterprise Canton read here. */
    public static final String STR_MAIN_ENTERPRISE = "com.digitalasset.canton.CantonEnterpriseApp";

    public static final String STR_ENTRY_MANIFEST = "META-INF/MANIFEST.MF";

    /** How far in to look before deciding the manifest is not at the front. */
    public static final int CNT_ENTRY_MAX = 64;

    private static final Map<String, Edition> mapCache = new ConcurrentHashMap<>();


    private CantonRuntimeJar() {
    }


    /**
     * Never throws. A jar that cannot be read has not answered, and discovery
     * reporting an installation it could not classify beats dropping it.
     *
     * @param fileRuntime the runtime jar, or null when none was found
     * @return the edition it declares, or UNKNOWN
     */
    public static Edition editionOf(Path fileRuntime) {
        if (fileRuntime == null || !Files.isRegularFile(fileRuntime))
            return Edition.UNKNOWN;

        String strKey = keyFor(fileRuntime);
        if (strKey == null)
            return editionOfMainClass(strMainClass(fileRuntime));
        return mapCache.computeIfAbsent(strKey,
                strIgnored -> editionOfMainClass(strMainClass(fileRuntime)));
    }


    /**
     * @param strMainClass a manifest Main-Class, or null
     * @return which edition declares it
     */
    public static Edition editionOfMainClass(String strMainClass) {
        if (STR_MAIN_COMMUNITY.equals(strMainClass))
            return Edition.OPEN_SOURCE;
        if (STR_MAIN_ENTERPRISE.equals(strMainClass))
            return Edition.ENTERPRISE;
        return Edition.UNKNOWN;
    }


    /**
     * @param fileRuntime the runtime jar
     * @return its manifest Main-Class, or null when the manifest is not near
     *         the front, declares none, or the file is not a zip at all
     */
    public static String strMainClass(Path fileRuntime) {
        if (fileRuntime == null || !Files.isRegularFile(fileRuntime))
            return null;

        try (InputStream in = new BufferedInputStream(Files.newInputStream(fileRuntime), 65536);
                ZipInputStream zip = new ZipInputStream(in)) {

            int cntEntry = 0;
            ZipEntry entry = zip.getNextEntry();
            while (entry != null && cntEntry < CNT_ENTRY_MAX) {
                if (STR_ENTRY_MANIFEST.equals(entry.getName())) {
                    // The Manifest constructor does not close what it reads,
                    // and must not: closing here would close the zip stream.
                    return new Manifest(zip).getMainAttributes()
                            .getValue(Attributes.Name.MAIN_CLASS);
                }
                cntEntry++;
                entry = zip.getNextEntry();
            }
            log.debug("no manifest in the first {} entries of {}", CNT_ENTRY_MAX, fileRuntime);
            return null;
        }
        catch (IOException | RuntimeException ex) {
            log.warn("could not read the manifest of {}: {}", fileRuntime, ex.toString());
            return null;
        }
    }


    /**
     * @param fileRuntime the runtime jar
     * @return a cache key that changes when the file does, or null when its
     *         attributes cannot be read
     */
    private static String keyFor(Path fileRuntime) {
        try {
            return fileRuntime.toAbsolutePath() + "|" + Files.size(fileRuntime) + "|"
                    + Files.getLastModifiedTime(fileRuntime).toMillis();
        }
        catch (IOException ex) {
            return null;
        }
    }
}
