// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.spi;

import com.raposza.api.LedgerException;
import com.raposza.api.model.PackageShape;

/**
 * Decodes one Daml-LF archive into the semantic model.
 *
 * One implementation per LF major version: daml_lf_1.proto and
 * daml_lf_2.proto produce separate generated Java trees with no shared types.
 *
 * Selection is PER ARCHIVE, not per connection. Each archive carries its own
 * LF version and a single participant can host several. That is why a decoder
 * is NOT obtained from Target_i: LF versions and Canton generations are
 * different axes, and a 3.x participant can serve an LF 1.15 archive.
 *
 * Author Claude/bentzn
 */
public interface LfDecoder_i {

    /**
     * @param arrArchive the raw archive bytes as returned by the package service
     * @return true when this decoder handles that archive's LF version
     */
    boolean accepts(byte[] arrArchive);


    /**
     * @param arrArchive the raw archive bytes
     * @return the decoded package
     * @throws LedgerException when the archive cannot be decoded
     */
    PackageShape decode(byte[] arrArchive);

}
