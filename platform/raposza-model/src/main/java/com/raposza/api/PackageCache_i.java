// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api;

import com.raposza.api.model.PackageShape;

import java.util.Optional;
import java.util.Set;

/**
 * Persistent cache of decoded packages.
 *
 * Package ids are content hashes, so an entry is immutable and the cache never
 * needs invalidating. On connect the client lists package ids, subtracts what
 * is already cached, and fetches only the remainder.
 *
 * The raw archive is kept alongside the decoded form so a decoder fix can
 * re-decode without re-fetching.
 *
 * Author Claude/bentzn
 */
public interface PackageCache_i {

    /**
     * @return every package id currently cached
     */
    Set<String> ids();


    /**
     * @param idPackage the package id
     * @return the decoded package, empty when not cached
     */
    Optional<PackageShape> get(String idPackage);


    /**
     * @param idPackage the package id
     * @return the raw archive bytes, empty when not cached
     */
    Optional<byte[]> archive(String idPackage);


    /**
     * @param idPackage the package id
     * @param arrArchive raw archive bytes as fetched
     * @param shape the decoded form
     */
    void put(String idPackage, byte[] arrArchive, PackageShape shape);


    /**
     * Discards decoded forms but keeps raw archives, so a decoder fix can
     * re-decode offline.
     */
    void invalidateDecoded();

}
