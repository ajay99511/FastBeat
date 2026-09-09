package com.local.offlinemediaplayer.ui.common

import androidx.annotation.DrawableRes
import com.local.offlinemediaplayer.R

/**
 * Placeholder shown by `AsyncImage` when a track, album or artist has no embedded album art.
 *
 * WHY THIS IS A RESOURCE ID AND NOT A URI STRING. Nine screens each inlined the literal
 * `"android.resource://com.local.offlinemediaplayer/drawable/ic_launcher_foreground"`. A resource
 * named only inside a string literal is invisible to every tool that decides what to keep: Android
 * Lint reported the drawable as unused, and R8's resource shrinker — on for `release` only —
 * removed it from the APK. Debug showed the placeholder, release silently showed an empty tile, on
 * every screen, for every track without embedded art. Nothing caught it because no test covers
 * artwork and, until now, nothing built the release variant.
 *
 * `AsyncImage(model = ...)` takes `Any?` and resolves a `@DrawableRes Int` directly, so this
 * constant keeps the reference visible to lint and to the shrinker. **Do not turn it back into a
 * URI string, and do not inline it at call sites** — both reintroduce the same invisible edge.
 *
 * WHY THE APP ICON FOREGROUND. This used to point at `ic_launcher_foreground` — the Android
 * Studio template droid — which was never a deliberate choice, only the asset that happened to be
 * lying around when the URI string was written. Reusing the launcher foreground costs no new
 * asset, is already in the APK and already reachable through the manifest, and makes an art-less
 * track read as FastBeat rather than as Android. It is square and centred, so it survives
 * `ContentScale.Crop` into a square tile without cropping anything meaningful.
 */
@get:DrawableRes
val fallbackArtwork: Int get() = R.mipmap.app_logo_foreground
