# Getting help with FastBeat

GitHub links this file from the issue chooser and the repository sidebar. Its only job is to get you
to the right place in one hop, so it is deliberately short.

**FastBeat is maintained by one person** ([@ajay99511](https://github.com/ajay99511)) alongside other
work. There is no support SLA. A well-formed report with a device, an Android version and reproduction
steps gets looked at; a one-line "doesn't work" usually cannot be acted on at all.

## Pick the right channel

| What you have | Where it goes |
|---|---|
| **A security vulnerability** | **Never a public issue.** [Report a private advisory](https://github.com/ajay99511/FastBeat/security/advisories/new) — see [SECURITY.md](SECURITY.md) |
| A crash or wrong behaviour | [Open a bug report](https://github.com/ajay99511/FastBeat/issues/new?template=bug_report.yml) |
| An idea or a missing capability | [Open a feature request](https://github.com/ajay99511/FastBeat/issues/new?template=feature_request.yml) — check the [roadmap](README.md#-roadmap) first |
| A question, or you are not sure it is a bug | [Discussions](https://github.com/ajay99511/FastBeat/discussions) |
| Gradle, SDK or emulator trouble building it | [docs/GETTING_STARTED.md](docs/GETTING_STARTED.md), then Discussions |
| You want to send a patch | [CONTRIBUTING.md](CONTRIBUTING.md) |

## Before you ask

Three things resolve most reports without anyone having to reply:

1. **Be on the latest release.** Older versions are not patched — [SECURITY.md](SECURITY.md) says so
   explicitly, and a fixed bug reported again costs everyone time.
2. **Search existing issues**, open *and* closed. A closed issue often carries the answer or the
   reason it was declined.
3. **For playback bugs, note the file.** Container, codec and duration. These bugs are almost always
   specific to one file's format, so without it the report usually ends as "cannot reproduce".

## What to expect

- Security reports: acknowledged within **3 business days**, assessed within **7** — the only
  commitments in this project, and they are in [SECURITY.md](SECURITY.md).
- Everything else: best effort, no promised timeline.
- A feature can be declined for being a poor fit rather than a bad idea. Two properties are not
  negotiable — FastBeat works **fully offline** and **sends nothing off the device** — so a request
  that costs either one has to make a strong case. The feature-request form asks about this up front
  so you are not surprised by it later.
