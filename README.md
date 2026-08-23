# Margelet

A Telegram fork for Android, built from [DrKLO/Telegram](https://github.com/DrKLO/Telegram)
with the patch in this repository.

- Channel: [t.me/margeletter](https://t.me/margeletter)
- Forum: [t.me/margeletforum](https://t.me/margeletforum)

Package: `cat.narezany.margelet` — it installs next to the official Telegram,
not over it. arm64 only.

## What it adds

**A message field that grows as far as you want.** Stock Telegram stops the
input box at six lines and starts scrolling. Here you pick: 2 to 15 lines, or
"max" — grow while there is room on the screen. The text size in the box is
adjustable too. (The 4096-character limit per message stays: that one is the
server's, not the app's.)

**The message field can move to the top of the chat**, under the header, with
the room for it moving to the top of the message list as well. The keyboard and
the panels stay at the bottom — they belong to the screen, not to the field.

**A dark green theme out of the box.** On first launch Margelet turns on
Telegram's own Night theme with its green accent. Once. Pick another one and the
fork stays out of it.

**Its own icon, in six colours** — green, night, lavender, sand, sea, rose —
all of them in the app-icon picker, none of them behind Premium.

**A cat.** Somewhere.

Everything the fork adds lives in one place: Settings → Margelet, the first row.

## Building it yourself

You need SDK 35 with build-tools 35.0.0, NDK 27.2.12479018 and JDK 21.

```
git clone https://github.com/DrKLO/Telegram
cd Telegram
git checkout $(cat ../margelet/patch/UPSTREAM)
git submodule update --init --recursive
git apply ../margelet/patch/margelet.patch
# put your own api_id / api_hash from https://my.telegram.org into
# TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java
gradle :TMessagesProj_AppStandalone:assembleAfatStandalone
```

The apk lands in `TMessagesProj_AppStandalone/build/outputs/apk/afat/standalone/`.

The patch contains the added files as well as the edits, so applying it is all
you need. (It did not, once: `git diff` silently leaves out files git has never
been told about, and the first published patch was missing every new class in
it. That is why `java/` and `res/` are also kept here as plain copies — and why
the patch is now generated after `git add -N`.)

## What this repository does not contain

- **api_id / api_hash.** Those are the build owner's personal keys. Get your own
  at my.telegram.org. They are never committed here.
- **google-services.json.** The one shipped in Telegram's sources describes
  Telegram's own Firebase project, which does not list our package. Until the
  fork has a project of its own the plugin stays disabled — which means **push
  notifications do not arrive while the app is not running.**

## Files here

| | |
|---|---|
| `patch/margelet.patch` | every change to Telegram's sources, in one file |
| `patch/UPSTREAM` | the Telegram commit the patch applies to |
| `FEATURES.md` | **every change, where it lives and why** — the porting document |
| `java/`, `res/` | files the fork adds whole |
| `tools/` | scripts: draw the icon, install it, synthesise a sound |
| `ATTRIBUTION.md` | the one thing in here that someone else made |

`FEATURES.md` is the file to read before moving the fork to a newer Telegram.
Telegram rewrites its screens often; the patch will stop applying. Each entry
says what was done, where, and — the part that survives a rewrite — why.

## Licence

Telegram's sources are GPL v2 or later, and the fork inherits that. If you hand
someone the apk, you owe them the source it was built from: this repository plus
the commit named in `patch/UPSTREAM` is exactly that.
