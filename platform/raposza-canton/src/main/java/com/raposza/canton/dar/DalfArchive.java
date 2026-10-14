// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.dar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The `.dalf` entries of a DAR, each unwrapped to the `ArchivePayload` a
 * decoder takes.
 *
 * <h2>The payload is found by its hash</h2>
 *
 * A `.dalf` in a DAR is an archive wrapping the payload, and the package id is
 * the SHA-256 of the payload bytes. No generated protobuf class is used and no
 * field number is assumed: the archive's top-level fields are walked by the
 * protobuf wire format, and the length-delimited field whose SHA-256 equals a
 * 64-hex string the archive carries, or the id in the entry name, IS the
 * payload. An entry where nothing matches gets no package id and is reported,
 * not guessed at.
 *
 * <h2>The LF version</h2>
 *
 * Read off the payload's top-level fields: the minor is field 3, a string, and
 * the package sits in field 2 for LF 1 and field 4 for LF 2 - measured on
 * LF 1.15, 2.2 and 2.3 archives. Reported as `1.15`, `2.3` and so on, and
 * `null` when the fields are not those.
 *
 * Author Claude/bentzn
 */
final class DalfArchive {

    private static final String STR_DALF = ".dalf";

    private static final Pattern PAT_HEX64 = Pattern.compile("[0-9a-f]{64}");

    private static final Pattern PAT_ID_IN_NAME = Pattern.compile("([0-9a-f]{64})\\.dalf$");

    private static final int N_FIELD_MINOR = 3;

    private static final int N_FIELD_LF1 = 2;

    private static final int N_FIELD_LF2 = 4;

    private static final int N_WIRE_VARINT = 0;

    private static final int N_WIRE_FIXED64 = 1;

    private static final int N_WIRE_LEN = 2;

    private static final int N_WIRE_FIXED32 = 5;


    /**
     * One `.dalf` entry.
     *
     * @param strEntry the entry name inside the DAR
     * @param strPkgId the package id, or null when no payload matched
     * @param arrPayload the payload bytes, or null with the id
     * @param strLf the LF version, e.g. `2.3`, or null
     */
    record Dalf(String strEntry, String strPkgId, byte[] arrPayload, String strLf) {
    }


    private record Field(int nNum, int nWire, byte[] arrValue) {
    }


    private DalfArchive() {
    }


    /**
     * @param fileDar the DAR
     * @return every `.dalf` entry, by entry name
     * @throws IOException when the DAR cannot be read as a zip
     */
    static List<Dalf> readAll(Path fileDar) throws IOException {
        List<Dalf> lstDalf = new ArrayList<>();
        try (ZipFile zip = new ZipFile(fileDar.toFile())) {
            List<ZipEntry> lstEntry = new ArrayList<>();
            Enumeration<? extends ZipEntry> enmEntry = zip.entries();
            while (enmEntry.hasMoreElements()) {
                ZipEntry entry = enmEntry.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(STR_DALF))
                    lstEntry.add(entry);
            }
            lstEntry.sort(Comparator.comparing(ZipEntry::getName));
            for (ZipEntry entry : lstEntry) {
                byte[] arrDalf;
                try (InputStream in = zip.getInputStream(entry)) {
                    arrDalf = in.readAllBytes();
                }
                lstDalf.add(unwrap(entry.getName(), arrDalf));
            }
        }
        return lstDalf;
    }


    /**
     * @param strEntry the entry name, which may carry the package id
     * @param arrDalf the entry's bytes
     * @return the entry unwrapped, or with a null id when no field matched
     */
    static Dalf unwrap(String strEntry, byte[] arrDalf) {
        List<Field> lstField = fieldsOf(arrDalf);
        if (lstField == null)
            return new Dalf(strEntry, null, null, null);

        Set<String> setHash = new HashSet<>();
        Matcher mtc = PAT_ID_IN_NAME.matcher(strEntry);
        if (mtc.find())
            setHash.add(mtc.group(1));
        for (Field field : lstField) {
            if (field.nWire() != N_WIRE_LEN)
                continue;
            String strText = new String(field.arrValue(), StandardCharsets.US_ASCII);
            if (PAT_HEX64.matcher(strText).matches())
                setHash.add(strText);
        }
        for (Field field : lstField) {
            if (field.nWire() != N_WIRE_LEN)
                continue;
            String strHash = sha256Of(field.arrValue());
            if (setHash.contains(strHash))
                return new Dalf(strEntry, strHash, field.arrValue(), lfOf(field.arrValue()));
        }
        return new Dalf(strEntry, null, null, null);
    }


    /**
     * @param arrPayload an ArchivePayload
     * @return `major.minor`, or null when the payload is not shaped as measured
     */
    static String lfOf(byte[] arrPayload) {
        List<Field> lstField = fieldsOf(arrPayload);
        if (lstField == null)
            return null;

        String strMinor = null;
        String strMajor = null;
        for (Field field : lstField) {
            if (field.nWire() != N_WIRE_LEN)
                continue;
            if (field.nNum() == N_FIELD_MINOR)
                strMinor = new String(field.arrValue(), StandardCharsets.UTF_8);
            else if (field.nNum() == N_FIELD_LF1)
                strMajor = "1";
            else if (field.nNum() == N_FIELD_LF2)
                strMajor = "2";
        }
        return strMajor == null || strMinor == null ? null : strMajor + "." + strMinor;
    }


    /**
     * @param arr bytes
     * @return their SHA-256, lower-case hex
     */
    static String sha256Of(byte[] arr) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable in this JVM", ex);
        }
        byte[] arrHash = digest.digest(arr);
        StringBuilder bld = new StringBuilder(arrHash.length * 2);
        for (byte bt : arrHash) {
            bld.append(Character.forDigit((bt >> 4) & 0x0f, 16));
            bld.append(Character.forDigit(bt & 0x0f, 16));
        }
        return bld.toString();
    }


    /**
     * The top level of a protobuf message, by its wire format.
     *
     * @param arr the message
     * @return its fields in order, or null when the bytes are not a
     *         well-formed message
     */
    private static List<Field> fieldsOf(byte[] arr) {
        List<Field> lstField = new ArrayList<>();
        int[] arrPos = {0};
        while (arrPos[0] < arr.length) {
            long nKey = varintOf(arr, arrPos);
            if (nKey < 0)
                return null;
            int nNum = (int) (nKey >>> 3);
            int nWire = (int) (nKey & 7);
            if (nNum <= 0)
                return null;

            if (nWire == N_WIRE_VARINT) {
                if (!skipVarint(arr, arrPos))
                    return null;
                lstField.add(new Field(nNum, nWire, null));
            }
            else if (nWire == N_WIRE_FIXED64 || nWire == N_WIRE_FIXED32) {
                int nLen = nWire == N_WIRE_FIXED64 ? 8 : 4;
                if (nLen > arr.length - arrPos[0])
                    return null;
                arrPos[0] += nLen;
                lstField.add(new Field(nNum, nWire, null));
            }
            else if (nWire == N_WIRE_LEN) {
                long nLen = varintOf(arr, arrPos);
                if (nLen < 0 || nLen > arr.length - arrPos[0])
                    return null;
                byte[] arrValue = new byte[(int) nLen];
                System.arraycopy(arr, arrPos[0], arrValue, 0, (int) nLen);
                arrPos[0] += (int) nLen;
                lstField.add(new Field(nNum, nWire, arrValue));
            }
            else {
                return null;
            }
        }
        return lstField;
    }


    /**
     * @return whether a varint of at most ten bytes was there to skip
     */
    private static boolean skipVarint(byte[] arr, int[] arrPos) {
        for (int cntByte = 0; cntByte < 10; cntByte++) {
            if (arrPos[0] >= arr.length)
                return false;
            if ((arr[arrPos[0]++] & 0x80) == 0)
                return true;
        }
        return false;
    }


    /**
     * A key or a length: never negative when well-formed.
     *
     * @return the varint at the position, advancing it; -1 when it runs past
     *         the end, is longer than ten bytes or does not fit a non-negative
     *         long
     */
    private static long varintOf(byte[] arr, int[] arrPos) {
        long nValue = 0;
        for (int nShift = 0; nShift < 64; nShift += 7) {
            if (arrPos[0] >= arr.length)
                return -1;
            int nByte = arr[arrPos[0]++] & 0xff;
            nValue |= (long) (nByte & 0x7f) << nShift;
            if ((nByte & 0x80) == 0)
                return nValue;
        }
        return -1;
    }

}
