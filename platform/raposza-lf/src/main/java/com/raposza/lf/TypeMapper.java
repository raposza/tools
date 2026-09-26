// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lf;

import com.raposza.api.LedgerException;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.PrimKind;
import com.digitalasset.daml.lf.language.Ast;

import java.util.ArrayList;
import java.util.List;

/**
 * The decoded AST's Type, expressed in the semantic model.
 *
 * Builtin types arrive as Scala objects - TBuiltin(BTContractId) and so on -
 * and are recognised by productPrefix() rather than by importing thirty-eight
 * classes. The prefix is the object's own name; measured, not assumed.
 *
 * Application is a SPINE, not a list: "ContractId Account" decodes as
 * TApp(TBuiltin(BTContractId), TTyCon(Account)) and "GenMap k v" as
 * TApp(TApp(TBuiltin(BTGenMap), k), v). The spine is flattened here once, and
 * the head decides everything.
 *
 * The built-in containers stay as ListOf, OptionalOf, TextMapOf and GenMapOf
 * rather than becoming App over a builtin constructor, because they are the
 * cases a widget must recognise and a general application would make every
 * renderer re-derive what the model already knows. A user-defined
 * parameterised type keeps its application: "Box Text" is
 * App(Ref(Main:Box), [Prim(TEXT)]).
 *
 * ContractId LOSES its argument. PrimKind.CONTRACT_ID carries no template, and
 * inventing a place for it here would widen the model on behalf of a value
 * that renders as a string either way. If that becomes wrong, it is a finding
 * about the model and the model changes - not this file.
 *
 * An unmappable type THROWS rather than degrading. Only serializable data
 * types are mapped (see AstMapper), and a serializable type that this cannot
 * express is exactly the finding the design says to report and stop on.
 *
 * Author Claude/bentzn
 */
public final class TypeMapper {

    private TypeMapper() {
    }


    /**
     * @param type a type from the decoded AST
     * @return the same type in the semantic model
     * @throws LedgerException when the type cannot be expressed
     */
    public static DamlType map(Ast.Type type) {
        if (type == null)
            throw new LedgerException("null type in the decoded archive");

        if (type instanceof Ast.TVar typeVar)
            return new DamlType.Var(typeVar.name());

        if (type instanceof Ast.TTyCon typeCon)
            return new DamlType.Ref(Refs.dataId(typeCon.tycon()));

        if (type instanceof Ast.TBuiltin typeBuiltin)
            return builtin(typeBuiltin.bt(), new ArrayList<>(), type);

        if (type instanceof Ast.TApp) {
            List<Ast.Type> lstArg = new ArrayList<>();
            Ast.Type typeHead = type;
            while (typeHead instanceof Ast.TApp typeApp) {
                lstArg.add(0, typeApp.arg());
                typeHead = typeApp.tyfun();
            }

            if (typeHead instanceof Ast.TBuiltin typeBuiltin)
                return builtin(typeBuiltin.bt(), lstArg, type);

            List<DamlType> lstMapped = new ArrayList<>();
            for (Ast.Type typeArg : lstArg) {
                lstMapped.add(map(typeArg));
            }
            return new DamlType.App(map(typeHead), List.copyOf(lstMapped));
        }

        throw new LedgerException("cannot express this Daml-LF type in the semantic model: "
                + type.getClass().getSimpleName() + " " + type);
    }


    /**
     * @param bt the builtin type constructor
     * @param lstArg its arguments, in application order, empty when unapplied
     * @param typeWhole the whole type, for the failure message only
     * @return the semantic model form
     * @throws LedgerException when the arity is not what the constructor takes,
     *         or the constructor is one no serializable value can carry
     */
    private static DamlType builtin(Ast.BuiltinType bt, List<Ast.Type> lstArg,
            Ast.Type typeWhole) {
        String nameBt = bt.productPrefix();
        switch (nameBt) {
            case "BTUnit":
                return prim(PrimKind.UNIT, nameBt, lstArg, 0, typeWhole);
            case "BTBool":
                return prim(PrimKind.BOOL, nameBt, lstArg, 0, typeWhole);
            case "BTInt64":
                return prim(PrimKind.INT64, nameBt, lstArg, 0, typeWhole);
            case "BTText":
                return prim(PrimKind.TEXT, nameBt, lstArg, 0, typeWhole);
            case "BTTimestamp":
                return prim(PrimKind.TIMESTAMP, nameBt, lstArg, 0, typeWhole);
            case "BTDate":
                return prim(PrimKind.DATE, nameBt, lstArg, 0, typeWhole);
            case "BTParty":
                return prim(PrimKind.PARTY, nameBt, lstArg, 0, typeWhole);
            case "BTContractId":
                // Argument deliberately dropped; see the type comment.
                return prim(PrimKind.CONTRACT_ID, nameBt, lstArg, 1, typeWhole);
            case "BTNumeric":
                return numeric(lstArg, typeWhole);
            case "BTList":
                arity(nameBt, lstArg, 1, typeWhole);
                return new DamlType.ListOf(map(lstArg.get(0)));
            case "BTOptional":
                arity(nameBt, lstArg, 1, typeWhole);
                return new DamlType.OptionalOf(map(lstArg.get(0)));
            case "BTTextMap":
                arity(nameBt, lstArg, 1, typeWhole);
                return new DamlType.TextMapOf(map(lstArg.get(0)));
            case "BTGenMap":
                arity(nameBt, lstArg, 2, typeWhole);
                return new DamlType.GenMapOf(map(lstArg.get(0)), map(lstArg.get(1)));
            default:
                throw new LedgerException("no serializable value carries this builtin type: "
                        + nameBt + " in " + typeWhole);
        }
    }


    /**
     * Numeric carries its scale as a type-level natural: "Numeric 10" is
     * TApp(TBuiltin(BTNumeric), TNat(10)).
     *
     * @param lstArg the application's arguments
     * @param typeWhole the whole type, for the failure message
     * @return the semantic model form
     */
    private static DamlType numeric(List<Ast.Type> lstArg, Ast.Type typeWhole) {
        arity("BTNumeric", lstArg, 1, typeWhole);
        if (!(lstArg.get(0) instanceof Ast.TNat typeNat)) {
            throw new LedgerException("Numeric scale is not a type-level natural in "
                    + typeWhole);
        }
        return new DamlType.Numeric(typeNat.n());
    }


    /**
     * @param kind the primitive kind
     * @param nameBt the constructor name, for the failure message
     * @param lstArg the application's arguments
     * @param cntAllowed how many arguments the constructor may carry
     * @param typeWhole the whole type, for the failure message
     * @return the primitive
     */
    private static DamlType prim(PrimKind kind, String nameBt, List<Ast.Type> lstArg,
            int cntAllowed, Ast.Type typeWhole) {
        if (lstArg.size() > cntAllowed) {
            throw new LedgerException(nameBt + " applied to " + lstArg.size()
                    + " arguments in " + typeWhole);
        }
        return new DamlType.Prim(kind);
    }


    /**
     * @param nameBt the constructor name, for the failure message
     * @param lstArg the application's arguments
     * @param cntWant the required count
     * @param typeWhole the whole type, for the failure message
     */
    private static void arity(String nameBt, List<Ast.Type> lstArg, int cntWant,
            Ast.Type typeWhole) {
        if (lstArg.size() != cntWant) {
            throw new LedgerException(nameBt + " takes " + cntWant + " arguments, found "
                    + lstArg.size() + " in " + typeWhole);
        }
    }

}
