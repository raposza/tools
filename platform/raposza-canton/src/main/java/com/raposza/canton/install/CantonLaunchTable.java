// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <b>GENERATED - DO NOT EDIT.</b> What each Canton binary accepts on its command
 * line: the subcommands it offers, the flags each one takes, and the entry point
 * in its jar manifest.
 *
 * <b>Measured, not documented.</b> Every row was read from the output of
 * {@code java -jar canton.jar --help} on that exact binary, and from the jar's
 * own manifest. Nothing here is inferred from a release note or from the major
 * version - which matters, because the major version is a BAD predictor: the
 * {@code sandbox} subcommand exists from 3.4.4 and not from 3.0, and {@code
 * sandbox-interactive} only from 3.5.
 *
 * <b>An absent version is UNKNOWN, never NO.</b> A lookup for a version that was
 * not measured returns empty rather than a default, and the caller decides what
 * to do about it. A table that answered for versions it had never seen would be
 * the hardcoded major-version rule again, wearing a data structure.
 *
 * Author Claude/bentzn
 */
public final class CantonLaunchTable {

    /** The scope key for flags that are not under any subcommand. */
    public static final String STR_SCOPE_GLOBAL = "(global)";

    /** How every measured binary is started. */
    public static final String STR_FORM = "java -jar <runtimeJar> <argv>";

    private static final List<Entry> LST_ENTRY = lstEntryOf();


    private CantonLaunchTable() {
    }


    /**
     * One measured binary.
     *
     * @param version the Canton version
     * @param edition the edition, UNKNOWN for a Daml Assistant install
     *        which declares none
     * @param strMainClass the jar manifest entry point
     * @param lstCommand the subcommands, in the order the usage line lists them
     * @param mapFlag scope to flags; the scope is a subcommand name or {@link
     *        #STR_SCOPE_GLOBAL}
     */
    public record Entry(VersionId version, Edition edition, String strMainClass,
            List<String> lstCommand, Map<String, List<String>> mapFlag) {

        public Entry {
            lstCommand = List.copyOf(lstCommand);
            mapFlag = Collections.unmodifiableMap(new LinkedHashMap<>(mapFlag));
        }


        /**
         * @param strCommand a subcommand name
         * @return whether this binary offers it
         */
        public boolean hasCommand(String strCommand) {
            return lstCommand.contains(strCommand);
        }


        /**
         * @param strScope a subcommand name, or STR_SCOPE_GLOBAL
         * @param strFlag the flag, dashes included
         * @return whether that scope accepts it
         */
        public boolean hasFlag(String strScope, String strFlag) {
            return mapFlag.getOrDefault(strScope, List.of()).contains(strFlag);
        }


        /**
         * @param strScope a subcommand name, or STR_SCOPE_GLOBAL
         * @return its flags, empty when the scope is not measured
         */
        public List<String> lstFlag(String strScope) {
            return mapFlag.getOrDefault(strScope, List.of());
        }
    }


    /**
     * @return every measured binary, in version order
     */
    public static List<Entry> lstEntry() {
        return LST_ENTRY;
    }


    /**
     * @param version the version to look up
     * @param edition its edition; UNKNOWN matches any measured edition of that
     *        version, because the Daml Assistant declares none
     * @return the entry, or empty when that binary was not measured
     */
    public static Optional<Entry> find(VersionId version, Edition edition) {
        if (version == null)
            return Optional.empty();

        Entry entryLoose = null;
        for (Entry entry : LST_ENTRY) {
            if (!entry.version().equals(version))
                continue;
            if (edition == null || edition == Edition.UNKNOWN
                    || edition == entry.edition())
                return Optional.of(entry);
            entryLoose = entryLoose == null ? entry : entryLoose;
        }

        // An edition that does not match is still the same version of the same
        // vendor binary, and its command line is the fact being asked for. It is
        // returned rather than refused, and the caller can compare editions if
        // that ever matters - the one measured difference between them is the
        // manifest entry point.
        return Optional.ofNullable(entryLoose);
    }


    /**
     * @param version the version to look up
     * @return the entry, or empty when that version was not measured
     */
    public static Optional<Entry> find(VersionId version) {
        return find(version, Edition.UNKNOWN);
    }


    /**
     * @param version the version to look up
     * @param strCommand a subcommand name
     * @return whether that binary offers it; empty when the version was not
     *         measured, which is NOT the same as false
     */
    public static Optional<Boolean> hasCommand(VersionId version, String strCommand) {
        return find(version).map(entry -> entry.hasCommand(strCommand));
    }


    private static List<Entry> lstEntryOf() {
        List<Entry> lst = new ArrayList<>();
        lst.add(entry2_8_12_OPEN_SOURCE());
        lst.add(entry2_9_1_OPEN_SOURCE());
        lst.add(entry2_9_6_OPEN_SOURCE());
        lst.add(entry2_9_7_OPEN_SOURCE());
        lst.add(entry2_10_0_OPEN_SOURCE());
        lst.add(entry2_10_2_OPEN_SOURCE());
        lst.add(entry2_10_4_OPEN_SOURCE());
        lst.add(entry3_4_4_ENTERPRISE());
        lst.add(entry3_4_5_ENTERPRISE());
        lst.add(entry3_4_6_ENTERPRISE());
        lst.add(entry3_4_7_ENTERPRISE());
        lst.add(entry3_4_8_ENTERPRISE());
        lst.add(entry3_4_9_ENTERPRISE());
        lst.add(entry3_4_10_ENTERPRISE());
        lst.add(entry3_4_10_OPEN_SOURCE());
        lst.add(entry3_4_11_ENTERPRISE());
        lst.add(entry3_4_11_OPEN_SOURCE());
        lst.add(entry3_5_1_OPEN_SOURCE());
        lst.add(entry3_5_2_OPEN_SOURCE());
        lst.add(entry3_5_3_OPEN_SOURCE());
        lst.add(entry3_5_4_OPEN_SOURCE());
        lst.add(entry3_5_5_OPEN_SOURCE());
        lst.add(entry3_5_6_OPEN_SOURCE());
        lst.add(entry3_5_10_OPEN_SOURCE());
        lst.add(entry3_5_11_OPEN_SOURCE());
        lst.add(entry3_5_12_OPEN_SOURCE());
        lst.add(entry3_5_13_OPEN_SOURCE());
        lst.add(entry3_5_14_OPEN_SOURCE());
        lst.add(entry3_5_15_OPEN_SOURCE());
        lst.add(entry3_5_16_OPEN_SOURCE());
        lst.add(entry3_5_17_OPEN_SOURCE());
        lst.add(entry3_5_18_OPEN_SOURCE());
        return List.copyOf(lst);
    }


    private static Entry entry2_8_12_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--auto-connect-local", "--bootstrap", "--config", "--debug",
                        "--help", "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));

        return new Entry(VersionId.parse("2.8.12"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate"),
                mapFlag);
    }


    private static Entry entry2_9_1_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--auto-connect-local", "--bootstrap", "--config", "--debug",
                        "--help", "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));

        return new Entry(VersionId.parse("2.9.1"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate"),
                mapFlag);
    }


    private static Entry entry2_9_6_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--auto-connect-local", "--bootstrap", "--config", "--debug",
                        "--help", "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));

        return new Entry(VersionId.parse("2.9.6"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate"),
                mapFlag);
    }


    private static Entry entry2_9_7_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--auto-connect-local", "--bootstrap", "--config", "--debug",
                        "--help", "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));

        return new Entry(VersionId.parse("2.9.7"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate"),
                mapFlag);
    }


    private static Entry entry2_10_0_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--auto-connect-local", "--bootstrap", "--config", "--debug",
                        "--help", "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));

        return new Entry(VersionId.parse("2.10.0"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate"),
                mapFlag);
    }


    private static Entry entry2_10_2_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--auto-connect-local", "--bootstrap", "--config", "--debug",
                        "--help", "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));

        return new Entry(VersionId.parse("2.10.2"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate"),
                mapFlag);
    }


    private static Entry entry2_10_4_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--auto-connect-local", "--bootstrap", "--config", "--debug",
                        "--help", "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));

        return new Entry(VersionId.parse("2.10.4"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate"),
                mapFlag);
    }


    private static Entry entry3_4_4_ENTERPRISE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.4.4"), Edition.ENTERPRISE,
                "com.digitalasset.canton.CantonEnterpriseApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_4_5_ENTERPRISE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.4.5"), Edition.ENTERPRISE,
                "com.digitalasset.canton.CantonEnterpriseApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_4_6_ENTERPRISE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.4.6"), Edition.ENTERPRISE,
                "com.digitalasset.canton.CantonEnterpriseApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_4_7_ENTERPRISE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.4.7"), Edition.ENTERPRISE,
                "com.digitalasset.canton.CantonEnterpriseApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_4_8_ENTERPRISE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.4.8"), Edition.ENTERPRISE,
                "com.digitalasset.canton.CantonEnterpriseApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_4_9_ENTERPRISE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.4.9"), Edition.ENTERPRISE,
                "com.digitalasset.canton.CantonEnterpriseApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_4_10_ENTERPRISE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.4.10"), Edition.ENTERPRISE,
                "com.digitalasset.canton.CantonEnterpriseApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_4_10_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.4.10"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_4_11_ENTERPRISE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.4.11"), Edition.ENTERPRISE,
                "com.digitalasset.canton.CantonEnterpriseApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_4_11_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-last-errors", "--log-level-canton",
                        "--log-level-root", "--log-level-stdout", "--log-profile",
                        "--log-truncate", "--manual-start", "--no-tty", "--verbose",
                        "--version", "-C", "-D", "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.4.11"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_5_1_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-access", "--log-access-errors",
                        "--log-access-errors-filename", "--log-access-filename",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-level-canton", "--log-level-root",
                        "--log-level-stdout", "--log-profile", "--log-truncate",
                        "--manual-start", "--no-tty", "--verbose", "--version", "-C", "-D",
                        "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-interactive",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.5.1"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-interactive",
                        "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_5_2_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-access", "--log-access-errors",
                        "--log-access-errors-filename", "--log-access-filename",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-level-canton", "--log-level-root",
                        "--log-level-stdout", "--log-profile", "--log-truncate",
                        "--manual-start", "--no-tty", "--verbose", "--version", "-C", "-D",
                        "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-interactive",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.5.2"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-interactive",
                        "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_5_3_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-access", "--log-access-errors",
                        "--log-access-errors-filename", "--log-access-filename",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-level-canton", "--log-level-root",
                        "--log-level-stdout", "--log-profile", "--log-truncate",
                        "--manual-start", "--no-tty", "--verbose", "--version", "-C", "-D",
                        "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-interactive",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.5.3"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-interactive",
                        "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_5_4_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-access", "--log-access-errors",
                        "--log-access-errors-filename", "--log-access-filename",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-level-canton", "--log-level-root",
                        "--log-level-stdout", "--log-profile", "--log-truncate",
                        "--manual-start", "--no-tty", "--verbose", "--version", "-C", "-D",
                        "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-interactive",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.5.4"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-interactive",
                        "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_5_5_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-access", "--log-access-errors",
                        "--log-access-errors-filename", "--log-access-filename",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-level-canton", "--log-level-root",
                        "--log-level-stdout", "--log-profile", "--log-truncate",
                        "--manual-start", "--no-tty", "--verbose", "--version", "-C", "-D",
                        "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-interactive",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.5.5"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-interactive",
                        "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_5_6_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-access", "--log-access-errors",
                        "--log-access-errors-filename", "--log-access-filename",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-level-canton", "--log-level-root",
                        "--log-level-stdout", "--log-profile", "--log-truncate",
                        "--manual-start", "--no-tty", "--verbose", "--version", "-C", "-D",
                        "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-interactive",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.5.6"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-interactive",
                        "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_5_10_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-access", "--log-access-errors",
                        "--log-access-errors-filename", "--log-access-filename",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-level-canton", "--log-level-root",
                        "--log-level-stdout", "--log-profile", "--log-truncate",
                        "--manual-start", "--no-tty", "--verbose", "--version", "-C", "-D",
                        "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-interactive",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.5.10"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-interactive",
                        "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_5_11_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-access", "--log-access-errors",
                        "--log-access-errors-filename", "--log-access-filename",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-level-canton", "--log-level-root",
                        "--log-level-stdout", "--log-profile", "--log-truncate",
                        "--manual-start", "--no-tty", "--verbose", "--version", "-C", "-D",
                        "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-interactive",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.5.11"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-interactive",
                        "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_5_12_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-access", "--log-access-errors",
                        "--log-access-errors-filename", "--log-access-filename",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-level-canton", "--log-level-root",
                        "--log-level-stdout", "--log-profile", "--log-truncate",
                        "--manual-start", "--no-tty", "--verbose", "--version", "-C", "-D",
                        "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-interactive",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.5.12"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-interactive",
                        "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_5_13_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-access", "--log-access-errors",
                        "--log-access-errors-filename", "--log-access-filename",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-level-canton", "--log-level-root",
                        "--log-level-stdout", "--log-profile", "--log-truncate",
                        "--manual-start", "--no-tty", "--verbose", "--version", "-C", "-D",
                        "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-interactive",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.5.13"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-interactive",
                        "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_5_14_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-access", "--log-access-errors",
                        "--log-access-errors-filename", "--log-access-filename",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-level-canton", "--log-level-root",
                        "--log-level-stdout", "--log-profile", "--log-truncate",
                        "--manual-start", "--no-tty", "--verbose", "--version", "-C", "-D",
                        "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-interactive",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.5.14"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-interactive",
                        "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_5_15_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-access", "--log-access-errors",
                        "--log-access-errors-filename", "--log-access-filename",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-level-canton", "--log-level-root",
                        "--log-level-stdout", "--log-profile", "--log-truncate",
                        "--manual-start", "--no-tty", "--verbose", "--version", "-C", "-D",
                        "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-interactive",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.5.15"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-interactive",
                        "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_5_16_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-access", "--log-access-errors",
                        "--log-access-errors-filename", "--log-access-filename",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-level-canton", "--log-level-root",
                        "--log-level-stdout", "--log-profile", "--log-truncate",
                        "--manual-start", "--no-tty", "--verbose", "--version", "-C", "-D",
                        "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-interactive",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.5.16"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-interactive",
                        "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_5_17_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-access", "--log-access-errors",
                        "--log-access-errors-filename", "--log-access-filename",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-level-canton", "--log-level-root",
                        "--log-level-stdout", "--log-profile", "--log-truncate",
                        "--manual-start", "--no-tty", "--verbose", "--version", "-C", "-D",
                        "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-interactive",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.5.17"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-interactive",
                        "sandbox-console"),
                mapFlag);
    }


    private static Entry entry3_5_18_OPEN_SOURCE() {
        Map<String, List<String>> mapFlag = new LinkedHashMap<>();
        mapFlag.put("(global)",
                List.of("--bootstrap", "--config", "--debug", "--help",
                        "--kms-log-file-name", "--kms-log-file-rolling-history",
                        "--kms-log-file-rolling-pattern", "--kms-log-immediate-flush",
                        "--log-access", "--log-access-errors",
                        "--log-access-errors-filename", "--log-access-filename",
                        "--log-encoder", "--log-file-appender", "--log-file-name",
                        "--log-file-rolling-history", "--log-file-rolling-pattern",
                        "--log-immediate-flush", "--log-level-canton", "--log-level-root",
                        "--log-level-stdout", "--log-profile", "--log-truncate",
                        "--manual-start", "--no-tty", "--verbose", "--version", "-C", "-D",
                        "-c", "-h", "-v"));
        mapFlag.put("sandbox",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-interactive",
                List.of("--admin-api-port", "--canton-port-file", "--dar", "--dev",
                        "--json-api-port", "--ledger-api-port", "--mediator-admin-port",
                        "--multi-sync", "--sequencer-admin-port", "--sequencer-public-port",
                        "--static-time"));
        mapFlag.put("sandbox-console",
                List.of("--admin-api-port", "--host", "--mediator-admin-port", "--port",
                        "--sequencer-admin-port", "--sequencer-public-port"));

        return new Entry(VersionId.parse("3.5.18"), Edition.OPEN_SOURCE,
                "com.digitalasset.canton.CantonCommunityApp",
                List.of("daemon", "run", "generate", "sandbox", "sandbox-interactive",
                        "sandbox-console"),
                mapFlag);
    }
}
