// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.profile;

/**
 * Whether a profile may submit. Fixed when the profile is created and not
 * changeable from the session - the point is that reaching a shared
 * participant in write mode has to be a deliberate act recorded in a file, not
 * a checkbox someone clicks while distracted.
 *
 * Author Claude/bentzn
 */
public enum AccessMode {

    /** Explore pane only. The Act pane renders disabled. */
    READ_ONLY,

    /** Both panes. */
    READ_WRITE

}
