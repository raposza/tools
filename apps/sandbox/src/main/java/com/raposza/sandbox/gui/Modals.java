// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Every modal the Sandbox window can raise goes through here, so that an
 * unattended run can be certain none of them is waiting for a person.
 *
 * <h2>Why this exists and what it is not</h2>
 *
 * A refused start puts up a modal dialog with nobody to dismiss it and the
 * run stops there, and it is unfixed. An unattended run of three to ten hours
 * is exactly the case that cannot survive it, and the DAR work added new
 * refusal paths INSIDE the dwell: a failed upload, a participant that will not
 * vet, a removal confirmation.
 *
 * **THIS IS NOT A FIX FOR THAT.** It is the operator's decision:
 * suppress the dialog, record what it would have said, and FAIL THE WHOLE RUN.
 * Not the step - the run. An unattended run that reports hundreds of green
 * steps and one silently skipped dialog is worse than one that stops,
 * because the first is
 * read as a result. The hang itself is picked up separately.
 *
 * <h2>Armed for an unattended run and for nothing else</h2>
 *
 * There is no switch. An unattended driver arms this explicitly, which is
 * the only way the window is ever driven with nobody in front of it. A hand-run
 * window is untouched and its dialogs behave exactly as they did. A flag would
 * be one more thing to forget on the one run that matters.
 *
 * <h2>The caller still unwinds</h2>
 *
 * A suppressed dialog returns the SAFE answer - no, null, no option chosen -
 * and the caller carries on as if the operator had cancelled. The abort happens
 * on a thread of its own, so the event dispatch thread is never blocked inside
 * `System.exit` while a shutdown hook is trying to stop a stack. Only the FIRST
 * suppression aborts; the ones that follow while the run unwinds are recorded
 * and say nothing, or the log fills with a cascade whose first line - the only
 * one that matters - has scrolled away.
 *
 * Author Claude/bentzn
 */
public final class Modals {

    /**
     * The first token of the abort line, so a log can be grepped for it.
     * Deliberately not a sentence: an unattended run's log is read by eye
     * and by script.
     */
    public static final String STR_ABORT = "RUN ABORTED";

    /** What the JVM exits with. Matches `SandboxApp.N_EXIT_UNATTENDED_MODAL`. */
    public static final int N_EXIT = 6;

    /** The gap between the question, the detail and the link. */
    private static final int N_GAP_ASK = 8;

    /** The link colour on a look-and-feel that declares none. */
    private static final int N_RGB_LINK = 0x1A6FB4;

    private static final Object OBJ_LOCK = new Object();

    private static volatile Consumer<String> sinkAbort;

    private static final AtomicBoolean FLAG_FIRED = new AtomicBoolean();


    private Modals() {
        throw new AssertionError("no instances");
    }


    /**
     * Arms suppression for an unattended run. Called by the driver of such a
     * run and by nothing else.
     */
    public static void armUnattended() {
        arm(Modals::abortAndExit);
    }


    /**
     * Arms suppression with a sink of the caller's choosing.
     *
     * The sink exists so a test can assert the whole path without a display and
     * without exiting the JVM under the test runner. Production arms through
     * {@link #armUnattended()}.
     *
     * @param sinkAbortNew what receives the one-line abort report
     */
    public static void arm(Consumer<String> sinkAbortNew) {
        if (sinkAbortNew == null)
            throw new IllegalArgumentException("a sink is required");

        synchronized (OBJ_LOCK) {
            sinkAbort = sinkAbortNew;
            FLAG_FIRED.set(false);
        }
    }


    /**
     * Disarms. For tests; nothing in the application calls it.
     */
    public static void disarm() {
        synchronized (OBJ_LOCK) {
            sinkAbort = null;
            FLAG_FIRED.set(false);
        }
    }


    /**
     * @return whether an unattended run is driving the window
     */
    public static boolean isArmed() {
        return sinkAbort != null;
    }


    /**
     * @return whether a dialog has already been suppressed in this JVM
     */
    public static boolean isFired() {
        return FLAG_FIRED.get();
    }


    /**
     * A warning with an OK button and nothing to decide.
     *
     * @param owner the parent component, or null
     * @param strMessage what it would have said
     */
    public static void warn(Component owner, String strMessage) {
        if (suppressed("warn", strMessage))
            return;

        JOptionPane.showMessageDialog(owner, strMessage, "raposza sandbox",
                JOptionPane.WARNING_MESSAGE);
    }


    /**
     * A statement with an OK button and nothing to decide.
     *
     * SEPARATE FROM `warn` because the icon is the message. A page of
     * documentation behind a warning triangle reads as something having gone
     * wrong with the documentation.
     *
     * @param owner the parent component, or null
     * @param strMessage what it says
     * @param strTitle the dialog title
     */
    public static void inform(Component owner, String strMessage, String strTitle) {
        if (suppressed("inform", strMessage))
            return;

        JOptionPane.showMessageDialog(owner, strMessage, strTitle,
                JOptionPane.INFORMATION_MESSAGE);
    }


    /**
     * An error with an OK button and nothing to decide.
     *
     * @param owner the parent component, or null
     * @param strMessage what it would have said
     */
    public static void error(Component owner, String strMessage) {
        if (suppressed("error", strMessage))
            return;

        JOptionPane.showMessageDialog(owner, strMessage, "raposza sandbox",
                JOptionPane.ERROR_MESSAGE);
    }


    /**
     * An OK / Cancel question.
     *
     * @param owner the parent component, or null
     * @param strMessage the question
     * @param strTitle the dialog title
     * @param nMessageType one of the `JOptionPane` message types
     * @return true only when the operator pressed OK; a suppressed dialog is
     *         false, which is the same answer a closed one gives
     */
    public static boolean isConfirmed(Component owner, String strMessage, String strTitle,
            int nMessageType) {
        if (suppressed("confirm", strMessage))
            return false;

        return JOptionPane.showConfirmDialog(owner, strMessage, strTitle,
                JOptionPane.OK_CANCEL_OPTION, nMessageType) == JOptionPane.OK_OPTION;
    }


    /**
     * A one-line text prompt.
     *
     * @param owner the parent component, or null
     * @param strMessage the prompt
     * @param strTitle the dialog title
     * @return what was typed, or null when it was cancelled or suppressed
     */
    public static String strInput(Component owner, String strMessage, String strTitle) {
        if (suppressed("input", strMessage))
            return null;

        return JOptionPane.showInputDialog(owner, strMessage, strTitle,
                JOptionPane.QUESTION_MESSAGE);
    }


    /**
     * A dialog with named buttons.
     *
     * @param owner the parent component, or null
     * @param strMessage the question
     * @param arrOption the button labels, in order
     * @param objDefault which of them is focused
     * @return the index of the button pressed, or -1 when the dialog was closed
     *         or suppressed; -1 is never an affirmative
     */
    public static int idxOption(Component owner, String strMessage, Object[] arrOption,
            Object objDefault) {
        if (suppressed("option", strMessage))
            return -1;

        return JOptionPane.showOptionDialog(owner, strMessage, "raposza sandbox",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, arrOption,
                objDefault);
    }


    /**
     * A dialog with named buttons that opens SHORT: one question, and a link
     * that shows the rest.
     *
     * <h2>The long text is not gone, it is behind one click</h2>
     *
     * A question a reader has already answered twice is read as an obstacle,
     * and a box of prose in front of a Yes button is skipped rather than read.
     * The question is one line; whoever has not met it before presses the link
     * and gets the whole of it, and the dialog grows to hold it.
     *
     * The suppression report carries BOTH parts, because an unattended run's
     * abort line has no link to press.
     *
     * @param owner the parent component, or null
     * @param strShort the question, one line
     * @param strDetail what the link reveals
     * @param strExpand the link's text
     * @param arrOption the button labels, in order
     * @param objDefault which of them is focused
     * @return the index of the button pressed, or -1 when the dialog was closed
     *         or suppressed; -1 is never an affirmative
     */
    public static int idxOptionExpandable(Component owner, String strShort, String strDetail,
            String strExpand, Object[] arrOption, Object objDefault) {
        if (suppressed("option", strShort + "\n" + strDetail))
            return -1;

        JPanel pnlAsk = new JPanel(new BorderLayout(0, N_GAP_ASK));
        pnlAsk.setOpaque(false);

        JLabel lblAsk = new JLabel(strShort);
        pnlAsk.add(lblAsk, BorderLayout.NORTH);

        JTextArea txtDetail = new JTextArea(strDetail);
        txtDetail.setEditable(false);
        txtDetail.setOpaque(false);
        txtDetail.setFocusable(false);
        txtDetail.setBorder(null);
        txtDetail.setFont(lblAsk.getFont());
        txtDetail.setVisible(false);
        pnlAsk.add(txtDetail, BorderLayout.CENTER);

        JLabel lblExpand = new JLabel("<html><u>" + strExpand + "</u></html>");
        Color colLink = UIManager.getColor("Component.linkColor");
        lblExpand.setForeground(colLink == null ? new Color(N_RGB_LINK) : colLink);
        lblExpand.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel pnlLink = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pnlLink.setOpaque(false);
        pnlLink.add(lblExpand);
        pnlAsk.add(pnlLink, BorderLayout.SOUTH);

        lblExpand.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent evt) {
                txtDetail.setVisible(true);
                pnlLink.setVisible(false);
                // PACKED, NOT REVALIDATED. The dialog sized itself to one line
                // before the text existed; revalidating inside a window that
                // small lays the whole detail out in a column one word wide.
                Window winAsk = SwingUtilities.getWindowAncestor(pnlAsk);
                if (winAsk == null)
                    return;
                winAsk.pack();
                winAsk.setLocationRelativeTo(winAsk.getOwner());
            }
        });

        return JOptionPane.showOptionDialog(owner, pnlAsk, "raposza sandbox",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, arrOption,
                objDefault);
    }


    /**
     * @param strKind which shape of dialog was asked for
     * @param strMessage what it would have said
     * @return true when the dialog must NOT be shown
     */
    private static boolean suppressed(String strKind, String strMessage) {
        Consumer<String> sinkHere = sinkAbort;
        if (sinkHere == null)
            return false;

        if (FLAG_FIRED.compareAndSet(false, true))
            sinkHere.accept(STR_ABORT + " - a " + strKind + " dialog was suppressed: "
                    + strOneLine(strMessage));

        return true;
    }


    /**
     * A dialog's text is written for a box and carries newlines. An abort line
     * that spans four lines is one a `grep` for {@link #STR_ABORT} shows a
     * quarter of.
     *
     * @param strMessage the dialog text, which may be null
     * @return the same text on one line
     */
    static String strOneLine(String strMessage) {
        if (strMessage == null)
            return "<no message>";

        String strFlat = strMessage.replace("\r\n", " | ").replace('\n', '|').replace('\r', '|')
                .replace("|", " | ").replaceAll(" +", " ").trim();
        return strFlat.isEmpty() ? "<empty message>" : strFlat;
    }


    /**
     * Prints the report and brings the JVM down.
     *
     * On a thread of its own, so the event dispatch thread returns from the
     * call site and unwinds. `System.exit` runs the window's shutdown hook,
     * which stops the stack; a hook that needed the event dispatch thread while
     * that thread was blocked inside `exit` would deadlock, and this is the
     * cheapest way of never finding out whether it does.
     *
     * @param strReport the one-line report
     */
    private static void abortAndExit(String strReport) {
        System.out.println(strReport);
        System.out.flush();
        System.err.println(strReport);
        System.err.flush();

        Thread threadExit = new Thread(() -> System.exit(N_EXIT), "unattended-abort");
        threadExit.start();
    }

}
