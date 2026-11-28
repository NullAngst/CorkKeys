# Maintenance

A check-up every couple of months keeps CorkKeys building on the newest Android and its tools current. Most of it is reading a few pages and bumping a few numbers. Plan on an hour when nothing broke, an afternoon when something did.

## 1. Check the build still passes

1. Open the repo's Actions tab and look at the latest **build** run on `main`. Green means it still builds, lints and passes its tests on GitHub's current runner image.
2. If it's red, open the failed step. Compile errors, lint errors and failed tests are also posted as annotations on the run's summary page, so you rarely need the full log.
3. Locally: `bash ./gradlew assembleDebug lintDebug testDebugUnitTest`.

## 2. Bump the build tools

Dependabot opens pull requests for Gradle and the GitHub Actions versions on its own. Merge those once their checks pass. Then look at what it doesn't cover:

1. Android Gradle Plugin: the version is in `build.gradle.kts` at the repo root. Check the [AGP release notes](https://developer.android.com/build/releases/gradle-plugin) for the newest stable version and what Gradle version it needs.
2. Gradle wrapper: `bash ./gradlew wrapper --gradle-version <version>`, then commit `gradle/wrapper/`.
3. Compile and target SDK: when a new Android version is out of preview, raise `compileSdk` and `targetSdk` in `app/build.gradle.kts`. Read that version's "behavior changes for apps targeting" page; the parts that bite keyboards are window insets, edge-to-edge, and input method changes.
4. JUnit is the only test library. It changes rarely.

After any bump, run the build and the four checks:

```
bash .github/scripts/check_runtime_deps.sh
bash .github/scripts/check_permissions.sh
bash .github/scripts/check_no_logging.sh
bash .github/scripts/check_clipboard.sh
```

IF CHECK_PERMISSIONS FAILS AFTER A BUMP, DON'T WAVE IT THROUGH. A build tool added a permission on its own. Find what added it (the merged manifest names it) and remove it with a `tools:node="remove"` entry in `AndroidManifest.xml`.

## 3. Check on a phone

Before each release, and after an Android update on your own phone:

1. Install the debug build from the latest Actions run (the `CorkKeys-debug` artifact).
2. Type in each language: accents, ß and ẞ, ñ, ¿ and ¡, symbols on long-press, both symbol pages, the number and phone pads (a calculator app and the dialer work for those).
3. Rotate mid-word, in portrait and landscape, with gesture and three-button navigation. Keys should fill the width and stay clear of the navigation bar.
4. Swipe a few sentences. Try the editing panel, one-handed on both sides, the floating keyboard, all three heights.
5. Paste in a plain text field, Chrome, Firefox and a terminal app (Termux uses the fallback path).
6. Reboot, and type the lock screen PIN with CorkKeys before unlocking.
7. Turn on TalkBack and type a word by sliding and lifting.
8. Screenshot each theme, bordered and flat.

The emulator harness Corkboard uses can be adapted for CorkKeys; keep it out of `main`, as Corkboard does.

## 4. Memory and the word cap

Each list is cut to 250,000 words. Measured on a desktop JVM, loaded with its trie:

| Loaded | Heap |
|---|---|
| English | about 21 MB |
| German | about 22 MB |
| Spanish | about 13 MB |
| English and German together | about 43 MB |

Nothing loads with suggestions and swipe off, so a plain keyboard holds none of this. On the phone, measure English and German together with Android Studio's memory profiler (or `adb shell dumpsys meminfo io.github.nullangst.corkkeys`) and write the number here. If it gets uncomfortable on older phones, lower the cap in `.github/scripts/fetch_wordlists.sh` (`WORD_CAP`) and refresh the lists.

Swipe decodes take 10 to 15 ms on a desktop JVM. They run on the word thread either way, so a slower phone shows the word a moment later instead of freezing the keys.

## 5. Refresh the word lists

The sources change rarely. Every few months:

1. Look at the [aosp-dictionaries](https://codeberg.org/Helium314/aosp-dictionaries) repository for newer `main_en_US__experimental`, `main_de` and `main_es__experimental` lists.
2. If there are, go to Actions, **word lists**, Run workflow. It commits converted lists to the `wordlists-refresh` branch.
3. Open a pull request from that branch. The run's annotations show the new word counts; update the table in README.md to match, since `DictionaryTest` checks it.
4. Check the typo tables in `CorrectorTest` still pass. If a new list drops a word the extra lists rely on, add it to `<lang>_extra.txt`.

## 6. Adding a language

1. A layout: copy `LayoutSpanish.kt` to `Layout<Name>.kt` and change the rows and accents. Add it to `Letters.data()` in `Key.kt`.
2. Add the code to `Languages.ALL`, a name in `Languages.name()`, the currency in `LongPressSymbols.currency()` if it uses €, and a `<subtype>` in both `res/xml/method.xml` and `res/xml/spellchecker.xml`.
3. A word list: add a `convert` line to `.github/scripts/fetch_wordlists.sh`, run the word lists workflow, and add `app/src/main/assets/words/<lang>_extra.txt`.
4. A strings folder: copy `res/values/strings.xml` to `res/values-<lang>/strings.xml` and translate it. Lint fails if a string is missing.
5. Add the language to the tests' language lists, the typo and keep tables in `CorrectorTest`, and the README table.

## 7. Release

Follow "Making a release" in the README. Add an entry to `changes.md` first.
