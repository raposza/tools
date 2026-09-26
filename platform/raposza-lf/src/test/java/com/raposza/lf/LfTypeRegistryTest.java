// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.model.ChoiceControllers;
import com.raposza.api.model.ChoiceInfo;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.DataId;
import com.raposza.api.model.DataShape;
import com.raposza.api.model.InterfaceInfo;
import com.raposza.api.model.PackageShape;
import com.raposza.api.model.PrimKind;
import com.raposza.api.model.TemplateInfo;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The registry over the cache, and the cross-package union it exists to
 * complete.
 *
 * Author Claude/bentzn
 */
class LfTypeRegistryTest {

    private static final DataId ID_TEMPLATE = new DataId("pkgA", "Main", "Account");
    private static final DataId ID_INTERFACE = new DataId("pkgB", "Lib", "Reportable");
    private static final DataId ID_RECORD = new DataId("pkgA", "Main", "Address");


    private static ChoiceInfo choice(String nameChoice) {
        return new ChoiceInfo(nameChoice, true, new DamlType.Prim(PrimKind.UNIT),
                new DamlType.Prim(PrimKind.UNIT),
                new ChoiceControllers.Unresolved("test"), Optional.empty());
    }


    private static FakePackageCache cacheTwoPackages() {
        TemplateInfo template = new TemplateInfo(ID_TEMPLATE, List.of(),
                List.of(choice("Deposit")), List.of(ID_INTERFACE), Optional.empty());
        PackageShape shapeA = new PackageShape("pkgA", null, null, "LF", List.of(template),
                List.of(), List.of(new DataShape.Rec(ID_RECORD, List.of(), List.of())));

        InterfaceInfo iface = new InterfaceInfo(ID_INTERFACE, List.of(choice("Describe")),
                List.of(), Optional.empty());
        PackageShape shapeB = new PackageShape("pkgB", null, null, "LF", List.of(),
                List.of(iface), List.of());

        FakePackageCache cache = new FakePackageCache();
        cache.add(shapeA);
        cache.add(shapeB);
        return cache;
    }


    /**
     * The template and its interface are in DIFFERENT packages, so decoding
     * either archive alone cannot produce the union. The registry can.
     */
    @Test
    void theUnionIsCompletedAcrossPackages() {
        LfTypeRegistry registry = new LfTypeRegistry(cacheTwoPackages());
        TemplateInfo template = registry.template(ID_TEMPLATE).orElseThrow();

        assertEquals(2, template.lstChoice().size());
        assertFalse(template.lstChoice().get(0).flagInherited());
        assertTrue(template.lstChoice().get(1).flagInherited());
        assertEquals(Optional.of(ID_INTERFACE), template.lstChoice().get(1).idInterface());
    }


    @Test
    void anUnknownTypeAnswersEmptyRatherThanThrowing() {
        LfTypeRegistry registry = new LfTypeRegistry(cacheTwoPackages());

        assertTrue(registry.shape(new DataId("nope", "No", "Such")).isEmpty());
        assertTrue(registry.template(new DataId("nope", "No", "Such")).isEmpty());
        assertTrue(registry.shape(null).isEmpty());
        assertTrue(registry.templatesByName("  ").isEmpty());
    }


    @Test
    void shapesAreReachableByIdentity() {
        LfTypeRegistry registry = new LfTypeRegistry(cacheTwoPackages());
        assertTrue(registry.shape(ID_RECORD).isPresent());
    }


    @Test
    void aShortNameFindsTheTemplateWithOrWithoutItsModule() {
        LfTypeRegistry registry = new LfTypeRegistry(cacheTwoPackages());

        assertEquals(1, registry.templatesByName("Main:Account").size());
        assertEquals(1, registry.templatesByName("Account").size());
        assertEquals(0, registry.templatesByName("Main:Missing").size());
    }


    /**
     * The name is what a 3.x active-contract filter wants, and a package that
     * predates Smart Contract Upgrades has none - so empty is an answer and not
     * a failure. A caller that treated the two alike would send an empty
     * package part to a participant that was perfectly happy with the hash.
     */
    @Test
    void aPackageNameIsAnsweredOnlyWhenTheArchiveCarriesOne() {
        FakePackageCache cache = new FakePackageCache();
        cache.add(new PackageShape("pkgNamed", "model-tests", "1.0.0", "LF", List.of(),
                List.of(), List.of()));
        cache.add(new PackageShape("pkgBare", null, null, "LF", List.of(), List.of(), List.of()));

        LfTypeRegistry registry = new LfTypeRegistry(cache);

        assertEquals(Optional.of("model-tests"), registry.namePackage("pkgNamed"));
        assertTrue(registry.namePackage("pkgBare").isEmpty());
        assertTrue(registry.namePackage("pkgAbsent").isEmpty());
        assertTrue(registry.namePackage(null).isEmpty());
    }


    /**
     * Reading the index repeatedly must not go back to the cache, and nothing
     * is decoded twice. refresh() is the only thing that re-reads.
     */
    @Test
    void aHitDoesNotReRead() {
        FakePackageCache cache = cacheTwoPackages();
        LfTypeRegistry registry = new LfTypeRegistry(cache);

        registry.templates();
        int cntAfterLoad = cache.cntGet();
        registry.templates();
        registry.template(ID_TEMPLATE);
        registry.shape(ID_RECORD);
        assertEquals(cntAfterLoad, cache.cntGet());

        registry.refresh();
        assertTrue(cache.cntGet() > cntAfterLoad);
    }

}
