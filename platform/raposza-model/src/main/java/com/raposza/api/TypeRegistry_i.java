// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api;

import com.raposza.api.model.ChoiceInfo;
import com.raposza.api.model.DataId;
import com.raposza.api.model.DataShape;
import com.raposza.api.model.TemplateInfo;

import java.util.List;
import java.util.Optional;

/**
 * Template and data type discovery. This is the module that makes the tool
 * generic: without it the UI can only render templates it was compiled
 * against, which is what every existing option already does.
 *
 * Implementations decode Daml-LF obtained from the participant's package
 * service, and may additionally read DAR files from disk.
 *
 * Author Claude/bentzn
 */
public interface TypeRegistry_i {

    /**
     * The package NAME for a package id, when the registry knows one.
     *
     * <h2>Why a registry answers this at all</h2>
     *
     * A package id is a content hash and a package name is a property of the
     * package it hashes, so only something that has decoded the archive can
     * relate the two. That is this. A client cannot: it offers bytes and holds
     * no decoder, deliberately.
     *
     * <h2>What it is for</h2>
     *
     * {@link com.raposza.api.model.DataId#idPackage()} is documented as the
     * package hash OR a package name, and the two are not interchangeable on
     * every call: a Canton 3.x participant EMITS a hash on a created event and
     * REFUSES one in an active-contract template filter, where it wants the
     * name. So a caller building a filter asks here and uses whichever it gets.
     *
     * Empty is the honest answer for a package that predates Smart Contract
     * Upgrades, which carries no name at all, and the caller then sends the
     * hash - which is what such a participant expects.
     *
     * @param idPackage the package id
     * @return the name, empty when the package is unknown here or carries none
     */
    default Optional<String> namePackage(String idPackage) {
        return Optional.empty();
    }


    /**
     * Every template known to the registry.
     *
     * @return templates, sorted by module then entity
     */
    List<TemplateInfo> templates();


    /**
     * @param idTemplate the template to look up
     * @return the template, empty when unknown
     */
    Optional<TemplateInfo> template(DataId idTemplate);


    /**
     * Templates whose short name matches, ignoring package id. A ledger
     * carrying two versions of the same DAR returns both.
     *
     * @param nameShort module:entity, or a bare entity name
     * @return matching templates
     */
    List<TemplateInfo> templatesByName(String nameShort);


    /**
     * The choices an INTERFACE declares, as the interface declares them - no
     * union and no de-duplication. A template's choice list is the union by
     * name, which keeps ONE of two same-named choices from two interfaces; an
     * exercise that has to say which interface it means reads this instead.
     *
     * Empty by default, which is the honest answer for a registry that holds
     * no interfaces.
     *
     * @param idInterface the interface
     * @return its choices, empty when unknown
     */
    default List<ChoiceInfo> interfaceChoices(DataId idInterface) {
        return List.of();
    }


    /**
     * Resolve a DamlType.Ref.
     *
     * @param idData the data type to look up
     * @return the declaration, empty when unknown
     */
    Optional<DataShape> shape(DataId idData);


    /**
     * Reload from the current source. Called after a DAR upload, and on demand
     * from the UI.
     *
     * @throws LedgerException when the source cannot be read
     */
    void refresh();

}
