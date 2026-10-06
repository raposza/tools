#!/usr/bin/env bash
# Copyright (c) 2026 bentzn
# SPDX-License-Identifier: Apache-2.0
# Author Claude/bentzn
#
# Starts the Sandbox. The window by default; `--cli` starts the stack here,
# in the terminal, and stops it on Ctrl-C.
#
#   ./run-sandbox.sh                  the window
#   ./run-sandbox.sh --build          rebuild the launcher jar first, then the window
#   ./run-sandbox.sh --jfr <file>     the window, with a flight recording of THIS JVM
#   ./run-sandbox.sh --cli            a stack with the defaults, in the terminal
#   ./run-sandbox.sh --list           what Canton and scribe this machine has
#   ./run-sandbox.sh --help           every argument
#
# Follows run.sh: the jar is built once and reused, and is NOT rebuilt when
# sources change. A launcher that quietly rebuilt would make "I changed one line
# and it still behaves the old way" indistinguishable from a slow build, and
# both from a real bug. Only its absence triggers a build by itself.
#
# `--build` comes first when given, `--jfr <file>` next; the rest passes
# through. `--jfr` puts -XX:StartFlightRecording on THIS java command line and
# nowhere else - not JAVA_TOOL_OPTIONS, which Maven reads and every process
# the Sandbox starts inherits, so every JVM under it records to the same file;
# the likely reason, NOT MEASURED, that the recording uploaded 2026-10-03
# arrived as 0 bytes. The file is written when the JVM exits: close the
# window, or Ctrl-C here. A-63 (b).
set -eu
cd "$(dirname "$0")"

FLAG_BUILD=0
if [ "${1:-}" = "--build" ]; then
    FLAG_BUILD=1
    shift
fi

ARR_JVM=()
if [ "${1:-}" = "--jfr" ]; then
    if [ -z "${2:-}" ]; then
        echo "--jfr needs the file the recording is written to" >&2
        exit 2
    fi
    ARR_JVM+=("-XX:StartFlightRecording=filename=$2,settings=profile,dumponexit=true")
    shift 2
fi

jar_path() {
    ls -1t apps/sandbox/target/sandbox-*-app.jar 2>/dev/null | head -1
}

FILE_JAR="$(jar_path || true)"

if [ -z "$FILE_JAR" ] || [ "$FLAG_BUILD" = "1" ]; then
    echo "building the launcher jar..."
    mvn -pl apps/sandbox -am package -DskipTests -Papp
    FILE_JAR="$(jar_path || true)"
fi

if [ -z "$FILE_JAR" ]; then
    echo "no launcher jar was produced under apps/sandbox/target/" >&2
    echo "run: mvn -pl apps/sandbox -am package -DskipTests -Papp" >&2
    exit 1
fi

echo "starting the sandbox from $FILE_JAR"
exec java ${ARR_JVM[@]+"${ARR_JVM[@]}"} --add-opens java.desktop/sun.awt.X11=ALL-UNNAMED -jar "$FILE_JAR" "$@"
