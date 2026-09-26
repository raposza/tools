// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds the Canton installations present on this machine.
 *
 * There are two installation models and they do not share a layout:
 *
 * <pre>
 * Daml Assistant : &lt;damlRoot&gt;/sdk/&lt;sdk-version&gt;/canton/
 * DPM            : &lt;dpmRoot&gt;/cache/components/&lt;component&gt;/&lt;canton-version&gt;/
 * </pre>
 *
 * Two rules follow, and both are here because breaking them has already cost
 * time on this project. The DPM SDK bundle version is NOT the Canton version,
 * so nothing is inferred from a bundle number. And the runtime JAR is located
 * by searching the installation, not by composing a filename, because the
 * internal layout differs across generations.
 *
 * The roots are constructor arguments so this is testable against a fixture
 * tree; {@link #ofDefaults()} is what production uses.
 *
 * Author Claude/bentzn
 */
public final class CantonInstallations {

    private static final Logger log = LoggerFactory.getLogger(CantonInstallations.class);

    /** DPM component directories that hold a Canton, and the edition each is. */
    private static final String STR_COMPONENT_OPEN_SOURCE = "canton-open-source";

    private static final String STR_COMPONENT_ENTERPRISE = "canton-enterprise";

    private static final int N_RUNTIME_SEARCH_DEPTH = 3;

    /**
     * How far under `cache/components` a component directory can sit.
     *
     * A component installed as part of a bundle is stored flat, at depth 1. One
     * added by explicit OCI URI is stored under a REGISTRY-MIRRORED path -
     * `&lt;registry&gt;/&lt;project&gt;/&lt;repository&gt;/components/&lt;component&gt;`
     * - which is depth 5, and the tail of it is the flat layout with the
     * registry coordinates in front. Two layouts in one cache, keyed on how the
     * component was requested, so the cache is WALKED rather than resolved.
     */
    private static final int N_COMPONENT_SEARCH_DEPTH = 6;

    private final Path dirDamlRoot;
    private final Path dirDpmRoot;


    /**
     * @param dirDamlRoot the Daml Assistant root, normally ~/.daml
     * @param dirDpmRoot the DPM root, normally ~/.dpm
     */
    public CantonInstallations(Path dirDamlRoot, Path dirDpmRoot) {
        if (dirDamlRoot == null || dirDpmRoot == null)
            throw new IllegalArgumentException("both roots are required");
        this.dirDamlRoot = dirDamlRoot;
        this.dirDpmRoot = dirDpmRoot;
    }


    /**
     * The roots are ANSWERED rather than composed - see {@link ToolchainRoots}.
     * This used to resolve `~/.daml` and `~/.dpm` off the home directory, which
     * finds nothing on Windows, where they are `%APPDATA%\daml` and
     * `%APPDATA%\dpm` with no leading dot.
     *
     * @return discovery against the roots this machine's environment describes
     */
    public static CantonInstallations ofDefaults() {
        ToolchainRoots roots = ToolchainRoots.ofDefaults();
        return new CantonInstallations(roots.dirDaml(), roots.dirDpm());
    }


    /**
     * What a PICKER shows: the list, less every installation without a runtime
     * jar whose version and edition another entry HAS a jar for.
     *
     * dpm keeps a component in two layouts - flat when a bundle pulls it,
     * mirrored under the registry host when `dpm add component oci://...` does
     * - so one version can be found twice. MEASURED 2026-09-23: the mirrored
     * 3.5.16 was a pull that wrote its metadata and an empty `lib/`, beside a
     * complete flat copy, and the picker listed both - D-837.
     *
     * {@link #discover} is unchanged and still reports the broken one: the
     * capture sweep reads it, and a jar-less install nothing else covers is
     * still shown, as `[no runtime jar]`.
     *
     * @param lstInstall discovered installations, in any order; never null
     * @return the same order, without the shadowed entries
     */
    public static List<CantonInstallation> lstShown(List<CantonInstallation> lstInstall) {
        if (lstInstall == null)
            throw new IllegalArgumentException("a list is required");
        List<CantonInstallation> lstOut = new ArrayList<>();
        for (CantonInstallation inst : lstInstall) {
            if (inst.hasRuntime() || !isShadowed(inst, lstInstall))
                lstOut.add(inst);
        }
        return lstOut;
    }


    /**
     * @param inst an installation without a runtime jar
     * @param lstInstall everything discovered
     * @return whether another entry of the same version and edition has one
     */
    private static boolean isShadowed(CantonInstallation inst, List<CantonInstallation> lstInstall) {
        for (CantonInstallation other : lstInstall) {
            if (other.hasRuntime() && other.version().equals(inst.version())
                    && other.edition() == inst.edition())
                return true;
        }
        return false;
    }


    /**
     * @return every installation found, newest version first; empty when
     *         neither root exists
     */
    public List<CantonInstallation> discover() {
        List<CantonInstallation> lstFound = new ArrayList<>();
        lstFound.addAll(discoverAssistant());
        lstFound.addAll(discoverDpm());
        Collections.sort(lstFound);
        return lstFound;
    }


    /**
     * @param version the exact Canton version
     * @param edition the required edition, or null for any
     * @return the matching installation, preferring one with a runtime JAR
     */
    public Optional<CantonInstallation> find(VersionId version, Edition edition) {
        if (version == null)
            throw new IllegalArgumentException("version is required");

        CantonInstallation best = null;
        for (CantonInstallation inst : discover()) {
            if (!version.equals(inst.version()))
                continue;
            if (edition != null && edition != inst.edition())
                continue;
            if (best == null || (!best.hasRuntime() && inst.hasRuntime()))
                best = inst;
        }
        return Optional.ofNullable(best);
    }


    /**
     * @param strLine a minor line such as "3.5"
     * @param edition the required edition, or null for any
     * @return the highest patch version installed on that line
     */
    public Optional<CantonInstallation> newestOfLine(String strLine, Edition edition) {
        for (CantonInstallation inst : discover()) {
            if (!inst.line().equals(strLine))
                continue;
            if (edition != null && edition != inst.edition())
                continue;
            return Optional.of(inst);
        }
        return Optional.empty();
    }


    private List<CantonInstallation> discoverAssistant() {
        List<CantonInstallation> lstFound = new ArrayList<>();
        Path dirSdk = dirDamlRoot.resolve("sdk");
        for (Path dirVersion : listDirectories(dirSdk)) {
            Optional<VersionId> optVersion = VersionId.tryParse(dirVersion.getFileName().toString());
            if (optVersion.isEmpty())
                continue;

            Path dirCanton = dirVersion.resolve("canton");
            if (!Files.isDirectory(dirCanton))
                continue;

            Path fileRuntime = findRuntimeJar(dirCanton);
            lstFound.add(new CantonInstallation(optVersion.get(),
                    CantonRuntimeJar.editionOf(fileRuntime),
                    InstallSource.DAML_ASSISTANT, dirCanton, fileRuntime));
        }
        return lstFound;
    }


    private List<CantonInstallation> discoverDpm() {
        List<CantonInstallation> lstFound = new ArrayList<>();
        Path dirComponents = dirDpmRoot.resolve("cache").resolve("components");
        if (!Files.isDirectory(dirComponents))
            return lstFound;

        try (Stream<Path> strmPaths = Files.walk(dirComponents, N_COMPONENT_SEARCH_DEPTH)) {
            Iterator<Path> itPath = strmPaths.iterator();
            while (itPath.hasNext()) {
                Path dir = itPath.next();
                if (!Files.isDirectory(dir))
                    continue;

                String strName = dir.getFileName().toString();
                if (STR_COMPONENT_OPEN_SOURCE.equals(strName))
                    lstFound.addAll(discoverDpmComponent(dir, Edition.OPEN_SOURCE));
                else if (STR_COMPONENT_ENTERPRISE.equals(strName))
                    lstFound.addAll(discoverDpmComponent(dir, Edition.ENTERPRISE));
            }
        }
        catch (IOException | UncheckedIOException ex) {
            log.warn("could not walk the DPM component cache {}: {}", dirComponents,
                    ex.toString());
        }
        return lstFound;
    }


    /*
     * THE ENTERPRISE COMPONENT IS DISCOVERED LIKE ANY OTHER. An operator
     * decision, and it supersedes the refusal that used to live here as a
     * warnEnterprise() guard.
     *
     * The refusal rested on a distinction that does not exist. Both editions
     * ship the SAME three licence files - LICENSE, LICENSE-2.0.txt and
     * LICENSE-DA.txt - so the presence of LICENSE-DA.txt in a jar says nothing
     * about which edition it is, and it was the whole of the evidence.
     *
     * Acquisition is not this workspace's problem. Every binary here came from
     * a PUBLIC OCI registry with no credentials, no entitlement check and no
     * consent step. If a vendor publishes an artefact openly, the consequences
     * of that publication are the vendor's. The operator is the professional
     * carrying whatever exposure exists and has decided; a discovery routine is
     * not the place to relitigate it, and an agent is not the party to decide
     * it.
     *
     * DO NOT RE-ADD A LICENCE GUARD HERE. The question is not open.
     */


    /**
     * @param dirComponent the component directory to walk
     * @param editionNamed the edition the COMPONENT NAME claims, used only when
     *        the jar declines to answer
     * @return what is installed under it
     */
    private List<CantonInstallation> discoverDpmComponent(Path dirComponent,
            Edition editionNamed) {
        List<CantonInstallation> lstFound = new ArrayList<>();
        for (Path dirVersion : listDirectories(dirComponent)) {
            Optional<VersionId> optVersion = VersionId.tryParse(dirVersion.getFileName().toString());
            if (optVersion.isEmpty()) {
                log.debug("skipping non-version directory in the DPM cache: {}", dirVersion);
                continue;
            }
            // THE JAR IS THE AUTHORITY, the component name the fallback. They
            // agree on both components here - `canton-enterprise/3.4.4`
            // declares CantonEnterpriseApp - so the fallback covers a jar that
            // could not be read, not a disagreement. The name stays because
            // it records WHERE THE BINARY CAME FROM, which
            // survives a manifest that says nothing.
            Path fileRuntime = findRuntimeJar(dirVersion);
            Edition editionRead = CantonRuntimeJar.editionOf(fileRuntime);
            if (editionRead == Edition.UNKNOWN)
                editionRead = editionNamed;

            lstFound.add(new CantonInstallation(optVersion.get(), editionRead,
                    InstallSource.DPM, dirVersion, fileRuntime));
        }
        return lstFound;
    }


    /**
     * The layout beneath an installation is not assumed. The tree is walked to
     * a shallow depth and the largest canton*.jar wins, because a generation
     * that ships several - a runtime beside a tool - puts the runtime first by
     * size.
     */
    private Path findRuntimeJar(Path dirHome) {
        Path fileBest = null;
        long cntBestBytes = -1L;
        try (Stream<Path> strmPaths = Files.walk(dirHome, N_RUNTIME_SEARCH_DEPTH)) {
            Iterator<Path> itPath = strmPaths.iterator();
            while (itPath.hasNext()) {
                Path file = itPath.next();
                if (!Files.isRegularFile(file))
                    continue;
                String strName = file.getFileName().toString();
                if (!strName.startsWith("canton") || !strName.endsWith(".jar"))
                    continue;

                long cntBytes = Files.size(file);
                if (cntBytes > cntBestBytes) {
                    cntBestBytes = cntBytes;
                    fileBest = file;
                }
            }
        }
        catch (IOException | UncheckedIOException ex) {
            log.warn("could not search {} for a Canton runtime: {}", dirHome, ex.toString());
            return null;
        }
        return fileBest;
    }


    private static List<Path> listDirectories(Path dirParent) {
        return InstallFs.listDirectories(dirParent);
    }
}
