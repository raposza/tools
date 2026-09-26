// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Author Claude/bentzn
 */
class InstallEnvTest {

    private static final Path DIR_STAGE = Path.of("work", "stage");

    private static final Path DIR_DPM = Path.of("work", "dpm");


    /**
     * Without these two the SDK installer reports success and installs
     * nothing, so their absence is the defect this exists to prevent.
     */
    @Test
    void windowsGetsBothTempVariables() {
        Map<String, String> mapEnv = InstallEnv.mapOverride(HostPlatform.WINDOWS_X64,
                DIR_STAGE, null);

        assertEquals(2, mapEnv.size());
        assertEquals(DIR_STAGE.toAbsolutePath().toString(), mapEnv.get("TMP"));
        assertEquals(mapEnv.get("TMP"), mapEnv.get("TEMP"));
    }


    /** The denial is a Windows behaviour and the override is noise elsewhere. */
    @Test
    void theOtherPlatformsGetNoTempOverride() {
        assertTrue(InstallEnv.mapOverride(HostPlatform.LINUX_X64, DIR_STAGE, null).isEmpty());
        assertTrue(InstallEnv.mapOverride(HostPlatform.MACOS_X64, DIR_STAGE, null).isEmpty());
    }


    @Test
    void aStagingDirectoryIsRequiredBeforeAnythingIsOverridden() {
        assertTrue(InstallEnv.mapOverride(HostPlatform.WINDOWS_X64, null, null).isEmpty());
    }


    @Test
    void theDpmRootIsPassedOnEveryPlatformAndOnlyWhenGiven() {
        assertEquals(DIR_DPM.toAbsolutePath().toString(),
                InstallEnv.mapOverride(HostPlatform.LINUX_X64, null, DIR_DPM).get("DPM_HOME"));
        assertFalse(InstallEnv.mapOverride(HostPlatform.LINUX_X64, null, null)
                .containsKey("DPM_HOME"));
    }


    @Test
    void bothOverridesTogetherAreThreeVariables() {
        Map<String, String> mapEnv = InstallEnv.mapOverride(HostPlatform.WINDOWS_X64,
                DIR_STAGE, DIR_DPM);

        assertEquals(3, mapEnv.size());
    }


    /**
     * The builder's own environment is inherited and then overridden. A
     * variable set in a shell does not reach a spawned process, which is the
     * whole reason this is applied here.
     */
    @Test
    void theBuilderKeepsItsInheritedEnvironment() {
        ProcessBuilder builder = new ProcessBuilder("true");
        builder.environment().put("RAPOSZA_PROBE", "kept");

        InstallEnv.apply(builder, HostPlatform.WINDOWS_X64, DIR_STAGE, DIR_DPM);

        assertEquals("kept", builder.environment().get("RAPOSZA_PROBE"));
        assertEquals(DIR_STAGE.toAbsolutePath().toString(), builder.environment().get("TEMP"));
        assertEquals(DIR_DPM.toAbsolutePath().toString(),
                builder.environment().get("DPM_HOME"));
    }

}
