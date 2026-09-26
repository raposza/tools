// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A WHERE clause, as parsed. Four node kinds and nothing else.
 *
 * <h2>Both sides of a comparison are fixed in shape</h2>
 *
 * The left is a field path into the payload; the right is a literal or a
 * binding. Nothing is computed on either side: no arithmetic, no call, no
 * index into a list or a map, no field compared with another field. The clause
 * REDUCES a set and produces no value the language then has to hold, which is
 * why QUERY can carry one and still bind nothing.
 *
 * <h2>The right-hand side stays JSON until it is coerced</h2>
 *
 * At parse time nothing is resolved - no participant, no registry - so the
 * parser stays testable without a ledger. The value is coerced against the
 * field's DECLARED type at run time, by the same coercer a WITH payload goes
 * through, substitution included. That is what makes {@code "2026-01-01"} a
 * date rather than a string, {@code 10.0} a Numeric at the declared scale, and
 * {@code $plant} a binding that is looked up AND type-checked.
 *
 * <h2>There is no {@code !=}</h2>
 *
 * {@code NOT field = value} says it, and a second spelling of one test is one
 * more thing to keep in step.
 *
 * Author Claude/bentzn
 */
public sealed interface Clause {

    /** @return the clause as it reads, for the transcript */
    String str();


    /** The five operators. Whether ordering applies is decided by the field's type. */
    enum Op {

        EQ("="),
        LT("<"),
        LE("<="),
        GT(">"),
        GE(">=");

        private final String strSymbol;


        Op(String strSymbol) {
            this.strSymbol = strSymbol;
        }


        /** @return the symbol as written */
        public String strSymbol() {
            return strSymbol;
        }


        /** @return true for the four that need an ordering, false for equality */
        public boolean flagOrdering() {
            return this != EQ;
        }

    }


    /**
     * @param lstField the field path, split on its dots; never empty
     * @param op the operator
     * @param nodeValue the right-hand side as JSON: text, number, boolean,
     *                  null, or the text {@code $name} for a binding
     * @param strValue the right-hand side as written, for the transcript
     */
    record Cmp(List<String> lstField, Op op, JsonNode nodeValue, String strValue)
            implements Clause {

        @Override
        public String str() {
            return String.join(".", lstField) + " " + op.strSymbol() + " " + strValue;
        }

    }


    /**
     * @param left the first operand
     * @param right the second operand
     */
    record And(Clause left, Clause right) implements Clause {

        @Override
        public String str() {
            // An OR under an AND needs its parentheses back, or the reading
            // changes; anything else binds at least as tightly and does not.
            return wrap(left, true) + " AND " + wrap(right, true);
        }

    }


    /**
     * @param left the first operand
     * @param right the second operand
     */
    record Or(Clause left, Clause right) implements Clause {

        @Override
        public String str() {
            return left.str() + " OR " + right.str();
        }

    }


    /** @param inner what is negated */
    record Not(Clause inner) implements Clause {

        @Override
        public String str() {
            return "NOT " + wrap(inner, false);
        }

    }


    /**
     * Parenthesises a child whose own operator binds LESS tightly than the
     * parent's, which is exactly when dropping the parentheses would change
     * the reading. NOT binds tightest, then AND, then OR.
     *
     * @param clause the child
     * @param flagUnderAnd true under an AND, false under a NOT
     * @return the child's text, parenthesised when it must be
     */
    private static String wrap(Clause clause, boolean flagUnderAnd) {
        boolean flagParen = clause instanceof Or || (!flagUnderAnd && clause instanceof And);
        return flagParen ? "(" + clause.str() + ")" : clause.str();
    }

}
