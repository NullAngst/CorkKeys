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
| Clipboard history | Only with "Clipboard history" on (off by default) | Memory, plus an encrypted file in credential-encrypted storage | Text you copied: pinned clips, and as many of the newest as "Saved across restarts" says (5 by default). |
| Recent emoji and skin tones | When you use the emoji board | Memory only | Which emoji you used last and the tone you picked for each. Gone when the keyboard restarts. |

A word is learned only after you type it in full twice, letter by letter. The count of how often a word has been typed is kept in memory only and is gone when the keyboard closes. Turning "Learn new words" off deletes the learned words.

Both word lists can be viewed and deleted in settings, under "Learned and removed words".

Settings live in device-protected storage so the keyboard works on the lock screen right after a reboot. Learned and removed words live in credential-encrypted storage and aren't read until the phone has been unlocked once.

## Fields that stay private

- Password fields: no suggestions, no autocorrect, no swipe, no key bubbles, nothing learned.
- Incognito fields: apps ask for this with Android's `IME_FLAG_NO_PERSONALIZED_LEARNING`. Signal with its "Incognito keyboard" setting on does, and so do Firefox and Chrome private tabs. In those fields nothing is learned, your learned words aren't offered as suggestions, and the space bar shows "incognito".
- Manual incognito: the switch in the long-press space menu, or on the Privacy page, does the same everywhere until you switch it back.

## Clipboard

With clipboard history off, which is how a new install starts, nothing touches the clipboard until you press Paste. The keyboard never checks whether anything is on the clipboard, never listens for changes, and keeps no history.

A Paste press first asks the app to paste, the same as the app's own Paste menu item, so the keyboard never sees the text. Only when the app says it can't (terminals, mostly) does CorkKeys read the clipboard, once, on that press, and type what is there. The editing panel's Cut, Copy and Paste work the same way.

### Clipboard history

Switch it on on the Privacy page and CorkKeys listens for copies. Android only lets the keyboard in use read the clipboard while another app is in front, which is what makes this work, and it's also why history only fills while CorkKeys is your keyboard.

- Every copy goes into memory. Unpinned clips are dropped after an hour, the same as Gboard. Pinned clips stay until you delete them.
- "Saved across restarts" picks how many of the newest unpinned clips are also written to storage, so a reboot doesn't lose them: none, 1, 3, 5, 10, 20 or 50, with 5 to start. Pinned clips are always saved. A saved clip still goes after its hour unless pinned.
- The saved file sits in credential-encrypted storage, unreadable until the phone's first unlock after a reboot, and its contents are encrypted again with AES-256-GCM. The key lives in Android's Keystore: it can't be copied off the phone and only works while the phone is unlocked. Until it can be used, history stays in memory and the file is left alone.
- Copies an app marks sensitive with Android's `EXTRA_IS_SENSITIVE` are never recorded. Password managers mark passwords this way. An app that doesn't mark them leaves that to you: delete the clip, or keep history off.
- Nothing is recorded while incognito, whether you switched it on or the app asked for it.
- The history button is hidden in password fields and while the phone is locked, so a clip can't be pasted from the lock screen.
- Switching history off deletes everything at once: memory, the saved file and pinned clips.
- Clipboard history isn't in Android's backup (the app opts out of backup entirely).

CI fails if Android's clipboard service shows up anywhere outside `Paste.kt` and `ClipboardHistory.kt` (`.github/scripts/check_clipboard.sh`), so those two files are the whole story.

## Emoji

The emoji list ships inside the app. Recently used emoji and the skin tone picked for each are kept in memory only, never written to storage, so they reset when the keyboard restarts.

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
gh attestation verify CorkKeys-v0.6.0.apk --repo NullAngst/CorkKeys
```
