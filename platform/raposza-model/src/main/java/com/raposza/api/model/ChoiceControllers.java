// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What a decoder could work out about who may exercise a choice.
 *
 * A Daml choice carries a controller EXPRESSION, not a party list. Three
 * outcomes are possible and they are NOT interchangeable:
 *
 * Parties - the expression names party literals. Rare in practice.
 *
 * Fields - the expression projects fields reachable from the contract argument.
 * This is the ordinary case: "controller owner" names a FIELD, and its value
 * lives in the payload, per contract, which is why it cannot be a party string
 * here. resolve() turns it into parties once a payload is in hand.
 *
 * Unresolved - anything else, and it carries the reason. A fold, a lookup or a
 * conditional does not reduce without evaluating Daml, which this tool will
 * never do. THIS IS NOT A FAILURE and must not be logged as one; it is the
 * expected outcome for ordinary Daml, and a caller that treats it as an error
 * will be wrong most of the time.
 *
 * The previous model was a party list that was empty when the controller was
 * computed, which collapsed "resolves to nobody" and "cannot be resolved" into
 * the same value - exactly the distinction a three-state button rests on.
 *
 * A MIXED expression - a literal and a field together - is Unresolved. Partial
 * resolution would hand a caller a controller set it believes is complete, and
 * a confident wrong answer about who may act is worse than admitting the limit.
 *
 * Author Claude/bentzn
 */
public sealed interface ChoiceControllers {

    /** @param lstParty party ids named literally in the expression */
    record Parties(List<String> lstParty) implements ChoiceControllers {}

    /**
     * @param lstPath dotted field paths from the contract argument, one per
     *                controller; field names cannot contain a dot in Daml, so
     *                the dot is unambiguous as a separator
     */
    record Fields(List<String> lstPath) implements ChoiceControllers {}

    /**
     * @param strReason why the expression was not reduced, in words an operator
     *                  can act on - "controller is computed" says more than a
     *                  missing value does
     */
    record Unresolved(String strReason) implements ChoiceControllers {}


    /**
     * Resolves the controllers against a contract payload.
     *
     * EMPTY IS NOT AN EMPTY PARTY LIST. It means the controllers could not be
     * determined from here and the ledger decides - which is the third button
     * state, not a disabled one. Any path that does not lead to a party fails
     * the whole answer rather than returning what it found: a partial set would
     * be indistinguishable from a complete one.
     *
     * @param controllers the decoded outcome
     * @param payload the contract's create argument
     * @return the controlling parties in first-seen order, empty when they
     *         cannot be determined
     */
    static Optional<List<String>> resolve(ChoiceControllers controllers, DamlValue.Rec payload) {
        if (controllers instanceof Parties parties)
            return Optional.of(List.copyOf(new LinkedHashSet<>(parties.lstParty())));

        if (!(controllers instanceof Fields fields))
            return Optional.empty();

        if (payload == null)
            return Optional.empty();

        Set<String> setParty = new LinkedHashSet<>();
        for (String strPath : fields.lstPath()) {
            DamlValue value = follow(payload, strPath);
            if (value == null)
                return Optional.empty();

            List<String> lstFound = parties(value);
            if (lstFound == null)
                return Optional.empty();

            setParty.addAll(lstFound);
        }
        return Optional.of(List.copyOf(setParty));
    }


    /**
     * @param payload the record to walk
     * @param strPath dotted path
     * @return the value at the end of the path, null when any segment is
     *         missing or is not a record
     */
    private static DamlValue follow(DamlValue.Rec payload, String strPath) {
        DamlValue value = payload;
        for (String nameField : strPath.split("[.]")) {
            value = unwrap(value);
            if (!(value instanceof DamlValue.Rec rec))
                return null;

            DamlValue valueNext = null;
            for (DamlValue.Rec.Field field : rec.lstField()) {
                if (nameField.equals(field.nameField())) {
                    valueNext = field.value();
                    break;
                }
            }
            if (valueNext == null)
                return null;

            value = valueNext;
        }
        return unwrap(value);
    }


    /**
     * @param value any value
     * @return the payload of a Some, the value itself otherwise; null for a
     *         None, because a controller that is not there is not a controller
     */
    private static DamlValue unwrap(DamlValue value) {
        if (value instanceof DamlValue.Opt opt)
            return opt.value() == null ? null : unwrap(opt.value());

        return value;
    }


    /**
     * @param value the resolved leaf
     * @return the parties it holds, null when it holds something that is not a
     *         party - which fails the resolution rather than being ignored
     */
    private static List<String> parties(DamlValue value) {
        if (value instanceof DamlValue.Party party)
            return List.of(party.idParty());

        if (!(value instanceof DamlValue.Lst lst))
            return null;

        List<String> lstParty = new ArrayList<>();
        for (DamlValue elem : lst.lstElem()) {
            DamlValue valueElem = unwrap(elem);
            if (!(valueElem instanceof DamlValue.Party party))
                return null;

            lstParty.add(party.idParty());
        }
        return lstParty;
    }

}
