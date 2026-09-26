// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.Edition;
import com.raposza.canton.install.VersionId;
import com.raposza.runtime.settings.RaposzaSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The founding snapshot: the one a start RESTORES instead of founding again.
 *
 * <h2>What it is for</h2>
 *
 * Operator instruction, 2026-09-22. A stack starts from scratch every time -
 * nothing a run accumulated survives it. The founding is the expensive part of
 * that and it produces the same bytes every time, so it is taken ONCE per
 * {@link #strKeyOfSandbox} or {@link #strKeyOfLocalNet} key and laid down on
 * every start after the first. The user-visible contract is unchanged: still
 * from scratch, reconstituted rather than rebuilt.
 *
 * <h2>Why it is a separate store and not a reserved name</h2>
 *
 * {@link Snapshots#lstNames()} offers every directory under its root that holds
 * a `snapshot.properties`, so a founding snapshot sitting in a per-version root
 * would be a row in the list - and the operator asked for one that is displayed
 * nowhere. Filtering it out by NAME would leave the name available to type into
 * the Save dialog, where {@link Snapshots#requireName} accepts it, and the two
 * would then be one directory.
 *
 * So it lives in a root of its own, `founding/&lt;key&gt;/`, a SIBLING of the
 * per-version roots. Nothing can collide: {@link VersionKey#strOf} always
 * carries a `-` and begins with a version, so no per-version root is ever named
 * `founding`, and no per-version store ever lists a sibling.
 *
 * <h2>What the key has to carry</h2>
 *
 * A raw cluster is only meaningful to the software that wrote it, which is why
 * {@link Snapshots} refuses a cross-version restore outright. On the Sandbox
 * that software is one Canton, so version and edition key it - and the port
 * block, because the cluster persists the mediator's sequencer connection
 * (D-852).
 *
 * ON LOCALNETND IT IS THREE THINGS. The cluster was written by a Splice BUNDLE
 * and a Canton JAR, both of which move independently, and Canton's topology
 * state names the ports it was founded on. A key missing any of the three
 * restores a cluster another generation wrote, and Canton discovers that some
 * way into a start as a failure about topology rather than about a snapshot.
 *
 * Author Claude/bentzn
 */
public final class Founding {

    /**
     * The reserved root, a SIBLING of the per-version snapshot roots and never
     * a per-version root itself - see the type comment.
     */
    public static final String STR_DIR_ROOT = "founding";

    /** The one entry a founding store ever holds. */
    public static final String STR_NAME = "founding";

    /** What a founding snapshot records about itself, beside the Canton keys. */
    public static final String STR_KEY_TOPOLOGY = "topology";

    private final Snapshots store;

    private final String strKey;


    /**
     * @param storeNew where the one entry lives; never null
     * @param strKeyNew the key it belongs to; never null
     */
    private Founding(Snapshots storeNew, String strKeyNew) {
        this.store = storeNew;
        this.strKey = strKeyNew;
    }


    /**
     * @param strKeyNew what the cluster belongs to, from one of the two key
     *        methods; never null or empty
     * @return the founding store for it
     */
    public static Founding of(String strKeyNew) {
        if (strKeyNew == null || strKeyNew.isEmpty())
            throw new IllegalArgumentException("a founding key is required");
        Path dirRoot = RaposzaSettings.current().dirSnapshots()
                .resolve(STR_DIR_ROOT)
                .resolve(Snapshots.strEncode(strKeyNew));
        return new Founding(new Snapshots(dirRoot), strKeyNew);
    }


    /**
     * THE SANDBOX KEY IS THE VERSION KEY AND THE PORT BLOCK - D-852. One
     * Canton wrote the cluster, but the mediator's sequencer connection is
     * persisted in it, so a founding taken on another block starts a mediator
     * that never reaches its sequencer. Measured on 3.5.14, 2026-09-25: a
     * cluster founded on 22010 restored onto 30010 dialled 22013 until the
     * start timed out. A founding keyed without the block is never found
     * again, and the first start founds anew.
     *
     * @param version the Canton version; never null
     * @param edition its edition, or null for unknown
     * @param nPortFirst the first port of the block
     * @param nPortPostgres the embedded cluster's port
     * @return the key
     */
    public static String strKeyOfSandbox(VersionId version, Edition edition, int nPortFirst,
            int nPortPostgres) {
        return VersionKey.strOf(version, edition) + "_p" + nPortFirst + "-" + nPortPostgres;
    }


    /**
     * THE LOCALNETND KEY IS THREE THINGS - see the type comment for why each
     * one is load-bearing.
     *
     * @param strBundle the Splice bundle version; never null or empty
     * @param version the Canton version the participants run inside; never null
     * @param edition its edition, or null for unknown
     * @param nPortFirst the lowest port of the block
     * @param nPortPostgres the embedded cluster's port
     * @return the key
     */
    public static String strKeyOfLocalNet(String strBundle, VersionId version, Edition edition,
            int nPortFirst, int nPortPostgres) {
        if (strBundle == null || strBundle.isEmpty())
            throw new IllegalArgumentException("a Splice bundle version is required");
        return "splice" + strBundle + "_" + VersionKey.strOf(version, edition)
                + "_p" + nPortFirst + "-" + nPortPostgres;
    }


    /**
     * What a start does about the cluster, decided without a window.
     *
     * THE ORDER IS THE OPERATOR'S, 2026-09-22: a user snapshot wins, then the
     * founding snapshot, and founding is what is left when there is neither.
     *
     * @param strNameUser the user snapshot the list has selected, or null for
     *        none
     * @param flagFoundingHere whether a founding snapshot exists for this key
     * @return what the start does
     */
    public static Action actionOf(String strNameUser, boolean flagFoundingHere) {
        if (strNameUser != null)
            return Action.RESTORE_USER;
        return flagFoundingHere ? Action.RESTORE_FOUNDING : Action.FOUND;
    }


    /**
     * @return the key this store belongs to
     */
    public String strKey() {
        return strKey;
    }


    /**
     * @return where the cluster is, whether or not it is there
     */
    public Path dir() {
        return store.dirOf(STR_NAME);
    }


    /**
     * A PRESENT DIRECTORY IS NOT A SNAPSHOT. `snapshot.properties` is written
     * LAST, so a copy that failed part way leaves a directory this must not
     * offer - which is the same test {@link Snapshots#lstNames()} applies.
     *
     * @return whether a usable founding snapshot is on disk
     */
    public boolean exists() {
        return Files.isRegularFile(dir().resolve(Snapshots.STR_FILE_META))
                && Files.isDirectory(dir().resolve(Snapshots.STR_DIR_DATA));
    }


    /**
     * Copies a STOPPED cluster in. Replaces whatever was there, because a
     * founding snapshot is one per key by definition and a refusal would leave
     * the caller with nothing to do about it.
     *
     * @param dirData the cluster to copy, which nothing may be writing to
     * @param props what was running when it was taken
     */
    public void save(Path dirData, Properties props) {
        if (exists() || Files.exists(dir()))
            store.delete(STR_NAME);
        Properties propsAll = new Properties();
        propsAll.putAll(props);
        store.save(STR_NAME, dirData, propsAll);
    }


    /**
     * @param dirData where the cluster goes; it is REPLACED
     */
    public void restore(Path dirData) {
        store.restore(STR_NAME, dirData);
    }


    /**
     * @return what was recorded with it
     */
    public Properties read() {
        return store.read(STR_NAME);
    }


    /**
     * Removes it, so the next start founds again and takes a new one. Absent is
     * not an error: the caller wants it gone and it is.
     */
    public void delete() {
        store.delete(STR_NAME);
    }


    /** What a start does about the cluster. */
    public enum Action {

        /** Found the network, then take the founding snapshot. */
        FOUND,

        /** Lay down the user snapshot the list has selected. */
        RESTORE_USER,

        /** Lay down the founding snapshot instead of founding. */
        RESTORE_FOUNDING
    }
}
