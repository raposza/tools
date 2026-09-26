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
import com.raposza.api.model.PrimKind;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The union of own and inherited choices - the case that is silently wrong if
 * built per caller instead of once.
 *
 * Author Claude/bentzn
 */
class ChoiceUnionTest {

    private static final DataId ID_REPORTABLE = new DataId("pkg1", "Main", "Reportable");
    private static final DataId ID_OTHER = new DataId("pkg9", "Other", "Auditable");


    private static ChoiceInfo choice(String nameChoice) {
        return new ChoiceInfo(nameChoice, true, new DamlType.Prim(PrimKind.UNIT),
                new DamlType.Prim(PrimKind.UNIT),
                new ChoiceControllers.Unresolved("test"), Optional.empty());
    }


    @Test
    void inheritedChoicesAreAddedAndMarked() {
        List<ChoiceInfo> lstUnion = ChoiceUnion.merge(List.of(choice("Deposit")),
                List.of(ID_REPORTABLE), Map.of(ID_REPORTABLE, List.of(choice("Describe"))));

        assertEquals(2, lstUnion.size());
        assertEquals("Deposit", lstUnion.get(0).nameChoice());
        assertFalse(lstUnion.get(0).flagInherited());
        assertEquals("Describe", lstUnion.get(1).nameChoice());
        assertTrue(lstUnion.get(1).flagInherited());
        assertEquals(Optional.of(ID_REPORTABLE), lstUnion.get(1).idInterface());
    }


    /**
     * Archive is declared by the template AND by every interface, measured on
     * both LF generations. A naive union lists it twice.
     */
    @Test
    void archiveAppearsOnceAndTheTemplateOwnsIt() {
        List<ChoiceInfo> lstUnion = ChoiceUnion.merge(
                List.of(choice("Archive"), choice("Deposit")), List.of(ID_REPORTABLE),
                Map.of(ID_REPORTABLE, List.of(choice("Describe"), choice("Archive"))));

        assertEquals(3, lstUnion.size());
        assertEquals(1, lstUnion.stream().filter(ch -> "Archive".equals(ch.nameChoice())).count());
        assertFalse(lstUnion.get(0).flagInherited());
    }


    /**
     * The registry re-runs the union once every package is cached. A template
     * whose interfaces were all in its own package must come out unchanged.
     */
    @Test
    void mergingTwiceChangesNothing() {
        Map<DataId, List<ChoiceInfo>> mapByInterface =
                Map.of(ID_REPORTABLE, List.of(choice("Describe")));
        List<ChoiceInfo> lstOnce = ChoiceUnion.merge(List.of(choice("Deposit")),
                List.of(ID_REPORTABLE), mapByInterface);
        List<ChoiceInfo> lstTwice =
                ChoiceUnion.merge(lstOnce, List.of(ID_REPORTABLE), mapByInterface);

        assertEquals(lstOnce, lstTwice);
    }


    /**
     * An interface whose package is not cached yet leaves the list incomplete,
     * which is the truth, rather than failing the whole read.
     */
    @Test
    void anUncachedInterfaceIsSkipped() {
        List<ChoiceInfo> lstUnion = ChoiceUnion.merge(List.of(choice("Deposit")),
                List.of(ID_OTHER), Map.of());

        assertEquals(1, lstUnion.size());
    }

}
