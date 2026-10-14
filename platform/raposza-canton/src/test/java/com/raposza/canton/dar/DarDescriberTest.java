// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.dar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.raposza.api.LedgerException;
import com.raposza.api.model.ChoiceControllers;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The description, against DARs built here and a decoder that answers from a
 * table: what is described in full, what only as referenced, how a type is
 * spelled, and that a refusal is reported rather than thrown.
 *
 * Author Claude/bentzn
 */
class DarDescriberTest {

    private static final DamlType TYPE_PARTY = new DamlType.Prim(PrimKind.PARTY);

    private static final DamlType TYPE_TEXT = new DamlType.Prim(PrimKind.TEXT);

    private static final ChoiceControllers CONTROLLERS =
            new ChoiceControllers.Unresolved("schema decode");

    private final Map<String, PackageShape> mapShape = new HashMap<>();

    private final LfDecoder_i decoder = new LfDecoder_i() {

        @Override
        public boolean accepts(byte[] arrArchive) {
            return mapShape.containsKey(DalfArchive.sha256Of(arrArchive));
        }


        @Override
        public PackageShape decode(byte[] arrArchive) {
            String strId = DalfArchive.sha256Of(arrArchive);
            PackageShape shape = mapShape.get(strId);
            if (shape == null)
                throw new LedgerException("cannot decode archive " + strId,
                        new IllegalArgumentException("3 is not supported"));
            return shape;
        }
    };


    @Test
    void mainsInFullTheDependencyOnlyAsReferenced(@TempDir Path dir) throws IOException {
        Fixture fix = fixture(dir);

        JsonNode node = describe(fix);
        JsonNode nodePkgs = node.get("packages");

        assertEquals(3, nodePkgs.size());
        assertEquals(fix.strIdMain1, nodePkgs.get(0).get("packageId").asText());
        assertEquals(fix.strIdMain2, nodePkgs.get(1).get("packageId").asText());
        assertEquals("main", nodePkgs.get(0).get("role").asText());
        assertEquals("full", nodePkgs.get(0).get("described").asText());

        JsonNode nodeDep = nodePkgs.get(2);
        assertEquals(fix.strIdDep, nodeDep.get("packageId").asText());
        assertEquals("dependency", nodeDep.get("role").asText());
        assertEquals("referenced", nodeDep.get("described").asText());
        JsonNode nodeModule = nodeDep.get("modules").get(0);
        assertEquals("Api.Token", nodeModule.get("name").asText());
        assertEquals(1, nodeModule.get("interfaces").size());
        assertEquals("Holding", nodeModule.get("interfaces").get(0).get("name").asText());
        assertEquals(1, nodeModule.get("records").size());
        assertEquals("HoldingView", nodeModule.get("records").get(0).get("name").asText());
    }


    @Test
    void aSharedDependencyIsDescribedOnceAndNamesEveryDar(@TempDir Path dir) throws IOException {
        Fixture fix = fixture(dir);

        JsonNode nodeDep = describe(fix).get("packages").get(2);

        assertEquals(2, nodeDep.get("files").size());
        assertTrue(nodeDep.get("dependencies").isNull());
        JsonNode nodeDeps = describe(fix).get("packages").get(0).get("dependencies");
        assertEquals(1, nodeDeps.size());
        assertEquals("api-token", nodeDeps.get(0).get("name").asText());
        assertEquals(fix.strIdDep, nodeDeps.get(0).get("packageId").asText());
    }


    /**
     * Two packages carry `Registry.Instrument:Instrument`, so each is spelled
     * with its package id - CaQL's rule for a template name.
     */
    @Test
    void anAmbiguousNameCarriesItsPackageId(@TempDir Path dir) throws IOException {
        Fixture fix = fixture(dir);

        JsonNode nodeTemplate = templateOf(describe(fix).get("packages").get(0));

        assertEquals(fix.strIdMain1 + ":Registry.Instrument:Instrument",
                nodeTemplate.get("ref").asText());
        assertEquals("Api.Token:Holding", nodeTemplate.get("implements").get(0).asText());
        JsonNode nodeChoice = nodeTemplate.get("choices").get(0);
        assertEquals("ContractId " + fix.strIdMain1 + ":Registry.Instrument:Instrument",
                nodeChoice.get("returns").asText());
    }


    @Test
    void typesAreSpelledAsDamlWritesThem(@TempDir Path dir) throws IOException {
        Fixture fix = fixture(dir);

        JsonNode nodeFields = templateOf(describe(fix).get("packages").get(0)).get("fields");

        assertEquals("Party", nodeFields.get(0).get("type").asText());
        assertEquals("[Text]", nodeFields.get(1).get("type").asText());
        assertEquals("Optional (ContractId Api.Token:Holding)", nodeFields.get(2).get("type").asText());
    }


    /**
     * A choice's argument record is written inline, and is not listed again
     * as a record; neither is the template's own payload record. A record
     * nothing inlines stays.
     */
    @Test
    void argumentAndPayloadRecordsAreInline(@TempDir Path dir) throws IOException {
        Fixture fix = fixture(dir);

        JsonNode nodePkg = describe(fix).get("packages").get(0);
        JsonNode nodeChoice = templateOf(nodePkg).get("choices").get(0);
        JsonNode nodeRecords = nodePkg.get("modules").get(0).get("records");

        assertEquals("newId", nodeChoice.get("fields").get(0).get("name").asText());
        assertEquals(1, nodeRecords.size());
        assertEquals("Config", nodeRecords.get(0).get("name").asText());
    }


    @Test
    void aRefusalIsReportedAndTheRestDescribed(@TempDir Path dir) throws IOException {
        Fixture fix = fixture(dir);
        byte[] arrPayload = payload("unknown", "3");
        String strId = DalfArchive.sha256Of(arrPayload);
        Path fileNew = dir.resolve("new.dar");
        writeDar(fileNew, "new-1.0.0", List.of(new Entry("new-1.0.0-" + strId, arrPayload)));
        Path fileJunk = dir.resolve("junk.dar");
        Files.writeString(fileJunk, "not a zip");

        List<DarDescriber.Input> lstInput = new ArrayList<>(fix.lstInput);
        lstInput.add(new DarDescriber.Input(fileNew, "user", "selected"));
        lstInput.add(new DarDescriber.Input(fileJunk, "user", "selected"));
        ObjectNode node = new DarDescriber(decoder, "test").describe("sandbox", lstInput);

        assertEquals(3, node.get("packages").size());
        JsonNode nodeRefused = node.get("refused");
        assertEquals(2, nodeRefused.size());
        assertEquals(strId, nodeRefused.get(0).get("packageId").asText());
        assertEquals("2.3", nodeRefused.get(0).get("lf").asText());
        assertTrue(nodeRefused.get(0).get("reason").asText().contains("3 is not supported"));
        assertTrue(nodeRefused.get(1).get("reason").asText().startsWith("unreadable DAR"));
    }


    @Test
    void whatIsNotKnownIsNullNotAbsent(@TempDir Path dir) throws IOException {
        JsonNode node = describe(fixture(dir));

        assertTrue(node.get("controllers").isNull());
        assertFalse(node.get("controllersNote").asText().isEmpty());
        assertEquals(1, node.get("schema").asInt());
        assertEquals("2.2", node.get("packages").get(0).get("lf").asText());
        assertTrue(templateOf(node.get("packages").get(0)).get("key").isNull());
    }


    /**
     * The reader names an LF 1 package without metadata `-no-package-metadata`
     * at version `0`; the description says null for both.
     */
    @Test
    void theReadersPlaceholderNameIsNull(@TempDir Path dir) throws IOException {
        byte[] arrPayload = payload("old", "15");
        String strId = DalfArchive.sha256Of(arrPayload);
        mapShape.put(strId, new PackageShape(strId, DarDescriber.STR_NO_METADATA, "0", "1.15",
                List.of(), List.of(), List.of()));
        Path fileDar = dir.resolve("old.dar");
        writeDar(fileDar, "old-1.0.0", List.of(new Entry("old-1.0.0-" + strId, arrPayload)));

        JsonNode nodePkg = new DarDescriber(decoder, "test").describe("sandbox",
                List.of(new DarDescriber.Input(fileDar, "user", "selected"))).get("packages").get(0);

        assertTrue(nodePkg.get("name").isNull());
        assertTrue(nodePkg.get("version").isNull());
    }


    private JsonNode describe(Fixture fix) {
        ObjectNode node = new DarDescriber(decoder, "test").describe("sandbox", fix.lstInput);
        // Written and read back would prove Jackson; this proves the tree.
        assertFalse(DarDescriber.strJson(node).isEmpty());
        return node;
    }


    private static JsonNode templateOf(JsonNode nodePkg) {
        return nodePkg.get("modules").get(0).get("templates").get(0);
    }


    private record Fixture(String strIdMain1, String strIdMain2, String strIdDep,
            List<DarDescriber.Input> lstInput) {
    }


    private record Entry(String strBase, byte[] arrPayload) {
    }


    /**
     * Two DARs, `registry` 0.6.0 and 0.8.2, both carrying the same
     * `api-token` dependency.
     */
    private Fixture fixture(Path dir) throws IOException {
        byte[] arrDep = payload("api-token", "2");
        byte[] arrMain1 = payload("registry-1", "2");
        byte[] arrMain2 = payload("registry-2", "2");
        String strIdDep = DalfArchive.sha256Of(arrDep);
        String strIdMain1 = DalfArchive.sha256Of(arrMain1);
        String strIdMain2 = DalfArchive.sha256Of(arrMain2);

        DataId idHolding = new DataId(strIdDep, "Api.Token", "Holding");
        DataId idView = new DataId(strIdDep, "Api.Token", "HoldingView");
        DataId idUnused = new DataId(strIdDep, "Api.Token", "Unused");
        mapShape.put(strIdDep, new PackageShape(strIdDep, "api-token", "1.0.0", "2.2", List.of(),
                List.of(new InterfaceInfo(idHolding, List.of(), List.of(),
                        Optional.of(new DamlType.Ref(idView)))),
                List.of(new DataShape.Rec(idView, List.of(),
                                List.of(new FieldInfo("owner", TYPE_PARTY),
                                        new FieldInfo("amount", new DamlType.Numeric(10)))),
                        new DataShape.Rec(idUnused, List.of(), List.of(new FieldInfo("x", TYPE_TEXT))))));

        mapShape.put(strIdMain1, registry(strIdMain1, "0.6.0", idHolding));
        mapShape.put(strIdMain2, registry(strIdMain2, "0.8.2", idHolding));

        Path fileDar1 = dir.resolve("registry-0.6.0.dar");
        Path fileDar2 = dir.resolve("registry-0.8.2.dar");
        writeDar(fileDar1, "registry-0.6.0", List.of(new Entry("registry-0.6.0-" + strIdMain1, arrMain1),
                new Entry("api-token-1.0.0-" + strIdDep, arrDep)));
        writeDar(fileDar2, "registry-0.8.2", List.of(new Entry("registry-0.8.2-" + strIdMain2, arrMain2),
                new Entry("api-token-1.0.0-" + strIdDep, arrDep)));
        return new Fixture(strIdMain1, strIdMain2, strIdDep,
                List.of(new DarDescriber.Input(fileDar1, "user", "selected"),
                        new DarDescriber.Input(fileDar2, "user", "selected")));
    }


    private static PackageShape registry(String strId, String strVersion, DataId idHolding) {
        DataId idInstrument = new DataId(strId, "Registry.Instrument", "Instrument");
        DataId idUpdate = new DataId(strId, "Registry.Instrument", "Instrument_Update");
        DataId idConfig = new DataId(strId, "Registry.Instrument", "Config");
        DamlType typeCid = new DamlType.Prim(PrimKind.CONTRACT_ID, new DamlType.Ref(idInstrument));
        DamlType typeHoldingCid = new DamlType.Prim(PrimKind.CONTRACT_ID, new DamlType.Ref(idHolding));
        List<FieldInfo> lstField = List.of(new FieldInfo("registrar", TYPE_PARTY),
                new FieldInfo("ids", new DamlType.ListOf(TYPE_TEXT)),
                new FieldInfo("holding", new DamlType.OptionalOf(typeHoldingCid)));
        ChoiceInfo choice = new ChoiceInfo("Instrument_Update", true, new DamlType.Ref(idUpdate), typeCid,
                CONTROLLERS, Optional.empty());
        TemplateInfo template = new TemplateInfo(idInstrument, lstField, List.of(choice),
                List.of(idHolding), Optional.empty());
        return new PackageShape(strId, "registry", strVersion, "2.2", List.of(template), List.of(),
                List.of(new DataShape.Rec(idInstrument, List.of(), lstField),
                        new DataShape.Rec(idUpdate, List.of(), List.of(new FieldInfo("newId", TYPE_TEXT))),
                        new DataShape.Rec(idConfig, List.of(),
                                List.of(new FieldInfo("limit", new DamlType.Prim(PrimKind.INT64))))));
    }


    /**
     * An LF 2 payload as measured: the minor in field 3, the package in field
     * 4 - here a tag that makes each payload's hash its own.
     */
    private static byte[] payload(String strTag, String strMinor) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        field(out, 4, strTag.getBytes(StandardCharsets.UTF_8));
        field(out, 3, strMinor.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }


    /** An archive: hash function 0 in field 1, the payload in 3, its hash in 4. */
    private static byte[] archive(byte[] arrPayload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(1 << 3);
        out.write(0);
        field(out, 3, arrPayload);
        field(out, 4, DalfArchive.sha256Of(arrPayload).getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }


    private static void field(ByteArrayOutputStream out, int nNum, byte[] arrValue) {
        varint(out, (nNum << 3) | 2);
        varint(out, arrValue.length);
        out.writeBytes(arrValue);
    }


    private static void varint(ByteArrayOutputStream out, long nValue) {
        long nLeft = nValue;
        while ((nLeft & ~0x7fL) != 0) {
            out.write((int) ((nLeft & 0x7f) | 0x80));
            nLeft >>>= 7;
        }
        out.write((int) nLeft);
    }


    /** The first entry is the main package. */
    private static void writeDar(Path fileDar, String strDir, List<Entry> lstEntry) throws IOException {
        String strMain = strDir + "/" + lstEntry.get(0).strBase() + ".dalf";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(fileDar))) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write(("Manifest-Version: 1.0\r\nMain-Dalf: " + strMain + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (Entry entry : lstEntry) {
                zip.putNextEntry(new ZipEntry(strDir + "/" + entry.strBase() + ".dalf"));
                zip.write(archive(entry.arrPayload()));
                zip.closeEntry();
            }
        }
    }

}
