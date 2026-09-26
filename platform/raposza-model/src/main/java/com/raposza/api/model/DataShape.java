// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.util.List;

/**
 * Declaration of a user-defined data type, resolved from a DamlType.Ref.
 *
 * Author Claude/bentzn
 */
public sealed interface DataShape {

    /** @return the type this shape declares */
    DataId idData();

    /**
     * Type parameters, in declaration order, empty for a ground type.
     *
     * Added on measurement: the fixture's "Box a" reports one parameter, and
     * without the names here the substitution needed to render a "Box Text"
     * field cannot be performed.
     *
     * @return the parameter names
     */
    List<String> lstParam();


    /**
     * @param idData the record type
     * @param lstField fields in declaration order
     */
    record Rec(DataId idData, List<String> lstParam, List<FieldInfo> lstField)
            implements DataShape {}


    /**
     * @param idData the variant type
     * @param lstCtor constructors in declaration order
     */
    record Variant(DataId idData, List<String> lstParam, List<FieldInfo> lstCtor)
            implements DataShape {}


    /**
     * @param idData the enum type
     * @param lstCtor constructor names in declaration order
     */
    record EnumShape(DataId idData, List<String> lstParam, List<String> lstCtor)
            implements DataShape {}

}
