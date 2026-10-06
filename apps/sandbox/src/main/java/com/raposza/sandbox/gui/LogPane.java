// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultCaret;

/**
 * Everything the process printed, newest at the bottom.
 *
 * IT KEEPS THE WHOLE RUN. It used to keep 4000 lines and drop the oldest
 * quarter beyond that, which threw away the start of a run just as a long one
 * became worth reading - and for postgres, PQS and the JSON API the pane is
 * the ONLY copy, because only Canton writes a log file. There is still a
 * ceiling, but it is a safety valve rather than a policy: see
 * {@link #N_CHAR_MAX}.
 *
 * <h2>Wrap is a checkbox and it starts ON</h2>
 *
 * The pane shipped with `setLineWrap(false)` and a horizontal scrollbar, so a
 * long line - which is most of what Canton prints, and all of what a JDBC URL
 * or a participant id is - ran off the right edge and had to be scrolled to be
 * read. It is a checkbox rather than a fixed choice because a wrapped stack
 * trace is harder to read than a scrolled one, and both cases occur in this
 * window.
 *
 * <h2>...and a FIREHOSE pane starts it OFF - A-63, measured 2026-10-04</h2>
 *
 * His stall reports after the batching: 3.3 to 8.1 s on the event thread,
 * every one in `JTextArea.getPreferredSize` - `WrappedPlainView.breakLines` -
 * `Font.getStringBounds`, under `ScrollPaneLayout.layoutContainer` while the
 * window validated. A wrapped area measures its lines again whenever its
 * width is set anew, so the cost is the size of the document, and the Debug
 * panes hold megabytes - Web alone is a line per wallet poll. Unwrapped,
 * `PlainView` keeps its longest line up to date per insert and breaks
 * nothing. {@link #useWrap} turns it off where the window builds a pane that
 * fills like that; the checkbox still turns it on, at that cost.
 *
 * <h2>The spinner</h2>
 *
 * A participant takes about 45 seconds on this machine and nearer three
 * minutes on a modest one, and for all of it the milestone pane says
 * `Starting participant` and nothing else. A reader cannot tell that from a
 * hung start, and the honest signal was already arriving: the participant's own
 * output, line after line, on another tab.
 *
 * So {@link #tick()} advances a character on the pending line and is called
 * once per line of DETAIL log. It is not a clock. A spinner driven by a timer
 * keeps turning after a process has stopped saying anything, which is precisely
 * the case a reader needs to see; this one stops when the output stops.
 *
 * <h2>Lines from other threads are appended in BATCHES - A-63, measured 2026-10-04</h2>
 *
 * This pane held the event thread for 101 s on his console. Every line came in
 * as its own event and its own `JTextArea.append`, and with wrap on each
 * insert makes `WrappedPlainView` rebuild its layout arrays and lay out every
 * line it holds - `BoxView.updateLayoutArray`, `layoutMajorAxis` - so one
 * insert costs the size of the pane, and a burst of a thousand lines costs a
 * thousand full layouts. A line is now queued and a one-shot timer of
 * {@link #N_MS_FLUSH} drains the queue into ONE insert and ONE caret move.
 * On the event thread an append is still immediate, after the queue is
 * drained in front of it, so what was printed before it stays before it and
 * {@link #isEmpty()} keeps its meaning.
 *
 * Author Claude/bentzn
 */
public final class LogPane extends JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * The safety valve, in characters. About 300,000 Canton lines per pane -
     * far beyond any run a person watches, and small enough that a process
     * stuck in a loop fills a bounded buffer instead of the heap. Beyond it
     * the oldest HALF goes and the pane says so, because the tail is the
     * diagnosis.
     *
     * WHAT THIS COSTS, and it is the one thing to know: a `JTextArea` holding
     * tens of megabytes with word wrap on is slow to scroll and slow to copy.
     * A run that reaches this ceiling will feel it. Four panes at the ceiling
     * is about 250 MB of character data, which this machine has and a laptop
     * may not; it becomes a setting when B-3's tab exists.
     */
    public static final int N_CHAR_MAX = 32 * 1024 * 1024;

    /** What replaces the half that went. */
    private static final String STR_TRIMMED =
            "--- earlier output dropped: this pane holds the last 32 MB ---";

    /** The frames, in the order they turn. */
    private static final String[] ARR_SPIN = { ".", "..", "...", "...." };

    /**
     * HH:mm, LOCAL. Not the milestone's own words - `Started X in 3 seconds`
     * already says how long something took, and this says when. Minute
     * resolution because its job is to separate one run from the next, not
     * to time a start.
     */
    private static final DateTimeFormatter FMT_CLOCK =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Milliseconds between frames. */
    private static final int N_MS_FRAME = 400;

    /** How long queued lines wait for company before one insert takes them all. */
    private static final int N_MS_FLUSH = 40;

    private final JTextArea areaLog = new JTextArea();

    private final JCheckBox chkFollow = new JCheckBox("Follow", true);

    private final JCheckBox chkWrap = new JCheckBox("Wrap", true);

    /** Follow, Wrap, Copy, Clear and whatever {@link #addControl} adds. */
    private final JPanel bar;

    /** Whether lines carry the local time. Off for the firehose tabs. */
    private boolean flagClock;

    /** Whether the last line carries a spinner rather than a terminator. */
    private boolean flagSpin;

    private int idxSpin;

    /** How many characters the pending line's suffix occupies. */
    private int cntSuffix;

    /**
     * Where the pending line starts, so it can be rewritten rather than
     * followed. -1 when no line is open.
     */
    private int nOffsetLine = -1;

    private final transient javax.swing.Timer timerSpin =
            new javax.swing.Timer(N_MS_FRAME, evt -> tickHere());

    /** Lines appended off the event thread, waiting for {@link #flushHere()}. */
    private final transient Deque<String> lstQueued = new ArrayDeque<>();

    private final transient javax.swing.Timer timerFlush =
            new javax.swing.Timer(N_MS_FLUSH, evt -> flushHere());


    /** A pane with its control bar, which is what a firehose tab wants. */
    public LogPane() {
        this(true);
    }


    /**
     * @param flagControls whether to show Follow, Wrap, Copy and Clear. The
     *        milestone pane passes false: there are eight lines in it, so
     *        there is nothing to follow to, nothing long enough to need
     *        wrapping decided, and four controls over eight lines is a bar
     *        taller than the thing it governs.
     */
    public LogPane(boolean flagControls) {
        super(new BorderLayout());
        setOpaque(false);

        areaLog.setEditable(false);
        areaLog.setLineWrap(true);
        // Word boundaries, not character ones: a wrapped identifier broken
        // mid-token cannot be copied out of the pane in one piece by eye.
        areaLog.setWrapStyleWord(true);
        GuiTheme.mono(areaLog);
        areaLog.setBorder(GuiTheme.borderScaled(8, 8, 8, 8));

        // FOLLOW COULD NOT BE TURNED OFF, and the checkbox was not the reason.
        // A JTextArea's DefaultCaret runs UPDATE_WHEN_ON_EDT, so the caret
        // moves to the end of the document on EVERY insert and the view
        // scrolls after it - whatever the checkbox said. The policy goes to
        // NEVER_UPDATE and following becomes something this class does on
        // purpose, in appendHere, when the box is ticked.
        DefaultCaret caret = (DefaultCaret) areaLog.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

        JScrollPane scroll = new JScrollPane(areaLog);
        scroll.setBorder(BorderFactory.createLineBorder(GuiTheme.colCardBorder(), 1, true));

        // TICKING IT AGAIN CATCHES UP. Without this the pane stays where it
        // was left and only moves on the next line, which reads as a Follow
        // that did not take.
        chkFollow.addActionListener(evt -> {
            if (chkFollow.isSelected())
                areaLog.setCaretPosition(areaLog.getDocument().getLength());
        });

        chkWrap.addActionListener(evt -> {
            areaLog.setLineWrap(chkWrap.isSelected());
            areaLog.setWrapStyleWord(chkWrap.isSelected());
        });

        JButton btnCopy = new JButton("Copy");
        btnCopy.addActionListener(evt -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(areaLog.getText()), null));

        // ASKED FIRST, AND ONLY HERE. For postgres, PQS and the JSON API this
        // pane is the ONLY copy of the run - nothing writes a file - so a
        // mis-clicked Clear next to Copy throws away the whole diagnosis with
        // no undo. The question is on the BUTTON and not in clear(), because
        // the window clears all four panes at the start of every run and a
        // modal there would stop an unattended run dead.
        JButton btnClear = new JButton("Clear");
        btnClear.addActionListener(evt -> {
            if (Modals.isConfirmed(this, "Clear this log pane?\n\nThis pane is the only copy"
                    + " of what the process printed.", "Clear log",
                    javax.swing.JOptionPane.WARNING_MESSAGE)) {
                clear();
            }
        });

        // A JToolBar paints its buttons borderless, so `Clear` came out as
        // the word "Clear" sitting against the Follow checkbox and read as
        // part of its label. A plain panel gives the button its border back.
        bar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,
                GuiTheme.scale(12), GuiTheme.scale(4)));
        bar.setOpaque(false);
        bar.add(chkFollow);
        bar.add(chkWrap);
        bar.add(btnCopy);
        bar.add(btnClear);

        // UNDER THE PANE. Above it, the bar sat between the card's title and
        // the text and pushed the first line of output down; the pane is the
        // subject of the tab and the controls are what is done to it.
        if (flagControls)
            add(bar, BorderLayout.SOUTH);
        add(scroll, BorderLayout.CENTER);

        timerFlush.setRepeats(false);
    }


    /**
     * Safe from any thread: the append is hopped onto the event dispatch
     * thread here rather than at each of the four call sites, because the one
     * that forgets is the one that corrupts the document. Off the event
     * thread the line is QUEUED and lands with the next flush - see the class
     * comment.
     *
     * @param strLine one line, without its terminator
     */
    public void append(String strLine) {
        if (SwingUtilities.isEventDispatchThread()) {
            flushHere();
            appendHere(strLine, false);
            return;
        }
        synchronized (lstQueued) {
            lstQueued.addLast(strLine);
        }
        // A Timer may be started from any thread; starting one that runs is
        // a no-op, so a burst starts it once and the rest ride along.
        timerFlush.start();
    }


    /**
     * The same, but the line stays open with a spinner on it until the next
     * line arrives.
     *
     * @param strLine one line, without its terminator
     */
    /**
     * <b>Rewrites the pending line instead of adding one.</b> A download that
     * reports itself every percent would otherwise put a hundred lines in the
     * pane and push everything before it out of sight - the reader wants the
     * CURRENT figure, not the history of it.
     *
     * With no line open this behaves as {@link #appendSpinning}.
     *
     * @param strLine what the pending line should now say
     */
    public void replaceSpinning(String strLine) {
        onSwing(() -> replaceHere(strLine));
    }


    /**
     * The same, but the line stays open with a spinner on it until the next
     * line arrives.
     *
     * @param strLine one line, without its terminator
     */
    public void appendSpinning(String strLine) {
        onSwing(() -> appendHere(strLine, true));
    }


    /**
     * Kept so a caller that used to drive the spinner does not have to know
     * it no longer does. The frames run on {@link #N_MS_FRAME}.
     */
    /**
     * @return whether a pending line is open, so a caller with another
     *         pending line can REPLACE it rather than open a second one
     */
    public boolean isSpinning() {
        return flagSpin;
    }


    public void tick() {
    }


    public void clear() {
        onSwing(() -> {
            areaLog.setText("");
            flagSpin = false;
            timerSpin.stop();
        });
    }


    /**
     * READ ON THE EVENT THREAD, by a caller that is about to append. The
     * separator between one run and the next is decided from it, and a run
     * starts on the event thread, so there is nothing to marshal.
     *
     * @return whether the pane holds nothing at all
     */
    public boolean isEmpty() {
        return areaLog.getDocument().getLength() == 0;
    }


    /**
     * Runs the task on the event thread AFTER the queued lines, so a spinner
     * line or a clear never overtakes output that was printed before it.
     */
    private void onSwing(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            flushHere();
            task.run();
            return;
        }
        SwingUtilities.invokeLater(() -> {
            flushHere();
            task.run();
        });
    }


    /**
     * EVENT THREAD ONLY. Every queued line, as one insert into the document
     * and one caret move - the same text {@link #appendHere} would have
     * produced line by line.
     */
    private void flushHere() {
        timerFlush.stop();
        StringBuilder bld = new StringBuilder();
        int cntLine = 0;
        synchronized (lstQueued) {
            while (!lstQueued.isEmpty()) {
                String strLine = lstQueued.removeFirst();
                if (flagClock && !strLine.isBlank())
                    bld.append(FMT_CLOCK.format(LocalTime.now())).append("  ");
                bld.append(strLine).append('\n');
                cntLine++;
            }
        }
        if (cntLine == 0)
            return;

        closeSpin();
        nOffsetLine = areaLog.getDocument().getLength();
        areaLog.append(bld.toString());

        if (areaLog.getDocument().getLength() > N_CHAR_MAX)
            trim();
        if (chkFollow.isSelected())
            areaLog.setCaretPosition(areaLog.getDocument().getLength());
    }


    /**
     * EVENT THREAD - the window calls it while it builds.
     *
     * @param comp a control of the pane's owner, after Clear
     */
    public void addControl(JComponent comp) {
        bar.add(comp);
    }


    /**
     * EVENT THREAD - the window calls it while it builds.
     *
     * @param flagOn whether long lines wrap; the Wrap box follows
     */
    public void useWrap(boolean flagOn) {
        chkWrap.setSelected(flagOn);
        areaLog.setLineWrap(flagOn);
        areaLog.setWrapStyleWord(flagOn);
    }


    /**
     * @param flagOn whether every line is prefixed with the local HH:mm
     */
    public void useClock(boolean flagOn) {
        this.flagClock = flagOn;
    }


    private void replaceHere(String strLine) {
        if (!flagSpin || nOffsetLine < 0) {
            appendHere(strLine, true);
            return;
        }

        timerSpin.stop();
        areaLog.replaceRange("", nOffsetLine, areaLog.getDocument().getLength());
        if (flagClock && !strLine.isBlank())
            areaLog.append(FMT_CLOCK.format(LocalTime.now()) + "  ");
        areaLog.append(strLine);

        idxSpin = 0;
        appendSuffix();
        flagSpin = true;
        timerSpin.start();
        if (chkFollow.isSelected())
            areaLog.setCaretPosition(areaLog.getDocument().getLength());
    }


    private void appendHere(String strLine, boolean flagPending) {
        // CLOSE FIRST. The clock used to be written before this, which
        // appended it to the end of the still-open spinning line - and
        // closeSpin then removed the suffix's worth of characters off the
        // end of the TIMESTAMP. `Starting postgres ....14` was the last
        // three digits of a clock reading that belonged to the next line.
        closeSpin();
        nOffsetLine = areaLog.getDocument().getLength();
        // NOT ON A BLANK LINE. The milestone pane writes an empty line as a
        // separator between one run and the next, and a clock in front of it
        // reads as an event that has no words.
        if (flagClock && !strLine.isBlank())
            areaLog.append(FMT_CLOCK.format(LocalTime.now()) + "  ");
        areaLog.append(strLine);

        if (flagPending) {
            idxSpin = 0;
            appendSuffix();
            flagSpin = true;
            timerSpin.start();
        }
        else {
            areaLog.append("\n");
        }

        // NOT WHILE A LINE IS OPEN. trim() rewrites the whole document, and
        // the spinner's suffix is addressed by offset from the end.
        if (!flagSpin && areaLog.getDocument().getLength() > N_CHAR_MAX)
            trim();
        if (chkFollow.isSelected())
            areaLog.setCaretPosition(areaLog.getDocument().getLength());
    }


    private void tickHere() {
        if (!flagSpin)
            return;

        idxSpin = (idxSpin + 1) % ARR_SPIN.length;
        int cntChar = areaLog.getDocument().getLength();
        // THE WHOLE SUFFIX, because the frames are not all one character.
        // Replacing the last character only - which is what the bar spinner
        // needed - would leave `....` behind and grow the line every turn.
        areaLog.replaceRange("", cntChar - cntSuffix, cntChar);
        appendSuffix();
    }


    /** Writes the current frame and records how long it is. */
    private void appendSuffix() {
        String strSuffix = " " + ARR_SPIN[idxSpin];
        areaLog.append(strSuffix);
        cntSuffix = strSuffix.length();
    }


    /** Takes the spinner off the pending line and terminates it. */
    private void closeSpin() {
        if (!flagSpin)
            return;

        flagSpin = false;
        nOffsetLine = -1;
        timerSpin.stop();
        int cntChar = areaLog.getDocument().getLength();
        if (cntChar >= cntSuffix)
            areaLog.replaceRange("", cntChar - cntSuffix, cntChar);
        areaLog.append("\n");
    }


    /**
     * Drops the oldest half, at a line boundary, and says so in the pane.
     *
     * HALF rather than a quarter: this runs when the pane is already tens of
     * megabytes, and rewriting the document is the expensive part, so it is
     * worth doing rarely.
     */
    private void trim() {
        String strAll = areaLog.getText();
        int nCut = strAll.indexOf('\n', strAll.length() / 2);
        if (nCut < 0)
            return;
        areaLog.setText(STR_TRIMMED + "\n" + strAll.substring(nCut + 1));
    }

}
