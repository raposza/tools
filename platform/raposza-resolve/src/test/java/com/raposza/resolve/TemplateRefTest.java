// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.TypeRegistry_i;
import com.raposza.api.model.DataId;
import com.raposza.api.model.DataShape;
import com.raposza.api.model.TemplateInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Reference resolution against a registry that holds two versions of the same
 * DAR, which is the normal state of a ledger that has been upgraded once and
 * the state every interesting case here depends on.
 *
 * Author Claude/bentzn
 */
class TemplateRefTest {

    private static final String PKG_OLD = "aaaa1111";
    private static final String PKG_NEW = "bbbb2222";

    private static final DataId ACCOUNT_OLD = new DataId(PKG_OLD, "Main", "Account");
    private static final DataId ACCOUNT_NEW = new DataId(PKG_NEW, "Main", "Account");
    private static final DataId IOU = new DataId(PKG_NEW, "Iou.Main", "Iou");


    @Test
    void aUniqueShortNameResolves() {
        TemplateRef.Found found = assertInstanceOf(TemplateRef.Found.class,
                TemplateRef.resolve(registry(), "Iou.Main:Iou"));

        assertEquals(IOU, found.template().idTemplate());
        assertEquals("", found.strProblem());
    }


    /** A module name carries dots, and they must not be confused with the separator. */
    @Test
    void aDottedModuleNameSurvives() {
        assertInstanceOf(TemplateRef.Found.class, TemplateRef.resolve(registry(), "Iou.Main:Iou"));
    }


    @Test
    void aBareEntityNameResolvesWhenItIsUnique() {
        assertInstanceOf(TemplateRef.Found.class, TemplateRef.resolve(registry(), "Iou"));
    }


    /**
     * Two versions of one DAR is the normal case. Picking either would submit
     * against a package the operator did not name.
     */
    @Test
    void twoVersionsOfOneTemplateAreAmbiguousAndBothAreNamed() {
        TemplateRef ref = TemplateRef.resolve(registry(), "Main:Account");
        TemplateRef.Ambiguous amb = assertInstanceOf(TemplateRef.Ambiguous.class, ref);

        assertEquals(2, amb.lstCandidate().size());
        assertTrue(amb.strProblem().contains(PKG_OLD), amb.strProblem());
        assertTrue(amb.strProblem().contains(PKG_NEW), amb.strProblem());
    }


    /** Which is what the package-qualified form is for. */
    @Test
    void thePackageQualifiedFormDisambiguates() {
        TemplateRef.Found found = assertInstanceOf(TemplateRef.Found.class,
                TemplateRef.resolve(registry(), PKG_NEW + ":Main:Account"));

        assertEquals(ACCOUNT_NEW, found.template().idTemplate());
    }


    /**
     * The package part is matched against whatever the registry holds, not
     * assumed to be hex. On 2.10+ an identifier may carry a package NAME.
     */
    @Test
    void aPackageNameIsMatchedTheSameWayAHashIs() {
        List<TemplateInfo> lstTemplate = new ArrayList<>();
        lstTemplate.add(template(new DataId("acme-banking", "Main", "Account")));

        TemplateRef.Found found = assertInstanceOf(TemplateRef.Found.class,
                TemplateRef.resolve(new FakeRegistry(lstTemplate), "acme-banking:Main:Account"));

        assertEquals("acme-banking", found.template().idTemplate().idPackage());
    }


    @Test
    void aPackageThatDoesNotHoldTheTemplateIsUnknown() {
        assertInstanceOf(TemplateRef.Unknown.class,
                TemplateRef.resolve(registry(), "cccc3333:Main:Account"));
    }


    /**
     * The distinction that matters most in the message: an empty registry is
     * not the operator's spelling. It means no package has been read yet, and
     * it is fixed by reconnecting rather than by editing the reference.
     */
    @Test
    void anEmptyRegistrySaysSoRatherThanBlamingTheName() {
        TemplateRef ref = TemplateRef.resolve(new FakeRegistry(List.of()), "Main:Account");
        TemplateRef.Unknown unknown = assertInstanceOf(TemplateRef.Unknown.class, ref);

        assertEquals(0, unknown.cntKnown());
        assertTrue(unknown.strProblem().contains("no package"), unknown.strProblem());

        String strOther = TemplateRef.resolve(registry(), "Main:Nope").strProblem();
        assertTrue(strOther.contains("no template matches"), strOther);
    }


    @Test
    void malformedReferencesAreRefusedRatherThanGuessed() {
        assertInstanceOf(TemplateRef.Invalid.class, TemplateRef.resolve(registry(), ""));
        assertInstanceOf(TemplateRef.Invalid.class, TemplateRef.resolve(registry(), "   "));
        assertInstanceOf(TemplateRef.Invalid.class, TemplateRef.resolve(registry(), "Main:"));
        assertInstanceOf(TemplateRef.Invalid.class, TemplateRef.resolve(registry(), ":Account"));
        assertInstanceOf(TemplateRef.Invalid.class,
                TemplateRef.resolve(registry(), "a:b:c:d"));
    }


    @Test
    void surroundingSpaceIsTrimmedAndReportedTrimmed() {
        TemplateRef.Found found = assertInstanceOf(TemplateRef.Found.class,
                TemplateRef.resolve(registry(), "  Iou.Main:Iou  "));

        assertEquals("Iou.Main:Iou", found.strRef());
    }


    private static TypeRegistry_i registry() {
        List<TemplateInfo> lstTemplate = new ArrayList<>();
        lstTemplate.add(template(ACCOUNT_OLD));
        lstTemplate.add(template(ACCOUNT_NEW));
        lstTemplate.add(template(IOU));
        return new FakeRegistry(lstTemplate);
    }


    private static TemplateInfo template(DataId idTemplate) {
        return new TemplateInfo(idTemplate, List.of(), List.of(), List.of(), Optional.empty());
    }


    /**
     * Matches on module:entity or on a bare entity, which is what
     * TypeRegistry_i.templatesByName specifies. Hand written because the
     * resolver asks it exactly one question.
     */
    private record FakeRegistry(List<TemplateInfo> lstTemplate) implements TypeRegistry_i {

        @Override
        public List<TemplateInfo> templates() {
            return lstTemplate;
        }


        @Override
        public Optional<TemplateInfo> template(DataId idTemplate) {
            return lstTemplate.stream().filter(t -> t.idTemplate().equals(idTemplate)).findFirst();
        }


        @Override
        public List<TemplateInfo> templatesByName(String nameShort) {
            List<TemplateInfo> lstOut = new ArrayList<>();
            for (TemplateInfo info : lstTemplate) {
                if (info.idTemplate().shortName().equals(nameShort)
                        || info.idTemplate().nameEntity().equals(nameShort))
                    lstOut.add(info);
            }
            return List.copyOf(lstOut);
        }


        @Override
        public Optional<DataShape> shape(DataId idData) {
            return Optional.empty();
        }


        @Override
        public void refresh() {
        }

    }

}
