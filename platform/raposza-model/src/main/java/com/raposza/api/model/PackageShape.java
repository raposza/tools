// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.model;

import java.util.List;

/**
 * One decoded Daml package. The output of an LfDecoder_i and the unit of
 * caching - the package id is a content hash, so an entry never needs
 * invalidating.
 *
 * @param idPackage the package id (content hash)
 * @param namePackage package name, null before SCU-capable LF versions
 * @param versionPackage package version, null before SCU-capable LF versions
 * @param versionLf the Daml-LF version string as reported by the archive
 * @param lstTemplate templates declared by this package
 * @param lstInterface interfaces declared by this package
 * @param lstShape data types declared by this package
 *
 * Author Claude/bentzn
 */
public record PackageShape(String idPackage, String namePackage, String versionPackage,
        String versionLf, List<TemplateInfo> lstTemplate, List<InterfaceInfo> lstInterface,
        List<DataShape> lstShape) {}
