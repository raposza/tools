// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.Substitution_i;
import com.raposza.api.TypeRegistry_i;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;
import com.raposza.api.model.DataShape;
import com.raposza.api.model.FieldInfo;
import com.raposza.api.model.PrimKind;
import com.raposza.api.model.TemplateInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Substitution, which happens INSIDE coercion because that is the only place
 * holding both the binding and the declared type at the same point.
 *
 * Author Claude/bentzn
 */
class JsonCoercerSubstTest {

    private static final String ID_PKG = "cafe";
    private static final DataId ID_ACCOUNT = new DataId(ID_PKG, "Main", "Account");

    private static final DamlValue VAL_ALICE = new DamlValue.Party("Alice::1220ab");
    private static final DamlValue VAL_CID = new DamlValue.ContractRef("00abcd");

    private final ObjectMapper mapper = new ObjectMapper();


    private JsonCoercer coercer() {
        Map<String, Substitution_i.Bound> mapBound = new LinkedHashMap<>();
        mapBound.put("alice", new Substitution_i.Bound(VAL_ALICE,
                new DamlType.Prim(PrimKind.PARTY)));
        mapBound.put("acct", new Substitution_i.Bound(VAL_CID,
                new DamlType.Prim(PrimKind.CONTRACT_ID)));
        mapBound.put("amount", new Substitution_i.Bound(
                new DamlValue.Decimal(new BigDecimal("10.00")), new DamlType.Numeric(2)));

        return new JsonCoercer(registry(), name -> Optional.ofNullable(mapBound.get(name)));
    }


    private JsonNode json(String str) throws Exception {
        return mapper.readTree(str);
    }


    @Test
    void aWholeStringReferenceIsReplacedByTheBoundValue() throws Exception {
        DamlValue value = coercer().coerce(json("\"$alice\""),
                new DamlType.Prim(PrimKind.PARTY));
        assertEquals(VAL_ALICE, value);
    }


    /**
     * The case that motivated putting the check here at all: both are strings
     * in JSON, so nothing downstream would have caught it and the participant
     * would have rejected an invalid party id.
     */
    @Test
    void aBindingOfTheWrongTypeIsRefusedWithBothTypesNamed() throws Exception {
        CoercionException ex = assertThrows(CoercionException.class,
                () -> coercer().coerce(json("\"$acct\""), new DamlType.Prim(PrimKind.PARTY)));

        assertTrue(ex.getMessage().contains("CONTRACT_ID"), ex.getMessage());
        assertTrue(ex.getMessage().contains("PARTY"), ex.getMessage());
    }


    /** A Numeric of the wrong scale is a different type and is refused too. */
    @Test
    void numericScaleIsPartOfTheTypeMatch() throws Exception {
        assertEquals(new DamlValue.Decimal(new BigDecimal("10.00")),
                coercer().coerce(json("\"$amount\""), new DamlType.Numeric(2)));

        assertThrows(CoercionException.class,
                () -> coercer().coerce(json("\"$amount\""), new DamlType.Numeric(10)));
    }


    /**
     * An Optional field takes the bare value, because that is what the renderer
     * emits for Some - so the check has to see the ELEMENT type, not the
     * Optional.
     */
    @Test
    void aBindingSubstitutesIntoAnOptionalField() throws Exception {
        DamlValue value = coercer().coerce(json("\"$alice\""),
                new DamlType.OptionalOf(new DamlType.Prim(PrimKind.PARTY)));

        assertEquals(new DamlValue.Opt(VAL_ALICE), value);
    }


    @Test
    void aBindingSubstitutesIntoAListElement() throws Exception {
        DamlValue value = coercer().coerce(json("[\"$alice\", \"$alice\"]"),
                new DamlType.ListOf(new DamlType.Prim(PrimKind.PARTY)));

        assertEquals(new DamlValue.Lst(List.of(VAL_ALICE, VAL_ALICE)), value);
    }


    /** Sec. 6: whole string only. This is fixed now because a retrofit breaks scripts. */
    @Test
    void aReferenceInsideALongerStringIsLiteralText() throws Exception {
        assertEquals(new DamlValue.Text("owner-$alice"),
                coercer().coerce(json("\"owner-$alice\""), new DamlType.Prim(PrimKind.TEXT)));
    }


    @Test
    void doubleDollarIsALiteralDollar() throws Exception {
        assertEquals(new DamlValue.Text("$alice"),
                coercer().coerce(json("\"$$alice\""), new DamlType.Prim(PrimKind.TEXT)));

        assertEquals(new DamlValue.Text("$"),
                coercer().coerce(json("\"$$\""), new DamlType.Prim(PrimKind.TEXT)));
    }


    /**
     * A dollar followed by something that is not a name is ordinary text.
     * Refusing it would make a currency label a syntax error.
     */
    @Test
    void aDollarThatIsNotAReferenceIsOrdinaryText() throws Exception {
        assertEquals(new DamlValue.Text("$100"),
                coercer().coerce(json("\"$100\""), new DamlType.Prim(PrimKind.TEXT)));
    }


    /** The message says how to write it literally, since that is the likely intent. */
    @Test
    void anUnboundReferenceIsRefusedAndSaysHowToEscapeIt() throws Exception {
        CoercionException ex = assertThrows(CoercionException.class,
                () -> coercer().coerce(json("\"$nope\""), new DamlType.Prim(PrimKind.TEXT)));

        assertTrue(ex.getMessage().contains("not bound"), ex.getMessage());
        assertTrue(ex.getMessage().contains("$$nope"), ex.getMessage());
    }


    /** Without a provider, '$' is an ordinary character and nothing is touched. */
    @Test
    void withNoProviderADollarStringIsUntouched() throws Exception {
        JsonCoercer plain = new JsonCoercer(registry());

        assertEquals(new DamlValue.Text("$alice"),
                plain.coerce(json("\"$alice\""), new DamlType.Prim(PrimKind.TEXT)));
        assertEquals(new DamlValue.Text("$$alice"),
                plain.coerce(json("\"$$alice\""), new DamlType.Prim(PrimKind.TEXT)));
    }


    /** The path reaches the field, which is what makes a failure actionable. */
    @Test
    void theFailurePathNamesTheField() throws Exception {
        CoercionException ex = assertThrows(CoercionException.class,
                () -> coercer().coerceRecord(json("{\"owner\":\"$acct\",\"label\":\"x\"}"),
                        ID_ACCOUNT));

        assertEquals("$.owner", ex.path(), ex.getMessage());
    }


    @Test
    void aWholePayloadSubstitutes() throws Exception {
        DamlValue.Rec rec = coercer().coerceRecord(
                json("{\"owner\":\"$alice\",\"label\":\"fixture-1\"}"), ID_ACCOUNT);

        assertEquals(new DamlValue.Rec(ID_ACCOUNT, List.of(
                new DamlValue.Rec.Field("owner", VAL_ALICE),
                new DamlValue.Rec.Field("label", new DamlValue.Text("fixture-1")))), rec);
    }


    private static TypeRegistry_i registry() {
        Map<DataId, DataShape> mapShape = new LinkedHashMap<>();
        mapShape.put(ID_ACCOUNT, new DataShape.Rec(ID_ACCOUNT, List.of(),
                List.of(new FieldInfo("owner", new DamlType.Prim(PrimKind.PARTY)),
                        new FieldInfo("label", new DamlType.Prim(PrimKind.TEXT)))));

        return new TypeRegistry_i() {

            @Override
            public List<TemplateInfo> templates() {
                return List.of();
            }


            @Override
            public Optional<TemplateInfo> template(DataId idTemplate) {
                return Optional.empty();
            }


            @Override
            public List<TemplateInfo> templatesByName(String nameShort) {
                return List.of();
            }


            @Override
            public Optional<DataShape> shape(DataId idData) {
                return Optional.ofNullable(mapShape.get(idData));
            }


            @Override
            public void refresh() {
            }

        };
    }

}
