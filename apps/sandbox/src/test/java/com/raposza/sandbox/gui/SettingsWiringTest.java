// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.raposza.canton.install.Edition;
import com.raposza.canton.install.VersionId;
import com.raposza.runtime.settings.RaposzaSettings;
import com.raposza.sandbox.app.SandboxOptions;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The point of B-3, asserted: every global default is READ from the settings
 * rather than compiled in.
 *
 * The check that matters is not that a settings file can be written - the store
 * has its own tests for that - but that a value written into it reaches the
 * static methods the rest of the application calls. A settings pane wired to
 * nothing would pass every test in the store and change nothing at all, which
 * is exactly the failure this class exists to catch.
 *
 * No window is constructed. Every method here is static and none of them needs
 * a graphics environment.
 *
 * Author Claude/bentzn
 */
class SettingsWiringTest {

    private String strWas;


    @AfterEach
    void restore() {
        if (strWas == null)
            System.clearProperty(RaposzaSettings.STR_PROP_FILE);
        else
            System.setProperty(RaposzaSettings.STR_PROP_FILE, strWas);
        RaposzaSettings.reload();
    }


    /** ONE setting moves every caller, which is the whole of B-3. */
    @Test
    void every_caller_follows_the_one_directory(@TempDir Path dirTmp) throws IOException {
        Path dirRoot = dirTmp.resolve("elsewhere");
        Properties props = new Properties();
        props.setProperty(RaposzaSettings.STR_KEY_DIR_HOME, dirRoot.toString());
        use(dirTmp, props);

        VersionId version = VersionId.parse("3.5.12");
        String strKey = VersionKey.strOf(version, Edition.OPEN_SOURCE);
        assertEquals(dirRoot.resolve("sandbox"), SandboxOptions.dirWorkDefault());
        assertEquals(dirRoot.resolve("sandbox").resolve(strKey),
                SandboxProfile.dirRunDefault(version, Edition.OPEN_SOURCE));
        assertEquals(dirRoot.resolve("snapshots").resolve(strKey),
                Snapshots.dirRootOf(version, Edition.OPEN_SOURCE));
        assertEquals(dirRoot.resolve("profiles"), ProfileStore.ofDefaults().dirRoot());
    }


    @Test
    void the_default_line_follows_the_settings(@TempDir Path dirTmp) throws IOException {
        Properties props = new Properties();
        props.setProperty(RaposzaSettings.STR_KEY_LINE, "2.10");
        use(dirTmp, props);

        assertEquals("2.10", SandboxOptions.strLineDefault());
        assertEquals("2.10", SandboxOptions.ofDefaults().strLine());
    }


    @Test
    void a_new_version_starts_on_the_settings_values(@TempDir Path dirTmp) throws IOException {
        Properties props = new Properties();
        props.setProperty(RaposzaSettings.STR_KEY_PORT_FIRST, "30400");
        props.setProperty(RaposzaSettings.STR_KEY_PORT_POSTGRES, "32400");
        props.setProperty(RaposzaSettings.STR_KEY_SECONDS_READY, "90");
        use(dirTmp, props);

        SandboxProfile profile = SandboxProfile.ofDefaults(VersionId.parse("3.5.12"),
                Edition.OPEN_SOURCE);
        assertEquals(30400, profile.nPortFirst());
        assertEquals(32400, profile.nPortPostgres());
        assertEquals(90, profile.nSecondsReady());
        assertEquals(32400, SandboxOptions.ofDefaults().nPortPostgres());
    }


    private void use(Path dirTmp, Properties props) throws IOException {
        Path file = dirTmp.resolve("settings.properties");
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "test");
        }
        strWas = System.getProperty(RaposzaSettings.STR_PROP_FILE);
        System.setProperty(RaposzaSettings.STR_PROP_FILE, file.toString());
        RaposzaSettings.reload();
    }
}
