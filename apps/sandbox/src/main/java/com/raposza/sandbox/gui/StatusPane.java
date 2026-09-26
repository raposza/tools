// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.sandbox.app.ReadyReport;

import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * What the stack is, while it is up.
 *
 * The table is {@link ReadyReport} and nothing else - the same map that is
 * printed to the terminal and written to `sandbox.properties`. A window that
 * assembled its own view of the ports would be a third rendering of those
 * facts and the one nobody would think to check against the file.
 *
 * <h2>The status file is a ROW, not a label</h2>
 *
 * Its path used to sit beside the chip with a Copy button that took the whole
 * map. Two things were wrong with that. The path is one more piece of the same
 * report, so a line above the table that is not in the table invites the reader
 * to wonder what else is missing; and a button that copies everything is the
 * answer to a question nobody asks, because what a reader wants is the ONE
 * value under the pointer - a JDBC URL, a port, a participant id.
 *
 * So the path is the first row, and a click on any row copies that row's value.
 * The pane keeps no button at all.
 *
 * <h2>The state chip is not here any more</h2>
 *
 * It sat above the table, at the top of a card in the middle of the window,
 * saying one word about the whole stack. It is the most-read thing in the
 * window and it was the least findable, so it moved to the right of the footer
 * - beside the last thing that happened, which is the other line a reader
 * checks without looking for it. The window owns it now.
 *
 * Author Claude/bentzn
 */
public final class StatusPane extends JPanel {

    private static final long serialVersionUID = 1L;

    /** The key the status file is listed under, first, above the report. */
    public static final String STR_KEY_STATUS_FILE = "status file";

    private static final int N_COLUMN_KEY = 0;

    private static final int N_COLUMN_VALUE = 1;

    private BiConsumer<String, String> sinkCopy;

    private final DefaultTableModel model = new DefaultTableModel(new Object[] { "key", "value" },
            0) {

        private static final long serialVersionUID = 1L;


        @Override
        public boolean isCellEditable(int nRow, int nColumn) {
            return false;
        }
    };

    private final JTable table = new JTable(model);


    public StatusPane() {
        super(new BorderLayout(GuiTheme.scale(GuiTheme.N_GAP), GuiTheme.scale(GuiTheme.N_GAP)));
        setOpaque(false);

        // Per component and through UIScale: `Table.rowHeight` given to
        // UIManager as a plain Integer is consumed by Swing core and is NOT
        // scaled by FlatLaf.
        table.setRowHeight(GuiTheme.scale(GuiTheme.N_ROW_HEIGHT));
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new java.awt.Dimension(0, 0));
        table.getTableHeader().setReorderingAllowed(false);
        table.setFillsViewportHeight(true);

        // SELECTION IS BACK, and now it means something. It was turned off
        // when a highlighted row promised an action the pane did not have;
        // clicking a row copies its value, so the highlight is the feedback
        // that the click landed on the row the pointer was over.
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent evt) {
                copyRowAt(evt.getPoint().y);
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(GuiTheme.colCardBorder(), 1, true));

        add(scroll, BorderLayout.CENTER);
    }


    /**
     * @param sinkNew told the key and the value of the row that was copied,
     *        or null for none. ONLY THE VALUE reaches the clipboard; the key
     *        is there so a surface can say WHICH value was taken, which is
     *        the half a reader cannot check by looking.
     */
    public void useCopySink(BiConsumer<String, String> sinkNew) {
        this.sinkCopy = sinkNew;
    }


    /**
     * @param report what came back, or null to empty the table
     * @param strFileStatus the status file's path, listed first, or null
     */
    public void setReport(ReadyReport report, String strFileStatus) {
        model.setRowCount(0);
        if (report == null)
            return;

        if (strFileStatus != null)
            model.addRow(new Object[] { STR_KEY_STATUS_FILE, strFileStatus });
        addRows(report.map());
    }


    /**
     * THE SAME TABLE, FILLED FROM SOMETHING THAT IS NOT A ReadyReport.
     *
     * A LocalNet stack has three participants and three databases and no
     * single report to render, but what it can state is a flat list of facts -
     * which is all this table has ever been underneath.
     *
     * @param mapRow key to value, in iteration order, replacing what is shown
     */
    public void setRows(Map<String, String> mapRow) {
        model.setRowCount(0);
        addRows(mapRow);
    }


    /**
     * @param mapRow key to value, appended in iteration order
     */
    private void addRows(Map<String, String> mapRow) {
        for (Map.Entry<String, String> entry : mapRow.entrySet()) {
            model.addRow(new Object[] { entry.getKey(), entry.getValue() });
        }
    }


    /**
     * @param nY where in the table the pointer was
     */
    private void copyRowAt(int nY) {
        int nRow = table.rowAtPoint(new java.awt.Point(0, nY));
        if (nRow < 0)
            return;

        Object objValue = model.getValueAt(nRow, N_COLUMN_VALUE);
        if (objValue == null)
            return;

        String strValue = objValue.toString();
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(strValue), null);

        BiConsumer<String, String> sinkHere = sinkCopy;
        if (sinkHere != null)
            sinkHere.accept(String.valueOf(model.getValueAt(nRow, N_COLUMN_KEY)),
                    strValue);
    }


}
