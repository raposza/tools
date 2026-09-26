// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.raposza.api.LedgerException;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * What the decoder can be asserted on without an archive.
 *
 * The decode itself needs real bytes from a participant and is covered by the
 * live suite; committing a .dalf here would commit build output, and a package
 * id is a content hash so a stale fixture fails as a decode error rather than
 * as the wrong fixture.
 *
 * Author Claude/bentzn
 */
class LfDecoderTest {

    /**
     * The package id IS the SHA-256 of the archive payload, equal to the hash
     * the package service reports, on both Ledger API generations. This is the
     * standard vector for "abc".
     */
    @Test
    void packageIdIsTheSha256OfTheBytes() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                LfDecoder.packageId("abc".getBytes(StandardCharsets.UTF_8)));
    }


    @Test
    void anEmptyArchiveIsRefusedRatherThanDecodedToNothing() {
        LfDecoder decoder = new LfDecoder();

        assertThrows(LedgerException.class, () -> decoder.decode(null));
        assertThrows(LedgerException.class, () -> decoder.decode(new byte[0]));
        assertFalse(decoder.accepts(null));
        assertFalse(decoder.accepts(new byte[0]));
    }

}
