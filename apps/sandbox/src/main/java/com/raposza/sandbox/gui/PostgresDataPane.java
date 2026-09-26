// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.runtime.db.PostgresCoordinates;
import com.raposza.sandbox.app.SandboxService;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * What is actually in the embedded server: every database, its schemas, their
 * base tables, and the first rows of whichever table is selected.
 *
 * <h2>Three levels, not two</h2>
 *
 * The tree was database then `schema.table`, which put ninety tables in one
 * flat list under `participant` and repeated `ledger_api.` on every one of
 * them. The schema is a level of its own now: a Canton database has few
 * schemas and many tables, so that is where the cut belongs.
 *
 * <h2>Read straight through JDBC, not through anything of ours</h2>
 *
 * The coordinates come from {@link SandboxService}, which gets them from
 * `SandboxPostgres`, so this pane cannot disagree with what Canton and scribe
 * were told to connect to. Everything after that is `information_schema` and a
 * bounded SELECT: nothing here knows what a ledger is, which is why it works
 * the same on both generations and on the PQS database.
 *
 * <h2>Every query is off the event dispatch thread</h2>
 *
 * A database on a laptop under a starting participant is not fast, and a tree
 * expansion that blocks the EDT freezes the whole window including the log it
 * is competing with. The refresh and the row read both run on a worker and hop
 * back.
 *
 * Author Claude/bentzn
 */
public final class PostgresDataPane extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Rows read from a selected table. The pane is a look, not an export. */
    private static final int CNT_ROW_MAX = 200;

    /** The database every server has, used to list the others. */
    private static final String STR_DB_ADMIN = "postgres";

    /** Sorted to the bottom, prefixed or not. */
    private static final String[] ARR_NODE_LAST =
            { "mediator", "sequencer", "sequencer_driver" };

    /** A column never gets narrower than this, or wider. */
    private static final int N_WIDTH_MIN = 60;

    private static final int N_WIDTH_MAX = 420;

    private final Supplier<SandboxService> supService;

    private final DefaultMutableTreeNode nodeRoot = new DefaultMutableTreeNode("databases");

    private final DefaultTreeModel modelTree = new DefaultTreeModel(nodeRoot);

    private final JTree tree = new JTree(modelTree);

    private final DefaultTableModel modelRow = new DefaultTableModel() {

        private static final long serialVersionUID = 1L;


        @Override
        public boolean isCellEditable(int nRow, int nColumn) {
            return false;
        }
    };

    private final JTable tableRow = new JTable(modelRow);

    private final JButton btnRefresh = new JButton("Refresh");

    private final JLabel lblNote = new JLabel(" ");


    /**
     * @param supServiceNew where the running service comes from; it is asked
     *        each time rather than held, because a pane built once outlives
     *        every stack the window starts
     */
    public PostgresDataPane(Supplier<SandboxService> supServiceNew) {
        super(new BorderLayout(GuiTheme.scale(GuiTheme.N_GAP), GuiTheme.scale(GuiTheme.N_GAP)));
        setOpaque(false);
        this.supService = supServiceNew;

        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setRootVisible(true);
        tree.addTreeSelectionListener(evt -> selected(evt.getNewLeadSelectionPath()));

        tableRow.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tableRow.setRowHeight(GuiTheme.scale(GuiTheme.N_ROW_HEIGHT));
        GuiTheme.mono(tableRow);
        tableRow.getTableHeader().setReorderingAllowed(false);
        tableRow.setFillsViewportHeight(true);

        btnRefresh.addActionListener(evt -> refresh());
        lblNote.setForeground(GuiTheme.colMuted());

        JPanel pnlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiTheme.scale(12),
                GuiTheme.scale(4)));
        pnlBar.setOpaque(false);
        pnlBar.add(btnRefresh);
        pnlBar.add(lblNote);

        JScrollPane scrollTree = new JScrollPane(tree);
        scrollTree.setBorder(BorderFactory.createLineBorder(GuiTheme.colCardBorder(), 1, true));
        JScrollPane scrollRow = new JScrollPane(tableRow);
        scrollRow.setBorder(BorderFactory.createLineBorder(GuiTheme.colCardBorder(), 1, true));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollTree, scrollRow);
        split.setBorder(null);
        split.setResizeWeight(0.32);
        split.setDividerSize(GuiTheme.scale(8));

        add(pnlBar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }


    /**
     * Empties the tree and the rows. Called when a stack stops, so the pane
     * never shows the tables of a server that is gone.
     */
    public void reset() {
        nodeRoot.removeAllChildren();
        modelTree.reload();
        modelRow.setRowCount(0);
        modelRow.setColumnCount(0);
        lblNote.setText(" ");
    }


    private void refresh() {
        SandboxService service = supService.get();
        if (service == null || !service.isRunning()) {
            reset();
            lblNote.setText("nothing is running");
            return;
        }

        PostgresCoordinates coordAdmin = service.coordinatesFor(STR_DB_ADMIN);
        if (coordAdmin == null) {
            reset();
            lblNote.setText("no PostgreSQL coordinates yet");
            return;
        }

        btnRefresh.setEnabled(false);
        lblNote.setText("reading " + coordAdmin.strHost() + ":" + coordAdmin.nPort() + " ...");

        Thread threadRead = new Thread(() -> {
            Map<String, Map<String, List<String>>> mapDatabase;
            String strError = null;
            try {
                mapDatabase = readCatalog(coordAdmin);
            }
            catch (SQLException | RuntimeException ex) {
                mapDatabase = new LinkedHashMap<>();
                strError = String.valueOf(ex.getMessage());
            }
            Map<String, Map<String, List<String>>> mapDone = mapDatabase;
            String strErrorDone = strError;
            SwingUtilities.invokeLater(() -> catalogDone(mapDone, strErrorDone));
        }, "sandbox-gui-pg-catalog");
        threadRead.setDaemon(true);
        threadRead.start();
    }


    /**
     * <h2>The template databases are not listed</h2>
     *
     * `template0` and `template1` are PostgreSQL's own prototypes, present on
     * every cluster and containing nothing this stack put there. `template0`
     * additionally refuses connections by design - `datallowconn` is false -
     * so listing it produced a tree node reading `FATAL: database "template0"
     * is not currently accepting connections`, which is an error message
     * standing in for a database nobody asked about. `NOT datistemplate`
     * removes both, and the `<unavailable>` node stays for the case it was
     * really for: a Canton node holding its own database during a start.
     *
     * <h2>`pg_catalog` and `information_schema` are never listed either</h2>
     *
     * They were behind a `System schemas` checkbox. The catalogue is the same
     * on every PostgreSQL in the world and none of it is this stack's; a
     * control whose ON position adds four hundred rows nobody reads is a
     * control that only has a wrong setting.
     *
     * @param coordAdmin where to ask which databases exist
     * @return database to schema to table names, in catalogue order
     * @throws SQLException when the server refuses
     */
    private static Map<String, Map<String, List<String>>> readCatalog(
            PostgresCoordinates coordAdmin) throws SQLException {
        List<String> lstDatabase = new ArrayList<>();
        try (Connection conn = connect(coordAdmin);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT datname FROM pg_database"
                        + " WHERE NOT datistemplate ORDER BY 1")) {
            while (rs.next()) {
                lstDatabase.add(rs.getString(1));
            }
        }
        lstDatabase.sort(PostgresDataPane::compareDatabase);

        Map<String, Map<String, List<String>>> mapOut = new LinkedHashMap<>();
        for (String strDatabase : lstDatabase) {
            Map<String, List<String>> mapSchema = new LinkedHashMap<>();
            String strQuery = "SELECT table_schema, table_name FROM information_schema.tables"
                    + " WHERE table_type = 'BASE TABLE'"
                    + " AND table_schema NOT IN ('pg_catalog', 'information_schema')"
                    + " ORDER BY 1, 2";
            try (Connection conn = connect(coordAdmin.withDatabase(strDatabase));
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(strQuery)) {
                while (rs.next()) {
                    mapSchema.computeIfAbsent(rs.getString(1), strKey -> new ArrayList<>())
                            .add(rs.getString(2));
                }
            }
            catch (SQLException ex) {
                // One database that will not open is not a reason to lose the
                // rest: a template database refuses connections outright, and
                // a Canton node holds its own for parts of a start.
                mapSchema.put("<unavailable: " + ex.getMessage() + ">", new ArrayList<>());
            }
            mapOut.put(strDatabase, mapSchema);
        }
        return mapOut;
    }


    /**
     * The three that are Canton's own plumbing go LAST. A stack has one
     * database anyone opens on purpose - the participant - and three the
     * sequencer and mediator keep for themselves; alphabetical order put two
     * of those above it.
     *
     * @param strLeft one database name
     * @param strRight another
     * @return the ordering
     */
    private static int compareDatabase(String strLeft, String strRight) {
        int nRank = Integer.compare(rankOf(strLeft), rankOf(strRight));
        return nRank != 0 ? nRank : strLeft.compareTo(strRight);
    }


    /**
     * @param strDatabase a database name
     * @return 1 for the plumbing, 0 for everything else
     */
    private static int rankOf(String strDatabase) {
        for (String strTail : ARR_NODE_LAST) {
            if (strDatabase.equals(strTail) || strDatabase.endsWith("_" + strTail))
                return 1;
        }
        return 0;
    }


    private void catalogDone(Map<String, Map<String, List<String>>> mapDatabase,
            String strError) {
        nodeRoot.removeAllChildren();
        int cntTable = 0;
        for (Map.Entry<String, Map<String, List<String>>> entryDatabase : mapDatabase.entrySet()) {
            DefaultMutableTreeNode nodeDatabase =
                    new DefaultMutableTreeNode(entryDatabase.getKey());
            for (Map.Entry<String, List<String>> entrySchema
                    : entryDatabase.getValue().entrySet()) {
                DefaultMutableTreeNode nodeSchema = new DefaultMutableTreeNode(
                        new Schema(entrySchema.getKey(), entrySchema.getValue().size()));
                for (String strTable : entrySchema.getValue()) {
                    nodeSchema.add(new DefaultMutableTreeNode(strTable));
                    cntTable++;
                }
                nodeDatabase.add(nodeSchema);
            }
            nodeRoot.add(nodeDatabase);
        }
        modelTree.reload();
        // COLLAPSED. Ninety tables under one schema is several screens, and
        // the one being looked for is never on the first of them.
        tree.expandRow(0);

        btnRefresh.setEnabled(true);
        lblNote.setText(strError == null
                ? mapDatabase.size() + " database(s), " + cntTable + " table(s)"
                : "failed: " + strError);
    }


    private void selected(TreePath path) {
        // databases / database / schema / table
        if (path == null || path.getPathCount() != 4)
            return;

        String strDatabase = String.valueOf(((DefaultMutableTreeNode) path.getPathComponent(1))
                .getUserObject());
        Object objSchema = ((DefaultMutableTreeNode) path.getPathComponent(2)).getUserObject();
        if (!(objSchema instanceof Schema))
            return;
        String strSchema = ((Schema) objSchema).strName;
        String strTable = String.valueOf(((DefaultMutableTreeNode) path.getPathComponent(3))
                .getUserObject());

        SandboxService service = supService.get();
        if (service == null || !service.isRunning())
            return;
        PostgresCoordinates coord = service.coordinatesFor(strDatabase);
        if (coord == null)
            return;

        String strQualified = strSchema + "." + strTable;
        lblNote.setText("reading " + strQualified + " ...");

        Thread threadRows = new Thread(() -> {
            List<String> lstColumn = new ArrayList<>();
            List<Object[]> lstRow = new ArrayList<>();
            String strError = null;
            try {
                readRows(coord, strSchema, strTable, lstColumn, lstRow);
            }
            catch (SQLException | RuntimeException ex) {
                strError = String.valueOf(ex.getMessage());
            }
            String strErrorDone = strError;
            SwingUtilities.invokeLater(() -> rowsDone(strQualified, lstColumn, lstRow,
                    strErrorDone));
        }, "sandbox-gui-pg-rows");
        threadRows.setDaemon(true);
        threadRows.start();
    }


    private static void readRows(PostgresCoordinates coord, String strSchema, String strTable,
            List<String> lstColumn, List<Object[]> lstRow) throws SQLException {
        // Quoted, and the quotes are doubled inside. Canton's own table names
        // are lower case and would not need it; scribe's schema is not this
        // pane's to assume, and an identifier cannot be a bind parameter.
        String strQuery = "SELECT * FROM " + quote(strSchema) + "." + quote(strTable)
                + " LIMIT " + CNT_ROW_MAX;
        try (Connection conn = connect(coord);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(strQuery)) {
            ResultSetMetaData meta = rs.getMetaData();
            int cntColumn = meta.getColumnCount();
            for (int cntCol = 1; cntCol <= cntColumn; cntCol++) {
                lstColumn.add(meta.getColumnLabel(cntCol));
            }
            while (rs.next()) {
                Object[] arrCell = new Object[cntColumn];
                for (int cntCol = 1; cntCol <= cntColumn; cntCol++) {
                    Object objValue = rs.getObject(cntCol);
                    arrCell[cntCol - 1] = objValue == null ? "" : String.valueOf(objValue);
                }
                lstRow.add(arrCell);
            }
        }
    }


    private void rowsDone(String strQualified, List<String> lstColumn, List<Object[]> lstRow,
            String strError) {
        modelRow.setRowCount(0);
        modelRow.setColumnCount(0);
        if (strError != null) {
            lblNote.setText(strQualified + " failed: " + strError);
            return;
        }
        for (String strColumn : lstColumn) {
            modelRow.addColumn(strColumn);
        }
        for (Object[] arrCell : lstRow) {
            modelRow.addRow(arrCell);
        }
        sizeColumns();
        lblNote.setText(strQualified + ": " + lstRow.size() + " row(s), limit " + CNT_ROW_MAX);
    }


    /**
     * Every column as wide as what is in it, clamped.
     *
     * `AUTO_RESIZE_OFF` gives each column the same 75 pixels, which turned a
     * flyway history into nine columns of ellipsis - `ins...`, `ver...`,
     * `de...` - and the table said nothing at all. The width is MEASURED from
     * the renderer rather than guessed from a character count, because the
     * font is proportional in the header and monospaced in the cells.
     */
    private void sizeColumns() {
        for (int cntCol = 0; cntCol < tableRow.getColumnCount(); cntCol++) {
            TableColumn column = tableRow.getColumnModel().getColumn(cntCol);
            int nWidth = N_WIDTH_MIN;

            TableCellRenderer rendererHead = tableRow.getTableHeader().getDefaultRenderer();
            Component compHead = rendererHead.getTableCellRendererComponent(tableRow,
                    column.getHeaderValue(), false, false, -1, cntCol);
            nWidth = Math.max(nWidth, compHead.getPreferredSize().width);

            for (int cntRow = 0; cntRow < tableRow.getRowCount(); cntRow++) {
                TableCellRenderer renderer = tableRow.getCellRenderer(cntRow, cntCol);
                Component comp = tableRow.prepareRenderer(renderer, cntRow, cntCol);
                nWidth = Math.max(nWidth, comp.getPreferredSize().width);
                if (nWidth >= N_WIDTH_MAX)
                    break;
            }
            column.setPreferredWidth(Math.min(nWidth + GuiTheme.scale(12),
                    GuiTheme.scale(N_WIDTH_MAX)));
        }
    }


    private static Connection connect(PostgresCoordinates coord) throws SQLException {
        return DriverManager.getConnection(coord.jdbcUrl(), coord.strUser(), coord.strPassword());
    }


    private static String quote(String strName) {
        return "\"" + strName.replace("\"", "\"\"") + "\"";
    }


    /** A schema and how many tables it has, so the count is on the node. */
    private static final class Schema {

        private final String strName;

        private final int cntTable;


        Schema(String strNameNew, int cntTableNew) {
            this.strName = strNameNew;
            this.cntTable = cntTableNew;
        }


        @Override
        public String toString() {
            return strName + "  (" + cntTable + ")";
        }
    }

}
