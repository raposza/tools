// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import com.raposza.api.model.DamlType;
import com.raposza.api.model.DamlValue;

import java.util.Optional;

/**
 * One name bound by a script. Sec. 6.
 *
 * <h2>A binding holds a value AND its type</h2>
 *
 * The value alone is not enough, and the document gives the three cases: a
 * contract id carries no template, a decimal carries no scale, and an empty
 * list carries no element type. Re-deriving the type at the point of use would
 * be guessing, and the guess would be wrong exactly where it mattered.
 *
 * <h2>Staleness is NOT a field here, and that is deliberate</h2>
 *
 * Sec. 6 lists staleness as part of a binding. It is modelled in {@link Env} as
 * a property of a CONTRACT ID instead, because sec. 7 requires a consuming
 * exercise to stale every binding holding that id - including ones bound
 * before the exercise and ones holding the id inside a structured choice
 * result. A boolean per binding would have to be revisited on every consume and
 * would be wrong for any binding created afterwards from the same id.
 *
 * Same requirement, derived rather than stored, and it cannot fall out of date.
 *
 * @param name the binding name, without its '$'
 * @param value what it holds
 * @param type the type it holds, which is what a substitution is checked against
 * @param numLine the line of the statement that bound it
 * @param idUpdate the update that produced it, empty when the statement made no
 *                 submission - an ALLOCATE PARTY or a FETCH
 *
 * Author Claude/bentzn
 */
public record Binding(String name, DamlValue value, DamlType type, int numLine,
        Optional<String> idUpdate) {}
