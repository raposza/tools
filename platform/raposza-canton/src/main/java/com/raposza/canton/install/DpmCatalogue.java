// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Which Canton versions the OCI registry publishes, read out of what
 * `dpm tags` prints.
 *
 * <h2>This is a PARSER, not a caller</h2>
 *
 * It turns the command's output into versions and nothing else. Running the
 * command is a process concern and is not done here, so this stays answerable
 * with no network, no dpm on the machine and no registry.
 *
 * <h2>The listing is mostly noise and the filter is the whole design</h2>
 *
 * One call returns stable patches beside floating line tags (`3.5`, `3.6`),
 * channel tags (`devnet`, `mainnet`, `testnet`), release candidates, dated
 * snapshots, ad-hoc builds, and a `.generic` twin of very nearly everything.
 * Only `major.minor.patch` is kept. That sidesteps having to know what
 * `.generic` means, which has never been established, and it drops the line
 * tags - which matters because `3.6` and `3.7` are both published as lines with
 * no stable patch under either, so a floating tag would offer a version that
 * resolves to a snapshot.
 *
 * <h2>Two registry paths are live and this uses the one that answered</h2>
 *
 * `dpm-config.yaml` on a workstation carries a `public` repository, and the
 * reference below is `public-all`, which is what returned a tag listing. The
 * base is what Canton's own build info reports; `components/` is the segment
 * that has to be there and the artefact name follows it.
 *
 * <b>An empty answer is not a fact about the registry.</b> `dpm tags` reports a
 * wrong path and an empty repository identically, with no error and no
 * authentication failure, so nothing here reads "no tags" as "no versions".
 *
 * Author Claude/bentzn
 */
public final class DpmCatalogue {

    /** The registry base, as Canton's own build info reports it. */
    public static final String STR_REGISTRY = "europe-docker.pkg.dev/da-images/public-all";

    public static final String STR_COMPONENT_OPEN_SOURCE = "canton-open-source";

    public static final String STR_COMPONENT_ENTERPRISE = "canton-enterprise";

    /** What `dpm tags` is given, and what `dpm add component` is given plus a tag. */
    public static final String STR_URI_FORMAT = "oci://%s/components/%s";

    /**
     * Exactly three dotted numbers and nothing else. Anything carrying a dash,
     * a further dot or a word is a candidate, a snapshot, an ad-hoc build or a
     * `.generic` twin.
     */
    private static final Pattern PAT_STABLE = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");


    private DpmCatalogue() {
    }


    /**
     * @param strComponent the component name, normally
     *        {@link #STR_COMPONENT_OPEN_SOURCE}
     * @return the reference to hand `dpm tags`
     */
    public static String strUri(String strComponent) {
        if (strComponent == null || strComponent.isBlank())
            throw new IllegalArgumentException("a component name is required");

        return String.format(STR_URI_FORMAT, STR_REGISTRY, strComponent.trim());
    }


    /**
     * @param strComponent the component name
     * @param version the exact version to pin
     * @return the reference to hand `dpm add component`
     */
    public static String strUriAt(String strComponent, VersionId version) {
        if (version == null)
            throw new IllegalArgumentException("a version is required");

        return strUri(strComponent) + ":" + version;
    }


    /**
     * @param strOutput everything `dpm tags` printed, or null
     * @return the stable versions in it, newest first, without duplicates
     */
    public static List<VersionId> lstStable(String strOutput) {
        List<VersionId> lstOut = new ArrayList<>();
        if (strOutput == null || strOutput.isBlank())
            return lstOut;

        for (String strLine : strOutput.split("\\R")) {
            String strTag = strLine.trim();
            if (!PAT_STABLE.matcher(strTag).matches())
                continue;

            VersionId version = VersionId.parse(strTag);
            if (!lstOut.contains(version))
                lstOut.add(version);
        }
        Collections.sort(lstOut);
        Collections.reverse(lstOut);
        return lstOut;
    }

}
