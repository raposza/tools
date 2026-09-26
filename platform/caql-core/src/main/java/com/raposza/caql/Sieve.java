// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import com.raposza.api.TypeRegistry_i;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataShape;
import com.raposza.api.model.FieldInfo;
import com.raposza.api.model.PrimKind;
import com.raposza.api.model.TemplateInfo;
import com.raposza.render.CoercionException;
import com.raposza.render.JsonCoercer;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * A WHERE clause bound to one template, applied to the contracts a read
 * returned.
 *
 * <h2>Structure is decided once, before any contract is looked at</h2>
 *
 * A field the template does not declare, an ordering asked of a type that has
 * none, a value that does not coerce to the field's declared type, a binding
 * holding a contract id where the field declares a party: each is a mistake in
 * the script, decidable without data, and each fails the statement locally
 * with nothing sent. They are resolved here, in the constructor, so a read
 * that returns no contracts still refuses a clause that could never have
 * matched one.
 *
 * <h2>Missing data does not fail - it does not match</h2>
 *
 * A declared field absent from one contract's payload, or an Optional holding
 * None, makes that contract a non-match and the sieve moves on. If absence
 * stopped the run, the first ragged contract would make the filter unusable on
 * exactly the data it exists to explore.
 *
 * <h2>Comparison semantics</h2>
 *
 * Ordered: Int64, Numeric, Text (lexicographic), Date, Timestamp - all five
 * operators. Equality only: party, contract id, Bool, enum constructor; an
 * ordering on one of these stops the statement and says so. Optionals are
 * transparent: {@code note = "x"} tests the Some, {@code note = null} asks for
 * None, and null may only be compared with {@code =}.
 *
 * Author Claude/bentzn
 */
final class Sieve {

    private final Node root;


    /**
     * Resolves every comparison in the clause against the template.
     *
     * @param clause the parsed clause
     * @param template the template the read is over
     * @param registry the run's registry snapshot, for nested record shapes
     * @param coercer the statement's coercer, substitution included
     * @param stmt the statement, for the line and source on every failure
     * @throws CaqlException on any structural fault
     */
    Sieve(Clause clause, TemplateInfo template, TypeRegistry_i registry, JsonCoercer coercer,
            Stmt stmt) {
        this.root = resolve(clause, template, registry, coercer, stmt);
    }


    /**
     * @param payload one contract's payload
     * @return whether the clause holds for it
     */
    boolean matches(DamlValue.Rec payload) {
        return root.holds(payload);
    }


    // ------------------------------------------------------------ resolution

    /** The clause, with every comparison resolved, in the same shape. */
    private static Node resolve(Clause node, TemplateInfo template, TypeRegistry_i registry,
            JsonCoercer coercer, Stmt stmt) {
        return switch (node) {
            case Clause.And val -> new And(resolve(val.left(), template, registry, coercer, stmt),
                    resolve(val.right(), template, registry, coercer, stmt));
            case Clause.Or val -> new Or(resolve(val.left(), template, registry, coercer, stmt),
                    resolve(val.right(), template, registry, coercer, stmt));
            case Clause.Not val -> new Not(resolve(val.inner(), template, registry, coercer, stmt));
            case Clause.Cmp val -> test(val, template, registry, coercer, stmt);
        };
    }


    /**
     * One comparison, made ready: the declared type found, the operator
     * checked against it, the right-hand side coerced.
     */
    private static Test test(Clause.Cmp cmp, TemplateInfo template, TypeRegistry_i registry,
            JsonCoercer coercer, Stmt stmt) {
        String strPath = String.join(".", cmp.lstField());
        DamlType type = declared(cmp.lstField(), template, registry, strPath, stmt);

        // Optionals are transparent: the comparison is against the Some.
        boolean flagOptional = type instanceof DamlType.OptionalOf;
        DamlType typeElem = flagOptional ? ((DamlType.OptionalOf) type).typeElem() : type;

        Kind kind = kindOf(typeElem, registry);
        if (kind == Kind.NONE) {
            throw new CaqlException(stmt.numLine(), stmt.strSource(), "'" + strPath + "' is "
                    + describe(typeElem, registry) + ", which cannot be compared; WHERE"
                    + " compares Int64, Numeric, Text, Date, Timestamp, party, contract id,"
                    + " Bool and enum fields");
        }
        if (cmp.op().flagOrdering() && kind == Kind.EQUALITY) {
            throw new CaqlException(stmt.numLine(), stmt.strSource(), "'" + strPath + "' is "
                    + describe(typeElem, registry) + ", which has no ordering; only = applies"
                    + " to it");
        }

        if (cmp.nodeValue().isNull()) {
            if (cmp.op().flagOrdering()) {
                throw new CaqlException(stmt.numLine(), stmt.strSource(), "'" + strPath + " "
                        + cmp.op().strSymbol() + " null' has no answer; null may only be"
                        + " compared with =");
            }
            if (!flagOptional) {
                throw new CaqlException(stmt.numLine(), stmt.strSource(), "'" + strPath
                        + "' is not an Optional, so it is never null");
            }
            return new Test(cmp, null, kind);
        }

        DamlValue value;
        try {
            value = coercer.coerce(cmp.nodeValue(), typeElem);
        }
        catch (CoercionException ex) {
            throw new CaqlException(stmt.numLine(), stmt.strSource(), "WHERE " + strPath + " "
                    + cmp.op().strSymbol() + " " + cmp.strValue() + ": " + ex.getMessage());
        }
        return new Test(cmp, value, kind);
    }


    /**
     * The declared type at the end of the path: the template's own fields for
     * the first segment, the registry's record shapes for every further one.
     */
    private static DamlType declared(List<String> lstField, TemplateInfo template,
            TypeRegistry_i registry, String strPath, Stmt stmt) {
        String nameFirst = lstField.get(0);
        DamlType type = null;
        List<String> lstOffered = new ArrayList<>();
        for (FieldInfo info : template.lstField()) {
            if (nameFirst.equals(info.nameField()))
                type = info.type();
            lstOffered.add(info.nameField());
        }
        if (type == null) {
            throw new CaqlException(stmt.numLine(), stmt.strSource(),
                    template.idTemplate().shortName() + " declares no field '" + nameFirst
                            + "'; it offers " + String.join(", ", lstOffered));
        }

        for (int cntLoop = 1; cntLoop < lstField.size(); cntLoop++) {
            type = FieldPath.fieldType(type, lstField.get(cntLoop), registry, strPath,
                    stmt.numLine(), stmt.strSource());
        }
        return type;
    }


    private static Kind kindOf(DamlType type, TypeRegistry_i registry) {
        return switch (type) {
            case DamlType.Numeric ignored -> Kind.ORDERED;
            case DamlType.Prim val -> switch (val.kind()) {
                case INT64, TEXT, DATE, TIMESTAMP -> Kind.ORDERED;
                case PARTY, CONTRACT_ID, BOOL -> Kind.EQUALITY;
                case UNIT -> Kind.NONE;
            };
            case DamlType.Ref val -> registry.shape(val.idData())
                    .filter(shape -> shape instanceof DataShape.EnumShape).isPresent()
                            ? Kind.EQUALITY
                            : Kind.NONE;
            default -> Kind.NONE;
        };
    }


    // ------------------------------------------------------------ evaluation

    private sealed interface Node {

        boolean holds(DamlValue.Rec payload);

    }


    private record And(Node left, Node right) implements Node {

        @Override
        public boolean holds(DamlValue.Rec payload) {
            return left.holds(payload) && right.holds(payload);
        }

    }


    private record Or(Node left, Node right) implements Node {

        @Override
        public boolean holds(DamlValue.Rec payload) {
            return left.holds(payload) || right.holds(payload);
        }

    }


    private record Not(Node inner) implements Node {

        @Override
        public boolean holds(DamlValue.Rec payload) {
            return !inner.holds(payload);
        }

    }


    /** What the declared type admits. */
    private enum Kind {

        ORDERED,
        EQUALITY,
        NONE

    }


    /**
     * One resolved comparison.
     *
     * @param cmp the comparison as parsed
     * @param value the coerced right-hand side, or null for {@code = null}
     * @param kind what the declared type admits
     */
    private record Test(Clause.Cmp cmp, DamlValue value, Kind kind) implements Node {

        @Override
        public boolean holds(DamlValue.Rec payload) {
            DamlValue found = walk(payload, cmp.lstField());

            // Absent, or None: a non-match, never a failure. Except that None
            // is exactly what '= null' asks for.
            if (found instanceof DamlValue.Opt opt)
                found = opt.value();
            if (value == null)
                return found == null;
            if (found == null)
                return false;

            Integer numOrder = compare(found, value);
            if (numOrder == null)
                return false;

            return switch (cmp.op()) {
                case EQ -> numOrder == 0;
                case LT -> numOrder < 0;
                case LE -> numOrder <= 0;
                case GT -> numOrder > 0;
                case GE -> numOrder >= 0;
            };
        }


        /** The value at the path, or null where the payload does not carry it. */
        private static DamlValue walk(DamlValue.Rec payload, List<String> lstField) {
            DamlValue value = payload;
            for (String nameField : lstField) {
                if (!(value instanceof DamlValue.Rec rec))
                    return null;
                DamlValue next = null;
                for (DamlValue.Rec.Field fld : rec.lstField()) {
                    if (nameField.equals(fld.nameField())) {
                        next = fld.value();
                        break;
                    }
                }
                if (next == null)
                    return null;
                value = next;
            }
            return value;
        }


        /**
         * Compares two values of what should be the same type. Null when
         * the payload's value is not of the declared shape - which the
         * registry said it would be, so this is broken data, and broken data
         * does not match rather than stopping the run.
         */
        private static Integer compare(DamlValue found, DamlValue value) {
            if (found instanceof DamlValue.Int64 a && value instanceof DamlValue.Int64 b)
                return Long.compare(a.num(), b.num());
            if (found instanceof DamlValue.Decimal a && value instanceof DamlValue.Decimal b)
                return a.num().compareTo(b.num());
            if (found instanceof DamlValue.Text a && value instanceof DamlValue.Text b)
                return Integer.signum(a.str().compareTo(b.str()));
            if (found instanceof DamlValue.DateVal a && value instanceof DamlValue.DateVal b)
                return a.date().compareTo(b.date());
            if (found instanceof DamlValue.TimeVal a && value instanceof DamlValue.TimeVal b)
                return a.inst().compareTo(b.inst());
            if (found instanceof DamlValue.Party a && value instanceof DamlValue.Party b)
                return a.idParty().equals(b.idParty()) ? 0 : 1;
            if (found instanceof DamlValue.ContractRef a
                    && value instanceof DamlValue.ContractRef b)
                return a.idContract().equals(b.idContract()) ? 0 : 1;
            if (found instanceof DamlValue.Bool a && value instanceof DamlValue.Bool b)
                return a.flag() == b.flag() ? 0 : 1;
            // The constructor name alone: the ledger reports the enum's data
            // id in whatever form the target hands back, and the coerced side
            // carries the registry's, so the id is not what is being asked.
            if (found instanceof DamlValue.EnumVal a && value instanceof DamlValue.EnumVal b)
                return a.nameCtor().equals(b.nameCtor()) ? 0 : 1;
            return null;
        }

    }


    private static String describe(DamlType type, TypeRegistry_i registry) {
        return switch (type) {
            case DamlType.Prim val -> switch (val.kind()) {
                case PARTY -> "a party";
                case CONTRACT_ID -> "a contract id";
                case BOOL -> "a Bool";
                case UNIT -> "unit";
                default -> val.kind().name();
            };
            case DamlType.Numeric val -> "Numeric " + val.cntScale();
            case DamlType.ListOf ignored -> "a list";
            case DamlType.OptionalOf ignored -> "an optional";
            case DamlType.TextMapOf ignored -> "a text map";
            case DamlType.GenMapOf ignored -> "a map";
            case DamlType.Ref val -> val.idData().shortName() + (registry.shape(val.idData())
                    .filter(shape -> shape instanceof DataShape.EnumShape).isPresent()
                            ? ", an enum" : ", a record or variant");
            case DamlType.App val -> "a parameterised type";
            case DamlType.Var val -> "a type variable";
        };
    }

}
