// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

/**
 * Fully qualified identifier of a Daml data type or template.
 *
 * @param idPackage package id (LF package hash), or a package name when the
 *                  participant reports one
 * @param nameModule dotted module name, e.g. "Iou.Main"
 * @param nameEntity entity name within the module, e.g. "Iou"
 *
 * Author Claude/bentzn
 */
public record DataId(String idPackage, String nameModule, String nameEntity) {

    @Override
    public String toString() {
        return idPackage + ":" + nameModule + ":" + nameEntity;
    }


    /**
     * Module and entity only. This is what the user reads; the package hash is
     * noise until two versions of the same template are on the ledger at once.
     */
    public String shortName() {
        return nameModule + ":" + nameEntity;
    }

}
