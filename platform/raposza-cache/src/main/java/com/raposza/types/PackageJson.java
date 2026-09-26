// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.types;

import com.raposza.api.model.ChoiceControllers;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.DataShape;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

/**
 * The ObjectMapper used to persist decoded packages.
 *
 * DamlType and DataShape are sealed interfaces, so a type tag is needed to read
 * them back. The tag is applied through MIXINS rather than annotations on the
 * types themselves, because annotating them would put a Jackson dependency on
 * raposza-model - and that module taking no serialisation dependency is what
 * keeps a second Ledger API generation a module rather than a rewrite.
 *
 * Default typing is NOT used. It resolves arbitrary class names out of a file
 * on disk, which is a deserialisation gadget waiting to happen; an explicit
 * property with an explicit subtype list cannot name a class we did not list.
 *
 * Author Claude/bentzn
 */
public final class PackageJson {

    private PackageJson() {
    }


    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = DamlType.Prim.class, name = "prim"),
        @JsonSubTypes.Type(value = DamlType.Numeric.class, name = "numeric"),
        @JsonSubTypes.Type(value = DamlType.ListOf.class, name = "list"),
        @JsonSubTypes.Type(value = DamlType.OptionalOf.class, name = "optional"),
        @JsonSubTypes.Type(value = DamlType.TextMapOf.class, name = "textmap"),
        @JsonSubTypes.Type(value = DamlType.GenMapOf.class, name = "genmap"),
        @JsonSubTypes.Type(value = DamlType.Ref.class, name = "ref"),
        @JsonSubTypes.Type(value = DamlType.Var.class, name = "var"),
        @JsonSubTypes.Type(value = DamlType.App.class, name = "app")
    })
    abstract static class DamlTypeMixin {
    }


    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = ChoiceControllers.Parties.class, name = "parties"),
        @JsonSubTypes.Type(value = ChoiceControllers.Fields.class, name = "fields"),
        @JsonSubTypes.Type(value = ChoiceControllers.Unresolved.class, name = "unresolved")
    })
    abstract static class ChoiceControllersMixin {
    }


    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = DataShape.Rec.class, name = "record"),
        @JsonSubTypes.Type(value = DataShape.Variant.class, name = "variant"),
        @JsonSubTypes.Type(value = DataShape.EnumShape.class, name = "enum")
    })
    abstract static class DataShapeMixin {
    }


    /**
     * @return a mapper configured for the semantic model
     */
    public static ObjectMapper mapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.addMixIn(DamlType.class, DamlTypeMixin.class);
        mapper.addMixIn(DataShape.class, DataShapeMixin.class);
        mapper.addMixIn(ChoiceControllers.class, ChoiceControllersMixin.class);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

}
