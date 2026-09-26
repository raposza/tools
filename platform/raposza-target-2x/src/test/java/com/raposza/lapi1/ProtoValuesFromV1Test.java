// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lapi1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.LedgerException;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;
import com.raposza.wire.ProtoValues;

import com.daml.ledger.api.v1.ValueOuterClass;

import com.google.protobuf.Message;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The write direction, against REAL v1 protobuf messages and no participant.
 *
 * Stated as a ROUND TRIP - semantic to wire to semantic - rather than against
 * expected bytes. Asserting bytes would pin this file to one generation's field
 * numbers, which is the exact property the converter exists not to depend on,
 * and it would pass on a pair of converters that were wrong in the same
 * direction. The round trip fails when they disagree, which is the risk.
 *
 * Lives in the client module for the reason ProtoValuesV1Test does: the wire
 * module takes no bindings dependency and has nothing to build a Value from.
 * raposza-target-3x gets the same assertions at P5, and THAT pair is the
 * evidence that one converter serves both generations.
 *
 * Author Claude/bentzn
 */
class ProtoValuesFromV1Test {

    private static final String ID_PKG =
            "5d4f288902e3de632a6e4c4c82596c8ebca93f62c5339488ee47f6fc0c2f5dd6";

    private static final DataId ID_ADDRESS = new DataId(ID_PKG, "Main", "Address");
    private static final DataId ID_GRADE = new DataId(ID_PKG, "Main", "Grade");
    private static final DataId ID_SHAPE = new DataId(ID_PKG, "Main", "Shape");
    private static final DataId ID_ACCOUNT = new DataId(ID_PKG, "Main", "Account");


    private static DamlValue back(DamlValue value) {
        Message msg = ProtoValues.fromValue(value, ValueOuterClass.Value.newBuilder());
        return ProtoValues.toValue(msg);
    }


    private static void trip(DamlValue value) {
        assertEquals(value, back(value));
    }


    @Test
    void everyPrimitiveRoundTrips() {
        trip(new DamlValue.Unit());
        trip(new DamlValue.Bool(true));
        trip(new DamlValue.Bool(false));
        trip(new DamlValue.Int64(0L));
        trip(new DamlValue.Int64(Long.MIN_VALUE));
        trip(new DamlValue.Int64(Long.MAX_VALUE));
        trip(new DamlValue.Text(""));
        trip(new DamlValue.Text("a \"quoted\" string with \u00e7 and \u4e2d"));
        trip(new DamlValue.Party("party-1::1220abcd"));
        trip(new DamlValue.ContractRef("00abcdef"));
        trip(new DamlValue.DateVal(LocalDate.of(2026, 8, 7)));
        trip(new DamlValue.DateVal(LocalDate.of(1969, 12, 31)));
    }


    /** Trailing zeros are part of what the ledger holds. */
    @Test
    void numericKeepsItsScale() {
        trip(new DamlValue.Decimal(new BigDecimal("0.0000000000")));
        trip(new DamlValue.Decimal(new BigDecimal("1250.0")));
        trip(new DamlValue.Decimal(new BigDecimal("-99999999999999999999.9999999999")));
    }


    /**
     * Scientific notation is not what the ledger accepts, and BigDecimal
     * reaches for it above a certain exponent. toPlainString is the reason this
     * passes.
     */
    @Test
    void aLargeNumericIsNotWrittenInScientificNotation() {
        DamlValue.Decimal value = new DamlValue.Decimal(new BigDecimal("1E+20"));
        Message msg = ProtoValues.fromValue(value, ValueOuterClass.Value.newBuilder());
        String strWire = ((ValueOuterClass.Value) msg).getNumeric();

        assertFalse(strWire.contains("E"), "wrote scientific notation: " + strWire);
        assertEquals(0, new BigDecimal(strWire).compareTo(value.num()));
    }


    /** Microseconds, and it has to survive the epoch boundary in both directions. */
    @Test
    void timestampRoundTripsAcrossTheEpoch() {
        trip(new DamlValue.TimeVal(Instant.parse("2026-08-07T10:33:57.418958Z")));
        trip(new DamlValue.TimeVal(Instant.EPOCH));
        trip(new DamlValue.TimeVal(Instant.ofEpochSecond(-1L, 999_999_000L)));
        trip(new DamlValue.TimeVal(Instant.ofEpochSecond(-86_400L)));
    }


    @Test
    void noneAndSomeStayApart() {
        trip(new DamlValue.Opt(null));
        trip(new DamlValue.Opt(new DamlValue.Text("day-to-day")));

        Message msgNone = ProtoValues.fromValue(new DamlValue.Opt(null),
                ValueOuterClass.Value.newBuilder());
        assertFalse(((ValueOuterClass.Value) msgNone).getOptional().hasValue(),
                "None must leave the payload unset");
    }


    /** Empty containers are not the same as absent ones and must survive. */
    @Test
    void emptyContainersRoundTrip() {
        trip(new DamlValue.Lst(List.of()));
        trip(new DamlValue.TextMap(List.of()));
        trip(new DamlValue.GenMap(List.of()));
        trip(new DamlValue.Rec(ID_ADDRESS, List.of()));
    }


    @Test
    void variantAndEnumKeepTheirIdentity() {
        trip(new DamlValue.EnumVal(ID_GRADE, "Gold"));
        trip(new DamlValue.Variant(ID_SHAPE, "Circle", new DamlValue.Int64(7L)));
    }


    /** A type id the caller does not have must not become three empty strings. */
    @Test
    void anAbsentTypeIdIsNotWritten() {
        Message msg = ProtoValues.fromRecord(
                new DamlValue.Rec(null, List.of(
                        new DamlValue.Rec.Field("label", new DamlValue.Text("x")))),
                ValueOuterClass.Record.newBuilder());

        assertFalse(((ValueOuterClass.Record) msg).hasRecordId(),
                "a null DataId must leave record_id unset, not name an empty package");
    }


    /** Entry ORDER is data on both map kinds; a re-ordering is a different value. */
    @Test
    void mapEntryOrderIsPreserved() {
        List<DamlValue.TextMap.Entry> lstText = new ArrayList<>();
        lstText.add(new DamlValue.TextMap.Entry("z", new DamlValue.Int64(1L)));
        lstText.add(new DamlValue.TextMap.Entry("a", new DamlValue.Int64(2L)));
        lstText.add(new DamlValue.TextMap.Entry("m", new DamlValue.Int64(3L)));
        trip(new DamlValue.TextMap(List.copyOf(lstText)));

        List<DamlValue.GenMap.Entry> lstGen = new ArrayList<>();
        lstGen.add(new DamlValue.GenMap.Entry(new DamlValue.Int64(9L),
                new DamlValue.Text("nine")));
        lstGen.add(new DamlValue.GenMap.Entry(new DamlValue.Party("p::1"),
                new DamlValue.Text("one")));
        trip(new DamlValue.GenMap(List.copyOf(lstGen)));
    }


    /**
     * The whole fixture shape in one value. A per-variant test passes on a
     * converter that cannot nest, and nesting is where a builder-based encoder
     * goes wrong.
     */
    @Test
    void theWholeAccountShapeRoundTrips() {
        trip(account());
    }


    /** A Record straight to a Record message, which is what a create carries. */
    @Test
    void aCreateArgumentRoundTripsAsARecord() {
        DamlValue.Rec rec = account();
        Message msg = ProtoValues.fromRecord(rec, ValueOuterClass.Record.newBuilder());
        assertEquals(rec, ProtoValues.toRecord(msg));
    }


    @Test
    void anIdentifierRoundTrips() {
        Message msg = ProtoValues.fromDataId(ID_ACCOUNT,
                ValueOuterClass.Identifier.newBuilder());
        assertEquals(ID_ACCOUNT, ProtoValues.toDataId(msg));
    }


    /**
     * There is no ledger value meaning "absent", so encoding null would have to
     * invent one. None is DamlValue.Opt(null) and says something different.
     */
    @Test
    void nullIsRefusedRatherThanEncoded() {
        assertThrows(LedgerException.class,
                () -> ProtoValues.fromValue(null, ValueOuterClass.Value.newBuilder()));
    }


    /**
     * Handing the encoder a builder that is not a Value must fail rather than
     * set an ordinary field of the same name and produce a message that
     * serialises cleanly and means nothing.
     */
    @Test
    void aBuilderThatIsNotAValueIsRefused() {
        LedgerException ex = assertThrows(LedgerException.class,
                () -> ProtoValues.fromValue(new DamlValue.Text("x"),
                        ValueOuterClass.Identifier.newBuilder()));

        assertTrue(ex.getMessage().contains("Identifier"), ex.getMessage());
    }


    private static DamlValue.Rec account() {
        List<DamlValue.Rec.Field> lstField = new ArrayList<>();
        lstField.add(fld("owner", new DamlValue.Party("alice::1220ab")));
        lstField.add(fld("bank", new DamlValue.Party("bank::1220cd")));
        lstField.add(fld("balance", new DamlValue.Decimal(new BigDecimal("1250.0"))));
        lstField.add(fld("label", new DamlValue.Text("primary")));
        lstField.add(fld("address", new DamlValue.Rec(ID_ADDRESS, List.of(
                fld("street", new DamlValue.Text("Rua Augusta 1")),
                fld("city", new DamlValue.Text("Lisboa")),
                fld("postcode", new DamlValue.Text("1100-053"))))));
        lstField.add(fld("grade", new DamlValue.EnumVal(ID_GRADE, "Gold")));
        lstField.add(fld("nickname", new DamlValue.Opt(new DamlValue.Text("day-to-day"))));
        lstField.add(fld("tags", new DamlValue.Lst(
                List.of(new DamlValue.Text("eur"), new DamlValue.Text("retail")))));
        lstField.add(fld("shape", new DamlValue.Variant(ID_SHAPE, "Circle",
                new DamlValue.Int64(7L))));
        lstField.add(fld("meta", new DamlValue.TextMap(List.of(
                new DamlValue.TextMap.Entry("k", new DamlValue.Text("v"))))));
        lstField.add(fld("pairs", new DamlValue.GenMap(List.of(
                new DamlValue.GenMap.Entry(new DamlValue.Int64(1L),
                        new DamlValue.Text("one"))))));
        lstField.add(fld("nested", new DamlValue.Lst(List.of(
                new DamlValue.Opt(new DamlValue.Lst(List.of(new DamlValue.Unit())))))));
        return new DamlValue.Rec(ID_ACCOUNT, List.copyOf(lstField));
    }


    private static DamlValue.Rec.Field fld(String nameField, DamlValue value) {
        return new DamlValue.Rec.Field(nameField, value);
    }

}
