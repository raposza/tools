// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lf;

import com.raposza.spi.LfDecoder_i;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link LfDecoder} whose Daml-LF reader comes from jars named at run time -
 * an installed Canton's own jar - rather than from the build.
 *
 * <h2>Why</h2>
 *
 * The published archive reader lags the participants: a Canton 3.5 participant
 * accepts LF 2.3 packages that the newest stable reader on Maven Central
 * refuses. The Canton jar carries the reader that participant uses, so the
 * reader that matches the ledger is the one already on the machine. Loading it
 * at run time keeps it out of the build: nothing new is resolved, shaded or
 * shipped.
 *
 * <h2>How the classes are split</h2>
 *
 * A child loader over the given jars, CHILD FIRST, so the reader and the Scala,
 * protobuf and logging classes it was built with come from the same jar and
 * never from whatever the application happens to carry. Three exceptions:
 *
 * <ul>
 * <li>the JDK, from the parent - `java.` cannot be defined by an application
 * loader anyway;</li>
 * <li>`com.raposza.api.` and `com.raposza.spi.` from the parent, because they
 * are the types the caller and the decoder share - {@link LfDecoder_i},
 * `PackageShape` and `LedgerException`. Defined twice they would be two
 * unrelated classes and every cast across the boundary would fail;</li>
 * <li>`com.raposza.lf.` DEFINED IN THE CHILD from the parent's own class
 * bytes, so that this module's references to the reader resolve against the
 * jars given here. The parent may therefore carry raposza-lf without carrying
 * any reader at all.</li>
 * </ul>
 *
 * Measured: raposza-lf as built against reader 3.4.11, unchanged, decodes
 * LF 1.15, LF 2.2 and LF 2.3 packages through the reader of every Canton
 * open-source jar from 3.5.1 to 3.5.19 loaded this way.
 *
 * Author Claude/bentzn
 */
public final class LfReaderLoader {

    private static final String STR_OWN = "com.raposza.lf.";

    private static final List<String> LST_SHARED = List.of("java.", "com.raposza.api.",
            "com.raposza.spi.");

    private static final String STR_DECODER = "com.raposza.lf.LfDecoder";


    private LfReaderLoader() {
    }


    /**
     * @param lstJar the jars that carry the reader - an installed Canton's
     *        `canton-open-source-&lt;version&gt;.jar` is one jar that carries
     *        all of it
     * @return a decoder whose reader comes from those jars
     * @throws IllegalArgumentException when a jar does not exist
     * @throws IllegalStateException when the decoder cannot be loaded through
     *         them
     */
    public static LfDecoder_i decoderFrom(List<Path> lstJar) {
        ClassLoader loader = loaderOver(lstJar);
        try {
            Class<?> clsDecoder = Class.forName(STR_DECODER, true, loader);
            return (LfDecoder_i) clsDecoder.getDeclaredConstructor().newInstance();
        }
        catch (ReflectiveOperationException | LinkageError ex) {
            throw new IllegalStateException("cannot load the Daml-LF decoder through " + lstJar
                    + ": " + ex, ex);
        }
    }


    /**
     * The loader {@link #decoderFrom} uses, for a caller that has to say where
     * a class actually came from.
     *
     * @param lstJar the jars that carry the reader
     * @return a child-first loader over them, with this class's loader as its
     *         parent
     * @throws IllegalArgumentException when a jar does not exist
     */
    public static ClassLoader loaderOver(List<Path> lstJar) {
        if (lstJar == null)
            throw new IllegalArgumentException("a jar list is required");

        List<URL> lstUrl = new ArrayList<>();
        for (Path fileJar : lstJar) {
            if (fileJar == null || !Files.isRegularFile(fileJar))
                throw new IllegalArgumentException("no such jar: " + fileJar);
            try {
                lstUrl.add(fileJar.toUri().toURL());
            }
            catch (MalformedURLException ex) {
                throw new IllegalArgumentException("not a usable jar path: " + fileJar, ex);
            }
        }
        return new ChildFirst(lstUrl.toArray(new URL[0]), LfReaderLoader.class.getClassLoader());
    }


    /**
     * Child first, with the shared prefixes from the parent and this module's
     * own classes redefined here.
     */
    private static final class ChildFirst extends URLClassLoader {

        static {
            registerAsParallelCapable();
        }


        ChildFirst(URL[] arrUrl, ClassLoader loaderParent) {
            super(arrUrl, loaderParent);
        }


        @Override
        protected Class<?> loadClass(String strName, boolean flagResolve)
                throws ClassNotFoundException {
            synchronized (getClassLoadingLock(strName)) {
                Class<?> cls = findLoadedClass(strName);
                if (cls == null)
                    cls = loadUnloaded(strName);
                if (flagResolve)
                    resolveClass(cls);
                return cls;
            }
        }


        private Class<?> loadUnloaded(String strName) throws ClassNotFoundException {
            for (String strPrefix : LST_SHARED) {
                if (strName.startsWith(strPrefix))
                    return getParent().loadClass(strName);
            }
            if (strName.startsWith(STR_OWN))
                return defineOwn(strName);
            try {
                return findClass(strName);
            }
            catch (ClassNotFoundException ex) {
                return getParent().loadClass(strName);
            }
        }


        private Class<?> defineOwn(String strName) throws ClassNotFoundException {
            String strResource = strName.replace('.', '/') + ".class";
            try (InputStream in = getParent().getResourceAsStream(strResource)) {
                if (in == null)
                    throw new ClassNotFoundException(strName);
                byte[] arrClass = in.readAllBytes();
                return defineClass(strName, arrClass, 0, arrClass.length);
            }
            catch (IOException ex) {
                throw new UncheckedIOException("cannot read " + strResource, ex);
            }
        }


        @Override
        public URL getResource(String strName) {
            URL url = findResource(strName);
            return url != null ? url : super.getResource(strName);
        }
    }

}
