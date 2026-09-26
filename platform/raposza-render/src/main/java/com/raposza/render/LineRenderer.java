// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.render;

import com.raposza.api.model.DamlValue;

/**
 * Renders a value onto ONE line, short enough to sit in a tree row.
 *
 * Not a competitor to JsonRenderer. That one is exact and pastes back into
 * things that expect the Daml JSON encoding; this one is glanceable and lossy,
 * and says so by ending an elided value with an ellipsis. A row that silently
 * dropped the tail of a list would be the worse of the two failures.
 *
 * Conventions that differ from the JSON encoding, on purpose:
 *
 * Text is QUOTED, because "" and a missing value look identical unquoted and
 * an empty string is a thing an operator needs to be able to see. Int64 and
 * Numeric are BARE - the JSON encoding quotes them to survive a JSON parser's
 * doubles, and there is no parser here to survive. None renders as None rather
 * than as null, since this is read by a person rather than by a program.
 *
 * Author Claude/bentzn
 */
public final class LineRenderer {

    /** What a tree row can hold before the eye stops reading it. */
    public static final int CNT_LINE_DEFAULT = 90;

    private static final String STR_ELLIPSIS = "\u2026";


    private LineRenderer() {
    }


    /**
     * @param value the value, may be null
     * @return the whole value on one line, however long that is
     */
    public static String line(DamlValue value) {
        StringBuilder buf = new StringBuilder();
        append(buf, value);
        return buf.toString();
    }


    /**
     * @param value the value, may be null
     * @param cntMax maximum characters; anything longer is cut and marked
     * @return the value on one line, at most cntMax characters plus the mark
     */
    public static String line(DamlValue value, int cntMax) {
        return clip(line(value), cntMax);
    }


    /**
     * @param strLine any single line
     * @param cntMax maximum characters
     * @return it, marked when anything was dropped
     */
    public static String clip(String strLine, int cntMax) {
        if (strLine == null)
            return "";
        if (cntMax <= 0 || strLine.length() <= cntMax)
            return strLine;
        return strLine.substring(0, cntMax) + STR_ELLIPSIS;
    }


    /**
     * @param value a value
     * @return a short name for what KIND of value it is, for the type column
     */
    public static String kind(DamlValue value) {
        if (value == null)
            return "";

        return switch (value) {
            case DamlValue.Unit ignored -> "Unit";
            case DamlValue.Bool ignored -> "Bool";
            case DamlValue.Int64 ignored -> "Int64";
            case DamlValue.Decimal ignored -> "Numeric";
            case DamlValue.Text ignored -> "Text";
            case DamlValue.TimeVal ignored -> "Time";
            case DamlValue.DateVal ignored -> "Date";
            case DamlValue.Party ignored -> "Party";
            case DamlValue.ContractRef ignored -> "ContractId";
            case DamlValue.Rec val -> val.idData() == null ? "Record"
                    : val.idData().nameEntity();
            case DamlValue.Variant val -> val.idData() == null ? "Variant"
                    : val.idData().nameEntity();
            case DamlValue.EnumVal val -> val.idData() == null ? "Enum"
                    : val.idData().nameEntity();
            case DamlValue.Lst val -> "List[" + val.lstElem().size() + "]";
            case DamlValue.Opt ignored -> "Optional";
            case DamlValue.TextMap val -> "TextMap[" + val.lstEntry().size() + "]";
            case DamlValue.GenMap val -> "GenMap[" + val.lstEntry().size() + "]";
        };
    }


    private static void append(StringBuilder buf, DamlValue value) {
        if (value == null) {
            buf.append("null");
            return;
        }

        switch (value) {
            case DamlValue.Unit ignored -> buf.append("()");
            case DamlValue.Bool val -> buf.append(val.flag());
            case DamlValue.Int64 val -> buf.append(val.num());
            case DamlValue.Decimal val -> buf.append(val.num().toPlainString());
            case DamlValue.Text val -> buf.append('"').append(val.str()).append('"');
            case DamlValue.TimeVal val -> buf.append(val.inst());
            case DamlValue.DateVal val -> buf.append(val.date());
            case DamlValue.Party val -> buf.append(val.idParty());
            case DamlValue.ContractRef val -> buf.append(val.idContract());

            case DamlValue.Rec val -> {
                buf.append('{');
                for (int idx = 0; idx < val.lstField().size(); idx++) {
                    if (idx > 0)
                        buf.append(", ");
                    DamlValue.Rec.Field fld = val.lstField().get(idx);
                    // An unlabelled record - a read that did not ask for
                    // labels - renders positionally rather than with invented
                    // names, the same rule the JSON renderer follows.
                    if (fld.nameField() != null && !fld.nameField().isBlank())
                        buf.append(fld.nameField()).append(" = ");
                    append(buf, fld.value());
                }
                buf.append('}');
            }

            case DamlValue.Variant val -> {
                buf.append(val.nameCtor()).append(' ');
                append(buf, val.value());
            }

            case DamlValue.EnumVal val -> buf.append(val.nameCtor());

            case DamlValue.Lst val -> {
                buf.append('[');
                for (int idx = 0; idx < val.lstElem().size(); idx++) {
                    if (idx > 0)
                        buf.append(", ");
                    append(buf, val.lstElem().get(idx));
                }
                buf.append(']');
            }

            case DamlValue.Opt val -> {
                if (val.value() == null) {
                    buf.append("None");
                }
                else {
                    buf.append("Some ");
                    append(buf, val.value());
                }
            }

            case DamlValue.TextMap val -> {
                buf.append('{');
                for (int idx = 0; idx < val.lstEntry().size(); idx++) {
                    if (idx > 0)
                        buf.append(", ");
                    DamlValue.TextMap.Entry entry = val.lstEntry().get(idx);
                    buf.append('"').append(entry.strKey()).append("\": ");
                    append(buf, entry.value());
                }
                buf.append('}');
            }

            case DamlValue.GenMap val -> {
                buf.append('{');
                for (int idx = 0; idx < val.lstEntry().size(); idx++) {
                    if (idx > 0)
                        buf.append(", ");
                    DamlValue.GenMap.Entry entry = val.lstEntry().get(idx);
                    append(buf, entry.key());
                    buf.append(": ");
                    append(buf, entry.value());
                }
                buf.append('}');
            }
        }
    }

}
