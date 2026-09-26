// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.nio.file.Path;
import java.util.List;

/**
 * How DPM itself is acquired, on a machine that has no toolchain at all.
 *
 * <h2>Read off the vendor's own installer, not guessed</h2>
 *
 * Their shell installer resolves a version from a `latest` endpoint, maps
 * `uname` to an operating system and an architecture, composes
 * `dpm-&lt;version&gt;-&lt;os&gt;-&lt;arch&gt;.tar.gz`, extracts it stripping
 * the archive's single top directory, and runs the extracted `bin/dpm
 * bootstrap &lt;dir&gt;`. Everything below is that sequence, expressed as data
 * so it can be driven from a window rather than from a shell.
 *
 * <b>The architecture token is `amd64`, not `x86_64`.</b> The two lines differ
 * here and the difference is not cosmetic: `dpm-&lt;v&gt;-linux-amd64.tar.gz`
 * answers, `dpm-&lt;v&gt;-linux-x86_64.tar.gz` does not exist, while the Daml
 * SDK on the same version uses the opposite convention. Two vendors' habits in
 * one product.
 *
 * <b>Windows is not served by that installer at all</b> - it refuses any
 * `uname` that is not Linux or Darwin - but the archive exists, as a zip rather
 * than a tarball, and installs the same way once it is extracted.
 *
 * <h2>The archive host</h2>
 *
 * Two paths serve these archives. The vendor's script fetches from a Google
 * artifact registry download URL; the host below is the one this project has
 * actually installed a working DPM from, and it answers for the Linux, macOS
 * and Windows archives alike. Nothing here depends on which of the two is
 * canonical, and the constant is the only place to change it.
 *
 * <h2>What this class does NOT do</h2>
 *
 * It fetches nothing, extracts nothing and starts nothing. Composing a name and
 * running an installer are separate concerns, and keeping them apart is what
 * makes every rule above assertable with no network and no toolchain.
 *
 * Author Claude/bentzn
 */
public final class DpmBootstrap {

    /** Answers the newest published DPM version, as bare text. */
    public static final String STR_URL_LATEST = "https://get.digitalasset.com/install/latest";

    /** Where an archive is fetched from, with the archive's own name appended. */
    public static final String STR_URL_ARCHIVE = "https://get.digitalasset.com/install/dpm-sdk/";

    public static final String STR_PREFIX_ARCHIVE = "dpm-";

    /** The subcommand that installs an extracted tree into the DPM root. */
    public static final String STR_CMD_BOOTSTRAP = "bootstrap";

    public static final String STR_DIR_BIN = "bin";

    public static final String STR_BIN_UNIX = "dpm";

    public static final String STR_BIN_WINDOWS = "dpm.exe";

    /**
     * The tarballs carry ONE top directory, named for the platform, and the
     * vendor's installer removes it on the way in. A zip is extracted whole and
     * that directory is entered instead - which is why the extracted root is
     * asked for rather than assumed.
     */
    public static final int N_STRIP_COMPONENTS = 1;


    private DpmBootstrap() {
    }


    /**
     * @param platform the platform; never null
     * @return the `&lt;os&gt;-&lt;arch&gt;` token the archive is named for
     */
    public static String strTarget(HostPlatform platform) {
        if (platform == null)
            throw new IllegalArgumentException("a platform is required");

        return switch (platform) {
            case LINUX_X64 -> "linux-amd64";
            case LINUX_ARM64 -> "linux-arm64";
            case MACOS_X64 -> "darwin-amd64";
            case WINDOWS_X64 -> "windows-amd64";
        };
    }


    /**
     * @param platform the platform; never null
     * @return `.zip` on Windows, `.tar.gz` everywhere else
     */
    public static String strExtension(HostPlatform platform) {
        if (platform == null)
            throw new IllegalArgumentException("a platform is required");

        return platform.flagWindows() ? ".zip" : ".tar.gz";
    }


    /**
     * @param strVersion the DPM version, as the `latest` endpoint reports it
     * @param platform the platform; never null
     * @return the archive's file name
     */
    public static String strArchive(String strVersion, HostPlatform platform) {
        if (strVersion == null || strVersion.isBlank())
            throw new IllegalArgumentException("a version is required");

        return STR_PREFIX_ARCHIVE + strVersion.trim() + "-" + strTarget(platform)
                + strExtension(platform);
    }


    /**
     * @param strVersion the DPM version
     * @param platform the platform
     * @return where to fetch that archive
     */
    public static String strUrl(String strVersion, HostPlatform platform) {
        return STR_URL_ARCHIVE + strArchive(strVersion, platform);
    }


    /**
     * @param dirExtracted the directory the archive's contents ended up in -
     *        the one holding `bin`, after any top directory has been removed or
     *        entered
     * @param platform the platform; never null
     * @return the launcher inside it
     */
    public static Path fileLauncher(Path dirExtracted, HostPlatform platform) {
        if (dirExtracted == null)
            throw new IllegalArgumentException("an extracted directory is required");

        String strBin = platform != null && platform.flagWindows()
                ? STR_BIN_WINDOWS : STR_BIN_UNIX;
        return dirExtracted.resolve(STR_DIR_BIN).resolve(strBin);
    }


    /**
     * The extracted tree installs ITSELF, and it is given its own directory as
     * the argument. That is the vendor's sequence and it needs no elevation, no
     * package manager and no interactive step.
     *
     * @param dirExtracted the directory holding `bin`; never null
     * @param platform the platform; never null
     * @return the command line to run
     */
    public static List<String> lstCommand(Path dirExtracted, HostPlatform platform) {
        return List.of(fileLauncher(dirExtracted, platform).toString(), STR_CMD_BOOTSTRAP,
                dirExtracted.toString());
    }

}
