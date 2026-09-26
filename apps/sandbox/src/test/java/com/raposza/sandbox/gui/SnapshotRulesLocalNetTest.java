// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.canton.install.Edition;
import com.raposza.canton.install.VersionId;

import org.junit.jupiter.api.Test;

import java.util.Properties;

/**
 * What a LocalNetND snapshot records, and when it may be started on.
 *
 * THE KEY IS THREE THINGS THERE, not one. A Sandbox cluster was written by one
 * Canton; a LocalNetND cluster was written by a Splice BUNDLE and a Canton JAR,
 * and Canton's topology state names the ports it was founded on - `Founding`'s
 * type comment, and `exploration_2026-09-21_post_close.md` C2. A restore that
 * checked only the Canton would hand a participant a store another generation
 * wrote, which it discovers some way into a start as a failure about topology.
 *
 * Author Claude/bentzn
 */
class SnapshotRulesLocalNetTest {

    private static final VersionId VERSION = VersionId.parse("3.5.13");

    private static final VersionId VERSION_OTHER = VersionId.parse("3.5.14");


    @Test
    void theSpliceBundleAndThePortsAreRecorded() {
        Properties props = SnapshotRules.propsOfLocalNetForm("0.8.1", VERSION,
                Edition.OPEN_SOURCE, 30010, 32101);

        assertEquals("0.8.1", props.getProperty(Snapshots.STR_KEY_SPLICE));
        assertEquals("30010-32101", props.getProperty(Snapshots.STR_KEY_PORTS));
        assertEquals("3.5.13", props.getProperty(Snapshots.STR_KEY_CANTON));
        assertEquals("OPEN_SOURCE", props.getProperty(Snapshots.STR_KEY_EDITION));
    }


    /**
     * A-35 (b): `propsOfRun` read `form.selected()`, which is ALWAYS null on
     * this topology, so the founding snapshot recorded an empty Canton version.
     * Harmless while the key carried the discriminators; not harmless the
     * moment `strDisagreement` compares them.
     */
    @Test
    void theCantonVersionIsNotEmptyOnThisTopology() {
        Properties props = SnapshotRules.propsOfLocalNetRun("0.8.1", VERSION,
                Edition.OPEN_SOURCE, 30010, 32101);

        assertTrue(!props.getProperty(Snapshots.STR_KEY_CANTON).isEmpty());
    }


    @Test
    void anAgreeingPairIsNotRefused() {
        assertNull(SnapshotRules.strDisagreement(
                SnapshotRules.propsOfLocalNetForm("0.8.1", VERSION, Edition.OPEN_SOURCE,
                        30010, 32101),
                SnapshotRules.propsOfLocalNetForm("0.8.1", VERSION, Edition.OPEN_SOURCE,
                        30010, 32101)));
    }


    @Test
    void anotherSpliceBundleIsRefusedAndSaysBoth() {
        String strWhy = SnapshotRules.strDisagreement(
                SnapshotRules.propsOfLocalNetForm("0.7.4", VERSION, Edition.OPEN_SOURCE,
                        30010, 32101),
                SnapshotRules.propsOfLocalNetForm("0.8.1", VERSION, Edition.OPEN_SOURCE,
                        30010, 32101));

        assertNotNull(strWhy);
        assertTrue(strWhy.contains("0.7.4") && strWhy.contains("0.8.1"), strWhy);
    }


    @Test
    void anotherCantonOrAnotherPortBlockIsRefused() {
        assertNotNull(SnapshotRules.strDisagreement(
                SnapshotRules.propsOfLocalNetForm("0.8.1", VERSION, Edition.OPEN_SOURCE,
                        30010, 32101),
                SnapshotRules.propsOfLocalNetForm("0.8.1", VERSION_OTHER, Edition.OPEN_SOURCE,
                        30010, 32101)));
        assertNotNull(SnapshotRules.strDisagreement(
                SnapshotRules.propsOfLocalNetForm("0.8.1", VERSION, Edition.OPEN_SOURCE,
                        30010, 32101),
                SnapshotRules.propsOfLocalNetForm("0.8.1", VERSION, Edition.OPEN_SOURCE,
                        23010, 32101)));
    }


    /**
     * A SANDBOX SNAPSHOT DECLARES NO SPLICE BUNDLE, and a Sandbox pair agrees
     * on that row. It DOES declare the port block since D-852.
     *
     * AND A SANDBOX AGAINST A LOCALNETND ONE ON THE SAME BLOCK DISAGREES ON
     * THREE: the Canton, the edition and the Splice bundle.
     */
    @Test
    void aSandboxPairIsUnaffectedByTheSpliceRow() {
        Properties props = SnapshotRules.propsOfForm(null, 30010, 32101);

        assertNull(SnapshotRules.strDisagreement(props,
                SnapshotRules.propsOfForm(null, 30010, 32101)));
        assertEquals(3, SnapshotRules.strDisagreement(props,
                SnapshotRules.propsOfLocalNetForm("0.8.1", VERSION, Edition.OPEN_SOURCE,
                        30010, 32101)).strip().split("\n").length);
    }


    /**
     * THE ROOTS ARE SIBLINGS AND CANNOT COLLIDE. A per-version root always
     * begins with a version and carries a `-`; `localnet` and `founding` are
     * names no version key can take.
     */
    @Test
    void theLocalNetRootIsBesideThePerVersionOnes() {
        String strKey = "splice0.8.1_3.5.13-open_source_p22010-32101";

        String strRoot = Snapshots.ofLocalNet(strKey).dirRoot().toString();

        assertTrue(strRoot.contains(Snapshots.STR_DIR_LOCALNET), strRoot);
        assertTrue(!VersionKey.strOf(VERSION, Edition.OPEN_SOURCE)
                .equals(Snapshots.STR_DIR_LOCALNET));
    }

}
