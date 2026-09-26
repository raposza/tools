// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The coercer against synthetic types, with no participant anywhere.
 *
 * Most cases are stated as a ROUND TRIP through JsonRenderer rather than
 * against a hand-written JSON literal, and that is the point of the class: the
 * two are one encoding specification, so a test that asserts against a literal
 * would let them drift together while both stayed green.
 *
 * A handful of cases are literals on purpose - the ones an operator types by
 * hand and the renderer never produces, such as a missing optional field or an
 * Int64 written as a bare number.
 *
 * Author Claude/bentzn
 */
class JsonCoercerTest {

    private static final String ID_PKG = "cafe";

    private static final DataId ID_ADDRESS = new DataId(ID_PKG, "Main", "Address");
    private static final DataId ID_COLOUR = new DataId(ID_PKG, "Main", "Colour");
    private static final DataId ID_SHAPE = new DataId(ID_PKG, "Main", "Shape");
    private static final DataId ID_BOX = new DataId(ID_PKG, "Main", "Box");
    private static final DataId ID_ACCOUNT = new DataId(ID_PKG, "Main", "Account");

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonRenderer renderer = new JsonRenderer(false);
    private final JsonCoercer coercer = new JsonCoercer(registry());


    /**
     * Every primitive, every container and every user-defined shape in one
     * value. A per-kind test would pass on a coercer that cannot nest.
     */
    @Test
    void theWholeShapeRoundTrips() throws Exception {
        DamlValue.Rec account = account();
        assertEquals(account, coercer.coerceRecord(json(account), ID_ACCOUNT));
    }


    @Test
    void aParameterisedFieldRoundTrips() throws Exception {
        DamlValue.Rec box = new DamlValue.Rec(ID_BOX,
                List.of(new DamlValue.Rec.Field("content", new DamlValue.Text("wrapped"))));

        DamlType type = new DamlType.App(new DamlType.Ref(ID_BOX),
                List.of(new DamlType.Prim(PrimKind.TEXT)));
        assertEquals(box, coercer.coerce(json(box), type));
    }


    /** Absent means None. It is the convention an operator will have typed. */
    @Test
    void anAbsentOptionalFieldIsNone() throws Exception {
        JsonNode node = mapper.readTree("{\"line\":\"1 High St\"}");
        DamlValue value = coercer.coerce(node, new DamlType.Ref(ID_ADDRESS));

        assertEquals(new DamlValue.Rec(ID_ADDRESS,
                List.of(new DamlValue.Rec.Field("line", new DamlValue.Text("1 High St")),
                        new DamlValue.Rec.Field("zip", new DamlValue.Opt(null)))),
                value);
    }


    /** An operator writing a fixture by hand types 42, not "42". */
    @Test
    void aBareNumberIsAcceptedForInt64() throws Exception {
        assertEquals(new DamlValue.Int64(42),
                coercer.coerce(mapper.readTree("42"), new DamlType.Prim(PrimKind.INT64)));
    }


    /**
     * A key nobody declared is a typo, and a typo that submits is worse than a
     * refusal. The message names the key.
     */
    @Test
    void anUnknownKeyIsRefusedByName() throws Exception {
        JsonNode node = mapper.readTree("{\"line\":\"x\",\"zip\":null,\"postcode\":\"y\"}");
        CoercionException ex = assertThrows(CoercionException.class,
                () -> coercer.coerce(node, new DamlType.Ref(ID_ADDRESS)));

        assertTrue(ex.getMessage().contains("postcode"), ex.getMessage());
    }


    /** The path is what makes a failure inside a nested list actionable. */
    @Test
    void theFailurePathReachesTheOffendingField() throws Exception {
        JsonNode node = mapper.readTree(
                "{\"owner\":\"alice\",\"balance\":\"1.00\",\"count\":\"3\",\"opened\":"
                        + "\"2026-01-01T00:00:00Z\",\"since\":\"2026-01-01\",\"active\":true,"
                        + "\"unit\":{},\"cid\":\"00ab\",\"home\":{\"line\":\"x\",\"zip\":null},"
                        + "\"tags\":[\"a\",\"b\"],\"colour\":\"Red\",\"shape\":{\"tag\":\"Circle\","
                        + "\"value\":\"7\"},\"boxed\":{\"content\":\"c\"},\"meta\":{\"k\":\"v\"},"
                        + "\"pairs\":[[\"1\",\"one\"]],\"nick\":\"al\","
                        + "\"others\":[{\"line\":\"y\",\"zip\":{}}]}");

        CoercionException ex = assertThrows(CoercionException.class,
                () -> coercer.coerce(node, new DamlType.Ref(ID_ACCOUNT)));

        assertEquals("$.others[0].zip", ex.path(), ex.getMessage());
    }


    /**
     * Refused by name rather than guessed. The renderer cannot express Some
     * None either, and a coercer that invented an encoding here would disagree
     * with the file it is defined against.
     */
    @Test
    void aNestedOptionalIsRefusedRatherThanGuessed() throws Exception {
        DamlType type =
                new DamlType.OptionalOf(new DamlType.OptionalOf(new DamlType.Prim(PrimKind.TEXT)));
        CoercionException ex = assertThrows(CoercionException.class,
                () -> coercer.coerce(mapper.readTree("null"), type));

        assertTrue(ex.getMessage().contains("nested Optional"), ex.getMessage());
    }


    @Test
    void aScaleBeyondTheDeclaredNumericIsRefused() throws Exception {
        CoercionException ex = assertThrows(CoercionException.class,
                () -> coercer.coerce(mapper.readTree("\"1.234\""), new DamlType.Numeric(2)));

        assertTrue(ex.getMessage().contains("scale"), ex.getMessage());
    }


    /** Trailing zeros are part of what the ledger sent and must survive. */
    @Test
    void numericScaleIsPreserved() throws Exception {
        DamlValue value = new DamlValue.Decimal(new BigDecimal("100.0000000000"));
        assertEquals(value, coercer.coerce(json(value), new DamlType.Numeric(10)));
    }


    /** Unit is {} and an empty record is []; the two must not collapse. */
    @Test
    void unitAndAnEmptyRecordStayApart() throws Exception {
        assertEquals(new DamlValue.Unit(),
                coercer.coerce(mapper.readTree("{}"), new DamlType.Prim(PrimKind.UNIT)));

        assertThrows(CoercionException.class,
                () -> coercer.coerce(mapper.readTree("[]"), new DamlType.Prim(PrimKind.UNIT)));
    }


    /**
     * A read that did not set verbose returns no labels, so the renderer emits
     * an array. The coercer has to be able to read its own output.
     */
    @Test
    void aPositionalRecordIsReadAgainstTheDeclaredOrder() throws Exception {
        DamlValue.Rec unlabelled = new DamlValue.Rec(null,
                List.of(new DamlValue.Rec.Field("", new DamlValue.Text("1 High St")),
                        new DamlValue.Rec.Field("", new DamlValue.Opt(null))));

        assertEquals(new DamlValue.Rec(ID_ADDRESS,
                List.of(new DamlValue.Rec.Field("line", new DamlValue.Text("1 High St")),
                        new DamlValue.Rec.Field("zip", new DamlValue.Opt(null)))),
                coercer.coerce(json(unlabelled), new DamlType.Ref(ID_ADDRESS)));
    }


    @Test
    void aTypeTheRegistryDoesNotHoldSaysSo() throws Exception {
        DataId idUnknown = new DataId(ID_PKG, "Main", "Nope");
        CoercionException ex = assertThrows(CoercionException.class,
                () -> coercer.coerce(mapper.readTree("{}"), new DamlType.Ref(idUnknown)));

        assertTrue(ex.getMessage().contains("registry"), ex.getMessage());
    }


    private JsonNode json(DamlValue value) throws Exception {
        return mapper.readTree(renderer.value(value, Optional.empty()));
    }


    private static DamlValue.Rec account() {
        List<DamlValue.Rec.Field> lstField = new ArrayList<>();
        lstField.add(fld("owner", new DamlValue.Party("alice::abc")));
        lstField.add(fld("balance", new DamlValue.Decimal(new BigDecimal("1234.50"))));
        lstField.add(fld("count", new DamlValue.Int64(9007199254740993L)));
        lstField.add(fld("opened", new DamlValue.TimeVal(Instant.parse("2026-01-01T09:30:00Z"))));
        lstField.add(fld("since", new DamlValue.DateVal(LocalDate.of(2026, 1, 1))));
        lstField.add(fld("active", new DamlValue.Bool(true)));
        lstField.add(fld("unit", new DamlValue.Unit()));
        lstField.add(fld("cid", new DamlValue.ContractRef("00abcdef")));
        lstField.add(fld("home", address("1 High St", new DamlValue.Opt(null))));
        lstField.add(fld("tags", new DamlValue.Lst(
                List.of(new DamlValue.Text("a"), new DamlValue.Text("b")))));
        lstField.add(fld("colour", new DamlValue.EnumVal(ID_COLOUR, "Red")));
        lstField.add(fld("shape",
                new DamlValue.Variant(ID_SHAPE, "Circle", new DamlValue.Int64(7))));
        lstField.add(fld("boxed", new DamlValue.Rec(ID_BOX,
                List.of(fld("content", new DamlValue.Text("c"))))));
        lstField.add(fld("meta", new DamlValue.TextMap(
                List.of(new DamlValue.TextMap.Entry("k", new DamlValue.Text("v"))))));
        lstField.add(fld("pairs", new DamlValue.GenMap(
                List.of(new DamlValue.GenMap.Entry(new DamlValue.Int64(1),
                        new DamlValue.Text("one"))))));
        lstField.add(fld("nick", new DamlValue.Opt(new DamlValue.Text("al"))));
        lstField.add(fld("others", new DamlValue.Lst(
                List.of(address("y", new DamlValue.Opt(new DamlValue.Int64(17)))))));
        return new DamlValue.Rec(ID_ACCOUNT, List.copyOf(lstField));
    }


    private static DamlValue.Rec address(String strLine, DamlValue zip) {
        return new DamlValue.Rec(ID_ADDRESS,
                List.of(fld("line", new DamlValue.Text(strLine)), fld("zip", zip)));
    }


    private static DamlValue.Rec.Field fld(String nameField, DamlValue value) {
        return new DamlValue.Rec.Field(nameField, value);
    }


    private static TypeRegistry_i registry() {
        Map<DataId, DataShape> mapShape = new LinkedHashMap<>();

        mapShape.put(ID_ADDRESS, new DataShape.Rec(ID_ADDRESS, List.of(),
                List.of(new FieldInfo("line", new DamlType.Prim(PrimKind.TEXT)),
                        new FieldInfo("zip",
                                new DamlType.OptionalOf(new DamlType.Prim(PrimKind.INT64))))));

        mapShape.put(ID_COLOUR,
                new DataShape.EnumShape(ID_COLOUR, List.of(), List.of("Red", "Green")));

        mapShape.put(ID_SHAPE, new DataShape.Variant(ID_SHAPE, List.of(),
                List.of(new FieldInfo("Circle", new DamlType.Prim(PrimKind.INT64)),
                        new FieldInfo("Square", new DamlType.Prim(PrimKind.TEXT)))));

        // Parameterised, so the substitution path is exercised by the same
        // fixture the whole-shape test uses rather than only in isolation.
        mapShape.put(ID_BOX, new DataShape.Rec(ID_BOX, List.of("a"),
                List.of(new FieldInfo("content", new DamlType.Var("a")))));

        List<FieldInfo> lstField = new ArrayList<>();
        lstField.add(new FieldInfo("owner", new DamlType.Prim(PrimKind.PARTY)));
        lstField.add(new FieldInfo("balance", new DamlType.Numeric(2)));
        lstField.add(new FieldInfo("count", new DamlType.Prim(PrimKind.INT64)));
        lstField.add(new FieldInfo("opened", new DamlType.Prim(PrimKind.TIMESTAMP)));
        lstField.add(new FieldInfo("since", new DamlType.Prim(PrimKind.DATE)));
        lstField.add(new FieldInfo("active", new DamlType.Prim(PrimKind.BOOL)));
        lstField.add(new FieldInfo("unit", new DamlType.Prim(PrimKind.UNIT)));
        lstField.add(new FieldInfo("cid", new DamlType.Prim(PrimKind.CONTRACT_ID)));
        lstField.add(new FieldInfo("home", new DamlType.Ref(ID_ADDRESS)));
        lstField.add(new FieldInfo("tags",
                new DamlType.ListOf(new DamlType.Prim(PrimKind.TEXT))));
        lstField.add(new FieldInfo("colour", new DamlType.Ref(ID_COLOUR)));
        lstField.add(new FieldInfo("shape", new DamlType.Ref(ID_SHAPE)));
        lstField.add(new FieldInfo("boxed", new DamlType.App(new DamlType.Ref(ID_BOX),
                List.of(new DamlType.Prim(PrimKind.TEXT)))));
        lstField.add(new FieldInfo("meta",
                new DamlType.TextMapOf(new DamlType.Prim(PrimKind.TEXT))));
        lstField.add(new FieldInfo("pairs", new DamlType.GenMapOf(
                new DamlType.Prim(PrimKind.INT64), new DamlType.Prim(PrimKind.TEXT))));
        lstField.add(new FieldInfo("nick",
                new DamlType.OptionalOf(new DamlType.Prim(PrimKind.TEXT))));
        lstField.add(new FieldInfo("others",
                new DamlType.ListOf(new DamlType.Ref(ID_ADDRESS))));

        mapShape.put(ID_ACCOUNT, new DataShape.Rec(ID_ACCOUNT, List.of(), List.copyOf(lstField)));

        return new FakeRegistry(mapShape);
    }


    /**
     * Hand written rather than mocked. The coercer asks it exactly one
     * question and a mock would add a framework to answer it.
     */
    private record FakeRegistry(Map<DataId, DataShape> mapShape) implements TypeRegistry_i {

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

    }

}
