// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

/**
 * Which Canton build an installation is.
 *
 * Load-bearing, not cosmetic. `dpm install 3.4.11` puts a Canton 3.4.11 from
 * the ENTERPRISE component in the DPM cache while the Daml Assistant's
 * OPEN_SOURCE 3.4.11 sits under ~/.daml/sdk. Same version string, different
 * capabilities, so a version alone does not identify a participant to launch.
 *
 * READ FROM THE RUNTIME JAR's `Main-Class` - see {@link CantonRuntimeJar}. A
 * DPM component name is the fallback when a jar cannot be read, and that name
 * records which artefact was fetched rather than what licence the release
 * carries: 3.4.4 is published under both and sits in the cache under the
 * enterprise component name. Do not read an entitlement out of this enum, and
 * do not gate on it.
 *
 * Author Claude/bentzn
 * Amended 2026-08-18
 */
public enum Edition {

    OPEN_SOURCE,

    ENTERPRISE,

    /** The layout named neither. Never assume open source to fill this in. */
    UNKNOWN
}
