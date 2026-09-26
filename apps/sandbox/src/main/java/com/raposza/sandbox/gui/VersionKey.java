// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.Edition;
import com.raposza.canton.install.VersionId;

import java.util.Locale;

/**
 * The directory name an installation gets, and the only place it is decided.
 *
 * <h2>Version AND edition, not version alone</h2>
 *
 * 3.4.4 was released under both licences and sits in two caches under one
 * version string. They are different binaries with different manifest
 * entry points, so a profile shared between them would be one file describing
 * two things, and a snapshot taken under one and restored under the other
 * would be exactly the cross-version restore this application refuses.
 *
 * <h2>Safe on both filesystems</h2>
 *
 * Digits, dots, an underscore and a dash. No colon, no space, nothing Windows
 * refuses in a path segment, and stable enough to type into a terminal.
 *
 * Author Claude/bentzn
 */
public final class VersionKey {

    private VersionKey() {
    }


    /**
     * @param version the Canton version; never null
     * @param edition its edition, or null for unknown
     * @return the directory name, e.g. `3.5.11-open_source`
     */
    public static String strOf(VersionId version, Edition edition) {
        if (version == null)
            throw new IllegalArgumentException("a version is required");
        Edition editionHere = edition == null ? Edition.UNKNOWN : edition;
        return version + "-" + editionHere.name().toLowerCase(Locale.ROOT);
    }


    /**
     * @param inst the installation; never null
     * @return the directory name for it
     */
    public static String strOf(CantonInstallation inst) {
        if (inst == null)
            throw new IllegalArgumentException("an installation is required");
        return strOf(inst.version(), inst.edition());
    }
}
