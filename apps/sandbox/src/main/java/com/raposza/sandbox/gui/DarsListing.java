// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.dar.DarCatalog;
import com.raposza.canton.dar.DarException;
import com.raposza.canton.dar.DarInfo;
import com.raposza.sandbox.app.DiscoveryDar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The DARs the discovery document lists: every DAR in the store that is
 * ticked for the next start or that a participant has, as `selected` or
 * `uploaded`. A DAR that is neither is not listed.
 *
 * <h2>Built on the event thread, every tick</h2>
 *
 * So the manifest of a DAR is read ONCE and kept against its size and
 * modification time; a tick after that costs one stat per file. A DAR whose
 * manifest cannot be read is left out rather than listed with nothing known -
 * the Store tab is where it is reported.
 *
 * Author Claude/bentzn
 */
final class DarsListing {

    private static final String STR_SUFFIX_DAR = ".dar";

    private record Cached(long nModified, long nSize, DarInfo info) {
    }

    private final Map<Path, Cached> mapCache = new HashMap<>();


    /**
     * @param lstFileStore every DAR in the store, in the order to list them
     * @param collFileSelected the ones ticked for the next start
     * @param setPkgLive the main package ids the participants have, as they
     *        last answered; empty when nothing runs
     * @return the listed DARs, in store order
     */
    List<DiscoveryDar> lstOf(List<Path> lstFileStore, Collection<Path> collFileSelected,
            Set<String> setPkgLive) {
        List<DiscoveryDar> lstOut = new ArrayList<>();
        for (Path fileDar : lstFileStore) {
            DarInfo info = infoOf(fileDar);
            if (info == null)
                continue;
            boolean flagLive = setPkgLive.contains(info.strPkgId());
            if (!flagLive && !collFileSelected.contains(fileDar))
                continue;
            lstOut.add(new DiscoveryDar(info.strName(), info.strVersion(), info.strPkgId(),
                    DiscoveryDar.STR_ORIGIN_USER,
                    flagLive ? DiscoveryDar.STR_STATUS_UPLOADED : DiscoveryDar.STR_STATUS_SELECTED,
                    info.fileDar()));
        }
        return lstOut;
    }


    /**
     * @param fileDar a DAR
     * @return what its manifest says, or null when it cannot be read
     */
    DarInfo infoOf(Path fileDar) {
        long nModified;
        long nSize;
        try {
            nModified = Files.getLastModifiedTime(fileDar).toMillis();
            nSize = Files.size(fileDar);
        }
        catch (IOException ex) {
            mapCache.remove(fileDar);
            return null;
        }

        Cached cached = mapCache.get(fileDar);
        if (cached != null && cached.nModified() == nModified && cached.nSize() == nSize)
            return cached.info();

        DarInfo info;
        try {
            info = DarCatalog.inspect(fileDar);
        }
        catch (DarException ex) {
            info = null;
        }
        mapCache.put(fileDar, new Cached(nModified, nSize, info));
        return info;
    }


    /**
     * @param dirDars a DAR store, or null
     * @return its `*.dar` files by name; empty when it does not exist
     */
    static List<Path> lstDarsIn(Path dirDars) {
        if (dirDars == null || !Files.isDirectory(dirDars))
            return List.of();
        try (Stream<Path> strm = Files.list(dirDars)) {
            return strm.filter(file -> file.getFileName().toString().endsWith(STR_SUFFIX_DAR))
                    .filter(Files::isRegularFile).sorted().toList();
        }
        catch (IOException ex) {
            return List.of();
        }
    }

}
