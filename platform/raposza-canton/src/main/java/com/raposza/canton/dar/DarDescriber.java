// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.dar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.raposza.api.model.ChoiceInfo;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.DataId;
import com.raposza.api.model.DataShape;
import com.raposza.api.model.FieldInfo;
import com.raposza.api.model.InterfaceInfo;
import com.raposza.api.model.PackageShape;
import com.raposza.api.model.PrimKind;
import com.raposza.api.model.TemplateInfo;
import com.raposza.spi.LfDecoder_i;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Every template, choice, field and data type in a set of DARs, as one JSON
 * document - written for a reader that has never seen the DARs and has to
 * write CaQL against them.
 *
 * <h2>What is described in full, and what only as referenced</h2>
 *
 * The MAIN package of each DAR is described in full. The other packages a DAR
 * carries - its dependencies, `daml-stdlib` and `daml-prim` among them - are
 * decoded so that every type can be resolved, and from them only what a main
 * package REFERENCES is described: a record a choice takes, an interface a
 * template implements, a variant inside either, followed transitively. A
 * package carried by several DARs is decoded and described once.
 *
 * <h2>Types are spelled the way CaQL names a template</h2>
 *
 * `Module:Entity`, and `packageId:Module:Entity` where two packages in the set
 * carry the same module and entity name. Builtins as a Daml developer writes
 * them - `Party`, `Text`, `Time`, `ContractId Module:Entity`, `[Party]`,
 * `Optional (ContractId Module:Entity)`; a compound argument is parenthesised.
 * Every template and interface also carries `ref`, the exact name to write.
 *
 * <h2>What is not known is null, never absent</h2>
 *
 * A reader that sees a missing key invents one. Controllers are not carried by
 * the schema decode on every LF generation, so the document says so once at
 * the top rather than per choice.
 *
 * <h2>Nothing is thrown for a bad input</h2>
 *
 * An unreadable DAR, an entry without a matching payload, or a package the
 * decoder refuses - an LF version newer than its reader - lands in `refused`
 * with the file, the entry and the reason, and every other package is still
 * described.
 *
 * Author Claude/bentzn
 */
public final class DarDescriber {

    /** The version of the document's shape. */
    public static final int N_SCHEMA = 1;

    private static final String STR_SPELLING = "Module:Entity, or packageId:Module:Entity where two"
            + " packages in this set carry the same module and entity name - the way CaQL names"
            + " a template";

    private static final String STR_CONTROLLERS = "not carried: the schema decode does not"
            + " include choice controllers on every Daml-LF generation";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** What the reader names an LF 1 package that carries no metadata. */
    static final String STR_NO_METADATA = "-no-package-metadata";

    private final LfDecoder_i decoder;

    private final String strReader;


    /**
     * One DAR to describe.
     *
     * @param fileDar the DAR file
     * @param strOrigin where it comes from, e.g. `user` or `splice`
     * @param strStatus its state, e.g. `selected` or `uploaded`
     */
    public record Input(Path fileDar, String strOrigin, String strStatus) {
    }


    /** What is gathered about one package before anything is written. */
    private static final class Pkg {

        private final String strPkgId;

        private final PackageShape shape;

        private final String strLf;

        private final List<String> lstFile = new ArrayList<>();

        private final Set<String> setDep = new LinkedHashSet<>();

        private String strOrigin;

        private String strStatus;

        private boolean flagMain;

        private int nOrder;


        Pkg(String strPkgId, PackageShape shape, String strLf) {
            this.strPkgId = strPkgId;
            this.shape = shape;
            this.strLf = strLf;
        }
    }


    /**
     * @param decoder the Daml-LF decoder - from the build, or through
     *        `LfReaderLoader` from an installed Canton
     * @param strReader what the decoder's reader is, for the document - e.g.
     *        `canton-open-source 3.5.19`
     */
    public DarDescriber(LfDecoder_i decoder, String strReader) {
        if (decoder == null)
            throw new IllegalArgumentException("a decoder is required");
        this.decoder = decoder;
        this.strReader = strReader;
    }


    /**
     * @param strTopology `sandbox` or `localnet`, as discovery spells it; may
     *        be null
     * @param lstInput the DARs, in the order they are to be listed
     * @return the description
     */
    public ObjectNode describe(String strTopology, List<Input> lstInput) {
        ObjectNode nodeRoot = MAPPER.createObjectNode();
        nodeRoot.put("schema", N_SCHEMA);
        putText(nodeRoot, "topology", strTopology);
        putText(nodeRoot, "reader", strReader);
        nodeRoot.put("typeSpelling", STR_SPELLING);
        nodeRoot.putNull("controllers");
        nodeRoot.put("controllersNote", STR_CONTROLLERS);
        ArrayNode nodePackages = nodeRoot.putArray("packages");
        ArrayNode nodeRefused = nodeRoot.putArray("refused");

        Map<String, Pkg> mapPkg = new LinkedHashMap<>();
        for (Input input : lstInput == null ? List.<Input>of() : lstInput) {
            gather(input, mapPkg, nodeRefused);
        }

        Index index = new Index(mapPkg.values());
        Map<String, Set<DataId>> mapNeeded = needed(mapPkg, index);

        List<Pkg> lstMain = new ArrayList<>();
        List<Pkg> lstDep = new ArrayList<>();
        for (Pkg pkg : mapPkg.values()) {
            if (pkg.flagMain)
                lstMain.add(pkg);
            else if (mapNeeded.containsKey(pkg.strPkgId))
                lstDep.add(pkg);
        }
        lstMain.sort(Comparator.comparingInt(pkg -> pkg.nOrder));
        lstDep.sort(Comparator.comparing((Pkg pkg) -> String.valueOf(pkg.shape.namePackage()))
                .thenComparing(pkg -> pkg.strPkgId));

        for (Pkg pkg : lstMain) {
            writePackage(nodePackages.addObject(), pkg, null, mapPkg, index);
        }
        for (Pkg pkg : lstDep) {
            writePackage(nodePackages.addObject(), pkg, mapNeeded.get(pkg.strPkgId), mapPkg, index);
        }
        return nodeRoot;
    }


    /**
     * @param node a description
     * @return it as indented JSON
     */
    public static String strJson(ObjectNode node) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("the description could not be written", ex);
        }
    }


    private void gather(Input input, Map<String, Pkg> mapPkg, ArrayNode nodeRefused) {
        Path fileDar = input.fileDar();
        String strMainId;
        List<DalfArchive.Dalf> lstDalf;
        try {
            strMainId = DarCatalog.inspect(fileDar).strPkgId();
            lstDalf = DalfArchive.readAll(fileDar);
        }
        catch (IOException | RuntimeException ex) {
            refuse(nodeRefused, fileDar, null, null, null, "unreadable DAR: " + ex.getMessage());
            return;
        }

        // The main package first, so a refusal of it is reported before its
        // dependencies and its origin wins over any DAR that carries it as a
        // dependency.
        List<DalfArchive.Dalf> lstOrdered = new ArrayList<>(lstDalf);
        lstOrdered.sort(Comparator.comparing((DalfArchive.Dalf dalf) -> !strMainId.equals(dalf.strPkgId())));

        Set<String> setInDar = new LinkedHashSet<>();
        for (DalfArchive.Dalf dalf : lstOrdered) {
            if (dalf.strPkgId() != null)
                setInDar.add(dalf.strPkgId());
        }

        for (DalfArchive.Dalf dalf : lstOrdered) {
            if (dalf.strPkgId() == null) {
                refuse(nodeRefused, fileDar, dalf.strEntry(), null, dalf.strLf(),
                        "no payload whose SHA-256 the entry names");
                continue;
            }

            Pkg pkg = mapPkg.get(dalf.strPkgId());
            if (pkg == null) {
                PackageShape shape;
                try {
                    shape = decoder.decode(dalf.arrPayload());
                }
                catch (RuntimeException | LinkageError ex) {
                    refuse(nodeRefused, fileDar, dalf.strEntry(), dalf.strPkgId(), dalf.strLf(),
                            chainOf(ex));
                    continue;
                }
                pkg = new Pkg(dalf.strPkgId(), shape, dalf.strLf());
                mapPkg.put(dalf.strPkgId(), pkg);
            }

            String strFile = fileDar.toAbsolutePath().normalize().toString();
            if (!pkg.lstFile.contains(strFile))
                pkg.lstFile.add(strFile);

            if (dalf.strPkgId().equals(strMainId)) {
                if (!pkg.flagMain)
                    pkg.nOrder = cntMain(mapPkg);
                pkg.flagMain = true;
                pkg.strOrigin = input.strOrigin();
                pkg.strStatus = input.strStatus();
                for (String strDep : setInDar) {
                    if (!strDep.equals(strMainId))
                        pkg.setDep.add(strDep);
                }
            }
            else if (pkg.strOrigin == null) {
                pkg.strOrigin = input.strOrigin();
                pkg.strStatus = input.strStatus();
            }
        }
    }


    /** @return how many packages are a main package so far - the next one's place */
    private static int cntMain(Map<String, Pkg> mapPkg) {
        int cnt = 0;
        for (Pkg pkg : mapPkg.values()) {
            if (pkg.flagMain)
                cnt++;
        }
        return cnt;
    }


    /**
     * What each non-main package must describe: everything a main package
     * references, followed through records, variants, interfaces and
     * templates until nothing new turns up.
     */
    private static Map<String, Set<DataId>> needed(Map<String, Pkg> mapPkg, Index index) {
        Set<String> setMain = new HashSet<>();
        for (Pkg pkg : mapPkg.values()) {
            if (pkg.flagMain)
                setMain.add(pkg.strPkgId);
        }

        Deque<DataId> deqTodo = new ArrayDeque<>();
        for (Pkg pkg : mapPkg.values()) {
            if (pkg.flagMain)
                deqTodo.addAll(refsOfPackage(pkg.shape));
        }

        Set<DataId> setSeen = new HashSet<>();
        Map<String, Set<DataId>> mapNeeded = new HashMap<>();
        while (!deqTodo.isEmpty()) {
            DataId id = deqTodo.poll();
            if (!setSeen.add(id) || setMain.contains(id.idPackage()) || !mapPkg.containsKey(id.idPackage()))
                continue;
            mapNeeded.computeIfAbsent(id.idPackage(), strKey -> new LinkedHashSet<>()).add(id);
            deqTodo.addAll(index.refsOf(id));
        }
        return mapNeeded;
    }


    private static List<DataId> refsOfPackage(PackageShape shape) {
        List<DataId> lstRef = new ArrayList<>();
        for (TemplateInfo template : shape.lstTemplate()) {
            lstRef.addAll(refsOfTemplate(template));
        }
        for (InterfaceInfo iface : shape.lstInterface()) {
            lstRef.addAll(refsOfInterface(iface));
        }
        for (DataShape data : shape.lstShape()) {
            lstRef.addAll(refsOfShape(data));
        }
        return lstRef;
    }


    private static List<DataId> refsOfTemplate(TemplateInfo template) {
        List<DataId> lstRef = new ArrayList<>(template.lstInterface());
        for (FieldInfo field : template.lstField()) {
            collect(field.type(), lstRef);
        }
        template.typeKey().ifPresent(type -> collect(type, lstRef));
        for (ChoiceInfo choice : template.lstChoice()) {
            collect(choice.typeArg(), lstRef);
            collect(choice.typeReturn(), lstRef);
        }
        return lstRef;
    }


    private static List<DataId> refsOfInterface(InterfaceInfo iface) {
        List<DataId> lstRef = new ArrayList<>(iface.lstRequires());
        iface.typeView().ifPresent(type -> collect(type, lstRef));
        for (ChoiceInfo choice : iface.lstChoice()) {
            collect(choice.typeArg(), lstRef);
            collect(choice.typeReturn(), lstRef);
        }
        return lstRef;
    }


    private static List<DataId> refsOfShape(DataShape data) {
        List<DataId> lstRef = new ArrayList<>();
        if (data instanceof DataShape.Rec rec) {
            for (FieldInfo field : rec.lstField()) {
                collect(field.type(), lstRef);
            }
        }
        else if (data instanceof DataShape.Variant variant) {
            for (FieldInfo field : variant.lstCtor()) {
                collect(field.type(), lstRef);
            }
        }
        return lstRef;
    }


    private static void collect(DamlType type, List<DataId> lstRef) {
        if (type == null)
            return;
        switch (type) {
            case DamlType.Ref ref -> lstRef.add(ref.idData());
            case DamlType.Prim prim -> collect(prim.typeArg(), lstRef);
            case DamlType.ListOf list -> collect(list.typeElem(), lstRef);
            case DamlType.OptionalOf opt -> collect(opt.typeElem(), lstRef);
            case DamlType.TextMapOf map -> collect(map.typeValue(), lstRef);
            case DamlType.GenMapOf map -> {
                collect(map.typeKey(), lstRef);
                collect(map.typeValue(), lstRef);
            }
            case DamlType.App app -> {
                collect(app.typeFun(), lstRef);
                for (DamlType typeArg : app.lstArg()) {
                    collect(typeArg, lstRef);
                }
            }
            default -> {
            }
        }
    }


    /**
     * @param setOnly the entities to write, or null for everything the
     *        package declares
     */
    private void writePackage(ObjectNode node, Pkg pkg, Set<DataId> setOnly, Map<String, Pkg> mapPkg,
            Index index) {
        PackageShape shape = pkg.shape;
        node.put("packageId", pkg.strPkgId);
        putText(node, "name", strNameOf(shape));
        putText(node, "version", strNameOf(shape) == null ? null : shape.versionPackage());
        putText(node, "lf", pkg.strLf);
        node.put("role", pkg.flagMain ? "main" : "dependency");
        node.put("described", setOnly == null ? "full" : "referenced");
        putText(node, "origin", pkg.strOrigin);
        putText(node, "status", pkg.strStatus);
        ArrayNode nodeFiles = node.putArray("files");
        for (String strFile : pkg.lstFile) {
            nodeFiles.add(strFile);
        }
        if (pkg.flagMain) {
            ArrayNode nodeDeps = node.putArray("dependencies");
            for (String strDep : pkg.setDep) {
                ObjectNode nodeDep = nodeDeps.addObject();
                Pkg pkgDep = mapPkg.get(strDep);
                putText(nodeDep, "name", pkgDep == null ? null : strNameOf(pkgDep.shape));
                nodeDep.put("packageId", strDep);
            }
        }
        else {
            node.putNull("dependencies");
        }

        // What a template's own payload record and its choices' argument
        // records say is written inline where they are used; listing them
        // again as records doubles the document for nothing.
        Set<DataId> setInline = new HashSet<>();
        if (setOnly == null) {
            for (TemplateInfo template : shape.lstTemplate()) {
                setInline.add(template.idTemplate());
                for (ChoiceInfo choice : template.lstChoice()) {
                    if (choice.typeArg() instanceof DamlType.Ref ref
                            && ref.idData().idPackage().equals(pkg.strPkgId))
                        setInline.add(ref.idData());
                }
            }
        }

        Map<String, ObjectNode> mapModule = new TreeMap<>();
        for (TemplateInfo template : sortedTemplates(shape)) {
            if (setOnly == null || setOnly.contains(template.idTemplate()))
                writeTemplate(arrayIn(mapModule, template.idTemplate(), "templates"), template, index);
        }
        for (InterfaceInfo iface : sortedInterfaces(shape)) {
            if (setOnly == null || setOnly.contains(iface.idInterface()))
                writeInterface(arrayIn(mapModule, iface.idInterface(), "interfaces"), iface, index);
        }
        for (DataShape data : sortedShapes(shape)) {
            boolean flagWanted = setOnly == null ? !setInline.contains(data.idData())
                    : setOnly.contains(data.idData()) && index.template(data.idData()) == null;
            if (flagWanted)
                writeShape(mapModule, data, index);
        }

        ArrayNode nodeModules = node.putArray("modules");
        for (ObjectNode nodeModule : mapModule.values()) {
            nodeModules.add(nodeModule);
        }
    }


    /**
     * The package's name, or null when it has none. An LF 1 package without
     * metadata comes back from the reader named `-no-package-metadata` at
     * version `0` - measured, Canton 3.5.19's reader on a 2.x Aviation DAR -
     * and a placeholder that reads like a name is worse than no name.
     */
    private static String strNameOf(PackageShape shape) {
        String strName = shape.namePackage();
        return strName == null || STR_NO_METADATA.equals(strName) ? null : strName;
    }


    private static ArrayNode arrayIn(Map<String, ObjectNode> mapModule, DataId id, String strKind) {
        ObjectNode nodeModule = mapModule.computeIfAbsent(id.nameModule(), strModule -> {
            ObjectNode nodeNew = MAPPER.createObjectNode();
            nodeNew.put("name", strModule);
            nodeNew.putArray("templates");
            nodeNew.putArray("interfaces");
            nodeNew.putArray("records");
            nodeNew.putArray("variants");
            nodeNew.putArray("enums");
            return nodeNew;
        });
        return (ArrayNode) nodeModule.get(strKind);
    }


    private static void writeTemplate(ArrayNode nodeArr, TemplateInfo template, Index index) {
        ObjectNode node = nodeArr.addObject();
        node.put("name", template.idTemplate().nameEntity());
        node.put("ref", index.strRef(template.idTemplate()));
        writeFields(node.putArray("fields"), template.lstField(), index);
        putText(node, "key", template.typeKey().map(index::strType).orElse(null));
        ArrayNode nodeImpl = node.putArray("implements");
        for (DataId idIface : template.lstInterface()) {
            nodeImpl.add(index.strRef(idIface));
        }
        writeChoices(node.putArray("choices"), template.lstChoice(), index);
    }


    private static void writeInterface(ArrayNode nodeArr, InterfaceInfo iface, Index index) {
        ObjectNode node = nodeArr.addObject();
        node.put("name", iface.idInterface().nameEntity());
        node.put("ref", index.strRef(iface.idInterface()));
        putText(node, "viewType", iface.typeView().map(index::strType).orElse(null));
        ArrayNode nodeReq = node.putArray("requires");
        for (DataId idReq : iface.lstRequires()) {
            nodeReq.add(index.strRef(idReq));
        }
        writeChoices(node.putArray("choices"), iface.lstChoice(), index);
    }


    private static void writeChoices(ArrayNode nodeArr, List<ChoiceInfo> lstChoice, Index index) {
        List<ChoiceInfo> lstSorted = new ArrayList<>(lstChoice);
        lstSorted.sort(Comparator.comparing(ChoiceInfo::nameChoice));
        for (ChoiceInfo choice : lstSorted) {
            ObjectNode node = nodeArr.addObject();
            node.put("name", choice.nameChoice());
            node.put("consuming", choice.flagConsuming());
            node.put("argument", index.strType(choice.typeArg()));
            List<FieldInfo> lstArgField = index.fieldsOf(choice.typeArg());
            if (lstArgField == null)
                node.putNull("fields");
            else
                writeFields(node.putArray("fields"), lstArgField, index);
            node.put("returns", index.strType(choice.typeReturn()));
            putText(node, "fromInterface", choice.idInterface().map(index::strRef).orElse(null));
        }
    }


    private static void writeShape(Map<String, ObjectNode> mapModule, DataShape data, Index index) {
        if (data instanceof DataShape.Rec rec) {
            ObjectNode node = arrayIn(mapModule, rec.idData(), "records").addObject();
            node.put("name", rec.idData().nameEntity());
            writeParams(node, rec.lstParam());
            writeFields(node.putArray("fields"), rec.lstField(), index);
        }
        else if (data instanceof DataShape.Variant variant) {
            ObjectNode node = arrayIn(mapModule, variant.idData(), "variants").addObject();
            node.put("name", variant.idData().nameEntity());
            writeParams(node, variant.lstParam());
            writeFields(node.putArray("constructors"), variant.lstCtor(), index);
        }
        else if (data instanceof DataShape.EnumShape enm) {
            ObjectNode node = arrayIn(mapModule, enm.idData(), "enums").addObject();
            node.put("name", enm.idData().nameEntity());
            writeParams(node, enm.lstParam());
            ArrayNode nodeCtors = node.putArray("constructors");
            for (String strCtor : enm.lstCtor()) {
                nodeCtors.add(strCtor);
            }
        }
    }


    private static void writeParams(ObjectNode node, List<String> lstParam) {
        ArrayNode nodeParams = node.putArray("params");
        for (String strParam : lstParam) {
            nodeParams.add(strParam);
        }
    }


    private static void writeFields(ArrayNode nodeArr, List<FieldInfo> lstField, Index index) {
        for (FieldInfo field : lstField) {
            ObjectNode node = nodeArr.addObject();
            node.put("name", field.nameField());
            node.put("type", index.strType(field.type()));
        }
    }


    private static List<TemplateInfo> sortedTemplates(PackageShape shape) {
        List<TemplateInfo> lst = new ArrayList<>(shape.lstTemplate());
        lst.sort(Comparator.comparing((TemplateInfo template) -> template.idTemplate().nameModule())
                .thenComparing(template -> template.idTemplate().nameEntity()));
        return lst;
    }


    private static List<InterfaceInfo> sortedInterfaces(PackageShape shape) {
        List<InterfaceInfo> lst = new ArrayList<>(shape.lstInterface());
        lst.sort(Comparator.comparing((InterfaceInfo iface) -> iface.idInterface().nameModule())
                .thenComparing(iface -> iface.idInterface().nameEntity()));
        return lst;
    }


    private static List<DataShape> sortedShapes(PackageShape shape) {
        List<DataShape> lst = new ArrayList<>(shape.lstShape());
        lst.sort(Comparator.comparing((DataShape data) -> data.idData().nameModule())
                .thenComparing(data -> data.idData().nameEntity()));
        return lst;
    }


    private static void refuse(ArrayNode nodeRefused, Path fileDar, String strEntry, String strPkgId,
            String strLf, String strReason) {
        ObjectNode node = nodeRefused.addObject();
        putText(node, "file", fileDar == null ? null : fileDar.toAbsolutePath().normalize().toString());
        putText(node, "entry", strEntry);
        putText(node, "packageId", strPkgId);
        putText(node, "lf", strLf);
        node.put("reason", strReason);
    }


    private static void putText(ObjectNode node, String strKey, String strValue) {
        if (strValue == null)
            node.putNull(strKey);
        else
            node.put(strKey, strValue);
    }


    /**
     * The cause chain on one line: a reader's refusal sits under the
     * decoder's own exception, and the top message alone hides it.
     */
    private static String chainOf(Throwable ex) {
        StringBuilder bld = new StringBuilder();
        Set<Throwable> setSeen = new HashSet<>();
        Throwable exHere = ex;
        while (exHere != null && setSeen.add(exHere)) {
            if (bld.length() > 0)
                bld.append(" <- ");
            bld.append(exHere.getClass().getSimpleName()).append(": ").append(exHere.getMessage());
            exHere = exHere.getCause();
        }
        return bld.toString();
    }


    /**
     * Every entity across the set, and the one spelling of a type.
     */
    private static final class Index {

        private final Map<DataId, TemplateInfo> mapTemplate = new HashMap<>();

        private final Map<DataId, InterfaceInfo> mapInterface = new HashMap<>();

        private final Map<DataId, DataShape> mapShape = new HashMap<>();

        private final Map<String, Set<String>> mapPkgByShort = new HashMap<>();


        Index(Iterable<Pkg> itrPkg) {
            for (Pkg pkg : itrPkg) {
                for (TemplateInfo template : pkg.shape.lstTemplate()) {
                    mapTemplate.put(template.idTemplate(), template);
                    note(template.idTemplate());
                }
                for (InterfaceInfo iface : pkg.shape.lstInterface()) {
                    mapInterface.put(iface.idInterface(), iface);
                    note(iface.idInterface());
                }
                for (DataShape data : pkg.shape.lstShape()) {
                    mapShape.put(data.idData(), data);
                    note(data.idData());
                }
            }
        }


        private void note(DataId id) {
            mapPkgByShort.computeIfAbsent(id.shortName(), strKey -> new HashSet<>()).add(id.idPackage());
        }


        TemplateInfo template(DataId id) {
            return mapTemplate.get(id);
        }


        List<DataId> refsOf(DataId id) {
            List<DataId> lstRef = new ArrayList<>();
            TemplateInfo template = mapTemplate.get(id);
            if (template != null)
                lstRef.addAll(refsOfTemplate(template));
            InterfaceInfo iface = mapInterface.get(id);
            if (iface != null)
                lstRef.addAll(refsOfInterface(iface));
            DataShape data = mapShape.get(id);
            if (data != null)
                lstRef.addAll(refsOfShape(data));
            return lstRef;
        }


        /**
         * @return the fields of the record the type names, or null when it
         *         names no record in the set
         */
        List<FieldInfo> fieldsOf(DamlType type) {
            if (type instanceof DamlType.Ref ref && mapShape.get(ref.idData()) instanceof DataShape.Rec rec)
                return rec.lstField();
            return null;
        }


        String strRef(DataId id) {
            Set<String> setPkg = mapPkgByShort.get(id.shortName());
            if (setPkg != null && setPkg.size() > 1)
                return id.idPackage() + ":" + id.shortName();
            return id.shortName();
        }


        String strType(DamlType type) {
            if (type == null)
                return null;
            return switch (type) {
                case DamlType.Prim prim -> prim.typeArg() == null ? strPrim(prim.kind())
                        : strPrim(prim.kind()) + " " + paren(strType(prim.typeArg()));
                case DamlType.Numeric num -> "Numeric " + num.cntScale();
                case DamlType.ListOf list -> "[" + strType(list.typeElem()) + "]";
                case DamlType.OptionalOf opt -> "Optional " + paren(strType(opt.typeElem()));
                case DamlType.TextMapOf map -> "TextMap " + paren(strType(map.typeValue()));
                case DamlType.GenMapOf map -> "Map " + paren(strType(map.typeKey())) + " "
                        + paren(strType(map.typeValue()));
                case DamlType.Ref ref -> strRef(ref.idData());
                case DamlType.Var var -> var.nameVar();
                case DamlType.App app -> strApp(app);
            };
        }


        private String strApp(DamlType.App app) {
            StringBuilder bld = new StringBuilder(paren(strType(app.typeFun())));
            for (DamlType typeArg : app.lstArg()) {
                bld.append(' ').append(paren(strType(typeArg)));
            }
            return bld.toString();
        }


        private static String paren(String strType) {
            if (strType == null)
                return "?";
            if (strType.indexOf(' ') < 0 || strType.startsWith("["))
                return strType;
            return "(" + strType + ")";
        }


        private static String strPrim(PrimKind kind) {
            return switch (kind) {
                case UNIT -> "Unit";
                case BOOL -> "Bool";
                case INT64 -> "Int";
                case TEXT -> "Text";
                case TIMESTAMP -> "Time";
                case DATE -> "Date";
                case PARTY -> "Party";
                case CONTRACT_ID -> "ContractId";
            };
        }
    }

}
