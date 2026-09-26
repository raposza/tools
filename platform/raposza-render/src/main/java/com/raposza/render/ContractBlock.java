// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.render;

import com.raposza.api.model.Contract;
import com.raposza.api.model.DamlValue;

import java.util.List;

/**
 * A contract laid out for reading rather than for parsing.
 *
 * <h2>Why not the JSON</h2>
 *
 * {@link JsonRenderer} is exact and pastes back into things that expect the
 * Daml encoding, and that is what it is for. In a detail pane it spends four
 * of every ten characters on punctuation the reader has to look past - braces,
 * quotes, commas - to reach a party id that is the only thing on the line worth
 * seeing. This form drops all of it: one `name: value` per line, nesting by
 * indent, and nothing quoted.
 *
 * <h2>Nothing is elided, and the values align on a FIXED column</h2>
 *
 * Every field the ledger reported appears, in the order the JSON form uses, so
 * one contract does not read two ways depending on which pane opened it.
 *
 * The values start at {@link #CNT_COLUMN}, counted from the left margin rather
 * than from the field's own indent, so a nested field lines up with the one
 * above it instead of starting a second ragged column. The width is a CONSTANT
 * and is not measured from the record: a computed one moves every value sideways
 * as soon as one long name arrives, so the same template renders differently on
 * two contracts and the eye loses the column it was following. A name that
 * outgrows the column takes a single space instead - it pushes its own line
 * right and leaves every other line where it was.
 *
 * Values are written exactly as the ledger sent them - a Numeric keeps its
 * trailing zeros - because the pane's identifiers are copied out of it, and a
 * trimmed value is one that does not paste back.
 *
 * Author Claude/bentzn
 */
public final class ContractBlock {

    /** What is printed where the ledger reported nothing at all. */
    public static final String STR_NONE = "(none)";

    /** What is printed for a container the ledger reported as empty. */
    public static final String STR_EMPTY = "(empty)";

    /**
     * Where a value starts, counted from the left margin.
     *
     * 18 is a DECISION, not a measurement of the current fixture. It fitted
     * every payload field of the fixture it was set against; the Aviation
     * fixture's role fields - `maintenanceControl`, `releaseAuthority`,
     * `estimatedHours` - outgrow it under `payload` and take the single
     * space, which is the rule above and leaves every other line in place.
     */
    public static final int CNT_COLUMN = 18;

    private static final String STR_INDENT = "  ";


    private ContractBlock() {
    }


    /**
     * @param contract the contract
     * @return the whole contract, one field per line
     */
    public static String block(Contract contract) {
        StringBuilder buf = new StringBuilder();
        line(buf, 1, "contractId", strOr(contract.idContract()));
        line(buf, 1, "eventId", strOr(contract.idEvent()));
        line(buf, 1, "templateId",
                contract.idTemplate() == null ? STR_NONE : contract.idTemplate().toString());
        named(buf, 1, "payload", contract.payload());
        buf.append('\n');
        texts(buf, 1, "signatories", contract.lstSignatory());
        buf.append('\n');
        texts(buf, 1, "observers", contract.lstObserver());
        buf.append('\n');
        named(buf, 1, "key", contract.key().orElse(null));
        line(buf, 1, "active", Boolean.toString(contract.isActive()));
        return buf.toString();
    }


    /**
     * @param value a value
     * @return it as it appears after a name, or null when it needs its own
     *         lines
     */
    static String strInline(DamlValue value) {
        if (value == null)
            return STR_NONE;

        return switch (value) {
            case DamlValue.Unit ignored -> "()";
            case DamlValue.Bool val -> Boolean.toString(val.flag());
            case DamlValue.Int64 val -> Long.toString(val.num());
            case DamlValue.Decimal val -> val.num().toPlainString();
            // UNQUOTED. The pane is read, not parsed, and a quote around every
            // text turns a payload into something to look past.
            case DamlValue.Text val -> val.str();
            case DamlValue.TimeVal val -> val.inst().toString();
            case DamlValue.DateVal val -> val.date().toString();
            case DamlValue.Party val -> val.idParty();
            case DamlValue.ContractRef val -> val.idContract();
            case DamlValue.EnumVal val -> val.nameCtor();
            // None is nothing; Some is the bare value, which is the encoding
            // JsonRenderer uses and the one an operator has already read.
            case DamlValue.Opt val -> val.value() == null ? STR_NONE : strInline(val.value());
            case DamlValue.Rec val -> val.lstField().isEmpty() ? STR_EMPTY : null;
            case DamlValue.Lst val -> val.lstElem().isEmpty() ? STR_EMPTY : null;
            case DamlValue.TextMap val -> val.lstEntry().isEmpty() ? STR_EMPTY : null;
            case DamlValue.GenMap val -> val.lstEntry().isEmpty() ? STR_EMPTY : null;
            case DamlValue.Variant val -> strInline(val.value()) == null
                    ? null : val.nameCtor() + ' ' + strInline(val.value());
        };
    }


    /**
     * One named value, on its own line when it fits and down the page when it
     * does not.
     *
     * @param buf where it goes
     * @param cntDepth how far in
     * @param nameField the name
     * @param value the value, may be null
     */
    private static void named(StringBuilder buf, int cntDepth, String nameField,
            DamlValue value) {
        String strInline = strInline(value);
        if (strInline != null) {
            line(buf, cntDepth, nameField, strInline);
            return;
        }

        line(buf, cntDepth, nameField, "");
        parts(buf, cntDepth + 1, value);
    }


    /**
     * @param buf where it goes
     * @param cntDepth how far in
     * @param value a value {@link #strInline} would not take
     */
    private static void parts(StringBuilder buf, int cntDepth, DamlValue value) {
        switch (value) {
            case DamlValue.Rec val -> {
                for (int idx = 0; idx < val.lstField().size(); idx++) {
                    DamlValue.Rec.Field fld = val.lstField().get(idx);
                    named(buf, cntDepth, nameOr(fld.nameField(), idx), fld.value());
                }
            }

            case DamlValue.Lst val -> {
                for (int idx = 0; idx < val.lstElem().size(); idx++) {
                    element(buf, cntDepth, idx, val.lstElem().get(idx));
                }
            }

            case DamlValue.TextMap val -> {
                for (DamlValue.TextMap.Entry entry : val.lstEntry()) {
                    named(buf, cntDepth, entry.strKey(), entry.value());
                }
            }

            // A key is a value rather than a name, so the pair is written as a
            // pair. Flattening it would invent a name the ledger never sent.
            case DamlValue.GenMap val -> {
                for (int idx = 0; idx < val.lstEntry().size(); idx++) {
                    DamlValue.GenMap.Entry entry = val.lstEntry().get(idx);
                    line(buf, cntDepth, "[" + idx + "]", "");
                    named(buf, cntDepth + 1, "key", entry.key());
                    named(buf, cntDepth + 1, "value", entry.value());
                }
            }

            case DamlValue.Variant val -> {
                line(buf, cntDepth, val.nameCtor(), "");
                parts(buf, cntDepth + 1, val.value());
            }

            default -> line(buf, cntDepth, "", strInline(value));
        }
    }


    /**
     * A list element has no name, so a scalar is written bare - which is what
     * a list of parties should look like - and only a nested value earns an
     * index to hang under.
     *
     * @param buf where it goes
     * @param cntDepth how far in
     * @param idx which element
     * @param value the element
     */
    private static void element(StringBuilder buf, int cntDepth, int idx, DamlValue value) {
        String strInline = strInline(value);
        if (strInline != null) {
            indent(buf, cntDepth);
            buf.append(strInline).append('\n');
            return;
        }

        line(buf, cntDepth, "[" + idx + "]", "");
        parts(buf, cntDepth + 1, value);
    }


    /**
     * @param buf where it goes
     * @param cntDepth how far in
     * @param nameField the name
     * @param lstText the values, already strings
     */
    private static void texts(StringBuilder buf, int cntDepth, String nameField,
            List<String> lstText) {
        if (lstText == null || lstText.isEmpty()) {
            line(buf, cntDepth, nameField, STR_EMPTY);
            return;
        }

        line(buf, cntDepth, nameField, "");
        for (String strText : lstText) {
            indent(buf, cntDepth + 1);
            buf.append(strText).append('\n');
        }
    }


    private static void line(StringBuilder buf, int cntDepth, String nameField, String strValue) {
        int idxFrom = buf.length();
        indent(buf, cntDepth);
        buf.append(nameField).append(':');
        if (strValue != null && !strValue.isEmpty()) {
            pad(buf, buf.length() - idxFrom);
            buf.append(strValue);
        }
        buf.append('\n');
    }


    /**
     * @param buf where it goes
     * @param cntAt how wide the line is so far
     */
    private static void pad(StringBuilder buf, int cntAt) {
        buf.append(' ');
        for (int idx = cntAt + 1; idx < CNT_COLUMN; idx++) {
            buf.append(' ');
        }
    }


    private static void indent(StringBuilder buf, int cntDepth) {
        for (int idx = 0; idx < cntDepth; idx++) {
            buf.append(STR_INDENT);
        }
    }


    private static String nameOr(String nameField, int idx) {
        return nameField == null || nameField.isBlank() ? "[" + idx + "]" : nameField;
    }


    private static String strOr(String strValue) {
        return strValue == null || strValue.isBlank() ? STR_NONE : strValue;
    }

}
