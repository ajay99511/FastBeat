# Privacy Policy

**FastBeat (Offline Media Player)** — application ID `com.local.offlinemediaplayer`

**Effective:** 2026-10-08 · **Last updated:** 2026-10-08
**Contact:** Ajay Elika — ajayelika99511@gmail.com

> **Why this file exists in the repository rather than only on a website.** Google Play requires a
> privacy policy URL for every app, including one that collects nothing. Keeping the policy next to
> the code means it is versioned with the behaviour it describes, and a change to it shows up in a
> diff and needs a review like any other change. Link to its rendered URL from the Play listing.

---

## The short version

**FastBeat collects nothing, sends nothing, and has no way to.** There are no accounts, no analytics,
no crash reporting, no advertising, and no third-party SDK that phones home. Everything the app
knows about you — your library, playlists, play counts, streaks, resume positions, theme — is stored
on your device and goes no further.

This is not a promise resting on good intentions. **The build strips the `INTERNET` permission from
the shipped app**, with `tools:node="remove"` in
[`AndroidManifest.xml`](app/src/main/AndroidManifest.xml), so the manifest merger removes it even if
some future dependency declares it. An Android app without `INTERNET` **cannot open a network
connection at all**. You can verify this yourself for any build:

```bash
aapt dump permissions FastBeat-release.apk | grep INTERNET   # expect: no output
```

That makes the claim checkable from the APK alone, which is the only form of a privacy claim worth
anything.

---

## What the app accesses on your device

The app requests these permissions, and nothing it reads through them leaves the device.

| Permission | Why | What it does **not** do |
|---|---|---|
| `READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`, `READ_MEDIA_IMAGES` | Find and play the music, videos and photos already on your device. | Upload, copy off-device, or scan anything other than media. |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Honour Android 14+ "Select photos and videos" so a partial grant survives past one session. | Widen a partial grant. If you select five photos, the app sees five photos. |
| `READ_EXTERNAL_STORAGE` (Android 12L and below only) | The pre-Android-13 equivalent of the above. Capped with `maxSdkVersion="32"`. | Apply on modern Android at all. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playback running when the app is in the background. | Run when you are not playing anything. |
| `WAKE_LOCK` | Stop the device sleeping mid-playback. | Hold the device awake outside playback. |
| `POST_NOTIFICATIONS` | Show the playback notification and its controls. | Send promotional or any other notification. |
| `ACCESS_NETWORK_STATE` *(arrives transitively from Coil)* | Nothing, in practice. | Enable network access — without `INTERNET` it can observe connectivity and nothing else. |

You can revoke media access at any time in **Settings → Apps → FastBeat → Permissions**. The app will
show an empty library rather than misbehave.

---

## What the app stores, and where

All of it lives in the app's private storage on your device — a local **Room** database and
**DataStore** preference files, readable by no other app on a non-rooted device:

- Your media library index: titles, artists, albums, durations, file paths, thumbnails.
- Playback history, play counts, listening time, daily streaks and the statistics on the "Me" screen.
- Resume positions ("continue watching") and bookmarks.
- Playlists and queue state.
- Preferences: theme, sort orders, equalizer settings.

**Uninstalling the app deletes all of it.** Your actual media files are untouched by an uninstall —
they are yours and live in your device's own storage, not inside the app.

### Deleting media from inside FastBeat

On Android 11+ a delete from the app **moves the file to the system trash**, where Android keeps it
for about 30 days and you can restore it from your gallery or files app. On older versions the
delete is permanent and immediate. The app tells you which one happened.

---

## The one thing that is not under the app's control

`android:allowBackup="true"` is set, which means **Android's own Auto Backup may copy the app's data
(your statistics, playlists and preferences) to your personal Google account** as part of the system
device backup. This is Android doing it, not FastBeat: the data goes to *your* Google Drive quota
under *your* Google account, is covered by Google's privacy terms, and is never visible to the
developer of this app or to anyone else.

Your media files themselves are not included in this — only the app's own small database.

To switch it off entirely, disable **Settings → Google → Backup** on your device, or turn off backup
for this app where your device offers per-app control.

*This is disclosed rather than quietly omitted because it is the single way any FastBeat-related data
can end up off your device, and a privacy policy that said "nothing ever leaves" without mentioning
it would be false.*

---

## Children

FastBeat is a general-audience media player. It collects no data from anyone, so it collects none
from children. It contains no ads, no in-app purchases, no social features and no external links
that could lead a user somewhere else.

---

## Third parties

There are none. No analytics provider, no ad network, no crash reporter, no CDN, no account system.
The libraries the app is built on — Media3, Room, Hilt, Coil, Compose — run entirely on-device here
and are listed in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) if you want to check.

Because nothing is collected, there is nothing to sell, share, disclose on request, or delete at your
request — the GDPR and CCPA rights to access, portability and erasure have no data to apply to. Your
copy of the data is the only copy, and uninstalling is the deletion mechanism.

---

## Changes to this policy

Any change is a commit to this file, visible in the repository's history alongside the code change
that motivated it. If a future version ever did acquire network access, that would require removing
the manifest tripwire described above — and
[`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) carries a note saying that this file, the
README, SECURITY.md and the Play listing all have to change with it.

---

## Reporting a privacy or security problem

Privacy issues that are also security issues (data exposed to other apps, a leaked path, an exported
component) should go through [SECURITY.md](SECURITY.md) — **privately**, never in a public issue.
Anything else: ajayelika99511@gmail.com.
