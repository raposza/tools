// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The snapshot name escape, which is the only thing standing between a name a
 * developer types and a directory two file systems will accept.
 *
 * Author Claude/bentzn
 */
class SnapshotsNameTest {

    @Test
    void aPlainNameIsItsOwnDirectory() {
        // THE ONES ALREADY ON DISK. Every snapshot taken before the escape
        // existed has a name made of the old allowed set, so the encoding has
        // to be the identity over it or they all disappear from the list.
        assertEquals("my-fixture", Snapshots.strEncode("my-fixture"));
        assertEquals("v1.2_beta", Snapshots.strEncode("v1.2_beta"));
        assertEquals("my-fixture", Snapshots.strDecode("my-fixture"));
    }


    @Test
    void spacesAndPunctuationRoundTrip() {
        String strName = "pet shop, 3 orders (100%)";
        String strDir = Snapshots.strEncode(strName);

        assertFalse(strDir.contains(" "), strDir);
        assertEquals(strName, Snapshots.strDecode(strDir));
    }


    @Test
    void nonAsciiRoundTrips() {
        String strName = "kunde \u00e6\u00f8\u00e5 1";
        assertEquals(strName, Snapshots.strDecode(Snapshots.strEncode(strName)));
    }


    @Test
    void aTrailingDotDoesNotSurviveIntoTheDirectory() {
        // Windows drops it, so `v1.` and `v1` would be one directory and the
        // second save would refuse as a duplicate of a name nobody typed.
        String strDir = Snapshots.strEncode("v1.");
        assertFalse(strDir.endsWith("."), strDir);
        assertEquals("v1.", Snapshots.strDecode(strDir));
    }


    @Test
    void aDeviceNameIsEscapedRatherThanRefused() {
        String strDir = Snapshots.strEncode("con");
        assertFalse("con".equalsIgnoreCase(strDir), strDir);
        assertEquals("con", Snapshots.strDecode(strDir));
    }


    @Test
    void aDirectoryNobodyEncodedComesBackUnchanged() {
        assertEquals("50% done", Snapshots.strDecode("50% done"));
        assertEquals("%ZZ", Snapshots.strDecode("%ZZ"));
    }


    @Test
    void whatANameMayNotBe() {
        assertThrows(IllegalArgumentException.class, () -> Snapshots.requireName(null));
        assertThrows(IllegalArgumentException.class, () -> Snapshots.requireName(""));
        assertThrows(IllegalArgumentException.class, () -> Snapshots.requireName("a/b"));
        assertThrows(IllegalArgumentException.class, () -> Snapshots.requireName("a\\b"));
        assertThrows(IllegalArgumentException.class, () -> Snapshots.requireName("a:b"));
        assertThrows(IllegalArgumentException.class, () -> Snapshots.requireName(" a"));
        assertThrows(IllegalArgumentException.class, () -> Snapshots.requireName("a "));
        assertThrows(IllegalArgumentException.class, () -> Snapshots.requireName(".."));
        assertThrows(IllegalArgumentException.class,
                () -> Snapshots.requireName("x".repeat(Snapshots.N_NAME_MAX + 1)));
    }


    @Test
    void aNameWithSpacesIsAccepted() {
        Snapshots.requireName("pet shop, 3 orders");
        Snapshots.requireName("100% full");
    }
}
