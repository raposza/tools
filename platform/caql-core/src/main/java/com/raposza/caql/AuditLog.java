// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.caql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Set;

/**
 * What this machine submitted, and when.
 *
 * <h2>It is not the transcript, and the difference is one field</h2>
 *
 * A transcript diagnoses a fixture, carries the operator's source line and the
 * argument twice, and lives where the run is told. This accumulates, lives in
 * one place, and must never hold a payload - so it is built from
 * {@link Entry#audit}, by SUBTRACTION from the transcript form rather than by a
 * second assembly. A field added to one therefore cannot silently fail to reach
 * the other, and a field carrying business data has to be removed deliberately.
 *
 * The principle is AUTHORSHIP, not sensitivity: a fixture script contains
 * payloads and nobody objects, because the operator wrote it and chose where it
 * lives. The objection is to a file the TOOL writes without being asked.
 *
 * <h2>ONE record per operation, written when the outcome is known</h2>
 *
 * The invariant is over operations: every one that changes the
 * participant AND reaches it produces exactly one record. So a QUERY writes
 * nothing, and neither does a statement refused locally - it never reached the
 * participant, and a record of what the operator TRIED is a different artefact
 * that nobody has asked for.
 *
 * PRUNE is in the list and is the sharpest case for it: it is irreversible and
 * it destroys history, so a run that pruned a participant and left no record of
 * having done so would be the worst omission this file could have.
 *
 * `dql-design.md` sec. 10 additionally says the record is opened at submission
 * and closed at the outcome. An append-only file cannot do both: opened-then-
 * closed is two lines, and two lines is not one record. The invariant wins.
 * OUTCOME_UNKNOWN is a known outcome and IS logged, which is what that wording
 * was protecting. What remains uncovered is a process killed between the submit
 * and the outcome - which loses the transcript too, so it is the same window
 * an unobserved completion already leaves open. Flagged for amendment rather
 * than diverged from silently.
 *
 * <h2>One JSON object per line</h2>
 *
 * Not a JSON array. An array has to be rewritten to be appended to, and a log
 * that rewrites itself can be truncated by a crash into something that is no
 * longer a log at all. A line-delimited file appends, survives a kill mid-write
 * with at most one damaged line, and reads with any tool.
 *
 * Author Claude/bentzn
 */
public final class AuditLog {

    /** Owner read and write. Nothing here is secret, and nothing here is public either. */
    private static final Set<java.nio.file.attribute.PosixFilePermission> SET_PERM =
            Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);

    private final Path fileLog;
    private final ObjectMapper mapper = new ObjectMapper();


    /** The one file, under the user's home. */
    public AuditLog() {
        this(Path.of(System.getProperty("user.home"), ".raposza", "audit.jsonl"));
    }


    /**
     * @param fileLog where to append; the directory is created on first write
     */
    public AuditLog(Path fileLog) {
        if (fileLog == null)
            throw new IllegalArgumentException("an audit log path is required");

        this.fileLog = fileLog;
    }


    /** @return where records are appended */
    public Path fileLog() {
        return fileLog;
    }


    /**
     * Whether a statement produces a record at all.
     *
     * The unit is the OPERATION, so this asks two questions and not
     * one: did it change the participant, and did it reach it.
     *
     * @param stmt the statement
     * @param status where it got to
     * @return true when a record is owed
     */
    public static boolean flagAudited(Stmt stmt, RunStatus status) {
        // An EXPECT wraps a statement that DID reach the participant, so the
        // wrapper is audited on behalf of what is inside it. An ASSERT wraps a
        // read, which changes nothing and is not audited - so it is absent from
        // this list rather than excluded by it.
        Stmt stmtInner = stmt instanceof Stmt.Expect expect ? expect.stmtInner() : stmt;

        boolean flagMutating = stmtInner instanceof Stmt.Create
                || stmtInner instanceof Stmt.Exercise
                || stmtInner instanceof Stmt.ExerciseByKey
                || stmtInner instanceof Stmt.Allocate
                || stmtInner instanceof Stmt.CreateUser
                || stmtInner instanceof Stmt.DeleteUser
                || stmtInner instanceof Stmt.Rights
                || stmtInner instanceof Stmt.Prune;

        // VALIDATED sent nothing. FAILED_LOCALLY never left this machine.
        boolean flagReached = status != RunStatus.VALIDATED
                && status != RunStatus.FAILED_LOCALLY;

        return flagMutating && flagReached;
    }


    /**
     * Appends one record.
     *
     * @param entry the transcript entry; only its audit form is written
     * @param nameProfile the participant the run was pointed at, so a log
     *                    accumulating across participants can be read
     * @param instAt when the outcome was known
     * @throws UncheckedIOException when the record cannot be written. NOT
     *         swallowed: a log that silently fails to record a submission is
     *         worse than no log, because it will be believed
     */
    public void append(Entry entry, String nameProfile, Instant instAt) {
        ObjectNode obj = entry.audit(mapper);
        obj.put("participant", nameProfile);
        obj.put("at", instAt.toString());

        try {
            Path dirLog = fileLog.getParent();
            if (dirLog != null)
                Files.createDirectories(dirLog);

            boolean flagNew = !Files.exists(fileLog);
            Files.writeString(fileLog, mapper.writeValueAsString(obj) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);

            if (flagNew)
                permit(fileLog);
        }
        catch (IOException ex) {
            throw new UncheckedIOException("could not append to the audit log " + fileLog, ex);
        }
    }


    /**
     * Best effort. A filesystem without POSIX permissions is normal on Windows
     * and is not a reason to refuse to write the record.
     */
    private static void permit(Path file) {
        try {
            Files.setPosixFilePermissions(file, SET_PERM);
        }
        catch (IOException | UnsupportedOperationException ex) {
            // Not POSIX. The record matters more than the mode.
        }
    }

}
