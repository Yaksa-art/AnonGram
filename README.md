<div align="center">

<img src="assets/banner.png" alt="Margy" width="640">

**A Telegram fork for Android.**
Formerly **Margelet** — renamed to Margy; the package name, links and settings are unchanged.
Built from [DrKLO/Telegram](https://github.com/DrKLO/Telegram) with the patch in this repository.

[Русский](README.ru.md) · [中文](README.zh.md)

[![channel](https://img.shields.io/badge/channel-margeletter-8DD1B0?style=flat-square)](https://t.me/margeletter)
[![forum](https://img.shields.io/badge/forum-margeletforum-8DD1B0?style=flat-square)](https://t.me/margeletforum)
[![licence](https://img.shields.io/badge/licence-GPL--2.0--or--later-8DD1B0?style=flat-square)](#licence)

</div>

---

Package `cat.narezany.margelet`. It installs **next to** the official Telegram,
not over it. arm64 only.

Everything the fork adds lives in one place: **Settings → Margy**, the first row.

## What it adds

<details>
<summary><b>A message field that grows as far as you want</b></summary>

Stock Telegram stops the input box at six lines and starts scrolling. Here you
pick: 2 to 15 lines, or *max* — grow while there is room on the screen. The text
size inside the box is adjustable too.

The 4096-character limit per message stays. That one belongs to the server, not
to the app, and no client can lift it.
</details>

<details>
<summary><b>The message field can move to the top of the chat</b></summary>

It moves under the chat header, and the room reserved for it in the message list
moves up as well. The keyboard and the attachment panels stay at the bottom —
they belong to the screen, not to the field.

Applies to the next chat you open: in an already open one half the sizes are
counted from the old side.
</details>

<details>
<summary><b>Music tags: title, artist, cover</b></summary>

Long-press a track in a chat, then **Track tags**. In stock Telegram this is
done through bots, which means handing your file to someone else's server for
the sake of three lines of text. Here it happens on the phone: the tags are
written into a copy of the file, and the copy is sent to the same chat.

The cover is picked with Telegram's own gallery and cropped with its own
avatar-cropping screen.
</details>

<details>
<summary><b>Streamer mode</b></summary>

Your phone number is covered with dots everywhere the app shows it. Optionally
the numbers of other people and your own username too.

There is no tap-to-reveal, on purpose: on stream an accidental touch of the
screen is exactly the case the mode is turned on for. It opens only from the
switch in settings.
</details>

<details>
<summary><b>IDs in profiles</b></summary>

A line with the numeric id appears in profiles of people, bots, groups and
channels. Tap it to copy. Can be turned off.
</details>

<details>
<summary><b>Deleted gifts</b></summary>

Gifts Telegram took out of the catalog are added back to the end of the list, so
they can be sent again. The purchase is still confirmed by the server: if a gift
is really closed, sending it simply fails.

The trick and the list of gifts are by **[@binbash_0](https://t.me/binbash_0)**,
author of the *Deleted Gift Sender* plugin. The code here is written from
scratch, the idea is his.
</details>

<details>
<summary><b>A dark green theme out of the box</b></summary>

On first launch Margy turns on Telegram's own *Night* theme with its green
accent. Once. Pick another one and the fork stays out of it.
</details>

<details>
<summary><b>Its own icon, in six colours</b></summary>

Green, night, lavender, sand, sea, rose — all of them in the app-icon picker,
none of them behind Premium.
</details>

<details>
<summary><b>Text formatting of its own</b></summary>

Select a word and the menu gains "Size", "Dim" and "Rainbow" next to bold and
italic. Size is bounded both ways: neither two pixels nor full screen.

Telegram's list of formatting types is closed and lives on the server, so nothing
can be added to it. The formatting therefore travels inside the message text as
invisible characters and is decoded by the fork itself. Without the fork you see
plain text and a link line at the end of the message.

Each type can be turned off separately. Next to it, "Copy with formatting": a
plain copy in Telegram gives bare text, this keeps bold, italics, links and our
own formatting.
</details>

<details>
<summary><b>Donate</b></summary>

Its own row in the fork menu: the author's details, tap to copy.

There are no payment buttons inside the app and there will not be any — a
messenger fork is a bad place to type payment details into. And donating unlocks
nothing: there are no paid features here.
</details>

<details>
<summary><b>Badges</b></summary>

The person who made the fork, and their best friend, get a badge next to their
name — in the profile, as its own block under the details, and in the chat list.
Tapping it opens a window with the icon spinning in 3D: green and lavender
respectively.

The Margy channel and forum carry one too. There it answers "is this really
the right channel": the ids are compiled into the build.

The badge certifies nothing and asks no server: whoever builds their own fork
puts their own people in. The window says so.
</details>

<details>
<summary><b>Seizure</b></summary>

Every piece of text in the app keeps changing colour. It is good for nothing.

It is not a flicker: the hue moves smoothly and the brightness stays put.
Flashing between three and thirty times a second is what triggers photosensitive
seizures, and that is not something to ship even on request. There is a warning
before you turn it on anyway.
</details>

<details>
<summary><b>A cat</b></summary>

Somewhere. Want yours in the app? Write to [@narezany](https://t.me/narezany).
</details>

<details>
<summary><b>Python plugins</b></summary>

Settings → Margy → Plugins. A plugin is a `.marp` archive: a manifest,
`main.py`, and anything else it needs. Install from a file, a list, a console
with errors.

There is no sandbox. A plugin runs inside the app and can do anything the app
can; the permissions in the manifest are the author's word, the app does not
check them. The install dialog says exactly that. In exchange the forum rule
applies: **plugin code is not obfuscated**, anyone must be able to read it.

Plugins are off entirely by default, and a new one is installed disabled.
Documentation for authors: [docs/plugins.md](docs/plugins.md).

Badges are described in [docs/badges.md](docs/badges.md): the list lives in
`badges.json` and the app re-reads it on every start.

Releasing a new version: [docs/release.md](docs/release.md).
</details>

## Building it yourself

You need SDK 35 with build-tools 35.0.0, NDK 27.2.12479018 and JDK 21.

```bash
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
it. That is why `java/` and `res/` are also kept here as plain copies, and why
the patch is now generated after `git add -N`.)

## What this repository does not contain

- **api_id / api_hash.** The build owner's personal keys. Get your own at
  my.telegram.org. They are never committed here.
- **google-services.json.** The one shipped in Telegram's sources describes
  Telegram's own Firebase project, which does not list our package. Until the
  fork has a project of its own the plugin stays disabled, which means **push
  notifications do not arrive while the app is not running.**

## Files here

| | |
|---|---|
| `patch/margelet.patch` | every change to Telegram's sources, in one file |
| `patch/UPSTREAM` | the Telegram commit the patch applies to |
| `FEATURES.md` | **every change, where it lives and why** — the porting document |
| `java/`, `res/` | files the fork adds whole |
| `python/` | the layer between the app and its plugins |
| `badges.json` | **who has which badge** — edited here, no rebuild needed |
| `version.json` | the latest version and where its apk lives — this is what triggers the update bar |
| `docs/` | plugin and badge documentation, and an example plugin |
| `tools/` | scripts: draw the icon, install it, synthesise a sound |
| `assets/` | the logo, in svg and png |
| `ATTRIBUTION.md` | the things in here that someone else made |

`FEATURES.md` is the file to read before moving the fork to a newer Telegram.
Telegram rewrites its screens often; the patch will stop applying. Each entry
says what was done, where, and — the part that survives a rewrite — why.

## Licence

Telegram's sources are GPL v2 or later, and the fork inherits that. If you hand
someone the apk, you owe them the source it was built from: this repository plus
the commit named in `patch/UPSTREAM` is exactly that.
