#!/usr/bin/env bash
# Device test for CorkKeys on an emulator, run by .github/workflows/devtest.yml when the devtest
# branch is pushed. It installs the debug build, makes it the keyboard, types through it by
# tapping the keys TalkBack sees (uiautomator's dump of the keyboard window), swipes, rotates,
# switches modes and themes, takes screenshots, and checks Android's app-ops record for
# clipboard reads. Results go to devtest/: results.txt, steps.txt, screenshots and UI dumps.
#
# It never exits early on a failed check: every check is recorded and the run carries on.
set -uo pipefail
export LC_ALL=C

missing=""
for c in adb python3 awk sed; do
    command -v "$c" >/dev/null 2>&1 || missing="$missing $c"
done
if [ -n "$missing" ]; then
    printf 'Missing commands:%s\n' "$missing" >&2
    exit 1
fi

here=$(cd -- "$(dirname -- "$0")" && pwd)
ui="$here/ui.py"
pkg=io.github.nullangst.corkkeys.debug
ime="$pkg/io.github.nullangst.corkkeys.KeyboardService"
out=devtest
mkdir -p "$out"
: > "$out/results.txt"
: > "$out/steps.txt"
keys_file="$out/keys.tsv"
hint="Type something here"

log() { printf '%s\n' "$*" | tee -a "$out/steps.txt"; }

pass() { printf 'PASS %s\n' "$1" | tee -a "$out/results.txt"; }
fail() { printf 'FAIL %s: %s\n' "$1" "$2" | tee -a "$out/results.txt"; }

# check <name> <want> <got>
check() {
    if [ "$2" = "$3" ]; then pass "$1"; else fail "$1" "wanted [$2] got [$3]"; fi
}

shot() { adb exec-out screencap -p > "$out/$1.png"; }

dump() {
    adb shell uiautomator dump --windows /sdcard/ui.xml >/dev/null 2>&1 || adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml "$out/$1.xml" >/dev/null 2>&1
}

# Reads the keyboard's keys from a fresh dump into keys.tsv.
read_keys() {
    for _ in 1 2 3 4; do
        dump "$1"
        python3 "$ui" keys "$out/$1.xml" "$pkg" > "$keys_file"
        [ "$(wc -l < "$keys_file")" -ge 20 ] && return 0
        sleep 2
    done
}

field_text() {
    dump field
    t=$(python3 "$ui" text "$out/field.xml")
    [ "$t" = "$hint" ] && t=""
    printf '%s' "$t"
}

# key_xy <description>: the centre of a key, matched exactly, then ignoring case, then as a prefix.
key_xy() {
    awk -F '\t' -v d="$1" '
        $1 == d { print $2, $3; found = 1; exit }
        tolower($1) == tolower(d) && !best { best = $2 " " $3 }
        index($1, d) == 1 && !pre { pre = $2 " " $3 }
        END { if (!found) { if (best) print best; else if (pre) print pre } }
    ' "$keys_file"
}

tap_key() {
    xy=$(key_xy "$1")
    if [ -z "$xy" ]; then
        log "no key for [$1]"
        return 1
    fi
    # Word splitting wanted: "x y".
    # shellcheck disable=SC2086
    adb shell input tap $xy
}

# type_text <text>: one tap per character, space by the Space key.
type_text() {
    s=$1
    while [ -n "$s" ]; do
        c=${s:0:1}
        s=${s:1}
        if [ "$c" = " " ]; then tap_key "Space" || true; else tap_key "$c" || true; fi
    done
    sleep 1
}

clear_field() {
    adb shell input keyevent KEYCODE_MOVE_END
    # shellcheck disable=SC2046
    adb shell input keyevent $(printf '67 %.0s' $(seq 1 60))
    sleep 1
}

# prefs <name=value>...: writes the keyboard's settings file (device-protected storage) and
# restarts the app so the keyboard reads it. Values true/false are booleans, the rest strings.
prefs() {
    tmp=$(mktemp "${TMPDIR:-/tmp}/prefs.XXXXXX")
    {
        printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n'
        for kv in "$@"; do
            k=${kv%%=*}
            v=${kv#*=}
            case "$v" in
                true | false) printf '    <boolean name="%s" value="%s" />\n' "$k" "$v" ;;
                *[0-9].[0-9]*) printf '    <float name="%s" value="%s" />\n' "$k" "$v" ;;
                *) printf '    <string name="%s">%s</string>\n' "$k" "$v" ;;
            esac
        done
        printf '</map>\n'
    } > "$tmp"
    adb shell am force-stop "$pkg"
    adb push "$tmp" /data/local/tmp/settings.xml >/dev/null
    adb shell "run-as $pkg sh -c 'mkdir -p /data/user_de/0/$pkg/shared_prefs && cp /data/local/tmp/settings.xml /data/user_de/0/$pkg/shared_prefs/settings.xml'"
    rm -f -- "$tmp"
    sleep 2
    adb shell ime set "$ime" >/dev/null
    sleep 1
}

open_field() {
    adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
    cur=$(adb shell settings get secure default_input_method | tr -d '\r')
    sub=$(adb shell settings get secure selected_input_method_subtype | tr -d '\r')
    log "current keyboard: $cur (subtype $sub)"
    if [ "$cur" != "$ime" ]; then
        fail "keyboard still selected after restart" "Android switched to $cur"
        for _ in 1 2 3 4 5; do
            adb shell ime set "$ime" >/dev/null 2>&1
            [ "$(adb shell settings get secure default_input_method | tr -d '\r')" = "$ime" ] && break
            sleep 3
        done
    fi
    adb shell am start -W -n "$pkg/io.github.nullangst.corkkeys.SetupActivity" >/dev/null
    sleep 3
    dump setup
    xy=$(python3 "$ui" field "$out/setup.xml")
    if [ -z "$xy" ]; then
        # Landscape: the field is below the fold. Scroll the setup screen up and look again.
        adb shell input swipe 500 700 500 100 300
        sleep 1
        dump setup
        xy=$(python3 "$ui" field "$out/setup.xml")
    fi
    if [ -z "$xy" ]; then
        log "no text field on the setup screen"
        return 1
    fi
    # shellcheck disable=SC2086
    adb shell input tap $xy
    sleep 3
}

# swipe_word <word>: one finger path through the word's key centres. Every "input motionevent"
# starts a new process, so points are few and far apart: two per stroke. Many short steps would be
# slow enough for the first key's long-press to fire before the finger left it.
swipe_word() {
    pts=""
    for ((i = 0; i < ${#1}; i++)); do
        xy=$(key_xy "${1:i:1}")
        [ -n "$xy" ] && pts="$pts $xy"
    done
    # shellcheck disable=SC2086
    cmds=$(printf '%s\n' $pts | paste -d ' ' - - | awk '
        { x[NR] = $1; y[NR] = $2 }
        END {
            printf "input motionevent DOWN %d %d", x[1], y[1]
            for (i = 2; i <= NR; i++)
                for (s = 1; s <= 2; s++)
                    printf "; input motionevent MOVE %d %d", x[i-1] + (x[i] - x[i-1]) * s / 2, y[i-1] + (y[i] - y[i-1]) * s / 2
            printf "; input motionevent UP %d %d\n", x[NR], y[NR]
        }')
    adb shell "$cmds"
    sleep 2
}

# widths <name>: checks the letter rows reach both edges of the screen, minus the side insets.
widths() {
    # The current width, which follows rotation (wm size always reports portrait).
    w=$(adb shell dumpsys window displays | grep -o 'cur=[0-9]*x[0-9]*' | head -n 1 | sed 's/cur=//; s/x.*//')
    left=$(awk -F '\t' 'length($1) == 1 && $1 ~ /[a-z]/ { if (min == "" || $4 < min) min = $4 } END { print min }' "$keys_file")
    right=$(awk -F '\t' 'length($1) == 1 && $1 ~ /[a-z]/ { if ($6 > max) max = $6 } END { print max }' "$keys_file")
    log "$1: screen width $w, letter keys from $left to $right"
    if [ -n "$left" ] && [ -n "$right" ] && [ "$left" -le 200 ] && [ "$right" -ge $((w - 200)) ]; then
        pass "$1 keys span the width"
    else
        fail "$1 keys span the width" "screen $w, keys $left to $right"
    fi
}

# wait_for <name> <description> <seconds>: polls the keyboard until a node with that exact
# description shows (a suggestion in the strip, say). Logs how long it took.
wait_for() {
    start=$(date +%s)
    while :; do
        dump "$1"
        if python3 "$ui" keys "$out/$1.xml" "$pkg" | awk -F '\t' -v d="$2" '$1 == d { f = 1 } END { exit !f }'; then
            log "$1: [$2] showed after $(($(date +%s) - start)) s"
            pass "$1"
            return 0
        fi
        if [ $(($(date +%s) - start)) -ge "$3" ]; then
            fail "$1" "[$2] never showed in $3 s"
            return 1
        fi
        sleep 3
    done
}

heap() { adb shell dumpsys meminfo "$pkg" | awk '/Dalvik Heap/ && !h { h = $0 } /TOTAL PSS/ && !t { t = $0 } END { print h " | " t }' | tr -s ' '; }

clip_ops() { adb shell cmd appops get "$pkg" READ_CLIPBOARD 2>&1 | tr -s ' \n' ' '; }

# ---- Setup ----

# Root, so the test can open the app's own (unexported) setup and privacy screens.
adb root >/dev/null 2>&1 || true
sleep 3
adb wait-for-device
log "Android $(adb shell getprop ro.build.version.release) (API $(adb shell getprop ro.build.version.sdk)), root: $(adb shell id -u)"
log "word lists in the APK:"
unzip -lv app/build/outputs/apk/debug/app-debug.apk | grep 'words/' | tee -a "$out/steps.txt"
adb install -r -t app/build/outputs/apk/debug/app-debug.apk >/dev/null
# A slow runner can make the emulator's own launcher stop responding; that dialog would sit over
# every step. Errors still land in the crash log, which is checked at the end.
adb shell settings put global hide_error_dialogs 1
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
# The emulator has a hardware keyboard; without this Android hides every on-screen keyboard.
adb shell settings put secure show_ime_with_hard_keyboard 1
# Android sometimes ignores the first ime set right after an install; keep at it for a while.
for _ in 1 2 3 4 5 6 7 8 9 10; do
    adb shell ime enable "$ime" >/dev/null 2>&1
    adb shell ime set "$ime" >/dev/null 2>&1
    [ "$(adb shell settings get secure default_input_method | tr -d '\r')" = "$ime" ] && break
    sleep 3
done
check "CorkKeys is the keyboard" "$ime" "$(adb shell settings get secure default_input_method | tr -d '\r')"

# ---- 1. Plain keyboard ----

prefs
open_field
shot 01-plain-portrait
read_keys 01-plain-portrait
n=$(wc -l < "$keys_file")
log "keys found: $n"
if [ "$n" -ge 30 ]; then pass "keyboard window visible to accessibility"; else fail "keyboard window visible to accessibility" "$n nodes"; fi
widths "portrait"

type_text "hello world"
check "typing by taps" "hello world" "$(field_text)"
clear_field

type_text "hi  "
check "double-space period" "hi. " "$(field_text)"
clear_field


tap_key "Symbols" || true
sleep 1
shot 02-symbols
read_keys 02-symbols
tap_key "More symbols" || true
sleep 1
shot 03-symbols-more
read_keys 03-symbols-more
tap_key "Letters" || true
sleep 1
read_keys 01b-letters

xy=$(key_xy "e")
# shellcheck disable=SC2086
[ -n "$xy" ] && adb shell input swipe $xy $xy 900 &
sleep 0.7
shot 04-long-press-e
wait
sleep 1
log "after long-press on e: [$(field_text)]"
check "long-press types the corner symbol" "|" "$(field_text)"
clear_field

# ---- 2. Suggestions and autocorrect ----

log "memory, plain keyboard: $(heap)"
prefs suggestions=true autocorrect=true
open_field
read_keys 05-suggestions
type_text "teh"
wait_for 05-suggestion-shows "the" 90
shot 05-suggestions-teh
log "memory, English list loaded: $(heap)"
type_text " "
check "autocorrect teh" "the " "$(field_text)"
tap_key "Delete" || true
sleep 1
check "backspace undoes autocorrect" "teh" "$(field_text)"
clear_field

# ---- 3. Swipe ----

prefs suggestions=true swipe=true
open_field
read_keys 06-swipe
# The word lists load in the background the first time, and the swipe index after them.
type_text "helo"
wait_for 06-lists-ready "hello" 90
clear_field
sleep 8
swipe_word "hello"
shot 06-swipe-hello
got=$(field_text)
log "after swiping hello: [$got]"
check "swipe hello" "hello" "$(printf '%s' "$got" | tr '[:upper:]' '[:lower:]' | sed 's/ *$//')"
swipe_word "world"
got=$(field_text)
log "after swiping world: [$got]"
check "swipe adds a space between words" "hello world" "$(printf '%s' "$got" | tr '[:upper:]' '[:lower:]' | sed 's/ *$//')"
clear_field

# ---- 4. German layout ----

prefs languages=en,de current=de suggestions=true
open_field
shot 07-german
read_keys 07-german
if [ -n "$(key_xy "ü")" ] && [ -n "$(key_xy "ä")" ]; then pass "German layout has ü and ä"; else fail "German layout has ü and ä" "missing"; fi
type_text "schoen"
wait_for 07-german-suggestion "schön" 120
log "memory, English and German lists loaded: $(heap)"
clear_field

# ---- 5. Modes ----

prefs mode=ONE_RIGHT
open_field
shot 08-one-handed-right
prefs mode=FLOATING
open_field
shot 09-floating
read_keys 09-floating
widths_floating=$(awk -F '\t' 'length($1) == 1 && $1 ~ /[a-z]/ { if (min == "" || $4 < min) min = $4; if ($6 > max) max = $6 } END { print max - min }' "$keys_file")
log "floating keys span $widths_floating px"
type_text "float"
check "typing on the floating keyboard" "float" "$(field_text)"
clear_field

prefs height=TALLER
open_field
shot 09b-height-taller
prefs height=CUSTOM custom_row_dp=84.0
open_field
shot 09c-height-custom-84
read_keys 09c-height-custom
type_text "tall"
check "typing on a custom-height keyboard" "tall" "$(field_text)"
clear_field

# ---- 6. Landscape and navigation ----

prefs
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 3
open_field
shot 10-landscape
read_keys 10-landscape
widths "landscape"
log "keyboard inset as Android records it: $(adb shell dumpsys window | grep -m 2 -E 'type=ime|InsetsSource.*ime' | tr -s ' ' | tr '\n' ' ')"
type_text "rotate"
check "typing in landscape" "rotate" "$(field_text)"
clear_field
adb shell settings put system user_rotation 0
sleep 2

adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton >/dev/null 2>&1 || true
sleep 3
open_field
shot 11-three-button
read_keys 11-three-button
widths "three-button navigation"
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural >/dev/null 2>&1 || true
sleep 2

# ---- 7. Clipboard, in another app's field ----

adb shell am start -W -a android.intent.action.INSERT -t vnd.android.cursor.dir/contact >/dev/null 2>&1 || true
sleep 4
dump contacts
xy=$(python3 "$ui" field "$out/contacts.xml")
if [ -n "$xy" ]; then
    # shellcheck disable=SC2086
    adb shell input tap $xy
    sleep 3
    read_keys contacts-keys
    shot 16-contacts
    before=$(clip_ops)
    log "clipboard app-ops before Paste: $before"
    tap_key "Paste" || true
    sleep 2
    after=$(clip_ops)
    log "clipboard app-ops after Paste: $after"
    check "Paste in another app reads no clipboard" "$before" "$after"
    tap_key "Tools" || true
    sleep 1
    shot 17-tools-row
    adb shell input keyevent KEYCODE_BACK
else
    log "no Contacts field; clipboard check skipped"
fi

# ---- 7.5. Emoji ----

prefs
open_field
read_keys 18-emoji-keys
comma_w=$(awk -F '\t' '$1 == "Comma" { print $6 - $4; exit }' "$keys_file")
comma_xy=$(key_xy "Comma")
if [ -n "$comma_xy" ] && [ -n "$comma_w" ]; then
    read -r cx cy <<< "$comma_xy"
    # Hold the comma until its popup opens, slide one cell right onto the emoji button, lift.
    adb shell "input motionevent DOWN $cx $cy; sleep 1; input motionevent MOVE $((cx + comma_w)) $((cy - comma_w)); input motionevent UP $((cx + comma_w)) $((cy - comma_w))"
    sleep 2
    shot 18-emoji
    read_keys 18-emoji
    if [ -n "$(key_xy "grinning face")" ]; then pass "comma long-press opens emoji"; else fail "comma long-press opens emoji" "no grinning face"; fi
    tap_key "grinning face" || true
    sleep 1
    check "emoji types" "😀" "$(field_text)"
    tap_key "Delete" || true
    sleep 1
    check "backspace takes the emoji" "" "$(field_text)"

    tap_key "Flags" || true
    sleep 1
    read_keys 18b-emoji-flags
    tap_key "rainbow flag" || true
    sleep 1
    check "flag with joiners types" "🏳️‍🌈" "$(field_text)"
    tap_key "Delete" || true
    sleep 1
    check "one backspace takes the whole flag" "" "$(field_text)"

    tap_key "People and body" || true
    sleep 1
    read_keys 18c-emoji-people
    xy=$(key_xy "waving hand")
    # shellcheck disable=SC2086
    [ -n "$xy" ] && adb shell input swipe $xy $xy 1000
    sleep 1
    shot 19-emoji-tones
    read_keys 19-emoji-tones
    tap_key "waving hand, skin tone 3" || true
    sleep 1
    check "skin tone picker" "👋🏽" "$(field_text)"
    clear_field
    tap_key "Letters" || true
    sleep 1
    read_keys 19b-after-emoji
    tap_key "Tools" || true
    sleep 1
    read_keys 19c-tools
    tap_key "Emoji" || true
    sleep 2
    shot 20-emoji-recents
    read_keys 20-emoji-recents
    if [ -n "$(key_xy "waving hand, skin tone 3")" ]; then pass "recent emoji row"; else fail "recent emoji row" "toned wave not shown"; fi
    tap_key "Letters" || true
else
    fail "comma long-press opens emoji" "no comma key"
fi

# ---- 7.6. Clipboard history, in another app's field ----

history_field() {
    adb shell am start -W -a android.intent.action.INSERT -t vnd.android.cursor.dir/contact >/dev/null 2>&1 || true
    sleep 4
    dump contacts
    xy=$(python3 "$ui" field "$out/contacts.xml")
    [ -n "$xy" ] || return 1
    # shellcheck disable=SC2086
    adb shell input tap $xy
    sleep 3
    read_keys history-keys
}

prefs clip_history=true
if history_field; then
    type_text "copyme"
    tap_key "Tools" || true
    sleep 1
    read_keys history-tools
    tap_key "Editing panel" || true
    sleep 1
    read_keys history-editing
    tap_key "Select all" || true
    tap_key "Copy" || true
    sleep 2
    read_keys history-copied
    if [ -n "$(key_xy "Clipboard history")" ]; then pass "history button shows"; else fail "history button shows" "missing"; fi
    tap_key "Clipboard history" || true
    sleep 1
    shot 21-clipboard-history
    read_keys 21-clipboard-history
    if [ -n "$(key_xy "copyme")" ]; then pass "a copy shows in clipboard history"; else fail "a copy shows in clipboard history" "not listed"; fi
    clear_field
    tap_key "copyme" || true
    sleep 1
    check "a clip pastes from history" "copyme" "$(field_text)"
    clear_field
    files=$(adb shell "run-as $pkg ls files" | tr -d '\r' | tr '\n' ' ')
    log "app files: $files"
    case " $files " in
        *" clips.bin "*) pass "history saved for a restart" ;;
        *) fail "history saved for a restart" "no clips.bin" ;;
    esac
    plain=$(adb shell "run-as $pkg cat files/clips.bin" | grep -a -c -e copyme -e Y29weW1l || true)
    check "saved history is encrypted" "0" "$plain"
    adb shell input keyevent KEYCODE_BACK
    prefs clip_history=true
    if history_field; then
        tap_key "Clipboard history" || true
        sleep 1
        read_keys 22-history-after-restart
        if [ -n "$(key_xy "copyme")" ]; then pass "history survives a restart"; else fail "history survives a restart" "not listed"; fi
        adb shell input keyevent KEYCODE_BACK
    fi
    adb shell input keyevent KEYCODE_BACK
else
    log "no Contacts field; clipboard history check skipped"
fi

# ---- 8. Themes ----

for t in light dark verdigris kawaii_light kawaii_dark material; do
    prefs theme="$t"
    open_field
    shot "12-theme-$t"
done
prefs theme=dark bordered=false
open_field
shot 13-theme-dark-flat

# ---- 9. Pages ----

adb shell am start -W -n "$pkg/io.github.nullangst.corkkeys.SettingsActivity" >/dev/null
sleep 2
shot 14-settings
adb shell am start -W -n "$pkg/io.github.nullangst.corkkeys.PrivacyActivity" >/dev/null 2>&1 || true
sleep 2
shot 15-privacy

adb logcat -d -s InputMethodManagerService:* InputMethodService:* ImeTracker:* > "$out/ime-log.txt" 2>&1 || true
adb logcat -d -b crash > "$out/crash.txt" 2>&1 || true
if [ -s "$out/crash.txt" ] && grep -q "$pkg" "$out/crash.txt"; then fail "no crashes" "see crash.txt"; else pass "no crashes"; fi

log "done"
grep -c '^PASS' "$out/results.txt" | sed 's/^/passed: /' | tee -a "$out/steps.txt"
grep -c '^FAIL' "$out/results.txt" | sed 's/^/failed: /' | tee -a "$out/steps.txt"
exit 0
