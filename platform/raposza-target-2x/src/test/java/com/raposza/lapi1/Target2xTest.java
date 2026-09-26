// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lapi1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.model.ApiGeneration;
import com.raposza.spi.Capability;
import com.raposza.spi.CapabilityReport;
import com.raposza.spi.Support;
import com.raposza.spi.Target_i;
import com.raposza.spi.Targets;

import org.junit.jupiter.api.Test;

/**
 * Registration and declaration. No participant is needed for either, which is
 * the property that makes "will this work against that version" answerable
 * before connecting.
 *
 * Author Claude/bentzn
 */
class Target2xTest {

    @Test
    void theTargetIsDiscoveredByServiceLoader() {
        Target_i target = Targets.only();
        assertEquals("canton-2x", target.id());
        assertEquals(ApiGeneration.V1, target.generation());
    }


    /**
     * Identity is deliberately NOT asserted. ServiceLoader instantiates a fresh
     * provider on every load, so two lookups return equal targets and not the
     * same object. A target must therefore stay stateless.
     */
    @Test
    void byGenerationFindsIt() {
        assertEquals(Targets.only().id(),
                Targets.byGeneration(ApiGeneration.V1).orElseThrow().id());
        assertTrue(Targets.byGeneration(ApiGeneration.V2).isEmpty());
    }


    @Test
    void theDeclarationIsComplete() {
        Target_i target = Targets.only();
        for (Capability capability : Capability.values()) {
            CapabilityReport report = target.capabilities().report(capability);
            assertNotNull(report, capability.strId() + " is undeclared");
        }
    }


    @Test
    void everyClaimCarriesItsEvidence() {
        for (CapabilityReport report : Targets.only().capabilities().reports()) {
            if (report.support() == Support.UNMEASURED)
                continue;
            assertNotNull(report.strVersionMeasured(),
                    report.capability().strId() + " claims " + report.support()
                            + " without naming a version");
            assertNotNull(report.dateMeasured(),
                    report.capability().strId() + " claims " + report.support()
                            + " without a measurement date");
        }
    }


    @Test
    void offsetsAreNotNumericOnV1() {
        assertEquals(Support.UNSUPPORTED,
                Targets.only().capabilities().support(Capability.OFFSET_NUMERIC));
    }

}
