// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.Edition;
import com.raposza.canton.install.InstallSource;
import com.raposza.canton.install.SdkChannel;
import com.raposza.canton.install.SdkOffer;
import com.raposza.canton.install.VersionId;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The SDK box's rows lead with the SDK, and the SDK dialog lists newest first.
 *
 * Headless: nothing here opens a toolkit.
 *
 * Author Claude/bentzn
 */
class InstallLabelTest {

    /** Bundle 3.5.5 ships Canton 3.5.12 - `DpmBundles`, off the manifests. */
    private static final Map<String, String> MAP_BUNDLE = Map.of("3.5.12", "3.5.5");


    private static CantonInstallation inst(String strVersion, Edition edition,
            InstallSource source) {
        return new CantonInstallation(VersionId.parse(strVersion), edition, source,
                Path.of("/nowhere", strVersion), null);
    }


    @Test
    void aDpmRowNamesTheBundleThatShipsItsCanton() {
        CantonInstallation inst = inst("3.5.12", Edition.OPEN_SOURCE, InstallSource.DPM);

        assertEquals("3.5.5", InstallLabel.strSdk(inst, MAP_BUNDLE));
        assertEquals("SDK 3.5.5   Canton 3.5.12", InstallLabel.strRow(inst, MAP_BUNDLE));
    }


    /** His instruction of 2026-09-25: no edition on a row, for now. */
    @Test
    void noRowNamesItsEdition() {
        CantonInstallation instOpen = inst("3.5.12", Edition.OPEN_SOURCE, InstallSource.DPM);
        CantonInstallation instEnt = inst("3.4.10", Edition.ENTERPRISE,
                InstallSource.DAML_ASSISTANT);

        assertEquals("SDK 3.5.5   Canton 3.5.12", InstallLabel.strRow(instOpen, MAP_BUNDLE));
        assertEquals("SDK 3.4.10   Canton 3.4.10", InstallLabel.strRow(instEnt, MAP_BUNDLE));
    }


    @Test
    void aCantonNoManifestNamesHasNoSdkRatherThanAGuess() {
        CantonInstallation inst = inst("3.5.13", Edition.OPEN_SOURCE, InstallSource.DPM);

        assertNull(InstallLabel.strSdk(inst, MAP_BUNDLE));
        assertNull(InstallLabel.strSdk(inst, null));
        assertEquals("SDK -   Canton 3.5.13", InstallLabel.strRow(inst, MAP_BUNDLE));
    }


    @Test
    void anAssistantInstallIsItsOwnSdkDirectory() {
        CantonInstallation inst = inst("3.4.10", Edition.ENTERPRISE,
                InstallSource.DAML_ASSISTANT);

        assertEquals("3.4.10", InstallLabel.strSdk(inst, Map.of()));
        assertEquals("SDK 3.4.10   Canton 3.4.10", InstallLabel.strRow(inst, Map.of()));
    }


    @Test
    void theSdkDialogListsNewestFirst() {
        SdkOffer offerOld = new SdkOffer(VersionId.parse("2.8.12"), SdkChannel.ASSISTANT, true);
        SdkOffer offerMid = new SdkOffer(VersionId.parse("3.4.10"), SdkChannel.ASSISTANT, false);
        SdkOffer offerNew = new SdkOffer(VersionId.parse("3.5.11"), SdkChannel.DPM, false);

        assertEquals(List.of(offerNew, offerMid, offerOld),
                SdkInstallDialog.lstNewestFirst(List.of(offerOld, offerMid, offerNew)));
    }

}
