// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The two channels' catalogues, as one list.
 *
 * <h2>Where a version exists both ways, DPM wins</h2>
 *
 * The 3.4 patches are published as GitHub assets AND as registry bundles. Two
 * rows for one version would ask the reader to choose between two things they
 * have no way to tell apart, so the DPM row is kept and the assistant row is
 * dropped. That is the operator's decision, and it also keeps the row that
 * carries an installed flag the vendor's own tool reported.
 *
 * <h2>Oldest first</h2>
 *
 * The list reads downwards in the order the versions were released.
 *
 * <h2>An empty DPM side is not an empty catalogue</h2>
 *
 * A machine with no DPM contributes no bundles, and the assistant's whole set
 * survives the merge unchanged - including the 3.4 patches, which are then the
 * only way to reach that line.
 *
 * Author Claude/bentzn
 */
public final class SdkOffers {

    private SdkOffers() {
    }


    /**
     * @param platform the platform to install for; never null
     * @param setInstalled the SDK versions under the assistant root; never null
     * @return what the assistant channel can offer, in catalogue order
     */
    public static List<SdkOffer> lstAssistant(HostPlatform platform,
            Set<VersionId> setInstalled) {
        if (platform == null || setInstalled == null)
            throw new IllegalArgumentException("a platform and an installed set are required");

        List<SdkOffer> lstOut = new ArrayList<>();
        for (VersionId version : SdkCatalogue.lstVersion(platform)) {
            lstOut.add(new SdkOffer(version, SdkChannel.ASSISTANT,
                    setInstalled.contains(version)));
        }
        for (VersionId version : SdkCatalogue.lstVersion3x(platform)) {
            lstOut.add(new SdkOffer(version, SdkChannel.ASSISTANT,
                    setInstalled.contains(version)));
        }
        return Collections.unmodifiableList(lstOut);
    }


    /**
     * @param lstAssistant what the assistant can offer; never null
     * @param lstDpm what DPM can offer; never null
     * @return one row per version, oldest first, DPM's row where both have one
     */
    public static List<SdkOffer> lstMerged(List<SdkOffer> lstAssistant, List<SdkOffer> lstDpm) {
        if (lstAssistant == null || lstDpm == null)
            throw new IllegalArgumentException("two lists are required");

        Map<VersionId, SdkOffer> mapOffer = new LinkedHashMap<>();
        for (SdkOffer offer : lstAssistant) {
            mapOffer.put(offer.version(), offer);
        }
        // SECOND, so it REPLACES rather than loses to what is already there.
        for (SdkOffer offer : lstDpm) {
            mapOffer.put(offer.version(), offer);
        }

        List<SdkOffer> lstOut = new ArrayList<>(mapOffer.values());
        Collections.sort(lstOut);
        return Collections.unmodifiableList(lstOut);
    }

}
