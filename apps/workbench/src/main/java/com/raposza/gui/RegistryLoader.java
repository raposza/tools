// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.LedgerException;
import com.raposza.api.PackageCache_i;
import com.raposza.api.TypeRegistry_i;
import com.raposza.api.model.PackageShape;
import com.raposza.lf.LfTypeRegistry;
import com.raposza.spi.LedgerClient_i;
import com.raposza.spi.LfDecoder_i;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches what the cache is missing and hands back a registry over the result.
 *
 * THE FRONT END ASSEMBLES THIS, not the client. A client module knows a Ledger
 * API generation and nothing else; making it construct a registry would make
 * decoding a property of the transport, and the second generation would then
 * need its own copy of a decision that has nothing to do with the wire. So the
 * client offers bytes - packageIds() and archive() - and the three parts are
 * put together here, once, for either generation.
 *
 * Package ids are content hashes, so a cache entry is immutable and this
 * subtracts what is already held rather than re-fetching it. A package that
 * fails to fetch or to decode is SKIPPED with a warning: one bad archive out of
 * a hundred must not cost the operator every template on the participant, and
 * the registry says what it has rather than refusing to exist.
 *
 * <h2>THE REGISTRY IS SCOPED TO WHAT THE PARTICIPANT LISTED</h2>
 *
 * The cache is a MACHINE-WIDE store - {@code ~/.raposza/packages} - shared by
 * every participant this workstation has ever connected to, and by design: a
 * package id is a content hash, so one decode serves everybody. The registry is
 * not shared and must not be. Handing {@link LfTypeRegistry} the raw cache made
 * it index every package ever seen, so a template compiled against one ledger
 * appeared on the next one as a candidate the participant has never held.
 *
 * The failure that follows is asymmetric, which is why nothing upstream
 * caught it. A QUERY goes out as a package NAME and the participant resolves
 * it itself, so a phantom id passes; CREATE, EXERCISE and FETCH send the id
 * and come back PACKAGE_NOT_FOUND. A QUERY cannot tell a phantom id from a
 * real one.
 *
 * So the cache is filled globally and READ through a view restricted to the ids
 * this participant just listed. Nothing is evicted - another ledger's package is
 * not stale, it is simply not here.
 *
 * Author Claude/bentzn
 */
public final class RegistryLoader {

    private static final Logger LOG = LoggerFactory.getLogger(RegistryLoader.class);


    private RegistryLoader() {
    }


    /**
     * @param client the connected participant
     * @param cache the package cache, which is per user and not per profile
     * @param decoder the Daml-LF decoder
     * @return a registry over the packages this participant holds
     * @throws LedgerException when the participant will not list its packages,
     *         which is a refusal to answer rather than an empty ledger
     */
    public static TypeRegistry_i load(LedgerClient_i client, PackageCache_i cache,
            LfDecoder_i decoder) {
        if (client == null || cache == null || decoder == null)
            throw new IllegalArgumentException("client, cache and decoder are required");

        List<String> lstId = client.packageIds();
        Set<String> setHeld = cache.ids();

        List<String> lstMissing = new ArrayList<>();
        for (String idPackage : lstId) {
            if (!setHeld.contains(idPackage))
                lstMissing.add(idPackage);
        }

        int cntFetched = 0;
        int cntFailed = 0;
        for (String idPackage : lstMissing) {
            try {
                Optional<byte[]> optArchive = client.archive(idPackage);
                if (optArchive.isEmpty()) {
                    cntFailed++;
                    LOG.warn("participant listed package {} but does not hold it", idPackage);
                    continue;
                }

                byte[] arrArchive = optArchive.get();
                PackageShape shape = decoder.decode(arrArchive);
                cache.put(idPackage, arrArchive, shape);
                cntFetched++;
            }
            catch (RuntimeException ex) {
                cntFailed++;
                LOG.warn("package {} could not be read: {}", idPackage, ex.toString());
            }
        }

        LOG.info("packages: {} on the participant, {} already cached, {} fetched, {} skipped",
                lstId.size(), lstId.size() - lstMissing.size(), cntFetched, cntFailed);

        Set<String> setScope = new LinkedHashSet<>(lstId);
        int cntOther = cache.ids().size() - setScope.size();
        if (cntOther > 0) {
            LOG.debug("the cache holds {} package(s) this participant did not list; they are"
                    + " not in the registry", cntOther);
        }

        return new LfTypeRegistry(new ScopedCache(cache, setScope));
    }


    /**
     * The shared cache, read as one participant sees it.
     *
     * WRITES GO STRAIGHT THROUGH, so a package fetched here is available to
     * every other connection immediately - the sharing is the point and this
     * does not undo it. Only the READS are narrowed, and {@code ids()} is the
     * one that matters: it is what {@link LfTypeRegistry} walks to build its
     * index. {@code get} and {@code archive} are narrowed with it so that a
     * lookup by a phantom id answers empty rather than answering from another
     * ledger's package.
     */
    private static final class ScopedCache implements PackageCache_i {

        private final PackageCache_i cache;

        private final Set<String> setScope;


        private ScopedCache(PackageCache_i cacheNew, Set<String> setScopeNew) {
            this.cache = cacheNew;
            this.setScope = setScopeNew;
        }


        @Override
        public Set<String> ids() {
            Set<String> setOut = new LinkedHashSet<>();
            for (String idPackage : setScope) {
                if (cache.ids().contains(idPackage))
                    setOut.add(idPackage);
            }
            return setOut;
        }


        @Override
        public Optional<PackageShape> get(String idPackage) {
            return setScope.contains(idPackage) ? cache.get(idPackage) : Optional.empty();
        }


        @Override
        public Optional<byte[]> archive(String idPackage) {
            return setScope.contains(idPackage) ? cache.archive(idPackage) : Optional.empty();
        }


        @Override
        public void put(String idPackage, byte[] arrArchive, PackageShape shape) {
            cache.put(idPackage, arrArchive, shape);
        }


        @Override
        public void invalidateDecoded() {
            cache.invalidateDecoded();
        }

    }

}
