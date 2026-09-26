// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

/**
 * Whether this window should offer to build the Pharma fixture, and when it
 * should not, why not.
 *
 * <h2>Two conditions, not Aviation's three</h2>
 *
 * The machine can build it, or the DAR is already in the store. There is no
 * SETTING here yet, and that is a deliberate gap rather than an oversight: the
 * Aviation offer carries a `Never` because it is raised on every Sandbox start,
 * and a `fixture.pharma.offer` needs a component on {@link
 * com.raposza.runtime.settings.RaposzaSettings} and a row on the Settings tab -
 * which is the delivery after this one. Until then the question is Yes or Not
 * now, once per window.
 *
 * <h2>Why a class of its own</h2>
 *
 * The window decides this on the event thread at open, and every input is
 * something it already holds. Keeping the rule here means it can be asserted
 * for a machine with no toolchain, which is the case that matters most and the
 * one a developer's own machine can never be.
 *
 * <h2>WHAT IT DOES NOT DECIDE</h2>
 *
 * Whether a dialog may be raised at all. An unattended run answers every modal
 * with the safe answer and aborts, so the caller asks {@link
 * com.raposza.sandbox.gui.Modals} rather than this class.
 *
 * Author Claude/bentzn
 */
public final class PharmaOffer {

    /** The setting is off, so the developer has already answered this. */
    public static final String STR_WHY_SETTING = "the Pharma offer is switched off in Settings";

    /** No bundle names this Canton, so `build` has nothing to run under. */
    public static final String STR_WHY_TOOLCHAIN =
            "no Daml SDK on this machine can build for the Canton LocalNetND runs";

    /** This topology has no Canton to build against, which is a start away. */
    public static final String STR_WHY_NO_CANTON =
            "no installed Canton carries a runtime jar, and the participants run inside one";

    private PharmaOffer() {
    }


    /**
     * @param flagSetting what `fixture.pharma.offer` says
     * @param strSdk the SDK version that can build for the Canton LocalNetND
     *        runs, or null when none can - see {@link PharmaFixture#strSdkFor}
     * @param flagBuilt whether the DAR for that Canton is already in the store
     * @return whether to raise the offer
     */
    public static boolean flagOffer(boolean flagSetting, String strSdk, boolean flagBuilt) {
        return strWhyNot(flagSetting, strSdk, flagBuilt) == null;
    }


    /**
     * @param flagSetting what `fixture.pharma.offer` says
     * @param strSdk the SDK version that can build, or null
     * @param flagBuilt whether the DAR is already in the store
     * @return why the offer is not being made, or null when it is
     */
    public static String strWhyNot(boolean flagSetting, String strSdk, boolean flagBuilt) {
        // THE SETTING IS TESTED FIRST, and it is the only one of the three that
        // is the developer's own decision. Reporting a toolchain gap to
        // somebody who has switched the whole thing off is noise about a
        // question they are not being asked - Aviation's rule.
        if (!flagSetting)
            return STR_WHY_SETTING;
        // STAGING A BUILT DAR NEEDS NO COMPILER, so a machine that cannot build
        // is still asked when the fixture is there - Aviation's rule, and it
        // was learned at cost there.
        if (flagBuilt)
            return null;
        if (strSdk == null || strSdk.trim().isEmpty())
            return STR_WHY_TOOLCHAIN;
        return null;
    }

}
