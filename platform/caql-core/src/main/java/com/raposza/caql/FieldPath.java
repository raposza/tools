// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import com.raposza.api.Substitution_i;
import com.raposza.api.TypeRegistry_i;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataShape;
import com.raposza.api.model.FieldInfo;

import java.util.List;

/**
 * Projects a bound value through record fields. Sec. 6.
 *
 * <h2>Both halves are needed, and they come from different places</h2>
 *
 * The VALUE is walked out of the {@link DamlValue.Rec} that is already in hand.
 * The TYPE cannot be: a field's declared type lives in the {@link DataShape}
 * the registry holds, and {@code DamlValue.Rec} carries field names and values
 * but no types at all. So both are walked in step here, and a projection whose
 * type cannot be resolved fails rather than returning a value with a guessed
 * type - which is precisely the guess sec. 6 gives three examples of going
 * wrong.
 *
 * <h2>Records only, deliberately</h2>
 *
 * A dot into a list, an optional, a variant, a map or a scalar is refused. Each
 * of those would need something the language does not have: an index, a
 * null-or-not decision, a constructor test, a key. Refusing them here keeps the
 * dot a projection rather than the first half of an expression grammar, and the
 * refusal names the type it hit so the operator is not left guessing which
 * field was the wrong one.
 *
 * <h2>Why it is not on Env</h2>
 *
 * {@link Env} is the run's bindings and nothing else - it holds no registry and
 * takes no dependency on one. A projection needs the registry, so it lives
 * beside Env rather than inside it.
 *
 * Author Claude/bentzn
 */
public final class FieldPath {

    private FieldPath() {
    }


    /**
     * @param binding what the name is bound to
     * @param lstField the fields to project through, in order; empty returns
     *                 the binding's own value and type
     * @param registry the run's registry snapshot, for the declared field types
     * @param strRef the reference as written, for the message
     * @param numLine the line using it
     * @param strSource the statement using it
     * @return the projected value and its declared type
     * @throws CaqlException when a field does not exist, or the path runs into
     *         something that is not a record
     */
    public static Substitution_i.Bound project(Binding binding, List<String> lstField,
            TypeRegistry_i registry, String strRef, int numLine, String strSource) {

        DamlValue value = binding.value();
        DamlType type = binding.type();

        for (String nameField : lstField) {
            if (!(value instanceof DamlValue.Rec rec)) {
                throw new CaqlException(numLine, strSource, "'" + strRef + "' reaches '"
                        + nameField + "' through " + describe(value) + ", which has no fields;"
                        + " only records can be projected through");
            }

            value = fieldValue(rec, nameField, strRef, numLine, strSource);
            type = fieldType(type, nameField, registry, strRef, numLine, strSource);
        }

        return new Substitution_i.Bound(value, type);
    }


    private static DamlValue fieldValue(DamlValue.Rec rec, String nameField, String strRef,
            int numLine, String strSource) {

        List<String> lstName = new java.util.ArrayList<>();
        for (DamlValue.Rec.Field fld : rec.lstField()) {
            if (nameField.equals(fld.nameField()))
                return fld.value();
            lstName.add(fld.nameField());
        }

        // A record read back WITHOUT verbose carries no field labels at all, so
        // an empty name list is a different failure from a wrong name and says
        // so. The invariant is that verbose is set on every read; this is what
        // it looks like when something has broken it.
        if (lstName.isEmpty() || lstName.get(0).isEmpty()) {
            throw new CaqlException(numLine, strSource, "'" + strRef + "' cannot resolve '"
                    + nameField + "': the record carries no field labels, so it was read"
                    + " without verbose set");
        }

        throw new CaqlException(numLine, strSource, "'" + strRef + "' has no field '"
                + nameField + "'; it offers " + String.join(", ", lstName));
    }


    /**
     * The declared type of one field, reached through a record type. Shared
     * with the WHERE sieve, which walks a field path against a template's
     * declared fields and then through the registry exactly as a projection
     * does - one rule for what a dot may reach into.
     *
     * @param type the type being reached into; must be a record reference
     * @param nameField the field
     * @param registry the run's registry snapshot
     * @param strRef the path as written, for the message
     * @param numLine the line using it
     * @param strSource the statement using it
     * @return the field's declared type
     * @throws CaqlException when the type is not a record, the registry does
     *         not hold it, or it declares no such field
     */
    static DamlType fieldType(DamlType type, String nameField, TypeRegistry_i registry,
            String strRef, int numLine, String strSource) {

        if (!(type instanceof DamlType.Ref ref)) {
            throw new CaqlException(numLine, strSource, "'" + strRef + "' reaches '" + nameField
                    + "' through a value whose declared type is not a record type");
        }

        DataShape shape = registry.shape(ref.idData())
                .orElseThrow(() -> new CaqlException(numLine, strSource, "'" + strRef
                        + "' needs " + ref.idData() + ", which the registry does not hold;"
                        + " its package has not been read from the participant"));

        if (!(shape instanceof DataShape.Rec shapeRec)) {
            throw new CaqlException(numLine, strSource, "'" + strRef + "' reaches '" + nameField
                    + "' through " + ref.idData().shortName() + ", which is not a record");
        }

        for (FieldInfo info : shapeRec.lstField()) {
            if (nameField.equals(info.nameField()))
                return info.type();
        }

        throw new CaqlException(numLine, strSource, "'" + strRef + "': "
                + ref.idData().shortName() + " declares no field '" + nameField + "'");
    }


    private static String describe(DamlValue value) {
        return switch (value) {
            case DamlValue.Lst ignored -> "a list";
            case DamlValue.Opt ignored -> "an optional";
            case DamlValue.Variant val -> "a variant";
            case DamlValue.TextMap ignored -> "a text map";
            case DamlValue.GenMap ignored -> "a map";
            case DamlValue.ContractRef ignored -> "a contract id";
            case DamlValue.Party ignored -> "a party";
            case DamlValue.Unit ignored -> "unit";
            default -> "a scalar";
        };
    }

}
