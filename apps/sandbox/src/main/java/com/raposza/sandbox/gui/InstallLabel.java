// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.InstallSource;

import java.util.Map;

/**
 * What one row of the SDK box says: the SDK first, the Canton it runs second.
 *
 * <h2>THE SDK IS THE NUMBER A DEVELOPER THINKS IN</h2>
 *
 * `daml.yaml` names `sdk-version`, and on the 3.x line that is the dpm BUNDLE,
 * which is not the Canton it ships - bundle 3.5.11 brings Canton 3.5.18. A box
 * keyed on Canton alone answered a question in numbers the developer does not
 * have, so each row leads with the SDK and carries the Canton as what actually
 * runs.
 *
 * <h2>Where the SDK comes from, and nothing is derived by a rule</h2>
 *
 * A dpm installation's SDK is the bundle whose manifest names that Canton,
 * read off disk by {@code DpmBundles.mapBundle}. An Assistant installation IS
 * an SDK directory, `sdk/&lt;version&gt;/canton`, and its version is that
 * directory's name. A Canton no manifest on this machine names - an install by
 * OCI URI, for one - has no SDK here, and the row says so with
 * {@link #STR_SDK_NONE} rather than guessing.
 *
 * <h2>No LF column, and no edition</h2>
 *
 * The LF version the SDK's compiler emits, and which the participant accepts,
 * are not measured - his decision of 2026-09-25 is to leave the column out
 * until they are. The edition is left out as well, his instruction of the same
 * day: "drop 'open source' and 'enterprise' entirely for now".
 *
 * Author Claude/bentzn
 */
final class InstallLabel {

    /** What a row says when no SDK on this machine ships its Canton. */
    static final String STR_SDK_NONE = "-";

    static final String STR_PREFIX_SDK = "SDK ";

    static final String STR_PREFIX_CANTON = "Canton ";

    /** Between the SDK and the Canton on a row. */
    static final String STR_GAP = "   ";

    private InstallLabel() {
    }


    /**
     * @param inst the installation; never null
     * @param mapBundle Canton version to dpm bundle version, off this
     *        machine's manifests; may be null or empty
     * @return the SDK version the installation belongs to, or null when no
     *         SDK on this machine ships it
     */
    static String strSdk(CantonInstallation inst, Map<String, String> mapBundle) {
        if (inst == null)
            throw new IllegalArgumentException("an installation is required");
        if (inst.source() == InstallSource.DAML_ASSISTANT)
            return inst.version().toString();
        if (mapBundle == null)
            return null;
        return mapBundle.get(inst.version().toString());
    }


    /**
     * @param inst the installation; never null
     * @param mapBundle Canton version to dpm bundle version; may be null
     * @return the row's text, without the markers the renderer adds - the same
     *         in the closed box and in the open list
     */
    static String strRow(CantonInstallation inst, Map<String, String> mapBundle) {
        String strSdk = strSdk(inst, mapBundle);
        return STR_PREFIX_SDK + (strSdk == null ? STR_SDK_NONE : strSdk)
                + STR_GAP + STR_PREFIX_CANTON + inst.version();
    }

}
