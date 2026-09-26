// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.nio.file.Path;

/**
 * One Canton installation found on this machine, stated explicitly rather than
 * implied by a path template.
 *
 * @param version the Canton version, which is what decides behaviour - not the
 *        SDK bundle version and not the directory name
 * @param edition open source or enterprise; UNKNOWN when the layout said
 *        neither
 * @param source the packaging system that delivered it
 * @param dirHome the directory that IS the installation: the SDK's canton/
 *        subdirectory, or the DPM component directory
 * @param fileRuntime the runtime JAR found beneath dirHome, or null when none
 *        was found - the internal layout differs across generations and is
 *        searched, never assumed
 *
 * Author Claude/bentzn
 */
public record CantonInstallation(VersionId version, Edition edition, InstallSource source,
        Path dirHome, Path fileRuntime) implements Comparable<CantonInstallation> {

    public CantonInstallation {
        if (version == null)
            throw new IllegalArgumentException("version is required");
        if (edition == null)
            throw new IllegalArgumentException("edition is required");
        if (source == null)
            throw new IllegalArgumentException("source is required");
        if (dirHome == null)
            throw new IllegalArgumentException("dirHome is required");
    }


    public boolean hasRuntime() {
        return fileRuntime != null;
    }


    public String line() {
        return version.line();
    }


    /** Newest version first, then by edition, then by source. */
    @Override
    public int compareTo(CantonInstallation other) {
        int cmp = other.version.compareTo(version);
        if (cmp != 0)
            return cmp;
        cmp = edition.compareTo(other.edition);
        if (cmp != 0)
            return cmp;
        return source.compareTo(other.source);
    }


    @Override
    public String toString() {
        return "canton " + version + " " + edition + " (" + source + ") " + dirHome;
    }
}
