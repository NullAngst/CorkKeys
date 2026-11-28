# CorkKeys

CorkKeys is a private Android keyboard for English, German and Spanish, for anyone who wants swipe, suggestions and a Paste button without a keyboard that phones home or remembers what they type.

It is built the way [Corkboard](https://github.com/NullAngst/Corkboard) is: small enough to read in an afternoon, one feature per file, no networking at all, and nothing that records you unless you switch it on. It replaces OpenBoard, which no longer works well on current Android, and shares no code with it.

## What it does

A new install is a plain keyboard. Suggestions, autocorrect, swipe and learned words stay off until you turn them on.

- English QWERTY, German QWERTZ with ü, ö and ä on the main layer, Spanish with ñ on the main layer.
- A symbol on every letter's long-press, in the same spots as Gboard, with accents in the same popup. Long-press and release types the symbol; slide to an accented letter to pick it instead. ß and ẞ sit on s.
- ?123 opens the symbols page, and a key there opens a second page with currency, math signs, °, ©, ®, ™, • and the less common brackets. The currency key follows the language: € for German and Spanish, $ for English.
- A number pad for number, date and time fields and a phone pad for phone fields, opened on their own from the field type.
- An optional number row, off by default.
- Long-press the space bar for the language menu and the incognito switch. The space bar always shows the language: `EN`, or `EN • DE` with two languages at once.
- Swipe typing, written from scratch: no Google library, no model files.
- Suggestions and autocorrect from the bundled word lists, on the phone. Swear words and slurs are never corrected into something else.
- Double-space period, automatic capitals at sentence starts, space after a suggestion.
- A Paste button at the right end of the strip. Nothing touches the clipboard until you press it.
- Slide along the space bar to move the cursor. Slide left from backspace to delete whole words, slide back right to put them back before you lift. Hold backspace to repeat.
- A tools row with the editing panel (arrows, select, select all, cut, copy, paste, start and end of line), one-handed mode, a floating keyboard you drag anywhere, a voice keyboard handoff and settings.
- Android's spell checker service, from the same word lists, for red underlines in other apps.
- Themes: Light, Dark, Verdigris, Kawaii Light, Kawaii Dark and Material You, the same palettes as Corkboard. Bordered or flat keys, three heights.
- TalkBack support with explore-by-touch typing.
- Works on the lock screen right after a reboot.

Left out on purpose: emoji, GIF and sticker search, clipboard history, next-word prediction, cloud sync, online spell check, built-in voice typing, analytics, crash reporting, ads.

## Privacy, in short

CorkKeys asks Android for no permissions at all. Without INTERNET, Android refuses every network connection it could try to make. Nothing you type is written anywhere unless you turn on learned words, and then only the words themselves are kept. CI fails the build if a permission, a logging call, or clipboard use outside `Paste.kt` ever shows up. The full notes are in [PRIVACY.md](PRIVACY.md).

## Install and set up

Prerequisites: Android 10 or newer.

1. Download `CorkKeys-vX.Y.Z.apk` from [Releases](https://github.com/NullAngst/CorkKeys/releases) and install it.
2. Open CorkKeys. The setup screen opens on first launch.
3. Tap **Open keyboard settings** and turn CorkKeys on. Android warns that a keyboard can collect what you type; that's the standard warning for every keyboard.
4. Back in CorkKeys, tap **Switch keyboard** and pick CorkKeys.
5. Type in the test box. **Open settings** takes you to the switches.

To use the spell checker too, pick CorkKeys under Android's Settings, System, Languages, Spell checker. The path differs a little between phones, so look for "Spell checker" in the Settings search if it's not there.

Settings open from the launcher icon, from the tools row, or by long-pressing the comma key.

## Languages

English (US), German and Spanish, each on or off in settings; one always stays on. Need another language? Open an issue. Adding one is a layout file, a word list and a strings folder, and [maintenance.md](maintenance.md) walks through it.

## Word lists

The lists come from the [aosp-dictionaries](https://codeberg.org/Helium314/aosp-dictionaries) repository, the ones built from the [Leipzig Corpora Collection](https://wortschatz.uni-leipzig.de/en/download)'s sentence lists, under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Next-word data is dropped, since there is no next-word prediction, and each list is cut to its most frequent words. The files in `app/src/main/assets/words/` are plain gzipped text, one `word<TAB>frequency<TAB>flag` line per word, so anyone can read exactly what the keyboard knows. The `<lang>_extra.txt` files add swear words and slurs the sources miss, so autocorrect knows them and leaves them alone.

| Language | Code | Words shipped | Source list | Source words | Source date |
|---|---|---|---|---|---|
| English (US) | en | 250,000 | main_en_US__experimental | 279,913 | 2024-01-02 |
| German | de | 250,000 | main_de | 1,343,311 | 2023-09-25 |
| Spanish | es | 250,000 | main_es__experimental | 698,766 | 2023-09-09 |

Built from news and web sentences, so the vocabulary is current but carries some junk and rare forms. The source repository warns about that too.

To refresh them, go to Actions, click the **word lists** workflow, Run workflow. It fetches the sources on GitHub's runners, converts them with `tools/convert_wordlist.sh` and commits the result to the `wordlists-refresh` branch. Open a pull request from that branch, and update the table above, since a unit test checks the counts match.

## Building

Prerequisites: JDK 17 and the Android SDK (compile SDK 37), or just Android Studio, which brings both.

1. `git clone https://github.com/NullAngst/CorkKeys.git`
2. `cd CorkKeys`
3. `bash ./gradlew assembleDebug`, since `bash` works even when the executable bit got lost.
4. The APK is at `app/build/outputs/apk/debug/app-debug.apk`. Debug builds install as "CorkKeys debug" next to a release build.

Unit tests: `bash ./gradlew testDebugUnitTest`. They cover the layouts, geometry, shift, punctuation rules, word lists, corrections, learning, and a synthetic swipe test over the 500 most common words per language.

The same checks CI runs, from the repo root:

```
bash .github/scripts/check_runtime_deps.sh
bash .github/scripts/check_permissions.sh
bash .github/scripts/check_no_logging.sh
bash .github/scripts/check_clipboard.sh
```

## Signing

Releases are signed by GitHub Actions with a keystore stored in repo secrets. Make it once. `keytool` comes with a JDK; I use apt, so in my case that's `sudo apt install default-jdk-headless`, or whichever package your system calls it.

1. `keytool -genkeypair -v -keystore corkkeys.jks -alias corkkeys -keyalg RSA -keysize 4096 -validity 10000`
2. `base64 -w0 corkkeys.jks > corkkeys.jks.b64` (on macOS: `base64 -i corkkeys.jks -o corkkeys.jks.b64`)
3. In the repo, go to Settings, Secrets and variables, Actions, and add four repository secrets:
   - `KEYSTORE_BASE64`: the contents of `corkkeys.jks.b64`
   - `KEYSTORE_PASSWORD`: the keystore password
   - `KEY_ALIAS`: `corkkeys`, or whichever alias you used
   - `KEY_PASSWORD`: the key password. Newer JDKs don't ask for a separate one, so it's the same as the keystore password.
4. Delete `corkkeys.jks.b64`.

If a secret is wrong or missing, the release run stops at the "Rename APK" step with "Signed APK not found". Fix the secret and run it again on the same tag; it replaces the APK.

BACK UP THE KEYSTORE AND ITS PASSWORDS. If it is lost, no future APK can install over the current one, and the only way forward is uninstalling the keyboard, which deletes your settings and learned words.

## Making a release

1. Go to Releases, draft a new release, create a new tag there (like `v0.6.0`), and publish it.
2. Go to Actions, click the **build** workflow, click **Run workflow**.
3. Pick that tag under "Use workflow from" and run it.
4. The workflow builds from that tag and attaches the signed `CorkKeys-v0.6.0.apk` to the existing release, with a signed SLSA build provenance attestation.

Versions come from the tag: `v1.6.0` becomes versionName `1.6.0` and versionCode `10600`. Releases bump the minor number: `v0.5.0`, then `v0.6.0`, and so on.

The workflow fails with a clear message if it was run on a branch instead of a tag, or if no release exists for that tag yet. Rerunning it on the same tag replaces the APK. Pushes to `main` and pull requests only build a debug APK, run lint and unit tests, and run the four checks above; nothing is released on a tag push.

To check an APK's provenance: `gh attestation verify CorkKeys-v0.6.0.apk --repo NullAngst/CorkKeys`

## Credits and licence

CorkKeys is GPL-3.0, see [LICENSE](LICENSE). Inspired by OpenBoard; written from scratch, with no OpenBoard or AOSP keyboard code. The swipe decoder follows the SHARK2 method from Kristensson and Zhai's 2004 paper.

The word lists are from the Leipzig Corpora Collection, via the aosp-dictionaries repository, under CC BY 4.0, which allows shipping them inside a GPL-3.0 app with this credit.
