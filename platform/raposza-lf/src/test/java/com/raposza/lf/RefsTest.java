// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.raposza.api.model.DataId;

import org.junit.jupiter.api.Test;

/**
 * Identifier mapping, including the dotted entity name that a variant
 * constructor produces.
 *
 * Author Claude/bentzn
 */
class RefsTest {

    @Test
    void referenceBecomesDataId() {
        assertEquals(new DataId("abc123", "Main", "Account"),
                Refs.dataId(Lf.ref("abc123", "Main", "Account")));
    }


    @Test
    void aDottedModuleNameSurvives() {
        assertEquals(new DataId("abc123", "DA.Internal.Template", "Archive"),
                Refs.dataId(Lf.ref("abc123", "DA.Internal.Template", "Archive")));
    }


    /**
     * A variant constructor carrying fields becomes its own synthetic record,
     * named Main:Shape.Circle. Nothing may split an entity name on a dot.
     */
    @Test
    void aSyntheticVariantRecordKeepsItsDot() {
        DataId idData = Refs.dataId(Lf.ref("abc123", "Main", "Shape.Circle"));
        assertEquals("Shape.Circle", idData.nameEntity());
        assertEquals("Main:Shape.Circle", idData.shortName());
    }

}
