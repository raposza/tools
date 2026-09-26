// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

/**
 * Whether this start should offer to build the Aviation fixture, and when it
 * should not, why not.
 *
 * <h2>The setting decides, and the machine decides what the question can offer</h2>
 *
 * The developer wants to be asked, and either the fixture is already built or
 * this machine can build it. Either failing is a reason not to raise a dialog,
 * and the machine gap is worth a line in the log rather than silence - a
 * question that never appears is otherwise indistinguishable from a question
 * that appeared and was answered before anybody looked.
 *
 * <b>A DAR ON DISK IS SOMETHING TO OFFER, NOT A REASON TO SKIP THE QUESTION.</b>
 * It used to be the third condition, and the caller then staged it anyway - so
 * a fixture built in one session was ticked into the next start of the next
 * session with nothing asked, and `Never` did not stop it either.
 *
 * <h2>Why a class of its own</h2>
 *
 * The window decides this on the event thread while a start is being set up,
 * and every input is something it already holds. Keeping the rule here means it
 * can be asserted for a machine with no toolchain, which is the case that
 * matters most and the one a developer's own machine can never be.
 *
 * <h2>WHAT IT DOES NOT DECIDE</h2>
 *
 * Whether a dialog may be raised AT ALL. An unattended run answers every modal
 * with the safe answer and aborts, so the caller asks {@link
 * com.raposza.sandbox.gui.Modals} rather than this class - a run with nobody in
 * front of it must not be stopped by a question about test data.
 *
 * Author Claude/bentzn
 */
public final class AviationOffer {

    /** The setting is off, so the developer has already answered this. */
    public static final String STR_WHY_SETTING = "the Aviation offer is switched off in Settings";

    /** No bundle names this Canton, so `build` has nothing to run under. */
    public static final String STR_WHY_TOOLCHAIN =
            "no Daml SDK on this machine can build for the selected Canton";

    private AviationOffer() {
    }


    /**
     * @param flagSetting what `fixture.aviation.offer` says
     * @param strSdk the SDK version that can build for the selected Canton, or
     *        null when none can - see {@link AviationFixture#strSdkFor}
     * @param flagBuilt whether the DAR for that Canton is already in the store
     * @return whether to raise the offer
     */
    public static boolean flagOffer(boolean flagSetting, String strSdk, boolean flagBuilt) {
        return strWhyNot(flagSetting, strSdk, flagBuilt) == null;
    }


    /**
     * @param flagSetting what `fixture.aviation.offer` says
     * @param strSdk the SDK version that can build, or null
     * @param flagBuilt whether the DAR is already in the store
     * @return why the offer is not being made, or null when it is
     */
    public static String strWhyNot(boolean flagSetting, String strSdk, boolean flagBuilt) {
        // THE SETTING IS TESTED FIRST, and it is the only one of the two that
        // is the developer's own decision. Reporting a toolchain gap to
        // somebody who has switched the whole thing off is noise about a
        // question they are not being asked.
        if (!flagSetting)
            return STR_WHY_SETTING;
        // STAGING A BUILT DAR NEEDS NO COMPILER, so a machine that cannot build
        // is still asked when the fixture is there.
        if (flagBuilt)
            return null;
        if (strSdk == null || strSdk.trim().isEmpty())
            return STR_WHY_TOOLCHAIN;
        return null;
    }

}
