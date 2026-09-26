// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Author Claude/bentzn
 */
class FilePackageCacheTest {

    private static final String ID = "5d4f288902e3de632a6e4c4c82596c8ebca93f62c5339488ee47f6fc0c2f5dd6";

    @TempDir
    Path dirTmp;


    /**
     * A shape exercising the parts that need a type tag to survive a round
     * trip: a nested type reference, an Optional key, all three DataShape
     * variants, and - since the types spike - a genuinely parameterised data
     * type with an application of it, plus an inherited choice.
     */
    private static PackageShape sample() {
        DataId idAccount = new DataId(ID, "Main", "Account");
        DataId idAddress = new DataId(ID, "Main", "Address");
        DataId idGrade = new DataId(ID, "Main", "Grade");
        DataId idBox = new DataId(ID, "Main", "Box");
        DataId idDeposit = new DataId(ID, "Main", "Deposit");
        DataId idReportable = new DataId(ID, "Main", "Reportable");

        List<FieldInfo> lstField = List.of(
                new FieldInfo("balance", new DamlType.Numeric(10)),
                new FieldInfo("label", new DamlType.Prim(PrimKind.TEXT)),
                new FieldInfo("address", new DamlType.Ref(idAddress)),
                new FieldInfo("nickname", new DamlType.OptionalOf(new DamlType.Prim(PrimKind.TEXT))),
                new FieldInfo("tags", new DamlType.ListOf(new DamlType.Prim(PrimKind.TEXT))),
                new FieldInfo("ledger", new DamlType.GenMapOf(new DamlType.Prim(PrimKind.PARTY),
                        new DamlType.Numeric(10))),
                new FieldInfo("boxed", new DamlType.App(new DamlType.Ref(idBox),
                        List.of(new DamlType.Prim(PrimKind.TEXT)))));

        ChoiceInfo choiceOwn = new ChoiceInfo("Deposit", true, new DamlType.Ref(idDeposit),
                new DamlType.Prim(PrimKind.CONTRACT_ID),
                new ChoiceControllers.Parties(List.of("Bank")), Optional.empty());

        ChoiceInfo choiceInherited = new ChoiceInfo("Describe", false,
                new DamlType.Ref(new DataId(ID, "Main", "Describe")),
                new DamlType.Prim(PrimKind.TEXT),
                new ChoiceControllers.Fields(List.of("owner")), Optional.of(idReportable));

        TemplateInfo tmpl = new TemplateInfo(idAccount, lstField,
                List.of(choiceOwn, choiceInherited), List.of(idReportable),
                Optional.of(new DamlType.Prim(PrimKind.PARTY)));

        List<DataShape> lstShape = List.of(
                new DataShape.Rec(idAddress, List.of(), List.of(
                        new FieldInfo("street", new DamlType.Prim(PrimKind.TEXT)))),
                new DataShape.EnumShape(idGrade, List.of(), List.of("Gold", "Silver", "Bronze")),
                new DataShape.Variant(new DataId(ID, "Main", "Outcome"), List.of(), List.of(
                        new FieldInfo("Ok", new DamlType.Prim(PrimKind.UNIT)))),
                // "Box a" - the parameterised case, whose field type is the
                // parameter itself. Ground before the spike, and wrong.
                new DataShape.Rec(idBox, List.of("a"), List.of(
                        new FieldInfo("contents", new DamlType.Var("a")))));

        InterfaceInfo iface = new InterfaceInfo(idReportable, List.of(choiceInherited),
                List.of(), Optional.of(new DamlType.Ref(new DataId(ID, "Main", "ReportView"))));

        return new PackageShape(ID, "acstest", "0.0.1", "1.15",
                List.of(tmpl), List.of(iface), lstShape);
    }


    @Test
    void roundTripsArchiveAndDecodedShape() {
        FilePackageCache cache = new FilePackageCache(dirTmp);
        byte[] arrArchive = "not really a dalf".getBytes(StandardCharsets.UTF_8);

        cache.put(ID, arrArchive, sample());

        assertTrue(cache.ids().contains(ID));
        assertArrayEqualsOpt(arrArchive, cache.archive(ID));

        PackageShape back = cache.get(ID).orElseThrow();
        assertEquals(sample(), back);
    }


    @Test
    void sealedTypesSurviveTheRoundTrip() {
        FilePackageCache cache = new FilePackageCache(dirTmp);
        cache.put(ID, new byte[] {1, 2, 3}, sample());

        PackageShape back = cache.get(ID).orElseThrow();
        TemplateInfo tmpl = back.lstTemplate().get(0);

        assertTrue(tmpl.lstField().get(2).type() instanceof DamlType.Ref);
        assertTrue(tmpl.lstField().get(3).type() instanceof DamlType.OptionalOf);
        assertTrue(tmpl.lstField().get(5).type() instanceof DamlType.GenMapOf);
        assertEquals(10, ((DamlType.Numeric) tmpl.lstField().get(0).type()).cntScale());
        assertTrue(back.lstShape().get(1) instanceof DataShape.EnumShape);
        assertTrue(tmpl.typeKey().isPresent());
    }


    /**
     * The three changes the types spike forced into the model, pinned here
     * because a later simplification would drop them silently and the cost
     * would appear as a choice form with the wrong widget.
     *
     * Each was measured on a real archive, on LF 1.15 and LF 2.2 alike.
     */
    @Test
    void spikeFindingsSurviveTheRoundTrip() {
        FilePackageCache cache = new FilePackageCache(dirTmp);
        cache.put(ID, new byte[] {4, 5, 6}, sample());

        PackageShape back = cache.get(ID).orElseThrow();
        TemplateInfo tmpl = back.lstTemplate().get(0);

        // "Box Text" - Ref alone would have lost the Text.
        DamlType typeBoxed = tmpl.lstField().get(6).type();
        assertTrue(typeBoxed instanceof DamlType.App);
        DamlType.App app = (DamlType.App) typeBoxed;
        assertTrue(app.typeFun() instanceof DamlType.Ref);
        assertEquals(1, app.lstArg().size());

        // "data Box a" - the parameter name, and a field that IS the parameter.
        DataShape shapeBox = back.lstShape().get(3);
        assertEquals(List.of("a"), shapeBox.lstParam());
        assertTrue(((DataShape.Rec) shapeBox).lstField().get(0).type() instanceof DamlType.Var);

        // Provenance. Without it a choice list cannot say where Describe came
        // from, and an operator who sees five choices believes there are five.
        assertFalse(tmpl.lstChoice().get(0).flagInherited());
        assertTrue(tmpl.lstChoice().get(1).flagInherited());
        assertEquals("Reportable",
                tmpl.lstChoice().get(1).idInterface().orElseThrow().nameEntity());
        assertEquals(1, back.lstInterface().size());
    }


    @Test
    void invalidateDecodedKeepsArchives() throws IOException {
        FilePackageCache cache = new FilePackageCache(dirTmp);
        cache.put(ID, new byte[] {9}, sample());

        cache.invalidateDecoded();

        assertTrue(cache.get(ID).isEmpty(), "decoded form should be gone");
        assertTrue(cache.archive(ID).isPresent(), "archive must survive");
        assertTrue(cache.ids().contains(ID), "ids come from archives, not decoded forms");
        assertFalse(Files.exists(dirTmp.resolve(ID + ".json")));
    }


    @Test
    void truncatedDecodedFormIsAMissNotAFailure() throws IOException {
        FilePackageCache cache = new FilePackageCache(dirTmp);
        cache.put(ID, new byte[] {7}, sample());

        Path fileJson = dirTmp.resolve(ID + ".json");
        String strWhole = Files.readString(fileJson);
        Files.writeString(fileJson, strWhole.substring(0, strWhole.length() / 2));

        assertTrue(cache.get(ID).isEmpty(), "a corrupt decoded form reads as absent");
        assertTrue(cache.archive(ID).isPresent(), "the archive is untouched and can be re-decoded");
    }


    @Test
    void nothingIsCreatedUntilSomethingIsWritten() {
        Path dirUnused = dirTmp.resolve("never-used");
        FilePackageCache cache = new FilePackageCache(dirUnused);

        assertTrue(cache.ids().isEmpty());
        assertTrue(cache.get(ID).isEmpty());
        cache.invalidateDecoded();

        assertFalse(Files.exists(dirUnused), "construction and reads must leave no trace");
    }


    /**
     * A package id arrives from the network and becomes a filename. Rejection
     * must happen before any path is built, or the traversal has already
     * escaped the root.
     */
    @Test
    void pathTraversalIsRejectedBeforeAnyFileIsTouched() {
        FilePackageCache cache = new FilePackageCache(dirTmp);

        for (String bad : new String[] {"../escape", "a/b", "a\\b", "..", ".", ".hidden", ""}) {
            assertThrows(LedgerException.class, () -> cache.archive(bad), "should reject: " + bad);
        }
        assertFalse(Files.exists(dirTmp.getParent().resolve("escape.dalf")));
    }


    @Test
    void overlongIdIsRejected() {
        FilePackageCache cache = new FilePackageCache(dirTmp);
        assertThrows(LedgerException.class, () -> cache.archive("a".repeat(200)));
    }


    private static void assertArrayEqualsOpt(byte[] arrExpected, Optional<byte[]> optActual) {
        assertTrue(optActual.isPresent());
        assertEquals(arrExpected.length, optActual.get().length);
        for (int idx = 0; idx < arrExpected.length; idx++) {
            assertEquals(arrExpected[idx], optActual.get()[idx], "byte " + idx);
        }
    }

}
