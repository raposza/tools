// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lf;

import com.raposza.api.model.ChoiceControllers;
import com.raposza.api.model.ChoiceInfo;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.DataId;
import com.raposza.api.model.DataShape;
import com.raposza.api.model.FieldInfo;
import com.raposza.api.model.InterfaceInfo;
import com.raposza.api.model.PackageShape;
import com.raposza.api.model.TemplateInfo;
import com.digitalasset.daml.lf.data.Ref;
import com.digitalasset.daml.lf.language.Ast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import scala.Tuple2;

/**
 * One decoded Daml-LF package, expressed in the semantic model.
 *
 * Written against the SCHEMA decode, which erases every expression. That is
 * not a limitation being tolerated - it is the only decode whose output is the
 * same on both LF generations. The expression-carrying decode yields a
 * different shape per generation, and nothing above this file could use it
 * without branching on the generation, which is the one thing the design
 * forbids.
 *
 * So every ChoiceControllers here is Unresolved, with the reason stated. That
 * is honest rather than lossy: on a Canton 2.x archive the controllers are
 * genuinely absent from the decoded tree, and a caller must be able to say so.
 *
 * Only SERIALIZABLE data types are mapped. A non-serializable definition can
 * never appear in a contract payload or a choice argument, so skipping it is
 * scope rather than loss - and it is what lets an unmappable type throw
 * loudly instead of forcing this file to degrade quietly.
 *
 * Author Claude/bentzn
 */
public final class AstMapper {

    /** Stated on every choice, because the reason is the useful part. */
    static final String REASON_SCHEMA =
            "controller expressions are not carried by the schema decode";


    private AstMapper() {
    }


    /**
     * @param idPackage the package id, which is the archive's content hash
     * @param pkg the decoded package
     * @return the package in the semantic model
     */
    public static PackageShape map(String idPackage, Ast.GenPackage<?> pkg) {
        if (idPackage == null || idPackage.isEmpty())
            throw new IllegalArgumentException("package id is required");
        if (pkg == null)
            throw new IllegalArgumentException("decoded package is required");

        List<DataShape> lstShape = new ArrayList<>();
        List<InterfaceInfo> lstInterface = new ArrayList<>();
        List<TemplateInfo> lstTemplate = new ArrayList<>();

        // Interfaces first, and across every module, because a template maps
        // its inherited choices from them and may be declared before them.
        Map<DataId, List<ChoiceInfo>> mapChoiceByInterface = new LinkedHashMap<>();

        List<Object> lstModule = ScalaColl.list(pkg.modules());
        for (Object objModule : lstModule) {
            Ast.GenModule<?> module = module(objModule);
            String nameModule = Refs.dotted(module.name());

            for (Object objEntry : ScalaColl.list(module.interfaces())) {
                Tuple2<?, ?> entry = (Tuple2<?, ?>) objEntry;
                Ast.GenDefInterface<?> iface = (Ast.GenDefInterface<?>) entry._2();
                DataId idInterface = new DataId(idPackage, nameModule,
                        Refs.dotted((Ref.DottedName) entry._1()));

                List<ChoiceInfo> lstChoice = choices(iface.choices());
                mapChoiceByInterface.put(idInterface, lstChoice);
                lstInterface.add(new InterfaceInfo(idInterface, lstChoice,
                        requires(iface.requires()), viewOf(iface)));
            }
        }

        for (Object objModule : lstModule) {
            Ast.GenModule<?> module = module(objModule);
            String nameModule = Refs.dotted(module.name());

            for (Object objEntry : ScalaColl.list(module.definitions())) {
                Tuple2<?, ?> entry = (Tuple2<?, ?>) objEntry;
                if (!(entry._2() instanceof Ast.DDataType data))
                    continue;
                if (!data.serializable())
                    continue;

                DataId idData = new DataId(idPackage, nameModule,
                        Refs.dotted((Ref.DottedName) entry._1()));
                DataShape shape = shape(idData, data);
                if (shape != null)
                    lstShape.add(shape);
            }

            for (Object objEntry : ScalaColl.list(module.templates())) {
                Tuple2<?, ?> entry = (Tuple2<?, ?>) objEntry;
                Ast.GenTemplate<?> tpl = (Ast.GenTemplate<?>) entry._2();
                DataId idTemplate = new DataId(idPackage, nameModule,
                        Refs.dotted((Ref.DottedName) entry._1()));

                List<DataId> lstImplement = implementsOf(tpl);
                List<ChoiceInfo> lstChoice = ChoiceUnion.merge(choices(tpl.choices()),
                        lstImplement, mapChoiceByInterface);

                lstTemplate.add(new TemplateInfo(idTemplate, fieldsOfTemplate(idTemplate, lstShape),
                        lstChoice, lstImplement, keyOf(tpl)));
            }
        }

        return new PackageShape(idPackage, nameOf(pkg), versionOf(pkg),
                String.valueOf(pkg.languageVersion()), List.copyOf(lstTemplate),
                List.copyOf(lstInterface), List.copyOf(lstShape));
    }


    /**
     * A template's payload fields are those of the record that carries the
     * template's own name - the AST holds the two separately and the record is
     * an ordinary serializable data type.
     *
     * @param idTemplate the template
     * @param lstShape shapes mapped so far, from this package
     * @return the payload fields, empty when the record has not been seen
     */
    private static List<FieldInfo> fieldsOfTemplate(DataId idTemplate, List<DataShape> lstShape) {
        for (DataShape shape : lstShape) {
            if (!(shape instanceof DataShape.Rec rec))
                continue;
            if (rec.idData().equals(idTemplate))
                return rec.lstField();
        }
        return List.of();
    }


    /**
     * @param idData the identity to give the shape
     * @param data the decoded data type
     * @return record, variant or enum; null for a constructor this does not
     *         model, which cannot occur for a serializable type
     */
    private static DataShape shape(DataId idData, Ast.DDataType data) {
        List<String> lstParam = new ArrayList<>();
        for (Object objParam : ScalaColl.list(data.params())) {
            Tuple2<?, ?> pair = (Tuple2<?, ?>) objParam;
            lstParam.add(String.valueOf(pair._1()));
        }

        Ast.DataCons cons = data.cons();
        if (cons instanceof Ast.DataRecord rec)
            return new DataShape.Rec(idData, List.copyOf(lstParam), fields(rec.fields()));

        if (cons instanceof Ast.DataVariant variant) {
            return new DataShape.Variant(idData, List.copyOf(lstParam),
                    fields(variant.variants()));
        }

        if (cons instanceof Ast.DataEnum enumCons) {
            List<String> lstCtor = new ArrayList<>();
            for (Object objCtor : ScalaColl.list(enumCons.constructors())) {
                lstCtor.add(String.valueOf(objCtor));
            }
            return new DataShape.EnumShape(idData, List.copyOf(lstParam), List.copyOf(lstCtor));
        }
        return null;
    }


    /**
     * @param arr name-and-type pairs, as a record's fields or a variant's
     *            constructors
     * @return the same, in the semantic model
     */
    private static List<FieldInfo> fields(com.digitalasset.daml.lf.data.ImmArray<?> arr) {
        List<FieldInfo> lstField = new ArrayList<>();
        for (Object objField : ScalaColl.list(arr)) {
            Tuple2<?, ?> pair = (Tuple2<?, ?>) objField;
            lstField.add(new FieldInfo(String.valueOf(pair._1()),
                    TypeMapper.map((Ast.Type) pair._2())));
        }
        return List.copyOf(lstField);
    }


    /**
     * @param mapChoice a choice map from a template or an interface
     * @return the choices, in the order the map yields them
     */
    private static List<ChoiceInfo> choices(scala.collection.immutable.Map<?, ?> mapChoice) {
        List<ChoiceInfo> lstChoice = new ArrayList<>();
        for (Object objEntry : ScalaColl.list(mapChoice)) {
            Tuple2<?, ?> entry = (Tuple2<?, ?>) objEntry;
            Ast.GenTemplateChoice<?> ch = (Ast.GenTemplateChoice<?>) entry._2();

            lstChoice.add(new ChoiceInfo(ch.name(), ch.consuming(),
                    TypeMapper.map(ch.argBinder()._2()), TypeMapper.map(ch.returnType()),
                    new ChoiceControllers.Unresolved(REASON_SCHEMA), Optional.empty()));
        }
        return List.copyOf(lstChoice);
    }


    /**
     * @param setRequires the interfaces an interface requires
     * @return their identities
     */
    @SuppressWarnings("unchecked")
    private static List<DataId> requires(scala.collection.immutable.Set<?> setRequires) {
        List<DataId> lstRequires = new ArrayList<>();
        for (Object objRef : ScalaColl.list(setRequires)) {
            lstRequires.add(Refs.dataId((Ref.FullReference<String>) objRef));
        }
        return List.copyOf(lstRequires);
    }


    /**
     * @param iface the decoded interface
     * @return its view type, empty when it cannot be expressed - a view is not
     *         needed to exercise a choice, so an exotic one must not make the
     *         whole package undecodable
     */
    private static Optional<DamlType> viewOf(Ast.GenDefInterface<?> iface) {
        if (iface.view() == null)
            return Optional.empty();

        try {
            return Optional.of(TypeMapper.map(iface.view()));
        }
        catch (RuntimeException ex) {
            return Optional.empty();
        }
    }


    /**
     * GenTemplate.implements() is named with a Java keyword and cannot be
     * called from Java source at all. The bytecode member is public, so
     * reflection reaches it and nothing else does.
     *
     * @param tpl the decoded template
     * @return the interfaces it implements, in declaration order
     */
    @SuppressWarnings("unchecked")
    private static List<DataId> implementsOf(Ast.GenTemplate<?> tpl) {
        Object objMap;
        try {
            objMap = tpl.getClass().getMethod("implements").invoke(tpl);
        }
        catch (ReflectiveOperationException ex) {
            throw new com.raposza.api.LedgerException(
                    "the Daml-LF reader no longer exposes GenTemplate.implements", ex);
        }

        List<DataId> lstInterface = new ArrayList<>();
        for (Object objEntry : ScalaColl.list((scala.collection.Iterable<?>) objMap)) {
            Tuple2<?, ?> entry = (Tuple2<?, ?>) objEntry;
            lstInterface.add(Refs.dataId((Ref.FullReference<String>) entry._1()));
        }
        return List.copyOf(lstInterface);
    }


    /**
     * @param tpl the decoded template
     * @return the contract key type, empty when the template has no key
     */
    private static Optional<DamlType> keyOf(Ast.GenTemplate<?> tpl) {
        Object objKey = ScalaColl.orNull(tpl.key());
        if (objKey == null)
            return Optional.empty();

        Ast.GenTemplateKey<?> key = (Ast.GenTemplateKey<?>) objKey;
        return Optional.of(TypeMapper.map(key.typ()));
    }


    /**
     * Package name and version exist only from the LF versions that support
     * Smart Contract Upgrades. The accessors are declared on every version, so
     * an older archive is found out by calling them rather than by a version
     * comparison this file would have to keep current.
     *
     * @param pkg the decoded package
     * @return the package name, null when the archive carries none
     */
    private static String nameOf(Ast.GenPackage<?> pkg) {
        try {
            return pkg.pkgName();
        }
        catch (RuntimeException ex) {
            return null;
        }
    }


    /**
     * @param pkg the decoded package
     * @return the package version, null when the archive carries none
     */
    private static String versionOf(Ast.GenPackage<?> pkg) {
        try {
            Object objVersion = pkg.pkgVersion();
            return objVersion == null ? null : String.valueOf(objVersion);
        }
        catch (RuntimeException ex) {
            return null;
        }
    }


    /**
     * @param objModule a module map entry
     * @return the module it holds
     */
    private static Ast.GenModule<?> module(Object objModule) {
        Tuple2<?, ?> entry = (Tuple2<?, ?>) objModule;
        return (Ast.GenModule<?>) entry._2();
    }

}
