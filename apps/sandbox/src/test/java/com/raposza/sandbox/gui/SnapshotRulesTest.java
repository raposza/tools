// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

/**
 * The two snapshot decisions, with no display.
 *
 * This is what the extraction bought: before it, the only way to find out that
 * a snapshot taken on 3.5 is refused on 2.10 was to run a window and click a
 * row.
 *
 * Author Claude/bentzn
 */
class SnapshotRulesTest {

    private static Properties props(String strCanton, String strEdition, String strPrefix) {
        Properties out = new Properties();
        out.setProperty(Snapshots.STR_KEY_CANTON, strCanton);
        out.setProperty(Snapshots.STR_KEY_EDITION, strEdition);
        out.setProperty(Snapshots.STR_KEY_PREFIX, strPrefix);
        return out;
    }


    @Test
    void anAgreeingPairIsNotRefused() {
        Properties propsWas = props("3.5.12", "OPEN_SOURCE", "sandbox");
        Properties propsNow = props("3.5.12", "OPEN_SOURCE", "sandbox");

        assertNull(SnapshotRules.strDisagreement(propsWas, propsNow));
    }


    @Test
    void aDifferentCantonIsRefusedAndSaysBothVersions() {
        Properties propsWas = props("3.5.12", "OPEN_SOURCE", "sandbox");
        Properties propsNow = props("2.10.4", "OPEN_SOURCE", "sandbox");

        String strWhy = SnapshotRules.strDisagreement(propsWas, propsNow);
        assertNotNull(strWhy);
        assertTrue(strWhy.contains("3.5.12") && strWhy.contains("2.10.4"), strWhy);
    }


    @Test
    void everyDisagreementIsReportedRatherThanTheFirst() {
        Properties propsWas = props("3.5.12", "OPEN_SOURCE", "sandbox");
        Properties propsNow = props("2.10.4", "ENTERPRISE", "other");

        String strWhy = SnapshotRules.strDisagreement(propsWas, propsNow);
        assertEquals(3, strWhy.strip().split("\n").length, strWhy);
    }


    /**
     * The PQS version deliberately does not gate a restore; see
     * {@link SnapshotRules#strDisagreement}.
     */
    @Test
    void thePqsSettingDoesNotRefuseARestore() {
        Properties propsWas = props("3.5.12", "OPEN_SOURCE", "sandbox");
        propsWas.setProperty(Snapshots.STR_KEY_PQS, "true");
        Properties propsNow = props("3.5.12", "OPEN_SOURCE", "sandbox");
        propsNow.setProperty(Snapshots.STR_KEY_PQS, "false");

        assertNull(SnapshotRules.strDisagreement(propsWas, propsNow));
    }


    @Test
    void anAbsentKeyReadsAsEmptyRatherThanThrowing() {
        Properties propsWas = new Properties();
        Properties propsNow = props("3.5.12", "OPEN_SOURCE", "sandbox");

        assertNotNull(SnapshotRules.strDisagreement(propsWas, propsNow));
    }


    @Test
    void aFreeNameIsAccepted() {
        assertNull(SnapshotRules.strRefusalOfName("after the DARs", List.of("before")));
    }


    @Test
    void aNameAlreadyTakenIsRefusedAndNamed() {
        String strWhy = SnapshotRules.strRefusalOfName("before", List.of("before"));
        assertNotNull(strWhy);
        assertTrue(strWhy.contains("before"), strWhy);
    }


    @Test
    void anEmptyNameIsRefused() {
        assertNotNull(SnapshotRules.strRefusalOfName("", List.of()));
    }


    @Test
    void noExistingNamesIsNotAFailure() {
        assertNull(SnapshotRules.strRefusalOfName("first", null));
    }


    @Test
    void noSelectedInstallationLeavesTheVersionEmptyRatherThanNull() {
        Properties props = SnapshotRules.propsOfForm(null, 30010, 32101);

        assertEquals("", props.getProperty(Snapshots.STR_KEY_CANTON));
        assertEquals("", props.getProperty(Snapshots.STR_KEY_EDITION));
        assertNotNull(props.getProperty(Snapshots.STR_KEY_PREFIX));
    }


    @Test
    void aRunCarriesThePqsSettingAndTheFormKeys() {
        Properties props = SnapshotRules.propsOfRun(null, "true", 30010, 32101);

        assertEquals("true", props.getProperty(Snapshots.STR_KEY_PQS));
        assertEquals("", props.getProperty(Snapshots.STR_KEY_CANTON));
        assertEquals("30010-32101", props.getProperty(Snapshots.STR_KEY_PORTS));
    }


    /** D-852: the block the mediator's sequencer connection was persisted on. */
    @Test
    void aSandboxSnapshotOnAnotherPortBlockIsRefused() {
        String strWhy = SnapshotRules.strDisagreement(
                SnapshotRules.propsOfForm(null, 22010, 32101),
                SnapshotRules.propsOfForm(null, 30010, 32101));

        assertNotNull(strWhy);
        assertTrue(strWhy.contains("port block: 22010-32101 -> 30010-32101"), strWhy);
    }


    /**
     * D-852, operator decision 2026-09-25: a Sandbox snapshot taken before the
     * block was recorded says nothing about it and is refused.
     */
    @Test
    void aSandboxSnapshotWithNoPortBlockIsRefused() {
        Properties propsOld = SnapshotRules.propsOfForm(null, 30010, 32101);
        propsOld.remove(Snapshots.STR_KEY_PORTS);

        String strWhy = SnapshotRules.strDisagreement(propsOld,
                SnapshotRules.propsOfForm(null, 30010, 32101));

        assertNotNull(strWhy);
        assertTrue(strWhy.contains("port block"), strWhy);
    }

}
