// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Which Daml SDK versions the assistant has on this machine.
 *
 * <h2>A version directory under the assistant root IS the answer</h2>
 *
 * `daml install` lands `&lt;root&gt;/sdk/&lt;version&gt;/`, so the directories
 * there that parse as a version are the SDKs that are installed. Nothing here
 * asks whether one of them carries a Canton: that is a different question,
 * answered by {@link CantonInstallations} against the same tree, and an SDK is
 * installed whether or not this application could start what is inside it.
 *
 * <h2>An absent root is an EMPTY SET, never an error</h2>
 *
 * A machine with no assistant has no SDKs, which is a fact about the machine
 * rather than a failure to read it.
 *
 * Author Claude/bentzn
 */
public final class SdkInstallations {

    /** Under the assistant root, one directory per installed SDK. */
    public static final String STR_DIR_SDK = ToolchainRoots.STR_MARKER_DAML;


    private SdkInstallations() {
    }


    /**
     * @param roots what the environment describes; never null
     * @return every SDK version under the assistant root; never null
     */
    public static Set<VersionId> setInstalled(ToolchainRoots roots) {
        if (roots == null)
            throw new IllegalArgumentException("roots are required");

        return setUnder(roots.dirDaml().resolve(STR_DIR_SDK));
    }


    /**
     * @param dirSdk the `sdk` directory, which need not exist
     * @return every version directory in it, in whatever order the filesystem
     *         gives them; never null
     */
    public static Set<VersionId> setUnder(Path dirSdk) {
        Set<VersionId> setOut = new LinkedHashSet<>();
        if (dirSdk == null)
            return Collections.unmodifiableSet(setOut);

        for (Path dirVersion : InstallFs.listDirectories(dirSdk)) {
            Optional<VersionId> optVersion =
                    VersionId.tryParse(dirVersion.getFileName().toString());
            optVersion.ifPresent(setOut::add);
        }
        return Collections.unmodifiableSet(setOut);
    }

}
