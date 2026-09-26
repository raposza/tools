// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import java.util.List;

/**
 * Where a party or a contract id comes from: a binding, or the script itself.
 *
 * Kept apart at parse time rather than resolved into a string, because the
 * transcript has to record BOTH what was written and what it resolved to
 * and a literal that happens to look like a binding must not be
 * reported as one.
 *
 * Author Claude/bentzn
 */
public sealed interface CaqlRef {

    /** @return the name or the literal, for a message that quotes the source */
    String str();


    /**
     * A binding, optionally projected through record fields.
     *
     * <h2>A REFERENCE, not an expression</h2>
     *
     * The dot is the only operator the language has and it is not really one:
     * there is no indexing, no arithmetic, no call, and nothing computed on
     * either side. That single restriction is what keeps sec. 12's "no
     * expressions" true while still letting a fixture reach a contract id that
     * a choice returned inside a record.
     *
     * It is also what an editor needs: after {@code $r} the binding's DamlType
     * says exactly which field names are offerable, and the field's own type
     * says whether the slot it is going into will accept it.
     *
     * lstField is EMPTY for a plain {@code $r}, so every existing call site
     * that built a Var from a name alone still means what it meant.
     *
     * @param name the binding name, without its '$'
     * @param lstField the field names to project through, in order; empty for
     *                 the binding itself
     */
    record Var(String name, List<String> lstField) implements CaqlRef {

        /** @param name the binding name, without its '$' */
        public Var(String name) {
            this(name, List.of());
        }


        @Override
        public String str() {
            if (lstField.isEmpty())
                return "$" + name;
            return "$" + name + "." + String.join(".", lstField);
        }

    }


    /** @param strValue the literal as written, already decoded */
    record Literal(String strValue) implements CaqlRef {

        @Override
        public String str() {
            return strValue;
        }

    }

}
