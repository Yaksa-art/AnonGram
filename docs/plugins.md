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
  "min_version": "0.3",
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
| `min_version` | The oldest Margelet the plugin works on. On anything older it will not install at all — with an explanation, not silently. Optional. |
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
| `margelet.ui(call, delay_ms=0)` | run this on the main thread — anything touching the screen has to |
| `margelet.every(ms, call)` | repeat every `ms`. Returns a handle |
| `margelet.cancel(handle)` | stop repeating |
| `margelet.toast(text)` | a short line over the screen |
| `margelet.get(key, fallback=None)` | the plugin's own memory |
| `margelet.set(key, value)` | write to it |
| `margelet.flag(key, fallback=False)` | read a switch from the settings screen as yes/no |
| `margelet.background(call)` | do something slow away from the screen |
| `margelet.send(chat, text)` | send a message to a chat |
| `margelet.fetch(url, call)` | go to the network and call `call(text)`; `None` if it failed |
| `margelet.activity()` | the app's current screen |
| `margelet.window(title, view)` | show your own window in the app's own dressing |
| `margelet.color(0xFFRRGGBB)` | a colour in the form Android understands |

`get` and `set` survive both a restart and an update of the plugin itself:
they are not kept in the plugin's folder, which is replaced on update.

## Events

A plugin does not poll the app — the app calls the plugin.

| | |
|---|---|
| `margelet.on_chat_opened(call)` | a chat was opened; gets the chat screen |
| `margelet.on_send(call)` | a text is being sent, before it goes |
| `margelet.on_message(call)` | a message arrived |
| `margelet.button(title, call)` | your own line in the chat menu (the three dots) |
| `margelet.on_settings(call)` | a setting of this plugin was changed |

There are deliberately few doors, and each one has a name. That is not the same
as letting a plugin replace any method of the app: replacing arbitrary methods
means rewriting someone else's code at runtime, it needs a separate library
that patches machine code, and every Telegram update breaks everything written
on top of it. A named door survives updates, because we are the ones who keep
it, not a coincidence of names.

If you need a door that is not here, [say so on the forum](https://t.me/margeletforum).
We will add a named one rather than open all of them at once.

### A chat was opened

```python
def on_start():
    margelet.on_chat_opened(sit_on_the_box)

def sit_on_the_box(chat):
    box = chat.getChatActivityEnterView()
    ...
```

Called every time a chat screen comes up, and handed that screen.

### Sending

```python
def on_start():
    margelet.on_send(sign)

def sign(text, chat):
    if text.startswith("/"):
        return False          # do not send at all
    return text + " 🌿"       # this goes instead
```

What to return: a string — that is what gets sent; `False` — do not send;
nothing — leave it alone. If several plugins are subscribed they are called in
turn, each seeing the text as the previous one left it.

This is the one event the app **waits** for: while the handler thinks, a person
is looking at an unsent message. If a handler took longer than a tenth of a
second, the console says so — not as a reproach, but so the author knows.

> **Never go to the network from a send handler.**
>
> This is a warning, not a style note. The handler runs on the same thread that
> draws the screen: while a request is in flight, the phone draws nothing. A
> request with a six-second timeout is six seconds of frozen app; three requests
> in a row are eighteen.
>
> Android normally catches this itself and crashes the app with a clear message.
> That protection does not apply here: it lives in Java's sockets, and Python
> goes to the network through its own, bypassing Java. No crash, no warning —
> just a frozen app. The first two plugins that were not ours were written
> exactly this way, and both authors believed everything was fine.
>
> The right shape is below, under "A command that answers from the network".

### A command that answers from the network

```python
def on_start():
    margelet.on_send(command)

def command(text, chat):
    if text.strip() != ".weather":
        return None
    margelet.background(lambda: margelet.send(chat, weather()))
    return False        # the command itself is not sent

def weather():
    ...                 # network and slowness are fine here
```

| | |
|---|---|
| `margelet.background(call)` | do something slow away from the screen |
| `margelet.send(chat, text)` | send a message to a chat |
| `margelet.dont_send()` | the same as returning `False` |

There are three equal ways to cancel a send: return `False`, call
`margelet.dont_send()`, or call `margelet.cancel()` with no argument. Three,
because people reach for `cancel` — and until now it only meant "stop
repeating" and silently cancelled nothing.

### Messages arriving

```python
def on_start():
    margelet.on_message(count)

def count(text, chat, message_id, mine):
    if not mine:
        margelet.log("arrived:", text)
```

Your own sent messages arrive here too — that is what `mine` is for. The return
value changes nothing: the message has already arrived.

### Your own button in a chat

```python
def on_start():
    margelet.button("Count", count)

def count(chat):
    margelet.toast("there are " + str(chat.getMessagesCount()) + " messages here")
```

The line goes last in the chat menu, after all the usual entries: someone
else's code should not push the familiar ones around.

If one plugin's callback throws, the others still get called: each is called
separately and the broken one gets its traceback in the console.

`print()` goes to the console as well — it is intercepted.

## A settings screen of your own

A plugin does not draw its own screens — it says what the screen is made of,
and the app draws it. That is why a plugin's switch looks like every other
switch: same theme, same colour, same tap.

```python
def on_start():
    margelet.settings(
        margelet.header("How to say hello"),
        margelet.switch("hello", "Say hello", default=True,
                        about="Says hi when you open a chat."),
        margelet.text("name", "Name", default="friend"),
        margelet.choice("mood", "Mood", ["cheerful", "calm"]),
        margelet.note("All of this stays on the phone and goes nowhere."),
        margelet.action("Forget everything", forget, danger=True),
    )
    margelet.on_settings(changed)

def changed(key, value):
    margelet.log("now", key, "=", value)

def forget():
    margelet.toast("forgotten")
```

| Row | What it is |
|---|---|
| `margelet.header(text)` | a section title |
| `margelet.note(text)` | an explanation in grey |
| `margelet.switch(key, title, default=False, about=None)` | a switch; read with `margelet.flag(key)` |
| `margelet.text(key, title, default="", about=None)` | a line typed by hand; read with `margelet.get(key)` |
| `margelet.choice(key, title, options, default=None)` | one of several |
| `margelet.action(title, call, danger=False)` | a button that just does something |

`settings()` is called once, at start. Defaults are written straight away —
otherwise the first read would come back empty although nobody changed
anything.

A plugin with settings gets a gear in the list. Tapping the row to the left of
it opens the settings; tapping the switch on the right turns the plugin itself
on and off.

The declaration is kept together with the plugin's memory rather than in RAM,
so the settings screen opens for a disabled plugin too: you may want to fix a
setting before turning it on.

## Three traps at the Android boundary

None of this is theory: all three came up while writing the games plugin, and
each cost a separate round.

**A colour is a signed number.** In Java a colour is a signed 32-bit integer,
and everything opaque is negative in it. Python treats `0xFFFFFFFF` as just a
large number, and the bridge into Java refuses to convert it:

```
OverflowError: value too large to convert to int32_t
```

So a colour has to be folded:

```python
def argb(value):
    return value - 0x100000000 if value > 0x7FFFFFFF else value

view.setTextColor(argb(0xFFFFFFFF))
```

This throws on the very first colour, so nothing appears at all — and it looks
exactly like "the plugin did not start".

**A Python list is not a Java array.** If a method wants an array, a list will
not do:

```python
from java import jarray
from java.lang import CharSequence

titles = jarray(CharSequence)(["First", "Second"])
```

**Touch the screen only from the main thread.** The `on_send` event is already
on it; a button on the settings screen is not. From there any window has to be
opened through `margelet.ui`, or nothing opens.

Errors like these are visible only in the console: Settings → Margelet →
Plugins → Console. It has both the line and the reason.

## Dynamic Java Method Hooks (MargeletHook & Xposed API)

In addition to standard plugin events, a powerful method hook engine is available to intercept and modify any internal Telegram client methods.

### Intercepting Methods with Decorators

```python
# Call before method execution (inspect arguments or cancel execution)
@margelet.before_method("org.telegram.messenger.SendMessagesHelper", "sendMessage")
def before_send(param):
    margelet.log("Sending message, arguments count:", len(param.args))

# Call after method execution (inspect and alter return values)
@margelet.after_method("org.telegram.messenger.MessagesController", "getUser")
def after_get_user(param):
    user = param.getResult()
    margelet.log("User received:", user)
```

| Method | Description |
|---|---|
| `@margelet.before_method(class_name, method_name)` | Intercept method before execution |
| `@margelet.after_method(class_name, method_name)` | Intercept method after execution |
| `param.args` | Array of method arguments |
| `param.thisObject` | Instance object (`this`) on which the method was called |
| `param.setResult(val)` | Override return value (calling this in `before` prevents the original method from running) |
| `param.getResult()` | Read the returned value of the original method |

### Xposed API Compatibility

For Xposed module developers, standard `XposedHelpers` are supported:

```python
from de.robv.android.xposed import XposedHelpers

# Read and write private fields
current_account = XposedHelpers.getIntField(obj, "currentAccount")
XposedHelpers.setBooleanField(obj, "deleted", True)

# Invoke private methods directly
result = XposedHelpers.callMethod(obj, "privateMethodName", arg1, arg2)
```

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

The install dialog has two buttons. "Install" installs the plugin disabled.
"Install and run" installs it, turns it on and restarts Margelet right away, so
the plugin starts working without waiting for the app to be closed by hand.

If the plugin declares a `min_version` above yours it will not install, and you
are told which version it needs — instead of installing and then breaking.

The install dialog has a "View the archive" button: it opens the `.marp` with
whatever on the phone handles archives, so the code can be read before
installing rather than after. The forum rule demands that the code be open —
so there has to be something to open it with.

Hold the row for the plugin's card and for deleting.

Turning a plugin off means "do not start it again". There is no way to stop
Python code that is already running — it lives until the app is restarted. That
is what the "Restart Margelet" button on the plugins screen is for: switch it
off, tap, and the plugin is gone.

## The example

[margelet_example.marp](margelet_example.marp) — the same one that ships with
the app. Two lines inside; they are a fine place to start.

## Questions

[Forum](https://t.me/margeletforum) · [Channel](https://t.me/margeletter)
