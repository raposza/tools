// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.wire;

import com.raposza.api.LedgerException;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts a Ledger API protobuf Value into the semantic model.
 *
 * This class deliberately does NOT reference a generated protobuf class. It
 * resolves every field through the descriptor carried by the message it is
 * handed, which means ONE implementation serves both Ledger API generations.
 *
 * That is not a stylistic choice. Measured against the two jars:
 *
 *   - the v1 and v2 Value oneof field NUMBERS are entirely different
 *     (record is 1 on v1 and 14 on v2, party is 11 and 7), so anything
 *     resolving by number would mis-decode silently on the other generation;
 *   - the field NAMES are identical on all sixteen variants except one:
 *     v1 names the text map "map", v2 names it "text_map". Both are accepted
 *     below and the difference is the only generation-specific line here;
 *   - the two binding jars share 306 class names and can never occupy one
 *     classpath, so a converter written against generated types would have to
 *     exist twice, in two modules that can never cross-test each other.
 *
 * Depends on protobuf-java only. No bindings artifact, by design: this must be
 * loadable inside whichever classloader holds the generation in use.
 *
 * An unrecognised variant FAILS. A placeholder would produce a contract that
 * renders as though it were complete, which is worse than no answer.
 *
 * <h2>Both directions live here  [ADDED 2026-08-07]</h2>
 *
 * fromValue is toValue run backwards and is in the same file for the reason
 * JsonCoercer sits beside JsonRenderer: the two are one specification of one
 * encoding, and two implementations of one specification drift. Here the drift
 * would be worse than in JSON, because the wire has no labels to disagree
 * about - a value that decodes correctly and re-encodes into a different oneof
 * is a submission the participant refuses for a reason nobody can see.
 *
 * The inverse takes a BUILDER rather than returning a generated type, for the
 * same reason the forward direction takes a Message: the caller owns the
 * generation, this class owns the mapping, and no bindings artifact is named
 * anywhere below.
 *
 * Author Claude/bentzn
 */
public final class ProtoValues {

    private static final long MICROS_PER_SECOND = 1_000_000L;
    private static final long NANOS_PER_MICRO = 1_000L;


    private ProtoValues() {
    }


    /**
     * @param msgValue a Ledger API Value message, either generation
     * @return the semantic value
     * @throws LedgerException when no variant is set, or the variant is not one
     *         this converter knows
     */
    public static DamlValue toValue(Message msgValue) {
        Descriptors.FieldDescriptor fldSum = sumField(msgValue);
        String nameVariant = fldSum.getName();

        switch (nameVariant) {
            case "unit":
                return new DamlValue.Unit();

            case "bool":
                return new DamlValue.Bool((Boolean) msgValue.getField(fldSum));

            case "int64":
                return new DamlValue.Int64((Long) msgValue.getField(fldSum));

            // Numeric arrives as a string and stays one until BigDecimal takes
            // it. Routing it through a double loses digits the ledger holds.
            case "numeric":
            case "decimal":
                return new DamlValue.Decimal(new BigDecimal((String) msgValue.getField(fldSum)));

            case "text":
                return new DamlValue.Text((String) msgValue.getField(fldSum));

            // Microseconds since epoch, not milliseconds.
            case "timestamp":
                return new DamlValue.TimeVal(toInstant((Long) msgValue.getField(fldSum)));

            case "date":
                return new DamlValue.DateVal(
                        LocalDate.ofEpochDay(((Integer) msgValue.getField(fldSum)).longValue()));

            case "party":
                return new DamlValue.Party((String) msgValue.getField(fldSum));

            case "contract_id":
                return new DamlValue.ContractRef((String) msgValue.getField(fldSum));

            case "record":
                return toRecord((Message) msgValue.getField(fldSum));

            case "variant":
                return toVariant((Message) msgValue.getField(fldSum));

            case "enum":
                return toEnum((Message) msgValue.getField(fldSum));

            case "list":
                return toList((Message) msgValue.getField(fldSum));

            case "optional":
                return toOptional((Message) msgValue.getField(fldSum));

            case "map":
            case "text_map":
                return toTextMap((Message) msgValue.getField(fldSum));

            case "gen_map":
                return toGenMap((Message) msgValue.getField(fldSum));

            default:
                throw new LedgerException("unhandled Value variant '" + nameVariant + "' on "
                        + msgValue.getDescriptorForType().getFullName()
                        + "; the payload cannot be rendered without inventing it");
        }
    }


    /**
     * @param msgRecord a Record message
     * @return the record, with field labels when the read set verbose
     */
    public static DamlValue.Rec toRecord(Message msgRecord) {
        DataId idData = optionalId(msgRecord, "record_id");

        Descriptors.FieldDescriptor fldFields = field(msgRecord, "fields");
        int cntField = msgRecord.getRepeatedFieldCount(fldFields);
        List<DamlValue.Rec.Field> lstField = new ArrayList<>(cntField);

        for (int cntLoop = 0; cntLoop < cntField; cntLoop++) {
            Message msgField = (Message) msgRecord.getRepeatedField(fldFields, cntLoop);
            // Empty when the read was not verbose. Left empty rather than
            // fabricated: a positional name would look like ledger metadata.
            String nameField = (String) msgField.getField(field(msgField, "label"));
            DamlValue value = toValue((Message) msgField.getField(field(msgField, "value")));
            lstField.add(new DamlValue.Rec.Field(nameField, value));
        }

        return new DamlValue.Rec(idData, List.copyOf(lstField));
    }


    /**
     * @param msgId an Identifier message
     * @return the identifier
     */
    public static DataId toDataId(Message msgId) {
        return new DataId((String) msgId.getField(field(msgId, "package_id")),
                (String) msgId.getField(field(msgId, "module_name")),
                (String) msgId.getField(field(msgId, "entity_name")));
    }


    /**
     * Semantic value to wire. The inverse of toValue, and asserted as a round
     * trip rather than against expected bytes.
     *
     * @param value the semantic value; null is refused rather than encoded as
     *              anything, because there is no ledger value meaning "absent" -
     *              None is DamlValue.Opt with a null payload, which is a
     *              different statement
     * @param bldValue an empty builder for the target generation's Value
     * @return the built message
     * @throws LedgerException when the value cannot be expressed on this
     *         generation's Value
     */
    public static Message fromValue(DamlValue value, Message.Builder bldValue) {
        if (value == null) {
            throw new LedgerException("null is not a ledger value; a missing Optional is"
                    + " DamlValue.Opt(null), which is a different thing");
        }

        Descriptors.Descriptor desc = bldValue.getDescriptorForType();

        switch (value) {
            case DamlValue.Unit ignored -> {
                // Unit is google.protobuf.Empty on the wire. Built from the
                // field rather than named, so this file still knows no types.
                Descriptors.FieldDescriptor fld = variant(desc, "unit");
                bldValue.setField(fld, bldValue.newBuilderForField(fld).build());
            }

            case DamlValue.Bool val -> bldValue.setField(variant(desc, "bool"), val.flag());

            case DamlValue.Int64 val -> bldValue.setField(variant(desc, "int64"), val.num());

            // toPlainString, not toString: scientific notation is not what the
            // ledger accepts, and the scale the caller holds is preserved.
            case DamlValue.Decimal val -> bldValue.setField(variant(desc, "numeric", "decimal"),
                    val.num().toPlainString());

            case DamlValue.Text val -> bldValue.setField(variant(desc, "text"), val.str());

            case DamlValue.TimeVal val -> bldValue.setField(variant(desc, "timestamp"),
                    toMicros(val.inst()));

            case DamlValue.DateVal val -> bldValue.setField(variant(desc, "date"),
                    (int) val.date().toEpochDay());

            case DamlValue.Party val -> bldValue.setField(variant(desc, "party"), val.idParty());

            case DamlValue.ContractRef val -> bldValue.setField(variant(desc, "contract_id"),
                    val.idContract());

            case DamlValue.Rec val -> {
                Descriptors.FieldDescriptor fld = variant(desc, "record");
                bldValue.setField(fld, fromRecord(val, bldValue.newBuilderForField(fld)));
            }

            case DamlValue.Variant val -> {
                Descriptors.FieldDescriptor fld = variant(desc, "variant");
                Message.Builder bld = bldValue.newBuilderForField(fld);
                Descriptors.Descriptor descVar = bld.getDescriptorForType();

                if (val.idData() != null) {
                    Descriptors.FieldDescriptor fldId = field(descVar, "variant_id");
                    bld.setField(fldId, fromDataId(val.idData(), bld.newBuilderForField(fldId)));
                }
                bld.setField(field(descVar, "constructor"), val.nameCtor());

                Descriptors.FieldDescriptor fldVal = field(descVar, "value");
                bld.setField(fldVal, fromValue(val.value(), bld.newBuilderForField(fldVal)));
                bldValue.setField(fld, bld.build());
            }

            case DamlValue.EnumVal val -> {
                Descriptors.FieldDescriptor fld = variant(desc, "enum");
                Message.Builder bld = bldValue.newBuilderForField(fld);
                Descriptors.Descriptor descEnum = bld.getDescriptorForType();

                if (val.idData() != null) {
                    Descriptors.FieldDescriptor fldId = field(descEnum, "enum_id");
                    bld.setField(fldId, fromDataId(val.idData(), bld.newBuilderForField(fldId)));
                }
                bld.setField(field(descEnum, "constructor"), val.nameCtor());
                bldValue.setField(fld, bld.build());
            }

            case DamlValue.Lst val -> {
                Descriptors.FieldDescriptor fld = variant(desc, "list");
                Message.Builder bld = bldValue.newBuilderForField(fld);
                Descriptors.FieldDescriptor fldElem = field(bld.getDescriptorForType(),
                        "elements");

                for (DamlValue elem : val.lstElem()) {
                    bld.addRepeatedField(fldElem, fromValue(elem, bld.newBuilderForField(fldElem)));
                }
                bldValue.setField(fld, bld.build());
            }

            // None leaves the payload UNSET. There is no Some-of-nothing, so
            // the absence is unambiguous - the same argument toOptional makes
            // reading it.
            case DamlValue.Opt val -> {
                Descriptors.FieldDescriptor fld = variant(desc, "optional");
                Message.Builder bld = bldValue.newBuilderForField(fld);

                if (val.value() != null) {
                    Descriptors.FieldDescriptor fldVal = field(bld.getDescriptorForType(),
                            "value");
                    bld.setField(fldVal, fromValue(val.value(), bld.newBuilderForField(fldVal)));
                }
                bldValue.setField(fld, bld.build());
            }

            case DamlValue.TextMap val -> {
                Descriptors.FieldDescriptor fld = variant(desc, "text_map", "map");
                Message.Builder bld = bldValue.newBuilderForField(fld);
                Descriptors.FieldDescriptor fldEntry = field(bld.getDescriptorForType(),
                        "entries");

                for (DamlValue.TextMap.Entry ent : val.lstEntry()) {
                    Message.Builder bldEntry = bld.newBuilderForField(fldEntry);
                    Descriptors.Descriptor descEntry = bldEntry.getDescriptorForType();
                    bldEntry.setField(field(descEntry, "key"), ent.strKey());

                    Descriptors.FieldDescriptor fldVal = field(descEntry, "value");
                    bldEntry.setField(fldVal,
                            fromValue(ent.value(), bldEntry.newBuilderForField(fldVal)));
                    bld.addRepeatedField(fldEntry, bldEntry.build());
                }
                bldValue.setField(fld, bld.build());
            }

            case DamlValue.GenMap val -> {
                Descriptors.FieldDescriptor fld = variant(desc, "gen_map");
                Message.Builder bld = bldValue.newBuilderForField(fld);
                Descriptors.FieldDescriptor fldEntry = field(bld.getDescriptorForType(),
                        "entries");

                for (DamlValue.GenMap.Entry ent : val.lstEntry()) {
                    Message.Builder bldEntry = bld.newBuilderForField(fldEntry);
                    Descriptors.Descriptor descEntry = bldEntry.getDescriptorForType();

                    Descriptors.FieldDescriptor fldKey = field(descEntry, "key");
                    bldEntry.setField(fldKey,
                            fromValue(ent.key(), bldEntry.newBuilderForField(fldKey)));

                    Descriptors.FieldDescriptor fldVal = field(descEntry, "value");
                    bldEntry.setField(fldVal,
                            fromValue(ent.value(), bldEntry.newBuilderForField(fldVal)));
                    bld.addRepeatedField(fldEntry, bldEntry.build());
                }
                bldValue.setField(fld, bld.build());
            }
        }

        return bldValue.build();
    }


    /**
     * A record straight into a Record message, which is what a create argument
     * is - not a Value wrapping one.
     *
     * The type id is written only when the caller HAS one. A create does not
     * need it and a null would encode as a three-empty-string Identifier,
     * which names a package that does not exist.
     *
     * @param rec the record
     * @param bldRecord an empty builder for the target generation's Record
     * @return the built message
     */
    public static Message fromRecord(DamlValue.Rec rec, Message.Builder bldRecord) {
        if (rec == null)
            throw new LedgerException("null is not a record");

        Descriptors.Descriptor desc = bldRecord.getDescriptorForType();

        if (rec.idData() != null) {
            Descriptors.FieldDescriptor fldId = field(desc, "record_id");
            bldRecord.setField(fldId,
                    fromDataId(rec.idData(), bldRecord.newBuilderForField(fldId)));
        }

        Descriptors.FieldDescriptor fldFields = field(desc, "fields");
        for (DamlValue.Rec.Field fld : rec.lstField()) {
            Message.Builder bldField = bldRecord.newBuilderForField(fldFields);
            Descriptors.Descriptor descField = bldField.getDescriptorForType();

            // An empty label is what a non-verbose read produced, and an empty
            // proto3 string is not serialised, so this is the same statement
            // on the wire as omitting it.
            bldField.setField(field(descField, "label"),
                    fld.nameField() == null ? "" : fld.nameField());

            Descriptors.FieldDescriptor fldValue = field(descField, "value");
            bldField.setField(fldValue,
                    fromValue(fld.value(), bldField.newBuilderForField(fldValue)));
            bldRecord.addRepeatedField(fldFields, bldField.build());
        }

        return bldRecord.build();
    }


    /**
     * @param idData the identifier
     * @param bldId an empty builder for the target generation's Identifier
     * @return the built message
     */
    public static Message fromDataId(DataId idData, Message.Builder bldId) {
        if (idData == null)
            throw new LedgerException("null is not an identifier");

        Descriptors.Descriptor desc = bldId.getDescriptorForType();
        bldId.setField(field(desc, "package_id"), idData.idPackage());
        bldId.setField(field(desc, "module_name"), idData.nameModule());
        bldId.setField(field(desc, "entity_name"), idData.nameEntity());
        return bldId.build();
    }


    private static DamlValue toVariant(Message msgVariant) {
        return new DamlValue.Variant(optionalId(msgVariant, "variant_id"),
                (String) msgVariant.getField(field(msgVariant, "constructor")),
                toValue((Message) msgVariant.getField(field(msgVariant, "value"))));
    }


    private static DamlValue toEnum(Message msgEnum) {
        return new DamlValue.EnumVal(optionalId(msgEnum, "enum_id"),
                (String) msgEnum.getField(field(msgEnum, "constructor")));
    }


    private static DamlValue toList(Message msgList) {
        Descriptors.FieldDescriptor fldElem = field(msgList, "elements");
        int cntElem = msgList.getRepeatedFieldCount(fldElem);
        List<DamlValue> lstElem = new ArrayList<>(cntElem);

        for (int cntLoop = 0; cntLoop < cntElem; cntLoop++) {
            lstElem.add(toValue((Message) msgList.getRepeatedField(fldElem, cntLoop)));
        }
        return new DamlValue.Lst(List.copyOf(lstElem));
    }


    /**
     * None is an Optional message with no value set, which is indistinguishable
     * on the wire from Some of nothing - there is no such thing, so the absence
     * is unambiguous.
     */
    private static DamlValue toOptional(Message msgOpt) {
        Descriptors.FieldDescriptor fldValue = field(msgOpt, "value");
        if (!msgOpt.hasField(fldValue))
            return new DamlValue.Opt(null);
        return new DamlValue.Opt(toValue((Message) msgOpt.getField(fldValue)));
    }


    private static DamlValue toTextMap(Message msgMap) {
        Descriptors.FieldDescriptor fldEntry = field(msgMap, "entries");
        int cntEntry = msgMap.getRepeatedFieldCount(fldEntry);
        List<DamlValue.TextMap.Entry> lstEntry = new ArrayList<>(cntEntry);

        for (int cntLoop = 0; cntLoop < cntEntry; cntLoop++) {
            Message msgEntry = (Message) msgMap.getRepeatedField(fldEntry, cntLoop);
            lstEntry.add(new DamlValue.TextMap.Entry(
                    (String) msgEntry.getField(field(msgEntry, "key")),
                    toValue((Message) msgEntry.getField(field(msgEntry, "value")))));
        }
        return new DamlValue.TextMap(List.copyOf(lstEntry));
    }


    private static DamlValue toGenMap(Message msgMap) {
        Descriptors.FieldDescriptor fldEntry = field(msgMap, "entries");
        int cntEntry = msgMap.getRepeatedFieldCount(fldEntry);
        List<DamlValue.GenMap.Entry> lstEntry = new ArrayList<>(cntEntry);

        for (int cntLoop = 0; cntLoop < cntEntry; cntLoop++) {
            Message msgEntry = (Message) msgMap.getRepeatedField(fldEntry, cntLoop);
            lstEntry.add(new DamlValue.GenMap.Entry(
                    toValue((Message) msgEntry.getField(field(msgEntry, "key"))),
                    toValue((Message) msgEntry.getField(field(msgEntry, "value")))));
        }
        return new DamlValue.GenMap(List.copyOf(lstEntry));
    }


    /**
     * Type identifiers are present only when the read set verbose. Null is the
     * honest answer when they are absent; DamlValue.Rec documents that.
     */
    private static DataId optionalId(Message msg, String nameField) {
        Descriptors.FieldDescriptor fld = msg.getDescriptorForType().findFieldByName(nameField);
        if (fld == null || !msg.hasField(fld))
            return null;
        return toDataId((Message) msg.getField(fld));
    }


    /**
     * The single real oneof on a Value. Looked up positionally rather than by
     * name because the oneof is named "Sum" on v1 and nothing guarantees the
     * casing survives a generation change; the field names inside it do the
     * discriminating work and those ARE verified.
     *
     * getRealOneofs excludes the synthetic oneofs proto3 creates for optional
     * scalar fields, which would otherwise be counted here.
     */
    private static Descriptors.FieldDescriptor sumField(Message msgValue) {
        List<Descriptors.OneofDescriptor> lstOneof = msgValue.getDescriptorForType().getRealOneofs();
        if (lstOneof.size() != 1) {
            throw new LedgerException("expected exactly one oneof on "
                    + msgValue.getDescriptorForType().getFullName() + ", found " + lstOneof.size()
                    + " - this is not a Ledger API Value message");
        }

        Descriptors.FieldDescriptor fldSum = msgValue.getOneofFieldDescriptor(lstOneof.get(0));
        if (fldSum == null) {
            throw new LedgerException("Value message with no variant set on "
                    + msgValue.getDescriptorForType().getFullName()
                    + "; an empty value is not a valid ledger value and must not be"
                    + " rendered as one");
        }
        return fldSum;
    }


    /**
     * Fails naming the field and what the message actually carries, so a
     * generation that renames something says so instead of yielding a default.
     */
    private static Descriptors.FieldDescriptor field(Message msg, String nameField) {
        return field(msg.getDescriptorForType(), nameField);
    }


    private static Descriptors.FieldDescriptor field(Descriptors.Descriptor desc,
            String nameField) {
        Descriptors.FieldDescriptor fld = desc.findFieldByName(nameField);
        if (fld == null) {
            List<String> lstName = new ArrayList<>();
            for (Descriptors.FieldDescriptor fldEach : desc.getFields()) {
                lstName.add(fldEach.getName());
            }
            throw new LedgerException("message " + desc.getFullName() + " has no field '"
                    + nameField + "'; it carries " + lstName);
        }
        return fld;
    }


    /**
     * The first candidate variant name this generation actually has, and the
     * write-side mirror of the two-name cases in toValue: v1 names the text map
     * "map" where v2 names it "text_map", and "decimal" was renamed "numeric".
     *
     * The chosen field must be inside a REAL oneof. Without that check, handing
     * this method a builder for something that is not a Value would set an
     * ordinary field of the same name and produce a message that serialises
     * cleanly and means nothing.
     *
     * @param desc the Value descriptor
     * @param arrName candidate names, most current first
     * @return the variant field
     */
    private static Descriptors.FieldDescriptor variant(Descriptors.Descriptor desc,
            String... arrName) {
        for (String nameField : arrName) {
            Descriptors.FieldDescriptor fld = desc.findFieldByName(nameField);
            if (fld == null)
                continue;

            if (fld.getRealContainingOneof() == null) {
                throw new LedgerException("field '" + nameField + "' on " + desc.getFullName()
                        + " is not part of a variant oneof; this is not a Ledger API Value"
                        + " message");
            }
            return fld;
        }

        List<String> lstName = new ArrayList<>();
        for (Descriptors.FieldDescriptor fldEach : desc.getFields()) {
            lstName.add(fldEach.getName());
        }
        throw new LedgerException(desc.getFullName() + " has none of the variants "
                + List.of(arrName) + "; it carries " + lstName);
    }


    /**
     * The inverse of toInstant, and it round trips across the epoch: a value of
     * -1 micro is second -1 plus 999999 micros there, and comes back to -1
     * here because getNano is always non-negative.
     */
    private static long toMicros(Instant inst) {
        return Math.multiplyExact(inst.getEpochSecond(), MICROS_PER_SECOND)
                + inst.getNano() / NANOS_PER_MICRO;
    }


    private static Instant toInstant(long micros) {
        long numSecond = Math.floorDiv(micros, MICROS_PER_SECOND);
        long numMicroRest = Math.floorMod(micros, MICROS_PER_SECOND);
        return Instant.ofEpochSecond(numSecond, numMicroRest * NANOS_PER_MICRO);
    }

}
