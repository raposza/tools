// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Every published Splice version, what is under `~/.splice` marked, and two
 * ways to add one - todo.md A-38.
 *
 * <h2>Two ways in - the operator's instruction of 2026-09-23</h2>
 *
 * A row's `Install` downloads the vendor's archive straight into `~/.splice`.
 * `Install from file...` takes an archive fetched by hand and installs that.
 * LocalNetND has no vendor installer the way the Sandbox has DAML Assistant and
 * DPM, so both are this window's own.
 *
 * <h2>THE SIZE IS SHOWN BEFORE ANYONE CLICKS - `splice_inventory.md` 2.2</h2>
 *
 * A version is around 800 MB down and 1.5 GB on disk. Each row's size is asked
 * of the vendor's server, newest first, on a thread of its own, and a row's
 * button is enabled only once its size is on the screen. A row whose size
 * cannot be read keeps its button down: the server that would not say how big
 * it is is the one about to be asked for all of it.
 *
 * <h2>What an install IS belongs to the window</h2>
 *
 * As {@link SdkInstallDialog}: this fetches nothing and unpacks nothing itself.
 * It is handed what does, which is what lets the flow be asserted offline.
 *
 * Author Claude/bentzn
 */
public final class SpliceInstallDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** The published versions, newest first. */
    @FunctionalInterface
    public interface Loader {

        List<String> lstPublished() throws IOException;
    }

    /** What one version's archive weighs, in bytes, or -1. */
    @FunctionalInterface
    public interface Sizer {

        long nBytes(String strVersion) throws IOException;
    }

    /** Downloading and installing one published version. */
    @FunctionalInterface
    public interface Downloader {

        void download(String strVersion, Consumer<String> lineProgress) throws IOException;
    }

    /** Installing an archive already on this machine. */
    @FunctionalInterface
    public interface FileInstaller {

        /**
         * @return the version it installed
         */
        String install(Path fileArchive, Consumer<String> lineProgress) throws IOException;
    }

    public static final String STR_TITLE = "Install Splice";

    public static final String STR_INSTALLED = "installed";

    public static final String STR_ABSENT = "not installed";

    public static final String STR_SIZING = "size...";

    public static final String STR_NO_SIZE = "size not available";

    public static final String STR_BUTTON = "Install";

    public static final String STR_FILE = "Install from file...";

    public static final String STR_CLOSE = "Close";

    public static final String STR_READING = "Reading the Splice releases...";

    public static final String STR_FAILED = "Install failed: ";

    public static final String STR_UNREAD = "Releases failed: ";

    private static final int N_WIDTH = 520;

    private static final int N_HEIGHT = 460;

    private static final int N_GAP_ROW = 2;

    private static final int N_GAP_COL = 10;

    private static final int N_SCROLL_UNIT = 34;

    private static final int N_SCROLL_BLOCK = 200;

    private final transient Loader loader;

    private final transient Sizer sizer;

    private final transient Downloader downloader;

    private final transient FileInstaller fileInstaller;

    private final transient Set<String> setInstalled;

    /** The versions that can be installed, and each one's button. */
    private final transient Map<String, JButton> mapButton = new LinkedHashMap<>();

    private final transient Map<String, JLabel> mapSize = new LinkedHashMap<>();

    /** Versions whose size is on the screen - only these may be enabled. */
    private final transient Set<String> setSized = new HashSet<>();

    private final JPanel pnlRows = new JPanel(new GridBagLayout());

    private final JLabel lblStatus = new JLabel(STR_READING);

    private final JButton btnFile = new JButton(STR_FILE);

    private final JButton btnClose = new JButton(STR_CLOSE);

    private transient boolean flagBusy;

    private transient String strInstalled;


    /**
     * @param owner the window this belongs to, or null
     * @param setInstalledNow the versions already under `~/.splice`
     * @param loaderNew reads the published versions
     * @param sizerNew asks one archive's size
     * @param downloaderNew downloads and installs one version
     * @param fileInstallerNew installs an archive on this machine
     */
    public SpliceInstallDialog(Window owner, Set<String> setInstalledNow, Loader loaderNew,
            Sizer sizerNew, Downloader downloaderNew, FileInstaller fileInstallerNew) {
        super(owner, STR_TITLE, ModalityType.APPLICATION_MODAL);
        if (loaderNew == null || sizerNew == null || downloaderNew == null || fileInstallerNew == null)
            throw new IllegalArgumentException("a loader, a sizer and both installers are required");

        this.setInstalled = setInstalledNow == null ? Set.of() : Set.copyOf(setInstalledNow);
        this.loader = loaderNew;
        this.sizer = sizerNew;
        this.downloader = downloaderNew;
        this.fileInstaller = fileInstallerNew;

        pnlRows.setOpaque(false);
        JPanel pnlHold = new JPanel(new BorderLayout());
        pnlHold.setOpaque(false);
        pnlHold.add(pnlRows, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(pnlHold);
        scroll.getVerticalScrollBar().setUnitIncrement(GuiTheme.scale(N_SCROLL_UNIT));
        scroll.getVerticalScrollBar().setBlockIncrement(GuiTheme.scale(N_SCROLL_BLOCK));

        btnFile.addActionListener(evt -> chooseFile());
        btnClose.addActionListener(evt -> dispose());
        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, GuiTheme.scale(GuiTheme.N_GAP), 0));
        pnlButtons.setOpaque(false);
        pnlButtons.add(btnFile);
        pnlButtons.add(btnClose);

        JPanel pnlFooter = new JPanel(new BorderLayout(GuiTheme.scale(GuiTheme.N_GAP), 0));
        pnlFooter.setOpaque(false);
        pnlFooter.add(lblStatus, BorderLayout.CENTER);
        pnlFooter.add(pnlButtons, BorderLayout.EAST);

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
     * @return the version installed while this was open, or null
     */
    public String strInstalled() {
        return strInstalled;
    }


    private void load() {
        Thread threadLoad = new Thread(() -> {
            try {
                List<String> lstVersion = loader.lstPublished();
                SwingUtilities.invokeLater(() -> loaded(lstVersion, null));
            }
            catch (IOException | RuntimeException ex) {
                SwingUtilities.invokeLater(() -> loaded(null, ex.getMessage()));
            }
        }, "raposza-splice-releases");
        threadLoad.setDaemon(true);
        threadLoad.start();
    }


    private void loaded(List<String> lstVersion, String strError) {
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

        int nRow = 0;
        for (String strVersion : lstVersion) {
            addRow(gbc, nRow, strVersion);
            nRow++;
        }
        lblStatus.setForeground(GuiTheme.colMuted());
        lblStatus.setText(" ");
        pnlRows.revalidate();
        pnlRows.repaint();
        size(new ArrayList<>(mapButton.keySet()));
    }


    private void addRow(GridBagConstraints gbc, int nRow, String strVersion) {
        boolean flagHere = setInstalled.contains(strVersion);
        gbc.gridy = nRow;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        pnlRows.add(new JLabel(strVersion), gbc);

        gbc.gridx = 1;
        JLabel lblState = new JLabel(flagHere ? STR_INSTALLED : STR_ABSENT);
        lblState.setForeground(flagHere ? GuiTheme.COL_OK : GuiTheme.colMuted());
        pnlRows.add(lblState, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        JLabel lblSize = new JLabel(flagHere ? " " : STR_SIZING);
        lblSize.setForeground(GuiTheme.colMuted());
        pnlRows.add(lblSize, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.0;
        JButton btnInstall = new JButton(STR_BUTTON);
        if (flagHere) {
            // THE ROW KEEPS ITS HEIGHT: an invisible button, not a missing one.
            btnInstall.setVisible(false);
            pnlRows.add(btnInstall, gbc);
            return;
        }
        btnInstall.setEnabled(false);
        btnInstall.addActionListener(evt -> beginDownload(strVersion));
        mapButton.put(strVersion, btnInstall);
        mapSize.put(strVersion, lblSize);
        pnlRows.add(btnInstall, gbc);
    }


    /**
     * Asks each size in turn, newest first, and enables a row as its size lands.
     *
     * @param lstVersion the rows that can be installed, in order
     */
    private void size(List<String> lstVersion) {
        Thread threadSize = new Thread(() -> {
            for (String strVersion : lstVersion) {
                String strText;
                boolean flagOk;
                try {
                    long nBytes = sizer.nBytes(strVersion);
                    flagOk = nBytes >= 0;
                    strText = flagOk ? strSize(nBytes) : STR_NO_SIZE;
                }
                catch (IOException | RuntimeException ex) {
                    flagOk = false;
                    strText = STR_NO_SIZE;
                }
                boolean flagOkFinal = flagOk;
                String strTextFinal = strText;
                SwingUtilities.invokeLater(() -> sized(strVersion, strTextFinal, flagOkFinal));
            }
        }, "raposza-splice-sizes");
        threadSize.setDaemon(true);
        threadSize.start();
    }


    private void sized(String strVersion, String strText, boolean flagOk) {
        JLabel lblSize = mapSize.get(strVersion);
        if (lblSize != null)
            lblSize.setText(strText);
        if (!flagOk)
            return;
        setSized.add(strVersion);
        mapButton.get(strVersion).setEnabled(!flagBusy);
    }


    /**
     * @param nBytes a size in bytes
     * @return it as the rows show it
     */
    static String strSize(long nBytes) {
        return (nBytes + 512L * 1024L) / (1024L * 1024L) + " MB";
    }


    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        // Hidden directories shown - `~/.raposza` and `~/.splice` are dotted.
        chooser.setFileHidingEnabled(false);
        chooser.setDialogTitle(STR_FILE);
        chooser.setFileFilter(new FileNameExtensionFilter("Splice archive (<version>_splice-node.tar.gz)", "gz"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        beginFile(chooser.getSelectedFile().toPath());
    }


    private void beginDownload(String strVersion) {
        begin(strVersion + "  starting", () -> {
            downloader.download(strVersion, this::say);
            return strVersion;
        });
    }


    /**
     * @param fileArchive an archive on this machine
     */
    void beginFile(Path fileArchive) {
        begin(fileArchive.getFileName() + "  starting", () -> fileInstaller.install(fileArchive, this::say));
    }


    @FunctionalInterface
    private interface Work {

        String run() throws IOException;
    }


    /**
     * NOT ON THE EVENT THREAD - hundreds of megabytes, and the footer is what
     * says how far it has got.
     */
    private void begin(String strFirst, Work work) {
        setBusy(true);
        lblStatus.setForeground(GuiTheme.colMuted());
        lblStatus.setText(strFirst);
        Thread threadInstall = new Thread(() -> {
            try {
                String strVersion = work.run();
                SwingUtilities.invokeLater(() -> done(strVersion, null));
            }
            catch (IOException | RuntimeException ex) {
                SwingUtilities.invokeLater(() -> done(null, ex.getMessage()));
            }
        }, "raposza-splice-install");
        threadInstall.setDaemon(true);
        threadInstall.start();
    }


    private void done(String strVersion, String strError) {
        if (strError != null) {
            lblStatus.setForeground(GuiTheme.COL_BAD);
            lblStatus.setText(STR_FAILED + strError);
            setBusy(false);
            return;
        }
        strInstalled = strVersion;
        dispose();
    }


    private void say(String strLine) {
        SwingUtilities.invokeLater(() -> lblStatus.setText(strLine));
    }


    /**
     * ONE AT A TIME, the close button included - a dialog closed halfway would
     * take the only report of what is happening.
     */
    private void setBusy(boolean flagBusyNew) {
        flagBusy = flagBusyNew;
        for (Map.Entry<String, JButton> entry : mapButton.entrySet()) {
            entry.getValue().setEnabled(!flagBusy && setSized.contains(entry.getKey()));
        }
        btnFile.setEnabled(!flagBusy);
        btnClose.setEnabled(!flagBusy);
    }


    /**
     * @return the versions offered for install, in the order shown
     */
    List<String> lstOffered() {
        return new ArrayList<>(mapButton.keySet());
    }


    /**
     * @param strVersion a version
     * @return its button, or null when it is installed or not yet listed
     */
    JButton buttonFor(String strVersion) {
        return mapButton.get(strVersion);
    }


    /**
     * @param strVersion a version
     * @return what its size column says, or null
     */
    String strSizeShown(String strVersion) {
        JLabel lblSize = mapSize.get(strVersion);
        return lblSize == null ? null : lblSize.getText();
    }


    String strStatus() {
        return lblStatus.getText();
    }
}
