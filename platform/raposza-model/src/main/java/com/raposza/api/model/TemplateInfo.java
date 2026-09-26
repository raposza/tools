// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.util.List;
import java.util.Optional;

/**
 * A template as the UI needs it: enough to render a contract table, a contract
 * detail pane and a choice form.
 *
 * @param idTemplate the template identifier
 * @param lstField payload fields in declaration order
 * @param lstChoice EVERY choice exercisable on this template - its own and
 *                  those it gains from the interfaces it implements - each
 *                  carrying its provenance in ChoiceInfo.idInterface. The union
 *                  is built once, here, because a list assembled per caller is
 *                  a list that is incomplete somewhere.
 *
 *                  De-duplication is by NAME and the template's own wins.
 *                  Archive is declared by the template AND by every interface,
 *                  measured on both fixtures, so a naive union lists it twice
 * @param lstInterface interfaces this template implements, empty when none
 * @param typeKey contract key type, empty when the template has no key
 *
 * Author Claude/bentzn
 */
public record TemplateInfo(DataId idTemplate, List<FieldInfo> lstField,
        List<ChoiceInfo> lstChoice, List<DataId> lstInterface, Optional<DamlType> typeKey) {}
