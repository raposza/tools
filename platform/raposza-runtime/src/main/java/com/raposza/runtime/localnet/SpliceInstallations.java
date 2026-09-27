// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.raposza.runtime.settings.RaposzaSettings;

/**
 * Which Splice bundles this machine has, read off the filesystem.
 *
 * <h2>Where they are, and why that directory</h2>
 *
 * `~/.splice/&lt;version&gt;/splice-node/`. D-305 settles the location: `~/.splice`
 * is ours the way `~/.pqs` is, and the archive is never deleted -
 * `canton_inventory.md` section 694 lists the bundles staged there. The layout
 * is the vendor's own: the release tarball unpacks to `splice-node/`, so the
 * version is the directory ABOVE it.
 *
 * <b>`dir.splice` moves the bundles and nothing else - todo.md A-45.</b> A
 * directory set there is where they are looked for and where a download from
 * the window lands, laid out the same way. LocalNetND's run directory
 * {@link #STR_DIR_RUN} stays under `~/.splice` whatever it says - the
 * operator's answer of 2026-09-27 - so it hangs off {@link #dirRootDefault()}.
 *
 * <h2>The test is the launcher, not the directory name</h2>
 *
 * `bin/splice-node` executable is the same condition {@link LocalNetRunner#of}
 * refuses on, deliberately: a list that offered a version the runner then
 * rejects would move the failure from the dropdown to the start, where it
 * costs a minute instead of nothing. A half-extracted bundle is therefore not
 * listed rather than listed and broken.
 *
 * NEWEST FIRST, by a plain descending sort of the version directory's name.
 * The names are dotted numbers of equal shape - 0.6.14, 0.7.1, 0.7.4 - so a
 * numeric comparison per segment orders them the way a reader expects, and
 * anything that does not parse sorts last rather than throwing.
 *
 * <h2>What a bundle says it was built with - todo.md A-39</h2>
 *
 * Every bundle carries the vendor's own statement of the Canton and Daml SDK
 * versions it was built with, as the rendered page {@link #STR_FILE_VERSIONS}.
 * MEASURED 2026-09-23 on all 22 staged bundles, 0.6.0 to 0.8.1: the page names
 * the three every time - the Canton of the validator and SV nodes, the SDK
 * that compiled the `.dars`, the SDK of the Java and TS codegens. Reading it is
 * one small file, not a JVM over the 607 MB jar the launcher banner costs, and
 * the banner names Canton, not the SDK.
 *
 * THE DROPDOWN SHOWS THE SDK AND NOT THE PAGE'S CANTON. The page's Canton is
 * the vendor's IMAGE pin: for 0.7.3 it states
 * `3.5.14-snapshot.20260815.19176.0.v65fa04f6`, which is `docker/canton:0.7.3`,
 * while `lib/splice-node.jar` carries `3.5.0-snapshot` - `splice_inventory.md`
 * section 5. Neither is necessarily the Canton a native stack runs, so it is
 * kept in {@link Versions} and not shown beside the version.
 *
 * The page's source substitutes `|daml_sdk_version|` and friends at build time,
 * so the RENDERED HTML is read, tags stripped, and each value is the token
 * after its label. A bundle whose page is missing or worded otherwise answers
 * null and the dropdown shows the version alone - never a guess.
 *
 * Author Claude/bentzn
 */
public final class SpliceInstallations {

    /** The directory under the home directory, per D-305. */
    public static final String STR_DIR = ".splice";

    /** What the release tarball unpacks to, inside the version directory. */
    public static final String STR_BUNDLE = "splice-node";

    /** The run directory LocalNetRunner defaults to; NOT a bundle. */
    public static final String STR_DIR_RUN = "native-localnet";

    /** The vendor's version page, relative to the bundle. */
    public static final String STR_FILE_VERSIONS = "docs/html/app_dev/overview/version_information.html";

    static final String STR_LABEL_CANTON = "Canton version used for validator and SV nodes";

    static final String STR_LABEL_SDK_DAR = "Daml SDK version used to compile .dars";

    static final String STR_LABEL_SDK_CODEGEN = "Daml SDK version used for Java and TS codegens";


    /**
     * What a bundle states it was built with.
     *
     * @param strCanton the Canton the page names for its validator and SV
     *        nodes - the vendor's image pin, not the jar's
     * @param strSdkDar the Daml SDK that compiled its `.dars`
     * @param strSdkCodegen the Daml SDK of its Java and TS codegens, or null
     *        when the page does not say
     */
    public record Versions(String strCanton, String strSdkDar, String strSdkCodegen) {
    }


    private SpliceInstallations() {
    }


    /**
     * @return the root the versions sit under: `dir.splice` when it is set,
     *         else {@link #dirRootDefault()}
     */
    public static Path dirRoot() {
        Path dirSet = RaposzaSettings.current().dirSplice();
        return dirSet != null ? dirSet : dirRootDefault();
    }


    /**
     * @return `~/.splice`, which the run directory stays under
     */
    public static Path dirRootDefault() {
        return Path.of(System.getProperty("user.home"), STR_DIR);
    }


    /**
     * @return every staged version, newest first, never null
     */
    public static List<String> lstVersion() {
        Path dirRoot = dirRoot();
        List<String> lstOut = new ArrayList<>();
        if (!Files.isDirectory(dirRoot))
            return lstOut;

        try (Stream<Path> strm = Files.list(dirRoot)) {
            for (Path dirVersion : strm.toList()) {
                if (STR_DIR_RUN.equals(dirVersion.getFileName().toString()))
                    continue;
                if (Files.isExecutable(dirVersion.resolve(STR_BUNDLE).resolve("bin")
                        .resolve(STR_BUNDLE)))
                    lstOut.add(dirVersion.getFileName().toString());
            }
        }
        catch (IOException ex) {
            return lstOut;
        }
        lstOut.sort(Comparator.comparing(SpliceInstallations::strSortKey).reversed());
        return lstOut;
    }


    /**
     * @param strVersion a staged version
     * @return where its bundle is, whether or not it exists
     */
    public static Path dirBundle(String strVersion) {
        return dirRoot().resolve(strVersion).resolve(STR_BUNDLE);
    }


    /**
     * @param strVersion a staged version
     * @return what its bundle states it was built with, or null when the page
     *         is missing or does not carry both the Canton and the SDK
     */
    public static Versions versionsOf(String strVersion) {
        try {
            return versionsOfHtml(Files.readString(dirBundle(strVersion).resolve(STR_FILE_VERSIONS)));
        }
        catch (IOException | UncheckedIOException ex) {
            return null;
        }
    }


    /**
     * @param strVersion a staged version
     * @return the version as the dropdown shows it: with the Daml SDK that
     *         compiled its `.dars` when the bundle states it, alone when not
     */
    public static String strShown(String strVersion) {
        Versions versions = versionsOf(strVersion);
        if (versions == null)
            return strVersion;
        return strVersion + "  -  Daml SDK " + versions.strSdkDar();
    }


    /**
     * @param strHtml the rendered version page
     * @return the three values, or null when the Canton or the dar SDK is absent
     */
    static Versions versionsOfHtml(String strHtml) {
        String strText = strHtml.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ");
        String strCanton = strAfter(strText, STR_LABEL_CANTON);
        String strSdkDar = strAfter(strText, STR_LABEL_SDK_DAR);
        if (strCanton == null || strSdkDar == null)
            return null;
        return new Versions(strCanton, strSdkDar, strAfter(strText, STR_LABEL_SDK_CODEGEN));
    }


    /**
     * @param strText the page as text, whitespace collapsed
     * @param strLabel a row label
     * @return the token after it, or null when the label is absent or nothing
     *         follows it
     */
    private static String strAfter(String strText, String strLabel) {
        int idx = strText.indexOf(strLabel);
        if (idx < 0)
            return null;
        String strRest = strText.substring(idx + strLabel.length()).trim();
        if (strRest.isEmpty())
            return null;
        int idxSpace = strRest.indexOf(' ');
        return idxSpace < 0 ? strRest : strRest.substring(0, idxSpace);
    }


    /**
     * A version as one sortable string, each segment zero-padded to four
     * digits, so 0.7.10 sorts above 0.7.9 rather than below it. A plain string
     * comparison of the versions themselves gets that pair wrong, which is the
     * whole reason this exists.
     *
     * @param strVersion the directory's name
     * @return its sort key; a segment with no digits contributes zeroes and so
     *         sorts last under the reversed comparator
     */
    private static String strSortKey(String strVersion) {
        StringBuilder bld = new StringBuilder();
        for (String strPart : strVersion.split("\\.")) {
            String strDigits = strPart.replaceAll("[^0-9]", "");
            if (strDigits.length() > 4)
                strDigits = strDigits.substring(strDigits.length() - 4);
            while (strDigits.length() < 4) {
                strDigits = "0" + strDigits;
            }
            bld.append(strDigits);
        }
        return bld.toString();
    }

}
