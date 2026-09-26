// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Author Claude/bentzn
 */
class HostPlatformTest {

    @Test
    void theThreeOperatingSystemsAreRecognisedByTheirOwnNames() {
        assertEquals(HostPlatform.WINDOWS_X64, HostPlatform.of("Windows Server 2025", "amd64"));
        assertEquals(HostPlatform.MACOS_X64, HostPlatform.of("Mac OS X", "x86_64"));
        assertEquals(HostPlatform.LINUX_X64, HostPlatform.of("Linux", "amd64"));
    }


    /** ARM is a Linux distinction only - there is no macOS ARM asset to pick. */
    @Test
    void armIsDistinguishedOnLinuxAndNotOnMac() {
        assertEquals(HostPlatform.LINUX_ARM64, HostPlatform.of("Linux", "aarch64"));
        assertEquals(HostPlatform.LINUX_ARM64, HostPlatform.of("Linux", "arm64"));
        assertEquals(HostPlatform.MACOS_X64, HostPlatform.of("Mac OS X", "aarch64"));
    }


    /**
     * An unrecognised platform answers rather than throwing, so the failure
     * arrives as a download that names what it tried.
     */
    @Test
    void anUnknownPlatformFallsBackRatherThanRefusing() {
        assertEquals(HostPlatform.LINUX_X64, HostPlatform.of("", ""));
        assertEquals(HostPlatform.LINUX_X64, HostPlatform.of(null, null));
    }


    @Test
    void onlyWindowsIsWindows() {
        assertTrue(HostPlatform.WINDOWS_X64.flagWindows());
        assertFalse(HostPlatform.LINUX_X64.flagWindows());
        assertFalse(HostPlatform.MACOS_X64.flagWindows());
    }

}
