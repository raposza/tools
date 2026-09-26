// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.caql.CaqlParser;

import java.awt.Color;

import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Colour in the CaQL editor: comments, keywords, identifiers.
 *
 * <h2>The vocabulary is not restated here</h2>
 *
 * The keywords come from {@link CaqlParser#setKeyword()} and the identifier
 * shapes from {@link LinkText#lstSpan}. A second list of either would agree
 * with the first until it did not, and the symptom - a word the parser
 * reserves that stops being coloured, or an id that is blue in one pane and
 * plain in the next - reads as a broken editor rather than as two rules.
 *
 * <h2>It restyles the whole document, and that is not a shortcut</h2>
 *
 * A script is tens of lines. Tracking which lines a change touched would cost
 * more code than it saves and gets the interesting case wrong: opening a
 * string literal recolours everything after it. Restyling everything is
 * correct by construction.
 *
 * The work is DEFERRED to the event queue and coalesced. A document listener
 * may not mutate the document it is being notified about, and a burst of edits
 * must not queue a pass each.
 *
 * <h2>changedUpdate is deliberately ignored</h2>
 *
 * That is the notification an attribute change raises - which is what this
 * class does. Restyling from it would call itself for as long as the operator
 * was prepared to watch.
 *
 * Author Claude/bentzn
 */
public final class CaqlSyntax implements DocumentListener {

    /**
     * The editor's point size, published so the pane and the styles agree.
     * UNSCALED: every use of it goes through {@link GuiScale}.
     */
    public static final int CNT_FONT = 13;

    /** Grey: present, and quieter than the statements around it. */
    private static final Color COLOR_COMMENT = new Color(0x80, 0x80, 0x80);

    /** Dark green, and bold, which is what the keywords are asked to be. */
    private static final Color COLOR_KEYWORD = new Color(0x00, 0x64, 0x00);

    /** The blue a detail pane draws a link in. */
    private static final Color COLOR_ID = Color.decode(LinkText.STR_COLOUR_ID);

    private static final String STR_COMMENT = "--";

    private final JTextPane pane;

    private final Color colorPlain;

    /** True while a pass is already on the queue. */
    private boolean flagQueued;


    private CaqlSyntax(JTextPane paneNew) {
        this.pane = paneNew;
        this.colorPlain = paneNew.getForeground();
    }


    /**
     * @param pane the editor, which is styled now and after every change
     */
    public static void install(JTextPane pane) {
        CaqlSyntax syntax = new CaqlSyntax(pane);
        pane.getStyledDocument().addDocumentListener(syntax);
        syntax.style();
    }


    @Override
    public void insertUpdate(DocumentEvent ev) {
        queue();
    }


    @Override
    public void removeUpdate(DocumentEvent ev) {
        queue();
    }


    @Override
    public void changedUpdate(DocumentEvent ev) {
        // OURS. See the class comment.
    }


    private void queue() {
        if (flagQueued)
            return;

        flagQueued = true;
        SwingUtilities.invokeLater(() -> {
            flagQueued = false;
            style();
        });
    }


    /** Recolours the whole document. */
    private void style() {
        StyledDocument doc = pane.getStyledDocument();
        String strText;
        try {
            strText = doc.getText(0, doc.getLength());
        }
        catch (BadLocationException ex) {
            return;
        }

        doc.setCharacterAttributes(0, doc.getLength(), attr(colorPlain, false), true);

        int idxLine = 0;
        while (idxLine < strText.length()) {
            int idxEnd = strText.indexOf('\n', idxLine);
            if (idxEnd < 0)
                idxEnd = strText.length();

            line(doc, strText, idxLine, idxEnd);
            idxLine = idxEnd + 1;
        }
    }


    /**
     * One line: comment first, then keywords, then identifiers.
     *
     * IDENTIFIERS LAST, because they win. A party id whose hint happens to
     * read as a reserved word would otherwise be half green, and the id is the
     * thing the eye is looking for.
     *
     * @param doc the document
     * @param strText the whole text
     * @param idxFrom start of the line
     * @param idxTo end of the line, exclusive of its newline
     */
    private void line(StyledDocument doc, String strText, int idxFrom, int idxTo) {
        int idxCode = idxComment(strText, idxFrom, idxTo);
        if (idxCode < idxTo)
            doc.setCharacterAttributes(idxCode, idxTo - idxCode, attr(COLOR_COMMENT, false), true);

        keywords(doc, strText, idxFrom, idxCode);

        String strCode = strText.substring(idxFrom, idxCode);
        for (LinkText.Span span : LinkText.lstSpan(strCode, true)) {
            doc.setCharacterAttributes(idxFrom + span.idxFrom(),
                    span.idxTo() - span.idxFrom(), attr(COLOR_ID, false), true);
        }
    }


    /**
     * A `--` INSIDE A STRING IS NOT A COMMENT, which is the splitter's rule
     * and the one thing this scan has to get right. A text literal cannot span
     * a line, so the state resets here and nothing has to be carried between
     * lines.
     *
     * @param strText the whole text
     * @param idxFrom start of the line
     * @param idxTo end of the line, exclusive
     * @return where the comment begins, or idxTo when the line has none
     */
    private static int idxComment(String strText, int idxFrom, int idxTo) {
        boolean flagString = false;
        boolean flagEscape = false;

        for (int idxAt = idxFrom; idxAt < idxTo; idxAt++) {
            char chAt = strText.charAt(idxAt);

            if (flagString) {
                if (flagEscape)
                    flagEscape = false;
                else if (chAt == '\\')
                    flagEscape = true;
                else if (chAt == '"')
                    flagString = false;
                continue;
            }

            if (chAt == '"') {
                flagString = true;
                continue;
            }

            if (strText.startsWith(STR_COMMENT, idxAt) && idxAt + 1 < idxTo)
                return idxAt;
        }
        return idxTo;
    }


    /**
     * Reserved words, OUTSIDE string literals only. A payload field spelled
     * like a keyword is a payload field, and the parser reads it as one.
     *
     * @param doc the document
     * @param strText the whole text
     * @param idxFrom start of the code part of the line
     * @param idxTo end of the code part, exclusive
     */
    private void keywords(StyledDocument doc, String strText, int idxFrom, int idxTo) {
        boolean flagString = false;
        boolean flagEscape = false;
        int idxWord = -1;

        for (int idxAt = idxFrom; idxAt <= idxTo; idxAt++) {
            char chAt = idxAt < idxTo ? strText.charAt(idxAt) : ' ';

            if (flagString) {
                if (flagEscape)
                    flagEscape = false;
                else if (chAt == '\\')
                    flagEscape = true;
                else if (chAt == '"')
                    flagString = false;
                continue;
            }

            if (Character.isLetter(chAt)) {
                if (idxWord < 0)
                    idxWord = idxAt;
                continue;
            }

            if (idxWord >= 0) {
                if (CaqlParser.setKeyword().contains(strText.substring(idxWord, idxAt))) {
                    doc.setCharacterAttributes(idxWord, idxAt - idxWord,
                            attr(COLOR_KEYWORD, true), true);
                }
                idxWord = -1;
            }

            if (chAt == '"')
                flagString = true;
        }
    }


    /**
     * Every style carries the font, because each is applied with REPLACE and
     * a set that named only a colour would leave the run resolving its family
     * elsewhere.
     *
     * @param color the foreground
     * @param flagBold whether it is bold
     * @return the attributes
     */
    private static AttributeSet attr(Color color, boolean flagBold) {
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attr, GuiDesign.strFamilyMono());
        StyleConstants.setFontSize(attr, GuiScale.scale(CNT_FONT));
        StyleConstants.setForeground(attr, color);
        StyleConstants.setBold(attr, flagBold);
        return attr;
    }

}
