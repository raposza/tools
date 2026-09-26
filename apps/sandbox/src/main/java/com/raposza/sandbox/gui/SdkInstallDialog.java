// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.SdkChannel;
import com.raposza.canton.install.SdkOffer;
import com.raposza.canton.install.VersionId;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

/**
 * Every Daml SDK either toolchain publishes, with what is on this machine
 * marked, and one button per version that is not.
 *
 * <h2>Every published version is listed, installed or not</h2>
 *
 * The question a developer arrives with is "which of these have I got", and a
 * version that vanished from the list once it was installed would make the list
 * answer a different question every time it is opened. So the whole catalogue
 * is shown, NEWEST FIRST - the proposal of 2026-09-25, his instruction to
 * implement it - and the status column carries the answer.
 *
 * <h2>The catalogue is read when this OPENS, not before</h2>
 *
 * One side of it comes from a table and the other from running a toolchain, so
 * the reading is a subprocess and it happens on a thread of its own. The dialog
 * is on the screen while it runs, saying so; nothing about opening this waits
 * on a program.
 *
 * <h2>The progress is shown HERE rather than in a second dialog</h2>
 *
 * A fetch is hundreds of megabytes and the only thing to say about it while it
 * runs is how far it has got. A second window to say that would be one more
 * thing on the screen owned by the thing underneath it, so the footer of this
 * dialog carries the figure and the dialog closes itself when the install is
 * done.
 *
 * <h2>A dpm row says which Canton it brings, when that is known</h2>
 *
 * A dpm version is the SDK BUNDLE, not the Canton it ships - bundle 3.5.11
 * brought Canton 3.5.18. The Canton is read from the bundle's manifest, which
 * exists only once the bundle is installed; a row without one says the Canton
 * is shown after install rather than guessing - `todo.md` T-3, D-834.
 *
 * <h2>What an install IS belongs to the window</h2>
 *
 * This raises no downloader, runs no toolchain and knows no URL. It is handed a
 * {@link Loader} and an {@link Installer}, which is what lets the flow - one at
 * a time, buttons down while it runs, closed on success, still up on a failure
 * - be asserted with nothing fetched.
 *
 * Author Claude/bentzn
 */
public final class SdkInstallDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Reading both catalogues, however that is done. */
    public interface Loader {

        /**
         * Blocks while the toolchains are asked.
         *
         * @return every version that can be offered, oldest first
         * @throws IOException when a catalogue cannot be read
         */
        List<SdkOffer> lstOffer() throws IOException;
    }

    /** Acquiring one SDK, however that is done. */
    public interface Installer {

        /**
         * Blocks until the SDK is installed or the attempt fails.
         *
         * @param offer which SDK, and which toolchain installs it; never null
         * @param lineProgress told what is happening, repeatedly
         * @throws IOException when any step fails
         */
        void install(SdkOffer offer, Consumer<String> lineProgress) throws IOException;
    }

    public static final String STR_TITLE = "Install Daml SDK";

    public static final String STR_INSTALLED = "installed";

    public static final String STR_ABSENT = "not installed";

    /** The column heads - his review of 2026-09-23: a table, not a list. */
    static final String[] ARR_HEAD = {"SDK", "Canton", "Tool", "Status"};

    /** A dpm bundle not on this machine has no manifest to read it from. */
    public static final String STR_CANTON_LATER = "after install";

    /**
     * A Canton version longer than this is cut and ends in "..." - his
     * instruction of 2026-09-23, so no row is wider than the dialog. The
     * tooltip carries it whole.
     */
    static final int N_CHARS_CANTON = 14;

    static final String STR_CUT = "...";

    public static final String STR_BUTTON = "Install";

    public static final String STR_CLOSE = "Close";

    public static final String STR_READING = "Reading the catalogues...";

    /** What a machine that can reach neither catalogue is told. */
    public static final String STR_NONE = "No Daml SDK is published for this platform.";

    public static final String STR_FAILED = "Install failed: ";

    public static final String STR_UNREAD = "Catalogue failed: ";

    private static final int N_WIDTH = 560;

    private static final int N_HEIGHT = 420;

    private static final int N_GAP_ROW = 2;

    private static final int N_GAP_COL = 10;

    /** One button-height per wheel notch rather than one text line. */
    private static final int N_SCROLL_UNIT = 34;

    private static final int N_SCROLL_BLOCK = 200;

    private final transient Loader loader;

    private final transient Installer installer;

    /** Which Canton an offer brings, or null when it is not known. */
    private final transient Function<SdkOffer, String> fnCanton;

    /** The Canton each offer brings, uncut, for the tooltip. */
    private final transient Map<SdkOffer, String> mapCantonFull = new LinkedHashMap<>();

    /**
     * The versions that can still be installed and the button that starts
     * each. An installed version is not in here, which is what
     * {@link #lstOffered()} means.
     */
    private final transient Map<VersionId, JButton> mapButton = new LinkedHashMap<>();

    private final JPanel pnlRows = new JPanel(new GridBagLayout());

    private final JLabel lblStatus = new JLabel(STR_READING);

    private final JButton btnClose = new JButton(STR_CLOSE);

    private transient VersionId versionInstalled;


    /**
     * @param owner the window this belongs to, or null
     * @param loaderNew what reads the catalogues; never null
     * @param installerNew what an install actually does; never null
     */
    public SdkInstallDialog(Window owner, Loader loaderNew, Installer installerNew) {
        this(owner, loaderNew, installerNew, offer -> null);
    }


    /**
     * @param owner the window this belongs to, or null
     * @param loaderNew what reads the catalogues; never null
     * @param installerNew what an install actually does; never null
     * @param fnCantonNew which Canton an offer brings, or null when unknown;
     *        asked on the catalogue's thread, never null
     */
    public SdkInstallDialog(Window owner, Loader loaderNew, Installer installerNew,
            Function<SdkOffer, String> fnCantonNew) {
        super(owner, STR_TITLE, ModalityType.APPLICATION_MODAL);
        if (loaderNew == null || installerNew == null || fnCantonNew == null)
            throw new IllegalArgumentException("a loader, an installer and a canton reader are required");

        this.loader = loaderNew;
        this.installer = installerNew;
        this.fnCanton = fnCantonNew;

        pnlRows.setOpaque(false);
        // NORTH, so the rows start at the top of the viewport. A panel dropped
        // straight into a scroll pane is centred in it, and a short list then
        // floats in the middle of an empty box.
        JPanel pnlHold = new JPanel(new BorderLayout());
        pnlHold.setOpaque(false);
        pnlHold.add(pnlRows, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(pnlHold);
        // NO SIDEWAYS SCROLLING - his instruction of 2026-09-23. The long field
        // is cut instead, see N_CHARS_CANTON.
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(GuiTheme.scale(N_SCROLL_UNIT));
        scroll.getVerticalScrollBar().setBlockIncrement(GuiTheme.scale(N_SCROLL_BLOCK));

        btnClose.addActionListener(evt -> dispose());
        JPanel pnlFooter = new JPanel(new BorderLayout(GuiTheme.scale(GuiTheme.N_GAP), 0));
        pnlFooter.setOpaque(false);
        pnlFooter.add(lblStatus, BorderLayout.CENTER);
        pnlFooter.add(btnClose, BorderLayout.EAST);

        JPanel pnlPage = new JPanel(new BorderLayout(0, GuiTheme.scale(GuiTheme.N_GAP)));
        pnlPage.setBorder(GuiTheme.borderScaled(GuiTheme.N_PAD_CARD, GuiTheme.N_PAD_CARD,
                GuiTheme.N_PAD_CARD, GuiTheme.N_PAD_CARD));
        pnlPage.add(scroll, BorderLayout.CENTER);
        pnlPage.add(pnlFooter, BorderLayout.SOUTH);

        setContentPane(pnlPage);
        setSize(GuiTheme.dimScaled(N_WIDTH, N_HEIGHT));
        setLocationRelativeTo(owner);
        load();
    }


    /**
     * @return what was installed while this was open, or null when nothing was
     */
    public VersionId versionInstalled() {
        return versionInstalled;
    }


    /**
     * Reads the catalogues on a thread of its own.
     *
     * STARTED FROM THE CONSTRUCTOR and answered on the event thread. The dialog
     * is modal, so the caller is blocked in `setVisible` while this runs - the
     * event thread is not, and it is the one that fills the rows.
     */
    private void load() {
        Thread threadLoad = new Thread(() -> {
            try {
                List<SdkOffer> lstOffer = loader.lstOffer();
                // HERE, not on the event thread - it reads manifests off disk.
                Map<SdkOffer, String> mapCanton = new LinkedHashMap<>();
                Map<SdkOffer, String> mapFull = new LinkedHashMap<>();
                for (SdkOffer offer : lstOffer) {
                    String strFull = fnCanton.apply(offer);
                    mapCanton.put(offer, strCanton(offer, strFull));
                    if (strFull != null)
                        mapFull.put(offer, strFull.trim());
                }
                SwingUtilities.invokeLater(() -> {
                    mapCantonFull.putAll(mapFull);
                    loaded(lstOffer, mapCanton, null);
                });
            }
            catch (IOException | RuntimeException ex) {
                SwingUtilities.invokeLater(() -> loaded(null, null, ex.getMessage()));
            }
        }, "raposza-sdk-catalogue");
        threadLoad.setDaemon(true);
        threadLoad.start();
    }


    /**
     * @param lstOffer what the toolchains published, or null when the read
     *        failed
     * @param mapCanton what each row says about its Canton, or null when the
     *        read failed
     * @param strError why it failed, or null when it did not
     */
    private void loaded(List<SdkOffer> lstOffer, Map<SdkOffer, String> mapCanton,
            String strError) {
        if (strError != null) {
            lblStatus.setForeground(GuiTheme.COL_BAD);
            lblStatus.setText(STR_UNREAD + strError);
            return;
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(GuiTheme.scale(N_GAP_ROW), GuiTheme.scale(N_GAP_COL),
                GuiTheme.scale(N_GAP_ROW), GuiTheme.scale(N_GAP_COL));
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        addHead(gbc);
        int nRow = 1;
        for (SdkOffer offer : lstNewestFirst(lstOffer)) {
            addRow(gbc, nRow, offer, mapCanton.get(offer));
            nRow++;
        }

        lblStatus.setForeground(GuiTheme.colMuted());
        lblStatus.setText(lstOffer.isEmpty() ? STR_NONE : " ");
        pnlRows.revalidate();
        pnlRows.repaint();
    }


    /**
     * The loader hands the catalogue over oldest first; the dialog shows it
     * newest first. Stable, so two channels publishing the same version keep
     * the loader's order between them.
     *
     * @param lstOffer the catalogue; never null
     * @return a copy, newest version first
     */
    static List<SdkOffer> lstNewestFirst(List<SdkOffer> lstOffer) {
        List<SdkOffer> lstOut = new ArrayList<>(lstOffer);
        lstOut.sort((offerA, offerB) -> offerB.version().compareTo(offerA.version()));
        return lstOut;
    }


    /**
     * Row 0: the column heads, and the glue that takes the spare width so the
     * button sits beside its status rather than against the far edge.
     *
     * @param gbc the shared constraints, mutated as it goes
     */
    private void addHead(GridBagConstraints gbc) {
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        for (int cntCol = 0; cntCol < ARR_HEAD.length; cntCol++) {
            gbc.gridx = cntCol;
            JLabel lblHead = new JLabel(ARR_HEAD[cntCol]);
            lblHead.setFont(lblHead.getFont().deriveFont(Font.BOLD));
            pnlRows.add(lblHead, gbc);
        }
        gbc.gridx = ARR_HEAD.length + 1;
        gbc.weightx = 1.0;
        pnlRows.add(Box.createHorizontalGlue(), gbc);
        gbc.weightx = 0.0;
    }


    /**
     * @param gbc the shared constraints, mutated as it goes
     * @param nRow which row
     * @param offer what the row is about
     * @param strCantonShown what the row says about its Canton, maybe empty
     */
    private void addRow(GridBagConstraints gbc, int nRow, SdkOffer offer, String strCantonShown) {
        gbc.gridy = nRow;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        pnlRows.add(new JLabel(offer.version().toString()), gbc);

        gbc.gridx = 1;
        JLabel lblCanton = new JLabel(strCantonShown);
        lblCanton.setForeground(GuiTheme.colMuted());
        if (strCantonShown.endsWith(STR_CUT))
            lblCanton.setToolTipText(fnCantonFull(offer));
        pnlRows.add(lblCanton, gbc);

        gbc.gridx = 2;
        JLabel lblChannel = new JLabel(offer.channel().strLabel());
        lblChannel.setForeground(GuiTheme.colMuted());
        pnlRows.add(lblChannel, gbc);

        gbc.gridx = 3;
        JLabel lblState = new JLabel(offer.flagInstalled() ? STR_INSTALLED : STR_ABSENT);
        lblState.setForeground(offer.flagInstalled() ? GuiTheme.COL_OK : GuiTheme.colMuted());
        pnlRows.add(lblState, gbc);

        gbc.gridx = 4;
        // AN INSTALLED ROW KEEPS ITS HEIGHT AND LOSES ITS BUTTON. Without the
        // spacer the rows with a button stand taller than the rows without one,
        // and the list reads as if it were grouped.
        if (offer.flagInstalled()) {
            pnlRows.add(Box.createRigidArea(new Dimension(0, nHeightButton())), gbc);
            return;
        }

        JButton btnInstall = new JButton(STR_BUTTON);
        btnInstall.addActionListener(evt -> begin(offer));
        mapButton.put(offer.version(), btnInstall);
        pnlRows.add(btnInstall, gbc);
    }


    /**
     * @param offer the row
     * @param strCanton the Canton its manifest names, or null when there is none
     * @return what the row says about its Canton - nothing on an assistant row,
     *         whose version is not a bundle
     */
    static String strCanton(SdkOffer offer, String strCanton) {
        if (offer.channel() != SdkChannel.DPM)
            return "";
        if (strCanton == null || strCanton.isBlank())
            return STR_CANTON_LATER;
        return strShort(strCanton.trim());
    }


    /**
     * @param strVersion a Canton version
     * @return it, or its head and "..." when it is longer than
     *         {@link #N_CHARS_CANTON}
     */
    static String strShort(String strVersion) {
        if (strVersion.length() <= N_CHARS_CANTON)
            return strVersion;
        return strVersion.substring(0, N_CHARS_CANTON - STR_CUT.length()) + STR_CUT;
    }


    /**
     * @param offer the row
     * @return the whole Canton version, for the tooltip; empty when unknown
     */
    private String fnCantonFull(SdkOffer offer) {
        String strCanton = mapCantonFull.get(offer);
        return strCanton == null ? "" : strCanton;
    }


    /**
     * @return how tall a button is on this look and feel, so a row without one
     *         is the same height as a row with one
     */
    private static int nHeightButton() {
        return new JButton(STR_BUTTON).getPreferredSize().height;
    }


    /**
     * Starts one install, on a thread of its own.
     *
     * NOT ON THE EVENT THREAD. This is hundreds of megabytes over someone
     * else's server; running it where the dialog is painted would freeze the
     * figure that says how far it has got.
     *
     * @param offer what to install
     */
    private void begin(SdkOffer offer) {
        setBusy(true);
        lblStatus.setForeground(GuiTheme.colMuted());
        lblStatus.setText(offer.version() + "  starting");

        Thread threadInstall = new Thread(() -> {
            try {
                installer.install(offer, this::say);
                SwingUtilities.invokeLater(() -> done(offer.version(), null));
            }
            catch (IOException | RuntimeException ex) {
                SwingUtilities.invokeLater(() -> done(null, ex.getMessage()));
            }
        }, "raposza-sdk-install");
        threadInstall.setDaemon(true);
        threadInstall.start();
    }


    /**
     * @param version what was installed, or null when it failed
     * @param strError why it failed, or null when it did not
     */
    private void done(VersionId version, String strError) {
        if (strError != null) {
            lblStatus.setForeground(GuiTheme.COL_BAD);
            lblStatus.setText(STR_FAILED + strError);
            // THE DIALOG STAYS UP. A failure is the one moment the list is
            // worth reading again, and the version that failed is still
            // offered.
            setBusy(false);
            return;
        }

        versionInstalled = version;
        dispose();
    }


    /**
     * @param strLine what the install is saying, from its own thread
     */
    private void say(String strLine) {
        SwingUtilities.invokeLater(() -> lblStatus.setText(strLine));
    }


    /**
     * ONE AT A TIME, including the close button: two vendor installers writing
     * the same root at once is not a case anybody has measured, and a dialog
     * closed halfway through would take the only report of what is happening.
     *
     * @param flagBusy whether an install is running
     */
    private void setBusy(boolean flagBusy) {
        for (JButton btn : mapButton.values()) {
            btn.setEnabled(!flagBusy);
        }
        btnClose.setEnabled(!flagBusy);
    }


    /**
     * @return the versions this dialog is offering to install, in the order
     *         they are shown
     */
    List<VersionId> lstOffered() {
        return new ArrayList<>(mapButton.keySet());
    }


    /**
     * @param version any version
     * @return the button that installs it, or null when it is already
     *         installed or the catalogue has not arrived
     */
    JButton buttonFor(VersionId version) {
        return mapButton.get(version);
    }


    /**
     * @return what the footer says
     */
    String strStatus() {
        return lblStatus.getText();
    }

}
