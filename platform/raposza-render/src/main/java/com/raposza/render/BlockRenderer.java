// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.render;

import com.raposza.api.model.DamlValue;

import java.util.List;

/**
 * A value down the page instead of across it.
 *
 * The third rendering, and the reason there are three: LineRenderer has to fit
 * a tree row and is lossy, JsonRenderer is exact and pastes into things that
 * expect the Daml encoding, and this one is for a detail pane - complete, but
 * laid out for reading rather than for parsing.
 *
 * Nothing here elides. A pane that scrolls is a pane; a payload that runs off
 * the right edge is the failure this replaces, and truncating instead of
 * wrapping would trade one for the other.
 *
 * Field names inside a record are padded to a common width so values line up:
 *
 * <pre>
 * {
 *   owner    = party-d10723e5-...
 *   balance  = 10.0000000000
 *   address  = {
 *     street   = "Rua Bulk"
 *     city     = "Setubal"
 *   }
 *   tags     = []
 * }
 * </pre>
 *
 * Author Claude/bentzn
 */
public final class BlockRenderer {

    private static final String STR_INDENT = "  ";

    /**
     * Beyond this a name column stops aligning anything and just pushes every
     * value off the screen, which is the problem this class exists to solve.
     */
    private static final int CNT_PAD_MAX = 20;


    private BlockRenderer() {
    }


    /**
     * @param value the value, may be null
     * @return the whole value, one element per line where it has elements
     */
    public static String block(DamlValue value) {
        StringBuilder buf = new StringBuilder();
        append(buf, value, 0);
        return buf.toString();
    }


    /**
     * Only things WITH parts go down the page. A scalar on its own line
     * surrounded by nothing is not more readable than the same scalar inline.
     *
     * @param value a value
     * @return true when block() will produce more than one line
     */
    static boolean hasParts(DamlValue value) {
        return switch (value) {
            case null -> false;
            case DamlValue.Rec val -> !val.lstField().isEmpty();
            case DamlValue.Lst val -> !val.lstElem().isEmpty();
            case DamlValue.TextMap val -> !val.lstEntry().isEmpty();
            case DamlValue.GenMap val -> !val.lstEntry().isEmpty();
            case DamlValue.Opt val -> hasParts(val.value());
            case DamlValue.Variant val -> hasParts(val.value());
            default -> false;
        };
    }


    private static void append(StringBuilder buf, DamlValue value, int cntDepth) {
        if (!hasParts(value)) {
            buf.append(LineRenderer.line(value));
            return;
        }

        switch (value) {
            case DamlValue.Rec val -> {
                int cntPad = pad(names(val));
                buf.append("{\n");
                for (int idx = 0; idx < val.lstField().size(); idx++) {
                    DamlValue.Rec.Field fld = val.lstField().get(idx);
                    String nameField = fld.nameField() == null || fld.nameField().isBlank()
                            ? "[" + idx + "]"
                            : fld.nameField();
                    indent(buf, cntDepth + 1);
                    buf.append(padTo(nameField, cntPad)).append(" = ");
                    append(buf, fld.value(), cntDepth + 1);
                    buf.append('\n');
                }
                indent(buf, cntDepth);
                buf.append('}');
            }

            case DamlValue.Lst val -> {
                buf.append("[\n");
                for (DamlValue elem : val.lstElem()) {
                    indent(buf, cntDepth + 1);
                    append(buf, elem, cntDepth + 1);
                    buf.append('\n');
                }
                indent(buf, cntDepth);
                buf.append(']');
            }

            case DamlValue.TextMap val -> {
                buf.append("{\n");
                for (DamlValue.TextMap.Entry entry : val.lstEntry()) {
                    indent(buf, cntDepth + 1);
                    buf.append('"').append(entry.strKey()).append("\": ");
                    append(buf, entry.value(), cntDepth + 1);
                    buf.append('\n');
                }
                indent(buf, cntDepth);
                buf.append('}');
            }

            case DamlValue.GenMap val -> {
                buf.append("{\n");
                for (DamlValue.GenMap.Entry entry : val.lstEntry()) {
                    indent(buf, cntDepth + 1);
                    buf.append(LineRenderer.line(entry.key())).append(": ");
                    append(buf, entry.value(), cntDepth + 1);
                    buf.append('\n');
                }
                indent(buf, cntDepth);
                buf.append('}');
            }

            case DamlValue.Opt val -> {
                buf.append("Some ");
                append(buf, val.value(), cntDepth);
            }

            case DamlValue.Variant val -> {
                buf.append(val.nameCtor()).append(' ');
                append(buf, val.value(), cntDepth);
            }

            default -> buf.append(LineRenderer.line(value));
        }
    }


    private static List<String> names(DamlValue.Rec rec) {
        return rec.lstField().stream().map(DamlValue.Rec.Field::nameField).toList();
    }


    private static int pad(List<String> lstName) {
        int cntPad = 0;
        for (String nameField : lstName) {
            if (nameField != null && nameField.length() > cntPad)
                cntPad = nameField.length();
        }
        return Math.min(cntPad, CNT_PAD_MAX);
    }


    private static String padTo(String strText, int cntPad) {
        if (strText.length() >= cntPad)
            return strText;
        return strText + " ".repeat(cntPad - strText.length());
    }


    private static void indent(StringBuilder buf, int cntDepth) {
        for (int cntLoop = 0; cntLoop < cntDepth; cntLoop++) {
            buf.append(STR_INDENT);
        }
    }

}
