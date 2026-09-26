// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.profile.HostProfile;
import com.raposza.api.profile.HostProfileStore;

import java.nio.file.Path;
import java.util.List;

/**
 * Where the profile catalogue comes from.
 *
 * One fixed location and one override, no file chooser and no search path. A
 * tool that hunts for its own configuration in several places is a tool whose
 * operator cannot say which file it read.
 *
 * Separated from the window so the path rule is testable without a display.
 *
 * Author Claude/bentzn
 */
public final class Catalogue {

    /** Where the catalogue lives unless an argument says otherwise. */
    public static final String PATH_DEFAULT = ".raposza/ledger_hosts";


    private Catalogue() {
    }


    /**
     * @param arrArg command line arguments; the first, when present, is the
     *        catalogue path
     * @param dirHome the user's home directory
     * @return the catalogue file to read
     */
    public static Path resolve(String[] arrArg, Path dirHome) {
        if (arrArg != null && arrArg.length > 0 && arrArg[0] != null && !arrArg[0].isBlank())
            return Path.of(arrArg[0].trim());
        return dirHome.resolve(PATH_DEFAULT);
    }


    /**
     * @param fileCatalogue the catalogue
     * @return the profiles it holds
     * @throws IllegalStateException when the file is missing or holds nothing,
     *         naming the path. An empty profile list would leave the window
     *         with nothing to connect to and no explanation
     */
    public static List<HostProfile> read(Path fileCatalogue) {
        List<HostProfile> lstProfile = HostProfileStore.load(fileCatalogue);
        if (lstProfile.isEmpty()) {
            throw new IllegalStateException("no profiles in " + fileCatalogue.toAbsolutePath()
                    + " - see ledger_hosts.example for the format");
        }
        return lstProfile;
    }

}
