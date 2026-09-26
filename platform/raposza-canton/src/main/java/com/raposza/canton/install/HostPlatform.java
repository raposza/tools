// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.util.Locale;

/**
 * The four platforms Digital Asset publishes an SDK for, as their release
 * assets name them.
 *
 * <h2>The names are read off the published assets, not composed by a rule</h2>
 *
 * There is no rule that holds across both generations. A 2.x asset is
 * `daml-sdk-&lt;version&gt;-linux.tar.gz`; a 3.x asset for the same platform is
 * `-linux-x86_64`. That rename is the whole of why `daml install &lt;3.x
 * version&gt;` fails on Windows and works on Linux: 3.x still publishes a plain
 * `-linux` asset alongside the qualified one, and publishes no plain `-windows`
 * or `-macos` twin, so the 2.x assistant's constructed name resolves on Linux
 * and 404s everywhere else.
 *
 * <b>There is no macOS ARM asset on any version</b>, stable or snapshot -
 * `-macos-x86_64` is the only Apple build published, so Apple silicon is served
 * by translation or not at all. Recorded because its absence is the kind of
 * thing that reads as an enumeration bug rather than a fact.
 *
 * Author Claude/bentzn
 */
public enum HostPlatform {

    /** `-linux` on 2.x, `-linux-x86_64` on 3.x. */
    LINUX_X64("linux", "linux-x86_64"),

    /**
     * 3.x ONLY. 2.x publishes no ARM asset at all, so {@link #strAsset2x()}
     * answers null and the 2.x line is not installable here.
     */
    LINUX_ARM64(null, "linux-aarch64"),

    /** `-macos` on 2.x, `-macos-x86_64` on 3.x. No ARM twin exists. */
    MACOS_X64("macos", "macos-x86_64"),

    /** `-windows` on 2.x, `-windows-x86_64` on 3.x. */
    WINDOWS_X64("windows", "windows-x86_64");

    private final String strSuffix2x;

    private final String strSuffix3x;


    HostPlatform(String strSuffix2x, String strSuffix3x) {
        this.strSuffix2x = strSuffix2x;
        this.strSuffix3x = strSuffix3x;
    }


    /**
     * @return the asset suffix the 2.x line uses, or null where that line
     *         publishes nothing for this platform
     */
    public String strAsset2x() {
        return strSuffix2x;
    }


    /**
     * @return the asset suffix the 3.x line uses; never null
     */
    public String strAsset3x() {
        return strSuffix3x;
    }


    /**
     * @return whether this is a Windows platform, which decides the launcher
     *         extension and the default toolchain roots
     */
    public boolean flagWindows() {
        return this == WINDOWS_X64;
    }


    /**
     * @return the platform this JVM is running on
     */
    public static HostPlatform ofDefaults() {
        return of(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }


    /**
     * Unknown answers LINUX_X64 rather than throwing. A platform this does not
     * recognise is one where the download will fail with the name it tried,
     * which is a better report than a window that refuses to open.
     *
     * @param strOs the `os.name` property
     * @param strArch the `os.arch` property
     * @return the platform those two describe
     */
    public static HostPlatform of(String strOs, String strArch) {
        String strOsLower = strOs == null ? "" : strOs.toLowerCase(Locale.ROOT);
        String strArchLower = strArch == null ? "" : strArch.toLowerCase(Locale.ROOT);
        boolean flagArm = strArchLower.contains("aarch64") || strArchLower.contains("arm64");

        if (strOsLower.contains("win"))
            return WINDOWS_X64;
        if (strOsLower.contains("mac") || strOsLower.contains("darwin"))
            return MACOS_X64;

        return flagArm ? LINUX_ARM64 : LINUX_X64;
    }

}
