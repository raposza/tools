// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.util.List;

/**
 * Whether the window should offer to install a toolchain when it opens.
 *
 * <h2>An offer is for a machine that cannot run anything</h2>
 *
 * The question is not "is everything present" - it is "can this developer start
 * a stack at all". A machine with a Canton on it can, and is left alone
 * whatever else is missing.
 *
 * <h2>The Daml Assistant silences the offer outright</h2>
 *
 * Its presence says the developer has a toolchain and a way of managing it, and
 * a window that offers to install one anyway is telling them something they
 * know better than it does. This holds even when no Canton has been found -
 * `daml install` is theirs to run.
 *
 * <h2>What is offered is DPM, and nothing before it</h2>
 *
 * The 2.x line is closed, its installer differs per platform, and it is
 * reachable by hand. So the offer is the one channel that covers everything
 * still being published, and a developer who wants the older line installs it
 * once themselves.
 *
 * Author Claude/bentzn
 */
public final class ToolchainAdvice {

    private ToolchainAdvice() {
    }


    /**
     * @param roots what the environment describes; never null
     * @param lstInstalled what discovery found; never null
     * @return whether to offer an install
     */
    public static boolean flagOffer(ToolchainRoots roots, List<CantonInstallation> lstInstalled) {
        if (roots == null || lstInstalled == null)
            throw new IllegalArgumentException("roots and a list of installations are required");

        if (roots.flagDaml())
            return false;

        return lstInstalled.isEmpty();
    }

}
