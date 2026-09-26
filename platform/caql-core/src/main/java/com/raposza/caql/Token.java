// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One lexed token.
 *
 * @param kind what it is
 * @param str the text: the word, the binding name without its '$', the DECODED
 *            text literal, or the raw JSON source
 * @param node the parsed object, set only on JSON and null otherwise. Parsed at
 *             lex time so a malformed payload fails with the line it is on
 *             rather than in the middle of a run
 * @param numOffset where the token starts in the statement's source, so a
 *                  refusal can name the LINE a token sits on after the
 *                  splitter has joined the lines
 *
 * Author Claude/bentzn
 */
record Token(Kind kind, String str, JsonNode node, int numOffset) {

    enum Kind {

        /** A bare word: a keyword, a binding name, a choice, a template reference. */
        WORD,

        /** $name. */
        VAR,

        /** A quoted literal, already decoded. */
        TEXT,

        /** A balanced JSON object. */
        JSON,

        COMMA,

        /** '=': an assignment, or the equality test in a WHERE clause. Told apart by position. */
        EQ,

        /** The four ordering tests of a WHERE clause. */
        LT,

        LE,

        GT,

        GE,

        LPAREN,

        RPAREN
    }

}
