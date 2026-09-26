// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.runtime.db.PostgresCoordinates;
import com.raposza.sandbox.app.SandboxService;
import com.raposza.sandbox.gui.LedgerProbe.Contract;
import com.raposza.sandbox.gui.LedgerProbe.Ledger;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * What is on the ledger: parties, templates, and the contracts that are and
 * are not still active.
 *
 * <h2>Read the way the CantonSandboxPqs prototype read it</h2>
 *
 * Over JDBC, out of Canton's own schema, because that works with no token, no
 * endpoint path and no client library - the prototype established this and it
 * is the half of it that is version-independent. The alternative is the JSON
 * Ledger API, whose paths differ between v1 on 2.x and v2 on 3.x and are not
 * banked in the inventory.
 *
 * <h2>Nothing about the schema is assumed. All of it is discovered</h2>
 *
 * The prototype's queries are 2.10's, and the corpus has never measured 3.x's
 * internal layout. So the SCHEMA is found by asking which one owns
 * `participant_events_create`, every COLUMN is chosen from what
 * `information_schema` reports, and each missing piece degrades to a blank
 * rather than to an exception. A pane that says "no create-event table here"
 * is a measurement; a pane that throws is a guess that failed.
 *
 * Parties and templates both come out of `string_interning`, which stores them
 * with a one-character tag - `p|` for a party and `t|` for a template. That is
 * banked evidence, recorded in the prototype's own javadoc, and it is why
 * neither needs a table whose name would have to be guessed.
 *
 * <h2>The reading itself is in {@link LedgerProbe}</h2>
 *
 * It was here until 2026-08-20, correctly, while a tree was the only reader.
 * An unattended harness asserts on the same numbers, and a harness with its own
 * copy of these queries would be green over a pane rendering something else.
 * This class is the tree; the probe is the read.
 *
 * Author Claude/bentzn
 */
public final class ParticipantDataPane extends JPanel {

    private static final long serialVersionUID = 1L;

    private final Supplier<SandboxService> supService;

    private final DefaultMutableTreeNode nodeRoot = new DefaultMutableTreeNode("ledger");

    private final DefaultMutableTreeNode nodeParty = new DefaultMutableTreeNode("parties");

    private final DefaultMutableTreeNode nodeTemplate = new DefaultMutableTreeNode("templates");

    private final DefaultMutableTreeNode nodeUser = new DefaultMutableTreeNode("users");

    private final DefaultMutableTreeNode nodeActive =
            new DefaultMutableTreeNode("contracts (active)");

    private final DefaultMutableTreeNode nodeArchived =
            new DefaultMutableTreeNode("contracts (archived)");

    private final DefaultTreeModel modelTree = new DefaultTreeModel(nodeRoot);

    private final JTree tree = new JTree(modelTree);

    private final JTextArea areaDetail = new JTextArea();

    private final JButton btnRefresh = new JButton("Refresh");

    private final JLabel lblNote = new JLabel(" ");


    /**
     * @param supServiceNew where the running service comes from; asked each
     *        time, because this pane outlives every stack the window starts
     */
    public ParticipantDataPane(Supplier<SandboxService> supServiceNew) {
        super(new BorderLayout(GuiTheme.scale(GuiTheme.N_GAP), GuiTheme.scale(GuiTheme.N_GAP)));
        setOpaque(false);
        this.supService = supServiceNew;

        nodeRoot.add(nodeParty);
        nodeRoot.add(nodeUser);
        nodeRoot.add(nodeTemplate);
        nodeRoot.add(nodeActive);
        nodeRoot.add(nodeArchived);

        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(evt -> selected(evt.getNewLeadSelectionPath()));

        areaDetail.setEditable(false);
        areaDetail.setLineWrap(true);
        areaDetail.setWrapStyleWord(false);
        GuiTheme.mono(areaDetail);
        areaDetail.setBorder(GuiTheme.borderScaled(8, 8, 8, 8));

        btnRefresh.addActionListener(evt -> refresh());
        lblNote.setForeground(GuiTheme.colMuted());

        JPanel pnlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiTheme.scale(12),
                GuiTheme.scale(4)));
        pnlBar.setOpaque(false);
        pnlBar.add(btnRefresh);
        pnlBar.add(lblNote);

        JScrollPane scrollTree = new JScrollPane(tree);
        scrollTree.setBorder(BorderFactory.createLineBorder(GuiTheme.colCardBorder(), 1, true));
        JScrollPane scrollDetail = new JScrollPane(areaDetail);
        scrollDetail.setBorder(BorderFactory.createLineBorder(GuiTheme.colCardBorder(), 1, true));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollTree, scrollDetail);
        split.setBorder(null);
        split.setResizeWeight(0.42);
        split.setDividerSize(GuiTheme.scale(8));

        add(pnlBar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }


    /** Empties the tree, so the pane never shows a ledger that is gone. */
    public void reset() {
        nodeParty.removeAllChildren();
        nodeUser.removeAllChildren();
        nodeTemplate.removeAllChildren();
        nodeActive.removeAllChildren();
        nodeArchived.removeAllChildren();
        modelTree.reload();
        areaDetail.setText("");
        lblNote.setText(" ");
    }


    private void refresh() {
        SandboxService service = supService.get();
        if (service == null || !service.isRunning()) {
            reset();
            lblNote.setText("nothing is running");
            return;
        }

        PostgresCoordinates coord = LedgerProbe.coordinatesParticipant(service);
        if (coord == null) {
            reset();
            lblNote.setText("the report carries no participant JDBC url");
            return;
        }

        btnRefresh.setEnabled(false);
        lblNote.setText("reading " + coord.strDatabase() + " ...");

        Thread threadRead = new Thread(() -> {
            Ledger ledger = null;
            String strError = null;
            try {
                ledger = LedgerProbe.read(coord);
            }
            catch (SQLException | RuntimeException ex) {
                strError = String.valueOf(ex.getMessage());
            }
            Ledger ledgerDone = ledger;
            String strErrorDone = strError;
            SwingUtilities.invokeLater(() -> readDone(ledgerDone, strErrorDone));
        }, "sandbox-gui-ledger");
        threadRead.setDaemon(true);
        threadRead.start();
    }


    /**
     * @param ledger what the probe returned, or null when it threw
     * @param strError the failure, or null
     */
    private void readDone(Ledger ledger, String strError) {
        nodeParty.removeAllChildren();
        nodeUser.removeAllChildren();
        nodeTemplate.removeAllChildren();
        nodeActive.removeAllChildren();
        nodeArchived.removeAllChildren();

        if (ledger == null) {
            modelTree.reload();
            btnRefresh.setEnabled(true);
            lblNote.setText("failed: " + strError);
            return;
        }

        for (String strParty : ledger.lstParty()) {
            nodeParty.add(new DefaultMutableTreeNode(strParty));
        }
        for (String strUser : ledger.lstUser()) {
            nodeUser.add(new DefaultMutableTreeNode(strUser));
        }
        for (String strTemplate : ledger.lstTemplate()) {
            nodeTemplate.add(new DefaultMutableTreeNode(strTemplate));
        }
        for (Contract contract : ledger.lstActive()) {
            nodeActive.add(new DefaultMutableTreeNode(contract));
        }
        for (Contract contract : ledger.lstArchived()) {
            nodeArchived.add(new DefaultMutableTreeNode(contract));
        }

        modelTree.reload();
        // The two category nodes that are small get opened; the contract ones
        // stay shut. Expanding two thousand rows costs a renderer pass each
        // and freezes the window on a populated ledger.
        tree.expandPath(new TreePath(nodeRoot.getPath()));
        tree.expandPath(new TreePath(nodeParty.getPath()));
        tree.expandPath(new TreePath(nodeUser.getPath()));
        tree.expandPath(new TreePath(nodeTemplate.getPath()));

        btnRefresh.setEnabled(true);
        if (strError != null) {
            lblNote.setText("failed: " + strError);
            return;
        }
        lblNote.setText(ledger + describeSource(ledger));
    }


    /**
     * @param ledger what was read
     * @return which tables the parties and users came out of, so the reading
     *         can be checked rather than believed
     */
    private static String describeSource(Ledger ledger) {
        List<String> lstAll = new ArrayList<>(ledger.lstSourceParty());
        lstAll.addAll(ledger.lstSourceUser());
        return lstAll.isEmpty() ? "" : " via " + String.join(", ", lstAll);
    }


    private void selected(TreePath path) {
        if (path == null)
            return;
        Object objNode = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (objNode instanceof Contract) {
            Contract contract = (Contract) objNode;
            areaDetail.setText("contract id : " + contract.strId()
                    + "\ntemplate    : " + contract.strTemplate()
                    + "\noffset      : " + contract.strOffset()
                    + "\ntime        : " + contract.strTime());
            areaDetail.setCaretPosition(0);
            return;
        }
        areaDetail.setText(String.valueOf(objNode));
        areaDetail.setCaretPosition(0);
    }
}
