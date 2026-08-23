# Margelet plugins

**English** · [Русский](plugins.ru.md) · [中文](plugins.zh.md)

A plugin is Python code that runs inside the fork. Not a separate program, not
a bot on the side: it lives in the same app your chats do.

## The forum rule

**Plugin code is not obfuscated.** It ships as source, and anyone must be able
to open it. Obfuscation means a ban on the forum.

The reason is plain: there is no sandbox here. The only way to know what a
plugin does is to read it. Hidden code takes that away from everyone at once.

## Honestly, about safety

A plugin can do anything the app can: read your chats, write as you, get at
your files. The permissions in the manifest are the **author's declaration**,
not a restriction; the app does not check them and cannot.

Install only what you have read yourself or what you trust.

## The .marp format

An ordinary zip renamed to `.marp`:

```
margelet_example.marp
├── manifest.json   required
├── main.py         required
├── icon.png        optional
└── ...             anything else the plugin needs
```

`icon.png` is shown in the plugin list. A square picture; 128×128 is plenty.

## manifest.json

```json
{
  "id": "margelet.example",
  "name": "Example",
  "version": "1.0",
  "author": "narezany",
  "description": "Says hello in the console.",
  "permissions": ["ui"]
}
```

| Field | What it is |
|---|---|
| `id` | The plugin's number. Latin letters and dots. Updates and settings are keyed by it — do not change it. |
| `name` | The name in the list. |
| `version` | Version, as a string. |
| `author` | Who wrote it. |
| `description` | A line or two: what it does. |
| `permissions` | What the plugin declares about itself. The list is below. |
| `name_en`, `name_zh`, `description_en`, … | The same in another language. The app picks by its own language and falls back to the plain field. |

Permissions: `read_chats`, `send_messages`, `edit_messages`,
`delete_messages`, `change_profile`, `ui`. A name of your own works too — it is
shown as written.

## main.py

```python
def on_start():
    margelet.log("hello from the plugin", margelet.name)
```

`on_start()` is called when the plugin starts. Without it the plugin is simply
executed top to bottom.

The `margelet` object is available without an import:

| | |
|---|---|
| `margelet.id` | the number from the manifest |
| `margelet.name` | the name from the manifest |
| `margelet.folder` | the plugin's folder on the phone |
| `margelet.log(*parts)` | a line in the console |
| `margelet.error(*parts)` | the same, in red |

`print()` goes to the console as well — it is intercepted.

## What else is available

The Python here is the real thing, with access to the app's Java classes:

```python
from java import jclass

Config = jclass("org.telegram.margelet.MargeletConfig")
margelet.log("watermark:", Config.watermarkOnSend())
```

The rest of the app is reachable from there. That is what "a plugin can do
everything" means — it is not a figure of speech.

## The console

Settings → Margelet → Plugins → Console. Everything the plugins print goes
there, and their errors in red. Python fails silently, so without this screen
an author learns about a typo only from "nothing works".

## Installing

Settings → Margelet → Plugins → Install from file. The install dialog shows the
author and the declared permissions.

You can also tap a `.marp` file right in a chat — the app will offer to install
it.

A new plugin is installed disabled. Tap the row to turn it on, hold it for the
card and for deleting.

Turning a plugin off means "do not start it again". There is no way to stop
Python code that is already running — it lives until the app is restarted.

## The example

[margelet_example.marp](margelet_example.marp) — the same one that ships with
the app. Two lines inside; they are a fine place to start.

## Questions

[Forum](https://t.me/margeletforum) · [Channel](https://t.me/margeletter)
