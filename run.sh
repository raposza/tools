#!/usr/bin/env bash
# Copyright (c) 2026 bentzn
# SPDX-License-Identifier: Apache-2.0
# Author Claude/bentzn
#
# Starts the Explore window.
#
#   ./run.sh                      reads ~/.raposza/ledger_hosts
#   ./run.sh /path/to/hosts       reads that file instead
#   ./run.sh --build              rebuilds the launcher jar first, then starts
#   ./run.sh --build /path/hosts  both
#
# Starts from a launcher jar rather than through Maven. The jar is built once
# and reused, so a normal start is a JVM launch and nothing else.
#
# THE JAR IS NOT REBUILT AUTOMATICALLY when sources change. A launcher that
# quietly rebuilt would make "I changed one line and it still behaves the old
# way" indistinguishable from "the build is slow today", and both from a real
# bug. After changing code, pass --build - or run test.sh, which is the thing
# that builds and tests, and then --build here.
#
# Only the absence of the jar triggers a build by itself, because that case has
# no wrong answer.
set -eu
cd "$(dirname "$0")"

FLAG_BUILD=0
if [ "${1:-}" = "--build" ]; then
    FLAG_BUILD=1
    shift
fi

jar_path() {
    ls -1t apps/workbench/target/workbench-*-app.jar 2>/dev/null | head -1
}

FILE_JAR="$(jar_path || true)"

if [ -z "$FILE_JAR" ] || [ "$FLAG_BUILD" = "1" ]; then
    echo "building the launcher jar..."
    # -am for the sibling modules, -Papp for the shade plugin, and package
    # rather than install: nothing outside this reactor consumes these
    # artifacts, so the local repository does not need them.
    mvn -pl apps/workbench -am package -DskipTests -Papp
    FILE_JAR="$(jar_path || true)"
fi

if [ -z "$FILE_JAR" ]; then
    echo "no launcher jar was produced under apps/workbench/target/" >&2
    echo "run: mvn -pl apps/workbench -am package -DskipTests -Papp" >&2
    exit 1
fi

echo "starting the workbench from $FILE_JAR"
# --add-opens is for WM_CLASS only. The X11 toolkit's application class is a
# private field of a package the JDK does not open, and without it the window
# manager groups the window under a generic Java entry rather than under the
# workbench icon. Harmless everywhere else: a JVM warns about an unknown
# package and carries on.
exec java --add-opens java.desktop/sun.awt.X11=ALL-UNNAMED -jar "$FILE_JAR" "$@"
