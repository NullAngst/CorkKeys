# Changes

2026.11.27 - Suggestions, swipe, modes and docs

* Word lists for English (US), German and Spanish from aosp-dictionaries (Leipzig Corpora Collection, CC BY 4.0), 250,000 words each, with extra lists for swear words and slurs
* Suggestions with three slots, autocorrect that never touches words in the lists, undo with backspace, long-press a suggestion to remove it
* Learned words on the Privacy page, off by default, deleted when switched off; a page to view and delete learned and removed words
* Two languages at once, and Android's spell checker service from the same lists
* Swipe typing with a from-scratch SHARK2 decoder and a trail in the theme's accent colour
* One-handed mode, floating keyboard, voice keyboard handoff
* Word list credit in the About section of settings
* Word lists load whether the APK holds them gzipped or unpacked: on a device they never loaded, which left suggestions, autocorrect, swipe and the spell checker empty
* A word list that fails to load is named in settings instead of failing silently
* Incognito when an app asks for it (Signal's incognito keyboard, private browser tabs): the space bar shows it, and learned words stay out of suggestions as well as nothing being learned
* Keyboard height: a Taller preset and a custom row height slider
* Landscape: the keys take at most 34% of the screen's height (42% in portrait) and the suggestion strip is shorter, so the app keeps room for the field being typed in
* Settings screens scroll a focused field back into view when the keyboard opens, so the setup screen's test field isn't hidden in landscape
* Screenshots in the README, from the device test
* Memory measured on Android 15 and written into maintenance.md, along with the device test on the devtest branch
* Full README, PRIVACY.md, maintenance.md and this file

Reasons for changes: v0.5.0 went out with only the plain keyboard, because the later pull requests merged into each other's branches instead of main. This brings the rest of the first build in.

2026.11.27 - First build (v0.5.0)

* Build setup matching Corkboard: Android Gradle Plugin 9, minSdk 29, target and compile SDK 37, R8 for release, debug builds installable next to release builds
* CI: debug build, lint and unit tests on pushes and pull requests, plus checks that fail the build on any permission in the merged release manifest, any runtime library besides the Kotlin standard library, any logging call, or clipboard use outside Paste.kt
* Release job on manual dispatch from a tag only, signed APK with build provenance attestation attached with gh release upload
* Word list tooling and the word lists workflow
* Layouts: English QWERTY, German QWERTZ, Spanish, Gboard's long-press symbol map with accents in the same popup, two symbol pages, number and phone pads, optional number row
* Shift and caps lock, automatic capitals, double-space period, space bar cursor, backspace repeat and backspace by word
* Long-press space language menu with incognito, the Paste button, the editing panel
* Six themes matching Corkboard, bordered or flat keys, three heights, TalkBack support, direct boot
* Setup screen, settings and the Privacy page

Reasons for changes: first build of CorkKeys, a from-scratch replacement for OpenBoard, which no longer works well on current Android.
