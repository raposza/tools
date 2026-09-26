// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

/**
 * A submittable command. Deliberately narrow: create and exercise cover every
 * action the two panes offer. createAndExercise and exerciseByKey are omitted
 * until something needs them.
 *
 * Author Claude/bentzn
 */
public sealed interface Command {

    /**
     * @param idTemplate template to create
     * @param argument the create argument
     */
    record Create(DataId idTemplate, DamlValue.Rec argument) implements Command {}


    /**
     * @param idTemplate template of the target contract
     * @param idContract target contract id
     * @param nameChoice choice to exercise
     * @param argument choice argument
     */
    record Exercise(DataId idTemplate, String idContract, String nameChoice,
            DamlValue argument) implements Command {}


    /**
     * Exercise a choice on the contract a KEY resolves to.
     *
     * The key is a DamlValue like any other, so nothing widens to admit it:
     * the wire carries it in the same Value shape a choice argument uses, on
     * both generations - v1 ExerciseByKeyCommand.contract_key and v2
     * exercise_by_key.contract_key are both a Value.
     *
     * A key names a template and the participant resolves it, so unlike
     * Exercise there is no contract id to carry and the template is REQUIRED
     * rather than reported back.
     *
     * @param idTemplate template whose key this is
     * @param valueKey the key
     * @param nameChoice choice to exercise
     * @param argument choice argument
     */
    record ExerciseByKey(DataId idTemplate, DamlValue valueKey, String nameChoice,
            DamlValue argument) implements Command {}

}
