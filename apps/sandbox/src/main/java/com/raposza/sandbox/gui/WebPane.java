// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.runtime.localnet.LocalNetUi;
import com.raposza.sandbox.app.RawarSites;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * EVERY PAGE THIS WINDOW SERVES, and where it is: the Web tab.
 *
 * <h2>A top-level tab, on both topologies</h2>
 *
 * His instruction, 2026-10-02: "Move the tab 'Web UIs' to the top level.
 * Rename to 'Web'. Keep the name, the node it belongs to and the URL in the
 * table." It was a sub-tab of OIDC on LocalNetND only - `todo.md` A-40 - when
 * the only pages were Splice's. The RAWARs - `rawar.md` section 5 - are pages
 * on BOTH topologies, so the tab is now on both. The user names and passwords
 * moved to OIDC's Users tab - {@link UsersPane}.
 *
 * <h2>URLs, not ports</h2>
 *
 * LocalNetND's UIs are host-routed virtual hosts - `LocalNetUi` - and a RAWAR
 * is a mount, so a port alone reaches neither. Each row is the URL to open.
 *
 * <h2>Two tabs, Endpoints and Log - his instruction, 2026-10-04</h2>
 *
 * "We need a log pane on 'Web' tab as well to show all access", and then "I
 * want a 'Log' tab! What is already there should be in a tab called
 * 'Endpoints'". The Log takes a line per request from LocalNetND's UI server
 * and from the RAWAR server, worded by {@link WebAccess}. A page that polls
 * is shown once and then hidden while it keeps polling - {@link WebRepeats},
 * behind the `Hide repeats` box. It starts unwrapped, for the reason
 * `LogPane` gives.
 *
 * Author Claude/bentzn
 */
public final class WebPane extends JPanel {

    private static final long serialVersionUID = 1L;

    /** The node column of a page that belongs to no one node. */
    static final String STR_NODE_NONE = "-";

    /** The name of a JSON Ledger API row. */
    static final String STR_NAME_JSON = "JSON Ledger API";

    /** What a RAWAR row's name is prefixed with, so it reads as one at a glance. */
    static final String STR_PREFIX_RAWAR = "RAWAR ";

    /**
     * THE NAMES ARE *.localhost. Browsers answer those on the loopback address
     * themselves, which is why a Windows machine that resolves no .localhost
     * name still opens every page - `LocalNetWebTest`. A tool that asks the
     * operating system's resolver instead - curl on some systems - may need
     * them in the hosts file.
     */
    static final String STR_HINT = "The *.localhost names are answered by the browser itself."
            + " A tool that asks the system resolver may need them in the hosts file.";

    private static final String[] ARR_COLUMN = { "Name", "Node", "URL" };

    /** The name column - a click reads it to tell a RAWAR. */
    private static final int IDX_COL_NAME = 0;

    /** The URL column - Open and Copy read it. */
    private static final int IDX_COL_URL = 2;

    /**
     * One row.
     *
     * @param strName what it is
     * @param strNode the node it belongs to
     * @param strUrl where it is
     */
    public record Row(String strName, String strNode, String strUrl) {
    }


    private final DefaultTableModel model = new DefaultTableModel(ARR_COLUMN, 0) {

        private static final long serialVersionUID = 1L;


        @Override
        public boolean isCellEditable(int idxRow, int idxCol) {
            return false;
        }
    };

    private final transient JTable table = new JTable(model);

    /**
     * OFF, with the reason on it - his instruction, 2026-10-04: it did not open
     * a browser on his machine. A click on a row copies its URL, which is the
     * route that works; this stays visible so the reader is told where the
     * browser went rather than finding it gone. `Copy URL` is gone outright,
     * the click does what it did.
     */
    private final JButton btnOpen = new JButton("Open");

    private final JButton btnRefresh = new JButton("Refresh");

    private final JLabel lblState = new JLabel(" ");

    /** Every request to a page on Endpoints. */
    private final LogPane logAccess = new LogPane();

    private final transient WebRepeats repeats = new WebRepeats();

    private final JCheckBox chkRepeats = new JCheckBox("Hide repeats", true);

    /** The box, readable from the threads that feed the Log. */
    private volatile boolean flagHideRepeats = true;

    private transient Runnable runRefresh;


    public WebPane() {
        super(new BorderLayout());
        setOpaque(false);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(GuiTheme.scale(22));

        // A CLICK ON A ROW COPIES ITS URL - his instruction, 2026-10-02, for
        // RAWARs, and 2026-10-04 for every row: the gate on the Name column
        // read as "it only happens with some lines".
        table.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent evt) {
                int idxRow = table.rowAtPoint(evt.getPoint());
                if (idxRow < 0)
                    return;
                int idxModel = table.convertRowIndexToModel(idxRow);
                copy(String.valueOf(model.getValueAt(idxModel, IDX_COL_URL)));
            }
        });
        btnOpen.setEnabled(false);
        btnOpen.setToolTipText("Click a row to copy its URL, and paste it into a browser");
        // A RAWAR ADDED ON DISK is served on the next request, with nothing
        // restarted - so the list that names them has to be able to catch up
        // without a restart either.
        btnRefresh.addActionListener(evt -> {
            if (runRefresh != null)
                runRefresh.run();
        });

        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiTheme.scale(8), 0));
        pnlButtons.setOpaque(false);
        pnlButtons.add(btnOpen);
        pnlButtons.add(btnRefresh);
        lblState.setForeground(GuiTheme.colMuted());
        pnlButtons.add(lblState);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(GuiTheme.colCardBorder(), 1, true));

        JLabel lblHint = new JLabel(STR_HINT);
        lblHint.setForeground(GuiTheme.colMuted());

        JPanel pnlInner = new JPanel(new BorderLayout(0, GuiTheme.scale(GuiTheme.N_GAP)));
        pnlInner.setOpaque(false);
        pnlInner.add(pnlButtons, BorderLayout.NORTH);
        pnlInner.add(scroll, BorderLayout.CENTER);
        pnlInner.add(lblHint, BorderLayout.SOUTH);

        logAccess.useClock(true);
        logAccess.useWrap(false);
        chkRepeats.setToolTipText("Hide a request identical to one in the last "
                + WebRepeats.N_MS_WINDOW / 1000L + " s - a page polling");
        chkRepeats.addActionListener(evt -> flagHideRepeats = chkRepeats.isSelected());
        logAccess.addControl(chkRepeats);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Endpoints", GuiTheme.card("Web pages and endpoints", pnlInner));
        tabs.addTab("Log", logAccess);
        add(tabs, BorderLayout.CENTER);
        show(List.of(), "nothing is served");
    }


    /**
     * @param runRefreshNew what Refresh does - the window rebuilds the rows
     */
    public void useRefresh(Runnable runRefreshNew) {
        this.runRefresh = runRefreshNew;
    }


    /**
     * LocalNetND's pages and its participants' JSON Ledger APIs. Built without
     * Swing so it can be asserted.
     *
     * @param lstPage what the runner serves, in its order
     * @param mapJson node to its JSON Ledger API url, in display order
     * @return the rows
     */
    static List<Row> lstRowLocalNet(List<LocalNetUi.Page> lstPage, Map<String, String> mapJson) {
        List<Row> lstOut = new ArrayList<>();
        for (LocalNetUi.Page page : lstPage) {
            lstOut.add(new Row(page.strTitle(), page.strRole() == null ? STR_NODE_NONE
                    : page.strRole(), page.strUrl()));
        }
        lstOut.addAll(lstRowJson(mapJson));
        return lstOut;
    }


    /**
     * @param mapJson node to its JSON Ledger API url
     * @return one row each
     */
    static List<Row> lstRowJson(Map<String, String> mapJson) {
        List<Row> lstOut = new ArrayList<>();
        for (Map.Entry<String, String> entry : mapJson.entrySet()) {
            lstOut.add(new Row(STR_NAME_JSON, entry.getKey(), entry.getValue()));
        }
        return lstOut;
    }


    /**
     * The RAWARs being served.
     *
     * @param scan what the RAWAR server serves now
     * @param nPort its port
     * @param strNode the node their `_ledger/` forwards to
     * @return one row per RAWAR
     */
    static List<Row> lstRowRawar(RawarSites.Scan scan, int nPort, String strNode) {
        List<Row> lstOut = new ArrayList<>();
        for (RawarSites.Site site : scan.lstSite()) {
            lstOut.add(new Row(STR_PREFIX_RAWAR + site.strName(), strNode,
                    "http://127.0.0.1:" + nPort + site.strMount()));
        }
        return lstOut;
    }


    /**
     * Safe from any thread.
     *
     * @param strLine one request, worded by {@link WebAccess}; null is ignored
     */
    public void access(String strLine) {
        if (strLine == null)
            return;
        // RECORDED EITHER WAY, so turning the box back on hides a poll that
        // ran while it was off.
        boolean flagNew = repeats.isNew(strLine);
        if (flagNew || !flagHideRepeats)
            logAccess.append(strLine);
    }


    /**
     * @param lstRow the rows, in display order
     * @param strState what the state label says, or null for nothing
     */
    public void show(List<Row> lstRow, String strState) {
        model.setRowCount(0);
        for (Row row : lstRow) {
            model.addRow(new Object[] { row.strName(), row.strNode(), row.strUrl() });
        }
        lblState.setText(strState == null || strState.isBlank() ? " " : strState);
    }


    /**
     * @param strName a Name cell
     * @return whether that row is a RAWAR
     */
    static boolean isRawar(String strName) {
        return strName != null && strName.startsWith(STR_PREFIX_RAWAR);
    }


    private void copy(String strUrl) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(strUrl), null);
        lblState.setText("copied " + strUrl);
    }
}
