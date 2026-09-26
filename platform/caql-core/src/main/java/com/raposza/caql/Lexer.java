// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns one logical statement into tokens.
 *
 * <h2>Jackson decodes both kinds of literal, and that is deliberate</h2>
 *
 * A quoted text and a JSON object are both handed to Jackson - the text by
 * quoting it back into a one-string document. So {@code "\u00e7"}, a surrogate
 * pair and an embedded quote mean here exactly what they mean in a {@code WITH}
 * payload, because the same code decides. Hand-rolling escape handling would
 * have produced two notations that agree until they do not, and the language's
 * whole claim in sec. 5 is that there is ONE notation.
 *
 * <h2>One token kind for words</h2>
 *
 * Keywords, binding names, choice names and template references are all WORD.
 * The parser tells them apart by POSITION, which the grammar makes possible:
 * every statement's shape is fixed by its second or third word. A lexer that
 * classified keywords would have to know that {@code CREATE} is a verb after
 * {@code AS} and part of {@code CREATE USER} otherwise, which is the parser's
 * business.
 *
 * A template reference lexes whole - dots and colons included - because it is
 * resolved against a registry snapshot later and splitting it here would just
 * mean rejoining it there.
 *
 * <h2>A VAR lexes whole too, dots included</h2>
 *
 * {@code $r.owner.party} is ONE token whose text is {@code r.owner.party}, and
 * the parser splits it. The alternative - VAR, DOT, WORD, DOT, WORD - would put
 * a dot in the token stream, where the grammar has no other use for one and
 * every statement rule would have to say so.
 *
 * Author Claude/bentzn
 */
final class Lexer {

    private static final ObjectMapper MAPPER = new ObjectMapper();


    private Lexer() {
    }


    /**
     * @param logical one statement
     * @return its tokens, in order
     * @throws CaqlException on an unreadable literal or a stray character
     */
    static List<Token> lex(Splitter.Logical logical) {
        String str = logical.strSource();
        List<Token> lstToken = new ArrayList<>();

        int cntLoop = 0;
        while (cntLoop < str.length()) {
            char ch = str.charAt(cntLoop);

            if (Character.isWhitespace(ch)) {
                cntLoop++;
                continue;
            }

            if (ch == ',') {
                lstToken.add(new Token(Token.Kind.COMMA, ",", null, cntLoop));
                cntLoop++;
                continue;
            }

            if (ch == '=') {
                lstToken.add(new Token(Token.Kind.EQ, "=", null, cntLoop));
                cntLoop++;
                continue;
            }

            // The WHERE operators. A second character is looked at only for
            // the two that have one, so '<' followed by anything else is the
            // one-character token and the parser says what it expected next.
            if (ch == '<' || ch == '>') {
                boolean flagEq = cntLoop + 1 < str.length() && str.charAt(cntLoop + 1) == '=';
                Token.Kind kind = ch == '<' ? (flagEq ? Token.Kind.LE : Token.Kind.LT)
                        : (flagEq ? Token.Kind.GE : Token.Kind.GT);
                lstToken.add(new Token(kind, flagEq ? ch + "=" : String.valueOf(ch), null,
                        cntLoop));
                cntLoop += flagEq ? 2 : 1;
                continue;
            }

            if (ch == '(' || ch == ')') {
                lstToken.add(new Token(ch == '(' ? Token.Kind.LPAREN : Token.Kind.RPAREN,
                        String.valueOf(ch), null, cntLoop));
                cntLoop++;
                continue;
            }

            // A negative number is a WORD, as a positive one already is: digits
            // start a word and '-' continues one, so only the LEADING minus
            // needs admitting here. It is admitted only when a digit follows
            // it, so a stray '-' is still the unexpected character it was.
            if (ch == '-' && cntLoop + 1 < str.length()
                    && Character.isDigit(str.charAt(cntLoop + 1))) {
                int numEnd = cntLoop + 1;
                while (numEnd < str.length() && isWordPart(str.charAt(numEnd))) {
                    numEnd++;
                }
                lstToken.add(new Token(Token.Kind.WORD, str.substring(cntLoop, numEnd), null,
                        cntLoop));
                cntLoop = numEnd;
                continue;
            }

            if (ch == '$') {
                int numEnd = cntLoop + 1;
                while (numEnd < str.length() && isIdent(str.charAt(numEnd), numEnd == cntLoop + 1)) {
                    numEnd++;
                }
                if (numEnd == cntLoop + 1) {
                    throw new CaqlException(logical.numLine(), str,
                            "'$' must be followed by a binding name");
                }

                // A dot CONTINUES the reference, and only when a name follows
                // it. A trailing dot is refused here rather than lexing into a
                // stray character several tokens later, and it is refused with
                // the reference in hand so the message can say what was wrong.
                while (numEnd < str.length() && str.charAt(numEnd) == '.') {
                    int numField = numEnd + 1;
                    while (numField < str.length()
                            && isIdent(str.charAt(numField), numField == numEnd + 1)) {
                        numField++;
                    }
                    if (numField == numEnd + 1) {
                        throw new CaqlException(logical.numLine(), str,
                                "'" + str.substring(cntLoop, numEnd + 1)
                                        + "' ends in a dot; a field name must follow it");
                    }
                    numEnd = numField;
                }

                lstToken.add(new Token(Token.Kind.VAR, str.substring(cntLoop + 1, numEnd), null,
                        cntLoop));
                cntLoop = numEnd;
                continue;
            }

            if (ch == '"') {
                int numEnd = endOfText(str, cntLoop, logical);
                String strRaw = str.substring(cntLoop, numEnd + 1);
                lstToken.add(new Token(Token.Kind.TEXT, decodeText(strRaw, logical), null,
                        cntLoop));
                cntLoop = numEnd + 1;
                continue;
            }

            if (ch == '{') {
                int numEnd = endOfJson(str, cntLoop, logical);
                String strRaw = str.substring(cntLoop, numEnd + 1);
                lstToken.add(new Token(Token.Kind.JSON, strRaw, decodeJson(strRaw, logical),
                        cntLoop));
                cntLoop = numEnd + 1;
                continue;
            }

            if (isWordStart(ch)) {
                int numEnd = cntLoop;
                while (numEnd < str.length() && isWordPart(str.charAt(numEnd))) {
                    numEnd++;
                }
                lstToken.add(new Token(Token.Kind.WORD, str.substring(cntLoop, numEnd), null,
                        cntLoop));
                cntLoop = numEnd;
                continue;
            }

            // '!' is refused HERE rather than as an unexpected character,
            // because the one thing it is ever typed for is '!=', and the
            // parser never sees the statement to say what to write instead.
            if (ch == '!') {
                throw new CaqlException(logical.numLine(), str,
                        "there is no !=; write NOT <field> = <value> instead");
            }

            throw new CaqlException(logical.numLine(), str,
                    "unexpected character '" + ch + "'");
        }

        return List.copyOf(lstToken);
    }


    /**
     * The splitter already proved the literal closes somewhere in this
     * statement, so this cannot run off the end - but it is written to fail
     * rather than to rely on that, because the two would drift.
     */
    private static int endOfText(String str, int numStart, Splitter.Logical logical) {
        boolean flagEscape = false;
        for (int cntLoop = numStart + 1; cntLoop < str.length(); cntLoop++) {
            char ch = str.charAt(cntLoop);
            if (flagEscape) {
                flagEscape = false;
                continue;
            }
            if (ch == '\\') {
                flagEscape = true;
                continue;
            }
            if (ch == '"')
                return cntLoop;
        }
        throw new CaqlException(logical.numLine(), str, "a text literal is not closed");
    }


    private static int endOfJson(String str, int numStart, Splitter.Logical logical) {
        int cntBrace = 0;
        boolean flagString = false;
        boolean flagEscape = false;

        for (int cntLoop = numStart; cntLoop < str.length(); cntLoop++) {
            char ch = str.charAt(cntLoop);

            if (flagString) {
                if (flagEscape)
                    flagEscape = false;
                else if (ch == '\\')
                    flagEscape = true;
                else if (ch == '"')
                    flagString = false;
                continue;
            }

            if (ch == '"') {
                flagString = true;
            }
            else if (ch == '{') {
                cntBrace++;
            }
            else if (ch == '}') {
                cntBrace--;
                if (cntBrace == 0)
                    return cntLoop;
            }
        }
        throw new CaqlException(logical.numLine(), str, "a JSON object is not closed");
    }


    /**
     * Quoted back into a one-string JSON document rather than unescaped by
     * hand, so a text literal and a string inside a WITH payload decode
     * identically. They are the same notation and this is what makes that true
     * rather than intended.
     */
    private static String decodeText(String strRaw, Splitter.Logical logical) {
        try {
            JsonNode node = MAPPER.readTree(strRaw);
            if (!node.isTextual())
                throw new CaqlException(logical.numLine(), logical.strSource(),
                        "expected a text literal");
            return node.textValue();
        }
        catch (JsonProcessingException ex) {
            throw new CaqlException(logical.numLine(), logical.strSource(),
                    "text literal cannot be read: " + ex.getOriginalMessage());
        }
    }


    private static JsonNode decodeJson(String strRaw, Splitter.Logical logical) {
        try {
            JsonNode node = MAPPER.readTree(strRaw);
            if (!node.isObject()) {
                throw new CaqlException(logical.numLine(), logical.strSource(),
                        "WITH takes a JSON object");
            }
            return node;
        }
        catch (JsonProcessingException ex) {
            throw new CaqlException(logical.numLine(), logical.strSource(),
                    "the JSON cannot be read: " + ex.getOriginalMessage());
        }
    }


    private static boolean isIdent(char ch, boolean flagFirst) {
        if (flagFirst)
            return Character.isLetter(ch);
        return Character.isLetterOrDigit(ch) || ch == '_';
    }


    private static boolean isWordStart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }


    /**
     * Dots, colons and hyphens are part of a word so that Iou.Main:Iou and a
     * package name like acme-banking lex as one thing. A WHERE field path -
     * holder.name - and a number - 10.5 - are words for the same reason, and
     * the parser splits or reads them by position.
     */
    private static boolean isWordPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '.' || ch == ':' || ch == '-';
    }

}
