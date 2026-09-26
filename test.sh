#!/usr/bin/env bash
# Copyright (c) 2026 bentzn
# SPDX-License-Identifier: Apache-2.0
# Author Claude/bentzn
#
# The one-command all-green check, and the build.
#
# A green reactor does not prove the tests ran: a module whose sources failed to
# be picked up reports SUCCESS in the same column as one that executed every
# case. Every module below must produce a surefire report with a non-zero
# EXECUTED count.
#
# "Executed" and "total" are reported separately on purpose. Surefire's tests=
# attribute counts SKIPPED cases too, so a suite that was entirely disabled
# yields the same total as one that fully ran - which is precisely the kind of
# false green this script exists to prevent.
#
# The reports are DELETED before the run, and that is not tidiness. Surefire
# writes one file per test class and never removes one, so a class that is
# renamed leaves its last result behind for ever and every later run adds it
# to the totals.
#
# Every test in this reactor runs from a plain checkout with one exception:
#   RAPOSZA_IT_PG=1   SandboxPostgresTest, against the embedded PostgreSQL
#                       it starts itself
set -eu
cd "$(dirname "$0")"

# The module list is DERIVED, not maintained. A module counts as having tests
# when it holds a file surefire would pick up by its default patterns, under
# src/test/java.
#
# It was a hand-edited string until 2026-08-15 and it went stale twice.
# apps/sandbox was missing from the day it gained tests until 2026-08-14, and
# raposza-wire was missing on the run that gained ITS first test - eight cases
# executed, surefire reported them, and this summary named every other module.
# The second time is what makes it a class rather than an oversight: the
# instruction to add a module by hand sits four lines above the list, was read
# by whoever wrote the new module, and did not help. Deriving it removes the
# step that can be forgotten.
#
# A module that appears here and produces no report still FAILS, and that is
# deliberate: a test source outside the reactor is a test nobody runs, which is
# the same silence in a different place.
MODULES_WITH_TESTS=$(find . \
        -path './*/target/*' -prune -o \
        -path '*/src/test/java/*' \
        \( -name '*Test.java' -o -name 'Test*.java' \
           -o -name '*Tests.java' -o -name '*TestCase.java' \) \
        -print \
    | sed 's|/src/test/java/.*||; s|^\./||' | sort -u)

if [ -z "$MODULES_WITH_TESTS" ]; then
    echo "FAIL: no module under this tree holds a test source at all" >&2
    exit 1
fi

for m in $MODULES_WITH_TESTS; do
    rm -rf "$m/target/surefire-reports"
done

# INSTALL, not verify, and with the launcher profile.
#
# `mvn verify` compiled and tested and stopped there, which left two things
# undone that then had to be done by hand on every pass: the artifacts never
# reached the local repository, so downstream consumers went on resolving the
# previous SNAPSHOT, and the shaded jar was never built, so starting the window
# meant a second full build through run.sh --build.
#
# -Papp costs the shade step - a few seconds and a 40 MB artifact - on every
# run of this script. That is the price of `./test.sh && ./run.sh` being one
# pass over the code instead of two.
mvn install -Papp

echo
rc=0
for m in $MODULES_WITH_TESTS; do
    dir="$m/target/surefire-reports"
    if [ ! -d "$dir" ]; then
        echo "FAIL: $m produced no surefire reports - its tests did not run"
        rc=1
        continue
    fi

    cnt_total=$(grep -ho 'tests="[0-9]*"' "$dir"/TEST-*.xml 2>/dev/null |
                sed 's/[^0-9]//g' | awk '{s+=$1} END {print s+0}')
    cnt_skip=$(grep -ho 'skipped="[0-9]*"' "$dir"/TEST-*.xml 2>/dev/null |
               sed 's/[^0-9]//g' | awk '{s+=$1} END {print s+0}')
    cnt_run=$((cnt_total - cnt_skip))

    if [ "$cnt_run" -eq 0 ]; then
        echo "FAIL: $m reported $cnt_total tests but EXECUTED none"
        rc=1
    elif [ "$cnt_skip" -gt 0 ]; then
        echo "ran: $m - $cnt_run executed, $cnt_skip SKIPPED"
    else
        echo "ran: $m - $cnt_run executed"
    fi
done

# Printed on EVERY run, not only when it is unset. What the script SEES is the
# one thing it can report without inference, so it reports it always.
echo
if [ "${RAPOSZA_IT_PG:-}" = "" ]; then
    echo "RAPOSZA_IT_PG = (unset) - SandboxPostgresTest is skipped"
else
    echo "RAPOSZA_IT_PG = $RAPOSZA_IT_PG"
    rep="platform/raposza-runtime/target/surefire-reports/TEST-com.raposza.runtime.db.SandboxPostgresTest.xml"
    if [ ! -f "$rep" ]; then
        echo "WARNING: it is set but that suite produced no report at all."
        echo "         It did NOT run."
    elif grep -q '<skipped' "$rep"; then
        echo "WARNING: it is set but that suite was skipped. Check the value"
        echo "         is spelled correctly and exported."
    fi
fi

if [ "$rc" -ne 0 ]; then
    echo "test: FAILED"
    exit 1
fi

echo "test: OK"
