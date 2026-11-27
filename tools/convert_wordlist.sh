#!/usr/bin/env bash
# Turns an aosp-dictionaries word list (.combined or .combined.gz) into the file CorkKeys reads:
# app/src/main/assets/words/<lang>.txt.gz, one "word<TAB>frequency<TAB>flag" line per word,
# most common first, flag "o" for possibly offensive and "-" for everything else.
#
# Next-word (bigram) lines and shortcut lines are dropped, since CorkKeys has no next-word
# prediction. Entries marked not_a_word are dropped too. The list is cut to the most frequent
# <cap> words, 250000 by default.
#
# Usage: bash tools/convert_wordlist.sh <file.combined[.gz]> <lang> [cap]
#   e.g. bash tools/convert_wordlist.sh main_de.combined.gz de 250000
set -euo pipefail
export LC_ALL=C

usage() {
    printf 'Usage: bash %s <file.combined[.gz]> <lang> [cap]\n' "$0" >&2
    exit 1
}

[ "$#" -ge 2 ] || usage
src=$1
lang=$2
cap=${3:-250000}

case "$lang" in
    *[!a-z_]* | "") printf 'Language code must be lowercase letters, like en or de: %s\n' "$lang" >&2; exit 1 ;;
esac
case "$cap" in
    *[!0-9]* | "") printf 'Cap must be a number: %s\n' "$cap" >&2; exit 1 ;;
esac
[ -f "$src" ] || { printf 'No such file: %s\n' "$src" >&2; exit 1; }

missing=""
for c in awk sort cut gzip mkdir mktemp wc; do
    command -v "$c" >/dev/null 2>&1 || missing="$missing $c"
done
if [ -n "$missing" ]; then
    printf 'Missing commands:%s\n' "$missing" >&2
    exit 1
fi

root=$(cd -- "$(dirname -- "$0")/.." && pwd)
out_dir="$root/app/src/main/assets/words"
out="$out_dir/$lang.txt.gz"
mkdir -p -- "$out_dir"

tmp=$(mktemp "${TMPDIR:-/tmp}/corkkeys_words.XXXXXX")
trap 'rm -f -- "$tmp"' EXIT

read_src() {
    case "$src" in
        *.gz) gzip -dc -- "$src" ;;
        *) cat -- "$src" ;;
    esac
}

# Word lines look like " word=haus,f=180,flags=,originalFreq=180,possibly_offensive=true".
# The word ends at the first ",f=", since a word can hold a comma but never ",f=".
# Output for sorting: frequency, line number, word, flag. The line number keeps equal
# frequencies in the source's order with plain POSIX sort keys.
read_src | awk '
    /^dictionary=/ {
        n = split($0, h, ",")
        for (i = 1; i <= n; i++) if (h[i] ~ /^date=/) print substr(h[i], 6) > "/dev/stderr"
        next
    }
    /^ word=/ {
        line = substr($0, 7)
        cut = index(line, ",f=")
        if (cut == 0) next
        word = substr(line, 1, cut - 1)
        rest = substr(line, cut + 3)
        f = rest
        sub(/,.*/, "", f)
        if (f !~ /^[0-9]+$/) next
        if (rest ~ /(^|,)not_a_word=true/) next
        if (word == "" || word ~ /[\t ]/) next
        flag = (rest ~ /(^|,)possibly_offensive=true/) ? "o" : "-"
        if (seen[word]++) next
        total++
        printf "%d\t%d\t%s\t%s\n", f + 0, NR, word, flag
    }
    END { print "total " total > "/dev/stderr" }
' 2>"$tmp" | sort -t "$(printf '\t')" -k1,1nr -k2,2n | awk -v cap="$cap" -F '\t' '
    NR <= cap { printf "%s\t%d\t%s\n", $3, ($1 > 255 ? 255 : $1), $4 }
' | gzip -9n > "$out"

before=$(awk '/^total / { print $2 }' "$tmp")
date=$(awk '!/^total / { print; exit }' "$tmp")
after=$(gzip -dc -- "$out" | wc -l | awk '{ print $1 }')

printf '%s: %s words in the source, %s kept (cap %s), header date %s\n' "$lang" "${before:-0}" "$after" "$cap" "${date:-unknown}"
printf 'Wrote %s\n' "$out"
