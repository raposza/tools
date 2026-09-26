// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api;

import com.raposza.api.model.DamlType;
import com.raposza.api.model.DamlValue;

import java.util.Optional;

/**
 * Somewhere a name can be looked up while a JSON document is being read.
 *
 * <h2>Why the coercer, of all places</h2>
 *
 * A fixture script writes {@code {"owner": "$alice"}} and expects the binding's
 * value to land there WITH ITS TYPE. Checking that the type fits needs two
 * things at once: what is bound, and what the field is declared to be. Only one
 * component ever holds both, and it is the coercer - it walks the JSON and the
 * DamlType together and already knows the path to every leaf.
 *
 * Doing it anywhere else means walking the type alongside the JSON a second
 * time, which is the duplication this arrangement exists to prevent, and
 * would disagree about exactly the cases nobody wrote a test for.
 *
 * <h2>Deliberately not a CaQL interface</h2>
 *
 * There is nothing about a script here. A choice form with a field bound to a
 * previous result would implement the same thing, which is why this is one
 * method taking a name and returning a typed value.
 *
 * Author Claude/bentzn
 */
public interface Substitution_i {

    /**
     * @param name the name, WITHOUT its marker
     * @return what it is bound to, empty when nothing is. Empty means "no such
     *         name" and nothing else; an implementation that knows the name but
     *         refuses it - because it is stale, say - throws rather than
     *         returning empty, so the two failures cannot be confused
     */
    Optional<Bound> lookup(String name);


    /**
     * @param value the bound value
     * @param type its type, which is what the substitution is checked against
     */
    record Bound(DamlValue value, DamlType type) {}

}
