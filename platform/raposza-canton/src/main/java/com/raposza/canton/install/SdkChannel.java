// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

/**
 * Which toolchain installs an SDK, and therefore which command acquires it.
 *
 * <h2>The channel is a property of the VERSION, not a preference</h2>
 *
 * The 2.x line exists only as a GitHub release the Daml Assistant installs; the
 * 3.5 line and everything above it exists only in the registry DPM reads.
 * Between them sits a handful of 3.4 patches published both ways, and for those
 * the choice is made once rather than offered - see {@link SdkOffers}.
 *
 * <h2>Neither channel installs the other's versions</h2>
 *
 * `daml install` resolves a GitHub asset and `dpm install` resolves a bundle in
 * the registry. Handing either the other's version fails at resolution, so the
 * channel travels with the version everywhere it goes.
 *
 * Author Claude/bentzn
 */
public enum SdkChannel {

    /** The Daml Assistant, `daml install`, from the GitHub releases. */
    ASSISTANT("daml"),

    /** The Digital Asset Package Manager, `dpm install`, from the registry. */
    DPM("dpm");

    private final String strLabel;


    SdkChannel(String strLabelNew) {
        this.strLabel = strLabelNew;
    }


    /**
     * @return what a window shows, which is the command's own name
     */
    public String strLabel() {
        return strLabel;
    }

}
