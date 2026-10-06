// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.runtime.settings.RaposzaSettings;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.HierarchyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JCheckBox;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The settings that are NOT per version and edition.
 *
 * <h2>ONE directory, and the line this tab draws</h2>
 *
 * `~/.raposza`, and everything else hangs under it. Six roots that could only
 * ever be moved together are one row, and the layout beneath it is not a
 * question anybody has to answer.
 *
 * Beyond the directory: a setting is GLOBAL when its value has no version in
 * it, and PER VERSION when it does. This tab owns the globals and says nothing
 * at all about the rest - the per-version values are on the Sandbox tab, which
 * is where they are edited, and repeating them here read-only was a column of
 * text nobody needed twice.
 *
 * <h2>There is no Save button</h2>
 *
 * An edit is written when it stops changing - 600 ms after the last keystroke -
 * and a field that does not parse is simply not written. A half-typed port is
 * never stored, and the moment it becomes a number it is. The footer says what
 * happened; the path under the cards says where.
 *
 * `Restore defaults` is the one destructive control, so it is the one with an
 * `Undo` beside it. Undo comes alive when defaults are applied and goes back to
 * exactly what was in force before, written the same way. It is not a general
 * edit history and does not pretend to be one.
 *
 * Author Claude/bentzn
 */
public final class SettingsPane extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Long enough that typing a path is one write rather than forty. */
    private static final int N_MS_SETTLE = 600;

    private static final int N_COLS_PATH = 44;

    private static final int N_COLS_SHORT = 10;

    private final JButton btnUndo = new JButton("Undo");

    private final JButton btnDefaults = new JButton("Restore defaults");

    private final Map<String, JTextField> mapField = new LinkedHashMap<>();

    /** The `...` beside each path field, by the same key. */
    private final Map<String, JButton> mapBrowse = new LinkedHashMap<>();

    /**
     * WHAT A RUNNING STACK IS BUILT ON - his instruction, 2026-10-04: lock
     * what cannot be changed while the system runs.
     *
     * The directory holds the keys the mint signs with and the snapshots;
     * the mint port is where the participant fetches its key set, named in
     * its configuration at start; the PostgreSQL, first stack and first web
     * UI ports are the running stack's own on LocalNetND; and the three
     * installation directories hold the toolchain a fixture runs scripts
     * with. The discovery and RAWAR ports are NOT here: a save rebinds both
     * servers on the spot - `SandboxWindow.settingsSaved`.
     */
    static final List<String> LST_KEY_LOCKED = List.of(RaposzaSettings.STR_KEY_DIR_HOME,
            RaposzaSettings.STR_KEY_PORT_MINT, RaposzaSettings.STR_KEY_PORT_POSTGRES,
            RaposzaSettings.STR_KEY_PORT_FIRST, RaposzaSettings.STR_KEY_PORT_UI_FIRST,
            RaposzaSettings.STR_KEY_DIR_DAML, RaposzaSettings.STR_KEY_DIR_DPM,
            RaposzaSettings.STR_KEY_DIR_SPLICE);

    private static final String STR_WHY_LOCKED = "Locked while the stack is running."
            + " Stop it to change this.";

    /** Whether {@link #setLocked} has locked the rows above. */
    private transient boolean flagLocked;

    /**
     * The one row on this tab that is not a text field.
     *
     * A CHECKBOX RATHER THAN A FIELD because the value is an answer to a
     * question and not a number: the offer is made or it is not. It therefore
     * saves on the click rather than on the settle timer - there is no
     * half-typed state to wait out.
     */
    private final JCheckBox chkOfferAviation = new JCheckBox("Offer to create it on a start");

    /** The same question for Pharma, which is LocalNetND's fixture. */
    private final JCheckBox chkOfferPharma = new JCheckBox("Offer to create it on a start");

    /**
     * For somebody who answered `Never`, or `Not now`, and has changed their
     * mind. It is a BUTTON and not a tick, because it does something once
     * rather than describing a state.
     */
    private final JButton btnAviationNow = new JButton("Create Aviation test data");

    /**
     * The narrative the offer at start puts behind `Expand`, reachable without
     * waiting for the offer.
     *
     * ALWAYS ENABLED. It explains what the fixture is, which is worth reading
     * exactly when the button above it is greyed out and the reader wants to
     * know what they are missing.
     */
    private final JButton btnAviationDoc = new JButton("Aviation documentation");

    /** Pharma's two, which do for LocalNetND what Aviation's do for the Sandbox. */
    private final JButton btnPharmaNow = new JButton("Create Pharma test data");

    private final JButton btnPharmaDoc = new JButton("Pharma documentation");

    /**
     * THE TWO FIXTURES ARE NOT SHOWN TOGETHER - operator instruction,
     * 2026-09-22. A window drives ONE topology and only one of the two can be
     * built on it, so the other's rows are a question the reader cannot act on.
     */
    private final JPanel pnlAviation = new JPanel(new GridBagLayout());

    private final JPanel pnlPharma = new JPanel(new GridBagLayout());

    /**
     * DELETES THE FOUNDING SNAPSHOT so the next start founds again -
     * operator instruction, 2026-09-22. A button rather than a tick,
     * because it does something once rather than describing a state, and
     * the user snapshots are not touched by it.
     */
    private final JButton btnFoundingReset = new JButton("Reset founding");

    /** Whether a founding snapshot exists for what is selected, and for what. */
    private final JLabel lblFounding = new JLabel();

    private final JLabel lblFile = new JLabel();

    private final Timer timerSettle = new Timer(N_MS_SETTLE, null);

    private transient Consumer<String> sayNotice;

    private transient Runnable runSaved;

    private transient Runnable runAviation;

    private transient Runnable runPharma;

    private transient Runnable runFounding;

    private transient RaposzaSettings settingsUndo;

    /**
     * `default.line` and `default.launcher` have NO row on this tab - operator
     * instruction, 2026-08-23 - so they are carried here instead of read back
     * out of a field. Carried rather than re-read from
     * {@link RaposzaSettings#current()}, or `Restore defaults` would write
     * every other value as a default and leave these two as they were.
     */
    private transient String strLine;

    private transient String strLauncher;

    /** True while the fields are being filled, so a fill is not an edit. */
    private transient boolean flagLoading;


    public SettingsPane() {
        super(new BorderLayout(GuiTheme.scale(GuiTheme.N_GAP), GuiTheme.scale(GuiTheme.N_GAP)));

        JPanel pnlStack = new JPanel(new GridBagLayout());
        pnlStack.setOpaque(false);
        addCard(pnlStack, 0, GuiTheme.card("Directory", pnlDirectory()));
        addCard(pnlStack, 1, GuiTheme.card("Ports and defaults", pnlValues()));
        addCard(pnlStack, 2, GuiTheme.card("Test data", pnlFixture()));
        addCard(pnlStack, 3, GuiTheme.card("Founding snapshot", pnlFounding()));
        // AT THE BOTTOM - operator instruction, 2026-09-26, todo.md A-45.
        addCard(pnlStack, 4, GuiTheme.card("Installations", pnlInstallations()));
        GridBagConstraints gbcFill = new GridBagConstraints();
        gbcFill.gridx = 0;
        gbcFill.gridy = 5;
        gbcFill.weightx = 1.0;
        gbcFill.weighty = 1.0;
        gbcFill.fill = GridBagConstraints.BOTH;
        pnlStack.add(Box.createGlue(), gbcFill);

        JScrollPane scroll = new JScrollPane(pnlStack);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(GuiTheme.scale(16));
        add(scroll, BorderLayout.CENTER);

        JPanel pnlSouth = new JPanel(new BorderLayout());
        pnlSouth.setOpaque(false);
        lblFile.setForeground(GuiTheme.colMuted());
        pnlSouth.add(lblFile, BorderLayout.WEST);
        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, GuiTheme.scale(8), 0));
        pnlButtons.setOpaque(false);
        pnlButtons.add(btnUndo);
        pnlButtons.add(btnDefaults);
        pnlSouth.add(pnlButtons, BorderLayout.EAST);
        add(pnlSouth, BorderLayout.SOUTH);

        btnUndo.setEnabled(false);
        btnUndo.addActionListener(evt -> undoRequested());
        btnDefaults.addActionListener(evt -> defaultsRequested());

        timerSettle.setRepeats(false);
        timerSettle.addActionListener(evt -> saveIfValid());
        for (JTextField fld : mapField.values())
            fld.getDocument().addDocumentListener(new EditListener());
        chkOfferAviation.addActionListener(evt -> {
            if (!flagLoading)
                saveIfValid();
        });
        chkOfferPharma.addActionListener(evt -> {
            if (!flagLoading)
                saveIfValid();
        });
        btnAviationNow.setEnabled(false);
        btnAviationDoc.addActionListener(evt ->
                Modals.inform(this, AviationSession.STR_DETAIL, "Aviation test data"));
        btnAviationNow.addActionListener(evt -> {
            if (runAviation != null)
                runAviation.run();
        });
        btnPharmaNow.setEnabled(false);
        btnPharmaDoc.addActionListener(evt ->
                Modals.inform(this, PharmaSession.STR_DETAIL, "Pharma test data"));
        btnPharmaNow.addActionListener(evt -> {
            if (runPharma != null)
                runPharma.run();
        });
        btnFoundingReset.setEnabled(false);
        btnFoundingReset.addActionListener(evt -> {
            if (runFounding != null)
                runFounding.run();
        });

        addHierarchyListener(evt -> {
            if ((evt.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                revalidate();
                repaint();
            }
        });

        show(RaposzaSettings.current());
    }


    /**
     * @param sinkNew where a one-line notice goes; may be null
     */
    public void useNoticeSink(Consumer<String> sinkNew) {
        this.sayNotice = sinkNew;
    }


    /**
     * @param runNew what to run after a successful save, so the window can
     *        re-point anything it holds; may be null
     */
    public void useSavedSink(Runnable runNew) {
        this.runSaved = runNew;
    }


    /**
     * @param runNew what the `Create Aviation test data` button does, or null to
     *        leave it disabled
     */
    public void useAviationAction(Runnable runNew) {
        this.runAviation = runNew;
        btnAviationNow.setEnabled(runNew != null);
    }


    /**
     * @param flagOn whether the button can be pressed. The window turns it off
     *        while a fixture is being built and leaves it off once the data is
     *        on the ledger - pressing it again would rebuild and re-run against
     *        a ledger that already has the parties
     */
    public void setAviationEnabled(boolean flagOn) {
        btnAviationNow.setEnabled(flagOn && runAviation != null);
    }


    /**
     * @param runNew what the `Create Pharma test data` button does, or null to
     *        leave it disabled
     */
    public void usePharmaAction(Runnable runNew) {
        this.runPharma = runNew;
        btnPharmaNow.setEnabled(runNew != null);
    }


    /**
     * @param flagOn whether the button can be pressed; the window's rule is
     *        Aviation's - off while one is being built and off once the data is
     *        on the ledger
     */
    public void setPharmaEnabled(boolean flagOn) {
        btnPharmaNow.setEnabled(flagOn && runPharma != null);
    }


    /**
     * Locks the rows a running stack is built on, and the two buttons that
     * would rewrite them.
     *
     * @param flagLockedNew true while a stack is starting, running or stopping
     */
    public void setLocked(boolean flagLockedNew) {
        this.flagLocked = flagLockedNew;
        for (String strKey : LST_KEY_LOCKED) {
            JTextField fld = mapField.get(strKey);
            if (fld != null) {
                fld.setEnabled(!flagLockedNew);
                fld.setToolTipText(flagLockedNew ? STR_WHY_LOCKED : null);
            }
            JButton btnBrowse = mapBrowse.get(strKey);
            if (btnBrowse != null)
                btnBrowse.setEnabled(!flagLockedNew);
        }
        btnDefaults.setEnabled(!flagLockedNew);
        btnDefaults.setToolTipText(flagLockedNew ? STR_WHY_LOCKED : null);
        btnUndo.setEnabled(!flagLockedNew && settingsUndo != null);
    }


    /**
     * @return whether the rows a running stack is built on are locked
     */
    public boolean isLocked() {
        return flagLocked;
    }


    /**
     * WHICH FIXTURE THIS WINDOW CAN BUILD, and the other's rows go away.
     *
     * @param flagLocalNet whether this window drives LocalNetND
     */
    public void setTopologyLocalNet(boolean flagLocalNet) {
        pnlAviation.setVisible(!flagLocalNet);
        pnlPharma.setVisible(flagLocalNet);
    }


    /**
     * Re-reads what is on disk.
     *
     * A `Never` ANSWERED IN A DIALOG IS A SETTING THIS TAB SHOWS, and nothing
     * told it - measured at his console, 2026-09-22: the log said the offer was
     * off and the checkbox was still ticked.
     */
    public void refresh() {
        show(RaposzaSettings.current());
    }


    /**
     * @param runNew what the `Reset founding` button does, or null to leave
     *        it disabled
     */
    public void useFoundingAction(Runnable runNew) {
        this.runFounding = runNew;
        btnFoundingReset.setEnabled(runNew != null);
    }


    /**
     * WHAT THE LABEL IS FOR. The founding snapshot is displayed nowhere else
     * - operator instruction, 2026-09-22 - so without this line there is no
     * way to tell a stack that restores from one apart from a stack that
     * founds every time, and `Reset founding` would be a button whose effect
     * is invisible until the next start.
     *
     * @param flagExists whether one is on disk for what is selected
     * @param strWhat the key it belongs to, or null when nothing is selected
     */
    public void setFoundingState(boolean flagExists, String strWhat) {
        if (strWhat == null) {
            lblFounding.setText("nothing is selected");
            btnFoundingReset.setEnabled(false);
            return;
        }
        lblFounding.setText(flagExists
                ? "taken, for " + strWhat + " - every start restores it"
                : "none for " + strWhat + " - the next start founds and takes one");
        btnFoundingReset.setEnabled(flagExists && runFounding != null);
    }


    /**
     * @param settings what to put in the fields; never null
     */
    public void show(RaposzaSettings settings) {
        if (settings == null)
            throw new IllegalArgumentException("settings are required");
        flagLoading = true;
        try {
            mapField.get(RaposzaSettings.STR_KEY_DIR_HOME)
                    .setText(settings.dirHome().toString());
            mapField.get(RaposzaSettings.STR_KEY_PORT_MINT)
                    .setText(Integer.toString(settings.nPortMint()));
            mapField.get(RaposzaSettings.STR_KEY_PORT_DISCOVERY)
                    .setText(Integer.toString(settings.nPortDiscovery()));
            mapField.get(RaposzaSettings.STR_KEY_PORT_RAWAR)
                    .setText(Integer.toString(settings.nPortRawar()));
            strLine = settings.strLine();
            strLauncher = settings.strLauncher();
            mapField.get(RaposzaSettings.STR_KEY_PORT_FIRST)
                    .setText(Integer.toString(settings.nPortFirst()));
            mapField.get(RaposzaSettings.STR_KEY_PORT_POSTGRES)
                    .setText(Integer.toString(settings.nPortPostgres()));
            mapField.get(RaposzaSettings.STR_KEY_PORT_UI_FIRST)
                    .setText(Integer.toString(settings.nPortUiFirst()));
            mapField.get(RaposzaSettings.STR_KEY_SECONDS_READY)
                    .setText(Integer.toString(settings.nSecondsReady()));
            chkOfferAviation.setSelected(settings.flagOfferAviation());
            chkOfferPharma.setSelected(settings.flagOfferPharma());
            mapField.get(RaposzaSettings.STR_KEY_DIR_DAML).setText(strOfDir(settings.dirDaml()));
            mapField.get(RaposzaSettings.STR_KEY_DIR_DPM).setText(strOfDir(settings.dirDpm()));
            mapField.get(RaposzaSettings.STR_KEY_DIR_SPLICE)
                    .setText(strOfDir(settings.dirSplice()));
        }
        finally {
            flagLoading = false;
        }
        lblFile.setText(RaposzaSettings.fileStore().toString());
    }


    /**
     * Reads the fields.
     *
     * @return what the form says
     * @throws IllegalArgumentException when a field cannot be read, naming it
     */
    public RaposzaSettings settings() {
        // THE PROVIDER URL IS NOT A FIELD ON THIS TAB ANY MORE - operator
        // instruction of 2026-09-21 - so it is carried through from what is in
        // force. Reading a blank field instead would clear the provider the
        // OIDC tab is pointed at every time a port here is saved.
        return new RaposzaSettings(
                dirOf(RaposzaSettings.STR_KEY_DIR_HOME, "Raposza directory"),
                nOf(RaposzaSettings.STR_KEY_PORT_MINT, "JWT mint port"),
                nOf(RaposzaSettings.STR_KEY_PORT_DISCOVERY, "Discovery port"),
                strLine,
                strLauncher,
                nOf(RaposzaSettings.STR_KEY_PORT_FIRST, "First stack port default"),
                nOf(RaposzaSettings.STR_KEY_PORT_POSTGRES, "PostgreSQL port default"),
                nOf(RaposzaSettings.STR_KEY_SECONDS_READY, "Ready timeout default"),
                chkOfferAviation.isSelected(),
                chkOfferPharma.isSelected(),
                RaposzaSettings.current().strUrlOidc(),
                nOf(RaposzaSettings.STR_KEY_PORT_UI_FIRST, "First web UI port"),
                dirOptionalOf(RaposzaSettings.STR_KEY_DIR_DAML, "DAML Assistant directory"),
                dirOptionalOf(RaposzaSettings.STR_KEY_DIR_DPM, "DPM directory"),
                dirOptionalOf(RaposzaSettings.STR_KEY_DIR_SPLICE, "Splice directory"),
                nOf(RaposzaSettings.STR_KEY_PORT_RAWAR, "RAWAR port"));
    }


    /**
     * Writes what the fields say, IF they say something readable.
     *
     * A field mid-edit is not an error and gets no dialog: it is not written,
     * and the next keystroke that makes it valid is. The only thing reported is
     * a write that failed on the file system, which is the one case the
     * operator can do something about.
     */
    void saveIfValid() {
        RaposzaSettings settingsNew;
        try {
            settingsNew = settings();
        }
        catch (IllegalArgumentException ex) {
            return;
        }
        if (settingsNew.equals(RaposzaSettings.current()))
            return;
        try {
            RaposzaSettings.store(settingsNew);
        }
        catch (IOException ex) {
            notice("Settings NOT saved - " + ex.getMessage());
            return;
        }
        if (runSaved != null)
            runSaved.run();
        notice("Settings saved");
    }


    private void defaultsRequested() {
        settingsUndo = RaposzaSettings.current();
        btnUndo.setEnabled(true);
        show(RaposzaSettings.ofDefaults());
        saveIfValid();
    }


    private void undoRequested() {
        RaposzaSettings settingsBack = settingsUndo;
        if (settingsBack == null)
            return;
        settingsUndo = null;
        btnUndo.setEnabled(false);
        show(settingsBack);
        saveIfValid();
    }


    private JPanel pnlDirectory() {
        JPanel pnl = new JPanel(new GridBagLayout());
        pnl.setOpaque(false);
        addPathRow(pnl, 0, RaposzaSettings.STR_KEY_DIR_HOME, "Raposza directory");
        return pnl;
    }


    private JPanel pnlValues() {
        JPanel pnl = new JPanel(new GridBagLayout());
        pnl.setOpaque(false);
        int nRow = 0;
        nRow = addTextRow(pnl, nRow, RaposzaSettings.STR_KEY_PORT_DISCOVERY,
                "Discovery port");
        // BESIDE DISCOVERY - his instruction, 2026-10-02. Both are the
        // window's own servers rather than a stack's, and both stay up while
        // stacks come and go.
        nRow = addTextRow(pnl, nRow, RaposzaSettings.STR_KEY_PORT_RAWAR, "RAWAR port");
        nRow = addTextRow(pnl, nRow, RaposzaSettings.STR_KEY_PORT_MINT, "JWT mint port");
        nRow = addTextRow(pnl, nRow, RaposzaSettings.STR_KEY_PORT_POSTGRES,
                "PostgreSQL port");
        nRow = addTextRow(pnl, nRow, RaposzaSettings.STR_KEY_PORT_FIRST, "First stack port");
        nRow = addTextRow(pnl, nRow, RaposzaSettings.STR_KEY_PORT_UI_FIRST, "First web UI port");
        addTextRow(pnl, nRow, RaposzaSettings.STR_KEY_SECONDS_READY, "Ready timeout s");
        return pnl;
    }


    private JPanel pnlFixture() {
        JPanel pnl = new JPanel(new GridBagLayout());
        pnl.setOpaque(false);
        pnlAviation.setOpaque(false);
        pnlPharma.setOpaque(false);
        fillFixture(pnlAviation, "Aviation test data", RaposzaSettings.STR_KEY_OFFER_AVIATION,
                chkOfferAviation, btnAviationNow, btnAviationDoc);
        fillFixture(pnlPharma, "Pharma test data", RaposzaSettings.STR_KEY_OFFER_PHARMA,
                chkOfferPharma, btnPharmaNow, btnPharmaDoc);
        // BOTH IN THE SAME CELL, one of them hidden. A row that moved up when
        // the other went away would make the card change height with the
        // topology, and the two are the same three rows either way.
        pnl.add(pnlAviation, gbc(0, 0, 1.0, GridBagConstraints.HORIZONTAL));
        pnl.add(pnlPharma, gbc(0, 1, 1.0, GridBagConstraints.HORIZONTAL));
        return pnl;
    }


    /**
     * One fixture's three rows: the question, `Create`, and the narrative.
     *
     * @param pnl where they go
     * @param strLabel what the fixture is called
     * @param strKey the settings key, for the help
     * @param chkOffer the question
     * @param btnNow the build button
     * @param btnDoc the narrative button
     */
    private void fillFixture(JPanel pnl, String strLabel, String strKey, JCheckBox chkOffer,
            JButton btnNow, JButton btnDoc) {
        // THE SAME TWO COLUMNS AS EVERY OTHER ROW: the label carries the name
        // and the help, the control carries the value. FieldHelp installs on a
        // label, and a checkbox that explained itself would be the only row on
        // the tab that did.
        JLabel lblRow = new JLabel(strLabel);
        FieldHelp.install(lblRow, strKey);
        pnl.add(lblRow, gbc(0, 0, 0.0, GridBagConstraints.NONE));
        pnl.add(chkOffer, gbc(1, 0, 1.0, GridBagConstraints.HORIZONTAL));

        JPanel pnlHold = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pnlHold.setOpaque(false);
        // THE BUTTONS START WHERE THE LABEL STARTS. They belong to the row
        // rather than to the checkbox beside it, and indenting them into the
        // value column read as a second answer to the same question.
        pnlHold.add(btnNow);
        GridBagConstraints gbcHold = gbc(0, 1, 1.0, GridBagConstraints.HORIZONTAL);
        gbcHold.gridwidth = 2;
        pnl.add(pnlHold, gbcHold);

        JPanel pnlDoc = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pnlDoc.setOpaque(false);
        pnlDoc.add(btnDoc);
        GridBagConstraints gbcDoc = gbc(0, 2, 1.0, GridBagConstraints.HORIZONTAL);
        gbcDoc.gridwidth = 2;
        pnl.add(pnlDoc, gbcDoc);
    }


    /**
     * THREE INSTALLATIONS THIS APPLICATION DOES NOT OWN - todo.md A-45. Each
     * points at one that already exists, a developer's own or a corporate
     * install directory; blank is the default. The installers do not follow
     * them.
     */
    private JPanel pnlInstallations() {
        JPanel pnl = new JPanel(new GridBagLayout());
        pnl.setOpaque(false);
        int nRow = 0;
        nRow = addPathRow(pnl, nRow, RaposzaSettings.STR_KEY_DIR_DAML, "DAML Assistant directory");
        nRow = addPathRow(pnl, nRow, RaposzaSettings.STR_KEY_DIR_DPM, "DPM directory");
        addPathRow(pnl, nRow, RaposzaSettings.STR_KEY_DIR_SPLICE, "Splice directory");
        return pnl;
    }


    private JPanel pnlFounding() {
        JPanel pnl = new JPanel(new GridBagLayout());
        pnl.setOpaque(false);
        // THE SAME TWO COLUMNS AS EVERY OTHER ROW - see pnlFixture. No
        // FieldHelp: this is not a settings key, it is a state and an action.
        pnl.add(new JLabel("Founding snapshot"),
                gbc(0, 0, 0.0, GridBagConstraints.NONE));
        lblFounding.setForeground(GuiTheme.colMuted());
        pnl.add(lblFounding, gbc(1, 0, 1.0, GridBagConstraints.HORIZONTAL));

        JPanel pnlHold = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pnlHold.setOpaque(false);
        pnlHold.add(btnFoundingReset);
        GridBagConstraints gbcHold = gbc(0, 1, 1.0, GridBagConstraints.HORIZONTAL);
        gbcHold.gridwidth = 2;
        pnl.add(pnlHold, gbcHold);
        return pnl;
    }


    private int addPathRow(JPanel pnl, int nRow, String strKey, String strLabel) {
        JTextField fld = new JTextField(N_COLS_PATH);
        mapField.put(strKey, fld);
        JButton btnBrowse = new JButton("...");
        mapBrowse.put(strKey, btnBrowse);
        btnBrowse.addActionListener(evt -> browse(fld, strLabel));
        // THE SETTINGS KEY IS THE HELP KEY. See FieldHelp.
        JLabel lblRow = new JLabel(strLabel);
        FieldHelp.install(lblRow, strKey);
        pnl.add(lblRow, gbc(0, nRow, 0.0, GridBagConstraints.NONE));
        pnl.add(fld, gbc(1, nRow, 1.0, GridBagConstraints.HORIZONTAL));
        pnl.add(btnBrowse, gbc(2, nRow, 0.0, GridBagConstraints.NONE));
        return nRow + 1;
    }


    private int addTextRow(JPanel pnl, int nRow, String strKey, String strLabel) {
        JTextField fld = new JTextField(N_COLS_SHORT);
        mapField.put(strKey, fld);
        JLabel lblRow = new JLabel(strLabel);
        FieldHelp.install(lblRow, strKey);
        pnl.add(lblRow, gbc(0, nRow, 0.0, GridBagConstraints.NONE));
        JPanel pnlHold = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pnlHold.setOpaque(false);
        pnlHold.add(fld);
        pnl.add(pnlHold, gbc(1, nRow, 1.0, GridBagConstraints.HORIZONTAL));
        return nRow + 1;
    }


    private static void addCard(JPanel pnlStack, int nRow, JPanel card) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = nRow;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, GuiTheme.scale(GuiTheme.N_GAP), 0);
        pnlStack.add(card, gbc);
    }


    private void browse(JTextField fld, String strTitle) {
        JFileChooser chooser = new JFileChooser();
        // Hidden directories shown - `~/.raposza` and `~/.splice` are dotted.
        chooser.setFileHidingEnabled(false);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(strTitle);
        String strNow = fld.getText().trim();
        if (!strNow.isEmpty()) {
            try {
                chooser.setCurrentDirectory(Path.of(strNow).toFile());
            }
            catch (RuntimeException ex) {
                chooser.setCurrentDirectory(null);
            }
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            fld.setText(chooser.getSelectedFile().toPath().toAbsolutePath().normalize()
                    .toString());
    }


    private Path dirOf(String strKey, String strLabel) {
        String strVal = mapField.get(strKey).getText().trim();
        if (strVal.isEmpty())
            throw new IllegalArgumentException(strLabel + " is empty.");
        try {
            return Path.of(strVal).toAbsolutePath().normalize();
        }
        catch (RuntimeException ex) {
            throw new IllegalArgumentException(strLabel + " is not a path: " + strVal);
        }
    }


    /**
     * @param strKey the field's key
     * @param strLabel its label, for the refusal
     * @return the path, or null when the field is blank - the default
     */
    private Path dirOptionalOf(String strKey, String strLabel) {
        if (mapField.get(strKey).getText().trim().isEmpty())
            return null;
        return dirOf(strKey, strLabel);
    }


    private static String strOfDir(Path dir) {
        return dir == null ? "" : dir.toString();
    }


    private int nOf(String strKey, String strLabel) {
        String strVal = mapField.get(strKey).getText().trim();
        try {
            return Integer.parseInt(strVal);
        }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException(strLabel + " is not a number: " + strVal);
        }
    }


    private void notice(String strText) {
        if (sayNotice != null)
            sayNotice.accept(strText);
    }


    private static GridBagConstraints gbc(int nCol, int nRow, double dWeight, int nFill) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = nCol;
        gbc.gridy = nRow;
        gbc.weightx = dWeight;
        gbc.fill = nFill;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(GuiTheme.scale(3), GuiTheme.scale(3), GuiTheme.scale(3),
                GuiTheme.scale(3));
        return gbc;
    }


    /**
     * @return the field for a key, for tests
     */
    /**
     * @return the build button, for tests
     */
    JButton buttonAviationDoc() {
        return btnAviationDoc;
    }


    JButton buttonAviationNow() {
        return btnAviationNow;
    }


    /**
     * @return the fixture offer box, for tests
     */
    JCheckBox checkOfferAviation() {
        return chkOfferAviation;
    }


    JTextField fieldOf(String strKey) {
        return mapField.get(strKey);
    }


    /**
     * Stops a save the settle timer still holds, for tests. A timer that
     * fires after a test has put the settings file back writes the test's
     * fields into the operator's own file - measured at his console,
     * 2026-09-27.
     */
    void cancelPendingSave() {
        timerSettle.stop();
    }


    /**
     * Restarts the settle timer on any edit the operator made.
     *
     * A programmatic fill is not an edit - {@link #show} raises a flag around
     * itself - or Restore defaults would write twice and Undo would arm itself
     * against its own output.
     */
    private final class EditListener implements DocumentListener {

        @Override
        public void insertUpdate(DocumentEvent evt) {
            edited();
        }


        @Override
        public void removeUpdate(DocumentEvent evt) {
            edited();
        }


        @Override
        public void changedUpdate(DocumentEvent evt) {
            edited();
        }


        private void edited() {
            if (!flagLoading)
                timerSettle.restart();
        }
    }
}
