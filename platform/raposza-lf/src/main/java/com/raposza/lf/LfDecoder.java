// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lf;

import com.raposza.api.LedgerException;
import com.raposza.api.model.PackageShape;
import com.raposza.spi.LfDecoder_i;
import com.digitalasset.daml.lf.archive.ArchivePayload;
import com.digitalasset.daml.lf.archive.DamlLf;
import com.digitalasset.daml.lf.archive.Decode;
import com.digitalasset.daml.lf.archive.Reader;
import com.digitalasset.daml.lf.language.Ast;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import scala.Tuple2;
import scala.util.Either;
import scala.util.Right;

/**
 * The published Daml-LF archive reader, wrapped as an LfDecoder_i.
 *
 * ONE decoder for both LF generations. The interface's note about one
 * implementation per LF major version predates the measurement: the reader
 * built for 3.4.11 decodes an LF 1.15 archive and an LF 2.2 archive through
 * the same call, so a second implementation would have nothing different in
 * it. accepts() therefore answers for the archive rather than for a version.
 *
 * The bytes handed in are an ArchivePayload as the package service returns
 * them - NOT a DAR and NOT a DamlLf.Archive. Its package id is the SHA-256 of
 * those bytes, equal to the hash the service reports, on both Ledger API
 * generations; it is computed here so the decoder needs nothing but the bytes.
 *
 * The third argument of readArchivePayload is undocumented in its signature
 * and does not change the result either way, so false is passed and nothing
 * rests on it.
 *
 * The SCHEMA decode is used. See AstMapper for why the expression-carrying
 * decode is not merely more expensive but generation-dependent.
 *
 * Author Claude/bentzn
 */
public final class LfDecoder implements LfDecoder_i {

    @Override
    public boolean accepts(byte[] arrArchive) {
        if (arrArchive == null || arrArchive.length == 0)
            return false;

        try {
            return payload(arrArchive, packageId(arrArchive)) != null;
        }
        catch (RuntimeException ex) {
            return false;
        }
    }


    @Override
    public PackageShape decode(byte[] arrArchive) {
        if (arrArchive == null || arrArchive.length == 0)
            throw new LedgerException("empty archive");

        String idPackage = packageId(arrArchive);
        ArchivePayload payload = payload(arrArchive, idPackage);

        Tuple2<String, Ast.GenPackage<scala.runtime.BoxedUnit>> pair;
        try {
            pair = Decode.assertDecodeArchivePayloadSchema(payload);
        }
        catch (RuntimeException ex) {
            throw new LedgerException("cannot decode archive " + idPackage, ex);
        }

        // The reader reports the package id back; it is the archive's own and
        // disagreeing with the hash would mean one of the two is not what it
        // claims, which is worth failing on rather than picking a winner.
        String idDecoded = pair._1();
        if (idDecoded != null && !idDecoded.equals(idPackage)) {
            throw new LedgerException("archive hashes to " + idPackage
                    + " but decodes as " + idDecoded);
        }

        return AstMapper.map(idPackage, pair._2());
    }


    /**
     * @param arrArchive the raw archive bytes
     * @param idPackage its content hash
     * @return the read payload
     * @throws LedgerException when the bytes are not an ArchivePayload, or the
     *         reader refuses them
     */
    private static ArchivePayload payload(byte[] arrArchive, String idPackage) {
        DamlLf.ArchivePayload proto;
        try {
            proto = DamlLf.ArchivePayload.parseFrom(arrArchive);
        }
        catch (Exception ex) {
            throw new LedgerException("not a Daml-LF ArchivePayload: " + idPackage, ex);
        }

        Either<com.digitalasset.daml.lf.archive.Error, ArchivePayload> either =
                Reader.readArchivePayload(idPackage, proto, false);
        if (either.isLeft()) {
            throw new LedgerException("the Daml-LF reader refused archive " + idPackage
                    + ": " + ((scala.util.Left<?, ?>) either).value());
        }
        return ((Right<?, ArchivePayload>) either).value();
    }


    /**
     * PUBLIC because a fetch verifies itself with it. The package id IS the
     * SHA-256 of the archive payload, equal to the hash the package service
     * reports, on both Ledger API generations - so a caller that fetched bytes
     * under an id can confirm it received those bytes and not others, without
     * decoding anything.
     *
     * @param arrArchive the raw archive bytes
     * @return their SHA-256, lower-case hex, which IS the package id
     */
    public static String packageId(byte[] arrArchive) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException ex) {
            throw new LedgerException("SHA-256 is unavailable in this JVM", ex);
        }

        byte[] arrHash = digest.digest(arrArchive);
        StringBuilder bld = new StringBuilder(arrHash.length * 2);
        for (byte bt : arrHash) {
            bld.append(Character.forDigit((bt >> 4) & 0x0f, 16));
            bld.append(Character.forDigit(bt & 0x0f, 16));
        }
        return bld.toString();
    }

}
