# Cutting a release

The client checks whether a newer version exists and shows a bar at the bottom
of the chat list. The bar downloads the apk from GitHub and hands it to the
system to install.

The order matters.

1. Raise the number in the client: `MargeletConfig.APP_VERSION`.
2. Build the apk.
3. Put it on the `apk` branch — both under its own number and as `margelet.apk`.
4. Only now raise the number in [version.json](../version.json).

`version.json` goes last because it is the announcement: the moment it carries a
higher number, everyone's bar appears. Announce it before the apk is in place
and people will tap it and get an error.

## version.json

```json
{
  "version": "0.2",
  "apk": "https://github.com/narezany/margelet/raw/apk/margelet.apk",
  "about": "What changed.",
  "about_ru": "Что изменилось."
}
```

| | |
|---|---|
| `version` | the latest version. Compared as numbers between dots, so `0.10` is above `0.9` |
| `apk` | where to download it from. The unnumbered link never changes, so it is the one to keep here |
| `about` | one line of what changed. `_ru` and `_zh` are translations |

## What it does not do

Nothing is downloaded or installed behind anyone's back: the download starts on
a tap, and the system installs the apk after asking. An app cannot quietly
replace itself, and that is as it should be.

A partial file does not count as ready. If the network drops or the download is
cancelled, the piece is deleted and the bar offers to download again rather than
to install something that is not there.

Forgetting step one is the expensive mistake: a build with a stale
`APP_VERSION` will offer to update itself to itself forever.
