// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.rawar.RawarDescriptor;
import com.raposza.rawar.RawarTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Which RAWARs the Sandbox serves, and at which mount - read off the disk on
 * every request, so a directory added, a page edited or a mount changed is
 * served on the next reload with nothing restarted. `rawar.md` section 5.
 *
 * <h2>One directory under the root is one RAWAR</h2>
 *
 * His decision, 2026-10-02. Its name is the directory's name, and a name that
 * is not a RAWAR name - lower case, digits and `-` - is listed as a problem
 * rather than served, because it could not be exported under that name either.
 *
 * <h2>The mount</h2>
 *
 * The descriptor's `mount` when the directory has a readable `rawar.json`,
 * otherwise `/` - his default. Always held with a trailing slash, because a
 * page that links `./css/a.css` from `/usdcx` resolves to `/css/a.css`.
 *
 * <h2>Two RAWARs on one mount</h2>
 *
 * The first by name is served and the others are problems, named with the
 * mount they lost. Serving neither would punish the RAWAR that was there
 * first; serving whichever the file system listed first would change between
 * runs.
 *
 * Author Claude/bentzn
 */
public final class RawarSites {

    /**
     * One RAWAR being served.
     *
     * @param strName its name - the directory's
     * @param strMount where it is served, `/` or `/x/`, always with both slashes
     * @param dirRoot its directory
     * @param desc its descriptor, or null when it has none yet
     */
    public record Site(String strName, String strMount, Path dirRoot, RawarDescriptor desc) {
    }


    /**
     * What one scan found.
     *
     * @param lstSite what is served, longest mount first - the order a request
     *        is matched in
     * @param lstProblem what is not, one line each
     */
    public record Scan(List<Site> lstSite, List<String> lstProblem) {

        public Scan {
            lstSite = Collections.unmodifiableList(new ArrayList<>(lstSite));
            lstProblem = Collections.unmodifiableList(new ArrayList<>(lstProblem));
        }


        /**
         * @param strPath a request path, starting with `/`
         * @return the site whose mount the path lies under, the longest such
         *         mount winning, or null
         */
        public Site site(String strPath) {
            for (Site site : lstSite) {
                if (strPath.startsWith(site.strMount()))
                    return site;
            }
            return null;
        }


        /**
         * @param strPath a request path
         * @return the site mounted exactly one slash beyond it - `/usdcx` for
         *         `/usdcx/` - or null
         */
        public Site siteBelowSlash(String strPath) {
            for (Site site : lstSite) {
                if (site.strMount().equals(strPath + "/"))
                    return site;
            }
            return null;
        }


        /**
         * @return every mount, in the order served
         */
        public List<String> lstMount() {
            List<String> lstOut = new ArrayList<>();
            for (Site site : lstSite) {
                lstOut.add(site.strMount());
            }
            return lstOut;
        }
    }


    private RawarSites() {
    }


    /**
     * Reads the root.
     *
     * @param dirRoot the directory holding one directory per RAWAR; an absent
     *        one is no RAWARs, not an error
     * @return what is served and what is not
     */
    public static Scan scan(Path dirRoot) {
        List<String> lstProblem = new ArrayList<>();
        List<Path> lstDir = new ArrayList<>();
        if (dirRoot != null && Files.isDirectory(dirRoot)) {
            try (Stream<Path> stream = Files.list(dirRoot)) {
                stream.filter(Files::isDirectory).sorted().forEach(lstDir::add);
            }
            catch (IOException ex) {
                lstProblem.add(dirRoot + " cannot be listed: " + ex.getMessage());
            }
        }

        Map<String, Site> mapByMount = new LinkedHashMap<>();
        for (Path dir : lstDir) {
            String strName = dir.getFileName().toString();
            if (strName.startsWith("."))
                continue;
            if (!RawarTree.flagNameValid(strName)) {
                lstProblem.add(strName + " - not served: a RAWAR name is lower case, digits"
                        + " and -");
                continue;
            }
            RawarDescriptor desc = null;
            String strMount = RawarDescriptor.STR_MOUNT_DEFAULT;
            if (Files.isRegularFile(dir.resolve(RawarTree.STR_DESCRIPTOR))) {
                try {
                    desc = RawarDescriptor.read(dir);
                    if (desc.strMount() != null)
                        strMount = desc.strMount();
                }
                catch (IOException ex) {
                    lstProblem.add(strName + " - " + RawarTree.STR_DESCRIPTOR
                            + " not read, served at / : " + ex.getMessage());
                }
            }
            String strMountNorm = strMountNormalised(strMount);
            if (strMountNorm == null) {
                lstProblem.add(strName + " - not served: mount " + strMount
                        + " must start with / and hold no .. or _ledger");
                continue;
            }
            Site siteThere = mapByMount.get(strMountNorm);
            if (siteThere != null) {
                lstProblem.add(strName + " - not served: " + strMountNorm + " is "
                        + siteThere.strName() + "'s");
                continue;
            }
            mapByMount.put(strMountNorm, new Site(strName, strMountNorm, dir, desc));
        }

        List<Site> lstSite = new ArrayList<>(mapByMount.values());
        lstSite.sort((siteA, siteB) -> Integer.compare(siteB.strMount().length(),
                siteA.strMount().length()));
        return new Scan(lstSite, lstProblem);
    }


    /**
     * @param strMount a mount as written
     * @return it with a leading and a trailing slash and no doubled slash, or
     *         null when it is not a mount
     */
    static String strMountNormalised(String strMount) {
        if (strMount == null || !strMount.startsWith("/"))
            return null;
        String strOut = strMount.replaceAll("/{2,}", "/");
        if (!strOut.endsWith("/"))
            strOut = strOut + "/";
        for (String strPart : strOut.split("/")) {
            if (strPart.equals("..") || strPart.equals(".")
                    || strPart.equals(RawarTree.STR_RESERVED_LEDGER)
                    || strPart.equals(RawarTree.STR_RESERVED_ENV))
                return null;
        }
        return strOut;
    }
}
