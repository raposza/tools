// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.admin.AdminChannels;
import com.raposza.admin.AdminPackages;
import com.raposza.admin.AdminTopology;
import com.raposza.wire.DescriptorSet;
import com.raposza.wire.ServerReflection;

import io.grpc.ManagedChannel;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

/**
 * What the RUNNING participant has, and the handles that change it.
 *
 * <h2>Not the same list as the pane above it</h2>
 *
 * `DarsPane` lists the DAR STORE - files on disk, ticked for the next start.
 * This lists what the participant actually holds, which diverges the moment a
 * script uploads something, a DAR is removed here, or Upload puts one on a
 * running node without restarting it.
 *
 * <h2>The columns are the participant's, not ours</h2>
 *
 * {@link AdminPackages} returns each DAR as field name to text, so the columns
 * are whatever that Canton version describes. A version that adds a column
 * shows it rather than losing it, and no column list is carried in this file.
 *
 * THE FIRST COLUMN IS THE IDENTIFIER. It is the first field of the vendor's own
 * description message on both generations, and it is what the removal and
 * vetting calls are given. If a version ever reorders that message the calls
 * fail with the participant's own message rather than silently acting on the
 * wrong DAR.
 *
 * <h2>Vetted is DERIVED, and says `?` when it cannot be</h2>
 *
 * The last column is ours. `ListDars` does not report vetting state, so it is
 * matched from {@link AdminTopology}'s vetted package ids against the
 * DAR's own main package id. Two things make it unknown rather than false: a
 * topology read that failed, and a DAR description carrying no package id at
 * all, which is what the 2.x `ListDars` looks like. A blank cell would be read
 * as "not vetted", so it says `?` instead.
 *
 * <h2>Everything happens off the event dispatch thread</h2>
 *
 * A removal is a topology change and an upload is a whole file; both take as
 * long as they take.
 *
 * Author Claude/bentzn
 */
public final class DarsLivePane extends JPanel {

    private static final long serialVersionUID = 1L;

    /** What the pane says when nothing is up. */
    private static final String STR_IDLE = "no participant is running";

    /** The derived column's heading. */
    private static final String STR_COL_VETTED = "Vetted";

    /** What a cell says when the answer is not known. */
    private static final String STR_UNKNOWN = "?";

    /** How long a vetting change is waited for before the pane gives up. */
    private static final long N_MS_SETTLE = 8000;

    /** How often the topology state is re-read while waiting. */
    private static final long N_MS_POLL = 250;

    private final DefaultTableModel model = new DefaultTableModel(new Object[] {"DAR"}, 0) {

        private static final long serialVersionUID = 1L;


        @Override
        public boolean isCellEditable(int idxRow, int idxCol) {
            return false;
        }
    };

    private final transient JTable table = new JTable(model);

    private final JButton btnRefresh = new JButton("Refresh");

    private final JButton btnUpload = new JButton("Upload DAR");

    private final JButton btnRemove = new JButton("Remove DAR");

    private final JButton btnVet = new JButton("Vet");

    private final JButton btnUnvet = new JButton("Unvet");

    private final JLabel lblState = new JLabel(STR_IDLE);

    /** The admin API port of the running stack, or 0 when nothing is up. */
    private transient IntSupplier supplierPort = () -> 0;

    /** Where the file chooser opens, or null when no run directory is known. */
    private transient Supplier<Path> supplierDirDar = () -> null;

    /**
     * THE DIRECTORY OF THE LAST DAR UPLOADED, from any participant's tab - his
     * instruction, 2026-10-03: "When one DAR has been uploaded, open the dialog
     * in the same dir as the previous DAR." Five Registry Utility DARs sit in
     * one directory, and the chooser went back to the store after each one.
     * Shared by the three LocalNetND tabs, because the next DAR is in the same
     * place whichever participant it is for. Null until the first upload.
     */
    private static volatile Path dirLastUpload;

    /**
     * The version's DAR STORE - `&lt;run&gt;/dars` - where an uploaded DAR is
     * copied, or null for none. His question of 2026-09-23: "When they are
     * uploaded, are they also copied to the dir of that version?" They were
     * not; a DAR put on a running node by hand was on the ledger and in a
     * snapshot of it, and nowhere the next start or the Store tab could see.
     */
    private transient Supplier<Path> supplierDirStore = () -> null;

    /** Told what happened, for the milestone pane. */
    private transient Consumer<String> sinkNote = strLine -> {
    };


    public DarsLivePane() {
        super(new BorderLayout());
        setOpaque(false);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(GuiTheme.scale(22));

        btnRefresh.addActionListener(evt -> refresh());
        btnUpload.addActionListener(evt -> upload());
        btnRemove.addActionListener(evt -> act(AdminPackages.STR_REMOVE_DAR, "removed"));
        btnVet.addActionListener(evt -> act(AdminPackages.STR_VET, "vetted"));
        btnUnvet.addActionListener(evt -> act(AdminPackages.STR_UNVET, "unvetted"));

        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiTheme.scale(8), 0));
        pnlButtons.setOpaque(false);
        pnlButtons.add(btnRefresh);
        pnlButtons.add(btnUpload);
        pnlButtons.add(btnRemove);
        pnlButtons.add(btnVet);
        pnlButtons.add(btnUnvet);
        lblState.setForeground(GuiTheme.colMuted());
        pnlButtons.add(lblState);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(GuiTheme.colCardBorder(), 1, true));

        JPanel pnlInner = new JPanel(new BorderLayout(0, GuiTheme.scale(GuiTheme.N_GAP)));
        pnlInner.setOpaque(false);
        pnlInner.add(pnlButtons, BorderLayout.NORTH);
        pnlInner.add(scroll, BorderLayout.CENTER);

        add(GuiTheme.card("DARs on the participant", pnlInner), BorderLayout.CENTER);

        // NOTHING IS RUNNING YET, so nothing here can act.
        setInputEnabled(true);
    }


    /**
     * @param supplierPortNew the admin API port while a stack is up, 0 otherwise
     */
    public void useAdminPort(IntSupplier supplierPortNew) {
        if (supplierPortNew == null)
            throw new IllegalArgumentException("a port supplier is required");
        this.supplierPort = supplierPortNew;
        setInputEnabled(true);
    }


    /**
     * @param supplierDirDarNew where the upload chooser opens - the run's own
     *        DAR store, which is where a developer's freshly built DAR is
     */
    public void useDarDirectory(Supplier<Path> supplierDirDarNew) {
        if (supplierDirDarNew == null)
            throw new IllegalArgumentException("a directory supplier is required");
        this.supplierDirDar = supplierDirDarNew;
    }


    /**
     * @param supplierDirStoreNew the version's DAR store, asked at each upload;
     *        it may answer null
     */
    public void useDarStore(Supplier<Path> supplierDirStoreNew) {
        if (supplierDirStoreNew == null)
            throw new IllegalArgumentException("a store supplier is required");
        this.supplierDirStore = supplierDirStoreNew;
    }


    /**
     * COPIES AN UPLOADED DAR INTO THE STORE, and never over a different file.
     *
     * A file of the same name and the same bytes is already there and nothing
     * is written. One of the same name and DIFFERENT bytes is left alone and
     * said so: overwriting it would silently change what the next start
     * uploads.
     *
     * @param fileDar what was uploaded
     * @param dirStore the store, or null
     * @return one line for the note, or "" when there is no store
     * @throws IOException when the copy fails
     */
    static String strCopyToStore(Path fileDar, Path dirStore) throws IOException {
        if (dirStore == null)
            return "";
        // SILENT UNLESS IT FAILS - his instruction, 2026-09-23: the line is
        // `app-user: aviation-0.0.1.dar uploaded` and nothing more. Only a copy
        // that did NOT happen is worth a word, because then the store and the
        // ledger disagree.
        Path fileKept = dirStore.resolve(fileDar.getFileName().toString());
        if (fileKept.toAbsolutePath().normalize().equals(fileDar.toAbsolutePath().normalize()))
            return "";
        if (Files.exists(fileKept)) {
            if (Files.mismatch(fileKept, fileDar) == -1L)
                return "";
            return ", NOT copied - a different " + fileKept.getFileName() + " is in the "
                    + strStoreName(dirStore);
        }
        Files.createDirectories(dirStore);
        Files.copy(fileDar, fileKept);
        return "";
    }


    /**
     * ONE LINE IN THE LOG, not a path - his instruction of 2026-09-23, "You
     * need to clean up the log". The store is named by the version directory
     * it belongs to, `3.5.18-open_source`, which is the part of the path a
     * reader needs.
     *
     * @param dirStore a DAR store, `&lt;run&gt;/dars`
     * @return e.g. `3.5.18-open_source DAR store`
     */
    static String strStoreName(Path dirStore) {
        Path dirRun = dirStore.toAbsolutePath().normalize().getParent();
        return (dirRun == null || dirRun.getFileName() == null ? dirStore.toString()
                : dirRun.getFileName().toString()) + " DAR store";
    }


    /**
     * @param strId a DAR's identifier, the first column
     * @param strName its name, or null when the version carries no such column
     * @param strVersion its version, or null
     * @return `name version`, or the first twelve characters of the id
     */
    static String strDarLabel(String strId, String strName, String strVersion) {
        if (strName != null && !strName.isBlank())
            return strName + (strVersion == null || strVersion.isBlank() ? "" : " " + strVersion);
        return strId.length() > 12 ? strId.substring(0, 12) + "..." : strId;
    }


    /**
     * @param sinkNoteNew told one line per action
     */
    public void useNoteSink(Consumer<String> sinkNoteNew) {
        if (sinkNoteNew == null)
            throw new IllegalArgumentException("a note sink is required");
        this.sinkNote = sinkNoteNew;
    }


    /**
     * Clears the table, for a stack that has gone down.
     */
    public void reset() {
        model.setColumnIdentifiers(new Object[] {"DAR", STR_COL_VETTED});
        model.setRowCount(0);
        lblState.setText(STR_IDLE);
        // A STACK THAT HAS GONE DOWN leaves nothing to upload to - his
        // instruction, 2026-10-03: "The button Upload DAR should be disabled if
        // no participant is running."
        setInputEnabled(true);
    }


    /**
     * Reads the participant's DAR list, off the event dispatch thread.
     */
    public void refresh() {
        int nPort = supplierPort.getAsInt();
        if (nPort <= 0) {
            reset();
            return;
        }

        lblState.setText("reading...");
        setInputEnabled(false);
        run(nPort, session -> {
            List<Map<String, String>> lstDar = session.packages().lstDar();
            List<String> lstPackageId = session.packages().lstPackageIdMain(lstDar);
            Set<String> setVetted = session.setVetted();
            SwingUtilities.invokeLater(() -> show(lstDar, lstPackageId, setVetted));
        });
    }


    /**
     * Sends a DAR the person picks to the running participant.
     */
    private void upload() {
        int nPort = supplierPort.getAsInt();
        if (nPort <= 0)
            return;

        Path dirLast = dirLastUpload;
        Path dirStart = dirLast != null && Files.isDirectory(dirLast) ? dirLast : supplierDirDar.get();
        JFileChooser chooser = new JFileChooser();
        // HIDDEN DIRECTORIES ARE SHOWN - his instruction, 2026-09-23. Every store
        // this application keeps is under `~/.raposza`, and Swing's default hides
        // it, so the chooser could not reach a DAR the window itself had built.
        chooser.setFileHidingEnabled(false);
        chooser.setDialogTitle("Upload a DAR to the participant");
        chooser.setFileFilter(new FileNameExtensionFilter("Daml archives (*.dar)", "dar"));
        if (dirStart != null && Files.isDirectory(dirStart))
            chooser.setCurrentDirectory(dirStart.toFile());
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;

        Path fileDar = chooser.getSelectedFile().toPath();
        String strName = fileDar.getFileName().toString();
        if (fileDar.getParent() != null)
            dirLastUpload = fileDar.getParent();

        lblState.setText("uploading...");
        setInputEnabled(false);
        run(nPort, session -> {
            byte[] arrDar;
            try {
                arrDar = Files.readAllBytes(fileDar);
            }
            catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            session.packages().uploadDar(arrDar, strName);
            // AFTER THE UPLOAD, so a DAR the participant refused is not kept as
            // if it were one the next start could use.
            String strKept;
            try {
                strKept = strCopyToStore(fileDar, supplierDirStore.get());
            }
            catch (IOException ex) {
                strKept = ", NOT copied to the DAR store - " + ex.getMessage();
            }
            String strKeptNote = strKept;
            List<Map<String, String>> lstDar = session.packages().lstDar();
            List<String> lstPackageId = session.packages().lstPackageIdMain(lstDar);
            Set<String> setVetted = session.setVetted();
            SwingUtilities.invokeLater(() -> {
                sinkNote.accept(strName + " uploaded" + strKeptNote);
                show(lstDar, lstPackageId, setVetted);
            });
        });
    }


    /**
     * @param strMethod which RPC, one of {@link AdminPackages}'s names
     * @param strDone the past tense, for the note
     */
    private void act(String strMethod, String strDone) {
        int nPort = supplierPort.getAsInt();
        String strId = strIdSelected();
        if (nPort <= 0 || strId == null)
            return;
        if (AdminPackages.STR_REMOVE_DAR.equals(strMethod) && !flagConfirmRemove(strId))
            return;

        String strLabel = strDarLabel(strId, strCellSelected("name"), strCellSelected("version"));
        lblState.setText(strMethod + "...");
        setInputEnabled(false);
        run(nPort, session -> {
            AdminPackages admin = session.packages();
            try {
                if (AdminPackages.STR_REMOVE_DAR.equals(strMethod))
                    admin.removeDar(strId);
                else if (AdminPackages.STR_VET.equals(strMethod))
                    admin.vetDar(strId);
                else
                    admin.unvetDar(strId);
            }
            catch (RuntimeException ex) {
                throw new IllegalStateException(strRefusal(strLabel, ex.getMessage()), ex);
            }

            List<Map<String, String>> lstDar = admin.lstDar();
            List<String> lstPackageId = admin.lstPackageIdMain(lstDar);
            String strPackageId = strPackageIdFor(lstDar, lstPackageId, strId);
            Set<String> setVetted = setVettedSettled(session, strMethod, strPackageId);
            String strNote = strOutcome(strMethod, strDone, strLabel, setVetted, strPackageId);
            SwingUtilities.invokeLater(() -> {
                sinkNote.accept(strNote);
                show(lstDar, lstPackageId, setVetted);
            });
        });
    }


    /** `<rpc> was refused with <CODE>: <description>`, as the wire layer words it. */
    private static final Pattern PAT_REFUSED =
            Pattern.compile("^(\\S+) was refused with ([A-Z_]+): (.*)$", Pattern.DOTALL);

    /** The participant's reason for keeping a DAR, with the ids it names. */
    private static final Pattern PAT_IN_USE = Pattern.compile(
            "main package ([0-9a-f]+) is in-use by contract ContractId\\(([0-9a-f]+)\\)"
                    + " on synchronizer (\\S+?)\\.*$", Pattern.DOTALL);


    /**
     * The refusal, worded for the dialog rather than for a log.
     *
     * What the wire layer hands over is the full RPC name, the gRPC code and
     * Canton's description with three unbroken ids in it - one line of 400
     * characters that the dialog wraps mid-id. His instruction, 2026-10-04:
     * pretty it up. The DAR is named as the table names it, the ids are cut
     * to a prefix, and the one refusal measured so far - a package still in
     * use by a contract - gets its own wording and what to do about it.
     *
     * @param strLabel the DAR, as {@link #strDarLabel} names it
     * @param strMessage what the call threw
     * @return the text for the dialog, with line breaks
     */
    static String strRefusal(String strLabel, String strMessage) {
        if (strMessage == null)
            return strLabel + ": the participant refused, and gave no reason";
        Matcher mRefused = PAT_REFUSED.matcher(strMessage.trim());
        if (!mRefused.matches())
            return strLabel + ": " + strMessage;
        String strRpc = mRefused.group(1).substring(mRefused.group(1).lastIndexOf('/') + 1);
        String strCode = mRefused.group(2);
        String strWhy = mRefused.group(3).trim();

        Matcher mInUse = PAT_IN_USE.matcher(strWhy);
        if (mInUse.find()) {
            return strLabel + " cannot be removed.\n\n"
                    + "Its main package is still in use by a contract on the ledger:\n"
                    + "    package      " + strShortId(mInUse.group(1)) + "\n"
                    + "    contract     " + strShortId(mInUse.group(2)) + "\n"
                    + "    synchronizer " + strShortId(mInUse.group(3)) + "\n\n"
                    + "Archive the contract first, or leave the DAR in place.";
        }
        return strLabel + ": " + strRpc + " was refused (" + strCode + ").\n\n" + strWhy;
    }


    /**
     * @param strId a package id, a contract id or a synchronizer id
     * @return its first 24 characters and an ellipsis, or the whole thing
     *         when it is not much longer than that
     */
    static String strShortId(String strId) {
        return strId.length() > 28 ? strId.substring(0, 24) + "..." : strId;
    }


    /**
     * WHAT HAPPENED, NOT WHAT WAS ASKED. Until 2026-09-23 a Vet or an Unvet was
     * reported as done whenever the call returned, and the call returns as
     * soon as the request is accepted - his `app-user: unvetted pharma 0.0.1`
     * sat beside a Vetted column that still said yes. The line now follows the
     * topology read {@link #setVettedSettled} waited for.
     *
     * @param strMethod which RPC was called
     * @param strDone its past tense
     * @param strLabel the DAR, as the log names it
     * @param setVetted the vetted package ids after the wait, possibly empty
     *        when they could not be read
     * @param strPackageId the DAR's main package id
     * @return one line for the log
     */
    static String strOutcome(String strMethod, String strDone, String strLabel,
            Set<String> setVetted, String strPackageId) {
        boolean flagVetting = AdminPackages.STR_VET.equals(strMethod)
                || AdminPackages.STR_UNVET.equals(strMethod);
        if (!flagVetting || strPackageId == null || setVetted == null || setVetted.isEmpty())
            return strLabel + " " + strDone;
        boolean flagWanted = AdminPackages.STR_VET.equals(strMethod);
        if (setVetted.contains(strPackageId) == flagWanted)
            return strLabel + " " + strDone;
        return strLabel + " NOT " + strDone + " - the participant accepted the request and"
                + " after " + (N_MS_SETTLE / 1000) + " s still lists it as "
                + (flagWanted ? "not vetted" : "vetted");
    }


    /**
     * The vetting state once the change has landed.
     *
     * VETTING IS ASYNCHRONOUS. `VetDar` and `UnvetDar` return as soon as the
     * topology transaction is submitted, and the participant observes it a
     * moment later - so a read taken straight after the call reports the
     * state before it, and the column only moved when the operator pressed
     * Refresh. This reads until the DAR's own package id has changed side,
     * and gives up quietly after {@link #N_MS_SETTLE} rather than blocking a
     * button for ever.
     *
     * @param session the open facades
     * @param strMethod which RPC was called
     * @param strId the DAR's main package id, which is NOT its identifier on
     *        the 2.x line - there the table holds a DAR hash and the vetting
     *        is recorded against the package
     * @return the vetted ids, settled or not
     */
    private static Set<String> setVettedSettled(Session session, String strMethod,
            String strId) {
        Set<String> setVetted = session.setVetted();
        if (!AdminPackages.STR_VET.equals(strMethod)
                && !AdminPackages.STR_UNVET.equals(strMethod))
            return setVetted;

        if (strId == null)
            return setVetted;

        boolean flagWanted = AdminPackages.STR_VET.equals(strMethod);
        long nGiveUp = System.currentTimeMillis() + N_MS_SETTLE;
        while (setVetted != null && !setVetted.isEmpty()
                && setVetted.contains(strId) != flagWanted
                && System.currentTimeMillis() < nGiveUp) {
            try {
                Thread.sleep(N_MS_POLL);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return setVetted;
            }
            setVetted = session.setVetted();
        }
        return setVetted;
    }


    /**
     * Asks before a removal, and only before a removal.
     *
     * A DAR removal cannot be undone from this window: what comes back is a
     * fresh upload of a file the participant no longer has. Vet and Unvet
     * are one press from each other and are not asked about.
     *
     * The dialog states the vetting, because a vetted DAR CAN be removed -
     * the participant revokes the main package's vetting on the way, so the
     * `yes` in the table is not the guard a reader might take it for.
     *
     * @param strId the DAR's identifier, as the table holds it
     * @return whether to go ahead
     */
    private boolean flagConfirmRemove(String strId) {
        StringBuilder bldText = new StringBuilder("Remove this DAR from the running"
                + " participant?\n\n");
        String strName = strCellSelected("name");
        if (strName != null)
            bldText.append(strName).append('\n');
        bldText.append(strId).append('\n');
        String strVetted = strCellSelected(STR_COL_VETTED);
        if (strVetted != null)
            bldText.append("Vetted: ").append(strVetted).append('\n');

        Object[] arrOption = {"Remove", "Cancel"};
        return Modals.idxOption(this, bldText.toString(), arrOption,
                arrOption[1]) == 0;
    }


    /**
     * @param strColumn a column heading
     * @return that cell of the selected row, or null when the version does
     *         not carry the column or nothing is selected
     */
    private String strCellSelected(String strColumn) {
        int idxRow = table.getSelectedRow();
        if (idxRow < 0)
            return null;
        for (int idxCol = 0; idxCol < model.getColumnCount(); idxCol++) {
            if (!strColumn.equals(model.getColumnName(idxCol)))
                continue;
            Object objCell = model.getValueAt(table.convertRowIndexToModel(idxRow), idxCol);
            return objCell == null ? null : String.valueOf(objCell);
        }
        return null;
    }


    /**
     * @param lstDar the DARs as described
     * @param lstPackageId their main package ids, in the same order
     * @param strId the identifier the buttons act on
     * @return that DAR's package id, falling back to the identifier itself -
     *         on 3.x the two are the same string
     */
    private static String strPackageIdFor(List<Map<String, String>> lstDar,
            List<String> lstPackageId, String strId) {
        for (int idxDar = 0; idxDar < lstDar.size() && idxDar < lstPackageId.size(); idxDar++) {
            if (strId.equals(AdminPackages.strFirstValue(lstDar.get(idxDar))))
                return lstPackageId.get(idxDar);
        }
        return strId;
    }


    /**
     * @return the first column of the selected row, or null when nothing is
     *         selected
     */
    private String strIdSelected() {
        int idxRow = table.getSelectedRow();
        if (idxRow < 0 || model.getColumnCount() == 0)
            return null;
        Object objId = model.getValueAt(table.convertRowIndexToModel(idxRow), 0);
        return objId == null ? null : String.valueOf(objId);
    }


    /**
     * One channel per action, opened and closed around it.
     *
     * A channel kept open across a Stop would be pointing at a dead port and
     * the failure would arrive on the next click rather than here. The
     * descriptor set is fetched ONCE and both facades are built over it: two
     * reflection walks per click is the same answer twice.
     *
     * @param nPort the admin API port
     * @param task what to do with the facades
     */
    private void run(int nPort, TaskAdmin_i task) {
        Thread threadWork = new Thread(() -> {
            ManagedChannel channel = null;
            try {
                channel = AdminChannels.open("127.0.0.1", nPort);
                DescriptorSet set = ServerReflection.of(channel).descriptorSet();
                task.run(new Session(channel, set));
            }
            catch (RuntimeException ex) {
                String strMessage = ex.getMessage();
                SwingUtilities.invokeLater(() -> failed(strMessage));
            }
            finally {
                if (channel != null) {
                    channel.shutdownNow();
                    try {
                        channel.awaitTermination(5, TimeUnit.SECONDS);
                    }
                    catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
                SwingUtilities.invokeLater(() -> setInputEnabled(true));
            }
        }, "sandbox-gui-admin");
        threadWork.setDaemon(true);
        threadWork.start();
    }


    /**
     * @param lstDar one map per DAR, field name to text
     * @param lstPackageId each DAR's main package id, in the same order
     * @param setVetted the vetted package ids, or null when the topology read
     *        did not answer
     */
    private void show(List<Map<String, String>> lstDar, List<String> lstPackageId,
            Set<String> setVetted) {
        List<String> lstColumn = new ArrayList<>();
        if (!lstDar.isEmpty())
            lstColumn.addAll(lstDar.get(0).keySet());
        if (lstColumn.isEmpty())
            lstColumn.add("DAR");
        lstColumn.add(STR_COL_VETTED);

        model.setColumnIdentifiers(lstColumn.toArray());
        model.setRowCount(0);
        for (int idxDar = 0; idxDar < lstDar.size(); idxDar++) {
            Map<String, String> mapDar = lstDar.get(idxDar);
            Object[] arrCell = new Object[lstColumn.size()];
            for (int idxCol = 0; idxCol < lstColumn.size() - 1; idxCol++) {
                arrCell[idxCol] = mapDar.get(lstColumn.get(idxCol));
            }
            arrCell[lstColumn.size() - 1] = strVetted(
                    idxDar < lstPackageId.size() ? lstPackageId.get(idxDar) : null, setVetted);
            model.addRow(arrCell);
        }

        String strCount = lstDar.size() + (lstDar.size() == 1 ? " DAR" : " DARs");
        lblState.setText(flagUnknown(setVetted) ? strCount + " - vetting unavailable"
                : strCount);
    }


    /**
     * @param strPackageId the DAR's main package id, or null
     * @param setVetted the vetted package ids, or null
     * @return `yes`, `no`, or `?` when either side of the match is missing
     */
    private static String strVetted(String strPackageId, Set<String> setVetted) {
        if (flagUnknown(setVetted) || strPackageId == null)
            return STR_UNKNOWN;
        return setVetted.contains(strPackageId) ? "yes" : "no";
    }


    /**
     * A running participant has vetted its own admin workflow packages, so
     * an EMPTY answer is a read that did not work rather than a ledger with
     * nothing vetted. Reporting it as `no` is what the authorized-store
     * version of this did, and it was wrong on every row.
     *
     * @param setVetted what the topology service gave back
     * @return whether the column can be derived at all
     */
    private static boolean flagUnknown(Set<String> setVetted) {
        return setVetted == null || setVetted.isEmpty();
    }


    /**
     * @param strMessage what the participant, or the connection, said
     */
    private void failed(String strMessage) {
        // A STACK TAKEN AWAY IS NOT A FAULT. `refresh` reads the admin API on
        // its own thread, and Stop can land while that read is in flight - in
        // which case it fails because the participant went away, which is what
        // the operator just asked for. Raising a modal there reports a
        // successful stop as an error, and it does it AFTER the window has
        // already said STOPPED.
        //
        // Same shape as the ordering rule the stack already obeys for scribe:
        // a client left connected to a server that is being taken away reports
        // the shutdown as a fault.
        //
        // THE PORT IS THE TEST, and it is exact rather than a guess about
        // timing: `nPortAdmin` answers 0 unless a stack is running, and this
        // runs on the event dispatch thread after the stop has been applied.
        //
        // FOUND BY AN UNATTENDED LIFECYCLE RUN, which presses Stop as soon as RUNNING
        // arrives and hit two of these dialogs on its first run.
        if (supplierPort.getAsInt() <= 0) {
            lblState.setText("stopped");
            return;
        }
        lblState.setText("failed");
        Modals.warn(this, strMessage);
    }


    /**
     * @param flagOn false while a call is in flight; true lets the controls
     *        follow whether a participant is running - with none, every one of
     *        them is off, because each one talks to the participant
     */
    private void setInputEnabled(boolean flagOn) {
        boolean flagUp = flagOn && supplierPort.getAsInt() > 0;
        btnRefresh.setEnabled(flagUp);
        btnUpload.setEnabled(flagUp);
        btnRemove.setEnabled(flagUp);
        btnVet.setEnabled(flagUp);
        btnUnvet.setEnabled(flagUp);
    }


    /**
     * One open channel, described once, with both facades over it.
     */
    private static final class Session {

        private final ManagedChannel channel;

        private final DescriptorSet set;

        private final AdminPackages packages;


        private Session(ManagedChannel channelNew, DescriptorSet setNew) {
            this.channel = channelNew;
            this.set = setNew;
            this.packages = new AdminPackages(channelNew, setNew);
        }


        private AdminPackages packages() {
            return packages;
        }


        /**
         * The vetted package ids, or null.
         *
         * A DAR list that renders is worth more than a column that is right,
         * so a topology service that is absent, refuses, or answers with
         * something this cannot read costs the column and nothing else.
         *
         * @return the ids, or null when the read did not answer
         */
        private Set<String> setVetted() {
            try {
                return new AdminTopology(channel, set).setPackageIdVetted();
            }
            catch (RuntimeException ex) {
                return null;
            }
        }
    }


    /**
     * What one button does once the facades are open.
     */
    private interface TaskAdmin_i {

        void run(Session session);
    }
}
