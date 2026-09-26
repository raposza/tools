// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lf;

import com.digitalasset.daml.lf.data.ImmArray;

import java.util.ArrayList;
import java.util.List;

import scala.collection.Iterator;

/**
 * Iteration over the Scala collections the AST is built from, in ONE place.
 *
 * Scala declaration-site variance does not survive into Java, so walking a
 * Map&lt;DottedName, GenTemplate&lt;E&gt;&gt; from Java needs a raw cast that
 * javac cannot check. Confining every such cast here means the mapping code
 * above reads as ordinary Java and there is exactly one file to revisit if the
 * reader changes shape.
 *
 * Elements come back as Object and the caller casts. That is deliberate: a
 * generic signature here would be a promise this class cannot keep.
 *
 * Author Claude/bentzn
 */
public final class ScalaColl {

    private ScalaColl() {
    }


    /**
     * @param coll any Scala collection, including a Map, whose elements are
     *             then Tuple2
     * @return its elements in iteration order
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static List<Object> list(scala.collection.Iterable<?> coll) {
        List<Object> lstElem = new ArrayList<>();
        if (coll == null)
            return lstElem;

        Iterator it = (Iterator) coll.iterator();
        while (it.hasNext()) {
            lstElem.add(it.next());
        }
        return lstElem;
    }


    /**
     * ImmArray is not a Scala collection and has its own iterator.
     *
     * @param arr an immutable array from the AST
     * @return its elements in order
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static List<Object> list(ImmArray<?> arr) {
        List<Object> lstElem = new ArrayList<>();
        if (arr == null)
            return lstElem;

        Iterator it = (Iterator) arr.iterator();
        while (it.hasNext()) {
            lstElem.add(it.next());
        }
        return lstElem;
    }


    /**
     * @param opt a Scala Option
     * @return its contents, null when empty
     */
    public static Object orNull(scala.Option<?> opt) {
        if (opt == null || !opt.isDefined())
            return null;

        return opt.get();
    }

}
