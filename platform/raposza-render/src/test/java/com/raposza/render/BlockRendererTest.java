// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.model.DamlValue;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The detail-pane form: complete, and down the page rather than across it.
 *
 * Author Claude/bentzn
 */
class BlockRendererTest {

    private static DamlValue.Rec.Field fld(String nameField, DamlValue value) {
        return new DamlValue.Rec.Field(nameField, value);
    }


    private static DamlValue.Rec rec(DamlValue.Rec.Field... arrField) {
        return new DamlValue.Rec(null, List.of(arrField));
    }


    /**
     * A scalar alone on a line surrounded by nothing is not more readable than
     * the same scalar inline, so only things with parts go down the page.
     */
    @Test
    void thingsWithoutPartsStayOnOneLine() {
        assertEquals("42", BlockRenderer.block(new DamlValue.Int64(42L)));
        assertEquals("\"x\"", BlockRenderer.block(new DamlValue.Text("x")));
        assertEquals("None", BlockRenderer.block(new DamlValue.Opt(null)));
        assertEquals("[]", BlockRenderer.block(new DamlValue.Lst(List.of())));
        assertEquals("{}", BlockRenderer.block(rec()));
        assertEquals("Some \"x\"",
                BlockRenderer.block(new DamlValue.Opt(new DamlValue.Text("x"))));

        assertFalse(BlockRenderer.hasParts(new DamlValue.Int64(1L)));
        assertTrue(BlockRenderer.hasParts(rec(fld("a", new DamlValue.Int64(1L)))));
    }


    @Test
    void aRecordGoesDownThePageWithItsNamesAligned() {
        String strOut = BlockRenderer.block(rec(fld("owner", new DamlValue.Party("alice::1")),
                fld("balance", new DamlValue.Int64(10L))));

        assertEquals("""
                {
                  owner   = alice::1
                  balance = 10
                }""", strOut);
    }


    @Test
    void nestingIndentsAndClosesAtTheRightLevel() {
        DamlValue.Rec address = rec(fld("street", new DamlValue.Text("Rua Bulk")),
                fld("city", new DamlValue.Text("Setubal")));

        String strOut = BlockRenderer.block(rec(fld("owner", new DamlValue.Party("alice::1")),
                fld("address", address)));

        assertEquals("""
                {
                  owner   = alice::1
                  address = {
                    street = "Rua Bulk"
                    city   = "Setubal"
                  }
                }""", strOut);
    }


    @Test
    void listElementsGetOneLineEach() {
        String strOut = BlockRenderer.block(new DamlValue.Lst(
                List.of(new DamlValue.Text("eur"), new DamlValue.Text("retail"))));

        assertEquals("""
                [
                  "eur"
                  "retail"
                ]""", strOut);
    }


    /**
     * An unlabelled record - a read that did not ask for labels - shows the
     * position rather than an invented name, the same rule everywhere else.
     */
    @Test
    void unlabelledFieldsShowTheirPosition() {
        String strOut = BlockRenderer.block(new DamlValue.Rec(null,
                List.of(fld(null, new DamlValue.Int64(1L)), fld("", new DamlValue.Int64(2L)))));

        assertEquals("""
                {
                  [0] = 1
                  [1] = 2
                }""", strOut);
    }


    /**
     * Nothing here elides. The pane scrolls; that is what a pane is for, and a
     * detail view that hid the tail would leave the operator with no form of
     * the value that is complete.
     */
    @Test
    void nothingIsCut() {
        String strLong = "x".repeat(400);
        String strOut = BlockRenderer.block(rec(fld("note", new DamlValue.Text(strLong))));

        assertTrue(strOut.contains(strLong));
        assertFalse(strOut.contains("\u2026"));
    }

}
