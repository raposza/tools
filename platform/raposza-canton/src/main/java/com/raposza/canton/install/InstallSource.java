// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

/**
 * Which packaging system delivered a Canton installation.
 *
 * The operational boundary is 2.8-3.4 Daml Assistant, 3.5+ DPM. The two use
 * different directory layouts, so the source decides how an installation is
 * read - never a path template applied to a version.
 *
 * Author Claude/bentzn
 */
public enum InstallSource {

    DAML_ASSISTANT,

    DPM
}
