// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import java.awt.Color;
import java.awt.Font;

import java.util.ArrayList;
import java.util.List;

import javax.swing.UIManager;

/**
 * Markdown, and this application's own prose, rendered as the HTML subset
 * Swing understands.
 *
 * <h2>Why HTML at all, having argued against it</h2>
 *
 * The dialog used a `JTextArea` precisely because both halves carry angle
 * brackets - the local text has `<port number>` in it, the fetched pages have
 * fenced shell and generics - and an HTML pane eats them silently.
 *
 * That objection was to putting RAW text into an HTML pane. It does not apply
 * to text that has been escaped first, which is what {@link #strEscaped} does
 * before a single tag is generated. Everything below builds markup out of
 * already-escaped content, so there is no path by which a character from the
 * source can be read as markup. The reward is real headings, real code blocks
 * and real tables instead of a wall of monospaced asterisks.
 *
 * <h2>What Swing's HTML actually is</h2>
 *
 * `HTMLEditorKit` is HTML 3.2 with a little CSS, not a browser. So: no flex,
 * no `rem`, point sizes only, and a `&lt;table&gt;` needs its border on the
 * element. Everything emitted here has been kept inside that subset
 * deliberately - it is not laziness, it is the only thing that renders.
 *
 * <h2>The two entry points are not the same job</h2>
 *
 * {@link #strHtmlOfDoc} is a markdown reader: the vendor writes markdown and
 * it is parsed as such. {@link #strHtmlOfText} is NOT - the local texts are
 * prose whose line breaks were placed on purpose, so every newline is kept as
 * written rather than being reflowed by a paragraph rule the author never
 * agreed to.
 *
 * Author Claude/bentzn
 */
final class DocFormat {

    /**
     * The stylesheet, built at call time rather than held as a constant.
     *
     * IT HAS TO TRACK THE REST OF THE WINDOW. A fixed `10pt` rendered legibly
     * on a 96 dpi screen and microscopic beside a scaled FlatLaf on this one -
     * the pane was the only thing in the dialog that had not been told about
     * the display. So the sizes come off `Label.font`, which is what every
     * other control in the window is already using, and the colour off
     * `Label.foreground`, which is what stops the body text rendering in
     * whatever navy `HTMLEditorKit` defaults to.
     *
     * POINTS, not `rem` or `em` on the body: `HTMLEditorKit` is HTML 3.2 with
     * a little CSS and understands neither.
     *
     * @return the stylesheet for the current look and feel
     */
    private static String strCss() {
        Font font = UIManager.getFont("Label.font");
        int nPt = font == null ? 12 : font.getSize();
        String strFace = font == null ? "sans-serif" : font.getFamily();
        String strCol = strHex(UIManager.getColor("Label.foreground"), "#1e1e1e");
        String strMuted = strHex(UIManager.getColor("Label.disabledForeground"), "#6b7280");

        return "body { font-family: '" + strFace + "'; font-size: " + nPt + "pt;"
                + " color: " + strCol + "; margin: " + (nPt / 2) + "px; }"
                + " h1 { font-size: " + (nPt + 5) + "pt; margin: 14px 0 6px 0; }"
                + " h2 { font-size: " + (nPt + 3) + "pt; margin: 12px 0 5px 0; }"
                + " h3 { font-size: " + (nPt + 1) + "pt; margin: 10px 0 4px 0; }"
                + " p { margin: 6px 0 6px 0; }"
                + " ul, ol { margin: 4px 0 8px 20px; }"
                + " li { margin: 3px 0 3px 0; }"
                + " code { font-family: '" + GuiTheme.strFamilyMono() + "'; font-size: " + nPt + "pt; }"
                + " pre { font-family: '" + GuiTheme.strFamilyMono() + "'; font-size: " + nPt + "pt;"
                + " margin: 8px 0 8px 0; }"
                + " blockquote { margin: 6px 0 6px 16px; color: " + strMuted + "; }"
                + " th { font-weight: bold; text-align: left; padding: 3px 10px 3px 5px; }"
                + " td { padding: 3px 10px 3px 5px; }"
                + " i { color: " + strMuted + "; }";
    }


    /**
     * @param col the colour, or null
     * @param strFallback what to use when the look and feel has no such key
     * @return it as `#rrggbb`
     */
    private static String strHex(Color col, String strFallback) {
        if (col == null)
            return strFallback;
        return String.format("#%02x%02x%02x", col.getRed(), col.getGreen(), col.getBlue());
    }


    private DocFormat() {
        throw new AssertionError("no instances");
    }


    /**
     * @param strBody the body markup, already built
     * @return a complete document Swing will render
     */
    static String strDocument(String strBody) {
        return "<html><head><style>" + strCss() + "</style></head><body>" + strBody
                + "</body></html>";
    }


    /**
     * This application's own text.
     *
     * EVERY NEWLINE IS KEPT. These entries carry hand-placed line structure -
     * the four auth modes each on their own line, an openssl command on its
     * own - and a reflow would run them together into prose that says the
     * same words in a worse order.
     *
     * @param strText the local text
     * @return it, as HTML
     */
    static String strHtmlOfText(String strText) {
        StringBuilder sb = new StringBuilder();
        for (String strPara : strText.split("\n\\s*\n")) {
            if (strPara.isBlank())
                continue;
            sb.append("<p>").append(strInline(strEscaped(strPara).trim())
                    .replace("\n", "<br>")).append("</p>");
        }
        return sb.toString();
    }


    /**
     * A markdown page.
     *
     * @param strMd the page, with its MDX scaffolding already removed
     * @return it, as HTML
     */
    static String strHtmlOfDoc(String strMd) {
        List<String> lstLine = new ArrayList<>(List.of(strMd.split("\n", -1)));
        StringBuilder sb = new StringBuilder();
        int idx = 0;

        while (idx < lstLine.size()) {
            String strLine = lstLine.get(idx);
            String strTrim = strLine.trim();

            if (strTrim.isEmpty()) {
                idx++;
                continue;
            }

            // FENCED CODE FIRST, and everything inside it is escaped and
            // otherwise untouched. A fence that never closes runs to the end
            // of the page rather than throwing away the rest of it.
            if (strTrim.startsWith("```")) {
                idx++;
                StringBuilder sbCode = new StringBuilder();
                while (idx < lstLine.size() && !lstLine.get(idx).trim().startsWith("```")) {
                    sbCode.append(strEscaped(lstLine.get(idx))).append('\n');
                    idx++;
                }
                idx++;
                sb.append("<pre>").append(sbCode.toString().stripTrailing()).append("</pre>");
                continue;
            }

            if (strTrim.startsWith("#")) {
                int cntHash = 0;
                while (cntHash < strTrim.length() && strTrim.charAt(cntHash) == '#') {
                    cntHash++;
                }
                int nLevel = Math.min(3, cntHash);
                sb.append("<h").append(nLevel).append('>')
                        .append(strInline(strEscaped(strTrim.substring(cntHash).trim())))
                        .append("</h").append(nLevel).append('>');
                idx++;
                continue;
            }

            // A RULE, but not a table separator and not a list bullet.
            if (strTrim.equals("---") || strTrim.equals("***") || strTrim.equals("___")) {
                sb.append("<hr>");
                idx++;
                continue;
            }

            if (isTableRow(strTrim) && idx + 1 < lstLine.size()
                    && isTableRule(lstLine.get(idx + 1).trim())) {
                idx = nTable(lstLine, idx, sb);
                continue;
            }

            if (isBullet(strTrim) || isNumbered(strTrim)) {
                idx = nList(lstLine, idx, sb);
                continue;
            }

            if (strTrim.startsWith(">")) {
                StringBuilder sbQuote = new StringBuilder();
                while (idx < lstLine.size() && lstLine.get(idx).trim().startsWith(">")) {
                    sbQuote.append(lstLine.get(idx).trim().substring(1).trim()).append(' ');
                    idx++;
                }
                sb.append("<blockquote>")
                        .append(strInline(strEscaped(sbQuote.toString().trim())))
                        .append("</blockquote>");
                continue;
            }

            // A PARAGRAPH runs to the next blank line or to anything that
            // opens a block of its own.
            StringBuilder sbPara = new StringBuilder();
            while (idx < lstLine.size()) {
                String strHere = lstLine.get(idx).trim();
                if (strHere.isEmpty() || strHere.startsWith("#") || strHere.startsWith("```")
                        || strHere.startsWith(">") || isBullet(strHere)
                        || isNumbered(strHere) || isTableRow(strHere)) {
                    break;
                }
                sbPara.append(strHere).append(' ');
                idx++;
            }
            sb.append("<p>").append(strInline(strEscaped(sbPara.toString().trim())))
                    .append("</p>");
        }
        return sb.toString();
    }


    /**
     * @param lstLine every line of the page
     * @param idxFrom where the list starts
     * @param sb where the markup goes
     * @return the line after the list
     */
    private static int nList(List<String> lstLine, int idxFrom, StringBuilder sb) {
        boolean flagOrdered = isNumbered(lstLine.get(idxFrom).trim());
        sb.append(flagOrdered ? "<ol>" : "<ul>");
        int idx = idxFrom;

        while (idx < lstLine.size()) {
            String strTrim = lstLine.get(idx).trim();
            if (strTrim.isEmpty()) {
                // A BLANK LINE INSIDE A LIST is a loose list, not the end of
                // one. It ends only when the next non-blank line is not an
                // item.
                int idxPeek = idx + 1;
                while (idxPeek < lstLine.size() && lstLine.get(idxPeek).isBlank()) {
                    idxPeek++;
                }
                if (idxPeek >= lstLine.size())
                    break;
                String strNext = lstLine.get(idxPeek).trim();
                if (!isBullet(strNext) && !isNumbered(strNext))
                    break;
                idx = idxPeek;
                continue;
            }
            if (!isBullet(strTrim) && !isNumbered(strTrim))
                break;

            String strItem = isBullet(strTrim) ? strTrim.substring(1).trim()
                    : strTrim.substring(strTrim.indexOf('.') + 1).trim();
            sb.append("<li>").append(strInline(strEscaped(strItem))).append("</li>");
            idx++;
        }

        sb.append(flagOrdered ? "</ol>" : "</ul>");
        return idx;
    }


    /**
     * @param lstLine every line of the page
     * @param idxFrom the header row
     * @param sb where the markup goes
     * @return the line after the table
     */
    private static int nTable(List<String> lstLine, int idxFrom, StringBuilder sb) {
        sb.append("<table border='1' cellspacing='0' cellpadding='2'>");
        sb.append("<tr>");
        for (String strCell : arrCell(lstLine.get(idxFrom))) {
            sb.append("<th>").append(strInline(strEscaped(strCell))).append("</th>");
        }
        sb.append("</tr>");

        int idx = idxFrom + 2;
        while (idx < lstLine.size() && isTableRow(lstLine.get(idx).trim())) {
            sb.append("<tr>");
            for (String strCell : arrCell(lstLine.get(idx))) {
                sb.append("<td>").append(strInline(strEscaped(strCell))).append("</td>");
            }
            sb.append("</tr>");
            idx++;
        }

        sb.append("</table>");
        return idx;
    }


    /**
     * @param strRow one table row
     * @return its cells, without the outer pipes
     */
    private static String[] arrCell(String strRow) {
        String strTrim = strRow.trim();
        if (strTrim.startsWith("|"))
            strTrim = strTrim.substring(1);
        if (strTrim.endsWith("|"))
            strTrim = strTrim.substring(0, strTrim.length() - 1);
        String[] arrOut = strTrim.split("\\|", -1);
        for (int idx = 0; idx < arrOut.length; idx++) {
            arrOut[idx] = arrOut[idx].trim();
        }
        return arrOut;
    }


    private static boolean isTableRow(String strTrim) {
        return strTrim.startsWith("|") && strTrim.length() > 1;
    }


    private static boolean isTableRule(String strTrim) {
        return isTableRow(strTrim) && strTrim.replace("|", "").replace(":", "")
                .replace("-", "").replace(" ", "").isEmpty()
                && strTrim.contains("-");
    }


    /**
     * @param strTrim a trimmed line
     * @return whether it opens a bullet, which `---` and `***` must not
     */
    private static boolean isBullet(String strTrim) {
        if (strTrim.length() < 2)
            return false;
        char ch = strTrim.charAt(0);
        return (ch == '-' || ch == '*' || ch == '+') && strTrim.charAt(1) == ' ';
    }


    private static boolean isNumbered(String strTrim) {
        int idx = 0;
        while (idx < strTrim.length() && Character.isDigit(strTrim.charAt(idx))) {
            idx++;
        }
        return idx > 0 && idx + 1 < strTrim.length() && strTrim.charAt(idx) == '.'
                && strTrim.charAt(idx + 1) == ' ';
    }


    /**
     * THE ONE THING THAT MAKES ALL OF THIS SAFE. Every character that could be
     * read as markup is turned into an entity BEFORE any tag is generated, so
     * `<port number>` in the local text and a shell redirection in a fenced
     * block both arrive as characters rather than as elements.
     *
     * @param strRaw anything at all
     * @return it, inert
     */
    static String strEscaped(String strRaw) {
        return strRaw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }


    /**
     * Inline markdown, applied to ALREADY-ESCAPED text.
     *
     * Code spans go first, because the whole point of a code span is that what
     * is inside it is not markup - and `**` inside one is an asterisk.
     *
     * @param strEsc escaped text
     * @return it, with the inline forms turned into tags
     */
    private static String strInline(String strEsc) {
        // A LINK BECOMES ITS TEXT PLUS ITS TARGET. Swing renders an anchor but
        // will not follow one without a listener, and a blue word that does
        // nothing when clicked is worse than a URL a reader can copy.
        String strOut = strEsc.replaceAll("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)", "$1 ($2)");
        strOut = strOut.replaceAll("`([^`]+)`", "<code>$1</code>");
        strOut = strOut.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        strOut = strOut.replaceAll("(?<![\\w*])\\*([^*\\n]+)\\*(?![\\w*])", "<i>$1</i>");
        strOut = strOut.replaceAll("(?<![\\w_])_([^_\\n]+)_(?![\\w_])", "<i>$1</i>");
        return strOut;
    }

}
