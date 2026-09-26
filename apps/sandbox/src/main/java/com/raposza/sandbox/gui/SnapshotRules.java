// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.Edition;
import com.raposza.canton.install.VersionId;
import com.raposza.sandbox.SandboxStack;

import java.util.List;
import java.util.Properties;

/**
 * What a snapshot may be called, and when one may be started on.
 *
 * These are the two questions the snapshot list exists to answer and they had
 * no answer reachable without a display: the name rule was inside the Save
 * button's handler and the compatibility rule inside a method that read a
 * `JList` selection. Both are decisions about DATA - a string, and two property
 * sets - and neither needs a window to be right or wrong.
 *
 * WHAT STAYED IN THE WINDOW is everything that is not a decision: the list, the
 * popup menu, the stop that a save has to happen after, and the milestones. A
 * pane that owned those as well would have needed the form, the service and
 * `stopThen`, which is the lifecycle cluster and a different piece of work.
 *
 * Author Claude/bentzn
 */
final class SnapshotRules {

    private SnapshotRules() {
    }


    /**
     * @param strName the name as the operator typed it, already trimmed
     * @param lstExisting the names already taken
     * @return why it is refused, or null when it is acceptable
     */
    static String strRefusalOfName(String strName, List<String> lstExisting) {
        try {
            Snapshots.requireName(strName);
        }
        catch (RuntimeException ex) {
            return ex.getMessage();
        }
        if (lstExisting != null && lstExisting.contains(strName))
            return "there is already a snapshot called " + strName;
        return null;
    }


    /**
     * What the form asks for, as a snapshot would record it.
     *
     * THE PORT BLOCK IS RECORDED ON THIS TOPOLOGY TOO - D-852. The mediator's
     * sequencer connection is persisted in the cluster, so a Sandbox snapshot
     * taken on 22010 and restored onto 30010 starts a mediator that dials 22013
     * for ever: measured at his console on 3.5.14, 2026-09-25.
     *
     * @param inst the selected Canton, or null when there is none
     * @param nPortFirst the first port of the block
     * @param nPortPostgres the embedded cluster's port
     * @return the properties
     */
    static Properties propsOfForm(CantonInstallation inst, int nPortFirst,
            int nPortPostgres) {
        Properties props = new Properties();
        props.setProperty(Snapshots.STR_KEY_PORTS, nPortFirst + "-" + nPortPostgres);
        props.setProperty(Snapshots.STR_KEY_CANTON,
                inst == null ? "" : String.valueOf(inst.version()));
        props.setProperty(Snapshots.STR_KEY_EDITION,
                inst == null ? "" : String.valueOf(inst.edition()));
        props.setProperty(Snapshots.STR_KEY_PREFIX, SandboxStack.STR_DEFAULT_PREFIX);
        return props;
    }


    /**
     * The same, for the LocalNetND topology.
     *
     * THE FORM HOLDS NO CantonInstallation THERE - the version box lists Splice
     * bundles as Strings, so `selected()` is always null and the Canton the
     * three participants run inside has to be passed in. That is A-35 (b): the
     * founding snapshot recorded an empty Canton version because nobody did.
     *
     * @param strBundle the Splice bundle version; never null
     * @param version the Canton the participants run inside; never null
     * @param edition its edition, or null for unknown
     * @param nPortFirst the lowest port of the block
     * @param nPortPostgres the embedded cluster's port
     * @return the properties
     */
    static Properties propsOfLocalNetForm(String strBundle, VersionId version,
            Edition edition, int nPortFirst, int nPortPostgres) {
        Properties props = new Properties();
        props.setProperty(Snapshots.STR_KEY_CANTON, String.valueOf(version));
        Edition editionHere = edition == null ? Edition.UNKNOWN : edition;
        props.setProperty(Snapshots.STR_KEY_EDITION, editionHere.name());
        props.setProperty(Snapshots.STR_KEY_PREFIX, SandboxStack.STR_DEFAULT_PREFIX);
        props.setProperty(Snapshots.STR_KEY_SPLICE, strBundle == null ? "" : strBundle);
        props.setProperty(Snapshots.STR_KEY_PORTS, nPortFirst + "-" + nPortPostgres);
        return props;
    }


    /**
     * The same, as a RUN records it. PQS is empty on this topology - no scribe
     * runs against a LocalNetND participant - and the row is written anyway so
     * the two topologies' files read alike.
     *
     * @param strBundle the Splice bundle version; never null
     * @param version the Canton the participants run inside; never null
     * @param edition its edition, or null for unknown
     * @param nPortFirst the lowest port of the block
     * @param nPortPostgres the embedded cluster's port
     * @return the properties
     */
    static Properties propsOfLocalNetRun(String strBundle, VersionId version,
            Edition edition, int nPortFirst, int nPortPostgres) {
        Properties props = propsOfLocalNetForm(strBundle, version, edition, nPortFirst,
                nPortPostgres);
        props.setProperty(Snapshots.STR_KEY_PQS, "");
        return props;
    }


    /**
     * The same, plus what the RUNNING stack adds to it.
     *
     * @param inst the selected Canton, or null when there is none
     * @param strPqs what the running options say about PQS, or "" when nothing
     *        is running
     * @param nPortFirst the first port of the block the stack runs on
     * @param nPortPostgres the embedded cluster's port
     * @return the properties
     */
    static Properties propsOfRun(CantonInstallation inst, String strPqs, int nPortFirst,
            int nPortPostgres) {
        Properties props = propsOfForm(inst, nPortFirst, nPortPostgres);
        props.setProperty(Snapshots.STR_KEY_PQS, strPqs == null ? "" : strPqs);
        return props;
    }


    /**
     * Whether a snapshot can be started on as things stand.
     *
     * THE CANTON KEY IS CHECKED THOUGH THE ROOTS ARE PER VERSION. The directory
     * name says which version a snapshot belongs to and this says the same
     * thing from inside the file; they can only disagree when a directory has
     * been moved between roots by hand, which is exactly the migration this
     * application does not do.
     *
     * THE PQS VERSION IS NOT CHECKED. Its database carries one name on every
     * version, so a restored snapshot hands whatever scribe now runs the schema
     * the snapshot's scribe wrote, and scribe migrates it or refuses it. Canton
     * reading a store another version wrote is the case that has to be refused
     * here.
     *
     * @param propsWas what the snapshot recorded
     * @param propsNow what the form asks for
     * @return what disagrees, one per line, or null when nothing does
     */
    static String strDisagreement(Properties propsWas, Properties propsNow) {
        StringBuilder sb = new StringBuilder();
        differ(sb, propsWas, propsNow, Snapshots.STR_KEY_CANTON, "Canton version");
        differ(sb, propsWas, propsNow, Snapshots.STR_KEY_EDITION, "edition");
        differ(sb, propsWas, propsNow, Snapshots.STR_KEY_PREFIX, "database prefix");
        // LOCALNETND'S OWN. Absent on both sides of a Sandbox pair, where it
        // reads "" and disagrees with nothing.
        differ(sb, propsWas, propsNow, Snapshots.STR_KEY_SPLICE, "Splice bundle");
        // BOTH TOPOLOGIES SINCE D-852. A Sandbox snapshot taken before it
        // carries no block and is REFUSED - operator decision, 2026-09-25 -
        // because nothing on disk says which block wrote it, and the one this
        // was measured on was the pre-D-817 22010.
        differ(sb, propsWas, propsNow, Snapshots.STR_KEY_PORTS, "port block");
        return sb.length() == 0 ? null : sb.toString();
    }


    private static void differ(StringBuilder sb, Properties propsWas,
            Properties propsNow, String strKey, String strWhat) {
        String strWas = propsWas.getProperty(strKey, "");
        String strNow = propsNow.getProperty(strKey, "");
        if (!strWas.equals(strNow))
            sb.append(strWhat).append(": ").append(strWas).append(" -> ").append(strNow)
                    .append("\n");
    }

}
