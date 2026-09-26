// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

/**
 * The glanceable form. Lossy by design, and every loss is marked.
 *
 * Author Claude/bentzn
 */
class LineRendererTest {

    private static DamlValue.Rec.Field fld(String nameField, DamlValue value) {
        return new DamlValue.Rec.Field(nameField, value);
    }


    /**
     * Unquoted, an empty string and an absent value are the same three
     * characters of nothing, and an empty string is a thing an operator has to
     * be able to see.
     */
    @Test
    void textIsQuoted() {
        assertEquals("\"\"", LineRenderer.line(new DamlValue.Text("")));
        assertEquals("\"retail\"", LineRenderer.line(new DamlValue.Text("retail")));
    }


    /**
     * The JSON form quotes these to survive a parser's doubles. There is no
     * parser here, and quotes on a balance make it look like text.
     */
    @Test
    void numbersAreBareAndKeepTheirTail() {
        assertEquals("42", LineRenderer.line(new DamlValue.Int64(42L)));
        assertEquals("1250.0000",
                LineRenderer.line(new DamlValue.Decimal(new BigDecimal("1250.0000"))));
    }


    @Test
    void optionalReadsAsNoneOrSome() {
        assertEquals("None", LineRenderer.line(new DamlValue.Opt(null)));
        assertEquals("Some \"x\"",
                LineRenderer.line(new DamlValue.Opt(new DamlValue.Text("x"))));
    }


    @Test
    void aRecordCarriesItsFieldNames() {
        DamlValue.Rec rec = new DamlValue.Rec(null,
                List.of(fld("owner", new DamlValue.Party("alice::1")),
                        fld("balance", new DamlValue.Int64(10L))));

        assertEquals("{owner = alice::1, balance = 10}", LineRenderer.line(rec));
    }


    /**
     * A read that did not ask for labels returns positional fields. Rendering
     * them positionally is honest; inventing names is not.
     */
    @Test
    void anUnlabelledRecordRendersPositionally() {
        DamlValue.Rec rec = new DamlValue.Rec(null,
                List.of(fld(null, new DamlValue.Int64(1L)), fld("", new DamlValue.Int64(2L))));

        assertEquals("{1, 2}", LineRenderer.line(rec));
    }


    @Test
    void listsAndEnumsAndVariants() {
        assertEquals("[\"eur\", \"retail\"]", LineRenderer.line(new DamlValue.Lst(
                List.of(new DamlValue.Text("eur"), new DamlValue.Text("retail")))));
        assertEquals("Gold", LineRenderer.line(new DamlValue.EnumVal(null, "Gold")));
        assertEquals("Left 1", LineRenderer.line(
                new DamlValue.Variant(null, "Left", new DamlValue.Int64(1L))));
    }


    @Test
    void whatIsCutIsMarked() {
        String strLong = LineRenderer.line(new DamlValue.Text("0123456789"), 6);

        assertTrue(strLong.endsWith("\u2026"), strLong);
        assertEquals(7, strLong.length());
        assertEquals("abc", LineRenderer.clip("abc", 90));
        assertEquals("", LineRenderer.clip(null, 5));
    }


    @Test
    void theKindNamesTheEntityWhereTheLedgerGaveOne() {
        DamlValue.Rec rec = new DamlValue.Rec(new DataId("pkg", "Main", "Address"), List.of());

        assertEquals("Address", LineRenderer.kind(rec));
        assertEquals("Record", LineRenderer.kind(new DamlValue.Rec(null, List.of())));
        assertEquals("Int64", LineRenderer.kind(new DamlValue.Int64(1L)));
        assertEquals("List[2]", LineRenderer.kind(new DamlValue.Lst(
                List.of(new DamlValue.Int64(1L), new DamlValue.Int64(2L)))));
        assertEquals("", LineRenderer.kind(null));
    }

}
