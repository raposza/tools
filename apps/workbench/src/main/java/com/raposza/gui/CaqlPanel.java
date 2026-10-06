// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.caql.Binding;
import com.raposza.caql.CaqlParser;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

/**
 * The CaQL tab: write it above, read what it did below.
 *
 * <h2>An execution surface, not an editor</h2>
 *
 * RUN, AND NOTHING ELSE - operator instruction, 2026-09-09. Open, save and
 * save-as are gone, and with them the file this panel used to remember; a
 * script is written here and run here, and the transcript is where a run is
 * kept. `Validate only` went the same way: a run runs.
 *
 * No language server, no completion, no project. The
 * one thing here that is not plain text editing is the caret jump on a failure
 * with a line number, because the first thing anybody does with a refused
 * statement is go and look at it.
 *
 * <h2>It owns NO connection</h2>
 *
 * The session identity lives above the tabs and the participant lives on
 * {@link MainWindow}, which is what makes CaQL another way of talking to the
 * ledger the Ledger tab is showing rather than a tool that happens to sit in a
 * tab. This panel hands the script out and paints what comes back.
 *
 * <h2>Results REPLACE, they do not accumulate</h2>
 *
 * A run is the unit the operator thinks in, and a pane that stacked them would
 * put the answer to the last question at the bottom of a column that has to be
 * scrolled to. The transcript is where a run is kept.
 *
 * <h2>Two output tabs, and a REGISTER behind the second</h2>
 *
 * `Results` is the transcript of the last run. `Parameters` is what the runs
 * have BOUND, and it outlives them: with Ctrl-Enter running one line, the
 * statement that allocates a party and the statement that uses it are two
 * runs. The window seeds the next run from that list and replaces it with what
 * the run ended holding.
 *
 * An entry is dropped by right-clicking it. A run binding a name the register
 * already holds replaces it, so removal is tidying rather than a step anything
 * requires.
 *
 * <h2>The editor is COLOURED, by the parser's own vocabulary</h2>
 *
 * Comments grey, reserved words bold dark green, identifiers the blue a detail
 * pane draws a link in - operator instruction. {@link CaqlSyntax} does it, and
 * takes the keyword list off the parser and the identifier shapes off
 * {@link LinkText}, so neither can drift from what the language actually
 * reserves or from what the panes actually call an id. Colour and nothing
 * more: no completion, no error marks, no language server.
 *
 * Author Claude/bentzn
 */
public final class CaqlPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Editor over results, as a fraction of the height. */
    private static final double NUM_SPLIT = 0.5;

    /** Which output tab a run's answer lands on. */
    private static final int N_TAB_RESULT = 0;

    /** Lines a wheel notch moves the script. */
    private static final int N_LINES_WHEEL = 3;

    private final JTextPane areaScript = new JTextPane();
    private final JEditorPane areaResult = new JEditorPane();
    private final JButton btnRun = new JButton("Run");
    private final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

    private final CaqlParams paneParams = new CaqlParams();

    private final JTabbedPane tabsOut = new JTabbedPane();

    /**
     * The editor's undo history, Ctrl-Z and Ctrl-Y. It records TEXT edits
     * only: the colouring pass writes character attributes on every change,
     * and each of those is an undoable edit too, so without the filter in
     * {@link #installUndo} the first Ctrl-Z would uncolour the script and
     * the second would undo the keystroke.
     */
    private final UndoManager undo = new UndoManager();

    /** What the pane holds, before shortening - the same rule as the Ledger tab. */
    private transient String strRawResult = "";

    private transient boolean flagShortIds = true;

    private transient Consumer<String> runOnRun;

    private transient Consumer<String> runOnLink;

    /**
     * How a short id is put back, or null before one is supplied. The
     * panel cannot do it itself: it takes the ids the WINDOW has read.
     */
    private transient UnaryOperator<String> runExpand;

    /** Set once the divider has been placed, so a resize does not re-place it. */
    private transient boolean flagSplitSet;


    public CaqlPanel() {
        super(new BorderLayout());

        split.setTopComponent(buildEditor());
        split.setBottomComponent(buildOutput());
        split.setResizeWeight(NUM_SPLIT);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
    }


    /**
     * The divider is a FRACTION of a height the panel does not have until it is
     * on screen. Placing it in the constructor puts it at zero; placing it here
     * puts it where the design asks, once, and leaves it wherever the operator
     * drags it afterwards.
     */
    @Override
    public void addNotify() {
        super.addNotify();
        if (flagSplitSet)
            return;

        flagSplitSet = true;
        SwingUtilities.invokeLater(() -> split.setDividerLocation(NUM_SPLIT));
    }


    /**
     * @param runOnRunNew what to do with the script when Run is pressed
     */
    public void onRun(Consumer<String> runOnRunNew) {
        this.runOnRun = runOnRunNew;
    }


    /**
     * @param runOnLinkNew what to do with an href in the results, which is how
     *        a created contract reaches the Ledger tab
     */
    public void onLink(Consumer<String> runOnLinkNew) {
        this.runOnLink = runOnLinkNew;
    }


    /**
     * Seeds the editor, and ONLY when there is nothing in it.
     *
     * An example is worth having on a first connection and is worth
     * nothing at the cost of a script the operator is in the middle of, so
     * a reload that finds work in progress leaves it alone.
     *
     * @param strScript what to seed it with, ignored when null or blank
     */
    public void setScriptIfEmpty(String strScript) {
        if (strScript == null || strScript.isBlank() || !areaScript.getText().isBlank())
            return;

        areaScript.setText(strScript);
        // THE SEED ARRIVES SHORT AND THE BOX MAY ALREADY BE OFF. Only
        // `setShortIds` retexted, so a script seeded while `Short ids` was
        // clear stayed abbreviated until the box was toggled twice.
        retextScript();
        areaScript.setCaretPosition(0);
        // The seed is the starting point, not an edit: undoing back past it
        // would leave an empty editor that nothing put there.
        undo.discardAllEdits();
    }


    /**
     * @param runExpandNew how to put a short id back, which the window
     *        supplies because it holds what has been read
     */
    public void onExpand(UnaryOperator<String> runExpandNew) {
        this.runExpand = runExpandNew;
    }


    /**
     * What the next run is seeded from.
     *
     * @return the register, in the order the runs bound it
     */
    public List<Binding> lstParam() {
        return paneParams.lstParam();
    }


    /**
     * @param lstParam what a run ended holding, which becomes the register
     */
    public void setParams(List<Binding> lstParam) {
        paneParams.setParams(lstParam);
    }


    /** @return the script as written */
    public String strScript() {
        return areaScript.getText();
    }


    /**
     * @param flagShortIdsNew whether ids are shown in their short form, which
     *        follows the box above the tabs so one id reads one way everywhere
     */
    public void setShortIds(boolean flagShortIdsNew) {
        this.flagShortIds = flagShortIdsNew;
        paneParams.setShortIds(flagShortIdsNew);
        paint();
        retextScript();
    }


    /**
     * Rewrites the SCRIPT to the form the box now asks for.
     *
     * The results pane re-renders from what it was handed, so it can do
     * this without help. The editor cannot: its text is the operator's, it
     * is the only copy, and going back to the long form means resolving
     * each short id against what the window has read. An id that cannot be
     * resolved is LEFT AS IT IS rather than lost - the run refuses it later
     * and says which one.
     */
    private void retextScript() {
        String strNow = areaScript.getText();
        if (strNow.isBlank())
            return;

        String strNew = flagShortIds ? ShortIds.text(strNow)
                : (runExpand == null ? strNow : runExpand.apply(strNow));
        if (strNew == null || strNew.equals(strNow))
            return;

        int idxCaret = Math.min(areaScript.getCaretPosition(), strNew.length());
        areaScript.setText(strNew);
        areaScript.setCaretPosition(idxCaret);
    }


    /**
     * @param strRaw what to show, exactly as {@link CaqlResults} produced it
     */
    public void setResult(String strRaw) {
        this.strRawResult = strRaw == null ? "" : strRaw;
        paint();
        areaResult.setCaretPosition(0);
        // A RUN IS ASKED AND ANSWERED HERE - operator instruction. Leaving
        // `Parameters` in front would answer a run with a list that does not
        // say whether it worked.
        tabsOut.setSelectedIndex(N_TAB_RESULT);
    }


    /** The lines of the run in flight that have ended, the head line first. */
    private final List<String> lstProgress = new ArrayList<>();

    /** The line of the statement running now, or null between statements. */
    private String strProgressRunning;


    /**
     * Starts a run's progress in the results pane - his instruction,
     * 2026-10-04: "When executing CaQL with 'Run' show progress line by line."
     * The transcript replaces it when the run ends.
     *
     * @param strHead the first line
     */
    public void progressStart(String strHead) {
        lstProgress.clear();
        lstProgress.add(strHead);
        lstProgress.add("");
        strProgressRunning = null;
        showProgress();
    }


    /**
     * @param strLine the statement that is running now; its line is replaced
     *        when it ends
     */
    public void progressRunning(String strLine) {
        strProgressRunning = strLine;
        showProgress();
    }


    /**
     * @param strLine a statement that has ended
     */
    public void progressDone(String strLine) {
        strProgressRunning = null;
        lstProgress.add(strLine);
        showProgress();
    }


    /**
     * THE LAST LINE STAYS IN VIEW, so the pane follows the run rather than
     * sitting on its first statement.
     */
    private void showProgress() {
        StringBuilder buf = new StringBuilder();
        for (String strLine : lstProgress) {
            buf.append(strLine).append('\n');
        }
        if (strProgressRunning != null)
            buf.append(strProgressRunning).append('\n');
        this.strRawResult = buf.toString();
        paint();
        areaResult.setCaretPosition(areaResult.getDocument().getLength());
        tabsOut.setSelectedIndex(N_TAB_RESULT);
    }


    /**
     * Empties the results pane WITHOUT bringing it forward.
     *
     * A transcript belongs to the participant it was run against, so a new
     * connection has to leave it behind - but this is not an answer to
     * anything the operator pressed, so it does not take the tab.
     */
    public void clearResult() {
        this.strRawResult = "";
        paint();
        areaResult.setCaretPosition(0);
    }


    /**
     * @param flagBusy true while a run is in flight, which disables Run rather
     *        than letting a second script be sent under the first one
     */
    public void setBusy(boolean flagBusy) {
        btnRun.setEnabled(!flagBusy);
        if (flagBusy)
            setResult("running...");
    }


    /**
     * Puts the caret where a failure says the trouble is.
     *
     * @param numLine one-based line, ignored when it is outside the script
     */
    public void caretTo(int numLine) {
        Element root = areaScript.getDocument().getDefaultRootElement();
        if (numLine < 1 || numLine > root.getElementCount())
            return;

        areaScript.setCaretPosition(root.getElement(numLine - 1).getStartOffset());
        areaScript.requestFocusInWindow();
    }


    /**
     * Puts the caret at the END of a line - where a missing {@code ;} is
     * typed. Operator instruction, 2026-09-14: a statement refused for its
     * terminator used to land the caret at its start, which is the wrong end
     * of the line for the one keystroke that fixes it.
     *
     * @param numLine one-based line, ignored when it is outside the script
     */
    public void caretToEnd(int numLine) {
        Element root = areaScript.getDocument().getDefaultRootElement();
        if (numLine < 1 || numLine > root.getElementCount())
            return;

        // An element's end offset is one past its newline; the last line's is
        // one past the document. Either way, the character before it is the
        // end of the text on that line.
        int idx = Math.min(root.getElement(numLine - 1).getEndOffset() - 1,
                areaScript.getDocument().getLength());
        areaScript.setCaretPosition(Math.max(idx, 0));
        areaScript.requestFocusInWindow();
    }


    private JPanel buildEditor() {
        areaScript.setFont(GuiScale.fontMono(CaqlSyntax.CNT_FONT));
        CaqlSyntax.install(areaScript);
        installUndo();

        // A STYLED PANE WRAPS AND A TEXT AREA DID NOT. Soft wrapping would
        // rewrap a seeded script at whatever width the window happens to be,
        // losing the layout it was written with - the seeds are HARD-wrapped to
        // fit, `AviationScript.CNT_WIDTH`. Inside a plain panel the pane keeps
        // its preferred width, so the scroll pane scrolls sideways exactly as it
        // did, which is what a line still needs when `Short ids` is cleared and
        // every party id goes back to 130 characters.
        JPanel pnlWide = new JPanel(new BorderLayout());
        pnlWide.setBackground(areaScript.getBackground());
        pnlWide.add(areaScript, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(pnlWide);
        scroll.setBorder(null);
        // A PLAIN PANEL IS NOT `Scrollable`, so the scroll pane falls back to
        // one pixel a unit and a wheel notch moves three of them. The unit a
        // reader is scrolling a script in is a line of it.
        int nLine = areaScript.getFontMetrics(areaScript.getFont()).getHeight();
        scroll.getVerticalScrollBar().setUnitIncrement(nLine * N_LINES_WHEEL);
        scroll.getHorizontalScrollBar().setUnitIncrement(nLine * N_LINES_WHEEL);

        JPanel pnl = new JPanel(new BorderLayout());
        pnl.add(buildBar(), BorderLayout.NORTH);
        pnl.add(scroll, BorderLayout.CENTER);
        pnl.setBorder(GuiScale.border(4, 4, 0, 4));
        return pnl;
    }


    /**
     * Run is on the bar rather than under the editor: the editor grows and a
     * control below it moves, which on a long script means hunting for the one
     * button that does something.
     */
    private JPanel buildBar() {
        JPanel pnl = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiScale.scale(8),
                GuiScale.scale(2)));

        btnRun.setToolTipText("execute the WHOLE script as the user selected above;"
                + " Ctrl-Enter runs only the statement the caret is in");
        btnRun.addActionListener(ev -> run());

        JButton btnSkills = new JButton("Skills");
        btnSkills.setToolTipText("the whole CaQL language, on one page");
        btnSkills.addActionListener(ev -> skills());

        pnl.add(btnRun);
        pnl.add(btnSkills);

        // On the SCRIPT AREA, not on the window: a shortcut registered on the
        // frame would fire while the Ledger tab is in front.
        //
        // IT RUNS THE CARET'S LINE, NOT THE SCRIPT - operator instruction,
        // 2026-09-09. `Run` is the whole script; the keystroke is the one
        // statement being worked on, which is what a script is written by.
        areaScript.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "caql-run");
        areaScript.getActionMap().put("caql-run", new AbstractAction() {

            private static final long serialVersionUID = 1L;


            @Override
            public void actionPerformed(ActionEvent ev) {
                runLine();
            }

        });

        return pnl;
    }


    /**
     * Ctrl-Z undoes, Ctrl-Y and Ctrl-Shift-Z redo. Operator instruction,
     * 2026-09-14.
     *
     * ATTRIBUTE CHANGES ARE NOT RECORDED. {@link CaqlSyntax} recolours the
     * whole document after every edit through {@code setCharacterAttributes},
     * and a styled document reports each of those as an undoable edit of type
     * CHANGE. Recording them would put a colouring pass between every two
     * keystrokes in the history. Only INSERT and REMOVE reach the manager.
     */
    private void installUndo() {
        areaScript.getDocument().addUndoableEditListener(ev -> {
            if (ev.getEdit() instanceof AbstractDocument.DefaultDocumentEvent evDoc
                    && evDoc.getType() == DocumentEvent.EventType.CHANGE)
                return;
            undo.addEdit(ev.getEdit());
        });

        areaScript.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "caql-undo");
        areaScript.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "caql-redo");
        areaScript.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "caql-redo");

        areaScript.getActionMap().put("caql-undo", new AbstractAction() {

            private static final long serialVersionUID = 1L;


            @Override
            public void actionPerformed(ActionEvent ev) {
                try {
                    if (undo.canUndo())
                        undo.undo();
                }
                catch (CannotUndoException ex) {
                    // Nothing to undo is not an error the operator needs told.
                }
            }

        });
        areaScript.getActionMap().put("caql-redo", new AbstractAction() {

            private static final long serialVersionUID = 1L;


            @Override
            public void actionPerformed(ActionEvent ev) {
                try {
                    if (undo.canRedo())
                        undo.redo();
                }
                catch (CannotRedoException ex) {
                    // As above.
                }
            }

        });
    }


    /**
     * The results pane is the SAME machinery as the Ledger tab's: one HTML
     * pane, one stylesheet, one link convention. A contract id created by a
     * script and a contract id read off the ledger are the same identifier and
     * must not look like two.
     */
    private JScrollPane buildResult() {
        HTMLEditorKit kit = new HTMLEditorKit();
        for (String strRule : LinkText.arrStyle()) {
            kit.getStyleSheet().addRule(strRule);
        }
        areaResult.setEditorKit(kit);
        areaResult.setEditable(false);
        areaResult.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        areaResult.setFont(GuiScale.fontMono(CaqlSyntax.CNT_FONT));
        areaResult.addHyperlinkListener(ev -> {
            if (ev.getEventType() == HyperlinkEvent.EventType.ACTIVATED && runOnLink != null)
                runOnLink.accept(ev.getDescription());
        });

        JScrollPane scroll = new JScrollPane(areaResult);
        scroll.setBorder(null);

        return scroll;
    }


    /**
     * The two output tabs.
     *
     * `Clear` is gone with the bar it sat on - operator instruction. A run
     * REPLACES the results pane rather than appending to it, so there was
     * never anything to clear that the next run would not have cleared, and
     * the button occupied the strip the tabs now use.
     */
    private JPanel buildOutput() {
        tabsOut.addTab("Results", buildResult());
        tabsOut.addTab("Parameters", paneParams);

        JPanel pnl = new JPanel(new BorderLayout());
        pnl.add(tabsOut, BorderLayout.CENTER);
        pnl.setBorder(GuiScale.border(0, 4, 4, 4));
        return pnl;
    }


    /**
     * Opens the language reference.
     *
     * NOT MODAL, deliberately: it is read WHILE a statement is being
     * written, and a dialog that has to be dismissed to type is a dialog
     * that gets read once and never opened again. Selectable, so a form can
     * be copied straight out of it.
     */
    private void skills() {
        JTextArea areaText = new JTextArea(CaqlSkills.text());
        areaText.setFont(GuiScale.fontMono(CaqlSyntax.CNT_FONT));
        areaText.setEditable(false);
        areaText.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(areaText);
        scroll.setPreferredSize(GuiScale.dim(760, 620));

        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "CaQL");
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.add(scroll);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }


    private void paint() {
        areaResult.setText(LinkText.html(strRawResult, flagShortIds));
    }


    private void run() {
        if (runOnRun == null)
            return;
        runOnRun.accept(areaScript.getText());
    }


    /**
     * Runs the STATEMENT the caret is in, and only that one.
     *
     * THE LINE NUMBER IS PRESERVED by sending the blank lines above it
     * rather than the line alone. `Splitter` drops blank lines and counts
     * every one of them, so the transcript, the caret jump on a refusal and
     * the editor all name the same line - and a run of line 12 that reported
     * line 1 would send the operator to the wrong statement.
     *
     * A STATEMENT LAID OUT OVER SEVERAL LINES RUNS FROM ANY OF THEM. The
     * span is cut by `CaqlParser.spanAt`, which is the splitter's own scan
     * rather than a second copy of it living in a Swing class - so what the
     * caret belongs to here and what `Run` would cut are the same thing by
     * construction.
     *
     * WHAT IS SENT IS THE SOURCE, terminator included and interior newlines
     * intact, so the statement parses on its own and the lines a refusal
     * names are the lines in the editor.
     *
     * A caret in a comment, or in the blank space between two statements,
     * belongs to no statement and runs nothing.
     */
    private void runLine() {
        if (runOnRun == null)
            return;

        try {
            String strAll = areaScript.getDocument()
                    .getText(0, areaScript.getDocument().getLength());
            int[] arrSpan = CaqlParser.spanAt(strAll, areaScript.getCaretPosition());
            if (arrSpan == null)
                return;

            // The blank lines above it are sent with it, so the transcript,
            // the caret jump on a refusal and the editor all name the same
            // line - see above.
            int numLine = 0;
            for (int cntLoop = 0; cntLoop < arrSpan[0]; cntLoop++) {
                if (strAll.charAt(cntLoop) == '\n')
                    numLine++;
            }
            runOnRun.accept("\n".repeat(numLine) + strAll.substring(arrSpan[0], arrSpan[1]));
        }
        catch (BadLocationException ex) {
            setResult(CaqlResults.textProblem("the caret is not on a line that can be"
                    + " read: " + ex.getMessage()));
        }
    }


}
