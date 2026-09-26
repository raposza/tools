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
#   ./run-sandbox.sh --cli            a stack with the defaults, in the terminal
#   ./run-sandbox.sh --list           what Canton and scribe this machine has
#   ./run-sandbox.sh --help           every argument
#
# Follows run.sh: the jar is built once and reused, and is NOT rebuilt when
# sources change. A launcher that quietly rebuilt would make "I changed one line
# and it still behaves the old way" indistinguishable from a slow build, and
# both from a real bug. Only its absence triggers a build by itself.
set -eu
cd "$(dirname "$0")"

FLAG_BUILD=0
if [ "${1:-}" = "--build" ]; then
    FLAG_BUILD=1
    shift
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
exec java --add-opens java.desktop/sun.awt.X11=ALL-UNNAMED -jar "$FILE_JAR" "$@"
