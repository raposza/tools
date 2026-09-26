// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which Daml SDK versions can be fetched from the vendor's GitHub releases, and
 * what each one's asset is called.
 *
 * <h2>The 2.x list is HARDCODED because that line is closed</h2>
 *
 * 2.10 is the last 2.x line, so the set below cannot grow except by a further
 * 2.10 patch, and enumerating a closed set over the network on every window
 * open buys nothing. The entries are the STABLE releases from 2.8.12 onward, as
 * published - the gaps are real: there is no 2.9.2, no 2.10.5, and the 2.9.0
 * and 2.10.0 preview builds are excluded along with every release candidate and
 * snapshot.
 *
 * <b>The 3.x side of this table is five patches and stops there.</b> Stable 3.5
 * does not exist on GitHub at all - the assistant is retired at 3.5 - so the
 * releases carry 3.4.4 and 3.4.8 to 3.4.11 for that line and nothing above it.
 * Every one of them is also a DPM bundle, and which row a reader is shown is
 * decided once, in {@link SdkOffers}, rather than by leaving them out here.
 *
 * <h2>The Canton tarball is not the artefact</h2>
 *
 * The releases also carry `canton-open-source-&lt;version&gt;.tar.gz`, and its
 * version set does NOT line up with the SDK's - 3.4.7 has a Canton tarball and
 * no SDK, 3.4.4 has an SDK and no Canton tarball. The SDK is what this installs
 * because it lands the Canton jar, the compiler and the script runner together,
 * in the layout discovery already walks, and it carries its own installer.
 *
 * Author Claude/bentzn
 */
public final class SdkCatalogue {

    /** Where a release asset is downloaded from, with the tag interpolated. */
    public static final String STR_URL_RELEASE =
            "https://github.com/digital-asset/daml/releases/download/v%s/%s";

    /** What every SDK asset is called, before the platform suffix. */
    public static final String STR_PREFIX_ASSET = "daml-sdk-";

    public static final String STR_SUFFIX_ASSET = ".tar.gz";

    /** The installer inside the tarball, per platform. */
    public static final String STR_INSTALLER_UNIX = "install.sh";

    public static final String STR_INSTALLER_WINDOWS = "install.bat";

    /**
     * The stable 2.x releases from 2.8.12, newest last.
     *
     * <b>Do not "fill in" the gaps.</b> 2.9.2, 2.9.8, 2.10.5 and the rest were
     * never published, and a version added here that has no release produces a
     * 404 at download time rather than an error the catalogue can report.
     */
    private static final List<String> LST_VERSION_2X = List.of(
            "2.8.12",
            "2.9.1", "2.9.3", "2.9.4", "2.9.5", "2.9.6", "2.9.7",
            "2.10.0", "2.10.1", "2.10.2", "2.10.3", "2.10.4", "2.10.6");

    /**
     * What the releases carry for 3.x, newest last. THE GAPS ARE REAL: there is
     * no 3.4.5, 3.4.6 or 3.4.7 SDK asset, and no stable 3.5 asset of any patch.
     */
    private static final List<String> LST_VERSION_3X = List.of(
            "3.4.4", "3.4.8", "3.4.9", "3.4.10", "3.4.11");


    private SdkCatalogue() {
    }


    /**
     * @param platform the platform to install for; never null
     * @return every version this catalogue can install there, newest first;
     *         empty where the platform has no 2.x asset at all
     */
    public static List<VersionId> lstVersion(HostPlatform platform) {
        if (platform == null)
            throw new IllegalArgumentException("a platform is required");

        List<VersionId> lstOut = new ArrayList<>();
        if (platform.strAsset2x() == null)
            return lstOut;

        for (String strVersion : LST_VERSION_2X) {
            lstOut.add(VersionId.parse(strVersion));
        }
        Collections.sort(lstOut);
        Collections.reverse(lstOut);
        return lstOut;
    }


    /**
     * SEPARATE FROM {@link #lstVersion}, because the two lines are not one
     * catalogue: they name their assets differently, they overlap with the DPM
     * channel differently, and a caller wanting one of them wants exactly one.
     *
     * @param platform the platform to install for; never null
     * @return the 3.x versions the releases carry, newest first
     */
    public static List<VersionId> lstVersion3x(HostPlatform platform) {
        if (platform == null)
            throw new IllegalArgumentException("a platform is required");

        List<VersionId> lstOut = new ArrayList<>();
        for (String strVersion : LST_VERSION_3X) {
            lstOut.add(VersionId.parse(strVersion));
        }
        Collections.sort(lstOut);
        Collections.reverse(lstOut);
        return lstOut;
    }


    /**
     * @param version the version to name an asset for; never null
     * @param platform the platform; never null
     * @return the asset's file name, or null where that line publishes nothing
     *         for that platform
     */
    public static String strAsset(VersionId version, HostPlatform platform) {
        if (version == null || platform == null)
            throw new IllegalArgumentException("a version and a platform are required");

        String strSuffix = version.major() >= 3 ? platform.strAsset3x() : platform.strAsset2x();
        if (strSuffix == null)
            return null;

        return STR_PREFIX_ASSET + version + "-" + strSuffix + STR_SUFFIX_ASSET;
    }


    /**
     * @param version the version to fetch; never null
     * @param platform the platform; never null
     * @return the download URL, or null where there is no asset to fetch
     */
    public static String strUrl(VersionId version, HostPlatform platform) {
        String strAsset = strAsset(version, platform);
        if (strAsset == null)
            return null;

        return String.format(STR_URL_RELEASE, version, strAsset);
    }


    /**
     * The tarball carries its own installer and that is what runs it - the
     * Windows `.exe` installer reports a normal completion having written a
     * directory skeleton and no payload, so it is never used.
     *
     * @param platform the platform; never null
     * @return the installer's name inside the extracted SDK directory
     */
    public static String strInstaller(HostPlatform platform) {
        if (platform == null)
            throw new IllegalArgumentException("a platform is required");

        return platform.flagWindows() ? STR_INSTALLER_WINDOWS : STR_INSTALLER_UNIX;
    }


    /**
     * @param version a version; never null
     * @return whether this catalogue publishes it
     */
    public static boolean flagKnown(VersionId version) {
        if (version == null)
            throw new IllegalArgumentException("a version is required");

        return LST_VERSION_2X.contains(version.toString());
    }

}
