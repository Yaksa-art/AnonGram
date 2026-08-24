# Badges

A badge is the small mark next to a name in the chat list and in the profile.
It is decoration, not proof of anything: anyone who builds their own fork can
put their own people in it. That was true when the list lived inside the app,
and moving it to this repository does not change it.

## The file

[badges.json](../badges.json) in the `main` branch. The app re-reads it on every
start, so adding a line here is enough — no new build, no update.

```json
[
  {
    "peer": 7826361017,
    "title": "Margelet creator",
    "title_ru": "Создатель Margelet",
    "about": "Made this fork.",
    "about_ru": "Сделал этот форк.",
    "color": "8DD1B0",
    "url": "https://t.me/narezanyinf"
  }
]
```

| | |
|---|---|
| `peer` | who gets it. A person is their own number; a channel or group is the same number with a minus |
| `title` | the badge name, shown next to the name and as the window title |
| `about` | one line inside the window |
| `color` | the field colour, hex — `8DD1B0`, `#8DD1B0` and `FF8DD1B0` all work |
| `url` | where the window's button leads. Leave it out and there is no button |

Add `_ru` or `_zh` to `title` and `about` for translations. Without them the
plain one is used, so a badge with only `title` works everywhere.

One person can have several badges. The one next to the name is the first they
match in the file, so order is seniority. The profile shows all of them.

## Colours

There is one picture — the fork's plane — and the field under it is painted in
whatever `color` says. That is why a new badge needs no new image: write
`E09A9A` and the field is pink.

## When it does not arrive

The list is cached. With no network the last downloaded one is shown, and until
the file has ever been downloaded the app falls back to the list built into it.
A fresh install with no internet therefore looks exactly as it did before.

A line that cannot be parsed is skipped rather than breaking the rest, and a
file that cannot be parsed at all leaves the previous list alone: one typo
should not take everyone's badges away.
