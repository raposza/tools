// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.util.Optional;

/**
 * A choice as the form builder needs it.
 *
 * Three fields were added on measurement, and two removed.
 *
 * The argument is a TYPE, not a field list. A choice argument decodes as a
 * reference to a synthetic record - "Describe" becomes Main:Describe with one
 * field - and flattening it here would discard the identity a submission has to
 * name. Resolve it through TypeRegistry_i, the same rule DamlType.Ref already
 * follows.
 *
 * idInterface is the choice's PROVENANCE. A template's own choice list omits
 * every choice it gains by implementing an interface, so a list built from the
 * template alone is silently incomplete, and an operator who sees five choices
 * believes there are five. Both fixtures show it: Account declares Archive and
 * Deposit, while Describe exists only on Reportable.
 *
 * controllers is an OUTCOME, not a party list. It replaced a List that was
 * empty when the controller was computed - which made "resolves to nobody"
 * indistinguishable from "cannot be resolved from here", and those are the two
 * cases a three-state button has to tell apart. See ChoiceControllers.
 *
 * @param nameChoice choice name
 * @param flagConsuming true when exercising archives the contract; drives the
 *                      confirmation wording in the UI
 * @param typeArg the argument type, normally a Ref to a synthetic record
 * @param typeReturn declared return type
 * @param controllers what could be determined about who may exercise it
 * @param idInterface the interface this choice came from, empty when the
 *                    template declares it itself
 *
 * Author Claude/bentzn
 */
public record ChoiceInfo(String nameChoice, boolean flagConsuming, DamlType typeArg,
        DamlType typeReturn, ChoiceControllers controllers, Optional<DataId> idInterface) {

    /**
     * NOT named isInherited. An is-prefixed no-arg method on a record is a bean
     * getter to Jackson, which writes it into the JSON as an extra property and
     * then refuses that property on the way back - the cache round trip fails
     * and, because get() swallows the error by design, surfaces as an entry
     * that simply is not there. DataId.shortName has always been safe for the
     * same reason inverted: no prefix.
     *
     * The flag prefix is the house convention anyway.
     *
     * @return true when this choice came from an implemented interface rather
     *         than from the template
     */
    public boolean flagInherited() {
        return idInterface.isPresent();
    }

}
