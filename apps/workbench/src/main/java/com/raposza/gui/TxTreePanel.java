// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.model.TxTree;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * A transaction as a tree rather than as a wall of JSON.
 *
 * The JSON pane is exact and pastes back into things that expect the Daml
 * encoding; it is not what makes a transaction readable at four in the morning.
 * Both are kept - "Show JSON" returns to the exact form, and nothing here
 * replaces it.
 *
 * The row that answers the search is marked. Pasting a contract id and getting
 * a fifty-node tree with no indication of WHICH node was the one asked about is
 * the failure this view exists to prevent, so the found row is coloured and
 * bold and the tree opens with it visible.
 *
 * Author Claude/bentzn
 */
public final class TxTreePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** How deep the tree opens by itself. Events, not every field. */
    private static final int CNT_DEPTH_OPEN = 2;

    private static final Color COLOUR_FOUND = new Color(0x1b5e20);

    private final DefaultMutableTreeNode nodeRoot = new DefaultMutableTreeNode("tx");
    private final DefaultTreeModel model = new DefaultTreeModel(nodeRoot);
    private final JTree tree = new JTree(model);
    private final JTextArea areaDetail = new JTextArea();

    /**
     * What the detail pane holds, exactly as TxItem produced it. The
     * shortened form is derived on the way to the screen, so the box can be
     * turned off and the pastable text is back with nothing re-read.
     */
    private transient String strDetailRaw = "";

    private transient boolean flagShortIds = true;
    private final JButton btnJson = new JButton("Show JSON");


    public TxTreePanel() {
        super(new BorderLayout());

        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new ItemRenderer());
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.addTreeSelectionListener(ev -> showDetail());

        JButton btnExpand = new JButton("Expand all");
        JButton btnCollapse = new JButton("Collapse");
        btnExpand.addActionListener(ev -> expandAll());
        btnCollapse.addActionListener(ev -> collapseAll());

        JPanel pnlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiScale.scale(4),
                GuiScale.scale(2)));
        pnlBar.add(btnExpand);
        pnlBar.add(btnCollapse);
        pnlBar.add(btnJson);

        areaDetail.setEditable(false);
        areaDetail.setFont(GuiScale.fontMono(CaqlSyntax.CNT_FONT));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tree),
                new JScrollPane(areaDetail));
        split.setDividerLocation(GuiScale.scale(430));
        split.setResizeWeight(1);
        split.setBorder(null);

        setBorder(GuiScale.border(4, 4, 4, 8));
        add(pnlBar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }


    /** @param listener called when the operator asks for the JSON form */
    public void onJson(ActionListener listener) {
        btnJson.addActionListener(listener);
    }


    /**
     * @param tx the transaction to show
     * @param strRefFound what was searched for, so the row answering it can be
     *        marked and revealed; null or blank marks nothing
     */
    public void setTree(TxTree tx, String strRefFound) {
        nodeRoot.removeAllChildren();
        nodeRoot.setUserObject(new TxItem.Tx(tx));

        DefaultMutableTreeNode nodeBuilt = TxNodes.build(tx, strRefFound);
        while (nodeBuilt.getChildCount() > 0) {
            nodeRoot.add((DefaultMutableTreeNode) nodeBuilt.getChildAt(0));
        }

        model.reload();
        openTo(CNT_DEPTH_OPEN);
        strDetailRaw = ((TxItem) nodeRoot.getUserObject()).full();
        paintDetail();
        reveal();
    }


    /**
     * Opens the events but not every field of every payload. A transaction that
     * arrives fully expanded is the wall of text this view replaced.
     */
    private void openTo(int cntDepth) {
        for (int idxRow = 0; idxRow < tree.getRowCount(); idxRow++) {
            TreePath path = tree.getPathForRow(idxRow);
            if (path != null && path.getPathCount() <= cntDepth)
                tree.expandPath(path);
        }
    }


    /** Selects and scrolls to the marked row, when there is one. */
    private void reveal() {
        DefaultMutableTreeNode node = findFound(nodeRoot);
        if (node == null)
            return;

        TreePath path = new TreePath(node.getPath());
        tree.expandPath(path.getParentPath());
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
    }


    static DefaultMutableTreeNode findFound(DefaultMutableTreeNode node) {
        if (node.getUserObject() instanceof TxItem item && item.flagFound())
            return node;

        for (int idx = 0; idx < node.getChildCount(); idx++) {
            DefaultMutableTreeNode found =
                    findFound((DefaultMutableTreeNode) node.getChildAt(idx));
            if (found != null)
                return found;
        }
        return null;
    }


    private void expandAll() {
        for (int idxRow = 0; idxRow < tree.getRowCount(); idxRow++) {
            tree.expandRow(idxRow);
        }
    }


    private void collapseAll() {
        for (int idxRow = tree.getRowCount() - 1; idxRow >= 0; idxRow--) {
            tree.collapseRow(idxRow);
        }
        tree.expandRow(0);
    }


    /** @param flagShortIdsNew whether the detail pane shortens the ids it shows */
    public void setShortIds(boolean flagShortIdsNew) {
        this.flagShortIds = flagShortIdsNew;
        paintDetail();
    }


    private void paintDetail() {
        areaDetail.setText(flagShortIds ? ShortIds.text(strDetailRaw) : strDetailRaw);
        areaDetail.setCaretPosition(0);
    }


    private void showDetail() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null || !(node.getUserObject() instanceof TxItem item))
            return;

        strDetailRaw = item.full();
        paintDetail();
    }


    /**
     * Labels come from the item. The marked row is coloured and bold rather
     * than only selected, because a selection disappears the moment the
     * operator clicks anything else.
     */
    private static final class ItemRenderer extends DefaultTreeCellRenderer {

        private static final long serialVersionUID = 1L;


        @Override
        public Component getTreeCellRendererComponent(JTree treeIn, Object value, boolean flagSel,
                boolean flagExpanded, boolean flagLeaf, int numRow, boolean flagFocus) {

            super.getTreeCellRendererComponent(treeIn, value, flagSel, flagExpanded, flagLeaf,
                    numRow, flagFocus);

            Object objUser = value instanceof DefaultMutableTreeNode node ? node.getUserObject()
                    : null;
            if (!(objUser instanceof TxItem item)) {
                setToolTipText(null);
                return this;
            }

            setText(item.label());
            // A Swing tooltip is one line. The whole thing is in the detail
            // pane below, which is where a multi-line value belongs anyway.
            setToolTipText(firstLine(item.full()));

            if (item.flagFound()) {
                setFont(getFont().deriveFont(Font.BOLD));
                if (!flagSel)
                    setForeground(COLOUR_FOUND);
            }
            else {
                setFont(getFont().deriveFont(Font.PLAIN));
            }
            return this;
        }


        private static String firstLine(String strText) {
            if (strText == null)
                return null;
            int numEnd = strText.indexOf('\n');
            return numEnd < 0 ? strText : strText.substring(0, numEnd);
        }

    }

}
