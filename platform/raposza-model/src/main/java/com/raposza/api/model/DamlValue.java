// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Dynamically typed ledger value. Mirrors DamlType. This is the wire-neutral
 * form: the v2 and v3 modules convert their own protobuf Value to and from it,
 * and nothing above the client layer sees a generated proto class.
 *
 * Author Claude/bentzn
 */
public sealed interface DamlValue {

    record Unit() implements DamlValue {}

    /** @param flag the boolean */
    record Bool(boolean flag) implements DamlValue {}

    /** @param num the integer */
    record Int64(long num) implements DamlValue {}

    /** @param num scale is carried by the declared type, not the value */
    record Decimal(BigDecimal num) implements DamlValue {}

    /** @param str the text */
    record Text(String str) implements DamlValue {}

    /** @param inst microsecond precision on the ledger */
    record TimeVal(Instant inst) implements DamlValue {}

    /** @param date the date */
    record DateVal(LocalDate date) implements DamlValue {}

    /** @param idParty the party id */
    record Party(String idParty) implements DamlValue {}

    /** @param idContract the contract id */
    record ContractRef(String idContract) implements DamlValue {}

    /**
     * @param idData the record type, may be null when the ledger omits it
     * @param lstField fields in declaration order
     */
    record Rec(DataId idData, List<Field> lstField) implements DamlValue {

        /**
         * @param nameField field name
         * @param value field value
         */
        public record Field(String nameField, DamlValue value) {}

    }

    /**
     * @param idData the variant type
     * @param nameCtor constructor name
     * @param value constructor payload
     */
    record Variant(DataId idData, String nameCtor, DamlValue value) implements DamlValue {}

    /**
     * @param idData the enum type
     * @param nameCtor constructor name
     */
    record EnumVal(DataId idData, String nameCtor) implements DamlValue {}

    /** @param lstElem elements */
    record Lst(List<DamlValue> lstElem) implements DamlValue {}

    /** @param value the Some payload, or null for None */
    record Opt(DamlValue value) implements DamlValue {}

    /** @param lstEntry entries in ledger order */
    record TextMap(List<Entry> lstEntry) implements DamlValue {

        /**
         * @param strKey the key
         * @param value the value
         */
        public record Entry(String strKey, DamlValue value) {}

    }

    /** @param lstEntry entries in ledger order */
    record GenMap(List<Entry> lstEntry) implements DamlValue {

        /**
         * @param key the key
         * @param value the value
         */
        public record Entry(DamlValue key, DamlValue value) {}

    }

}
