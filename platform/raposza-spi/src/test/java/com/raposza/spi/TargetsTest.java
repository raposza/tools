// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.spi;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * raposza-spi registers no target of its own, which is the case worth pinning:
 * an application that forgot to put a canton-target-* module on its classpath
 * must be told so, not handed a null.
 *
 * Author Claude/bentzn
 */
class TargetsTest {

    @Test
    void noTargetIsRegisteredInTheSpiModuleItself() {
        assertTrue(Targets.all().isEmpty());
    }


    @Test
    void onlyRefusesWhenNothingIsRegistered() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, Targets::only);
        assertTrue(ex.getMessage().contains("canton-target-"),
                "the message must say what is missing from the classpath");
    }

}
