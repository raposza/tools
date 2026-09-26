// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.canton.install.Edition;
import com.raposza.canton.install.VersionId;

import org.junit.jupiter.api.Test;

/**
 * The founding-snapshot decisions, with no display and no cluster.
 *
 * Two things are decided here and neither needs a window or a running
 * PostgreSQL: WHICH key a cluster belongs to, and WHAT a start does about it.
 * Both were the whole of the risk in the 2026-09-22 increment - a key missing a
 * discriminator restores a cluster another generation wrote, and a branch in the
 * wrong order silently overrides the user's own snapshot selection.
 *
 * Author Claude/bentzn
 */
class FoundingTest {

    private static final VersionId VERSION = VersionId.parse("3.5.14");

    private static final VersionId VERSION_OTHER = VersionId.parse("3.4.11");


    @Test
    void aUserSnapshotWinsOverTheFoundingSnapshot() {
        // THE OPERATOR'S ORDER, 2026-09-22: "unless the user has selected a
        // user snapshot". A founding snapshot that took precedence would
        // silently discard the state a developer built the snapshot to keep.
        assertEquals(Founding.Action.RESTORE_USER, Founding.actionOf("pet shop", true));
        assertEquals(Founding.Action.RESTORE_USER, Founding.actionOf("pet shop", false));
    }


    @Test
    void withNoUserSnapshotTheFoundingOneIsUsedWhenItExists() {
        assertEquals(Founding.Action.RESTORE_FOUNDING, Founding.actionOf(null, true));
    }


    @Test
    void withNeitherItFounds() {
        assertEquals(Founding.Action.FOUND, Founding.actionOf(null, false));
    }


    @Test
    void theSandboxKeyIsTheVersionKeyAndThePortBlock() {
        assertEquals(VersionKey.strOf(VERSION, Edition.OPEN_SOURCE) + "_p30010-32101",
                Founding.strKeyOfSandbox(VERSION, Edition.OPEN_SOURCE, 30010, 32101));
    }


    /** D-852: a founding taken on 22010 is never restored onto 30010. */
    @Test
    void theSandboxKeySeparatesPortBlocks() {
        String strBase = Founding.strKeyOfSandbox(VERSION, Edition.OPEN_SOURCE, 30010, 32101);

        assertNotEquals(strBase,
                Founding.strKeyOfSandbox(VERSION, Edition.OPEN_SOURCE, 22010, 32101));
        assertNotEquals(strBase,
                Founding.strKeyOfSandbox(VERSION, Edition.OPEN_SOURCE, 30010, 32102));
    }


    @Test
    void theSandboxKeySeparatesTheTwoEditionsOfOneVersion() {
        // 3.4.4 shipped under both licences, which is the case VersionKey
        // exists for. A founding snapshot shared between them would be the
        // cross-version restore this application refuses.
        assertNotEquals(Founding.strKeyOfSandbox(VERSION, Edition.OPEN_SOURCE, 30010, 32101),
                Founding.strKeyOfSandbox(VERSION, Edition.ENTERPRISE, 30010, 32101));
    }


    @Test
    void theLocalNetKeySeparatesEveryOneOfItsThreeDiscriminators() {
        String strBase = Founding.strKeyOfLocalNet("0.8.1", VERSION,
                Edition.OPEN_SOURCE, 30010, 32101);

        assertNotEquals(strBase, Founding.strKeyOfLocalNet("0.7.4", VERSION,
                Edition.OPEN_SOURCE, 30010, 32101));
        assertNotEquals(strBase, Founding.strKeyOfLocalNet("0.8.1", VERSION_OTHER,
                Edition.OPEN_SOURCE, 30010, 32101));
        assertNotEquals(strBase, Founding.strKeyOfLocalNet("0.8.1", VERSION,
                Edition.OPEN_SOURCE, 22110, 32101));
        assertNotEquals(strBase, Founding.strKeyOfLocalNet("0.8.1", VERSION,
                Edition.OPEN_SOURCE, 30010, 32201));
    }


    @Test
    void theLocalNetKeyNamesTheBundleAndTheCantonAndThePorts() {
        String strKey = Founding.strKeyOfLocalNet("0.8.1", VERSION,
                Edition.OPEN_SOURCE, 30010, 32101);

        assertTrue(strKey.contains("0.8.1"), strKey);
        assertTrue(strKey.contains("3.5.14"), strKey);
        assertTrue(strKey.contains("30010"), strKey);
        assertTrue(strKey.contains("32101"), strKey);
    }


    @Test
    void theLocalNetKeyRefusesAnAbsentBundle() {
        assertThrows(IllegalArgumentException.class,
                () -> Founding.strKeyOfLocalNet(null, VERSION, Edition.OPEN_SOURCE,
                        30010, 32101));
        assertThrows(IllegalArgumentException.class,
                () -> Founding.strKeyOfLocalNet("", VERSION, Edition.OPEN_SOURCE,
                        30010, 32101));
    }


    @Test
    void theRootCanNeverCollideWithAPerVersionRoot() {
        // WHY THE FOUNDING SNAPSHOT IS DISPLAYED NOWHERE. Its root is a SIBLING
        // of the per-version roots rather than an entry inside one, and this is
        // what makes that safe: a version key always carries a `-` and begins
        // with a version, so no per-version root is ever named `founding`.
        assertNotEquals(Founding.STR_DIR_ROOT, VersionKey.strOf(VERSION, Edition.OPEN_SOURCE));
        assertNotEquals(Founding.STR_DIR_ROOT, VersionKey.strOf(VERSION, Edition.UNKNOWN));
        assertTrue(VersionKey.strOf(VERSION, Edition.OPEN_SOURCE).indexOf('-') > 0);
    }


    @Test
    void theOneEntryNameIsAcceptableToTheStoreItSitsIn() {
        // The founding store is a Snapshots, so its one name has to pass the
        // same rule a typed one does - a device name or a trailing dot would
        // encode to something else and `exists()` would look in the wrong place.
        Snapshots.requireName(Founding.STR_NAME);

        assertEquals(Founding.STR_NAME, Snapshots.strEncode(Founding.STR_NAME));
    }


    @Test
    void aKeyIsEncodedIntoTheDirectoryNameSoNoVersionStringCanEscapeIt() {
        // A Splice version arrives as a string off a directory listing. It has
        // never carried a separator and nothing here promises it never will.
        assertEquals("0.8.1", Snapshots.strEncode("0.8.1"));
        assertTrue(Snapshots.strEncode("0.8/1").indexOf('/') < 0);
    }
}
