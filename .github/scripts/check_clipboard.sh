#!/usr/bin/env bash
# Fails when Android's clipboard service shows up anywhere in the app except Paste.kt and
# ClipboardHistory.kt.
#
# Paste.kt reads the clipboard only when Paste is pressed. ClipboardHistory.kt listens for copies,
# and only while clipboard history is switched on. Keeping every clipboard call in those two
# files means two files to read to check that.
set -euo pipefail
export LC_ALL=C

command -v grep >/dev/null 2>&1 || { printf 'Missing commands: grep\n' >&2; exit 1; }

src=app/src/main
dir=app/src/main/kotlin/io/github/nullangst/corkkeys
allowed="$dir/Paste.kt $dir/ClipboardHistory.kt"
[ -d "$src" ] || { printf 'No %s folder. Run this from the repository root.\n' "$src" >&2; exit 1; }

pattern='ClipboardManager|CLIPBOARD_SERVICE|ClipData|PrimaryClip|primaryClip'
hits=$(grep -rnE "$pattern" "$src" | grep -v -e "^$dir/Paste.kt:" -e "^$dir/ClipboardHistory.kt:" || true)
if [ -n "$hits" ]; then
    printf '%s\n' "$hits"
    printf '::error title=Clipboard outside Paste.kt and ClipboardHistory.kt::Only Paste.kt and ClipboardHistory.kt may use the clipboard (lines above).\n'
    exit 1
fi

printf 'Clipboard use is limited to %s.\n' "$allowed"
