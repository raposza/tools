// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.nio.file.Path;
import java.util.List;

/**
 * How an acquired archive is unpacked.
 *
 * <h2>One tool for both archive kinds</h2>
 *
 * `tar` is used for the tarballs AND for the Windows zip. Windows ships
 * `tar.exe`, which is libarchive and reads a zip as readily as a tarball, so
 * there is one command line here rather than one per platform - and no
 * PowerShell, whose archive cmdlet is a second dialect with its own failure
 * modes.
 *
 * <b>Compression is auto-detected, so no `z` flag is passed.</b> Naming the
 * codec is what would make this two commands: the same flag that is right for
 * a `.tar.gz` is wrong for the zip.
 *
 * <h2>The top directory is stripped, always</h2>
 *
 * Every archive acquired here carries exactly one top directory named for the
 * thing inside it - the platform for a DPM archive, the SDK version for an SDK
 * tarball - and the vendor's own installer removes it on the way in. Stripping
 * it means the caller knows where the contents landed without listing the
 * archive first, which is what lets the launcher path be composed rather than
 * searched for.
 *
 * Author Claude/bentzn
 */
public final class Extract {

    public static final String STR_BIN_TAR = "tar";

    public static final String STR_FLAG_EXTRACT = "-xf";

    public static final String STR_FLAG_DIR = "-C";

    public static final String STR_FLAG_STRIP = "--strip-components";

    public static final String STR_STRIP_ONE = "1";


    private Extract() {
    }


    /**
     * @param fileArchive the archive to unpack; never null
     * @param dirInto where its contents should land; never null, and it must
     *        exist - `tar` does not create it
     * @return the command line
     */
    public static List<String> lstCommand(Path fileArchive, Path dirInto) {
        if (fileArchive == null || dirInto == null)
            throw new IllegalArgumentException("an archive and a destination are required");

        return List.of(STR_BIN_TAR, STR_FLAG_EXTRACT, fileArchive.toString(),
                STR_FLAG_DIR, dirInto.toString(), STR_FLAG_STRIP, STR_STRIP_ONE);
    }

}
