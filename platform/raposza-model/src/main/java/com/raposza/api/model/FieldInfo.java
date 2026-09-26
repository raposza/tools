// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

/**
 * One field of a record, or one argument of a choice.
 *
 * @param nameField field name as declared in Daml
 * @param type declared type
 *
 * Author Claude/bentzn
 */
public record FieldInfo(String nameField, DamlType type) {}
