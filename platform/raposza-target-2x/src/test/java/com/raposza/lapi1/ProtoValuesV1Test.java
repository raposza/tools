// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lapi1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.LedgerException;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;
import com.raposza.wire.ProtoValues;
import com.raposza.wire.ValueText;

import com.daml.ledger.api.v1.ValueOuterClass;

import com.google.protobuf.Empty;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Exercises the descriptor-driven converter against REAL v1 protobuf messages,
 * built here rather than fetched from a participant. No Canton needed.
 *
 * These tests live in the client module, not in raposza-wire, because the
 * converter takes no bindings dependency by design and there is nothing in that
 * module to build a Value out of. At P5 raposza-target-3x gets the same
 * assertions against the v2 messages, and THAT pair is the evidence that one
 * converter serves both generations - a claim this file alone cannot make.
 *
 * Author Claude/bentzn
 */
class ProtoValuesV1Test {

    private static final String ID_PKG = "5d4f288902e3de632a6e4c4c82596c8ebca93f62c5339488ee47f6fc0c2f5dd6";


    private static ValueOuterClass.Identifier ident(String nameEntity) {
        return ValueOuterClass.Identifier.newBuilder().setPackageId(ID_PKG).setModuleName("Main")
                .setEntityName(nameEntity).build();
    }


    private static ValueOuterClass.Value text(String str) {
        return ValueOuterClass.Value.newBuilder().setText(str).build();
    }


    @Test
    void unitConverts() {
        DamlValue value = ProtoValues.toValue(ValueOuterClass.Value.newBuilder()
                .setUnit(Empty.getDefaultInstance()).build());
        assertInstanceOf(DamlValue.Unit.class, value);
    }


    @Test
    void boolConverts() {
        assertEquals(new DamlValue.Bool(true), ProtoValues
                .toValue(ValueOuterClass.Value.newBuilder().setBool(true).build()));
    }


    @Test
    void int64Converts() {
        assertEquals(new DamlValue.Int64(-9223372036854775808L), ProtoValues.toValue(
                ValueOuterClass.Value.newBuilder().setInt64(-9223372036854775808L).build()));
    }


    @Test
    void textConverts() {
        assertEquals(new DamlValue.Text("primary"), ProtoValues.toValue(text("primary")));
    }


    @Test
    void partyConverts() {
        String idParty = "Alice-d4d95138::122093c6658bca786e58d86ff00357812627486396660a8144d5";
        assertEquals(new DamlValue.Party(idParty), ProtoValues
                .toValue(ValueOuterClass.Value.newBuilder().setParty(idParty).build()));
    }


    @Test
    void contractIdConverts() {
        assertEquals(new DamlValue.ContractRef("00abc"), ProtoValues
                .toValue(ValueOuterClass.Value.newBuilder().setContractId("00abc").build()));
    }


    @Test
    void dateConverts() {
        DamlValue value = ProtoValues
                .toValue(ValueOuterClass.Value.newBuilder().setDate(20665).build());
        assertEquals(new DamlValue.DateVal(LocalDate.of(2026, 7, 31)), value);
    }


    /**
     * The failure this pins is a factor of 1000: microseconds read as millis
     * puts the value in 1970 and still renders as a plausible timestamp.
     */
    @Test
    void timestampIsMicrosecondsNotMillis() {
        // 2026-07-31T08:31:06.581794Z - the created_at from the banked OQ3 evidence.
        long micros = 1_785_486_666_581_794L;
        DamlValue value = ProtoValues
                .toValue(ValueOuterClass.Value.newBuilder().setTimestamp(micros).build());

        assertEquals(new DamlValue.TimeVal(Instant.ofEpochSecond(1_785_486_666L, 581_794_000L)),
                value);
        assertEquals(2026, ((DamlValue.TimeVal) value).inst().atZone(java.time.ZoneOffset.UTC)
                .getYear());
    }


    @Test
    void timestampBeforeEpochConverts() {
        DamlValue value = ProtoValues
                .toValue(ValueOuterClass.Value.newBuilder().setTimestamp(-1L).build());
        assertEquals(new DamlValue.TimeVal(Instant.ofEpochSecond(-1L, 999_999_000L)), value);
    }


    /**
     * 38 significant digits is the Daml Numeric maximum and roughly twice what
     * a double carries. Round-tripping through one loses the tail silently.
     */
    @Test
    void numericSurvivesMorePrecisionThanADouble() {
        String strNum = "12345678901234567890.1234567890123456";
        DamlValue value = ProtoValues
                .toValue(ValueOuterClass.Value.newBuilder().setNumeric(strNum).build());

        assertEquals(new BigDecimal(strNum), ((DamlValue.Decimal) value).num());
        assertEquals(strNum, ((DamlValue.Decimal) value).num().toPlainString());
    }


    @Test
    void numericKeepsTrailingZeroScale() {
        DamlValue value = ProtoValues
                .toValue(ValueOuterClass.Value.newBuilder().setNumeric("1250.0000000000").build());
        assertEquals("1250.0000000000", ((DamlValue.Decimal) value).num().toPlainString());
    }


    @Test
    void listConverts() {
        ValueOuterClass.Value value = ValueOuterClass.Value.newBuilder()
                .setList(ValueOuterClass.List.newBuilder().addElements(text("eur"))
                        .addElements(text("retail")))
                .build();

        DamlValue.Lst lst = (DamlValue.Lst) ProtoValues.toValue(value);
        assertEquals(2, lst.lstElem().size());
        assertEquals(new DamlValue.Text("retail"), lst.lstElem().get(1));
    }


    @Test
    void someConverts() {
        DamlValue value = ProtoValues.toValue(ValueOuterClass.Value.newBuilder()
                .setOptional(ValueOuterClass.Optional.newBuilder().setValue(text("day-to-day")))
                .build());
        assertEquals(new DamlValue.Opt(new DamlValue.Text("day-to-day")), value);
    }


    @Test
    void noneConverts() {
        DamlValue value = ProtoValues.toValue(ValueOuterClass.Value.newBuilder()
                .setOptional(ValueOuterClass.Optional.getDefaultInstance()).build());
        assertNull(((DamlValue.Opt) value).value());
    }


    @Test
    void enumConverts() {
        DamlValue value = ProtoValues.toValue(ValueOuterClass.Value.newBuilder()
                .setEnum(ValueOuterClass.Enum.newBuilder().setEnumId(ident("Grade"))
                        .setConstructor("Gold"))
                .build());

        DamlValue.EnumVal val = (DamlValue.EnumVal) value;
        assertEquals("Gold", val.nameCtor());
        assertEquals(new DataId(ID_PKG, "Main", "Grade"), val.idData());
    }


    @Test
    void variantConverts() {
        DamlValue value = ProtoValues.toValue(ValueOuterClass.Value.newBuilder()
                .setVariant(ValueOuterClass.Variant.newBuilder().setVariantId(ident("Shape"))
                        .setConstructor("Circle").setValue(text("r")))
                .build());

        DamlValue.Variant val = (DamlValue.Variant) value;
        assertEquals("Circle", val.nameCtor());
        assertEquals(new DamlValue.Text("r"), val.value());
        assertEquals("Shape", val.idData().nameEntity());
    }


    @Test
    void textMapConverts() {
        DamlValue value = ProtoValues.toValue(ValueOuterClass.Value.newBuilder()
                .setMap(ValueOuterClass.Map.newBuilder()
                        .addEntries(ValueOuterClass.Map.Entry.newBuilder().setKey("eur")
                                .setValue(text("euro"))))
                .build());

        DamlValue.TextMap map = (DamlValue.TextMap) value;
        assertEquals(1, map.lstEntry().size());
        assertEquals("eur", map.lstEntry().get(0).strKey());
    }


    @Test
    void genMapConverts() {
        DamlValue value = ProtoValues.toValue(ValueOuterClass.Value.newBuilder()
                .setGenMap(ValueOuterClass.GenMap.newBuilder()
                        .addEntries(ValueOuterClass.GenMap.Entry.newBuilder()
                                .setKey(ValueOuterClass.Value.newBuilder().setInt64(1))
                                .setValue(text("one"))))
                .build());

        DamlValue.GenMap map = (DamlValue.GenMap) value;
        assertEquals(new DamlValue.Int64(1), map.lstEntry().get(0).key());
    }


    /**
     * The whole increment rests on labels arriving from the wire, so a nested
     * record with labels at BOTH depths is the assertion that matters. A
     * top-level label with an unlabelled inner record still breaks rendering.
     */
    @Test
    void nestedRecordCarriesLabelsAtEveryDepth() {
        ValueOuterClass.Record recAddress = ValueOuterClass.Record.newBuilder()
                .setRecordId(ident("Address"))
                .addFields(ValueOuterClass.RecordField.newBuilder().setLabel("street")
                        .setValue(text("Rua Augusta 1")))
                .addFields(ValueOuterClass.RecordField.newBuilder().setLabel("city")
                        .setValue(text("Lisboa")))
                .build();

        ValueOuterClass.Record recAccount = ValueOuterClass.Record.newBuilder()
                .setRecordId(ident("Account"))
                .addFields(ValueOuterClass.RecordField.newBuilder().setLabel("label")
                        .setValue(text("primary")))
                .addFields(ValueOuterClass.RecordField.newBuilder().setLabel("address")
                        .setValue(ValueOuterClass.Value.newBuilder().setRecord(recAddress)))
                .build();

        DamlValue.Rec rec = ProtoValues.toRecord(recAccount);
        assertEquals(new DataId(ID_PKG, "Main", "Account"), rec.idData());
        assertEquals("address", rec.lstField().get(1).nameField());

        DamlValue.Rec recInner = (DamlValue.Rec) rec.lstField().get(1).value();
        assertEquals(new DataId(ID_PKG, "Main", "Address"), recInner.idData());
        assertEquals("street", recInner.lstField().get(0).nameField());
        assertEquals("city", recInner.lstField().get(1).nameField());
    }


    /**
     * What an unlabelled read looks like. Not a failure - it is what a
     * non-verbose request returns - but it must not be mistaken for one.
     */
    @Test
    void unlabelledRecordYieldsEmptyNamesAndNoTypeId() {
        ValueOuterClass.Record rec = ValueOuterClass.Record.newBuilder()
                .addFields(ValueOuterClass.RecordField.newBuilder().setValue(text("primary")))
                .build();

        DamlValue.Rec val = ProtoValues.toRecord(rec);
        assertNull(val.idData());
        assertEquals("", val.lstField().get(0).nameField());
    }


    @Test
    void valueWithNoVariantSetFailsLoudly() {
        LedgerException ex = assertThrows(LedgerException.class,
                () -> ProtoValues.toValue(ValueOuterClass.Value.getDefaultInstance()));
        assertTrue(ex.getMessage().contains("no variant set"), ex.getMessage());
    }


    @Test
    void identifierConverts() {
        DataId idData = ProtoValues.toDataId(ident("Account"));
        assertEquals(ID_PKG, idData.idPackage());
        assertEquals("Main:Account", idData.shortName());
    }


    @Test
    void flattenReachesNestedScalars() {
        ValueOuterClass.Record rec = ValueOuterClass.Record.newBuilder()
                .addFields(ValueOuterClass.RecordField.newBuilder().setLabel("tags")
                        .setValue(ValueOuterClass.Value.newBuilder()
                                .setList(ValueOuterClass.List.newBuilder()
                                        .addElements(text("retail")))))
                .build();

        DamlValue.Rec val = ProtoValues.toRecord(rec);
        assertNotNull(val);
        assertTrue(ValueText.contains(val, "RETAIL"));
        assertTrue(ValueText.contains(val, ""));
        assertTrue(!ValueText.contains(val, "wholesale"));
    }

}
