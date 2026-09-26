// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.Edition;
import com.raposza.canton.install.VersionId;
import com.raposza.runtime.settings.RaposzaSettings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * One properties file per Canton version, under `~/.raposza/profiles`.
 *
 * <h2>A file per version rather than one file with sections</h2>
 *
 * A single settings file would need a version-keyed section format, a writer
 * that rewrites the whole thing to change one value, and an answer to what
 * happens when two windows are open on two versions at once. A file per version
 * has none of those: the last writer of a version's file wins, and it can only
 * ever be a window that had that version selected.
 *
 * They are also readable and editable by hand, which is the point of properties
 * rather than something denser. A file that has been edited into nonsense costs
 * the keys that are nonsense and nothing else - see
 * {@link SandboxProfile#ofProperties}.
 *
 * <h2>Reading never throws</h2>
 *
 * A profile that cannot be read gives the fallback. There is no failure here
 * worth refusing to open a window over: the worst case is a form showing
 * defaults, which is what a new version shows anyway. WRITING does throw, since
 * a save that silently did nothing would be discovered the next time the window
 * opened and the settings were gone.
 *
 * Author Claude/bentzn
 */
public final class ProfileStore {

    public static final String STR_DIR_DEFAULT = ".raposza/profiles";

    public static final String STR_SUFFIX = ".properties";

    private final Path dirRoot;


    /**
     * @param dirRootNew where the profiles are kept; never null
     */
    public ProfileStore(Path dirRootNew) {
        if (dirRootNew == null)
            throw new IllegalArgumentException("a profile directory is required");
        this.dirRoot = dirRootNew.toAbsolutePath().normalize();
    }


    public static ProfileStore ofDefaults() {
        return new ProfileStore(RaposzaSettings.current().dirProfiles());
    }


    public Path dirRoot() {
        return dirRoot;
    }


    /**
     * @param version the Canton version
     * @param edition its edition, or null for unknown
     * @return where that version's profile is, whether or not it exists
     */
    public Path fileOf(VersionId version, Edition edition) {
        return dirRoot.resolve(VersionKey.strOf(version, edition) + STR_SUFFIX);
    }


    /**
     * @param inst the installation
     * @param profileFallback what an absent or unreadable profile becomes
     * @return the profile for it
     */
    public SandboxProfile read(CantonInstallation inst, SandboxProfile profileFallback) {
        if (inst == null)
            throw new IllegalArgumentException("an installation is required");
        return read(inst.version(), inst.edition(), profileFallback);
    }


    /**
     * @param version the Canton version
     * @param edition its edition, or null for unknown
     * @param profileFallback what an absent or unreadable profile becomes; when
     *        null, the version's own defaults are used
     * @return the profile for that version; never null
     */
    public SandboxProfile read(VersionId version, Edition edition,
            SandboxProfile profileFallback) {
        SandboxProfile profileElse = profileFallback == null
                ? SandboxProfile.ofDefaults(version, edition) : profileFallback;

        Path file = fileOf(version, edition);
        if (!Files.isRegularFile(file))
            return profileElse;

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }
        catch (IOException ex) {
            return profileElse;
        }

        try {
            return SandboxProfile.ofProperties(props, profileElse);
        }
        catch (RuntimeException ex) {
            // ofProperties falls back key by key, so this is a file whose
            // every key is unusable. Same answer, one level out.
            return profileElse;
        }
    }


    /**
     * @param inst the installation
     * @param profile what to remember for it
     */
    public void write(CantonInstallation inst, SandboxProfile profile) {
        if (inst == null)
            throw new IllegalArgumentException("an installation is required");
        write(inst.version(), inst.edition(), profile);
    }


    /**
     * @param version the Canton version
     * @param edition its edition, or null for unknown
     * @param profile what to remember for it; never null
     * @throws IllegalStateException when it cannot be written, because a save
     *         that failed quietly is found out a week later
     */
    public void write(VersionId version, Edition edition, SandboxProfile profile) {
        if (profile == null)
            throw new IllegalArgumentException("a profile is required");

        Path file = fileOf(version, edition);
        try {
            Files.createDirectories(dirRoot);
            try (OutputStream out = Files.newOutputStream(file)) {
                profile.toProperties().store(out,
                        "raposza sandbox - settings for Canton "
                                + VersionKey.strOf(version, edition));
            }
        }
        catch (IOException ex) {
            throw new IllegalStateException("could not write " + file + ": " + ex.getMessage(),
                    ex);
        }
    }
}
