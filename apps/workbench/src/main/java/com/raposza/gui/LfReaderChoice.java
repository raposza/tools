// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.LedgerException;
import com.raposza.api.model.PackageShape;
import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.CantonInstallations;
import com.raposza.canton.install.LfReaderSource;
import com.raposza.lf.LfDecoder;
import com.raposza.lf.LfReaderLoader;
import com.raposza.spi.LfDecoder_i;

import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Workbench's Daml-LF decoder: an installed Canton's reader when there is
 * one, the reader built into this jar otherwise.
 *
 * <h2>Why</h2>
 *
 * His decision of 2026-10-07: the Workbench reads what the participant
 * accepts. The reader built in - the newest stable on Maven Central - refuses
 * LF 2.3 (D-905); a Canton 3.5 participant accepts it. The Sandbox already
 * borrowed an installed Canton's reader for `GET /dars`, so the Sandbox
 * described packages the Workbench then could not read. Both now choose by
 * `LfReaderSource`.
 *
 * <h2>Chosen at the first package, not at construction</h2>
 *
 * Finding the installations walks directories and loading the reader opens a
 * large jar. The first decode runs on the worker that reads the ledger, so
 * neither lands on the event dispatch thread.
 *
 * <h2>The fallback says what it cannot do</h2>
 *
 * With no usable Canton the built-in reader decodes as before, and a package
 * it refuses carries {@link #STR_HINT_BUILT_IN} in its message - one line
 * that says what to install.
 *
 * Author Claude/bentzn
 */
final class LfReaderChoice implements LfDecoder_i {

    private static final Logger LOG = LoggerFactory.getLogger(LfReaderChoice.class);

    /** What the built-in reader is, for the log and {@link #strReader()}. */
    static final String STR_READER_BUILT_IN = "built-in daml-lf-archive-reader";

    /** Why a package the built-in reader refuses was refused, and the remedy. */
    static final String STR_HINT_BUILT_IN = "the Workbench's built-in Daml-LF reader does not"
            + " read LF 2.3. Install an open-source Canton "
            + LfReaderSource.VERSION_MIN + " or newer with the Install button beside the SDK"
            + " box on the Sandbox tab, then restart the Workbench";

    /** The same, short enough for the status bar's one line. */
    static final String STR_HINT_STATUS = "the built-in reader does not read LF 2.3 - install an"
            + " open-source Canton " + LfReaderSource.VERSION_MIN + " or newer with the Sandbox,"
            + " then restart the Workbench";

    private final Supplier<List<CantonInstallation>> supplierInstall;

    private LfDecoder_i decoder;

    private String strReader;

    private boolean flagBuiltIn;


    /**
     * The installations this machine's settings describe.
     */
    LfReaderChoice() {
        this(() -> CantonInstallations.ofDefaults().discover());
    }


    /**
     * @param supplierInstall where the installed Cantons come from, asked once
     */
    LfReaderChoice(Supplier<List<CantonInstallation>> supplierInstall) {
        if (supplierInstall == null)
            throw new IllegalArgumentException("an installation supplier is required");
        this.supplierInstall = supplierInstall;
    }


    @Override
    public boolean accepts(byte[] arrArchive) {
        return decoder().accepts(arrArchive);
    }


    @Override
    public PackageShape decode(byte[] arrArchive) {
        LfDecoder_i decoderHere = decoder();
        try {
            return decoderHere.decode(arrArchive);
        }
        catch (LedgerException ex) {
            if (!isBuiltIn())
                throw ex;
            throw new LedgerException(ex.getMessage() + " - " + STR_HINT_BUILT_IN, ex);
        }
    }


    /**
     * @return what reads the packages - `canton-open-source 3.5.19`, or
     *         {@link #STR_READER_BUILT_IN}; chooses when nothing has yet
     */
    synchronized String strReader() {
        decoder();
        return strReader;
    }


    /**
     * @return true when no installed Canton could lend its reader; chooses
     *         when nothing has yet
     */
    synchronized boolean isBuiltIn() {
        decoder();
        return flagBuiltIn;
    }


    private synchronized LfDecoder_i decoder() {
        if (decoder != null)
            return decoder;

        try {
            CantonInstallation inst = LfReaderSource.instReader(supplierInstall.get());
            if (inst != null) {
                decoder = LfReaderLoader.decoderFrom(List.of(inst.fileRuntime()));
                strReader = LfReaderSource.strReader(inst);
                flagBuiltIn = false;
                LOG.info("Daml-LF reader: {}, from {}", strReader, inst.fileRuntime());
                return decoder;
            }
            LOG.info("Daml-LF reader: {} - no open-source Canton {} or newer is installed",
                    STR_READER_BUILT_IN, LfReaderSource.VERSION_MIN);
        }
        catch (RuntimeException ex) {
            // A CANTON THAT CANNOT LEND ITS READER is not a reason to read
            // nothing: the built-in reader still reads every LF 2.2 package.
            LOG.warn("Daml-LF reader: {} - the installed Canton's reader could not be loaded: {}",
                    STR_READER_BUILT_IN, ex.toString());
        }
        decoder = new LfDecoder();
        strReader = STR_READER_BUILT_IN;
        flagBuiltIn = true;
        return decoder;
    }

}
