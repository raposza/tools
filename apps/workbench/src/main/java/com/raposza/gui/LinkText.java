// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * A rendered block, turned into HTML with every identifier a link.
 *
 * <h2>Link text is what fits; the link TARGET is the whole id</h2>
 *
 * The pane shows `0032[...]28be` and the anchor carries all 138 characters, so
 * shortening stops being a compromise: the short form is what the eye needs and
 * the full form is what the click uses. Nothing has to be pasted for the window
 * to follow a reference.
 *
 * <h2>What is linked, and what is deliberately not</h2>
 *
 * A PARTY ID is matched whole - hint, separator and fingerprint - because the
 * fingerprint on its own is not an identifier anything can be asked about.
 *
 * A BARE HEX RUN of 40 characters or more is a contract id or an update id, and
 * which of the two is a question for the participant rather than for a regular
 * expression. It is linked and the resolver decides.
 *
 * EXACTLY 64 HEX IS NOT LINKED. That is the length of a Daml-LF package id -
 * measured against this workspace, where contract ids are 138 and update ids
 * are 68 - and nothing in this window can open a package. A link that always
 * lands on NOT FOUND is worse than no link.
 *
 * A TEMPLATE ID is recognised so its package half is not mistaken for a bare
 * id, and shortened, but not linked, for the same reason.
 *
 * <h2>The href is not a URL</h2>
 *
 * It is `kind:value`, read back through HyperlinkEvent.getDescription() rather
 * than getURL(), which would be null for a scheme no protocol handler knows.
 * The kind is carried so the window can dispatch without asking the ledger what
 * it just clicked.
 *
 * Author Claude/bentzn
 */
public final class LinkText {

    /** A contract id or an update id; the resolver tells them apart. */
    public static final String KIND_REF = "ref";

    /** A template identifier, package id and qualified name. */
    public static final String KIND_TEMPLATE = "template";

    /** A party id, matched whole. */
    public static final String KIND_PARTY = "party";

    /** The length of a Daml-LF package id, which is not a link. */
    static final int CNT_PACKAGE = 64;

    /**
     * The length of an update id, measured on this workspace.
     *
     * A HINT, NEVER A RULE. Nothing here refuses a link because of it; the
     * window uses it to decide which of the two point lookups to try first, and
     * tries the other one anyway.
     */
    public static final int CNT_UPDATE = 68;

    /**
     * The blue an identifier is drawn in.
     *
     * ONE colour in ONE place: the same contract id in a detail pane and in
     * the CaQL editor must not be two shades of the same idea.
     */
    public static final String STR_COLOUR_ID = "#0d4280";

    /**
     * The rules the pane's editor kit needs.
     *
     * The monospace rule is not decoration: every block this window renders is
     * column-aligned by hand, and HTML defaults to a proportional font that
     * turns the alignment into a mess.
     */
    private static final String[] ARR_STYLE = {
        "pre { margin: 0; }",
        "a { color: " + STR_COLOUR_ID + "; text-decoration: none; }"
    };


    private LinkText() {
    }


    /** @return the stylesheet rules the pane's editor kit needs */
    public static String[] arrStyle() {
        return ARR_STYLE.clone();
    }


    /**
     * @param strPlain the rendered block, exactly as the renderers produced it
     * @param flagShort whether identifiers are displayed in their short form
     * @return an HTML document for a JEditorPane
     */
    public static String html(String strPlain, boolean flagShort) {
        return html(strPlain, flagShort, null);
    }


    /**
     * @param strPlain the rendered block, exactly as the renderers produced it
     * @param flagShort whether identifiers are displayed in their short form
     * @param idSelf the identifier this block IS ABOUT, which is shortened like
     *        any other and never linked - a contract offering to open itself
     *        is a control that does nothing and reads as one that should
     * @return an HTML document for a JEditorPane
     */
    public static String html(String strPlain, boolean flagShort, String idSelf) {
        StringBuilder buf = new StringBuilder("<html><body><pre>");
        String strText = strPlain == null ? "" : strPlain;

        int idxAt = 0;
        for (Span span : lstSpan(strText)) {
            while (idxAt < span.idxFrom()) {
                escape(buf, strText.charAt(idxAt));
                idxAt++;
            }
            anchor(buf, strText.substring(span.idxFrom(), span.idxTo()), flagShort, idSelf);
            idxAt = span.idxTo();
        }
        while (idxAt < strText.length()) {
            escape(buf, strText.charAt(idxAt));
            idxAt++;
        }

        return buf.append("</pre></body></html>").toString();
    }


    /** Where one identifier sits in a block. */
    public record Span(int idxFrom, int idxTo) {}


    /**
     * @param strText any block, may be null
     * @return every identifier in it, in order, LONG form only
     */
    public static List<Span> lstSpan(String strText) {
        return lstSpan(strText, false);
    }


    /**
     * ONE definition of what an identifier is, shared by the links this class
     * writes and by the colouring the CaQL editor does. Two matchers would
     * agree until they did not, and the symptom - an id that is blue in the
     * editor and plain in the pane beside it - reads as a rendering bug rather
     * than as two rules.
     *
     * @param strText any block, may be null
     * @param flagCutOk whether an ABBREVIATED id counts. The editor holds the
     *        form the panes display, so its ids carry the cut marker and fall
     *        far short of the length rules below. A rendered block holds the
     *        long form and must NOT have those rules relaxed: a link built
     *        from an abbreviation resolves to nothing
     * @return every identifier, in order
     */
    public static List<Span> lstSpan(String strText, boolean flagCutOk) {
        List<Span> lstOut = new ArrayList<>();
        if (strText == null)
            return List.copyOf(lstOut);

        int idxAt = 0;
        while (idxAt < strText.length()) {
            int idxEnd = flagStart(strText, idxAt) ? idxToken(strText, idxAt, flagCutOk) : -1;
            if (idxEnd < 0) {
                idxAt++;
                continue;
            }

            lstOut.add(new Span(idxAt, idxEnd));
            idxAt = idxEnd;
        }
        return List.copyOf(lstOut);
    }


    /**
     * Writes one identifier, as a link when it is one this window can follow.
     *
     * @param buf where it goes
     * @param strToken the identifier, whole
     * @param flagShort whether to display the short form
     */
    private static void anchor(StringBuilder buf, String strToken, boolean flagShort,
            String idSelf) {
        String strShown = flagShort ? ShortIds.text(strToken) : strToken;
        String strKind = strToken.equals(idSelf) ? null : strKindOf(strToken);

        if (strKind == null) {
            escape(buf, strShown);
            return;
        }

        buf.append("<a href=\"").append(strKind).append(':');
        escape(buf, strToken);
        buf.append("\">");
        escape(buf, strShown);
        buf.append("</a>");
    }


    /**
     * @param strToken a recognised identifier
     * @return the link kind, or null when it is shown but not followed
     */
    private static String strKindOf(String strToken) {
        if (strToken.contains("::"))
            return KIND_PARTY;
        // A template id opens the template, which the navigator holds. The
        // BARE package id below it does not: nothing in this window opens a
        // package, and a link that always lands on NOT FOUND is worse than
        // no link.
        if (strToken.indexOf(':') >= 0)
            return KIND_TEMPLATE;
        return strToken.length() == CNT_PACKAGE ? null : KIND_REF;
    }


    /**
     * @param strText the block
     * @param idxAt a position in it
     * @return true when a token could start here, which is to say the character
     *         before it is not part of one
     */
    private static boolean flagStart(String strText, int idxAt) {
        return idxAt == 0 || !isTokenChar(strText.charAt(idxAt - 1));
    }


    /**
     * Matches the longest identifier starting here, party first.
     *
     * ORDER MATTERS. A party id begins with a hint that is not hex and would
     * otherwise never be reached; a template id begins with a 64-character hex
     * run that the bare-id rule would happily claim on its own.
     *
     * @param strText the block
     * @param idxAt where to look
     * @return the end of the identifier, exclusive, or -1 when there is none
     */
    private static int idxToken(String strText, int idxAt, boolean flagCutOk) {
        int idxEnd = idxParty(strText, idxAt, flagCutOk);
        if (idxEnd > 0)
            return idxEnd;

        idxEnd = idxTemplate(strText, idxAt);
        if (idxEnd > 0)
            return idxEnd;

        return idxHex(strText, idxAt, flagCutOk);
    }


    /** @return the end of a `hint::fingerprint`, or -1 */
    private static int idxParty(String strText, int idxAt, boolean flagCutOk) {
        int idxHint = idxAt;
        while (idxHint < strText.length() && isHintChar(strText.charAt(idxHint))) {
            idxHint++;
        }
        if (idxHint == idxAt || idxHint + 1 >= strText.length()
                || strText.charAt(idxHint) != ':' || strText.charAt(idxHint + 1) != ':')
            return -1;

        int idxEnd = idxHexEnd(strText, idxHint + 2, flagCutOk);
        if (idxEnd - idxHint - 2 >= ShortIds.CNT_NAMESPACE_MIN)
            return idxEnd;
        return flagCut(strText, idxHint + 2, idxEnd) ? idxEnd : -1;
    }


    /** @return the end of a `package:Module:Entity`, or -1 */
    private static int idxTemplate(String strText, int idxAt) {
        int idxPkg = idxHexEnd(strText, idxAt, false);
        if (idxPkg - idxAt != CNT_PACKAGE || idxPkg >= strText.length()
                || strText.charAt(idxPkg) != ':')
            return -1;

        int idxEnd = idxNameEnd(strText, idxPkg + 1);
        if (idxEnd == idxPkg + 1 || idxEnd >= strText.length() || strText.charAt(idxEnd) != ':')
            return -1;

        int idxLast = idxNameEnd(strText, idxEnd + 1);
        return idxLast == idxEnd + 1 ? -1 : idxLast;
    }


    /** @return the end of a bare hex identifier, or -1 when it is too short */
    private static int idxHex(String strText, int idxAt, boolean flagCutOk) {
        int idxEnd = idxHexEnd(strText, idxAt, flagCutOk);
        if (idxEnd - idxAt >= ShortIds.CNT_HEX_MIN)
            return idxEnd;
        return flagCut(strText, idxAt, idxEnd) ? idxEnd : -1;
    }


    /**
     * @param strText the block
     * @param idxFrom start of the run, inclusive
     * @param idxTo end of the run, exclusive
     * @return true when the run was ABBREVIATED, which is what excuses it from
     *         the length rules - the marker cannot occur in a real id
     */
    private static boolean flagCut(String strText, int idxFrom, int idxTo) {
        return idxTo > idxFrom
                && strText.substring(idxFrom, idxTo).contains(ShortIds.STR_CUT);
    }


    /**
     * @param strText the block
     * @param idxAt where the run starts
     * @param flagCutOk whether the cut marker CONTINUES the run, which is what
     *        keeps an abbreviated id one token instead of three
     * @return the end of the run, exclusive
     */
    private static int idxHexEnd(String strText, int idxAt, boolean flagCutOk) {
        int idxEnd = idxAt;
        while (idxEnd < strText.length()) {
            if (isHex(strText.charAt(idxEnd))) {
                idxEnd++;
                continue;
            }
            if (flagCutOk && strText.startsWith(ShortIds.STR_CUT, idxEnd)) {
                idxEnd += ShortIds.STR_CUT.length();
                continue;
            }
            break;
        }
        return idxEnd;
    }


    private static int idxNameEnd(String strText, int idxAt) {
        int idxEnd = idxAt;
        while (idxEnd < strText.length() && isNameChar(strText.charAt(idxEnd))) {
            idxEnd++;
        }
        return idxEnd;
    }


    private static boolean isTokenChar(char chAt) {
        return isHintChar(chAt) || chAt == ':';
    }


    private static boolean isHintChar(char chAt) {
        return Character.isLetterOrDigit(chAt) || chAt == '-' || chAt == '_' || chAt == '.';
    }


    private static boolean isNameChar(char chAt) {
        return Character.isLetterOrDigit(chAt) || chAt == '_' || chAt == '.';
    }


    private static boolean isHex(char chAt) {
        return (chAt >= '0' && chAt <= '9') || (chAt >= 'a' && chAt <= 'f')
                || (chAt >= 'A' && chAt <= 'F');
    }


    private static void escape(StringBuilder buf, String strText) {
        for (int cntLoop = 0; cntLoop < strText.length(); cntLoop++) {
            escape(buf, strText.charAt(cntLoop));
        }
    }


    private static void escape(StringBuilder buf, char chAt) {
        switch (chAt) {
            case '&' -> buf.append("&amp;");
            case '<' -> buf.append("&lt;");
            case '>' -> buf.append("&gt;");
            case '"' -> buf.append("&quot;");
            default -> buf.append(chAt);
        }
    }

}
