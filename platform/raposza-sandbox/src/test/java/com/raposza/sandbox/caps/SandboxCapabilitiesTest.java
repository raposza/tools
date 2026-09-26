// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.caps;

import com.raposza.canton.install.CantonLaunchTable;
import com.raposza.canton.install.Edition;
import com.raposza.canton.install.VersionId;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The map answers for versions nobody has, and says which answers those are.
 *
 * Every expectation here is pinned to a MEASURED row of `CantonLaunchTable`:
 * `sandbox` is absent from 2.x and present from 3.4.4, `--multi-sync` is on the
 * 3.5 line and not on 3.4.11. A test that asserted a major-version rule instead
 * would pass over exactly the mistake the table exists to prevent.
 *
 * <h2>THE TOP OF A LINE IS NOT A LITERAL</h2>
 *
 * Two assertions here read `3.5.11` as "the newest 3.x there is". That was true
 * of a twelve-entry table and false the morning the inventory reached
 * twenty-two - both went red on 3.5.12 with nothing in the capability logic
 * changed, and the failure read as a defect rather than as an install. The
 * expectation is now COMPUTED from the table, by a max this file performs
 * itself rather than by asking the method under test. Installing a newer Canton
 * must not turn this class red.
 *
 * Author Claude/bentzn
 */
class SandboxCapabilitiesTest {

    @Test
    void aMeasuredVersionIsAnsweredFromItsOwnUsageLine() {
        FeatureSupport support = SandboxCapabilities.of(SandboxFeature.SANDBOX_SUBCOMMAND,
                VersionId.parse("3.5.11"), Edition.OPEN_SOURCE);

        assertEquals(FeatureSupport.Support.YES, support.support());
        assertEquals(FeatureSupport.Provenance.MEASURED, support.provenance());
    }


    @Test
    void twoXHasNoSandboxSubcommandAndThatIsMeasuredRatherThanAssumed() {
        FeatureSupport support = SandboxCapabilities.of(SandboxFeature.SANDBOX_SUBCOMMAND,
                VersionId.parse("2.10.4"), Edition.UNKNOWN);

        assertEquals(FeatureSupport.Support.NO, support.support());
        assertEquals(FeatureSupport.Provenance.MEASURED, support.provenance());
    }


    /**
     * @param strLine the minor line, or null for every 3.x row
     * @return the newest version the launch table measured there
     */
    private static VersionId versionTopOf(String strLine) {
        return CantonLaunchTable.lstEntry().stream()
                .map(CantonLaunchTable.Entry::version)
                .filter(version -> strLine == null
                        ? version.major() == 3 : version.isLine(strLine))
                .max(VersionId::compareTo)
                .orElseThrow(() -> new IllegalStateException(
                        "the launch table measured no 3.x binary, so nothing here"
                                + " can extrapolate"));
    }


    /** T-3: the picker is told which versions are second-class, and why. */
    @Test
    void anUnmeasuredVersionSaysWhereItsOptionsComeFrom() {
        VersionId versionTop = versionTopOf("3.5");

        assertNull(SandboxCapabilities.strUnmeasured(versionTop, Edition.OPEN_SOURCE));
        assertNull(SandboxCapabilities.strUnmeasured(null, null));
        String strWhy = SandboxCapabilities.strUnmeasured(VersionId.parse("3.5.99"),
                Edition.OPEN_SOURCE);
        assertNotNull(strWhy);
        assertTrue(strWhy.startsWith("Canton 3.5.99 was never run by Raposza"), strWhy);
        assertTrue(strWhy.endsWith(" " + versionTop), strWhy);
    }


    @Test
    void anUnmeasuredPatchTakesTheAnswerOfItsOwnLine() {
        // 3.5.99 is not installed anywhere. The 3.5 line is measured and
        // carries --multi-sync; 3.4.11 is measured and does not.
        FeatureSupport support = SandboxCapabilities.of(SandboxFeature.MULTI_SYNC,
                VersionId.parse("3.5.99"), Edition.OPEN_SOURCE);

        assertEquals(FeatureSupport.Support.YES, support.support());
        assertEquals(FeatureSupport.Provenance.EXTRAPOLATED, support.provenance());
        // The newest 3.5 row, whatever it is today - NOT a literal. The
        // bundle-to-Canton skew means the top of this line moves without
        // anything in this module changing.
        assertEquals(versionTopOf("3.5"), SandboxCapabilities.versionNearest(
                VersionId.parse("3.5.99")));

        FeatureSupport supportOlder = SandboxCapabilities.of(SandboxFeature.MULTI_SYNC,
                VersionId.parse("3.4.99"), Edition.UNKNOWN);
        assertEquals(FeatureSupport.Support.NO, supportOlder.support());
        assertEquals(FeatureSupport.Provenance.EXTRAPOLATED, supportOlder.provenance());
    }


    @Test
    void aLineNobodyHasFallsBackToTheMajorRatherThanToNothing() {
        // Nothing on line 3.6 is measured, so the nearest at or below it is
        // the newest 3.x there is - computed, for the reason the class note
        // gives.
        VersionId versionNear = SandboxCapabilities.versionNearest(VersionId.parse("3.6.0"));
        assertEquals(versionTopOf(null), versionNear);
        assertEquals(3, versionNear.major());

        FeatureSupport support = SandboxCapabilities.of(SandboxFeature.SANDBOX_SUBCOMMAND,
                VersionId.parse("3.6.0"), Edition.OPEN_SOURCE);
        assertEquals(FeatureSupport.Support.YES, support.support());
        assertEquals(FeatureSupport.Provenance.EXTRAPOLATED, support.provenance());
    }


    @Test
    void aVersionOlderThanEverythingMeasuredTakesTheNearestAbove() {
        // 3.0.0 predates every measured 3.x, and `sandbox` arrives at 3.4.4.
        // The extrapolation is therefore WRONG about 3.0, and it says
        // so by being EXTRAPOLATED rather than MEASURED. Answering nothing at
        // all would leave a window with no layout to choose.
        FeatureSupport support = SandboxCapabilities.of(SandboxFeature.SANDBOX_SUBCOMMAND,
                VersionId.parse("3.0.0"), Edition.UNKNOWN);

        assertFalse(support.isMeasured());
        assertEquals(VersionId.parse("3.4.4"), SandboxCapabilities.versionNearest(
                VersionId.parse("3.0.0")));
    }


    @Test
    void aMajorNobodyHasMeasuredIsUnknownAndStillExposes() {
        FeatureSupport support = SandboxCapabilities.of(SandboxFeature.SANDBOX_SUBCOMMAND,
                VersionId.parse("4.0.0"), Edition.UNKNOWN);

        assertEquals(FeatureSupport.Support.UNKNOWN, support.support());
        assertEquals(FeatureSupport.Provenance.NONE, support.provenance());
        // UNKNOWN IS NOT NO. A surface still offers the control.
        assertTrue(support.isExposed());
        assertNull(SandboxCapabilities.versionNearest(VersionId.parse("4.0.0")));
    }


    @Test
    void theJsonApiIsAProcessOnTwoXAndNotOnThreeX() {
        assertTrue(SandboxCapabilities.isYes(SandboxFeature.JSON_API_PROCESS,
                VersionId.parse("2.10.4"), Edition.UNKNOWN));

        FeatureSupport support = SandboxCapabilities.of(SandboxFeature.JSON_API_PROCESS,
                VersionId.parse("3.5.11"), Edition.OPEN_SOURCE);
        assertEquals(FeatureSupport.Support.NO, support.support());
        // MEASURED, not extrapolated: this is a fact about the stack this
        // application runs, and it does not depend on the patch level.
        assertEquals(FeatureSupport.Provenance.MEASURED, support.provenance());
        assertFalse(support.isExposed());
    }


    @Test
    void everyFeatureIsAnsweredAndANullVersionIsUnknownThroughout() {
        Map<SandboxFeature, FeatureSupport> mapSupport = SandboxCapabilities.mapOf(
                VersionId.parse("3.5.11"), Edition.OPEN_SOURCE);
        assertEquals(SandboxFeature.values().length, mapSupport.size());
        for (SandboxFeature feature : SandboxFeature.values()) {
            assertNotNull(mapSupport.get(feature), feature.name());
        }

        for (SandboxFeature feature : SandboxFeature.values()) {
            FeatureSupport support = SandboxCapabilities.of(feature, null, null);
            assertEquals(FeatureSupport.Support.UNKNOWN, support.support(), feature.name());
            assertTrue(support.isExposed(), feature.name());
        }
    }
}
