// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

/**
 * What {@link PqsInstallations#resolve} found for one Canton line: a scribe to
 * run, or why there is none.
 *
 * EXACTLY ONE OF THE TWO IS SET. A resolution with neither would be a mock that
 * nobody can explain, which is the state this exists to end - a PQS mock
 * running on a machine whose owner believed scribe was installed.
 *
 * @param installation the scribe to run, or null
 * @param strWhyNone why there is none, as a clause a person can act on, or
 *        null when there is one
 *
 * Author Claude/bentzn
 */
public record PqsResolution(PqsInstallation installation, String strWhyNone) {

    public PqsResolution {
        if ((installation == null) == (strWhyNone == null || strWhyNone.isBlank()))
            throw new IllegalArgumentException("exactly one of an installation and a reason");
    }


    public static PqsResolution of(PqsInstallation installation) {
        return new PqsResolution(installation, null);
    }


    public static PqsResolution none(String strWhyNone) {
        return new PqsResolution(null, strWhyNone);
    }


    public boolean isFound() {
        return installation != null;
    }
}
