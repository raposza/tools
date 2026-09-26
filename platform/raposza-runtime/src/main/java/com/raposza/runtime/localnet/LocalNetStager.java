// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Puts one namespace's configuration on disk outside a container.
 *
 * TWO STAGING ROOTS ARE STRUCTURAL. compose mounts conf/canton/app.conf and
 * conf/splice/app.conf both at /app/app.conf, in two containers, each with its
 * own /app. There is one filesystem here, so each namespace gets its own root
 * and the six absolute includes are rewritten to it.
 *
 * The environment is read from the bundle's own env files rather than restated,
 * so a release that changes a default is followed rather than contradicted.
 *
 * SEEDED FILES ARE NOT AN EXTRA. `storage.conf`, `parameters.conf` and
 * `additional-config.conf` exist only inside docker/canton and are what several
 * of the canton namespace's absolute includes point at. Staged from the banked
 * copies they resolve; left out, the include fails and the start does not
 * happen. `repeatable-migrations` is created empty because the seventh absolute
 * path is a Flyway location, and Flyway is given a directory, not a file.
 *
 * Author Claude/bentzn
 */
public final class LocalNetStager {

    private static final Pattern PAT_ENV_LINE =
            Pattern.compile("^\\s*([A-Za-z_][A-Za-z_0-9]*)=(.*)$");

    private static final Pattern PAT_SUBST =
            Pattern.compile("\\$\\{([A-Za-z_][A-Za-z_0-9]*)(:?[-?])?([^}]*)\\}");

    private static final String[] ARR_ROLE = { "app-provider", "app-user", "sv" };

    private final Path dirBundle;
    private final Path dirRun;
    private final LocalNetAuth auth;

    public LocalNetStager(Path dirBundle, Path dirRun) {
        this(dirBundle, dirRun, LocalNetAuth.ofUnsafe());
    }


    /**
     * @param dirBundle the extracted splice-node bundle
     * @param dirRun where the staged conf goes
     * @param authNew what the participants are to verify; an asymmetric one
     *        rewrites each role's app-auth.conf
     */
    public LocalNetStager(Path dirBundle, Path dirRun, LocalNetAuth authNew) {
        this.dirBundle = dirBundle.toAbsolutePath().normalize();
        this.dirRun = dirRun.toAbsolutePath().normalize();
        this.auth = authNew;
    }


    /**
     * REWRITTEN RATHER THAN OVERRIDDEN, and `LocalNetAuth`'s type comment says
     * why: `auth-services` is a HOCON list, a `-C` override sets a path, and no
     * path indexes into an array.
     *
     * Only the canton namespace owns participants, so only it is touched; the
     * splice namespace's app-auth.conf is left exactly as the bundle ships it.
     *
     * @param strNamespace canton or splice
     * @param strRole the role whose directory was just copied
     * @param dirApp that namespace's staging root
     * @throws IOException when the file cannot be written
     */
    private void writeAuth(String strNamespace, String strRole, Path dirApp)
            throws IOException {
        if (!LocalNetRunner.STR_NS_CANTON.equals(strNamespace) || !auth.isRewritten())
            return;
        Path fileAuth = dirApp.resolve(strRole).resolve("on").resolve("app-auth.conf");
        if (!Files.isRegularFile(fileAuth))
            return;
        writeOver(fileAuth, auth.strConfCanton(strRole));
    }


    public Path dirLocalnet() {
        return dirBundle.resolve("docker-compose/localnet");
    }


    /**
     * @param strNamespace canton or splice
     * @return the staged app.conf
     */
    public Path stage(String strNamespace) {
        return stage(strNamespace, List.of());
    }


    /**
     * @param strNamespace canton or splice
     * @param lstSeed image-only files copied into the staging root before the
     *        includes are rewritten against it
     * @return the staged app.conf
     */
    public Path stage(String strNamespace, List<Path> lstSeed) {
        Path dirApp = dirRun.resolve(strNamespace).resolve("app");
        try {
            if (Files.exists(dirRun.resolve(strNamespace)))
                deleteTree(dirRun.resolve(strNamespace));
            Files.createDirectories(dirApp);
            Files.createDirectories(dirApp.resolve("repeatable-migrations"));

            for (Path fileSeed : lstSeed) {
                Files.copy(fileSeed, dirApp.resolve(fileSeed.getFileName().toString()),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            Path fileConfSrc = dirLocalnet().resolve("conf").resolve(strNamespace)
                    .resolve("app.conf");
            Path fileConf = dirApp.resolve("app.conf");
            Files.copy(fileConfSrc, fileConf, StandardCopyOption.REPLACE_EXISTING);

            for (String strRole : ARR_ROLE) {
                Path dirRoleSrc = dirLocalnet().resolve("conf").resolve(strNamespace)
                        .resolve(strRole);
                if (!Files.isDirectory(dirRoleSrc))
                    continue;
                copyTree(dirRoleSrc, dirApp.resolve(strRole).resolve("on"));
                writeAuth(strNamespace, strRole, dirApp);
            }

            String strConf = Files.readString(fileConf, StandardCharsets.UTF_8);
            strConf = strConf.replace("include file(\"/app/",
                    "include file(\"" + dirApp + "/");
            strConf = strConf.replace("filesystem:/app/",
                    "filesystem:" + dirApp + "/");
            // THE SPLICE APPS' VERIFIER, after the includes and therefore
            // after the role files that set it - HOCON takes the last
            // assignment. Empty on the canton namespace and whenever the
            // bundle's own verifier is being kept.
            if (LocalNetRunner.STR_NS_SPLICE.equals(strNamespace))
                strConf = strConf + auth.strConfSpliceOverride();
            writeOver(fileConf, strConf);
            return fileConf;
        }
        catch (IOException ex) {
            // THE CAUSE'S OWN TEXT, IN THE MESSAGE. `UncheckedIOException`
            // does not fold it in, and every consumer of this - the window's
            // `startFailed` among them - shows `getMessage()` and nothing
            // else. So a `NoSuchFileException` naming the exact path arrived
            // at the operator as five words that say only which namespace,
            // and the file it could not touch was discarded - 2026-09-21.
            throw new UncheckedIOException("could not stage " + strNamespace + " - " + ex, ex);
        }
    }


    /**
     * @param fileConf a staged app.conf
     * @return every remaining absolute /app path, which must be empty
     */
    public List<String> lstAbsoluteLeft(Path fileConf) {
        List<String> lstLeft = new ArrayList<>();
        try {
            int cntLine = 0;
            for (String strLine : Files.readAllLines(fileConf, StandardCharsets.UTF_8)) {
                cntLine++;
                if (strLine.contains("\"/app/") || strLine.contains(":/app/"))
                    lstLeft.add(cntLine + ": " + strLine.trim());
            }
        }
        catch (IOException ex) {
            throw new UncheckedIOException("could not read " + fileConf, ex);
        }
        return lstLeft;
    }


    /**
     * Reads the bundle's env files the way compose does, resolving ${X:-d} and
     * ${X} against what is already known.
     *
     * @param strNamespace canton or splice
     * @return the environment for that namespace's process
     */
    public Map<String, String> mapEnv(String strNamespace) {
        Map<String, String> mapEnv = new LinkedHashMap<>();
        mapEnv.put("SV_PROFILE", "on");
        mapEnv.put("APP_PROVIDER_PROFILE", "on");
        mapEnv.put("APP_USER_PROFILE", "on");
        mapEnv.put("PARTY_HINT", "raposza-localparty-1");
        mapEnv.put("DB_SERVER", "localhost");
        mapEnv.put("DB_USER", "postgres");
        mapEnv.put("DB_PASSWORD", "postgres");

        // Read only by the image's own storage.conf, which names the role
        // cnadmin and lets these two replace it. The embedded server creates
        // postgres and runs trust auth.
        mapEnv.put("POSTGRES_USER", "postgres");
        mapEnv.put("POSTGRES_PASSWORD", "postgres");

        Path dirEnv = dirLocalnet().resolve("env");
        readEnv(dirEnv.resolve("common.env"), mapEnv);
        if ("splice".equals(strNamespace))
            readEnv(dirEnv.resolve("splice.env"), mapEnv);
        readEnv(dirEnv.resolve("app-provider-auth-on.env"), mapEnv);
        readEnv(dirEnv.resolve("app-user-auth-on.env"), mapEnv);
        readEnv(dirEnv.resolve("sv-auth-on.env"), mapEnv);
        return mapEnv;
    }


    private void readEnv(Path fileEnv, Map<String, String> mapEnv) {
        if (!Files.isRegularFile(fileEnv))
            return;
        try {
            for (String strLine : Files.readAllLines(fileEnv, StandardCharsets.UTF_8)) {
                String strTrim = strLine.trim();
                if (strTrim.isEmpty() || strTrim.startsWith("#"))
                    continue;
                Matcher matcher = PAT_ENV_LINE.matcher(strTrim);
                if (!matcher.matches())
                    continue;
                String strKey = matcher.group(1);
                String strValue = unquote(resolve(matcher.group(2), mapEnv));
                mapEnv.put(strKey, strValue);
            }
        }
        catch (IOException ex) {
            throw new UncheckedIOException("could not read " + fileEnv, ex);
        }
    }


    /**
     * @param strRaw a value possibly carrying ${X}, ${X:-d} or ${X-d}
     * @param mapEnv what is known so far
     * @return the resolved value
     */
    private String resolve(String strRaw, Map<String, String> mapEnv) {
        Matcher matcher = PAT_SUBST.matcher(strRaw);
        StringBuilder bld = new StringBuilder();
        while (matcher.find()) {
            String strName = matcher.group(1);
            String strDefault = matcher.group(3) == null ? "" : matcher.group(3);
            String strHave = mapEnv.get(strName);
            String strUse = strHave != null && !strHave.isEmpty() ? strHave : strDefault;
            matcher.appendReplacement(bld, Matcher.quoteReplacement(strUse));
        }
        matcher.appendTail(bld);
        return bld.toString();
    }


    private String unquote(String strValue) {
        String strTrim = strValue.trim();
        if (strTrim.length() >= 2 && strTrim.startsWith("\"") && strTrim.endsWith("\""))
            return strTrim.substring(1, strTrim.length() - 1);
        return strTrim;
    }


    /**
     * Replaces a staged file, whatever mode it was copied with.
     *
     * MEASURED 2026-09-21: the bundle ships `conf/canton/&lt;role&gt;/app-auth.conf`
     * and `conf/&lt;ns&gt;/app.conf` as `-r--r--r--`, and `Files.copy` carries the
     * permission bits across even without `COPY_ATTRIBUTES` - the flag governs
     * ownership and timestamps, not the mode. So the staged copy is read-only,
     * and writing THROUGH it fails with `AccessDeniedException` naming the
     * path, which is how the canton namespace stopped staging.
     *
     * DELETE, THEN CREATE. Removing a file needs write on its DIRECTORY, which
     * the stager made itself, not on the file. The replacement is then created
     * fresh under the umask and is writable, so a re-stage of the same tree
     * does not depend on what the previous one left behind.
     *
     * @param file what to replace
     * @param strBody the whole new content
     * @throws IOException when the directory itself refuses
     */
    private void writeOver(Path file, String strBody) throws IOException {
        Files.deleteIfExists(file);
        Files.writeString(file, strBody, StandardCharsets.UTF_8);
    }


    private void copyTree(Path dirFrom, Path dirTo) throws IOException {
        Files.createDirectories(dirTo);
        try (Stream<Path> strm = Files.walk(dirFrom)) {
            for (Path pathFrom : strm.toList()) {
                Path pathTo = dirTo.resolve(dirFrom.relativize(pathFrom).toString());
                if (Files.isDirectory(pathFrom)) {
                    Files.createDirectories(pathTo);
                }
                else {
                    Files.createDirectories(pathTo.getParent());
                    Files.copy(pathFrom, pathTo, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }


    private void deleteTree(Path dirGone) throws IOException {
        try (Stream<Path> strm = Files.walk(dirGone)) {
            for (Path pathGone : strm.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .toList()) {
                Files.deleteIfExists(pathGone);
            }
        }
    }
}
