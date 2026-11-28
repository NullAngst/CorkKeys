# Changes

2026.11.27 - First build

* Build setup matching Corkboard: Android Gradle Plugin 9, minSdk 29, target and compile SDK 37, R8 for release, debug builds installable next to release builds
* CI: debug build, lint and unit tests on pushes and pull requests, plus checks that fail the build on any permission in the merged release manifest, any runtime library besides the Kotlin standard library, any logging call, or clipboard use outside Paste.kt
* Release job on manual dispatch from a tag only, signed APK with build provenance attestation attached with gh release upload
* Word list tooling and workflow, and English (US), German and Spanish lists from aosp-dictionaries (Leipzig Corpora Collection, CC BY 4.0), 250,000 words each, with extra lists for swear words and slurs
* Layouts: English QWERTY, German QWERTZ, Spanish, Gboard's long-press symbol map with accents in the same popup, two symbol pages, number and phone pads, optional number row
* Shift and caps lock, automatic capitals, double-space period, space bar cursor, backspace repeat and backspace by word
* Long-press space language menu with incognito, the Paste button, the editing panel
* Suggestions, autocorrect that never touches words in the lists, learned and removed words, two languages at once, Android spell checker service
* Swipe typing with a from-scratch SHARK2 decoder and a trail in the theme's accent colour
* One-handed mode, floating keyboard, voice keyboard handoff
* Six themes matching Corkboard, bordered or flat keys, three heights, TalkBack support, direct boot
* Setup screen, settings, Privacy page, README, PRIVACY.md and maintenance.md

Reasons for changes: first build of CorkKeys, a from-scratch replacement for OpenBoard, which no longer works well on current Android.
