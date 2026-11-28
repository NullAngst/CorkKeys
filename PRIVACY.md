# CorkKeys privacy notes

A keyboard sees every password and message you type. These notes say exactly what CorkKeys can and can't do with that, and how each claim is checked.

## Network

CorkKeys declares no permissions at all, so it has no INTERNET permission, and Android refuses every network connection it could try to open. There is no networking code to begin with. CI reads the merged release manifest, the one that actually goes into the APK after every build tool has had its say, and fails if any permission appears in it (`.github/scripts/check_permissions.sh`).

The Privacy page in the app reads the installed app's own permission list and shows it. It should say "None".

## What is kept

On a new install, nothing you type is written anywhere. The word you are typing lives in memory until it goes into the app, then it's gone.

| What | When | Where | What exactly |
|---|---|---|---|
| Settings | Always | Device-protected storage | Your switches and choices. No typed text. |
| Learned words | Only with "Learn new words" on (off by default) | Credential-encrypted storage | The words themselves. Never what came before or after them, when you typed them, or how often. |
| Removed words | When you long-press a suggestion to remove it | Credential-encrypted storage | The words themselves, so they stay removed. |

A word is learned only after you type it in full twice, letter by letter. The count of how often a word has been typed is kept in memory only and is gone when the keyboard closes. Turning "Learn new words" off deletes the learned words.

Both word lists can be viewed and deleted in settings, under "Learned and removed words".

Settings live in device-protected storage so the keyboard works on the lock screen right after a reboot. Learned and removed words live in credential-encrypted storage and aren't read until the phone has been unlocked once.

## Fields that stay private

- Password fields: no suggestions, no autocorrect, no swipe, no key bubbles, nothing learned.
- Fields that ask for no personalised learning (`IME_FLAG_NO_PERSONALIZED_LEARNING`, used by private browser tabs and incognito apps): nothing is learned.
- Manual incognito: the switch in the long-press space menu, or on the Privacy page, turns learning off everywhere until you switch it back. The space bar shows it while it's on.

## Clipboard

Nothing touches the clipboard until you press Paste. The keyboard never checks whether anything is on the clipboard, never listens for changes, and keeps no history.

A press first asks the app to paste, the same as the app's own Paste menu item, so the keyboard never sees the text. Only when the app says it can't (terminals, mostly) does CorkKeys read the clipboard, once, on that press, and type what is there. The editing panel's Cut, Copy and Paste work the same way.

CI fails if Android's clipboard service shows up anywhere outside `Paste.kt` (`.github/scripts/check_clipboard.sh`).

## Spell checker

Android only sends words from other apps to CorkKeys after you pick it as the spell checker in Android's own settings. It checks a word and answers. It keeps nothing and never adds to learned words.

## Floating keyboard

Its window covers the screen so it can move anywhere, but only the keyboard's own rectangle takes touches. Everything else goes straight to the app underneath.

## Logs, crashes, backups

- No logging of anything. CI fails if `android.util.Log` shows up in the app's code (`.github/scripts/check_no_logging.sh`).
- No crash reporter and no analytics. A crash goes to Android's own log like any other app's.
- Backup rules exclude everything from Android's cloud backup and from device-to-device transfer, for both storage areas.

## Code

The app uses Android's own APIs and the Kotlin standard library, nothing else. CI checks the release build's runtime libraries (`.github/scripts/check_runtime_deps.sh`). Releases are built by GitHub Actions from a tag and carry a signed build provenance attestation, so anyone can check an APK came from this source:

```
gh attestation verify CorkKeys-v1.0.0.apk --repo NullAngst/CorkKeys
```
