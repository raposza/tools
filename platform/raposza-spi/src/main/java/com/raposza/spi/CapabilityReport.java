// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.spi;

import java.time.LocalDate;

/**
 * One capability, one claim, and the evidence behind it.
 *
 * The version and date are not decoration. "Supported" with neither is the
 * claim the status table in design sec. 7.1 has to be maintained by hand
 * because it was never machine-readable; putting them in the constructor is
 * what stops that table being rebuilt in Java.
 *
 * A claim describes THIS TARGET, not Canton. "Interface filters are
 * unsupported on 2.9.6" says the 2.x target refuses them; it says nothing
 * about whether a 2.9.6 participant could serve them.
 *
 * @param capability what is being claimed
 * @param support the claim
 * @param strVersionMeasured the Canton or SDK version the claim was measured
 *        against, e.g. "2.9.6"; null only when UNMEASURED
 * @param dateMeasured when it was measured; null only when UNMEASURED
 * @param strNote why, in one line. Required for anything other than SUPPORTED,
 *        because a bare refusal is the thing a caller then has to go and ask
 *        about
 *
 * Author Claude/bentzn
 */
public record CapabilityReport(Capability capability, Support support,
        String strVersionMeasured, LocalDate dateMeasured, String strNote) {

    public CapabilityReport {
        if (capability == null)
            throw new IllegalArgumentException("capability is required");
        if (support == null)
            throw new IllegalArgumentException("support is required");

        boolean flagMeasured = support != Support.UNMEASURED;

        if (flagMeasured && (strVersionMeasured == null || strVersionMeasured.isBlank())) {
            throw new IllegalArgumentException(capability.strId() + ": " + support
                    + " requires the version it was measured against");
        }
        if (flagMeasured && dateMeasured == null) {
            throw new IllegalArgumentException(capability.strId() + ": " + support
                    + " requires a measurement date");
        }
        if (!flagMeasured && (strVersionMeasured != null || dateMeasured != null)) {
            throw new IllegalArgumentException(capability.strId()
                    + ": UNMEASURED carries no version and no date; if it was measured,"
                    + " say what the measurement found");
        }
        if (support != Support.SUPPORTED && (strNote == null || strNote.isBlank())) {
            throw new IllegalArgumentException(capability.strId() + ": " + support
                    + " requires a note saying why");
        }
    }

}
