// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.util.List;

/**
 * Structural type model, decoded far enough to build an input widget and to
 * render a value.
 *
 * Var and App were added on measurement. The original note here said type
 * variables are not modelled because everything reaching the UI is fully
 * applied, and the second half of that is true while the first half does not
 * follow from it: a field of type "Box Text" IS fully applied, and expressing
 * it needs the application, because Ref(Box) alone has lost the Text. Both
 * banked fixtures carry such a field.
 *
 * Author Claude/bentzn
 */
public sealed interface DamlType {

    /** @param kind the primitive kind */
    record Prim(PrimKind kind) implements DamlType {}

    /** @param cntScale decimal places, 0..37 in LF */
    record Numeric(int cntScale) implements DamlType {}

    /** @param typeElem element type */
    record ListOf(DamlType typeElem) implements DamlType {}

    /** @param typeElem the Some type */
    record OptionalOf(DamlType typeElem) implements DamlType {}

    /** @param typeValue value type; keys are always Text */
    record TextMapOf(DamlType typeValue) implements DamlType {}

    /**
     * @param typeKey key type
     * @param typeValue value type
     */
    record GenMapOf(DamlType typeKey, DamlType typeValue) implements DamlType {}

    /**
     * Reference to a record, variant or enum. Resolve through TypeRegistry_i
     * rather than inlining, so recursive types terminate.
     *
     * @param idData the referenced data type
     */
    record Ref(DataId idData) implements DamlType {}

    /**
     * A type variable, as bound by the enclosing data type's parameters. Only
     * appears INSIDE a DataShape - a field of a parameterised record refers to
     * its own parameter. A fully applied occurrence carries App instead.
     *
     * @param nameVar the variable name, matching one of DataShape.lstParam
     */
    record Var(String nameVar) implements DamlType {}

    /**
     * Application of a parameterised type. "Box Text" is
     * App(Ref(Main:Box), [Prim(TEXT)]).
     *
     * The built-in applications - ListOf, OptionalOf, TextMapOf, GenMapOf -
     * stay as they are rather than becoming App over a builtin constructor.
     * They are the cases a widget must recognise, and collapsing them into a
     * general application would make every renderer re-derive what it already
     * knows.
     *
     * @param typeFun the applied type, normally a Ref
     * @param lstArg one argument per parameter, in declaration order
     */
    record App(DamlType typeFun, List<DamlType> lstArg) implements DamlType {}

}
