// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import java.nio.file.Path;

/**
 * The two console-script primitives both bootstrap renderers need.
 *
 * TWO METHODS, AND THAT IS THE POINT. `Canton3xBootstrap` and
 * `Canton3xDaemonBootstrap` carried these byte-identical, and nothing else they
 * emit is the same: one runs after the vendor subcommand has already started
 * and bootstrapped everything, the other starts and connects the nodes itself,
 * uploads DARs and writes a readiness sentinel the first has no use for. Their
 * scripts LOOK alike because both are Scala writing files. They are two
 * policies, and merging them on the strength of the resemblance is the thing
 * that would make the difference between them invisible.
 *
 * Author Claude/bentzn
 */
final class ConsoleScript {

    private ConsoleScript() {
    }


    /**
     * @param sb where the line goes
     * @param file what the script writes to
     * @param strExpression the Scala expression whose value is written
     */
    static void appendWrite(StringBuilder sb, Path file, String strExpression) {
        sb.append("java.nio.file.Files.writeString(java.nio.file.Paths.get(\"")
                .append(forScala(file)).append("\"), ").append(strExpression).append(")\n");
    }


    /**
     * Backslashes are an escape inside a Scala string literal, so a Windows
     * path written verbatim into the script produces a parse error a long way
     * from its cause.
     *
     * @param path any path
     * @return it, absolute and safe to put inside a Scala string literal
     */
    static String forScala(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "/");
    }

}
