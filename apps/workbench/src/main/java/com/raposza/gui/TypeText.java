// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.model.DamlType;
import com.raposza.api.model.PrimKind;

import java.util.ArrayList;
import java.util.List;

/**
 * A Daml type as one line of text.
 *
 * <h2>Short by design</h2>
 *
 * A field list is read down the left edge and the types are there to be
 * scanned, not parsed. So a {@link DamlType.Ref} shows its ENTITY name and
 * drops the package id and the module - the package id is 64 hex characters
 * and would push every type off the pane, and a template's own fields refer
 * overwhelmingly to types in its own module.
 *
 * The full name is still one click away in the detail of the referenced
 * template, so nothing is lost - only moved out of a column where it does not
 * fit.
 *
 * Author Claude/bentzn
 */
public final class TypeText {

    private TypeText() {
    }


    /**
     * @param type the type, may be null
     * @return it as one line; "?" when it is null or of a kind not known here
     */
    public static String strOf(DamlType type) {
        if (type == null)
            return "?";

        return switch (type) {
            case DamlType.Prim val -> strPrim(val.kind());

            case DamlType.Numeric val -> "Numeric " + val.cntScale();

            case DamlType.ListOf val -> "[" + strOf(val.typeElem()) + "]";

            case DamlType.OptionalOf val -> "Optional " + strOf(val.typeElem());

            case DamlType.TextMapOf val -> "TextMap " + strOf(val.typeValue());

            case DamlType.GenMapOf val -> "Map " + strOf(val.typeKey()) + " "
                    + strOf(val.typeValue());

            case DamlType.Ref val -> val.idData().nameEntity();

            case DamlType.Var val -> val.nameVar();

            case DamlType.App val -> strApp(val);

            default -> "?";
        };
    }


    /**
     * The name a developer WRITES, not the Daml-LF enum constant.
     *
     * A pane that answers "what do I have to supply" is read against a template
     * source file, and `party` is not a type anybody types. Two of these are not
     * a case change: LF's TIMESTAMP is Daml's Time, and CONTRACT_ID is
     * ContractId - which is why this is a table rather than a call to a
     * capitalising helper.
     *
     * @param kind the primitive
     * @return its Daml spelling
     */
    private static String strPrim(PrimKind kind) {
        return switch (kind) {
            case UNIT -> "Unit";
            case BOOL -> "Bool";
            case INT64 -> "Int64";
            case TEXT -> "Text";
            case TIMESTAMP -> "Time";
            case DATE -> "Date";
            case PARTY -> "Party";
            case CONTRACT_ID -> "ContractId";
        };
    }


    /**
     * @param val a type applied to arguments
     * @return `Fun a b`
     */
    private static String strApp(DamlType.App val) {
        List<String> lstArg = new ArrayList<>();
        for (DamlType typeArg : val.lstArg()) {
            lstArg.add(strOf(typeArg));
        }
        return strOf(val.typeFun()) + " " + String.join(" ", lstArg);
    }

}
