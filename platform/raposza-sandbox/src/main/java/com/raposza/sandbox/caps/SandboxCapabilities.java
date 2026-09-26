// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.caps;

import com.raposza.canton.install.CantonLaunchTable;
import com.raposza.canton.install.Edition;
import com.raposza.canton.install.VersionId;
import com.raposza.sandbox.caps.FeatureSupport.Provenance;
import com.raposza.sandbox.caps.FeatureSupport.Support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a given Canton version supports - the map a surface reads before it
 * decides what to put on screen.
 *
 * <h2>Measured first, extrapolated second, and the difference is reported</h2>
 *
 * `CantonLaunchTable` holds one row per binary that was actually run, and
 * returns EMPTY for anything else. This class is the layer that turns that
 * into an answer for every version, including ones nobody has installed:
 *
 * <ol>
 * <li>the exact version is in the table - MEASURED, and that is the end of it;</li>
 * <li>it is not, but a version on the SAME LINE is - EXTRAPOLATED from the
 * nearest one at or below it, or from the nearest above when the version
 * predates everything measured on its line;</li>
 * <li>nothing on the line, but something of the same MAJOR - EXTRAPOLATED from
 * that, by the same nearest rule;</li>
 * <li>nothing of that major at all - UNKNOWN.</li>
 * </ol>
 *
 * The line is preferred over the major because that is where the measurements
 * say the changes are: `sandbox` appears at 3.4.4 and not at 3.0, and
 * `sandbox-interactive` and `--multi-sync` at 3.5 and not at 3.4. A rule keyed
 * on the major would have got both wrong, which is why the table exists.
 *
 * <h2>The edition is not a factor here</h2>
 *
 * `CantonLaunchTable.find` already falls back across editions for a version it
 * has: the one measured difference between an open-source and an enterprise
 * build of the same version is the manifest entry point, and that is not a
 * feature. The edition is still taken as an argument so callers do not have to
 * decide that for themselves.
 *
 * <h2>Stack features do not extrapolate</h2>
 *
 * {@link SandboxFeature#JSON_API_PROCESS} and {@link SandboxFeature#PQS} are
 * facts about which stack THIS APPLICATION starts, not about the vendor binary.
 * They are exact for majors 2 and 3 and UNKNOWN otherwise, because a Canton 4
 * has no stack here to have a property of.
 *
 * Author Claude/bentzn
 */
public final class SandboxCapabilities {

    /** The scope the sandbox subcommand's flags are measured under. */
    private static final String STR_SCOPE_SANDBOX = "sandbox";

    private static final String STR_COMMAND_SANDBOX = "sandbox";


    private SandboxCapabilities() {
    }


    /**
     * @param feature what to ask about; never null
     * @param version the Canton version, or null when nothing is selected
     * @param edition its edition, or null for any
     * @return the answer, never null
     */
    public static FeatureSupport of(SandboxFeature feature, VersionId version, Edition edition) {
        if (feature == null)
            throw new IllegalArgumentException("a feature is required");
        if (version == null)
            return unknown(feature, "no Canton is selected");

        switch (feature) {
            case JSON_API_PROCESS:
                return ofJsonApiProcess(version);
            case PQS:
                return ofPqs(version);
            case SANDBOX_SUBCOMMAND:
                return ofCommand(feature, version, edition, STR_COMMAND_SANDBOX);
            case JSON_API_PORT:
                return ofFlag(feature, version, edition, "--json-api-port");
            case DEV_PROTOCOL:
                return ofFlag(feature, version, edition, "--dev");
            case STATIC_TIME:
                return ofFlag(feature, version, edition, "--static-time");
            case MULTI_SYNC:
                return ofFlag(feature, version, edition, "--multi-sync");
            default:
                return unknown(feature, "this feature has no rule");
        }
    }


    /**
     * @param version the Canton version, or null
     * @param edition its edition, or null for any
     * @return every feature, in declaration order; never null
     */
    public static Map<SandboxFeature, FeatureSupport> mapOf(VersionId version, Edition edition) {
        Map<SandboxFeature, FeatureSupport> mapOut = new LinkedHashMap<>();
        for (SandboxFeature feature : SandboxFeature.values()) {
            mapOut.put(feature, of(feature, version, edition));
        }
        return Collections.unmodifiableMap(mapOut);
    }


    /**
     * The question a layout method asks.
     *
     * @param feature what to ask about
     * @param version the Canton version, or null
     * @param edition its edition, or null for any
     * @return false only when the feature is KNOWN not to apply
     */
    public static boolean isExposed(SandboxFeature feature, VersionId version, Edition edition) {
        return of(feature, version, edition).isExposed();
    }


    /**
     * @param feature what to ask about
     * @param version the Canton version, or null
     * @param edition its edition, or null for any
     * @return true only when the feature is established as present
     */
    public static boolean isYes(SandboxFeature feature, VersionId version, Edition edition) {
        return of(feature, version, edition).isYes();
    }


    /** What the Canton picker appends to a version Raposza never ran - T-3. */
    public static final String STR_UNMEASURED = "[unmeasured]";

    /**
     * The same, in the CLOSED picker, where the version and edition already
     * fill the box and the long form was cut to "..." - his screenshot of
     * 2026-09-23. The tooltip says what it means.
     */
    public static final String STR_UNMEASURED_SHORT = "*";


    /**
     * A version installed from the window is SECOND-CLASS until it is measured:
     * no launch table row, so every option is extrapolated. The picker says so
     * - `todo.md` T-3, `canton_acquisition.md` section 9, D-835.
     *
     * @param version the selected Canton, or null
     * @param edition its edition, or null for any
     * @return why this version is second-class, or null when it was measured
     *         or nothing is selected
     */
    public static String strUnmeasured(VersionId version, Edition edition) {
        if (version == null || CantonLaunchTable.find(version, edition).isPresent())
            return null;
        VersionId versionFrom = versionNearest(version);
        if (versionFrom == null)
            return "Canton " + version + " was never run by Raposza, and nothing measured"
                    + " is near enough to take its options from";
        return "Canton " + version + " was never run by Raposza; its options are taken"
                + " from the measured " + versionFrom;
    }


    /**
     * @param version the version being asked about
     * @return the measured version an unmeasured one is answered from, or null
     *         when there is nothing to extrapolate from
     */
    public static VersionId versionNearest(VersionId version) {
        CantonLaunchTable.Entry entry = entryNearest(version);
        return entry == null ? null : entry.version();
    }


    private static FeatureSupport ofJsonApiProcess(VersionId version) {
        if (version.major() == 2) {
            return new FeatureSupport(SandboxFeature.JSON_API_PROCESS, Support.YES,
                    Provenance.MEASURED,
                    "2.x runs `daml json-api` from the SDK as a process of its own");
        }
        if (version.major() == 3) {
            return new FeatureSupport(SandboxFeature.JSON_API_PROCESS, Support.NO,
                    Provenance.MEASURED,
                    "3.x serves the HTTP Ledger API from the participant itself, so there"
                            + " is no separate process to show");
        }
        return unknown(SandboxFeature.JSON_API_PROCESS,
                "this application has no stack for Canton " + version.major());
    }


    private static FeatureSupport ofPqs(VersionId version) {
        if (version.major() == 2 || version.major() == 3) {
            return new FeatureSupport(SandboxFeature.PQS, Support.YES, Provenance.MEASURED,
                    "PQS ran against every installed version");
        }
        return unknown(SandboxFeature.PQS,
                "this application has no stack for Canton " + version.major());
    }


    private static FeatureSupport ofCommand(SandboxFeature feature, VersionId version,
            Edition edition, String strCommand) {
        Optional<CantonLaunchTable.Entry> optExact = CantonLaunchTable.find(version, edition);
        if (optExact.isPresent()) {
            return measured(feature, optExact.get().hasCommand(strCommand),
                    "read from " + version + "'s own usage line");
        }

        CantonLaunchTable.Entry entryNear = entryNearest(version);
        if (entryNear == null) {
            return unknown(feature, "no measured binary of Canton " + version.major()
                    + " to extrapolate from");
        }
        return extrapolated(feature, entryNear.hasCommand(strCommand), version, entryNear);
    }


    private static FeatureSupport ofFlag(SandboxFeature feature, VersionId version,
            Edition edition, String strFlag) {
        Optional<CantonLaunchTable.Entry> optExact = CantonLaunchTable.find(version, edition);
        if (optExact.isPresent()) {
            return measured(feature, optExact.get().hasFlag(STR_SCOPE_SANDBOX, strFlag),
                    "read from " + version + "'s own usage line");
        }

        CantonLaunchTable.Entry entryNear = entryNearest(version);
        if (entryNear == null) {
            return unknown(feature, "no measured binary of Canton " + version.major()
                    + " to extrapolate from");
        }
        return extrapolated(feature, entryNear.hasFlag(STR_SCOPE_SANDBOX, strFlag), version,
                entryNear);
    }


    /**
     * The line first, the major second, and at or below before above.
     *
     * @param version the version being asked about
     * @return the measured entry to answer from, or null
     */
    private static CantonLaunchTable.Entry entryNearest(VersionId version) {
        List<CantonLaunchTable.Entry> lstLine = lstCandidate(version, true);
        CantonLaunchTable.Entry entry = below(lstLine, version);
        if (entry == null)
            entry = above(lstLine, version);
        if (entry != null)
            return entry;

        List<CantonLaunchTable.Entry> lstMajor = lstCandidate(version, false);
        entry = below(lstMajor, version);
        if (entry == null)
            entry = above(lstMajor, version);
        return entry;
    }


    /**
     * @param version the version being asked about
     * @param flagSameLine true for the same minor line, false for the same major
     * @return every measured entry in that scope
     */
    private static List<CantonLaunchTable.Entry> lstCandidate(VersionId version,
            boolean flagSameLine) {
        List<CantonLaunchTable.Entry> lstOut = new ArrayList<>();
        for (CantonLaunchTable.Entry entry : CantonLaunchTable.lstEntry()) {
            if (flagSameLine && !entry.version().line().equals(version.line()))
                continue;
            if (!flagSameLine && entry.version().major() != version.major())
                continue;
            lstOut.add(entry);
        }
        return lstOut;
    }


    private static CantonLaunchTable.Entry below(List<CantonLaunchTable.Entry> lstEntry,
            VersionId version) {
        CantonLaunchTable.Entry entryBest = null;
        for (CantonLaunchTable.Entry entry : lstEntry) {
            if (entry.version().compareTo(version) > 0)
                continue;
            if (entryBest == null || entry.version().compareTo(entryBest.version()) > 0)
                entryBest = entry;
        }
        return entryBest;
    }


    private static CantonLaunchTable.Entry above(List<CantonLaunchTable.Entry> lstEntry,
            VersionId version) {
        CantonLaunchTable.Entry entryBest = null;
        for (CantonLaunchTable.Entry entry : lstEntry) {
            if (entry.version().compareTo(version) <= 0)
                continue;
            if (entryBest == null || entry.version().compareTo(entryBest.version()) < 0)
                entryBest = entry;
        }
        return entryBest;
    }


    private static FeatureSupport measured(SandboxFeature feature, boolean flagHas,
            String strWhy) {
        return new FeatureSupport(feature, flagHas ? Support.YES : Support.NO,
                Provenance.MEASURED, strWhy);
    }


    private static FeatureSupport extrapolated(SandboxFeature feature, boolean flagHas,
            VersionId version, CantonLaunchTable.Entry entryNear) {
        return new FeatureSupport(feature, flagHas ? Support.YES : Support.NO,
                Provenance.EXTRAPOLATED, version + " was never run; taken from the measured "
                        + entryNear.version());
    }


    private static FeatureSupport unknown(SandboxFeature feature, String strWhy) {
        return new FeatureSupport(feature, Support.UNKNOWN, Provenance.NONE, strWhy);
    }
}
