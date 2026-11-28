#!/usr/bin/env bash
# Turns Unicode's emoji-test.txt into the file the emoji board reads:
# app/src/main/assets/emoji.txt. One "% group <id>" line per category, then one line per emoji:
#
#   emoji<TAB>name<TAB>skin tone variants, space separated (empty when it has none)
#
# Only fully-qualified emoji are kept. Single skin tone variants are folded into their base emoji;
# combinations with two tones (handshake with two different hands) and tone plus hair style are
# left out, so long-press offers the five plain tones. The Component group (bare tone and hair
# swatches) is dropped. Names are kept for TalkBack.
#
# Usage: bash tools/convert_emoji.sh <emoji-test.txt>
# Get the file from https://www.unicode.org/Public/emoji/latest/emoji-test.txt
set -euo pipefail
export LC_ALL=C.UTF-8 2>/dev/null || export LC_ALL=C

[ "$#" -eq 1 ] || { printf 'Usage: bash %s <emoji-test.txt>\n' "$0" >&2; exit 1; }
src=$1
[ -f "$src" ] || { printf 'No such file: %s\n' "$src" >&2; exit 1; }

missing=""
for c in awk mkdir wc grep; do
    command -v "$c" >/dev/null 2>&1 || missing="$missing $c"
done
if [ -n "$missing" ]; then
    printf 'Missing commands:%s\n' "$missing" >&2
    exit 1
fi

root=$(cd -- "$(dirname -- "$0")/.." && pwd)
out="$root/app/src/main/assets/emoji.txt"
mkdir -p -- "$(dirname -- "$out")"

version=$(awk '/^# Version:/ { print $3; exit }' "$src")

awk -v version="${version:-unknown}" '
    BEGIN {
        ids["Smileys & Emotion"] = "smileys"; ids["People & Body"] = "people"
        ids["Animals & Nature"] = "animals"; ids["Food & Drink"] = "food"
        ids["Travel & Places"] = "travel"; ids["Activities"] = "activities"
        ids["Objects"] = "objects"; ids["Symbols"] = "symbols"; ids["Flags"] = "flags"
        print "% Emoji " version " from Unicode emoji-test.txt, converted by tools/convert_emoji.sh"
        n = 0
    }
    /^# group: / {
        g = substr($0, 10)
        group = (g in ids) ? ids[g] : ""
        if (group != "") { flush(); print "% group " group }
        next
    }
    group == "" || /^#/ || !/; fully-qualified/ { next }
    {
        # "1F44B 1F3FB ; fully-qualified # 👋🏻 E1.0 waving hand: light skin tone"
        rest = substr($0, index($0, "#") + 2)
        sp = index(rest, " ")
        emoji = substr(rest, 1, sp - 1)
        rest = substr(rest, sp + 1)
        name = substr(rest, index(rest, " ") + 1)
        if (match(name, /: (light|medium-light|medium|medium-dark|dark) skin tone$/)) {
            base = substr(name, 1, RSTART - 1)
            if (n > 0 && base == names[n]) tones[n] = tones[n] (tones[n] == "" ? "" : " ") emoji
            next
        }
        if (name ~ /skin tone/) next
        n++
        emojis[n] = emoji; names[n] = name; tones[n] = ""
    }
    END { flush() }
    function flush(   i) {
        for (i = 1; i <= n; i++) printf "%s\t%s\t%s\n", emojis[i], names[i], tones[i]
        n = 0
    }
' "$src" > "$out"

count=$(grep -vc '^%' "$out")
printf 'Wrote %s: %s emoji from Unicode %s\n' "$out" "$count" "${version:-unknown}"
