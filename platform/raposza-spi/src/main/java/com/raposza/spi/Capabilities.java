// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.spi;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A target's complete capability declaration.
 *
 * COMPLETE is enforced, not requested. build() refuses a declaration that omits
 * any Capability, so adding a member to that enum stops every target's build
 * until somebody says something about it - including "UNMEASURED, nobody has
 * looked". A declaration with holes in it is worse than none, because a hole
 * reads as a "no" to every caller and as an oversight to none.
 *
 * Author Claude/bentzn
 */
public final class Capabilities {

    private final String idTarget;

    private final Map<Capability, CapabilityReport> mapReport;


    private Capabilities(String idTarget, Map<Capability, CapabilityReport> mapReport) {
        this.idTarget = idTarget;
        this.mapReport = mapReport;
    }


    /**
     * @param idTarget the declaring target's id, used in refusal messages
     * @return a builder
     */
    public static Builder of(String idTarget) {
        return new Builder(idTarget);
    }


    /**
     * @param capability the capability
     * @return the claim; never null, because the declaration is complete
     */
    public Support support(Capability capability) {
        return mapReport.get(capability).support();
    }


    /**
     * @param capability the capability
     * @return the claim with its evidence; never null
     */
    public CapabilityReport report(Capability capability) {
        return mapReport.get(capability);
    }


    /** @return every report, in Capability declaration order */
    public List<CapabilityReport> reports() {
        return List.copyOf(mapReport.values());
    }


    /**
     * Gate a call on a declared refusal.
     *
     * UNMEASURED passes. "The gate reports rather than blocks", carried
     * into code: refusing an unmeasured capability would make the tool
     * useless against every version nobody has got to yet, which is every new
     * one, which is when the tool is most wanted.
     *
     * @param capability what the caller is about to do
     * @throws UnsupportedCapability when the target declares it will not
     */
    public void require(Capability capability) {
        CapabilityReport report = mapReport.get(capability);
        if (report.support() == Support.UNSUPPORTED)
            throw new UnsupportedCapability(capability, idTarget, report.strNote());
    }


    /** @return the declaring target's id */
    public String idTarget() {
        return idTarget;
    }


    /** Builder. Every Capability must be spoken for before build() returns. */
    public static final class Builder {

        private final String idTarget;

        private final Map<Capability, CapabilityReport> mapReport =
                new EnumMap<>(Capability.class);


        private Builder(String idTarget) {
            if (idTarget == null || idTarget.isBlank())
                throw new IllegalArgumentException("idTarget is required");
            this.idTarget = idTarget;
        }


        /**
         * @param capability the capability
         * @param strVersion the version it was measured green against
         * @param dateMeasured when
         * @return this
         */
        public Builder supported(Capability capability, String strVersion,
                LocalDate dateMeasured) {
            return put(new CapabilityReport(capability, Support.SUPPORTED, strVersion,
                    dateMeasured, null));
        }


        /**
         * @param capability the capability
         * @param strVersion the version the refusal applies to
         * @param dateMeasured when
         * @param strNote why, in one line
         * @return this
         */
        public Builder unsupported(Capability capability, String strVersion,
                LocalDate dateMeasured, String strNote) {
            return put(new CapabilityReport(capability, Support.UNSUPPORTED, strVersion,
                    dateMeasured, strNote));
        }


        /**
         * @param capability the capability
         * @param strNote what would have to happen for this to become a claim
         * @return this
         */
        public Builder unmeasured(Capability capability, String strNote) {
            return put(new CapabilityReport(capability, Support.UNMEASURED, null, null,
                    strNote));
        }


        /**
         * @param strNote applied to every capability not already spoken for
         * @return this
         */
        public Builder unmeasuredRest(String strNote) {
            for (Capability capability : Capability.values()) {
                if (!mapReport.containsKey(capability))
                    unmeasured(capability, strNote);
            }
            return this;
        }


        private Builder put(CapabilityReport report) {
            CapabilityReport reportOld = mapReport.put(report.capability(), report);
            if (reportOld != null) {
                throw new IllegalStateException(idTarget + " declares "
                        + report.capability().strId() + " twice");
            }
            return this;
        }


        /**
         * @return the declaration
         * @throws IllegalStateException when any capability is unspoken for
         */
        public Capabilities build() {
            List<String> lstMissing = new ArrayList<>();
            for (Capability capability : Capability.values()) {
                if (!mapReport.containsKey(capability))
                    lstMissing.add(capability.strId());
            }
            if (!lstMissing.isEmpty()) {
                throw new IllegalStateException(idTarget
                        + " declares nothing about " + String.join(", ", lstMissing)
                        + ". Say UNMEASURED if nobody has looked - silence reads as a"
                        + " refusal to every caller and as an oversight to none");
            }

            Map<Capability, CapabilityReport> mapOrdered = new EnumMap<>(Capability.class);
            mapOrdered.putAll(mapReport);
            return new Capabilities(idTarget, mapOrdered);
        }

    }

}
