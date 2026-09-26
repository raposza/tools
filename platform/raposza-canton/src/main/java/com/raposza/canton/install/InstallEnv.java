// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The environment a vendor installer has to be spawned with.
 *
 * <h2>`TMP` and `TEMP` are not optional on Windows</h2>
 *
 * The Daml SDK installer expands into a directory under `%LOCALAPPDATA%\Temp`
 * and then MOVES that directory to its destination. On the Windows guest this
 * project tests on, that move is denied - same volume, so not a cross-device
 * rename - and the failure is silent: the graphical installer runs to a normal
 * completion dialog and leaves an empty directory skeleton behind. Two
 * different SDK versions produced byte-identical empty results, which is what
 * ruled out a bad download.
 *
 * Redirecting both variables to a directory this application created makes
 * every install succeed. <b>Setting them in a shell does not reach a process
 * this application spawns</b> - they belong on the ProcessBuilder's own
 * environment, which is why they are here rather than in a script.
 *
 * The cause of the denial was never established. Candidates are a virus
 * scanner holding handles on the freshly written tree, or an ACL on that
 * particular image's temp directory. The workaround costs one directory, so it
 * is applied rather than diagnosed.
 *
 * <h2>`DPM_HOME` is how an install is kept out of the user's own root</h2>
 *
 * DPM reads its root from that variable, and it creates the root when it is
 * missing. Passing one lets an install land somewhere this application owns and
 * can remove again, which is what makes a test on a throwaway machine
 * repeatable. Pass null to leave the user's own root alone.
 *
 * Author Claude/bentzn
 */
public final class InstallEnv {

    public static final String STR_ENV_TMP = "TMP";

    public static final String STR_ENV_TEMP = "TEMP";

    public static final String STR_ENV_DPM_HOME = ToolchainRoots.STR_ENV_DPM_HOME;


    private InstallEnv() {
    }


    /**
     * @param platform the platform being installed on; never null
     * @param dirStage a directory this application created for the installer to
     *        expand into, or null to leave the temp directory alone
     * @param dirDpmHome the DPM root to install into, or null for the user's
     *        own
     * @return the variables to set, in a stable order; never null
     */
    public static Map<String, String> mapOverride(HostPlatform platform, Path dirStage,
            Path dirDpmHome) {
        if (platform == null)
            throw new IllegalArgumentException("a platform is required");

        Map<String, String> mapOut = new LinkedHashMap<>();
        if (platform.flagWindows() && dirStage != null) {
            String strStage = dirStage.toAbsolutePath().toString();
            mapOut.put(STR_ENV_TMP, strStage);
            mapOut.put(STR_ENV_TEMP, strStage);
        }
        if (dirDpmHome != null)
            mapOut.put(STR_ENV_DPM_HOME, dirDpmHome.toAbsolutePath().toString());

        return mapOut;
    }


    /**
     * Applies {@link #mapOverride} to a builder's own environment, which is
     * inherited from this process and then overridden - nothing is removed.
     *
     * @param builder the builder to configure; never null
     * @param platform the platform; never null
     * @param dirStage the staging directory, or null
     * @param dirDpmHome the DPM root, or null
     */
    public static void apply(ProcessBuilder builder, HostPlatform platform, Path dirStage,
            Path dirDpmHome) {
        if (builder == null)
            throw new IllegalArgumentException("a process builder is required");

        builder.environment().putAll(mapOverride(platform, dirStage, dirDpmHome));
    }

}
