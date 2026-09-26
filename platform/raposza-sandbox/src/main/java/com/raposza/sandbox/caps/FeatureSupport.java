// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.caps;

/**
 * One answer about one feature, and WHERE THE ANSWER CAME FROM.
 *
 * <h2>The provenance is not decoration</h2>
 *
 * `CantonLaunchTable` refuses to answer for a version it never measured, on
 * the grounds that a table which answered for unseen binaries would be a
 * major-version rule wearing a data structure. That refusal is right for a
 * table and useless for a window, which has to lay something out either way.
 *
 * So this type carries both: what the answer is, and whether it was MEASURED on
 * that exact binary or EXTRAPOLATED from the nearest one that was. A surface
 * can act on the answer and still say, in a tooltip, that nobody has run this
 * version.
 *
 * <h2>UNKNOWN exposes, it does not hide</h2>
 *
 * {@link #isExposed()} is true for YES and for UNKNOWN, and false only for NO.
 * Hiding a setting because nothing is known about it takes the control away
 * from the one person who could find out; hiding it because it is KNOWN not to
 * apply is the whole point of the class.
 *
 * @param feature what was asked about
 * @param support the answer
 * @param provenance where the answer came from
 * @param strWhy one line a tooltip can show; never null, possibly empty
 *
 * Author Claude/bentzn
 */
public record FeatureSupport(SandboxFeature feature, Support support, Provenance provenance,
        String strWhy) {

    /** What the answer is. */
    public enum Support {

        /** The feature is there. */
        YES,

        /** The feature is NOT there, and that is established rather than assumed. */
        NO,

        /** Nothing is established. Never read this as NO. */
        UNKNOWN
    }

    /** Where the answer came from. */
    public enum Provenance {

        /** Read off that exact binary, or off the stack this application runs. */
        MEASURED,

        /** Taken from the nearest measured binary, which strWhy names. */
        EXTRAPOLATED,

        /**
         * Nowhere. The companion of UNKNOWN, and the reason provenance is not
         * a boolean: an answer with nothing behind it must not be able to
         * claim a measurement by default.
         */
        NONE
    }


    public FeatureSupport {
        if (feature == null)
            throw new IllegalArgumentException("a feature is required");
        if (support == null)
            throw new IllegalArgumentException("a support state is required");
        if (provenance == null)
            throw new IllegalArgumentException("a provenance is required");
        strWhy = strWhy == null ? "" : strWhy;
    }


    /**
     * @return whether the feature is established as present
     */
    public boolean isYes() {
        return support == Support.YES;
    }


    /**
     * @return whether a surface should offer it - true unless it is known not
     *         to apply
     */
    public boolean isExposed() {
        return support != Support.NO;
    }


    /**
     * @return whether the answer was read off this exact binary
     */
    public boolean isMeasured() {
        return provenance == Provenance.MEASURED;
    }


    /**
     * @return the tooltip a surface shows beside the control
     */
    public String strTooltip() {
        StringBuilder sb = new StringBuilder();
        sb.append(feature.strLabel()).append(": ").append(support.name());
        if (!strWhy.isEmpty())
            sb.append(" - ").append(strWhy);
        return sb.toString();
    }
}
