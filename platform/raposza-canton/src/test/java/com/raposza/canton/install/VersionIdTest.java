// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Author Claude/bentzn
 */
class VersionIdTest {

    @Test
    void parsesThreeParts() {
        VersionId version = VersionId.parse("3.5.11");
        assertEquals(3, version.major());
        assertEquals(5, version.minor());
        assertEquals(11, version.patch());
        assertEquals("3.5", version.line());
        assertEquals("3.5.11", version.toString());
    }


    @Test
    void acceptsTheBannerLeadingV() {
        assertEquals(VersionId.of(3, 5, 7), VersionId.parse("v3.5.7"));
    }


    @Test
    void rejectsWhatIsNotAVersion() {
        // The DPM cache's generation segment. Discovery walks directories and
        // relies on this to fail rather than on a whitelist.
        assertTrue(VersionId.tryParse("daml3.4").isEmpty());
        assertTrue(VersionId.tryParse("3.4").isEmpty());
        assertTrue(VersionId.tryParse("3.4.1.2").isEmpty());
        assertTrue(VersionId.tryParse("line").isEmpty());
        assertTrue(VersionId.tryParse("3.4.x").isEmpty());
        assertTrue(VersionId.tryParse("").isEmpty());
        assertTrue(VersionId.tryParse(null).isEmpty());
        assertThrows(InstallException.class, () -> VersionId.parse("bin"));
    }


    @Test
    void ordersNumericallyNotAsText() {
        // Both are live compatibility targets and string ordering gets this
        // pair backwards.
        assertTrue(VersionId.parse("2.10.4").compareTo(VersionId.parse("2.9.7")) > 0);

        List<VersionId> lstVersion = new ArrayList<>();
        lstVersion.add(VersionId.parse("2.9.7"));
        lstVersion.add(VersionId.parse("3.5.11"));
        lstVersion.add(VersionId.parse("2.10.4"));
        Collections.sort(lstVersion);
        assertEquals("[2.9.7, 2.10.4, 3.5.11]", lstVersion.toString());
    }


    @Test
    void knowsItsLine() {
        assertTrue(VersionId.parse("3.4.3").isLine("3.4"));
        assertFalse(VersionId.parse("3.4.3").isLine("3.5"));
    }
}
