// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.ConnectException;

import org.junit.jupiter.api.Test;

/**
 * "Install FAILED: null", the WinVM, 2026-10-30 - never again.
 *
 * Author Claude/bentzn
 */
class FailuresTest {

    @Test
    void aMessageIsTheAnswer() {
        assertEquals("refused", Failures.strWhy(new IOException("refused")));
    }


    @Test
    void noMessageIsTheClassAndTheCause() {
        assertEquals("java.net.ConnectException", Failures.strWhy(new ConnectException()));
        assertEquals("java.io.IOException: java.net.ConnectException",
                Failures.strWhy(new IOException(null, new ConnectException())));
        assertEquals("java.io.IOException: inner",
                Failures.strWhy(new IOException(null, new IOException("inner"))));
    }
}
