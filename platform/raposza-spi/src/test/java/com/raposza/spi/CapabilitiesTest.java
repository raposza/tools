// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * The declaration's invariants, which are the point of the class.
 *
 * Author Claude/bentzn
 */
class CapabilitiesTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 10);


    private Capabilities.Builder complete() {
        return Capabilities.of("test-target").unmeasuredRest("nobody has looked");
    }


    @Test
    void incompleteDeclarationIsRefused() {
        Capabilities.Builder bld = Capabilities.of("test-target")
                .supported(Capability.PARTY_LIST, "2.9.6", DATE);

        IllegalStateException ex = assertThrows(IllegalStateException.class, bld::build);
        assertTrue(ex.getMessage().contains(Capability.COMMAND_SUBMIT.strId()),
                "the message must name what is missing");
    }


    @Test
    void everyCapabilityIsSpokenForAfterUnmeasuredRest() {
        Capabilities caps = complete().build();
        assertEquals(Capability.values().length, caps.reports().size());
    }


    @Test
    void supportedWithoutEvidenceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new CapabilityReport(
                Capability.PARTY_LIST, Support.SUPPORTED, null, DATE, null));
        assertThrows(IllegalArgumentException.class, () -> new CapabilityReport(
                Capability.PARTY_LIST, Support.SUPPORTED, "2.9.6", null, null));
    }


    @Test
    void unmeasuredCarryingEvidenceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new CapabilityReport(
                Capability.PARTY_LIST, Support.UNMEASURED, "2.9.6", DATE, "note"));
    }


    @Test
    void refusalWithoutAReasonIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new CapabilityReport(
                Capability.PARTY_LIST, Support.UNSUPPORTED, "2.9.6", DATE, "  "));
    }


    @Test
    void declaringTheSameCapabilityTwiceIsRefused() {
        Capabilities.Builder bld = Capabilities.of("test-target")
                .supported(Capability.PARTY_LIST, "2.9.6", DATE);

        assertThrows(IllegalStateException.class,
                () -> bld.supported(Capability.PARTY_LIST, "2.10.2", DATE));
    }


    @Test
    void requireThrowsOnlyOnADeclaredRefusal() {
        Capabilities caps = Capabilities.of("test-target")
                .supported(Capability.PARTY_LIST, "2.9.6", DATE)
                .unsupported(Capability.INTERFACE_FILTER, "2.9.6", DATE, "not implemented")
                .unmeasuredRest("nobody has looked")
                .build();

        caps.require(Capability.PARTY_LIST);
        caps.require(Capability.COMMAND_SUBMIT);

        UnsupportedCapability ex = assertThrows(UnsupportedCapability.class,
                () -> caps.require(Capability.INTERFACE_FILTER));
        assertEquals(Capability.INTERFACE_FILTER, ex.getCapability());
        assertEquals("test-target", ex.getIdTarget());
        assertTrue(ex.getMessage().contains("not implemented"),
                "the declared reason must reach the caller");
    }


    @Test
    void capabilityIdsAreUniqueAndStable() {
        Set<String> setId = new HashSet<>();
        for (Capability capability : Capability.values()) {
            assertTrue(setId.add(capability.strId()),
                    "duplicate capability id: " + capability.strId());
        }
    }

}
