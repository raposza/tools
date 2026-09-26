// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.raposza.api.LedgerException;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.DataId;
import com.raposza.api.model.PrimKind;
import com.digitalasset.daml.lf.language.Ast;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The type mapping, including the four cases measured on real archives.
 *
 * Author Claude/bentzn
 */
class TypeMapperTest {

    private static final String ID_PKG = "4702e9c4";


    @Test
    void primitivesMapByName() {
        assertEquals(new DamlType.Prim(PrimKind.UNIT), TypeMapper.map(Lf.prim("BTUnit")));
        assertEquals(new DamlType.Prim(PrimKind.TEXT), TypeMapper.map(Lf.prim("BTText")));
        assertEquals(new DamlType.Prim(PrimKind.PARTY), TypeMapper.map(Lf.prim("BTParty")));
        assertEquals(new DamlType.Prim(PrimKind.INT64), TypeMapper.map(Lf.prim("BTInt64")));
        assertEquals(new DamlType.Prim(PrimKind.DATE), TypeMapper.map(Lf.prim("BTDate")));
        assertEquals(new DamlType.Prim(PrimKind.TIMESTAMP),
                TypeMapper.map(Lf.prim("BTTimestamp")));
        assertEquals(new DamlType.Prim(PrimKind.BOOL), TypeMapper.map(Lf.prim("BTBool")));
    }


    @Test
    void numericCarriesItsScale() {
        Ast.Type type = Lf.app(Lf.prim("BTNumeric"), new Ast.TNat(10));
        assertEquals(new DamlType.Numeric(10), TypeMapper.map(type));
    }


    @Test
    void containersStayThemselves() {
        assertEquals(new DamlType.ListOf(new DamlType.Prim(PrimKind.TEXT)),
                TypeMapper.map(Lf.app(Lf.prim("BTList"), Lf.prim("BTText"))));
        assertEquals(new DamlType.OptionalOf(new DamlType.Prim(PrimKind.TEXT)),
                TypeMapper.map(Lf.app(Lf.prim("BTOptional"), Lf.prim("BTText"))));
        assertEquals(new DamlType.TextMapOf(new DamlType.Prim(PrimKind.INT64)),
                TypeMapper.map(Lf.app(Lf.prim("BTTextMap"), Lf.prim("BTInt64"))));
        assertEquals(
                new DamlType.GenMapOf(new DamlType.Prim(PrimKind.PARTY),
                        new DamlType.Prim(PrimKind.INT64)),
                TypeMapper.map(Lf.app(Lf.prim("BTGenMap"), Lf.prim("BTParty"),
                        Lf.prim("BTInt64"))));
    }


    /**
     * Measured on the fixture: Deposit returns
     * TApp(TBuiltin(BTContractId), TTyCon(Account)). PrimKind.CONTRACT_ID
     * carries no template, so the argument is dropped deliberately.
     */
    @Test
    void contractIdDropsItsTemplateArgument() {
        Ast.Type type = Lf.app(Lf.prim("BTContractId"), Lf.con(ID_PKG, "Main", "Account"));
        assertEquals(new DamlType.Prim(PrimKind.CONTRACT_ID), TypeMapper.map(type));
    }


    @Test
    void typeConstructorBecomesARef() {
        DamlType type = TypeMapper.map(Lf.con(ID_PKG, "Main", "Account"));
        DamlType.Ref ref = assertInstanceOf(DamlType.Ref.class, type);
        assertEquals(new DataId(ID_PKG, "Main", "Account"), ref.idData());
    }


    /**
     * "Box Text" is fully applied and Ref(Box) alone would have lost the Text.
     */
    @Test
    void parameterisedTypeKeepsItsApplication() {
        Ast.Type type = Lf.app(Lf.con(ID_PKG, "Main", "Box"), Lf.prim("BTText"));
        DamlType.App app = assertInstanceOf(DamlType.App.class, TypeMapper.map(type));
        assertEquals(new DamlType.Ref(new DataId(ID_PKG, "Main", "Box")), app.typeFun());
        assertEquals(List.of(new DamlType.Prim(PrimKind.TEXT)), app.lstArg());
    }


    @Test
    void aTypeVariableIsAVariable() {
        assertEquals(new DamlType.Var("a"), TypeMapper.map(new Ast.TVar("a")));
    }


    @Test
    void anExpressibleTypeIsNotInventedForAFunction() {
        Ast.Type type = Lf.app(Lf.prim("BTArrow"), Lf.prim("BTText"), Lf.prim("BTText"));
        assertThrows(LedgerException.class, () -> TypeMapper.map(type));
    }


    @Test
    void wrongArityIsRefusedRatherThanTruncated() {
        Ast.Type type = Lf.app(Lf.prim("BTList"), Lf.prim("BTText"), Lf.prim("BTText"));
        assertThrows(LedgerException.class, () -> TypeMapper.map(type));
    }

}
