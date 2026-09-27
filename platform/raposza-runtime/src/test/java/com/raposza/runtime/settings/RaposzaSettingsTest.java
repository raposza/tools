// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.raposza.runtime.localnet.LocalNetPorts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The settings store, driven with no window and no file system of the
 * operator's.
 *
 * Author Claude/bentzn
 */
class RaposzaSettingsTest {

    @Test
    void every_directory_hangs_under_the_one_setting() {
        RaposzaSettings settings = RaposzaSettings.ofDefaults();
        assertEquals(RaposzaSettings.dirHomeDefault(), settings.dirHome());
        assertEquals(settings.dirHome().resolve("sandbox"), settings.dirSandbox());
        assertEquals(settings.dirHome().resolve("snapshots"), settings.dirSnapshots());
        assertEquals(settings.dirHome().resolve("profiles"), settings.dirProfiles());
        assertEquals(settings.dirHome().resolve("fixtures/petshop"), settings.dirFixtures());
        assertEquals(settings.dirHome().resolve("jwtmint/keys"), settings.dirMintKeys());
        assertEquals(RaposzaSettings.N_PORT_MINT_DEFAULT, settings.nPortMint());
        assertEquals(RaposzaSettings.N_PORT_DISCOVERY_DEFAULT, settings.nPortDiscovery());
        assertEquals(RaposzaSettings.STR_LINE_DEFAULT, settings.strLine());
    }


    /** Moving the one setting moves everything, which is the point of it. */
    @Test
    void moving_the_root_moves_everything_under_it(@TempDir Path dirTmp) throws IOException {
        Properties props = new Properties();
        props.setProperty(RaposzaSettings.STR_KEY_DIR_HOME, dirTmp.resolve("elsewhere")
                .toString());
        RaposzaSettings settings = RaposzaSettings.read(write(dirTmp, props));
        Path dirRoot = dirTmp.resolve("elsewhere");
        assertEquals(dirRoot.resolve("sandbox"), settings.dirSandbox());
        assertEquals(dirRoot.resolve("snapshots"), settings.dirSnapshots());
        assertEquals(dirRoot.resolve("profiles"), settings.dirProfiles());
        assertEquals(dirRoot.resolve("fixtures/petshop"), settings.dirFixtures());
        assertEquals(dirRoot.resolve("jwtmint/keys"), settings.dirMintKeys());
    }


    @Test
    void an_absent_file_gives_the_defaults(@TempDir Path dirTmp) {
        assertEquals(RaposzaSettings.ofDefaults(),
                RaposzaSettings.read(dirTmp.resolve("nothing-here.properties")));
    }


    @Test
    void a_round_trip_through_properties_changes_nothing() {
        RaposzaSettings settings = RaposzaSettings.ofDefaults();
        assertEquals(settings, RaposzaSettings.ofProperties(settings.toProperties(), settings));
    }


    @Test
    void a_file_overrides_only_the_keys_it_carries(@TempDir Path dirTmp) throws IOException {
        Properties props = new Properties();
        props.setProperty(RaposzaSettings.STR_KEY_DIR_HOME, dirTmp.resolve("out").toString());
        props.setProperty(RaposzaSettings.STR_KEY_PORT_MINT, "32600");
        Path file = write(dirTmp, props);

        RaposzaSettings settings = RaposzaSettings.read(file);
        assertEquals(dirTmp.resolve("out"), settings.dirHome());
        assertEquals(32600, settings.nPortMint());
        // Untouched keys are still the defaults - the whole point of a per-key
        // fallback rather than a per-file one.
        assertEquals(RaposzaSettings.N_PORT_DISCOVERY_DEFAULT, settings.nPortDiscovery());
        assertEquals(RaposzaSettings.STR_LINE_DEFAULT, settings.strLine());
    }


    /** The rule that keeps a hand-edited file from stopping the window. */
    @Test
    void one_bad_key_costs_only_itself(@TempDir Path dirTmp) throws IOException {
        Properties props = new Properties();
        props.setProperty(RaposzaSettings.STR_KEY_PORT_MINT, "not a number");
        props.setProperty(RaposzaSettings.STR_KEY_PORT_DISCOVERY, "70000");
        props.setProperty(RaposzaSettings.STR_KEY_DIR_HOME, dirTmp.resolve("out").toString());
        Path file = write(dirTmp, props);

        RaposzaSettings settings = RaposzaSettings.read(file);
        assertEquals(RaposzaSettings.N_PORT_MINT_DEFAULT, settings.nPortMint());
        assertEquals(RaposzaSettings.N_PORT_DISCOVERY_DEFAULT, settings.nPortDiscovery());
        assertEquals(dirTmp.resolve("out"), settings.dirHome());
    }


    /**
     * EVERY PORT IN ITS CLASS. A settings file written before the classes
     * carries 22010, which is not a node port, and reads as the default rather
     * than stopping the window; a first port past the cap would push the
     * block out of 30xxx and is refused the same way.
     */
    @Test
    void a_port_outside_its_class_is_the_default(@TempDir Path dirTmp) throws IOException {
        Properties props = new Properties();
        props.setProperty(RaposzaSettings.STR_KEY_PORT_FIRST, "22010");
        props.setProperty(RaposzaSettings.STR_KEY_PORT_POSTGRES, "30101");
        props.setProperty(RaposzaSettings.STR_KEY_PORT_MINT, "31002");
        Path file = write(dirTmp, props);

        RaposzaSettings settings = RaposzaSettings.read(file);
        assertEquals(RaposzaSettings.N_PORT_FIRST_DEFAULT, settings.nPortFirst());
        assertEquals(RaposzaSettings.N_PORT_POSTGRES_DEFAULT, settings.nPortPostgres());
        assertEquals(RaposzaSettings.N_PORT_MINT_DEFAULT, settings.nPortMint());
        assertEquals(30010, RaposzaSettings.N_PORT_FIRST_DEFAULT);
    }


    /**
     * THE FILE IS REPAIRED ONCE. A port outside its class is written back as
     * the value it fell back to, so the second read finds nothing to warn
     * about; a key that is not a number, and a key this class does not know,
     * stay exactly as they were.
     */
    @Test
    void a_port_outside_its_class_is_rewritten_once(@TempDir Path dirTmp) throws IOException {
        Properties props = new Properties();
        props.setProperty(RaposzaSettings.STR_KEY_PORT_FIRST, "22010");
        props.setProperty(RaposzaSettings.STR_KEY_PORT_MINT, "not a number");
        props.setProperty("somebody.else", "kept");
        Path file = write(dirTmp, props);

        assertEquals(30010, RaposzaSettings.read(file).nPortFirst());

        Properties propsBack = new Properties();
        try (java.io.InputStream in = Files.newInputStream(file)) {
            propsBack.load(in);
        }
        assertEquals("30010", propsBack.getProperty(RaposzaSettings.STR_KEY_PORT_FIRST));
        assertEquals("not a number", propsBack.getProperty(RaposzaSettings.STR_KEY_PORT_MINT));
        assertEquals("kept", propsBack.getProperty("somebody.else"));

        long nModified = Files.getLastModifiedTime(file).toMillis();
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(1000L));
        RaposzaSettings.read(file);
        assertEquals(1000L, Files.getLastModifiedTime(file).toMillis(),
                "a clean file was written again, last written at " + nModified);
    }


    @Test
    void the_first_port_is_capped_where_the_block_would_leave_its_thousand() {
        RaposzaSettings settings = RaposzaSettings.ofDefaults();
        new RaposzaSettings(settings.dirHome(), settings.nPortMint(), settings.nPortDiscovery(),
                settings.strLine(), settings.strLauncher(), 30957, settings.nPortPostgres(),
                settings.nSecondsReady());
        assertThrows(IllegalArgumentException.class,
                () -> new RaposzaSettings(settings.dirHome(), settings.nPortMint(),
                        settings.nPortDiscovery(), settings.strLine(), settings.strLauncher(),
                        30958, settings.nPortPostgres(), settings.nSecondsReady()));
    }


    @Test
    void a_launcher_is_upper_cased_and_a_blank_line_is_the_default() {
        RaposzaSettings settings = with("daemon", "  ");
        assertEquals("DAEMON", settings.strLauncher());
        assertEquals(RaposzaSettings.STR_LINE_DEFAULT, settings.strLine());
    }


    /** The constructor still refuses what the file-reading path forgives. */
    @Test
    void the_constructor_refuses_an_impossible_port() {
        RaposzaSettings settings = RaposzaSettings.ofDefaults();
        assertThrows(IllegalArgumentException.class,
                () -> new RaposzaSettings(settings.dirHome(), 70000, settings.nPortDiscovery(),
                        settings.strLine(), settings.strLauncher(),
                        settings.nPortFirst(), settings.nPortPostgres(),
                        settings.nSecondsReady()));
    }


    @Test
    void store_writes_the_file_and_installs_what_it_wrote(@TempDir Path dirTmp)
            throws IOException {
        Path file = dirTmp.resolve("settings.properties");
        String strWas = System.getProperty(RaposzaSettings.STR_PROP_FILE);
        System.setProperty(RaposzaSettings.STR_PROP_FILE, file.toString());
        try {
            RaposzaSettings settingsNew = with("SUBCOMMAND", "2.10");
            RaposzaSettings.store(settingsNew);
            assertTrue(Files.isRegularFile(file));
            assertEquals("2.10", RaposzaSettings.current().strLine());
            assertEquals("2.10", RaposzaSettings.reload().strLine());
            assertNotEquals(RaposzaSettings.STR_LINE_DEFAULT,
                    RaposzaSettings.current().strLine());
        }
        finally {
            if (strWas == null)
                System.clearProperty(RaposzaSettings.STR_PROP_FILE);
            else
                System.setProperty(RaposzaSettings.STR_PROP_FILE, strWas);
            RaposzaSettings.reload();
        }
    }


    /**
     * The fixture offer, both ways round, plus the two things a hand-edited
     * file can say that are neither.
     */
    @Test
    void the_fixture_offer_survives_a_write_and_a_read(@TempDir Path dirTmp) throws IOException {
        assertTrue(RaposzaSettings.ofDefaults().flagOfferAviation());

        Properties props = new Properties();
        props.setProperty(RaposzaSettings.STR_KEY_OFFER_AVIATION, "false");
        assertFalse(RaposzaSettings.read(write(dirTmp, props)).flagOfferAviation());

        // A KEY NOBODY CAN PARSE IS THE DEFAULT, not false. Somebody who wrote
        // `yes` meant yes, and reading that as an answer of no would turn a
        // typo into a silently different application.
        props.setProperty(RaposzaSettings.STR_KEY_OFFER_AVIATION, "yes");
        assertTrue(RaposzaSettings.read(write(dirTmp, props)).flagOfferAviation());

        props.remove(RaposzaSettings.STR_KEY_OFFER_AVIATION);
        assertTrue(RaposzaSettings.read(write(dirTmp, props)).flagOfferAviation());
    }


    /** The eight-component form takes the defaults, so no old caller changed. */
    @Test
    void the_short_constructor_offers_the_fixture() {
        assertTrue(with("SUBCOMMAND", "3.5").flagOfferAviation());
        assertTrue(with("SUBCOMMAND", "3.5").flagOfferPharma());
        assertEquals(Boolean.toString(RaposzaSettings.FLAG_OFFER_AVIATION_DEFAULT),
                RaposzaSettings.ofDefaults().toProperties()
                        .getProperty(RaposzaSettings.STR_KEY_OFFER_AVIATION));
        assertEquals(Boolean.toString(RaposzaSettings.FLAG_OFFER_PHARMA_DEFAULT),
                RaposzaSettings.ofDefaults().toProperties()
                        .getProperty(RaposzaSettings.STR_KEY_OFFER_PHARMA));
    }


    /** THE TWO ANSWERS ARE SEPARATE - one fixture per topology. */
    @Test
    void neverOnOneFixtureLeavesTheOtherOffered() {
        Properties props = RaposzaSettings.ofDefaults().toProperties();
        props.setProperty(RaposzaSettings.STR_KEY_OFFER_PHARMA, "false");
        RaposzaSettings settings = RaposzaSettings.ofProperties(props,
                RaposzaSettings.ofDefaults());

        assertFalse(settings.flagOfferPharma());
        assertTrue(settings.flagOfferAviation());
    }


    /**
     * `todo.md` A-42: LocalNetND starts on THESE numbers. Every one of the
     * three moved away from its default, so a numbering still composed from
     * `LocalNetPorts.ofDefaults()` fails on each of them.
     */
    @Test
    void localnet_binds_the_three_numbers_the_settings_hold(@TempDir Path dirTmp)
            throws IOException {
        assertEquals(LocalNetPorts.ofDefaults(), RaposzaSettings.ofDefaults().portsLocalNet());

        Properties props = new Properties();
        props.setProperty(RaposzaSettings.STR_KEY_PORT_FIRST, "30400");
        props.setProperty(RaposzaSettings.STR_KEY_PORT_UI_FIRST, "31500");
        props.setProperty(RaposzaSettings.STR_KEY_PORT_POSTGRES, "32400");
        RaposzaSettings settings = RaposzaSettings.read(write(dirTmp, props));

        LocalNetPorts ports = settings.portsLocalNet();
        assertEquals(30400, ports.nPortFirst());
        assertEquals(30400, ports.nPortLedger("sv"));
        assertEquals(31500, ports.nPortUi("sv"));
        assertEquals(31510, ports.nPortUi("app-provider"));
        assertEquals(31520, ports.nPortUi("app-user"));
        assertEquals(32400, ports.nPortPostgres());
    }


    /**
     * THE WEB UI BLOCK STAYS IN 31xxx. A file from before the classes carries
     * nothing for it; one naming the bundle's own 22060-era numbers, or a first
     * port that would push app-user past 31999, reads as the default.
     */
    @Test
    void a_web_ui_first_port_outside_its_class_is_the_default(@TempDir Path dirTmp)
            throws IOException {
        assertEquals(31000, RaposzaSettings.ofDefaults().nPortUiFirst());
        assertEquals("31000", RaposzaSettings.ofDefaults().toProperties()
                .getProperty(RaposzaSettings.STR_KEY_PORT_UI_FIRST));

        Properties props = new Properties();
        props.setProperty(RaposzaSettings.STR_KEY_PORT_UI_FIRST, "22060");
        assertEquals(RaposzaSettings.N_PORT_UI_FIRST_DEFAULT,
                RaposzaSettings.read(write(dirTmp, props)).nPortUiFirst());
        props.setProperty(RaposzaSettings.STR_KEY_PORT_UI_FIRST, "31980");
        assertEquals(RaposzaSettings.N_PORT_UI_FIRST_DEFAULT,
                RaposzaSettings.read(write(dirTmp, props)).nPortUiFirst());
        props.setProperty(RaposzaSettings.STR_KEY_PORT_UI_FIRST, "31979");
        assertEquals(31979, RaposzaSettings.read(write(dirTmp, props)).nPortUiFirst());

        RaposzaSettings settings = RaposzaSettings.ofDefaults();
        assertThrows(IllegalArgumentException.class, () -> withUiFirst(settings, 31980));
        assertThrows(IllegalArgumentException.class, () -> withUiFirst(settings, 30010));
        assertEquals(31979, withUiFirst(settings, 31979).nPortUiFirst());
    }


    /** A write of any OTHER setting keeps a moved web UI block where it was. */
    @Test
    void the_web_ui_first_port_survives_a_round_trip() {
        RaposzaSettings settings = withUiFirst(RaposzaSettings.ofDefaults(), 31200);
        RaposzaSettings settingsBack = RaposzaSettings.ofProperties(settings.toProperties(),
                RaposzaSettings.ofDefaults());
        assertEquals(31200, settingsBack.nPortUiFirst());
        assertEquals(settings, settingsBack);
    }


    private static RaposzaSettings withUiFirst(RaposzaSettings settings, int nPortUiFirst) {
        return new RaposzaSettings(settings.dirHome(), settings.nPortMint(),
                settings.nPortDiscovery(), settings.strLine(), settings.strLauncher(),
                settings.nPortFirst(), settings.nPortPostgres(), settings.nSecondsReady(),
                settings.flagOfferAviation(), settings.flagOfferPharma(), settings.strUrlOidc(),
                nPortUiFirst, settings.dirDaml(), settings.dirDpm(), settings.dirSplice());
    }


    /** A file from before A-45, and one with the keys blank, both mean today. */
    @Test
    void blank_installation_directories_are_the_default(@TempDir Path dirTmp)
            throws IOException {
        RaposzaSettings settings = RaposzaSettings.ofDefaults();
        assertNull(settings.dirDaml());
        assertNull(settings.dirDpm());
        assertNull(settings.dirSplice());

        Properties props = new Properties();
        props.setProperty(RaposzaSettings.STR_KEY_DIR_DAML, "");
        props.setProperty(RaposzaSettings.STR_KEY_DIR_DPM, "   ");
        RaposzaSettings settingsRead = RaposzaSettings.read(write(dirTmp, props));
        assertNull(settingsRead.dirDaml());
        assertNull(settingsRead.dirDpm());
        assertNull(settingsRead.dirSplice());
        assertEquals(RaposzaSettings.ofDefaults(), settingsRead);
    }


    @Test
    void each_installation_directory_round_trips(@TempDir Path dirTmp) throws IOException {
        Properties props = new Properties();
        props.setProperty(RaposzaSettings.STR_KEY_DIR_DAML, dirTmp.resolve("daml").toString());
        props.setProperty(RaposzaSettings.STR_KEY_DIR_DPM, dirTmp.resolve("dpm").toString());
        props.setProperty(RaposzaSettings.STR_KEY_DIR_SPLICE,
                dirTmp.resolve("splice").toString());
        RaposzaSettings settings = RaposzaSettings.read(write(dirTmp, props));
        assertEquals(dirTmp.resolve("daml"), settings.dirDaml());
        assertEquals(dirTmp.resolve("dpm"), settings.dirDpm());
        assertEquals(dirTmp.resolve("splice"), settings.dirSplice());

        RaposzaSettings settingsBack = RaposzaSettings.ofProperties(settings.toProperties(),
                RaposzaSettings.ofDefaults());
        assertEquals(settings, settingsBack);
        assertEquals("", RaposzaSettings.ofDefaults().toProperties()
                .getProperty(RaposzaSettings.STR_KEY_DIR_SPLICE));
    }


    /** One unusable path costs its own key and no other. */
    @Test
    void a_nonsense_installation_directory_falls_back_per_key(@TempDir Path dirTmp)
            throws IOException {
        Properties props = new Properties();
        props.setProperty(RaposzaSettings.STR_KEY_DIR_DAML, "bad\u0000path");
        props.setProperty(RaposzaSettings.STR_KEY_DIR_DPM, dirTmp.resolve("dpm").toString());
        RaposzaSettings settings = RaposzaSettings.read(write(dirTmp, props));
        assertNull(settings.dirDaml());
        assertEquals(dirTmp.resolve("dpm"), settings.dirDpm());
    }


    private static RaposzaSettings with(String strLauncher, String strLine) {
        RaposzaSettings settings = RaposzaSettings.ofDefaults();
        return new RaposzaSettings(settings.dirHome(), settings.nPortMint(),
                settings.nPortDiscovery(), strLine, strLauncher,
                settings.nPortFirst(), settings.nPortPostgres(), settings.nSecondsReady());
    }


    private static Path write(Path dirTmp, Properties props) throws IOException {
        Path file = dirTmp.resolve("settings.properties");
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "test");
        }
        return file;
    }
}
