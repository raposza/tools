// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.dar.DarCatalog;
import com.raposza.canton.dar.DarInfo;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;

/**
 * Which DARs the next start uploads.
 *
 * <h2>It is a property of the NEXT START, not a button</h2>
 *
 * The same shape the snapshot list has, and for the same reason: an `Upload`
 * button beside `Start` would read as two ways to get a package onto a ledger
 * with no way to tell which one had won. The ticks are read when Start is
 * pressed, so pressing Start twice uploads the same set twice - against a wiped
 * cluster, which is what makes that a repeat rather than a conflict.
 *
 * <h2>The directory is `&lt;run&gt;/dars` and it is not selectable</h2>
 *
 * It follows the run directory, which is already per version and edition -
 * `~/.raposza/sandbox/&lt;version&gt;-&lt;edition&gt;/dars` - and it is where
 * `publish.py --install` stages the pet shop fixture. It was a text field with a
 * Browse button and is now shown and nothing else: a DAR is compiled against one
 * LF version, so a directory pointed at another version's output offers a set of
 * files that a participant answers for with an upload rejection at the far end of
 * a start. The one directory that is always right for the selected Canton is the
 * one that belongs to it.
 *
 * <h2>What the table shows, and why it is not the file name</h2>
 *
 * Name, version and the first twelve of the package id, read through
 * {@link DarCatalog#inspect} from each DAR's own manifest. Twelve builds of the
 * pet shop fixture are twelve files with ONE name, one version and twelve
 * package ids - the ledger's identity for the package is the only column that
 * tells them apart, and a table showing file names alone would make them look
 * interchangeable when they are the one thing in this application that must not
 * be mixed.
 *
 * A DAR whose manifest cannot be read is LISTED with its reason in the id
 * column rather than dropped. A file that vanishes from a list without a word
 * is a file somebody spends ten minutes looking for.
 *
 * <h2>The selection outlives a refresh, and null means all</h2>
 *
 * Ticks are held by FILE NAME rather than by row, so a refresh that reorders or
 * re-reads the directory keeps them. A name that is no longer in the directory
 * is dropped on read rather than refused: a DAR deleted between two sessions is
 * not a reason to refuse to open a window.
 *
 * A profile with no selection recorded - which is every profile written before
 * this tab existed - means EVERY DAR in the directory, so an existing setup
 * keeps doing what it did.
 *
 * <h2>Import puts a DAR of the user's own into the store - break 1</h2>
 *
 * `handover_2026-09-23_ports_dars_todo.md` section 4: nothing put a user's own
 * DAR into `&lt;run&gt;/dars`, and a browse field could not be the answer because
 * the directory is the version's. `Import...` CHECKS AT IMPORT, not at the far
 * end of a start - his choice of 2026-09-25: the DAR's own manifest must read
 * through {@link DarCatalog#inspect}; a package id the store already holds
 * under another file name is refused, because {@link DarCatalog#ofFiles}
 * refuses a start carrying one package twice; and a different file of the
 * same name is never overwritten - `DarsLivePane.strCopyToStore`'s rule. What
 * lands is ticked. Whether the participant accepts the archive's LF version is
 * NOT checked here: no rule for it is recorded, `lf_inventory.md` section 1
 * says LF version and Canton generation are different axes, and the upload at
 * Start reports a refusal.
 *
 * Author Claude/bentzn
 */
public final class DarsPane extends JPanel {

    private static final long serialVersionUID = 1L;

    /** How much of a package id a human needs to tell two builds apart. */
    public static final int N_PKG_ID_SHOWN = 12;

    private static final String STR_GLOB_DAR = "*.dar";

    private final JButton btnRefresh = new JButton("Refresh");

    private final JButton btnAll = new JButton("All");

    private final JButton btnNone = new JButton("None");

    private final JButton btnImport = new JButton("Import...");

    /** What the last import did, in one line. */
    private final JLabel lblImport = new JLabel(" ");

    /** Where the import chooser opens next, or null for the user's home. */
    private transient Path dirImportLast;

    private final JLabel lblDir = new JLabel(" ");

    private final JLabel lblCount = new JLabel(" ");

    private final transient List<Row> lstRow = new ArrayList<>();

    /** Ticked file names, which survive a refresh and a reorder. */
    private final transient Set<String> setTicked = new LinkedHashSet<>();

    /**
     * Whether a tick can be changed.
     *
     * A FIELD rather than asking the table: the model is consulted while
     * {@link JTable} is still being constructed, and a model that reached
     * through to a table which does not exist yet would fail on the one path
     * nothing here would exercise until it was in front of somebody.
     */
    private transient boolean flagEditable = true;

    private final transient DarTableModel model = new DarTableModel();

    private final JTable table = new JTable(model);

    /**
     * The run directory as the form currently holds it, so the listing follows
     * it when it is edited.
     */
    private transient Supplier<Path> supplierRun = () -> null;


    /** One DAR, as the table shows it. */
    private static final class Row {

        private final Path fileDar;

        private final String strName;

        private final String strVersion;

        private final String strPkgId;


        private Row(Path fileDarNew, String strNameNew, String strVersionNew,
                String strPkgIdNew) {
            this.fileDar = fileDarNew;
            this.strName = strNameNew;
            this.strVersion = strVersionNew;
            this.strPkgId = strPkgIdNew;
        }


        private String strFileName() {
            return fileDar.getFileName().toString();
        }
    }


    private final class DarTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private final String[] arrColumn = { "", "File", "Package", "Version", "Package id" };


        @Override
        public int getRowCount() {
            return lstRow.size();
        }


        @Override
        public int getColumnCount() {
            return arrColumn.length;
        }


        @Override
        public String getColumnName(int idxCol) {
            return arrColumn[idxCol];
        }


        @Override
        public Class<?> getColumnClass(int idxCol) {
            return idxCol == 0 ? Boolean.class : String.class;
        }


        @Override
        public boolean isCellEditable(int idxRow, int idxCol) {
            return idxCol == 0 && flagEditable;
        }


        @Override
        public Object getValueAt(int idxRow, int idxCol) {
            Row row = lstRow.get(idxRow);
            switch (idxCol) {
                case 0:
                    return Boolean.valueOf(setTicked.contains(row.strFileName()));
                case 1:
                    return row.strFileName();
                case 2:
                    return row.strName == null ? "-" : row.strName;
                case 3:
                    return row.strVersion == null ? "-" : row.strVersion;
                default:
                    return row.strPkgId;
            }
        }


        @Override
        public void setValueAt(Object objValue, int idxRow, int idxCol) {
            if (idxCol != 0)
                return;

            Row row = lstRow.get(idxRow);
            if (Boolean.TRUE.equals(objValue))
                setTicked.add(row.strFileName());
            else
                setTicked.remove(row.strFileName());
            fireTableRowsUpdated(idxRow, idxRow);
            showCount();
        }
    }


    public DarsPane() {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(GuiTheme.borderScaled(GuiTheme.N_GAP, 0, 0, 0));

        btnRefresh.addActionListener(evt -> refresh());
        btnAll.addActionListener(evt -> tickAll(true));
        btnNone.addActionListener(evt -> tickAll(false));
        btnImport.addActionListener(evt -> importChosen());

        JPanel pnlDir = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiTheme.scale(8), 0));
        pnlDir.setOpaque(false);
        JLabel lblDirCaption = new JLabel("Directory");
        FieldHelp.install(lblDirCaption, FieldHelp.KEY_DARS_DIR);
        pnlDir.add(lblDirCaption);
        pnlDir.add(lblDir);

        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiTheme.scale(8), 0));
        pnlButtons.setOpaque(false);
        pnlButtons.add(btnRefresh);
        pnlButtons.add(btnAll);
        pnlButtons.add(btnNone);
        pnlButtons.add(btnImport);
        pnlButtons.add(lblCount);
        pnlButtons.add(lblImport);

        JPanel pnlTop = new JPanel();
        pnlTop.setLayout(new BoxLayout(pnlTop, BoxLayout.Y_AXIS));
        pnlTop.setOpaque(false);
        pnlDir.setAlignmentX(LEFT_ALIGNMENT);
        pnlButtons.setAlignmentX(LEFT_ALIGNMENT);
        pnlTop.add(pnlDir);
        pnlTop.add(Box.createVerticalStrut(GuiTheme.scale(GuiTheme.N_GAP)));
        pnlTop.add(pnlButtons);

        lblCount.setForeground(GuiTheme.colMuted());
        lblImport.setForeground(GuiTheme.colMuted());
        table.setFillsViewportHeight(true);
        table.setRowHeight(GuiTheme.scale(22));
        table.getColumnModel().getColumn(0).setMaxWidth(GuiTheme.scale(32));

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(GuiTheme.colCardBorder(), 1, true));

        JPanel pnlInner = new JPanel(new BorderLayout(0, GuiTheme.scale(GuiTheme.N_GAP)));
        pnlInner.setOpaque(false);
        pnlInner.add(pnlTop, BorderLayout.NORTH);
        pnlInner.add(scroll, BorderLayout.CENTER);

        add(GuiTheme.card("DARs uploaded at start", pnlInner), BorderLayout.CENTER);
    }


    /**
     * @param supplierRunNew where the run directory can be read from; never
     *        null
     */
    public void useRunDirectory(Supplier<Path> supplierRunNew) {
        if (supplierRunNew == null)
            throw new IllegalArgumentException("a run directory supplier is required");
        this.supplierRun = supplierRunNew;
    }


    /**
     * @param profile what the selected version remembers; never null
     */
    public void applyProfile(SandboxProfile profile) {
        if (profile == null)
            throw new IllegalArgumentException("a profile is required");

        // THE DIRECTORY IS NOT READ FROM THE PROFILE. It is `<run>/dars` and
        // nothing else; a profile written while the field existed may still
        // name one, and that key is now ignored rather than followed.
        readDirectory();

        setTicked.clear();
        if (profile.flagAllDars()) {
            for (Row row : lstRow) {
                setTicked.add(row.strFileName());
            }
        }
        else {
            // NAMES NO LONGER PRESENT ARE DROPPED, not refused. The window has
            // to open, and a DAR deleted since the last session is a fact about
            // the directory rather than a fault in the profile.
            for (String strName : profile.lstDarSelected()) {
                if (flagPresent(strName))
                    setTicked.add(strName);
            }
        }
        model.fireTableDataChanged();
        showCount();
    }


    /**
     * @return null, always: the directory follows the run directory and is not
     *         a setting. Kept so the profile keeps writing no key of its own
     */
    public Path dirDars() {
        return null;
    }


    /**
     * @return the ticked file names, in the order the table holds them; never
     *         null, and empty means the next start uploads nothing
     */
    public List<String> lstSelected() {
        List<String> lstOut = new ArrayList<>();
        for (Row row : lstRow) {
            if (setTicked.contains(row.strFileName()))
                lstOut.add(row.strFileName());
        }
        return lstOut;
    }


    /**
     * @return the ticked DARs as files, which is what a start is given
     */
    public List<Path> lstFileSelected() {
        List<Path> lstOut = new ArrayList<>();
        for (Row row : lstRow) {
            if (setTicked.contains(row.strFileName()))
                lstOut.add(row.fileDar);
        }
        return lstOut;
    }


    /**
     * @param flagOn whether the tab accepts input; false while a stack is up,
     *        because the set that was uploaded is not editable afterwards
     */
    public void setInputEnabled(boolean flagOn) {
        this.flagEditable = flagOn;
        btnRefresh.setEnabled(flagOn);
        btnAll.setEnabled(flagOn);
        btnNone.setEnabled(flagOn);
        btnImport.setEnabled(flagOn);
        table.setEnabled(flagOn);
    }


    /**
     * Back to what a version with no profile shows: everything in the run
     * directory's `dars` ticked.
     *
     * Not the same as {@link #refresh}, which keeps the ticks. This is what a
     * window with NO version selected shows, and a selection carried over from
     * the version that was selected a moment ago would be a set of file names
     * from another directory.
     */
    public void reset() {
        readDirectory();
        tickAll(true);
    }


    /**
     * Re-reads the directory, keeping every tick whose file is still there.
     */
    public void refresh() {
        readDirectory();
        setTicked.retainAll(setNamesPresent());
        model.fireTableDataChanged();
        showCount();
    }


    /**
     * Re-reads the directory and ticks one file, leaving every other tick as
     * it was.
     *
     * FOR A DAR THIS WINDOW HAS JUST PUT THERE. A file that arrived while the
     * table was open is not in it, so the read comes first; and the tick is
     * what makes the next start upload it, which is the whole point of having
     * built it.
     *
     * @param strFileName the file to tick, as it is named on disk
     * @return whether the directory holds a file by that name
     */
    public boolean flagTick(String strFileName) {
        if (strFileName == null || strFileName.trim().isEmpty())
            throw new IllegalArgumentException("a file name is required");

        readDirectory();
        setTicked.retainAll(setNamesPresent());
        boolean flagFound = flagPresent(strFileName.trim());
        if (flagFound)
            setTicked.add(strFileName.trim());
        model.fireTableDataChanged();
        showCount();
        return flagFound;
    }


    /**
     * What one import did.
     *
     * @param flagOk whether the file is in the store now
     * @param strFileName its name there, or null when it is not
     * @param strSaid one line for the pane
     */
    record Imported(boolean flagOk, String strFileName, String strSaid) {}


    /**
     * THE CHECK AND THE COPY, without a window, so a test reaches them.
     *
     * @param fileDar the DAR the user chose
     * @param dirStore `&lt;run&gt;/dars`, or null when no version is selected
     * @return what happened; never null, and never an exception
     */
    static Imported importInto(Path fileDar, Path dirStore) {
        if (fileDar == null)
            throw new IllegalArgumentException("a DAR is required");

        String strName = fileDar.getFileName().toString();
        if (dirStore == null)
            return new Imported(false, null, strName + " NOT imported - select a Canton first");

        DarInfo info;
        try {
            info = DarCatalog.inspect(fileDar);
        }
        catch (RuntimeException ex) {
            return new Imported(false, null, strName + " NOT imported - " + ex.getMessage());
        }

        // ONE PACKAGE, ONE FILE. A start carrying the same package under two
        // names is refused by DarCatalog.ofFiles, so the store must not hold
        // one. A file of the SAME name is the copy rule's to decide, below.
        if (Files.isDirectory(dirStore)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirStore, STR_GLOB_DAR)) {
                for (Path fileHeld : stream) {
                    if (fileHeld.getFileName().toString().equals(strName))
                        continue;
                    String strPkgHeld = strPkgIdOrNull(fileHeld);
                    if (info.strPkgId().equals(strPkgHeld)) {
                        return new Imported(false, null, strName + " NOT imported - package "
                                + strShort(info.strPkgId()) + " is already in the store as "
                                + fileHeld.getFileName());
                    }
                }
            }
            catch (IOException ex) {
                return new Imported(false, null, strName + " NOT imported - cannot list "
                        + dirStore + ": " + ex.getMessage());
            }
        }

        String strRefused;
        try {
            strRefused = DarsLivePane.strCopyToStore(fileDar, dirStore);
        }
        catch (IOException ex) {
            return new Imported(false, null, strName + " NOT imported - " + ex.getMessage());
        }
        if (!strRefused.isEmpty())
            return new Imported(false, null, strName + strRefused);
        return new Imported(true, strName, info.strLabel() + " imported and ticked");
    }


    /**
     * @param fileDar a file in the store
     * @return its package id, or null when its manifest cannot be read - such
     *         a file is listed as UNREADABLE and cannot be a duplicate
     */
    private static String strPkgIdOrNull(Path fileDar) {
        try {
            return DarCatalog.inspect(fileDar).strPkgId();
        }
        catch (RuntimeException ex) {
            return null;
        }
    }


    private void importChosen() {
        JFileChooser chooser = new JFileChooser();
        // Hidden directories are shown, as on the DARs tabs of LocalNetND: a
        // DAR built under `.daml/dist` is otherwise out of reach.
        chooser.setFileHidingEnabled(false);
        chooser.setDialogTitle("Import a DAR into this version's store");
        chooser.setFileFilter(new FileNameExtensionFilter("Daml archives (*.dar)", "dar"));
        if (dirImportLast != null && Files.isDirectory(dirImportLast))
            chooser.setCurrentDirectory(dirImportLast.toFile());
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;

        Path fileDar = chooser.getSelectedFile().toPath();
        dirImportLast = fileDar.getParent();
        Imported imported = importInto(fileDar, dirEffective());
        if (imported.flagOk())
            flagTick(imported.strFileName());
        else
            refresh();
        lblImport.setText(imported.strSaid());
    }


    private void tickAll(boolean flagOn) {
        setTicked.clear();
        if (flagOn) {
            for (Row row : lstRow) {
                setTicked.add(row.strFileName());
            }
        }
        model.fireTableDataChanged();
        showCount();
    }


    /**
     * @return `&lt;run&gt;/dars`, or null when no run directory is known yet
     */
    private Path dirEffective() {
        Path dirRun = supplierRun.get();
        return dirRun == null ? null : dirRun.resolve(RunDirectory.STR_DIR_DARS);
    }


    private boolean flagPresent(String strName) {
        for (Row row : lstRow) {
            if (row.strFileName().equals(strName))
                return true;
        }
        return false;
    }


    private Set<String> setNamesPresent() {
        Set<String> setOut = new LinkedHashSet<>();
        for (Row row : lstRow) {
            setOut.add(row.strFileName());
        }
        return setOut;
    }


    /**
     * Fills the rows from disk. Never throws: an unreadable directory is an
     * empty table with a line under it saying so, and a window that will not
     * open is a worse answer than a table that says there is nothing there.
     */
    private void readDirectory() {
        lstRow.clear();
        Path dir = dirEffective();
        if (dir == null || !Files.isDirectory(dir)) {
            model.fireTableDataChanged();
            showCount();
            return;
        }

        List<Path> lstFile = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, STR_GLOB_DAR)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file))
                    lstFile.add(file);
            }
        }
        catch (IOException ex) {
            model.fireTableDataChanged();
            showCount();
            return;
        }

        // BY FILE NAME, which is the order they are uploaded in. A table in one
        // order and an upload in another is two answers to `which came first`.
        lstFile.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));

        for (Path file : lstFile) {
            try {
                DarInfo info = DarCatalog.inspect(file);
                lstRow.add(new Row(file, info.strName(), info.strVersion(),
                        strShort(info.strPkgId())));
            }
            catch (RuntimeException ex) {
                // LISTED WITH ITS REASON. A file that disappears from a list
                // without a word is one somebody spends ten minutes looking
                // for, and it stays untickable by being untickable at start.
                lstRow.add(new Row(file, null, null, "UNREADABLE: " + ex.getMessage()));
            }
        }
        model.fireTableDataChanged();
        showCount();
    }


    private static String strShort(String strPkgId) {
        return strPkgId.length() <= N_PKG_ID_SHOWN ? strPkgId
                : strPkgId.substring(0, N_PKG_ID_SHOWN);
    }


    private void showCount() {
        Path dir = dirEffective();
        lblDir.setText(dir == null ? "no run directory" : dir.toString());
        lblCount.setText(lstSelected().size() + " of " + lstRow.size() + " selected");
    }
}
