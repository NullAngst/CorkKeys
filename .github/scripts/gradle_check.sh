#!/usr/bin/env bash
# Runs the given Gradle tasks. If they fail, the compiler errors, lint errors and failed tests
# are also posted as annotations on the run, so they show on the pull request's Checks page
# without digging through the whole log.
#
# Usage: bash .github/scripts/gradle_check.sh assembleDebug lintDebug testDebugUnitTest
set -euo pipefail
export LC_ALL=C

missing=""
for c in grep sed head tee mktemp; do
    command -v "$c" >/dev/null 2>&1 || missing="$missing $c"
done
if [ -n "$missing" ]; then
    printf 'Missing commands:%s\n' "$missing" >&2
    exit 1
fi

log=$(mktemp "${TMPDIR:-/tmp}/gradle_log.XXXXXX")
trap 'rm -f -- "$log"' EXIT

if bash ./gradlew --no-daemon --stacktrace "$@" 2>&1 | tee "$log"; then
    exit 0
fi

# annotate <title> <text>: one annotation, newlines kept.
annotate() {
    body=$(printf '%s' "$2" | sed -e 's/%/%25/g' | sed -e ':a' -e 'N' -e '$!ba' -e 's/\n/%0A/g')
    printf '::error title=%s::%s\n' "$1" "$body"
}

# Kotlin compiler errors: "e: file:///.../Foo.kt:12:5 message". Paths shortened to the repo.
kotlin=$(grep -E '^e: ' "$log" | sed -e 's|^e: file://[^ ]*/app/src/|app/src/|' | head -n 60 || true)
if [ -n "$kotlin" ]; then
    annotate "Kotlin errors (1-30)" "$(printf '%s\n' "$kotlin" | head -n 30)"
    rest=$(printf '%s\n' "$kotlin" | sed -n '31,60p')
    [ -z "$rest" ] || annotate "Kotlin errors (31-60)" "$rest"
fi

# Lint: the text report holds every issue with its location.
for f in app/build/reports/lint-results-debug.txt app/build/intermediates/lint_intermediate_text_report/debug/*/lint-results-debug.txt; do
    if [ -f "$f" ]; then
        annotate "Lint" "$(grep -A 3 ': Error:' "$f" | head -n 60 || true)"
        break
    fi
done

# Unit tests: the first lines of each failure.
for f in app/build/test-results/testDebugUnitTest/*.xml; do
    [ -f "$f" ] || continue
    if grep -q '<failure' "$f"; then
        annotate "Test failed: ${f##*/}" "$(grep -A 6 '<failure' "$f" | head -n 24)"
    fi
done

# Anything else Gradle reported ("What went wrong").
wrong=$(grep -A 12 '^\* What went wrong' "$log" | head -n 40 || true)
[ -z "$wrong" ] || annotate "Gradle" "$wrong"

exit 1
