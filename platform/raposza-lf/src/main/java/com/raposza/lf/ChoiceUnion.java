// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lf;

import com.raposza.api.model.ChoiceInfo;
import com.raposza.api.model.DataId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The union of a template's own choices with the ones it gains from the
 * interfaces it implements.
 *
 * Plain Java over already-mapped values, with no AST and no Scala in sight.
 * That is not tidiness: the union CANNOT be completed while decoding a single
 * archive, because an implemented interface may live in another package
 * entirely. AstMapper performs the union it can from interfaces declared in
 * the same package, and LfTypeRegistry re-runs it once every cached package is
 * present. Both call this.
 *
 * Re-running is therefore a requirement, and the function is IDEMPOTENT:
 * de-duplication is by choice NAME, so a second pass adds nothing and reorders
 * nothing.
 *
 * De-duplication order is the template's own first, in declaration order, then
 * each interface's in the order the template lists them. The template's own
 * choice WINS: Archive is declared by the template AND by every interface,
 * measured on both LF generations, so a naive union lists it twice and an
 * operator reading the list believes there are two.
 *
 * Author Claude/bentzn
 */
public final class ChoiceUnion {

    private ChoiceUnion() {
    }


    /**
     * @param lstOwn the template's own choices, in declaration order. Entries
     *               already carrying an interface - from an earlier pass - are
     *               kept as they are and block the same name being added again
     * @param lstInterface the interfaces the template implements, in order
     * @param mapChoiceByInterface choices declared by each interface, as
     *                             decoded. An interface that is not present is
     *                             skipped rather than failing: its package may
     *                             simply not be cached yet
     * @return the union, in a stable order
     */
    public static List<ChoiceInfo> merge(List<ChoiceInfo> lstOwn, List<DataId> lstInterface,
            Map<DataId, List<ChoiceInfo>> mapChoiceByInterface) {
        List<ChoiceInfo> lstUnion = new ArrayList<>();
        Set<String> setSeen = new LinkedHashSet<>();

        if (lstOwn != null) {
            for (ChoiceInfo choice : lstOwn) {
                if (setSeen.add(choice.nameChoice()))
                    lstUnion.add(choice);
            }
        }

        if (lstInterface == null || mapChoiceByInterface == null)
            return List.copyOf(lstUnion);

        for (DataId idInterface : lstInterface) {
            List<ChoiceInfo> lstFrom = mapChoiceByInterface.get(idInterface);
            if (lstFrom == null)
                continue;

            for (ChoiceInfo choice : lstFrom) {
                if (!setSeen.add(choice.nameChoice()))
                    continue;

                lstUnion.add(stamp(choice, idInterface));
            }
        }
        return List.copyOf(lstUnion);
    }


    /**
     * A choice as declared ON an interface carries no provenance; the same
     * choice seen THROUGH a template does.
     *
     * @param choice the choice as the interface declares it
     * @param idInterface the interface it came from
     * @return the choice, marked with where it came from
     */
    private static ChoiceInfo stamp(ChoiceInfo choice, DataId idInterface) {
        if (choice.idInterface().isPresent())
            return choice;

        return new ChoiceInfo(choice.nameChoice(), choice.flagConsuming(), choice.typeArg(),
                choice.typeReturn(), choice.controllers(), Optional.of(idInterface));
    }

}
