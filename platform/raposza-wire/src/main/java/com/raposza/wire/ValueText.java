// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.wire;

import com.raposza.api.model.DamlValue;

/**
 * Flattens a value to searchable text.
 *
 * ContractQuery.strFilter is specified against the RENDERED payload. There is
 * no renderer yet - raposza-render is empty until later in P1 - so this
 * matches against the raw text of field values and labels instead. That is a
 * narrower thing than the specification asks for and is recorded here rather
 * than quietly ignored: when the renderer lands, the filter should move onto
 * its output so that what the operator sees is what the operator searches.
 *
 * Author Claude/bentzn
 */
public final class ValueText {

    private ValueText() {
    }


    /**
     * @param value the value, may be null
     * @param strNeedle text to look for, case-insensitive; empty matches
     * @return true when the value carries the text anywhere inside it
     */
    public static boolean contains(DamlValue value, String strNeedle) {
        if (strNeedle == null || strNeedle.isEmpty())
            return true;
        return flatten(value).toLowerCase().contains(strNeedle.toLowerCase());
    }


    /**
     * @param value the value, may be null
     * @return every scalar inside the value, space separated
     */
    public static String flatten(DamlValue value) {
        StringBuilder buf = new StringBuilder();
        append(buf, value);
        return buf.toString();
    }


    private static void append(StringBuilder buf, DamlValue value) {
        if (value == null)
            return;

        switch (value) {
            case DamlValue.Unit ignored -> buf.append("{} ");
            case DamlValue.Bool val -> buf.append(val.flag()).append(' ');
            case DamlValue.Int64 val -> buf.append(val.num()).append(' ');
            case DamlValue.Decimal val -> buf.append(val.num().toPlainString()).append(' ');
            case DamlValue.Text val -> buf.append(val.str()).append(' ');
            case DamlValue.TimeVal val -> buf.append(val.inst()).append(' ');
            case DamlValue.DateVal val -> buf.append(val.date()).append(' ');
            case DamlValue.Party val -> buf.append(val.idParty()).append(' ');
            case DamlValue.ContractRef val -> buf.append(val.idContract()).append(' ');

            case DamlValue.Rec val -> {
                for (DamlValue.Rec.Field fld : val.lstField()) {
                    buf.append(fld.nameField()).append(' ');
                    append(buf, fld.value());
                }
            }

            case DamlValue.Variant val -> {
                buf.append(val.nameCtor()).append(' ');
                append(buf, val.value());
            }

            case DamlValue.EnumVal val -> buf.append(val.nameCtor()).append(' ');

            case DamlValue.Lst val -> {
                for (DamlValue elem : val.lstElem()) {
                    append(buf, elem);
                }
            }

            case DamlValue.Opt val -> append(buf, val.value());

            case DamlValue.TextMap val -> {
                for (DamlValue.TextMap.Entry entry : val.lstEntry()) {
                    buf.append(entry.strKey()).append(' ');
                    append(buf, entry.value());
                }
            }

            case DamlValue.GenMap val -> {
                for (DamlValue.GenMap.Entry entry : val.lstEntry()) {
                    append(buf, entry.key());
                    append(buf, entry.value());
                }
            }
        }
    }

}
