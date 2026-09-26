// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * The long half of {@link FieldHelp}, raised by clicking a field's label, plus
 * the vendor's own page when there is one and the machine can reach it.
 *
 * <h2>The documentation is the view, and it is read rather than opened</h2>
 *
 * `docs.canton.network` is a Mintlify site and serves every page a second time
 * at `<url>.md` as `text/markdown`, about 16 kB. So the Canton 3.x pages are
 * fetched and shown IN THIS DIALOG rather than handed to a browser. That is
 * one window instead of two, it works where no browser is configured, and it
 * takes `Desktop.browse` - the least portable thing in here - off the 3.x path
 * entirely.
 *
 * The read starts when the dialog opens and has {@value #N_MS_DEADLINE} ms.
 * Until then the pane says so and nothing else; if the page beats the deadline
 * it takes the pane, and if it does not the local text does. A page arriving
 * afterwards is discarded rather than swapped in over a reader who has already
 * started.
 *
 * NOTHING IS STORED. No copy in the jar, no cache on disk. A stored page is a
 * snapshot of somebody else's document that goes stale silently, and this
 * vendor moved the whole tree twice in one week.
 *
 * <h2>MODELESS, because a modal dialog cannot be dismissed by clicking away</h2>
 *
 * The modal blocker consumes the click before any component sees it, so
 * "close when the user clicks outside" is unreachable while the dialog is
 * modal. It is therefore `MODELESS` and closes on losing the window focus,
 * which is how a popover behaves everywhere else.
 *
 * THE COST, and it is real: alt-tabbing away also closes it. That is the same
 * event and cannot be told apart from a click on the window behind. Escape
 * closes it too, and nothing here holds state, so reopening is a click.
 *
 * <h2>HTML, having previously argued for plain text</h2>
 *
 * The earlier objection - that these strings carry `&lt;port number&gt;` and
 * fenced shell, and an HTML pane eats them - was to putting RAW text into a
 * `JEditorPane`. {@link DocFormat} escapes every character that could be read
 * as markup before it generates a single tag, which removes that path
 * entirely. What it buys is headings, code blocks and tables that look like
 * what the vendor wrote.
 *
 * Author Claude/bentzn
 */
final class HelpDialog {

    /** Unscaled. The pane wraps to this and the dialog never changes width. */
    private static final int N_WIDTH = 700;

    /**
     * Unscaled height bounds.
     *
     * THE DIALOG IS SIZED TO WHAT IT HOLDS, between these two. A fixed height
     * gave a three-paragraph note the same 560 px as a 16 kB vendor page, so
     * most fields opened a mostly-empty window; a purely content-driven height
     * would give the vendor page a dialog taller than the screen.
     *
     * A field that is going to FETCH opens at the maximum, because it is about
     * to hold a long page and a dialog that jumped size when the page landed
     * would be worse than one that started big.
     */
    private static final int N_HEIGHT_MIN = 120;

    private static final int N_HEIGHT_MAX = 620;

    /**
     * THE WHOLE BUDGET, and it is a READER'S deadline rather than a network
     * one. Three seconds is about as long as a dialog can say it is doing
     * something before the reader concludes it is stuck; past that the local
     * text is worth more than the vendor's, however good the vendor's is.
     */
    private static final int N_MS_DEADLINE = 3000;

    /** Connect. Inside the deadline, so a dead host gives up before it does. */
    private static final Duration TIMEOUT_CONNECT = Duration.ofSeconds(2);

    /** Read. The same deadline, so the client stops when the dialog does. */
    private static final Duration TIMEOUT_READ = Duration.ofMillis(N_MS_DEADLINE);

    /** The toggle in each direction. Names what pressing it will show. */
    private static final String STR_BTN_LOCAL = "Notes for this field";

    private static final String STR_BTN_DOC = "Official documentation";

    /** What the site's own index banner starts with. */
    private static final String STR_BANNER = "> ## Documentation Index";

    private static final DateTimeFormatter FMT_STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    /**
     * The MDX component names these pages use. ONLY THESE are stripped - a
     * blanket "remove anything between angle brackets" would eat the generics,
     * the shell redirections and the literal `<port number>` that appear in
     * these very pages.
     */
    private static final String[] ARR_TAG = { "Card", "Columns", "Note", "Info", "Warning",
            "Tip", "Check", "Danger", "Steps", "Step", "Tabs", "Tab", "Accordion",
            "AccordionGroup", "CodeGroup", "Frame", "Expandable", "ParamField",
            "ResponseField", "Icon", "Update", "Snippet", "div", "br", "p", "h1", "h2",
            "h3", "img", "span", "strong", "em" };


    private HelpDialog() {
        throw new AssertionError("no instances");
    }


    /**
     * @param compOwner what the dialog is centred on
     * @param strTitle the field's label text
     * @param help what to say
     * @param nMajor the selected Canton's major version, or 0 for unknown
     */
    static void show(Component compOwner, String strTitle, FieldHelp.Help help, int nMajor) {
        if (help == null)
            return;

        Window winOwner = SwingUtilities.getWindowAncestor(compOwner);
        JDialog dlg = new JDialog(winOwner, strTitle, JDialog.ModalityType.MODELESS);

        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setBorder(GuiTheme.borderScaled(4, 4, 4, 4));
        // A LINK IN A FETCHED PAGE IS FLATTENED to text plus its URL by
        // DocFormat, so there is nothing here for a hyperlink listener to do
        // and none is installed. Anchors that Swing renders anyway are inert
        // on purpose: a blue word that does nothing when clicked is worse than
        // a URL a reader can copy.

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createLineBorder(GuiTheme.colCardBorder(), 1, true));
        scroll.getVerticalScrollBar().setUnitIncrement(GuiTheme.scale(16));

        String strLocal = DocFormat.strDocument(DocFormat.strHtmlOfText(help.strLong()));

        JPanel pnlBar = new JPanel(new BorderLayout());
        pnlBar.setOpaque(false);
        pnlBar.setBorder(GuiTheme.borderScaled(8, 0, 0, 0));

        String strUrl = help.strUrlFor(nMajor);
        if (strUrl != null) {
            JPanel pnlLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            pnlLeft.setOpaque(false);
            JButton btnDoc = new JButton(isReadable(strUrl) ? STR_BTN_LOCAL
                    : help.strLinkFor(nMajor));
            btnDoc.setToolTipText(strUrl);
            if (isReadable(strUrl)) {
                btnDoc.setEnabled(false);
                // MEASURED ON THE LOCAL TEXT, not on the placeholder, and
                // then opened at the maximum: the page is on its way and a
                // dialog sized to the words `Fetching documentation...` would
                // have to jump the moment it arrives.
                fit(scroll, pane, strLocal, true);
                swap(pane, strFetching());
                read(strUrl, btnDoc, pane, strLocal);
            }
            else {
                swap(pane, strLocal);
                fit(scroll, pane, strLocal, false);
                btnDoc.addActionListener(evt -> browse(strUrl, btnDoc));
            }
            pnlLeft.add(btnDoc);
            pnlBar.add(pnlLeft, BorderLayout.WEST);
        }
        else {
            swap(pane, strLocal);
            fit(scroll, pane, strLocal, false);
        }

        JPanel pnlRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pnlRight.setOpaque(false);
        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(evt -> dlg.dispose());
        pnlRight.add(btnClose);
        pnlBar.add(pnlRight, BorderLayout.EAST);

        JPanel pnlBody = new JPanel(new BorderLayout());
        pnlBody.setBorder(GuiTheme.borderScaled(12, 12, 12, 12));
        pnlBody.add(scroll, BorderLayout.CENTER);
        pnlBody.add(pnlBar, BorderLayout.SOUTH);

        dlg.setContentPane(pnlBody);
        dlg.getRootPane().setDefaultButton(btnClose);
        closeOnEscape(dlg);
        closeOnFocusLost(dlg);
        dlg.pack();
        dlg.setLocationRelativeTo(winOwner);
        dlg.setVisible(true);
    }


    /**
     * Sizes the pane to what it will hold, within the bounds.
     *
     * `JEditorPane` reports no useful preferred height until it has been laid
     * out at a known width, so it is given one and asked afterwards. The width
     * never varies, which is what keeps two dialogs opened side by side the
     * same shape.
     *
     * @param scroll what gets the size
     * @param pane the pane, used as its own measuring stick
     * @param strHtml the content to measure
     * @param flagMax whether to open at the maximum regardless
     */
    private static void fit(JScrollPane scroll, JEditorPane pane, String strHtml,
            boolean flagMax) {
        int nWidth = GuiTheme.scale(N_WIDTH);
        int nMin = GuiTheme.scale(N_HEIGHT_MIN);
        int nMax = GuiTheme.scale(N_HEIGHT_MAX);

        int nHeight = nMax;
        if (!flagMax) {
            String strWas = pane.getText();
            pane.setText(strHtml);
            pane.setSize(new Dimension(nWidth, Short.MAX_VALUE));
            // PLUS THE BORDER AND A LINE. Measured exactly, the last line sits
            // against the frame and reads as truncated even when it is not.
            nHeight = pane.getPreferredSize().height + GuiTheme.scale(24);
            pane.setText(strWas);
            nHeight = Math.max(nMin, Math.min(nMax, nHeight));
        }
        scroll.setPreferredSize(new Dimension(nWidth, nHeight));
    }


    /**
     * @param dlg the dialog
     */
    private static void closeOnEscape(JDialog dlg) {
        JComponent compRoot = dlg.getRootPane();
        compRoot.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        compRoot.getActionMap().put("close", new AbstractAction() {

            private static final long serialVersionUID = 1L;


            @Override
            public void actionPerformed(ActionEvent evt) {
                dlg.dispose();
            }
        });
    }


    /**
     * Closes when the dialog stops being the focused window, which is what a
     * click anywhere else amounts to.
     *
     * `windowLostFocus` rather than `windowDeactivated`: the latter also fires
     * when a child window of this one takes over, and a `Desktop.browse` on
     * the 2.x path would have closed the dialog before the browser had
     * finished starting.
     *
     * @param dlg the dialog
     */
    private static void closeOnFocusLost(JDialog dlg) {
        dlg.addWindowFocusListener(new WindowAdapter() {

            @Override
            public void windowLostFocus(WindowEvent evt) {
                dlg.dispose();
            }
        });
    }


    /**
     * @return the placeholder shown while the read is in flight
     */
    private static String strFetching() {
        return DocFormat.strDocument("<p>Fetching documentation...</p>");
    }


    /**
     * @param strUrl the page
     * @return whether it can be read as markdown rather than opened
     */
    private static boolean isReadable(String strUrl) {
        return strUrl.startsWith("https://docs.canton.network/");
    }


    /**
     * Fetches the page, racing a reader's deadline.
     *
     * ON A THREAD OF ITS OWN. The event dispatch thread cannot wait on a
     * network read, and on a machine with no route that wait is the whole
     * deadline.
     *
     * @param strUrl the human URL; the markdown is that plus `.md`
     * @param btn the button, which becomes the toggle once the page lands
     * @param pane where the page lands
     * @param strLocal this field's own text, already HTML
     */
    private static void read(String strUrl, JButton btn, JEditorPane pane, String strLocal) {
        btn.setEnabled(false);

        // FIRST ONE WINS. The reader's deadline and the network race each
        // other, and whichever finishes first owns the pane from then on.
        // Without this a page arriving at 3.1 s would overwrite a fallback
        // the reader was already three lines into.
        AtomicBoolean flagSettled = new AtomicBoolean();

        Timer timerDeadline = new Timer(N_MS_DEADLINE, evt -> {
            if (flagSettled.compareAndSet(false, true))
                fellBack(strUrl, btn, pane, strLocal);
        });
        timerDeadline.setRepeats(false);
        timerDeadline.start();

        Thread threadRead = new Thread(() -> {
            String strBody = null;
            String strWhy = null;
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(TIMEOUT_CONNECT)
                        .followRedirects(HttpClient.Redirect.NORMAL).build();
                HttpRequest req = HttpRequest.newBuilder(URI.create(strUrl + ".md"))
                        .timeout(TIMEOUT_READ).GET().build();
                HttpResponse<String> resp = client.send(req,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 200 && !resp.body().isBlank())
                    strBody = resp.body();
                else
                    strWhy = "the site answered " + resp.statusCode();
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                strWhy = "the read was interrupted";
            }
            catch (IOException | RuntimeException ex) {
                strWhy = strReason(ex);
            }

            String strBodyHere = strBody;
            String strWhyHere = strWhy;
            SwingUtilities.invokeLater(() -> {
                if (!flagSettled.compareAndSet(false, true))
                    return;
                timerDeadline.stop();
                if (strBodyHere != null)
                    landed(strBodyHere, strUrl, btn, pane, strLocal);
                else
                    failed(strWhyHere, strUrl, btn, pane, strLocal);
            });
        }, "help-doc-read");
        threadRead.setDaemon(true);
        threadRead.start();
    }


    /**
     * The page arrived inside the deadline: show it, and make the button the
     * way back.
     *
     * @param strBody the page as served
     * @param strUrl where it came from
     * @param btn the button, which becomes the toggle
     * @param pane where it lands
     * @param strLocal this field's own text, already HTML
     */
    private static void landed(String strBody, String strUrl, JButton btn, JEditorPane pane,
            String strLocal) {
        String strDoc = DocFormat.strDocument(DocFormat.strHtmlOfDoc(strPlain(strBody))
                + "<hr><p><i>" + DocFormat.strEscaped(strUrl) + "<br>read "
                + FMT_STAMP.format(Instant.now()) + " UTC</i></p>");
        swap(pane, strDoc);
        // ONE BUTTON, TWO STATES, and its label always names WHAT IT WILL
        // SHOW rather than what is on screen - a toggle labelled with the
        // current view reads as a statement and gets pressed by mistake.
        btn.setText(STR_BTN_LOCAL);
        clearActions(btn);
        btn.addActionListener(new ActionListener() {

            private boolean flagDoc = true;


            @Override
            public void actionPerformed(ActionEvent evt) {
                flagDoc = !flagDoc;
                swap(pane, flagDoc ? strDoc : strLocal);
                btn.setText(flagDoc ? STR_BTN_LOCAL : STR_BTN_DOC);
            }
        });
        btn.setEnabled(true);
    }


    /**
     * The deadline won: the page is not here yet, so the local text takes the
     * pane.
     *
     * NO REASON IS GIVEN, because none is known - the read has not failed, it
     * is merely slow, and "the network is slow" is not something a reader can
     * act on. The button says the page is still available and that is the
     * whole message.
     *
     * @param strUrl the page that was too slow
     * @param btn the button, which becomes the way to ask again
     * @param pane the pane
     * @param strLocal this field's own text, already HTML
     */
    private static void fellBack(String strUrl, JButton btn, JEditorPane pane,
            String strLocal) {
        swap(pane, strLocal);
        btn.setText(STR_BTN_DOC);
        clearActions(btn);
        btn.addActionListener(evt -> {
            swap(pane, strFetching());
            read(strUrl, btn, pane, strLocal);
        });
        btn.setEnabled(true);
    }


    /**
     * SAYS WHERE TO GO INSTEAD. A machine that cannot reach the page is the
     * case this application is built for, so the failure is a sentence and a
     * URL rather than an error box - and the URL goes to the clipboard, so it
     * can be carried to a machine that does have a route.
     *
     * @param strWhy what went wrong, in a few words
     * @param strUrl the page that could not be read
     * @param btn the button, which becomes Try again
     * @param pane where the message lands
     * @param strLocal this field's own text, already HTML
     */
    private static void failed(String strWhy, String strUrl, JButton btn, JEditorPane pane,
            String strLocal) {
        swap(pane, DocFormat.strDocument(strTextOf(strLocal)
                + "<hr><p>The official documentation could not be read: "
                + DocFormat.strEscaped(strWhy) + ".</p><p>Look it up at:<br><code>"
                + DocFormat.strEscaped(strUrl) + "</code></p><p>The URL is on the"
                + " clipboard. Everything above the line is local and needs no"
                + " connection.</p>"));
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(strUrl), null);
        btn.setText("Try again");
        clearActions(btn);
        btn.addActionListener(evt -> {
            swap(pane, strFetching());
            read(strUrl, btn, pane, strLocal);
        });
        btn.setEnabled(true);
    }


    /**
     * @param strHtml a document this class built
     * @return its body, so it can be rebuilt with something appended
     */
    private static String strTextOf(String strHtml) {
        int idxFrom = strHtml.indexOf("<body>");
        int idxTo = strHtml.lastIndexOf("</body>");
        if (idxFrom < 0 || idxTo < idxFrom)
            return strHtml;
        return strHtml.substring(idxFrom + "<body>".length(), idxTo);
    }


    /**
     * @param btn the button whose listeners are replaced wholesale, so a
     *        second press cannot run a previous state's action
     */
    private static void clearActions(JButton btn) {
        for (ActionListener lsn : btn.getActionListeners()) {
            btn.removeActionListener(lsn);
        }
    }


    /**
     * @param pane the pane
     * @param strHtml what it should now hold, scrolled to the top
     */
    private static void swap(JEditorPane pane, String strHtml) {
        pane.setText(strHtml);
        pane.setCaretPosition(0);
    }


    /**
     * @param ex what was thrown
     * @return a few words a reader can act on, rather than a class name
     */
    private static String strReason(Exception ex) {
        String strName = ex.getClass().getSimpleName();
        if (strName.contains("UnknownHost"))
            return "the host could not be resolved, so this machine has no DNS for it";
        if (strName.contains("HttpConnectTimeout") || strName.contains("ConnectException"))
            return "nothing answered within " + TIMEOUT_CONNECT.toSeconds() + " s";
        if (strName.contains("HttpTimeout"))
            return "it did not finish within " + TIMEOUT_READ.toMillis() + " ms";
        String strMessage = ex.getMessage();
        return strMessage == null || strMessage.isBlank() ? strName : strMessage;
    }


    /**
     * Takes the Mintlify scaffolding out of a markdown page, leaving markdown.
     *
     * WHAT IS REMOVED, and nothing else: the quoted index banner at the top,
     * and the MDX component tags named in {@link #ARR_TAG}. The tags go but
     * their CONTENT stays, because that content is the prose.
     *
     * WHAT IS KEPT: every heading, list, table and fenced code block exactly
     * as written, for {@link DocFormat} to parse. Inside a fence nothing is
     * touched at all.
     *
     * @param strMd the page as served
     * @return it, as plain markdown
     */
    static String strPlain(String strMd) {
        StringBuilder sb = new StringBuilder(strMd.length());
        boolean flagFence = false;
        boolean flagHead = true;

        for (String strLine : strMd.split("\n", -1)) {
            // THE BANNER: the quoted block at the very top, and the blank
            // lines around it. Anything quoted later in the page is the
            // author's and stays.
            if (flagHead) {
                if (strLine.isBlank())
                    continue;
                if (strLine.startsWith(STR_BANNER) || strLine.startsWith("> "))
                    continue;
                flagHead = false;
            }

            if (strLine.trim().startsWith("```")) {
                flagFence = !flagFence;
                sb.append(strLine).append('\n');
                continue;
            }
            if (flagFence) {
                sb.append(strLine).append('\n');
                continue;
            }

            String strOut = strStripped(strLine);
            // A LINE THAT WAS NOTHING BUT A TAG goes entirely, rather than
            // leaving a blank where a component opened.
            if (strOut.isBlank() && !strLine.isBlank())
                continue;
            sb.append(strOut).append('\n');
        }

        return sb.toString().replaceAll("\n{3,}", "\n\n").trim();
    }


    /**
     * @param strLine one line, outside a fenced block
     * @return it, without the MDX component tags
     */
    private static String strStripped(String strLine) {
        String strOut = strLine;
        for (String strTag : ARR_TAG) {
            strOut = strOut.replaceAll("(?i)</" + strTag + "\\s*>", "");
            strOut = strOut.replaceAll("(?i)<" + strTag + "(\\s[^<>]*)?/?>", "");
        }
        return strOut.stripTrailing();
    }


    /**
     * The Canton 2.x path, which has no markdown to read.
     *
     * @param strUrl where to go
     * @param btn the button, relabelled when no browser could be reached
     */
    private static void browse(String strUrl, JButton btn) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(strUrl));
                return;
            }
        }
        catch (RuntimeException | IOException ex) {
            // no browser, or it refused; the clipboard is the fallback
        }

        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(strUrl), null);
        btn.setText("URL copied to the clipboard");
    }

}
