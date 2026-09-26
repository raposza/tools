// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import com.raposza.runtime.db.PostgresCoordinates;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The whole 2.x topology - the configuration AND the console script - because
 * on 2.x there is nothing to overlay.
 *
 * 3.x ships `sandbox/sandbox.conf` inside the jar and the `sandbox` subcommand
 * bootstraps a synchronizer from it, so that line only needs overlays. The 2.x
 * usage line offers daemon, run and generate; the participant, the domain and
 * the script that connects them are ours to write.
 *
 * Ported from the CantonSandboxPqs prototype, which runs this shape in
 * production against 2.9 and 2.10. Auto-init only: Canton generates its own
 * namespace key and identity on start. The prototype's manual-init path, which
 * uploads a namespace key to hold the participant UID stable across runs, is a
 * later increment - its console calls are annotated as verified on one 2.x
 * minor line and this project targets three.
 *
 * The domain is on MEMORY storage and the participant on PostgreSQL. That is
 * not an oversight: nothing here survives a restart, because the embedded
 * server writes to a temporary directory that goes with it. A participant kept
 * across runs beside a domain that forgot its identity would be a stack that
 * cannot reconnect, which is worse than one that starts clean.
 *
 * Author Claude/bentzn
 */
public final class Canton2xConfig {

    /** Matches the 3.x bundled node name, so one auth overlay serves both. */
    public static final String STR_NODE_PARTICIPANT = StorageOverlay.STR_NODE_PARTICIPANT;

    public static final String STR_NODE_DOMAIN = "mydomain";

    /**
     * The DATABASE name, which is not the node name.
     *
     * The 2.x node is called `sandbox` - a name shared with the 3.x sibling so
     * that its overlay matches the vendor's own built-in topology - and naming
     * the database after it produced a database called `sandbox` the moment
     * the prefix went away. What is in there is the participant's ledger.
     */
    public static final String STR_DATABASE_PARTICIPANT = "participant";

    /**
     * Measured on 2.9 and 2.10 in the prototype. NOT measured on 2.8, which
     * sits on the far side of the PV 3/4 removal at 2.9.1 - if a 2.8 stack
     * refuses to start, this is the first field to change.
     */
    public static final int N_PROTOCOL_VERSION_DEFAULT = 5;

    /**
     * Printed by the bootstrap script AFTER the domain is connected and every
     * DAR is uploaded, which makes it a stronger readiness signal than 3.x's
     * port file: that one means listening, this one means onboarded.
     */
    public static final String STR_READY = "raposza sandbox is ready";

    public static final String STR_PARTICIPANT_ID_FILE = "participant-id.txt";

    /**
     * Written by the bootstrap only when a ping was asked for, and only after
     * it returned. Its presence is the evidence that ledger data exists; its
     * absence beside a green start would mean the script took a path nobody
     * asked for.
     */
    public static final String STR_PING_FILE = "ping.txt";

    private static final String STR_SUFFIX_DATABASE = "_participant";

    /**
     * What the domain's database is called, under the same prefix rule the
     * participant's follows.
     */
    private static final String STR_DATABASE_DOMAIN = "domain";

    private final String strParticipant;
    private final String strDomain;
    private final PostgresCoordinates pgParticipant;
    private final PostgresCoordinates pgDomain;
    private final Sandbox2xPorts ports;
    private final int nProtocolVersion;
    private final boolean flagDev;


    /**
     * @param strParticipant the participant node name; also the console val the
     *        bootstrap script uses, so it must be a plain identifier
     * @param strDomain the domain node name, same constraint
     * @param pgParticipant where the participant stores its state
     * @param pgDomain where the DOMAIN stores its state, or null for memory -
     *        see {@link #databaseNameDomain} for why null is not the default
     * @param ports what to listen on
     * @param nProtocolVersion the domain's protocol version
     * @param flagDev whether to enable dev version support, the preview and
     *        testing commands, and the non-standard-config they require
     */
    public Canton2xConfig(String strParticipant, String strDomain,
            PostgresCoordinates pgParticipant, PostgresCoordinates pgDomain,
            Sandbox2xPorts ports, int nProtocolVersion, boolean flagDev) {
        require(strParticipant, "a participant name");
        require(strDomain, "a domain name");
        if (pgParticipant == null || ports == null)
            throw new IllegalArgumentException("pgParticipant and ports are required");
        if (nProtocolVersion < 1)
            throw new IllegalArgumentException("protocol version must be positive: "
                    + nProtocolVersion);

        this.strParticipant = strParticipant;
        this.strDomain = strDomain;
        this.pgParticipant = pgParticipant;
        this.pgDomain = pgDomain;
        this.ports = ports;
        this.nProtocolVersion = nProtocolVersion;
        this.flagDev = flagDev;
    }


    /**
     * The MEMORY-domain shape, kept for callers that do not snapshot.
     *
     * @param strParticipant the participant node name
     * @param strDomain the domain node name
     * @param pgParticipant where the participant stores its state
     * @param ports what to listen on
     * @param nProtocolVersion the domain's protocol version
     * @param flagDev whether to enable the dev features
     */
    public Canton2xConfig(String strParticipant, String strDomain,
            PostgresCoordinates pgParticipant, Sandbox2xPorts ports, int nProtocolVersion,
            boolean flagDev) {
        this(strParticipant, strDomain, pgParticipant, null, ports, nProtocolVersion, flagDev);
    }


    public static Canton2xConfig of(PostgresCoordinates pgParticipant, Sandbox2xPorts ports) {
        return new Canton2xConfig(STR_NODE_PARTICIPANT, STR_NODE_DOMAIN, pgParticipant, ports,
                N_PROTOCOL_VERSION_DEFAULT, false);
    }


    /**
     * @param strPrefix a lower-case prefix, so a second stack on one server
     *        does not collide with the first
     * @return the participant's database
     */
    public static String databaseName(String strPrefix) {
        // An EMPTY prefix means no prefix. It is no longer `require`d: the
        // stacks default to none, and a blank one has to mean the plain node
        // name rather than a refusal.
        if (strPrefix == null || strPrefix.isBlank())
            return STR_DATABASE_PARTICIPANT;
        return strPrefix + "_" + STR_DATABASE_PARTICIPANT;
    }


    /**
     * The DOMAIN's database, and why a 2.x stack now needs two.
     *
     * The domain ran on `storage.type = memory`, which is what the vendor's own
     * single-node examples do and which is correct for a stack that is started,
     * used and thrown away. IT MAKES A SNAPSHOT UNRESTORABLE. A snapshot is a
     * copy of the PostgreSQL cluster, so on 2.x it captured the
     * participant and nothing else. Restoring it gives a participant that
     * remembers being onboarded to a domain, beside a domain that was created
     * empty two seconds ago with a different identity, and the bootstrap's
     * `connect_local` fails: `Bootstrap script terminated with an error`, exit
     * code 3, measured on 2.8.12, 2.9.1 and 2.9.6.
     *
     * The 3.x column never had this: its sequencer, its sequencer driver and
     * its mediator are all on PostgreSQL, so its snapshot is the whole stack.
     *
     * @param strPrefix the same prefix the participant's database takes
     * @return the domain's database name
     */
    public static String databaseNameDomain(String strPrefix) {
        if (strPrefix == null || strPrefix.isBlank())
            return STR_DATABASE_DOMAIN;
        return strPrefix + "_" + STR_DATABASE_DOMAIN;
    }


    public String strParticipant() {
        return strParticipant;
    }


    public String strDomain() {
        return strDomain;
    }


    public int nProtocolVersion() {
        return nProtocolVersion;
    }


    public Sandbox2xPorts ports() {
        return ports;
    }


    /**
     * @return the HOCON to hand to Canton with -c; the whole configuration,
     *         except auth-services, which stays a separate file so a stack that
     *         will not start can be diagnosed by removing one overlay at a time
     */
    public String renderConf() {
        StringBuilder sb = new StringBuilder();
        sb.append("// SPDX-License-Identifier: Apache-2.0\n");
        sb.append("// Generated by raposza-canton for the Canton 2.x daemon.\n");
        sb.append("canton {\n");

        if (flagDev) {
            sb.append("  parameters {\n");
            sb.append("    non-standard-config = yes\n");
            sb.append("  }\n");
        }

        sb.append("  participants {\n");
        sb.append("    ").append(strParticipant).append(" {\n");
        sb.append("      init.auto-init = true\n");
        StorageOverlay.appendStorage(sb, pgParticipant, 6, true);
        sb.append("      admin-api {\n");
        sb.append("        address = \"0.0.0.0\"\n");
        sb.append("        port = ").append(ports.nPortAdminApi()).append("\n");
        sb.append("      }\n");
        sb.append("      ledger-api {\n");
        sb.append("        address = \"0.0.0.0\"\n");
        sb.append("        port = ").append(ports.nPortLedgerApi()).append("\n");
        sb.append("      }\n");
        if (flagDev) {
            sb.append("      parameters {\n");
            sb.append("        dev-version-support = yes\n");
            sb.append("      }\n");
        }
        sb.append("    }\n");
        sb.append("  }\n");

        sb.append("  domains {\n");
        sb.append("    ").append(strDomain).append(" {\n");
        if (pgDomain == null) {
            sb.append("      storage {\n");
            sb.append("        type = memory\n");
            sb.append("      }\n");
        }
        else {
            StorageOverlay.appendStorage(sb, pgDomain, 6, true);
        }
        sb.append("      public-api { port = ").append(ports.nPortDomainPublic()).append(" }\n");
        sb.append("      admin-api { port = ").append(ports.nPortDomainAdmin()).append(" }\n");
        sb.append("      init.domain-parameters.protocol-version = ").append(nProtocolVersion)
                .append("\n");
        sb.append("    }\n");
        sb.append("  }\n");

        if (flagDev) {
            sb.append("  features {\n");
            sb.append("    enable-testing-commands = yes\n");
            sb.append("    enable-preview-commands = yes\n");
            sb.append("  }\n");
        }

        sb.append("}\n");
        return sb.toString();
    }


    /**
     * The console script Canton runs with --bootstrap.
     *
     * The order is the topology: the domain has to be up before a participant
     * can connect to it, and a DAR cannot be uploaded to a participant that has
     * not started. The sentinel goes last for the same reason - printed before
     * the uploads, it would report a stack ready that has no packages on it.
     *
     * @param lstFileDar DARs to upload, in order; may be empty
     * @param fileParticipantId where to write the participant id, which doubles
     *        as an out-of-band readiness signal
     * @return the script
     */
    public String renderBootstrap(List<Path> lstFileDar, Path fileParticipantId) {
        return renderBootstrap(lstFileDar, fileParticipantId, null);
    }


    /**
     * The same script, optionally pinging the participant from itself before it
     * declares readiness.
     *
     * A PING IS THE CHEAPEST THING THAT PUTS A TRANSACTION ON THE LEDGER. It is
     * a create and a choice on Canton's own admin workflow, so it needs no DAR
     * built, staged or committed and no template name known to this project.
     * That is what makes it the instrument for the question the 2.x column has
     * never been able to answer: the 2.10.4 gate proved the pipeline migrates
     * its schema and streams from an EMPTY ledger, and an empty ledger
     * cannot distinguish a working pipeline from one that would drop every
     * event it saw.
     *
     * THE SIGNATURE IS NOT MEASURED ON THIS GENERATION. On 3.5.11 it is
     * `ping(ParticipantId, ...)` with defaults on everything after the first,
     * which is why `<node>.id` is passed rather than the node itself or nothing
     * at all. The 2.x console has not been asked. If the shape differs
     * the script fails at that line and the stack reports it at start-up, which
     * is the intended way to find out and is cheaper than a probe.
     *
     * The ping goes AFTER the uploads and BEFORE the sentinel, so a failed ping
     * leaves no participant-id file and the stack reports a bootstrap that did
     * not finish rather than a stack that came up without the data it was
     * started for.
     *
     * @param lstFileDar DARs to upload, in order; may be empty
     * @param fileParticipantId where to write the participant id, which doubles
     *        as an out-of-band readiness signal
     * @param filePing where to write the ping round-trip duration, or null to
     *        run no ping
     * @return the script
     */
    public String renderBootstrap(List<Path> lstFileDar, Path fileParticipantId, Path filePing) {
        if (fileParticipantId == null)
            throw new IllegalArgumentException("fileParticipantId is required");

        List<Path> lstDar = lstFileDar == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(lstFileDar));

        StringBuilder sb = new StringBuilder();
        sb.append("// SPDX-License-Identifier: Apache-2.0\n");
        sb.append("// Generated by raposza-canton. Canton console, Scala.\n");
        sb.append(strDomain).append(".start()\n");
        sb.append(strParticipant).append(".start()\n");
        sb.append(strParticipant).append(".domains.connect_local(").append(strDomain)
                .append(")\n");

        for (Path fileDar : lstDar) {
            sb.append(strParticipant).append(".dars.upload(\"").append(forScala(fileDar))
                    .append("\")\n");
        }

        if (filePing != null) {
            sb.append("val _dur = ").append(strParticipant).append(".health.ping(")
                    .append(strParticipant).append(".id)\n");
            sb.append("java.nio.file.Files.writeString(java.nio.file.Paths.get(\"")
                    .append(forScala(filePing)).append("\"), _dur.toString)\n");
        }

        sb.append("val _strPid = ").append(strParticipant).append(".id.toProtoPrimitive\n");
        sb.append("java.nio.file.Files.writeString(java.nio.file.Paths.get(\"")
                .append(forScala(fileParticipantId)).append("\"), _strPid)\n");
        sb.append("println(\"").append(STR_READY).append("\")\n");
        return sb.toString();
    }


    @Override
    public String toString() {
        return "canton 2.x topology: participant " + strParticipant + ", domain " + strDomain
                + " (protocol version " + nProtocolVersion + "), " + pgParticipant;
    }


    /**
     * Backslashes are an escape inside a Scala string literal, so a Windows
     * path written verbatim into the script produces a parse error a long way
     * from its cause.
     */
    private static String forScala(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "/");
    }


    private static void require(String strValue, String strWhat) {
        if (strValue == null || strValue.isBlank())
            throw new IllegalArgumentException(strWhat + " is required");
    }
}
