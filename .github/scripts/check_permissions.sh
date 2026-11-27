#!/usr/bin/env bash
# Fails when the merged release manifest declares or asks for any permission.
#
# CorkKeys asks Android for nothing. Without INTERNET, Android refuses every network connection
# the app could try to open. This reads the manifest that actually goes into the release APK,
# after every library and build tool has added its part, so a permission slipped in by the
# build itself is caught too.
set -euo pipefail
export LC_ALL=C

missing=""
for c in find grep sort; do
    command -v "$c" >/dev/null 2>&1 || missing="$missing $c"
done
if [ -n "$missing" ]; then
    printf 'Missing commands:%s\n' "$missing" >&2
    exit 1
fi

bash ./gradlew --no-daemon -q :app:processReleaseManifest

manifests=$(find app/build/intermediates -type f -name AndroidManifest.xml -path '*release*' | sort)
if [ -z "$manifests" ]; then
    printf 'No merged release manifest found under app/build/intermediates.\n' >&2
    exit 1
fi

bad=0
while IFS= read -r m; do
    if grep -nE '<(uses-permission|uses-permission-sdk-23|permission)[[:space:]>]' "$m"; then
        printf '::error title=Permission in the release manifest::%s declares or asks for a permission (lines above).\n' "$m"
        bad=1
    fi
done <<EOF
$manifests
EOF

if [ "$bad" -ne 0 ]; then
    printf 'CorkKeys must not ask for any permission. Find what added it and take it out.\n' >&2
    exit 1
fi

printf 'No permissions in:\n%s\n' "$manifests"
