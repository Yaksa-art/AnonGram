# Cats

A cat is a full-screen picture the fork shows on its own occasions. There used
to be exactly two of them, both baked into the build: adding a third meant
rebuilding the client and handing it to everyone. The list now lives here, the
same way [badges](badges.md) do.

## The file

[cats.json](../cats.json) on the `main` branch. The app re-reads it at most
once every ten minutes — cats are not news, and going to the network on every
single showing means going there for nothing.

```json
[
  {
    "photo": "res/margelet_cat_1.jpg",
    "name": "Murzik",
    "name_ru": "Мурзик",
    "from": "@narezany"
  }
]
```

| | |
|---|---|
| `photo` | path to the picture in this repository, or a full address |
| `name` | the cat's name |
| `from` | whose cat it is. Shown as a caption |

Translations are the same keys with `_ru` or `_zh`, as with badges. No translation — the main
one is used.

## Pictures

A picture arrives separately from the list and by its own route: eight
megabytes is the ceiling, and it is written through a temporary file so that a
broken download cannot leave half a picture to be shown later as a whole one.

While the picture is on its way, a cat baked into the build is shown, not an
empty space.

## If the file did not arrive

The downloaded list is kept next to the app and survives having no network.
Until the file has been downloaded even once, the two built-in cats work — on a
fresh install with no internet everything looks the way it used to.

A line that could not be parsed is skipped, and a file that could not be parsed
at all leaves the previous list where it was.
