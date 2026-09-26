// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.render;

import com.raposza.api.model.Contract;
import com.raposza.api.model.DamlType;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.TxTree;

import java.util.Optional;

/**
 * Turns the semantic model into a presentation form.
 *
 * The type argument is OPTIONAL throughout: values served with the verbose flag
 * set carry field labels, so they render correctly with no type metadata at
 * all. A type improves the result; it is not a precondition.
 *
 * @param <T> the presentation form; a Swing component, a JSON string, plain
 *            text, HTML
 *
 * Author Claude/bentzn
 */
public interface ValueRenderer_i<T> {

    /**
     * @param value the value
     * @param type declared type, empty when unknown
     * @return the rendered form
     */
    T value(DamlValue value, Optional<DamlType> type);


    /**
     * @param contract the contract
     * @return the rendered form
     */
    T contract(Contract contract);


    /**
     * @param tree the transaction
     * @return the rendered form
     */
    T tree(TxTree tree);

}
