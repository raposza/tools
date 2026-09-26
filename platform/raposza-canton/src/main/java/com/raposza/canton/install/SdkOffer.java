// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

/**
 * One SDK version a window can offer, with the channel that would install it
 * and whether it is already on the machine.
 *
 * <b>Installed means installed IN THAT CHANNEL.</b> The two keep separate
 * trees - the assistant a version directory under its own root, DPM a bundle in
 * its cache - so the flag is read from whichever side the offer came from and
 * the two are never compared.
 *
 * Author Claude/bentzn
 *
 * @param version the SDK version
 * @param channel what would install it
 * @param flagInstalled whether that channel already has it
 */
public record SdkOffer(VersionId version, SdkChannel channel, boolean flagInstalled)
        implements Comparable<SdkOffer> {

    public SdkOffer {
        if (version == null)
            throw new IllegalArgumentException("a version is required");
        if (channel == null)
            throw new IllegalArgumentException("a channel is required");
    }


    /** OLDEST FIRST, the order the catalogue is read in; the SDK dialog shows it reversed. */
    @Override
    public int compareTo(SdkOffer other) {
        int nCmp = version.compareTo(other.version);
        if (nCmp != 0)
            return nCmp;
        return channel.compareTo(other.channel);
    }

}
