// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.CantonInstallations;
import com.raposza.canton.install.DpmBundles;
import com.raposza.canton.install.ToolchainRoots;
import com.raposza.runtime.localnet.SpliceInstallations;
import com.raposza.canton.topology.SandboxPorts;
import com.raposza.sandbox.SandboxStack;
import com.raposza.sandbox.caps.FeatureSupport;
import com.raposza.sandbox.caps.SandboxCapabilities;
import com.raposza.canton.topology.StorageOverlay;
import com.raposza.jwt.AuthPlan;
import com.raposza.jwt.TokenShape;
import com.raposza.sandbox.caps.SandboxFeature;
import com.raposza.sandbox.process.SandboxLauncher;
import com.raposza.sandbox.app.JwtMintProcess;
import com.raposza.sandbox.app.AuthSettings;
import com.raposza.sandbox.app.SandboxOptions;
import com.raposza.sandbox.app.SandboxService;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * The arguments, as fields.
 *
 * One rule shapes this class: **the form produces a
 * {@link SandboxOptions} and nothing else.** It does not resolve an
 * installation, does not check a port and does not decide what a blank field
 * means to the stack - {@link SandboxService} answers all three, identically
 * for the window and for the terminal.
 *
 * <h2>Which Canton, and why the box holds installations rather than a line</h2>
 *
 * The terminal defaults to the newest of line 3.5 because a default that moved
 * with what a machine happens to have would make `sandbox` mean different
 * things on two machines. A window has no such problem: it can SHOW what is
 * installed, so the box lists exactly what could be started and the selection
 * becomes an exact `--canton`. Anything without a runtime jar is listed and
 * DISABLED rather than hidden, because "my 2.10 is not in the list" is a worse
 * question than seeing it there greyed out.
 *
 * <h2>What the window no longer asks, and what it decides instead</h2>
 *
 * The terminal keeps every argument. The window starts a stack and stops it,
 * so it carries the ones a developer changes between runs and settles the rest:
 *
 * <pre>
 * launcher        DAEMON, always. MEASURED: a snapshot restored
 *                 under SUBCOMMAND dies with exit 3 before the process
 *                 reports ready. Canton's generated bootstrap re-proposes a
 *                 SynchronizerTrustCertificate and raises
 *                 TOPOLOGY_MAPPING_ALREADY_EXISTS against storage that
 *                 already carries the topology, which is the reason the
 *                 daemon and its shorter bootstrap exist at all.
 *                 Loading a snapshot IS a second start. The two launchers
 *                 also name their nodes differently - `sandbox` against
 *                 `raposza` - so this cannot be conditional on whether a
 *                 snapshot was picked: a cluster written by one and opened
 *                 by the other is a node meeting another node's identity.
 *                 The TERMINAL default is untouched: `--launcher daemon`
 *                 stays opt-in until the headless entry point has been run
 *                 on it as widely, and the terminal has no snapshots to make
 *                 it necessary.
 *                 not be a checkbox when it arrives.
 * heap            {@value #N_HEAP_CANTON_MB} MB, the prototype's figure.
 * db prefix       none, so the databases are named after their nodes.
 * party, user     neither. Canton already has `participant_admin` on a bare
 *                 start, measured on 3.5.11, so a form that asked for a
 *                 party and a user was asking a
 *                 developer to create the thing they were about to be given.
 * work/data/dars  three names under one run directory. {@link RunDirectory}.
 * dev protocol    off. The unstable protocol version, for reaching a feature
 *                 no released one carries. Nothing here needs it.
 * static time     off. Canton's clock advances on request through the time
 *                 service rather than with the wall clock - a property of a
 *                 TEST, decided by the test, not by whoever opened a window
 *                 an hour earlier.
 * ping            off. The participant pinging itself proves a stack serves
 *                 end to end, which is a thing to RUN against a stack rather
 *                 than a thing to arm before starting one.
 * </pre>
 *
 * <h2>The fields belong to the SELECTED VERSION, not to the window</h2>
 *
 * Changing the Canton box is not a small edit to one row: it changes which
 * settings apply and what their values are. The form reports the change through
 * {@link #useInstallSink}, and the window answers by writing the outgoing
 * version's {@link SandboxProfile}, reading the incoming one into these fields,
 * and calling {@link #applyCaps} to take away any row the new version has no
 * use for.
 *
 * A row is hidden ONLY when {@link SandboxCapabilities} says the feature behind
 * it is absent. An unmeasured version keeps every row, because taking a control
 * away from the one person who could find out whether it works is worse than
 * showing one that turns out to do nothing.
 *
 * <b>PQS is not part of the profile.</b> It is a decision about this run, made
 * every time, and it starts OFF - see {@link SandboxProfile}.
 *
 * Author Claude/bentzn
 */
public final class SandboxForm extends JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * The lowest port the stack takes, and the one the field shows. Canton's
     * own block starts at {@link SandboxPorts#N_DEFAULT_JSON_API} and the
     * offset is the difference, so the number on screen is a port rather than a
     * distance from one.
     *
     * THE NUMBER ITSELF LIVES ON {@link SandboxProfile}. It is what a version
     * with no profile starts from, which makes it a property of the settings
     * rather than of the panel that happens to show them - and a record that
     * had to name a Swing class to know its own defaults would be the
     * dependency the wrong way round.
     */
    /** How narrow a field may be dragged before it stops giving ground. */
    private static final int N_WIDTH_FIELD_MIN = 60;


    /**
     * `-Xmx` for Canton, from the prototype's `SettingsStore`. Measured there
     * on a 2.10 participant; a 3.x subcommand runs a participant, a sequencer
     * and a mediator in the one JVM, so if a 3.x start dies on memory THIS is
     * the number to raise and the symptom will say so plainly.
     *
     * Not in the profile: it is not a question the form asks.
     */
    public static final int N_HEAP_CANTON_MB = 1024;

    /** The row captions, used again to show and hide them. */
    private static final String STR_ROW_AUTH = "Auth";

    private static final String STR_ROW_SHAPE = "Token shape";

    private static final String STR_ROW_AUDIENCE = "Audience";

    private static final String STR_ROW_SCOPE = "Scope";

    /** The conventional scope, which is what a blank field used to mean. */
    private static final String STR_TARGET_SCOPE = AuthPlan.STR_SCOPE_DEFAULT;

    private static final String STR_ROW_SECRET = "Secret";

    private static final String STR_ROW_CERT = "Certificate";

    private static final String STR_ROW_JWKS = "JWKS URI";

    /** What the button beside the Canton box says. */
    public static final String STR_BUTTON_INSTALL = "Install";

    /**
     * The version row's label on the Sandbox topology. `SDK` since 2026-09-25,
     * when the rows became keyed on the SDK - {@link InstallLabel}.
     */
    public static final String STR_LABEL_SDK = "SDK";

    /** And on the LocalNet one. */
    public static final String STR_LABEL_SPLICE = "Splice";

    /**
     * OBJECT, because the box holds Splice VERSION STRINGS on the LocalNet
     * topology - his instruction of 2026-09-21, "the Canton dropdown will
     * become Splice". Every reader below asks what it got before casting.
     */
    private final JComboBox<Object> cmbCanton = new JComboBox<>();

    /**
     * BESIDE THE BOX, because what it acquires is what the box lists. A
     * developer looking for a version that is not in the box is looking at the
     * one control that can put it there.
     */
    private final JButton btnInstallSdk = new JButton(STR_BUTTON_INSTALL);

    /**
     * Canton version to dpm bundle, read off the manifests at each rescan and
     * not per paint - {@link InstallLabel}.
     */
    private transient Map<String, String> mapBundle = Collections.emptyMap();

    private final JComboBox<SandboxOptions.PqsMode> cmbPqs =
            new JComboBox<>(SandboxOptions.PqsMode.values());

    /**
     * A PLAIN FIELD, not a spinner. A spinner renders 22211 as `22,211` -
     * a port with a thousands separator in it - and offers to walk a port
     * one step at a time, which is not how anyone picks one.
     */
    private final JTextField fldPortFirst = new JTextField(8);

    private final JTextField fldPortPostgres = new JTextField(8);

    /**
     * A PLAIN FIELD, for the same reason the two ports above are: a spinner
     * right-aligns its value, renders 3600 with a thousands separator in it and
     * offers to walk a timeout ten seconds at a time. The document filter keeps
     * it to digits and the range is checked where the value is read.
     */
    private final JTextField fldTimeout = new JTextField(8);

    /**
     * NOT ON THE FORM ANY MORE, and still the field the run directory is read
     * from.
     *
     * The directory is per version and per edition and the profile already
     * holds it, so the row offered a developer the chance to point one
     * version's stack at another's cluster and be told about it some way into
     * a start. Where the roots live becomes a general setting; this stays as
     * the holder so that {@link #run()}, {@link #profile()} and
     * {@link #applyProfile} keep reading one value from one place.
     */
    private final JTextField fldRunDir = new JTextField(28);

    /**
     * EVERYTHING VARIABLE ABOUT AUTHENTICATION IS ON THIS FORM.
     *
     * The shape a participant verifies, the value it compares against, the
     * user it creates and the ceiling it puts on a token are all properties
     * of the stack that is about to start. The JWT tab reads them; it does
     * not have a copy. Two places to set one thing is how the participant
     * and its tokens came to disagree once already.
     */
    private final JComboBox<AuthSettings.Mode> cmbAuth =
            new JComboBox<>(AuthSettings.Mode.values());

    private final JComboBox<TokenShape> cmbShape = new JComboBox<>(
            new TokenShape[] {TokenShape.AUDIENCE, TokenShape.SCOPE});

    private final JTextField fldAuthValue = new JTextField(28);

    private final JTextField fldSecret = new JTextField(28);

    private final JTextField fldCert = new JTextField(24);

    private final JTextField fldJwks = new JTextField(28);

    /**
     * The last value this form PUT in the JWKS row, so a later change of
     * provider can tell its own text from the operator's.
     *
     * WHY THIS IS NEEDED AT ALL. The row is filled rather than left blank -
     * see the comment at the fill - so by the time anything else happens the
     * field holds a concrete url whether or not anybody typed it. Repointing
     * it unconditionally would throw away a deliberately typed key set; never
     * repointing it leaves the participant configured against the provider
     * that was in force when the window opened, which is what happened on
     * 2026-09-21: the OIDC tab was pointed at an external provider, this row
     * still read the embedded one, and the start polled an address nothing
     * was serving until it timed out.
     */
    private transient String strUrlJwksShown = "";

    /**
     * The conditional rows, HELD rather than found again by their caption.
     * The target row's caption CHANGES with the token shape - `Audience` or
     * `Scope`, because `Audience / scope` makes a reader work out which half
     * applies - and a lookup by caption would stop finding it the moment it
     * did.
     */
    private transient Row rowShape;

    private transient Row rowTarget;

    private transient Row rowSecret;

    private transient Row rowCert;

    private transient Row rowJwks;

    // THE RESCAN BUTTON IS GONE. The machine is read once, in this
    // constructor, and a developer who installs a Canton while the window is
    // open reopens the window - which is cheaper than a button that has to be
    // safe to press at any moment, including under a running stack.

    private final List<JComponent> lstInput = new ArrayList<>();

    /** Label and field together, so a hidden row does not leave its caption. */
    private final List<Row> lstRow = new ArrayList<>();

    /**
     * Told which installation is selected whenever that changes, or null for
     * none. The window uses it to swap profiles; nothing else may.
     */
    private transient Consumer<CantonInstallation> sinkInstall;

    /**
     * True while {@link #rescan} is rebuilding the box.
     *
     * `removeAllItems` and every `addItem` fire an action event, so a rescan
     * without this reports four selection changes for one, and each of them
     * would have the window write a profile for whichever version happened to
     * be halfway into the model.
     */
    private transient boolean flagAdjusting;


    /**
     * @param options what the command line asked for, used to pre-fill; never
     *        null - pass {@link SandboxOptions#ofDefaults()} when there was no
     *        command line
     */
    public SandboxForm(SandboxOptions options) {
        super(new GridBagLayout());
        setOpaque(false);

        cmbCanton.setRenderer(new InstallRenderer());
        cmbCanton.addActionListener(evt -> selectionChanged());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(GuiTheme.scale(4), GuiTheme.scale(4), GuiTheme.scale(4),
                GuiTheme.scale(4));
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int nRow = 0;
        nRow = addRow(gbc, nRow, STR_LABEL_SDK, pairWith(cmbCanton, btnInstallSdk), null,
                FieldHelp.KEY_CANTON);
        nRow = addRow(gbc, nRow, "PQS", cmbPqs, SandboxFeature.PQS, FieldHelp.KEY_PQS);
        nRow = addRow(gbc, nRow, "First port", fldPortFirst, null, FieldHelp.KEY_PORT_FIRST);
        nRow = addRow(gbc, nRow, "PostgreSQL port", fldPortPostgres, null,
                FieldHelp.KEY_PORT_POSTGRES);
        digitsOnly(fldTimeout);
        fldTimeout.setHorizontalAlignment(JTextField.LEFT);
        nRow = addRow(gbc, nRow, "Ready timeout s", fldTimeout, null,
                FieldHelp.KEY_TIMEOUT_READY);
        nRow = addRow(gbc, nRow, STR_ROW_AUTH, cmbAuth, null, FieldHelp.KEY_AUTH);
        nRow = addRow(gbc, nRow, STR_ROW_SHAPE, cmbShape, null, FieldHelp.KEY_TOKEN_SHAPE);
        rowShape = lstRow.get(lstRow.size() - 1);
        nRow = addRow(gbc, nRow, STR_ROW_AUDIENCE, fldAuthValue, null,
                FieldHelp.KEY_AUTH_TARGET);
        rowTarget = lstRow.get(lstRow.size() - 1);
        nRow = addRow(gbc, nRow, STR_ROW_SECRET, fldSecret, null, FieldHelp.KEY_SECRET);
        rowSecret = lstRow.get(lstRow.size() - 1);
        nRow = addRow(gbc, nRow, STR_ROW_CERT, pairWith(fldCert, browseFile(fldCert)),
                null, FieldHelp.KEY_CERTIFICATE);
        rowCert = lstRow.get(lstRow.size() - 1);
        nRow = addRow(gbc, nRow, STR_ROW_JWKS, fldJwks, null, FieldHelp.KEY_JWKS);
        rowJwks = lstRow.get(lstRow.size() - 1);

        lstInput.add(cmbCanton);
        // DOWN WITH THE REST WHILE A STACK RUNS. An install writes the tree
        // the running Canton was started from.
        lstInput.add(btnInstallSdk);
        lstInput.add(cmbPqs);
        lstInput.add(fldPortFirst);
        lstInput.add(fldPortPostgres);
        lstInput.add(fldTimeout);
        lstInput.add(cmbAuth);
        lstInput.add(cmbShape);
        lstInput.add(fldAuthValue);
        lstInput.add(fldSecret);
        lstInput.add(fldCert);
        lstInput.add(fldJwks);
        // WHICH FIELD MEANS ANYTHING DEPENDS ON THE TYPE, so the ones that
        // do not are taken away rather than left to be filled in and
        // ignored. A form that accepts a value it discards is a form that
        // lies.
        // JWT-JWKS IS WHAT A WINDOW WITH NOTHING TO READ STARTS ON -
        // operator instruction. `Mode.values()` puts NONE first and a
        // combo takes index 0, so the unauthenticated mode was the
        // default by accident of declaration order.
        cmbAuth.setSelectedItem(AuthSettings.Mode.JWKS);
        cmbAuth.addActionListener(evt -> applyAuthRows());
        cmbShape.addActionListener(evt -> applyAuthRows());

        apply(options);
        // THE FIRST READ IS THE PREFETCH'S - A-63 (b). `OpenPrefetch` says why.
        rescan(options, false);
    }


    /**
     * Reads the machine again and keeps the selection where it still exists.
     *
     * @param options what to select, or null to keep what is selected
     */
    public void rescan(SandboxOptions options) {
        rescan(options, true);
    }


    /**
     * @param options what to select, or null to keep what is selected
     * @param flagFresh true to read the disk now; false for the window's first
     *        read, which takes what {@link OpenPrefetch} read
     */
    private void rescan(SandboxOptions options, boolean flagFresh) {
        flagAdjusting = true;
        try {
            rescanNow(options, flagFresh);
        }
        finally {
            flagAdjusting = false;
        }
        // ONE report, after the box has settled, and unconditional: a rescan
        // that landed on the same version may still have landed on a
        // different INSTALLATION of it, and the window compares.
        selectionChanged();
    }


    private void rescanNow(SandboxOptions options, boolean flagFresh) {
        Object objSelected = cmbCanton.getSelectedItem();
        CantonInstallation selected = objSelected instanceof CantonInstallation
                ? (CantonInstallation) objSelected : null;
        cmbCanton.removeAllItems();
        mapBundle = DpmBundles.mapBundle(DpmBundles.dirSdk(ToolchainRoots.ofDefaults()));

        // OLDEST FIRST in the box. Discovery hands back newest first and that
        // stays as it is - the capture and probe suites read that order - so
        // the reversal is a copy made here and nowhere else. The DEFAULT
        // selection is unchanged: still the newest that can start.
        // LESS A JAR-LESS TWIN of a version that has a jar elsewhere - D-837.
        List<CantonInstallation> lstInstall = new ArrayList<>(
                CantonInstallations.lstShown(flagFresh ? SandboxService.rescanCanton()
                        : OpenPrefetch.lstCanton()));
        Collections.reverse(lstInstall);

        CantonInstallation startable = null;
        for (CantonInstallation inst : lstInstall) {
            cmbCanton.addItem(inst);
            if (!isStartable(inst))
                continue;
            // CantonInstallation.compareTo sorts NEWEST FIRST, so a version
            // comparison is the one that reads as it means here.
            if (startable == null || inst.version().compareTo(startable.version()) > 0)
                startable = inst;
            if (options != null && options.version() != null
                    && inst.version().equals(options.version()))
                selected = inst;
            else if (options != null && options.version() == null && options.strLine() != null
                    && inst.version().isLine(options.strLine())
                    && (selected == null
                            || inst.version().compareTo(selected.version()) > 0))
                selected = inst;
        }

        widenToRows();

        if (selected != null && isStartable(selected))
            cmbCanton.setSelectedItem(selected);
        else if (startable != null)
            cmbCanton.setSelectedItem(startable);
    }


    /**
     * @param inst any installation
     * @return whether this application could start it
     */
    public static boolean isStartable(CantonInstallation inst) {
        if (inst == null || !inst.hasRuntime())
            return false;
        // BOTH generations, since 2026-08-16. The service has a 2.x arm over
        // `Sandbox2xStack`, so the window no longer has to refuse the column
        // that has the harder PQS story and therefore the more interesting
        // one to look at.
        return inst.version().major() == 2 || inst.version().major() == 3;
    }


    /**
     * @return what is selected, or null when nothing startable is installed
     */
    /**
     * THE BOX BECOMES SPLICE - his instruction of 2026-09-21.
     *
     * The same row, the same position, the same width; what changes is the
     * label and what is in the list. `rescan` is not called on this topology,
     * so nothing puts the Canton installations back.
     *
     * @param lstVersion the Splice bundles under ~/.splice, newest first
     */
    public void useSplice(List<String> lstVersion) {
        // THE ITEM STAYS THE VERSION; ONLY ITS TEXT CARRIES THE SDK - A-39.
        // `strVersionSelected` reads the item, so what a start receives is
        // unchanged. Each bundle's page is read once, here, not per paint.
        Map<String, String> mapShown = new HashMap<>();
        for (String strVersion : lstVersion) {
            mapShown.put(strVersion, SpliceInstallations.strShown(strVersion));
        }
        useSplice(lstVersion, mapShown);
    }


    /**
     * @param lstVersion the staged Splice versions, newest first
     * @param mapShown each version as the dropdown shows it, already read - at
     *        open by {@link OpenPrefetch}, A-63 (b)
     */
    public void useSplice(List<String> lstVersion, Map<String, String> mapShown) {
        cmbCanton.setRenderer(new DefaultListCellRenderer() {

            private static final long serialVersionUID = 1L;


            @Override
            public Component getListCellRendererComponent(JList<?> lstUi, Object objValue,
                    int idx, boolean isSelected, boolean hasFocus) {
                Object objShown = objValue instanceof String
                        ? mapShown.getOrDefault(objValue, (String) objValue) : objValue;
                return super.getListCellRendererComponent(lstUi, objShown, idx, isSelected, hasFocus);
            }
        });
        // THE SDK ROWS' PROTOTYPE does not describe a Splice version.
        cmbCanton.setPrototypeDisplayValue(null);
        cmbCanton.removeAllItems();
        for (String strVersion : lstVersion) {
            cmbCanton.addItem(strVersion);
        }
        if (cmbCanton.getItemCount() > 0)
            cmbCanton.setSelectedIndex(0);
        for (Row row : lstRow) {
            if (STR_LABEL_SDK.equals(row.lbl.getText()))
                row.lbl.setText(STR_LABEL_SPLICE);
        }
    }


    /**
     * @return the Splice version selected, or null when the box holds Cantons
     *         or nothing is installed
     */
    public String strVersionSelected() {
        Object objSelected = cmbCanton.getSelectedItem();
        return objSelected instanceof String ? (String) objSelected : null;
    }


    /**
     * @return what is selected, or null when nothing startable is installed
     */
    public CantonInstallation selected() {
        Object objItem = cmbCanton.getSelectedItem();
        CantonInstallation inst = objItem instanceof CantonInstallation
                ? (CantonInstallation) objItem : null;
        return isStartable(inst) ? inst : null;
    }


    /**
     * Everything in the box that could actually be started, in the order it is
     * shown - OLDEST FIRST, which is the order {@link #rescan} puts it in.
     *
     * For an unattended harness, and public for that reason alone. The box's
     * own model is the authority on what a harness can select, and a harness
     * that asked {@link SandboxService#lstCanton()} instead would be walking a
     * list the window might not be showing.
     *
     * @return the startable installations; never null, possibly empty
     */
    public List<CantonInstallation> lstStartable() {
        List<CantonInstallation> lstOut = new ArrayList<>();
        for (int idx = 0; idx < cmbCanton.getItemCount(); idx++) {
            Object objAt = cmbCanton.getItemAt(idx);
            if (!(objAt instanceof CantonInstallation))
                continue;
            CantonInstallation inst = (CantonInstallation) objAt;
            if (isStartable(inst))
                lstOut.add(inst);
        }
        return lstOut;
    }


    /**
     * @param inst what to select; ignored when it is not startable, because a
     *        selection the window would refuse to start is worse than the one
     *        already there
     */
    public void select(CantonInstallation inst) {
        if (isStartable(inst))
            cmbCanton.setSelectedItem(inst);
    }


    /**
     * @param action what the button beside the box does; never null
     */
    public void useSdkInstallAction(Runnable action) {
        if (action == null)
            throw new IllegalArgumentException("an action is required");

        btnInstallSdk.addActionListener(evt -> action.run());
    }


    /**
     * @param sinkNew told which installation is selected whenever that changes,
     *        from the event dispatch thread; null for none
     */
    public void useInstallSink(Consumer<CantonInstallation> sinkNew) {
        this.sinkInstall = sinkNew;
    }


    private void selectionChanged() {
        if (flagAdjusting)
            return;
        // WHICH GENERATION'S DOCUMENTATION THE HELP DIALOGS OFFER. Here
        // rather than in applyCaps because it is true the moment the box
        // changes, and it costs nothing if the window never raises a dialog.
        CantonInstallation installNow = selected();
        FieldHelp.useGeneration(installNow == null ? 0 : installNow.version().major());
        Consumer<CantonInstallation> sinkHere = sinkInstall;
        if (sinkHere != null)
            sinkHere.accept(selected());
    }


    /**
     * Takes away every row the selected version has no use for, and tells the
     * rest what is known about them.
     *
     * Called by the window after a selection change rather than from
     * {@link #selectionChanged}, so that the profile and the layout move in one
     * step and the form is never briefly showing one version's rows with
     * another version's values in them.
     */
    public void applyCaps() {
        CantonInstallation inst = selected();
        for (Row row : lstRow) {
            if (row.feature == null)
                continue;

            FeatureSupport support = SandboxCapabilities.of(row.feature,
                    inst == null ? null : inst.version(),
                    inst == null ? null : inst.edition());
            row.lbl.setVisible(support.isExposed());
            row.comp.setVisible(support.isExposed());
            row.lbl.setToolTipText(support.strTooltip());
            row.comp.setToolTipText(support.strTooltip());
        }
        // THE PICKER SAYS WHY a version is marked - T-3, D-835.
        cmbCanton.setToolTipText(inst == null ? null
                : SandboxCapabilities.strUnmeasured(inst.version(), inst.edition()));
        revalidate();
        repaint();
    }


    /**
     * @param profile what the selected version remembers; never null
     */
    public void applyProfile(SandboxProfile profile) {
        if (profile == null)
            throw new IllegalArgumentException("a profile is required");
        fldPortFirst.setText(String.valueOf(profile.nPortFirst()));
        fldPortPostgres.setText(String.valueOf(profile.nPortPostgres()));
        fldRunDir.setText(profile.dirRun().toString());
        fldTimeout.setText(String.valueOf(profile.nSecondsReady()));

        AuthSettings authHere = profile.auth();
        cmbAuth.setSelectedItem(authHere.mode());
        cmbShape.setSelectedItem(authHere.shape());
        // SHOWN, not implied, for the reason the JWKS row gives: a blank
        // field that silently means the convention is a field whose value
        // nobody can read off the screen, and this one is half of what
        // decides whether a token is accepted.
        fldAuthValue.setText(strTargetDefault(authHere));
        fldSecret.setText(authHere.strSecret());
        fldCert.setText(authHere.strFileCert());
        // SHOWN, not implied. A blank field that silently means `the local
        // service` is a field whose value nobody can read off the screen, and
        // this one ends up in a participant's configuration.
        setJwksShown(authHere.strUrlJwksEffective(JwtMintProcess.strUrlJwks()));
        applyAuthRows();
        // PQS IS NOT TOUCHED. It is not in the profile and it is not reset
        // either: a developer who turned it on and then changed version was
        // not asking for it to be turned off again.
    }


    /**
     * @return what the fields hold, as the selected version would remember it
     * @throws IllegalArgumentException with a message naming the field
     */
    public SandboxProfile profile() {
        // THE FOUR THIS FORM OWNS, and nothing for the rest. The DAR directory
        // and the selection are the DARs tab's; the window puts the parts
        // together with `withDars` before writing - which is one place rather
        // than a second copy of that tab's state kept here.
        return new SandboxProfile(nPortFirstOf(fldPortFirst),
                nPortPostgresOf(fldPortPostgres), run().dirRun(),
                nSecondsReady(), null, null, auth());
    }


    /**
    /**
     * WHAT THE PQS ROW SAYS, on its own.
     *
     * NOT through {@link #toOptions}. That method resolves the Canton combo
     * with {@link #selected()}, and on the LocalNetND topology that combo
     * holds SPLICE versions - so it returns null and `toOptions` throws "no
     * startable Canton is installed". A caller that wants one row should not
     * have to build a whole `SandboxOptions` to read it.
     *
     * @return what to run PQS as, never null
     */
    public SandboxOptions.PqsMode pqs() {
        return (SandboxOptions.PqsMode) cmbPqs.getSelectedItem();
    }


    /**
     * @param mode what to run PQS as; ignored when null
     */
    public void setPqs(SandboxOptions.PqsMode mode) {
        if (mode != null)
            cmbPqs.setSelectedItem(mode);
    }


    /**
     * @return the PostgreSQL port the field holds
     * @throws IllegalArgumentException when it is not a port
     */
    /**
     * @return where the participant will serve the JSON Ledger API, which on
     *         3.x is the first port of the block
     * @throws IllegalArgumentException when the field is not a port
     */
    public int nPortJsonApi() {
        return nPortFirstOf(fldPortFirst);
    }


    public int nPortPostgres() {
        return nPortPostgresOf(fldPortPostgres);
    }


    /**
     * @param nPort the PostgreSQL port to show
     * @throws IllegalArgumentException when it is outside the usable range
     */
    public void setPortPostgres(int nPort) {
        requireIn(nPort, SandboxProfile.N_PORT_POSTGRES_MIN, SandboxProfile.N_PORT_POSTGRES_MAX,
                "PostgreSQL port");
        fldPortPostgres.setText(String.valueOf(nPort));
    }


    /**
     * For an unattended harness, which moves the whole Canton block rather
     * than only PostgreSQL: over many iterations the block's own
     * TIME_WAIT costs more than an unattended run can afford to sit through.
     *
     * @param nPort the first port of the block to show
     * @throws IllegalArgumentException when it is outside the usable range
     */
    public void setPortFirst(int nPort) {
        requireIn(nPort, SandboxProfile.N_PORT_FIRST_MIN, SandboxProfile.N_PORT_FIRST_MAX,
                "First port");
        fldPortFirst.setText(String.valueOf(nPort));
    }


    /**
     * @return how long the form gives a stack to report ready
     */
    public Duration timeoutReady() {
        return Duration.ofSeconds(nSecondsReady());
    }


    /**
     * @return the run directory the fields name
     */
    public RunDirectory run() {
        return new RunDirectory(pathOf(fldRunDir, SandboxOptions.dirWorkDefault()));
    }


    /**
     * @param flagOn whether the fields accept input
     */
    public void setInputEnabled(boolean flagOn) {
        for (JComponent comp : lstInput) {
            comp.setEnabled(flagOn);
        }
    }


    /**
     * READ OFF A WIDGET, not off a remembered flag. The caller asking is
     * asserting what is on screen, and a field that recorded the last call
     * would answer for the call rather than for the form.
     *
     * @return whether the fields accept input
     */
    public boolean isInputEnabled() {
        return !lstInput.isEmpty() && lstInput.get(0).isEnabled();
    }


    /**
     * The whole point of the class.
     *
     * PASSED IN rather than read here, because the DARs are not a field on this
     * form. They are a tab, they are a subset of a directory rather than the
     * directory itself, and the form's rule is that it produces options out of
     * what it holds - not that it goes looking for the rest.
     *
     * @param lstFileDar the DARs the next start uploads; may be empty
     * @return what the fields ask for
     * @throws IllegalArgumentException with a message naming the field
     */
    /**
     * @return what the six auth rows hold; never null
     */
    public AuthSettings auth() {
        return new AuthSettings((AuthSettings.Mode) cmbAuth.getSelectedItem(),
                (TokenShape) cmbShape.getSelectedItem(), fldAuthValue.getText(),
                fldSecret.getText(), fldCert.getText(), fldJwks.getText());
    }


    /**
     * Sets the six auth rows to what a developer would have typed.
     *
     * THE EFFECTIVE VALUES, not the blanks. {@link #applyProfile} writes the
     * secret through as it was stored, because a profile is a record of what
     * somebody typed; this writes what the settings RESOLVE to, because
     * {@link #requireAuthComplete} refuses a start over an empty secret, an
     * empty target or an empty JWKS url - and a refusal is a modal dialog,
     * which is the one thing an unattended harness cannot answer.
     *
     * The rows are laid out afterwards exactly as they are after a profile is
     * applied, so the window shows what it is about to start rather than the
     * previous cell's fields.
     *
     * @param authNew what to show; ignored when null
     */
    public void setAuth(AuthSettings authNew) {
        if (authNew == null)
            return;

        cmbAuth.setSelectedItem(authNew.mode());
        cmbShape.setSelectedItem(authNew.shape());
        fldAuthValue.setText(strTargetDefault(authNew));
        fldSecret.setText(authNew.strSecretEffective());
        fldCert.setText(authNew.strFileCert());
        setJwksShown(authNew.strUrlJwksEffective(JwtMintProcess.strUrlJwks()));
        applyAuthRows();
    }


    /**
     * Points the JWKS row at a provider, UNLESS the operator typed something.
     *
     * @param strUrlNew where the key set is now, or null or blank to leave the
     *        row alone
     */
    public void repointJwks(String strUrlNew) {
        if (strUrlNew == null || strUrlNew.isBlank())
            return;

        String strNow = fldJwks.getText().trim();
        // A ROW THE OPERATOR EDITED IS HIS. Anything else in it was put there
        // by this form and is a default that has just been superseded.
        if (!strNow.isEmpty() && !strNow.equals(strUrlJwksShown))
            return;
        setJwksShown(strUrlNew);
    }


    /**
     * @param strUrl what to show, and to remember having shown
     */
    private void setJwksShown(String strUrl) {
        this.strUrlJwksShown = strUrl == null ? "" : strUrl;
        fldJwks.setText(strUrlJwksShown);
    }


    /**
     * Shows the rows the selected auth-service type actually reads, and
     * hides the rest.
     */
    private void applyAuthRows() {
        AuthSettings.Mode mode = (AuthSettings.Mode) cmbAuth.getSelectedItem();
        AuthSettings.Mode modeHere = mode == null ? AuthSettings.Mode.JWKS : mode;

        // THE SHARED SECRET IS THE WHOLE CONFIGURATION for this type. It is
        // HMAC256 over a plaintext string and there is nothing else to say
        // about it, so a shape and a target beside it would be two rows the
        // participant never reads.
        boolean flagHmac = modeHere == AuthSettings.Mode.UNSAFE_HMAC_256;
        boolean flagTargets = modeHere.flagTargets() && !flagHmac;

        setRowShown(rowShape, flagTargets);
        setRowShown(rowTarget, flagTargets);
        setRowShown(rowSecret, flagHmac);
        setRowShown(rowCert, modeHere.flagCertificate());
        setRowShown(rowJwks, modeHere == AuthSettings.Mode.JWKS);

        // THE FIELD FOLLOWS THE SHAPE when it still holds the other shape's
        // convention. An audience left behind in a scope-based configuration
        // is refused by the participant, and the message is about the scope.
        String strTarget = fldAuthValue.getText().trim();
        if (strTarget.isEmpty() || strTarget.equals(STR_TARGET_SCOPE)
                || strTarget.equals(strAudienceConvention()))
            fldAuthValue.setText(strConventionForShape());
        rowTarget.lbl.setText(cmbShape.getSelectedItem() == TokenShape.SCOPE
                ? STR_ROW_SCOPE : STR_ROW_AUDIENCE);
        revalidate();
        repaint();
    }


    /**
     * @param strLabel the caption the row was added with
     * @param flagShow whether it belongs on the selected type
     */
    /**
     * @param authHere what the fields hold
     * @return the convention for the selected shape
     */
    private String strTargetDefault(AuthSettings authHere) {
        if (!authHere.strValue().isEmpty())
            return authHere.strValue();
        return strConventionForShape();
    }


    /**
     * @return the convention for the selected shape and NOTHING the field
     *         currently holds - which is what a shape change needs, since the
     *         value it is replacing is the other shape's convention
     */
    private String strConventionForShape() {
        return cmbShape.getSelectedItem() == TokenShape.SCOPE
                ? STR_TARGET_SCOPE : strAudienceConvention();
    }


    /**
     * @return `https://daml.com/jwt/aud/participant/sandbox`, which is the
     *         convention for the node this window starts
     */
    private static String strAudienceConvention() {
        return AuthPlan.audienceFor(StorageOverlay.STR_NODE_PARTICIPANT);
    }


    private void setRowShown(Row row, boolean flagShow) {
        if (row == null)
            return;
        row.lbl.setVisible(flagShow);
        row.comp.setVisible(flagShow);
    }


    /**
     * Refuses a start the auth block could not be rendered from.
     *
     * BEFORE anything is launched, not when Canton reads the file. A missing
     * certificate arrives forty seconds into a start as a Canton
     * configuration error, and by then PostgreSQL is up and a developer is
     * reading a stack trace to find out they left a field empty.
     *
     * @throws IllegalArgumentException naming the row on screen
     */
    private void requireAuthComplete() {
        AuthSettings.Mode mode = (AuthSettings.Mode) cmbAuth.getSelectedItem();
        if (mode == null)
            return;

        if (mode == AuthSettings.Mode.UNSAFE_HMAC_256
                && fldSecret.getText().trim().isEmpty()) {
            throw new IllegalArgumentException(mode + " needs a " + STR_ROW_SECRET);
        }
        // REFUSED HERE rather than at the mint. Canton accepts a secret of any
        // length and then refuses every token signed with it, because RFC 7518
        // section 3.2 puts a floor under an HS256 key that Canton's own
        // verifier does not. Both sides go quiet and the participant's log
        // says only that the signature did not verify.
        if (mode == AuthSettings.Mode.UNSAFE_HMAC_256) {
            int cntByte = fldSecret.getText().trim()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (cntByte < AuthSettings.N_BYTES_SECRET_MIN) {
                throw new IllegalArgumentException(STR_ROW_SECRET + " must be at least "
                        + AuthSettings.N_BYTES_SECRET_MIN + " bytes for HS256 (RFC 7518"
                        + " section 3.2); this one is " + cntByte);
            }
        }
        if (mode.flagCertificate() && fldCert.getText().trim().isEmpty())
            throw new IllegalArgumentException(mode + " needs a " + STR_ROW_CERT);
        if (mode.flagTargets() && mode != AuthSettings.Mode.UNSAFE_HMAC_256
                && fldAuthValue.getText().trim().isEmpty()) {
            throw new IllegalArgumentException(mode + " needs "
                    + rowTarget.lbl.getText());
        }
        if (mode == AuthSettings.Mode.JWKS && fldJwks.getText().trim().isEmpty())
            throw new IllegalArgumentException(mode + " needs a " + STR_ROW_JWKS);
    }


    /**
     * A browse button for a FILE rather than a directory. The one beside the
     * run directory chooses directories only, and a chooser that will not
     * select the certificate it was opened for is worse than no chooser.
     *
     * @param fld the field to fill
     * @return the button
     */
    private JButton browseFile(JTextField fld) {
        JButton btn = new JButton("...");
        btn.addActionListener(evt -> {
            JFileChooser chooser = new JFileChooser();
            // Hidden directories shown - `~/.raposza` and `~/.splice` are dotted.
            chooser.setFileHidingEnabled(false);
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            String strNow = fld.getText().trim();
            if (!strNow.isEmpty())
                chooser.setSelectedFile(new java.io.File(strNow));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                fld.setText(chooser.getSelectedFile().getAbsolutePath());
        });
        return btn;
    }


    public SandboxOptions toOptions(List<Path> lstFileDar) {
        requireAuthComplete();
        CantonInstallation inst = selected();
        if (inst == null)
            throw new IllegalArgumentException("no startable Canton is installed");

        RunDirectory run = run();
        // NO PARTY AND NO USER. Both were asked for on this form for one
        // increment and are not any more: what a participant is configured
        // to verify and what happens to exist on its ledger are different
        // questions, and this form answers the first one.
        return new SandboxOptions(false, false, inst.version(), null, inst.edition(),
                nPortOffset(), nPortPostgresOf(fldPortPostgres),
                SandboxStack.STR_DEFAULT_PREFIX,
                run.dirWork(), run.dirData(), lstFileDar,
                false, false, N_HEAP_CANTON_MB,
                SandboxLauncher.DAEMON,
                (SandboxOptions.PqsMode) cmbPqs.getSelectedItem(),
                null,
                null,
                false, Duration.ofSeconds(nSecondsReady()));
    }


    /**
     * @return how far Canton's own block has to move to start where the
     *         field says
     */
    private int nPortOffset() {
        return nPortFirstOf(fldPortFirst) - SandboxPorts.N_DEFAULT_JSON_API;
    }


    /**
     * @param fld the field to read
     * @param strWhat its label, so a refusal names the row on screen rather
     *        than a field of this class
     * @return the port it holds
     * @throws IllegalArgumentException when it is not a port
     */
    private static int nPortFirstOf(JTextField fld) {
        return nPortOf(fld, "First port", SandboxProfile.N_PORT_FIRST_MIN,
                SandboxProfile.N_PORT_FIRST_MAX);
    }


    private static int nPortPostgresOf(JTextField fld) {
        return nPortOf(fld, "PostgreSQL port", SandboxProfile.N_PORT_POSTGRES_MIN,
                SandboxProfile.N_PORT_POSTGRES_MAX);
    }


    /**
     * THE SAME FIELD GIVES ONE ANSWER. The bounds are the profile's own - the
     * port classes - so the form cannot accept a number the stack then refuses
     * at start.
     */
    private static void requireIn(int nPort, int nMin, int nMax, String strWhat) {
        if (nPort < nMin || nPort > nMax) {
            throw new IllegalArgumentException(strWhat + " is outside " + nMin + "-" + nMax
                    + ": " + nPort);
        }
    }


    private static int nPortOf(JTextField fld, String strWhat, int nMin, int nMax) {
        // Commas are STRIPPED rather than refused. The field was a spinner
        // until 2026-08-18 and rendered 22211 as `22,211`, so that is what
        // anyone copying a port out of an earlier window has to paste.
        String strText = fld.getText().trim().replace(",", "").replace(" ", "");
        int nPort;
        try {
            nPort = Integer.parseInt(strText);
        }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException(strWhat + " needs a whole number: "
                    + fld.getText(), ex);
        }
        requireIn(nPort, nMin, nMax, strWhat);
        return nPort;
    }


    private void apply(SandboxOptions options) {
        cmbPqs.setSelectedItem(options.pqs());
        // AN OFFSET OF ZERO IS THE WINDOW'S DEFAULT, not a request for
        // Canton's own 6864. The record cannot tell `--port-offset 0` from an
        // absent flag, and of the two readings only one is ever meant: a
        // developer who opens the window without arguments wants this
        // application's port, and one who passes an offset wants that offset.
        // Reading zero literally is what put 6864 in the field.
        int nPortFirst = options.nPortOffset() == 0 ? SandboxProfile.N_PORT_FIRST_DEFAULT
                : SandboxPorts.N_DEFAULT_JSON_API + options.nPortOffset();
        fldPortFirst.setText(String.valueOf(nPortFirst));
        fldPortPostgres.setText(String.valueOf(options.nPortPostgres()));
        fldTimeout.setText(String.valueOf(options.timeoutReady().getSeconds()));
        fldRunDir.setText(options.dirWork() == null ? "" : options.dirWork().toString());
    }


    /**
     * @param gbc the shared constraints, mutated as it goes
     * @param nRow which row
     * @param strLabel the caption
     * @param comp the input
     * @param feature the capability this row needs, or null when it always
     *        applies
     * @param strHelp a {@link FieldHelp} key, or null for a row with no help
     * @return the next row index
     */
    private int addRow(GridBagConstraints gbc, int nRow, String strLabel, JComponent comp,
            SandboxFeature feature, String strHelp) {
        gbc.gridx = 0;
        gbc.gridy = nRow;
        gbc.weightx = 0.0;
        JLabel lbl = new JLabel(strLabel);
        FieldHelp.install(lbl, strHelp);
        add(lbl, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        // A FIELD MUST BE ALLOWED TO GET SMALLER THAN IT WANTS. GridBagLayout
        // shrinks a cell no further than the component's MINIMUM, and a text
        // field built with a column count reports its preferred size as its
        // minimum - so the column refused to narrow, the card stayed wide, and
        // the divider could not be dragged left. The preferred width still
        // decides the opening size; this only says the field may give ground.
        comp.setMinimumSize(new Dimension(GuiTheme.scale(N_WIDTH_FIELD_MIN),
                comp.getPreferredSize().height));
        add(comp, gbc);
        lstRow.add(new Row(lbl, comp, feature));
        return nRow + 1;
    }


    private JPanel pairWith(JComponent compLeft, JComponent compRight) {
        JPanel pnlPair = new JPanel(new java.awt.BorderLayout(GuiTheme.scale(8), 0));
        pnlPair.setOpaque(false);
        pnlPair.add(compLeft, java.awt.BorderLayout.CENTER);
        pnlPair.add(compRight, java.awt.BorderLayout.EAST);
        return pnlPair;
    }


    /**
     * @return the ready timeout the field holds
     * @throws IllegalArgumentException when it is empty or outside the range
     */
    private int nSecondsReady() {
        String strText = fldTimeout.getText().trim().replace(",", "").replace(" ", "");
        int nSeconds;
        try {
            nSeconds = Integer.parseInt(strText);
        }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Ready timeout s needs a whole number: "
                    + fldTimeout.getText(), ex);
        }
        if (nSeconds < SandboxProfile.N_SECONDS_READY_MIN
                || nSeconds > SandboxProfile.N_SECONDS_READY_MAX) {
            throw new IllegalArgumentException("Ready timeout s is outside "
                    + SandboxProfile.N_SECONDS_READY_MIN + "-"
                    + SandboxProfile.N_SECONDS_READY_MAX + " s: " + nSeconds);
        }
        return nSeconds;
    }


    /**
     * Digits and nothing else, refused at the keystroke.
     *
     * THE RANGE IS NOT CHECKED HERE. A filter that refused `3` on the way to
     * `300` would make the field impossible to type into; the bound belongs
     * where the value is read and where a refusal can name the row on screen.
     *
     * @param fld the field to restrict
     */
    private static void digitsOnly(JTextField fld) {
        ((AbstractDocument) fld.getDocument()).setDocumentFilter(new DocumentFilter() {

            @Override
            public void insertString(FilterBypass bypass, int nOffset, String strText,
                    AttributeSet attr) throws BadLocationException {
                if (flagDigits(strText))
                    super.insertString(bypass, nOffset, strText, attr);
            }


            @Override
            public void replace(FilterBypass bypass, int nOffset, int cntLength,
                    String strText, AttributeSet attr) throws BadLocationException {
                if (flagDigits(strText))
                    super.replace(bypass, nOffset, cntLength, strText, attr);
            }
        });
    }


    /**
     * @param strText what is being typed or pasted; null on a delete
     * @return whether every character of it is a digit
     */
    private static boolean flagDigits(String strText) {
        if (strText == null)
            return true;

        for (int idxChar = 0; idxChar < strText.length(); idxChar++) {
            if (!Character.isDigit(strText.charAt(idxChar)))
                return false;
        }
        return true;
    }


    private static Path pathOf(JTextField fld, Path pathIfBlank) {
        String strText = fld.getText().trim();
        return strText.isEmpty() ? pathIfBlank : Paths.get(strText);
    }


    /**
     * One line of the form.
     *
     * A plain holder rather than a record, because a record's accessors would
     * be `lbl()` and `comp()` on a type only this class can see, and the two
     * fields are set once in {@link #addRow} and never again.
     */
    private static final class Row {

        private final JLabel lbl;

        private final JComponent comp;

        /** What decides whether this row is shown, or null for always. */
        private final SandboxFeature feature;


        Row(JLabel lblNew, JComponent compNew, SandboxFeature featureNew) {
            this.lbl = lblNew;
            this.comp = compNew;
            this.feature = featureNew;
        }
    }


    /**
     * @param inst one row
     * @param flagClosed whether it is shown in the closed box
     * @return the row's text with its marker
     */
    private String strShown(CantonInstallation inst, boolean flagClosed) {
        String strText = InstallLabel.strRow(inst, mapBundle);
        if (!inst.hasRuntime())
            return strText + "   [no runtime jar]";
        // SECOND-CLASS UNTIL MEASURED - T-3, D-835.
        if (SandboxCapabilities.strUnmeasured(inst.version(), inst.edition()) != null)
            return flagClosed ? strText + " " + SandboxCapabilities.STR_UNMEASURED_SHORT
                    : strText + "   " + SandboxCapabilities.STR_UNMEASURED;
        return strText;
    }


    /**
     * WIDE ENOUGH FOR THE WIDEST ROW - his instruction of 2026-09-25, "widen
     * the dropdown a bit so that the data is visible". The prototype is the
     * widest row as the open list spells it, so the closed box and the list,
     * which is as wide as the box, show every row whole. A String prototype
     * reaches {@link InstallRenderer} as itself and is rendered as its text.
     */
    private void widenToRows() {
        java.awt.FontMetrics fm = cmbCanton.getFontMetrics(cmbCanton.getFont());
        String strWidest = null;
        int nWidest = -1;
        for (int idx = 0; idx < cmbCanton.getItemCount(); idx++) {
            Object objItem = cmbCanton.getItemAt(idx);
            if (!(objItem instanceof CantonInstallation))
                continue;
            String strText = strShown((CantonInstallation) objItem, false);
            int nWidth = fm.stringWidth(strText);
            if (nWidth > nWidest) {
                nWidest = nWidth;
                strWidest = strText;
            }
        }
        cmbCanton.setPrototypeDisplayValue(strWidest);
    }


    /**
     * The SDK first, the Canton second - {@link InstallLabel}. Greys out what
     * cannot be started, rather than hiding it.
     */
    private final class InstallRenderer extends DefaultListCellRenderer {

        private static final long serialVersionUID = 1L;


        @Override
        public Component getListCellRendererComponent(javax.swing.JList<?> list, Object objValue,
                int nIndex, boolean flagSelected, boolean flagFocus) {
            Component comp = super.getListCellRendererComponent(list, objValue, nIndex,
                    flagSelected, flagFocus);
            if (!(objValue instanceof CantonInstallation))
                return comp;

            CantonInstallation inst = (CantonInstallation) objValue;
            // nIndex -1 is the closed box, which has room for a star only.
            setText(strShown(inst, nIndex < 0));
            if (!isStartable(inst) && !flagSelected)
                setForeground(GuiTheme.colMuted());
            return comp;
        }
    }

}
