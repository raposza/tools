// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The filesystem reads that discovery does, for the two things this module
 * discovers.
 *
 * DELIBERATELY ONE METHOD. `CantonInstallations` and `PqsInstallations` carried
 * a byte-identical `listDirectories`, and that is the whole of what they have
 * in common: what a version directory looks like, where the jar is inside it,
 * how an edition is decided and what a missing file means are different
 * questions with different answers on each side. An installation FRAMEWORK for
 * two callers would have to make those the same, and they are not.
 *
 * Author Claude/bentzn
 */
final class InstallFs {

    private static final Logger log = LoggerFactory.getLogger(InstallFs.class);

    private InstallFs() {
    }


    /**
     * A directory that cannot be read is EMPTY and not an error. Discovery runs
     * over machines that may have none of these roots, and a missing `~/.daml`
     * is the normal case rather than a fault.
     *
     * @param dirParent where to look; need not exist
     * @return its subdirectories, in whatever order the filesystem gives them
     */
    static List<Path> listDirectories(Path dirParent) {
        List<Path> lstDir = new ArrayList<>();
        if (!Files.isDirectory(dirParent))
            return lstDir;

        try (DirectoryStream<Path> strmDir = Files.newDirectoryStream(dirParent)) {
            for (Path path : strmDir) {
                if (Files.isDirectory(path))
                    lstDir.add(path);
            }
        }
        catch (IOException ex) {
            log.warn("could not list {}: {}", dirParent, ex.toString());
        }
        return lstDir;
    }

}
