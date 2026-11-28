#!/usr/bin/env bash
# Fails when the app's code uses android.util.Log.
#
# A keyboard sees every password and message. One stray log line with the current word in it
# would put typed text in the system log, where other tools can read it, so no logging at all.
set -euo pipefail
export LC_ALL=C

command -v grep >/dev/null 2>&1 || { printf 'Missing commands: grep\n' >&2; exit 1; }

src=app/src/main
[ -d "$src" ] || { printf 'No %s folder. Run this from the repository root.\n' "$src" >&2; exit 1; }

if hits=$(grep -rnE 'android\.util\.Log|(^|[^A-Za-z0-9_.])Log\.(v|d|i|w|e|wtf|println)\(' "$src"); then
    printf '%s\n' "$hits"
    printf '::error title=Logging in the app::android.util.Log is not allowed in CorkKeys (lines above).\n'
    exit 1
fi

printf 'No logging calls in %s.\n' "$src"
