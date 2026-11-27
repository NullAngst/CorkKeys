#!/usr/bin/env bash
# Fails when Android's clipboard service shows up anywhere in the app except Paste.kt.
#
# The keyboard touches the clipboard only when Paste is pressed, and Paste.kt is the one file
# that does it. Keeping every clipboard call in one file means one file to read to check that.
set -euo pipefail
export LC_ALL=C

command -v grep >/dev/null 2>&1 || { printf 'Missing commands: grep\n' >&2; exit 1; }

src=app/src/main
allowed=app/src/main/kotlin/io/github/nullangst/corkkeys/Paste.kt
[ -d "$src" ] || { printf 'No %s folder. Run this from the repository root.\n' "$src" >&2; exit 1; }

pattern='ClipboardManager|CLIPBOARD_SERVICE|ClipData|PrimaryClip|primaryClip'
hits=$(grep -rnE "$pattern" "$src" | grep -v "^$allowed:" || true)
if [ -n "$hits" ]; then
    printf '%s\n' "$hits"
    printf '::error title=Clipboard outside Paste.kt::Only Paste.kt may use the clipboard (lines above).\n'
    exit 1
fi

printf 'Clipboard use is limited to %s.\n' "$allowed"
