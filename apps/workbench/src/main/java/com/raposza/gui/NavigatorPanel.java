// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.model.Contract;
import com.raposza.api.model.PartyInfo;
import com.raposza.api.model.UserInfo;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * What is on the participant, down the left hand side: users, parties, the
 * templates seen and the contracts under them.
 *
 * The point of the pane is that using the tool no longer requires knowing an
 * identifier before you start. The search box answers "what is this string";
 * this answers "what is here".
 *
 * The filter box filters WHAT WAS LOADED, not the ledger. It is a text match
 * over the labels and it never goes back to the participant, so a party that
 * exists but was not read will not appear no matter what is typed. Reload is a
 * separate button for exactly that reason - a filter that silently re-queried
 * would make a capped read look like a complete one.
 *
 * Author Claude/bentzn
 */
public final class NavigatorPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Bound on how much a filtered tree opens, so a wide match stays usable. */
    private static final int CNT_ROW_EXPAND = 400;

    /**
     * Room for the expand handles, the indent and a scrollbar. The only
     * part of the width that is not measured, because none of the three is
     * a thing the pane can ask about before it is laid out.
     */
    private static final int CNT_GUTTER = 96;

    /**
     * How the Contracts section is ordered: the template as the row shows it,
     * then the contract id.
     *
     * THE PARTICIPANT'S OWN ORDER IS NOT AN ORDER. It returns the active set
     * however its index happens to hold it, which puts the three CpRecords of
     * one batch in three different places in a list of twenty-seven and gives
     * the reader no way to tell whether the one they want is above or below.
     * The id is the tie-break because it is what the row's second column
     * shows, so the sort matches what is on screen rather than something
     * behind it.
     */
    private static final Comparator<Contract> COMP_CONTRACT =
            Comparator.comparing(NavigatorPanel::strTemplateOf)
                    .thenComparing(NavigatorPanel::strIdOf);

    private final DefaultMutableTreeNode nodeRoot = new DefaultMutableTreeNode("ledger");
    private final DefaultTreeModel model = new DefaultTreeModel(nodeRoot);
    private final JTree tree = new JTree(model);

    private transient LedgerSnapshot snapshot;
    private transient Consumer<NavItem> listenerSelect;

    /** Set while the selection is being moved by code rather than by a click. */
    private transient boolean flagQuiet;


    public NavigatorPanel() {
        super(new BorderLayout());

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        // MONOSPACED, because the contract rows pad a template name to a
        // fixed column and a proportional font makes that padding land
        // somewhere different on every row.
        tree.setFont(GuiDesign.fontMonoAt(tree.getFont().getSize()));
        tree.setCellRenderer(new ItemRenderer());
        ToolTipManager.sharedInstance().registerComponent(tree);

        tree.addTreeSelectionListener(ev -> fire());

        // THE FILTER BOX AND RELOAD ARE GONE - operator instruction,
        // 2026-08-24. Both were controls over a snapshot the window now
        // re-reads whenever the session's user changes, and a filter over
        // what happens to be loaded was the box most often mistaken for a
        // ledger search.

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setPreferredSize(GuiScale.dim(340, 400));

        setBorder(GuiScale.border(8, 8, 4, 4));
        add(scroll, BorderLayout.CENTER);

        setEnabledControls(false);
    }


    /** @param listener called with the selected item, never with null */
    public void onSelect(Consumer<NavItem> listener) {
        this.listenerSelect = listener;
    }



    /**
     * Selects the node standing for an identifier, if the tree holds one.
     *
     * WITHOUT FIRING THE SELECTION LISTENER. The caller has just rendered the
     * object through a different renderer - a resolution says CONTRACT and then
     * shows the payload - and letting the tree's own listener run would
     * overwrite that with the navigator's version of the same thing, one frame
     * later, for no reason the operator could see.
     *
     * A false answer is not an error. The filter box may be hiding the node, or
     * the object may not have been read at all: an archived contract resolves
     * against the participant and was never in this tree. The detail pane
     * already holds the answer, so nothing is said about it.
     *
    /**
     * @return the point size the rows are drawn at, so a pane beside this one
     *         can match it instead of picking a number that looks close
     */
    public int cntFontSize() {
        return tree.getFont().getSize();
    }


    /**
     * The node standing for an identifier, without touching the selection.
     *
     * This is what lets a link open exactly what a click on the row would have
     * opened, rather than a second rendering of the same object built from a
     * second read.
     *
     * @param strId a contract id, party id or template identifier
     * @return the item, or null when this tree does not hold it
     */
    public NavItem item(String strId) {
        if (strId == null || strId.isBlank())
            return null;

        DefaultMutableTreeNode node = find(nodeRoot, strId.trim());
        return node != null && node.getUserObject() instanceof NavItem item ? item : null;
    }


    /**
     * Drops the selection.
     *
     * A row left highlighted after the pane moved somewhere else is the tree
     * claiming to show what the pane shows, and it is the one thing in this
     * window that cannot be checked at a glance.
     */
    public void clear() {
        flagQuiet = true;
        try {
            tree.clearSelection();
        }
        finally {
            flagQuiet = false;
        }
    }


    /**
     * @param strId a contract id, party id or template identifier
     * @return true when a node was found and selected
     */
    public boolean select(String strId) {
        if (strId == null || strId.isBlank())
            return false;

        DefaultMutableTreeNode node = find(nodeRoot, strId.trim());
        if (node == null)
            return false;

        TreePath path = new TreePath(node.getPath());
        flagQuiet = true;
        try {
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        }
        finally {
            flagQuiet = false;
        }
        return true;
    }


    /**
     * @param nodeFrom where to start
     * @param strId what to look for
     * @return the first matching node in tree order, null when there is none
     */
    private static DefaultMutableTreeNode find(DefaultMutableTreeNode nodeFrom, String strId) {
        for (int idxChild = 0; idxChild < nodeFrom.getChildCount(); idxChild++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodeFrom.getChildAt(idxChild);
            if (node.getUserObject() instanceof NavItem item && standsFor(item, strId))
                return node;

            DefaultMutableTreeNode nodeDeep = find(node, strId);
            if (nodeDeep != null)
                return nodeDeep;
        }
        return null;
    }


    /**
     * Matches on IDENTITY, never on the label - which is what separates this
     * from matches() above it, the filter's loose match over labels and
     * tooltips. Two methods with opposite rules must not share a name.
     * A label is truncated, may be
     * package-qualified and may be a display name; two of the three would match
     * the wrong node and the third would match nothing.
     *
     * @param item the node's item
     * @param strId a contract id, party id or template identifier
     * @return true when this item stands for that identifier
     */
    static boolean standsFor(NavItem item, String strId) {
        return switch (item) {
            case NavItem.Ct val -> strId.equals(val.contract().idContract());
            case NavItem.Party val -> strId.equals(val.party().idParty());
            case NavItem.Template val -> strId.equals(val.group().idTemplate().toString())
                    || strId.equals(val.group().idTemplate().shortName());
            case NavItem.User val -> strId.equals(val.user().idUser());
            case NavItem.Group val -> false;
        };
    }


    /**
     * Kept so the window can still ask for a re-read; nothing in this panel
     * raises it since the Reload button went.
     *
     * @param listener what would have been called
     */
    public void onReload(ActionListener listener) {
        // no control raises this any more
    }


    /**
     * How wide this pane has to be for a contract row to arrive whole.
     *
     * MEASURED, not a number someone tried until it looked right. The rows are
     * monospaced, so the width is the template column plus a shortened id in
     * character widths off the tree's own font - which is the only thing that
     * knows the display's scaling. A fixed pixel count is correct on the
     * machine it was picked on and clips the last four characters everywhere
     * else, and those four characters are the half of the id that
     * distinguishes one contract from another.
     *
     * @return the width in pixels, gutter included
     */
    public int cntWidthWanted() {
        FontMetrics metrics = tree.getFontMetrics(tree.getFont());
        return metrics.charWidth('0')
                * (NavItem.CNT_TEMPLATE_COL + ShortIds.CNT_ID_SHOWN) + CNT_GUTTER;
    }


    /**
     * @param snapshotNew what was just read, or null to empty the pane
     */
    public void setSnapshot(LedgerSnapshot snapshotNew) {
        this.snapshot = snapshotNew;
        setEnabledControls(snapshotNew != null);
        rebuild();
    }


    private void setEnabledControls(boolean flagOn) {
        tree.setEnabled(flagOn);
    }


    private void rebuild() {
        nodeRoot.removeAllChildren();
        if (snapshot != null) {
            DefaultMutableTreeNode nodeBuilt = buildRoot(snapshot, "");
            while (nodeBuilt.getChildCount() > 0) {
                nodeRoot.add((DefaultMutableTreeNode) nodeBuilt.getChildAt(0));
            }
        }
        model.reload();
        expand();
    }


    /**
     * The four sections open, their contents closed. With a filter typed
     * everything opens instead, bounded, because a match hidden three levels
     * down reads as no match at all.
     */
    private void expand() {
        for (int idxChild = 0; idxChild < nodeRoot.getChildCount(); idxChild++) {
            tree.expandPath(new TreePath(new Object[] { nodeRoot, nodeRoot.getChildAt(idxChild) }));
        }


        for (int idxRow = 0; idxRow < tree.getRowCount() && idxRow < CNT_ROW_EXPAND; idxRow++) {
            tree.expandRow(idxRow);
        }
    }


    private void fire() {
        if (listenerSelect == null || flagQuiet)
            return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null || !(node.getUserObject() instanceof NavItem item))
            return;

        listenerSelect.accept(item);
    }


    /**
     * Finishes a section and hangs it on the root.
     *
     * <h2>Every section is always there</h2>
     *
     * It used to be dropped when the call behind it had a note on it, which
     * silently defeated the note: NavItem.Group carries a note precisely so a
     * refusal or a cap can be READ off the heading, and a section that is
     * removed carries its explanation away with it.
     *
     * <h2>A REFUSED section counts (-), not (0)</h2>
     *
     * Zero is an answer about the ledger - there are no users - and a refusal
     * is the absence of one. Printing 0 for both is the confusion this whole
     * arrangement exists to end, so a refused call gets a dash and the reason
     * moves to the tooltip, where it does not push the tree three screens wide.
     *
     * A CAP IS NOT A REFUSAL. The count is real, so it is printed, and the cap
     * text stays on the heading because a truncated list that does not say so
     * is a wrong answer.
     *
     * <h2>The count is what is SHOWN, not what was read</h2>
     *
     * With a filter typed, a heading counting the whole snapshot would
     * disagree with the rows under it. So the node is filled first and labelled
     * afterwards.
     *
     * @param root where sections go
     * @param node the section, already filled
     * @param strLabel what the section is
     * @param strProblem why the call behind it returned nothing, null when it
     *        answered
     * @param strNote a note to show on the heading, null when there is none
     */
    private static void head(DefaultMutableTreeNode root, DefaultMutableTreeNode node,
            String strLabel, String strProblem, String strNote) {

        boolean flagRefused = strProblem != null && !strProblem.isBlank();
        String strCount = flagRefused ? "-" : String.valueOf(node.getChildCount());
        node.setUserObject(new NavItem.Group(strLabel + " (" + strCount + ")",
                flagRefused ? null : strNote, flagRefused ? strProblem : strNote));
        root.add(node);
    }


    /**
     * Builds the tree under an invisible root. Static and free of components so
     * the shape of the tree can be asserted without a display.
     *
     * Sections always appear, including empty ones. A missing "Users" section
     * and a "Users" section holding nothing look identical in a screenshot and
     * mean entirely different things.
     *
     * @param snap what was read
     * @param strFilter substring to match against labels, case insensitive;
     *        blank means everything. A section heading is never filtered out
     * @return the root; its children are the four sections
     */
    static DefaultMutableTreeNode buildRoot(LedgerSnapshot snap, String strFilter) {
        String strNeedle = strFilter == null ? "" : strFilter.trim().toLowerCase(Locale.ROOT);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("ledger");

        DefaultMutableTreeNode nodeUser = new DefaultMutableTreeNode();
        for (UserInfo user : snap.lstUser()) {
            NavItem.User item = new NavItem.User(user);
            if (matches(item, strNeedle))
                nodeUser.add(new DefaultMutableTreeNode(item));
        }
        head(root, nodeUser, "Users", snap.strProblemUser(), null);

        DefaultMutableTreeNode nodeParty = new DefaultMutableTreeNode();
        Map<String, String> mapPartyLabel = mapLabelOf(snap.lstParty());
        for (PartyInfo party : snap.lstParty()) {
            NavItem.Party item =
                    new NavItem.Party(party, mapPartyLabel.get(party.idParty()));
            if (matches(item, strNeedle))
                nodeParty.add(new DefaultMutableTreeNode(item));
        }
        head(root, nodeParty, "Parties", snap.strProblemParty(), null);

        String strNote = noteContract(snap);

        DefaultMutableTreeNode nodeTemplate = new DefaultMutableTreeNode();
        // A TEMPLATE IS A LEAF. Its contracts were repeated underneath it and
        // again under `Contracts`, so every contract appeared twice and the
        // section was twice as tall as the ledger. Selecting a template shows
        // its signature, which is the thing a template has that a contract
        // does not.
        for (LedgerSnapshot.TemplateGroup group : snap.lstTemplate()) {
            NavItem.Template item = new NavItem.Template(group);
            if (matches(item, strNeedle))
                nodeTemplate.add(new DefaultMutableTreeNode(item));
        }
        head(root, nodeTemplate, "Templates seen", snap.strProblemContract(),
                strNote);

        DefaultMutableTreeNode nodeContract = new DefaultMutableTreeNode();
        List<Contract> lstContract = new ArrayList<>(snap.lstContract());
        lstContract.sort(COMP_CONTRACT);
        for (Contract contract : lstContract) {
            NavItem.Ct item = new NavItem.Ct(contract);
            if (matches(item, strNeedle))
                nodeContract.add(new DefaultMutableTreeNode(item));
        }
        head(root, nodeContract, "Contracts", snap.strProblemContract(), strNote);

        return root;
    }


    /**
     * @param contract the contract
     * @return the template as the contract row shows it, never null
     */
    private static String strTemplateOf(Contract contract) {
        if (contract.idTemplate() == null || contract.idTemplate().shortName() == null)
            return "";
        return contract.idTemplate().shortName();
    }


    /**
     * @param contract the contract
     * @return its id, never null
     */
    private static String strIdOf(Contract contract) {
        return contract.idContract() == null ? "" : contract.idContract();
    }


    /**
     * @param snap the snapshot
     * @return the note both contract-bearing sections carry, null when the read
     *         was clean. The cap is reported wherever contracts are counted,
     *         because a truncated list that does not say so is a wrong answer
     */
    static String noteContract(LedgerSnapshot snap) {
        if (snap.strProblemContract() != null)
            return snap.strProblemContract();
        if (snap.flagCapped())
            return "capped at " + snap.cntLimit() + ", there may be more";
        return null;
    }


    /**
     * @param item the item
     * @param strNeedle already trimmed and lower cased
     * @return true when the label or the tooltip contains the needle
     */
    static boolean matches(NavItem item, String strNeedle) {
        if (strNeedle.isEmpty())
            return true;

        String strLabel = item.label() == null ? "" : item.label().toLowerCase(Locale.ROOT);
        if (strLabel.contains(strNeedle))
            return true;

        String strTip = item.tip() == null ? "" : item.tip().toLowerCase(Locale.ROOT);
        return strTip.contains(strNeedle);
    }


    /**
     * Labels and tooltips come from the item, so a node never holds a formatted
     * string of its own.
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
            if (objUser instanceof NavItem item) {
                setText(item.label());
                setToolTipText(item.tip());
            }
            else {
                setToolTipText(null);
            }
            return this;
        }

    }


    /**
     * A short label per party that stays UNAMBIGUOUS.
     *
     * <h2>Why the namespace cannot simply be dropped</h2>
     *
     * A party id is `Alice-9b3970be::1220ce...` and the half after the
     * separator is a namespace fingerprint. On a Sandbox that allocated every
     * party itself it is the same string on all of them, so it distinguishes
     * nothing and costs the width of the pane. On a real participant it is the
     * opposite: `ListKnownParties` returns parties hosted ELSEWHERE too, and
     * two of them can share a hint - at which point dropping the namespace
     * makes two different parties render as one row. That is a wrong answer,
     * not a terse one.
     *
     * So the head is used only while it is unique, and a colliding group gets
     * back as much of the namespace as it takes to separate its members - four
     * characters, then eight, and the whole id if it comes to that. Parties
     * that do not collide are unaffected by ones that do.
     *
     * THE FULL ID IS ALWAYS IN THE TOOLTIP, so nothing here can make an id
     * unreachable - only shorter.
     *
     * @param lstParty every party in the section
     * @return party id to label
     */
    static Map<String, String> mapLabelOf(List<PartyInfo> lstParty) {
        Map<String, List<String>> mapGroup = new LinkedHashMap<>();
        for (PartyInfo party : lstParty) {
            mapGroup.computeIfAbsent(NavItem.strBeforeNamespace(party.idParty()),
                    strKey -> new ArrayList<>()).add(party.idParty());
        }

        Map<String, String> mapOut = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : mapGroup.entrySet()) {
            List<String> lstId = entry.getValue();
            if (lstId.size() == 1) {
                mapOut.put(lstId.get(0), entry.getKey());
                continue;
            }
            for (String idParty : lstId) {
                mapOut.put(idParty, strWithEnough(idParty, lstId));
            }
        }
        return mapOut;
    }


    /**
     * @param idParty the party to label
     * @param lstId every id sharing its head
     * @return the head plus the shortest namespace prefix that is unique
     */
    private static String strWithEnough(String idParty, List<String> lstId) {
        String strTail = strNamespaceOf(idParty);
        for (int cntKeep = 4; cntKeep < strTail.length(); cntKeep += 4) {
            String strTry = strTail.substring(0, cntKeep);
            int cntSame = 0;
            for (String idOther : lstId) {
                if (strNamespaceOf(idOther).startsWith(strTry))
                    cntSame++;
            }
            if (cntSame == 1) {
                return NavItem.strBeforeNamespace(idParty) + "::" + strTry
                        + "\u2026";
            }
        }
        return idParty;
    }


    /**
     * @param idParty the party id
     * @return everything after `::`, or empty when there is none
     */
    private static String strNamespaceOf(String idParty) {
        if (idParty == null)
            return "";

        int idxSep = idParty.indexOf("::");
        return idxSep < 0 ? "" : idParty.substring(idxSep + 2);
    }

}
