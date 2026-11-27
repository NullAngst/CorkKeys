#!/usr/bin/env bash
# Fetches the aosp-dictionaries word lists CorkKeys uses and converts them with
# tools/convert_wordlist.sh. Run by .github/workflows/wordlists.yml on GitHub's runners.
#
# English (US) and Spanish come from the experimental lists and German from the main list:
# those are the ones built from the Leipzig Corpora Collection (CC BY 4.0).
set -euo pipefail
export LC_ALL=C

cap=${WORD_CAP:-250000}
repo=https://codeberg.org/Helium314/aosp-dictionaries.git

missing=""
for c in git find sort head; do
    command -v "$c" >/dev/null 2>&1 || missing="$missing $c"
done
if [ -n "$missing" ]; then
    printf 'Missing commands:%s\n' "$missing" >&2
    exit 1
fi

work=$(mktemp -d "${TMPDIR:-/tmp}/aosp_dicts.XXXXXX")
trap 'rm -rf -- "$work"' EXIT

git clone --quiet --depth 1 --filter=blob:none --sparse "$repo" "$work/src"
git -C "$work/src" sparse-checkout set wordlists wordlists_experimental
commit=$(git -C "$work/src" rev-parse HEAD)
printf '::notice title=aosp-dictionaries::commit %s\n' "$commit"

# pick <name pattern>: the first matching .combined file, gzipped or not, in either folder.
pick() {
    find "$work/src/wordlists" "$work/src/wordlists_experimental" -type f -name "$1" | sort | head -n 1
}

convert() {
    lang=$1
    file=$2
    if [ -z "$file" ]; then
        printf '::error title=Word list missing::No source list found for %s. Files there:%s\n' "$lang" \
            "$(find "$work/src/wordlists" "$work/src/wordlists_experimental" -type f | sed 's|.*/||' | tr '\n' ' ')" >&2
        exit 1
    fi
    result=$(bash tools/convert_wordlist.sh "$file" "$lang" "$cap" | head -n 1)
    printf '::notice title=%s::%s from %s\n' "$lang" "$result" "${file#"$work/src/"}"
}

convert en "$(pick 'main_en_US__experimental.combined*')"
convert de "$(pick 'main_de.combined*')"
convert es "$(pick 'main_es__experimental.combined*')"

printf 'aosp-dictionaries commit %s\n' "$commit" > wordlists_source.txt
