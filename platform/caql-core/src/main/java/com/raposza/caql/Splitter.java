// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a script into logical statements.
 *
 * <h2>Why this is a separate pass</h2>
 *
 * A statement ends at a {@code ;} and at nothing else. A newline is whitespace
 * wherever it falls, so a statement may be laid out over as many lines as it
 * reads well on. That rule is about characters, not tokens, so doing it before
 * lexing keeps the lexer from having to know about lines at all - and keeps the
 * parser from having to know about either.
 *
 * <h2>The terminator is MANDATORY, including on the last statement</h2>
 *
 * Operator decision, 2026-09-14. There is no end-of-file exemption: a script
 * whose final statement carries no {@code ;} is refused, and refused before
 * anything is sent. An optional terminator would mean two end-of-statement
 * rules living side by side, and the one that ends at a newline is the one that
 * quietly wins whenever a line is wrapped.
 *
 * THE TERMINATOR IS CONSUMED HERE and never reaches {@code strSource}. So a
 * statement id - the hash of the normalised source, {@code Runner.normalise} -
 * is exactly what it was before semicolons existed, and a transcript taken
 * before this change still compares against one taken after it.
 *
 * <h2>The three things it has to get right</h2>
 *
 * All three are the same mistake in different clothes: treating a character
 * inside a string literal as syntax.
 *
 * <ul>
 * <li>A {@code ;} or a closing brace inside a JSON string is not syntax, so
 *     {@code {"label": ";"}} is one statement and not a truncated one.</li>
 * <li>A {@code --} inside a string is not a comment, so a contract id or a
 *     label containing one survives.</li>
 * <li>A backslash escape hides the quote after it, so {@code "a\""} is one
 *     string and not two.</li>
 * </ul>
 *
 * A string that reaches end of line unterminated fails HERE rather than
 * producing a statement that lexes into nonsense several stages later. A text
 * literal still does not span lines: JSON has no raw newline inside a string
 * either, and the two notations are deliberately the same one.
 *
 * Author Claude/bentzn
 */
final class Splitter {

    private Splitter() {
    }


    /**
     * @param strScript the whole script
     * @return one entry per statement, blank lines and comments removed
     * @throws CaqlException on an unterminated string, an unclosed object, or a
     *                       statement with no ';' after it
     */
    static List<Logical> split(String strScript) {
        if (strScript == null)
            throw new CaqlException(0, "", "no script supplied");

        List<Logical> lstOut = new ArrayList<>();
        StringBuilder bld = new StringBuilder();
        List<Integer> lstNewline = new ArrayList<>();

        int numLine = 1;
        int numStart = 1;
        int cntBrace = 0;
        boolean flagString = false;
        boolean flagEscape = false;

        int cntLoop = 0;
        while (cntLoop < strScript.length()) {
            char ch = strScript.charAt(cntLoop);

            if (flagString) {
                if (flagEscape) {
                    flagEscape = false;
                }
                else if (ch == '\\') {
                    flagEscape = true;
                }
                else if (ch == '"') {
                    flagString = false;
                }
                else if (ch == '\n') {
                    throw new CaqlException(numLine, bld.toString().trim(),
                            "a text literal is not closed before the end of the line");
                }
                bld.append(ch);
                cntLoop++;
                continue;
            }

            // A carriage return is never syntax. Skipped rather than appended,
            // because a CRLF file would otherwise leave one inside a joined
            // multi-line statement where trim() cannot reach it.
            if (ch == '\r') {
                cntLoop++;
                continue;
            }

            // Outside a string. Now, and only now, -- is a comment. The newline
            // that ends it is left for the branch below, which is what keeps
            // the line count right.
            if (ch == '-' && cntLoop + 1 < strScript.length()
                    && strScript.charAt(cntLoop + 1) == '-') {
                while (cntLoop < strScript.length() && strScript.charAt(cntLoop) != '\n') {
                    cntLoop++;
                }
                continue;
            }

            if (ch == '"') {
                flagString = true;
                bld.append(ch);
                cntLoop++;
                continue;
            }

            if (ch == '{') {
                cntBrace++;
                bld.append(ch);
                cntLoop++;
                continue;
            }

            if (ch == '}') {
                cntBrace--;
                if (cntBrace < 0) {
                    throw new CaqlException(numLine, bld.toString().trim(),
                            "a closing brace with nothing open");
                }
                bld.append(ch);
                cntLoop++;
                continue;
            }

            // The terminator. Checked against the brace depth as well as
            // against the string flag: JSON has no use for a bare semicolon,
            // and a depth test costs nothing to keep the two independent.
            if (ch == ';' && cntBrace == 0) {
                if (bld.toString().trim().isEmpty()) {
                    throw new CaqlException(numLine, "",
                            "a ';' with no statement in front of it");
                }
                emit(lstOut, bld, lstNewline, numStart);
                numStart = numLine;
                cntLoop++;
                continue;
            }

            if (ch == '\n') {
                // A newline is whitespace. Inside a statement it keeps the
                // shape on the page and loses it in the token stream; between
                // statements it moves the line a statement is reported on.
                if (bld.length() > 0) {
                    // Where the newline WAS, so a line can still be named
                    // for an offset into the joined text - which is what a
                    // missing ';' needs: the end of the line before the
                    // next statement, not the start of this one.
                    lstNewline.add(bld.length());
                    bld.append(' ');
                }
                numLine++;
                if (bld.length() == 0)
                    numStart = numLine;
                cntLoop++;
                continue;
            }

            if (bld.length() == 0 && Character.isWhitespace(ch)) {
                // Leading whitespace decides where the statement starts, so the
                // reported line is the first line with content on it.
                numStart = numLine;
                cntLoop++;
                continue;
            }

            bld.append(ch);
            cntLoop++;
        }

        if (flagString)
            throw new CaqlException(numStart, bld.toString().trim(), "a text literal is not closed");
        if (cntBrace > 0) {
            throw new CaqlException(numStart, bld.toString().trim(),
                    "a JSON object is not closed before the end of the script");
        }

        // Whatever is left is a statement nobody terminated. Refused rather
        // than emitted: an end-of-file exemption is the optional terminator
        // arriving through the back door.
        String strLeft = bld.toString().trim();
        if (!strLeft.isEmpty()) {
            Logical tail = new Logical(numStart, strLeft, arrOf(lstNewline));
            throw new CaqlException(numStart, strLeft,
                    "this statement has no ';' after it; every CaQL statement ends with one,"
                            + " the last one included",
                    tail.lineAt(strLeft.length() - 1));
        }

        return List.copyOf(lstOut);
    }


    /**
     * The character span of the statement the caret is sitting in.
     *
     * HERE RATHER THAN IN THE EDITOR, because it is the same scan
     * {@link #split} performs and a second copy in a Swing class would agree
     * with this one until the day it did not. The editor needs offsets rather
     * than statements: it sends the SOURCE back to be parsed, so what it cuts
     * has to be bytes of the script and not a reconstruction of them.
     *
     * The span runs from the statement's first content character through its
     * terminating ';' inclusive, so the text it names parses on its own. A
     * caret in a comment or in the blank space between statements belongs to
     * no statement and is answered with null.
     *
     * @param strScript the whole editor text
     * @param idxCaret the caret offset
     * @return {from, toExclusive}, or null when the caret is in no statement
     */
    static int[] spanAt(String strScript, int idxCaret) {
        if (strScript == null || idxCaret < 0)
            return null;

        int idxStart = -1;
        int cntBrace = 0;
        boolean flagString = false;
        boolean flagEscape = false;

        int cntLoop = 0;
        while (cntLoop < strScript.length()) {
            char ch = strScript.charAt(cntLoop);

            if (flagString) {
                if (flagEscape) {
                    flagEscape = false;
                }
                else if (ch == '\\') {
                    flagEscape = true;
                }
                else if (ch == '"') {
                    flagString = false;
                }
                cntLoop++;
                continue;
            }

            if (ch == '-' && cntLoop + 1 < strScript.length()
                    && strScript.charAt(cntLoop + 1) == '-') {
                while (cntLoop < strScript.length() && strScript.charAt(cntLoop) != '\n') {
                    cntLoop++;
                }
                continue;
            }

            if (ch == ';' && cntBrace == 0) {
                if (idxStart >= 0 && idxCaret >= idxStart && idxCaret <= cntLoop + 1)
                    return new int[] {idxStart, cntLoop + 1};
                idxStart = -1;
                cntLoop++;
                continue;
            }

            if (ch == '"')
                flagString = true;
            else if (ch == '{')
                cntBrace++;
            else if (ch == '}' && cntBrace > 0)
                cntBrace--;

            if (idxStart < 0 && !Character.isWhitespace(ch))
                idxStart = cntLoop;
            cntLoop++;
        }

        // An unterminated tail still belongs to the caret sitting in it. Run it
        // and the refusal names the missing ';' - which is more use than a
        // Ctrl-Enter that silently does nothing.
        if (idxStart >= 0 && idxCaret >= idxStart)
            return new int[] {idxStart, strScript.length()};
        return null;
    }


    private static void emit(List<Logical> lstOut, StringBuilder bld, List<Integer> lstNewline,
            int numStart) {
        String str = bld.toString().trim();
        bld.setLength(0);
        if (!str.isEmpty())
            lstOut.add(new Logical(numStart, str, arrOf(lstNewline)));
        lstNewline.clear();
    }


    private static int[] arrOf(List<Integer> lst) {
        int[] arr = new int[lst.size()];
        for (int cntLoop = 0; cntLoop < arr.length; cntLoop++) {
            arr[cntLoop] = lst.get(cntLoop);
        }
        return arr;
    }


    /**
     * @param numLine one-based line the statement starts on
     * @param strSource the statement, comments stripped, continuations joined
     *                  and the terminating ';' removed; this is what a
     *                  transcript records as written
     * @param arrNewline offsets into strSource where a newline became the
     *                   joining space, ascending; empty for a one-line
     *                   statement
     */
    record Logical(int numLine, String strSource, int[] arrNewline) {

        /** A one-line statement, which is what every test builds. */
        Logical(int numLine, String strSource) {
            this(numLine, strSource, new int[0]);
        }


        /**
         * @param idx an offset into strSource
         * @return the one-based script line that character came from
         */
        int lineAt(int idx) {
            int num = numLine;
            for (int at : arrNewline) {
                if (at < idx)
                    num++;
            }
            return num;
        }

    }

}
