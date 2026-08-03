# Security Policy

## Supported Versions

FastBeat (OfflineMediaPlayer) is an actively developed Android app. Security
fixes are applied to the latest release; older versions are not patched.

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

Please make sure you are on the newest release before reporting an issue.

## Reporting a Vulnerability

**Please do not open a public GitHub issue for security problems.**

Report vulnerabilities privately through GitHub Security Advisories:

1. Go to the **Security** tab of this repository.
2. Click **Report a vulnerability** (GitHub Private Vulnerability Reporting).
3. Fill in the advisory form with the details below.

> Maintainer note: Private Vulnerability Reporting must be enabled once under
> **Settings → Code security and analysis → Private vulnerability reporting**
> for the "Report a vulnerability" button to appear.

### What to include

- A description of the vulnerability and its impact.
- Steps to reproduce (a minimal proof-of-concept if possible).
- Affected version(s) / commit, device, and Android/API level.
- Any relevant logs, stack traces, or screenshots.

### What to expect

- **Acknowledgement:** within **3 business days**.
- **Initial assessment:** within **7 business days**, including whether the
  report is accepted, needs more information, or is declined (with reasoning).
- **Fix & disclosure:** we aim to ship a fix for accepted, valid reports within
  **90 days**. We will coordinate a disclosure timeline with you and credit you
  in the release notes unless you prefer to remain anonymous.

## Scope

This app is an **offline** local media player. It requests media/storage
permissions and does not transmit your library off-device. Areas of particular
interest for reports:

- Improper handling of untrusted media files or metadata (e.g. crafted tags,
  filenames, or thumbnails leading to crashes, path traversal, or code
  execution).
- Exposure of user media or app data to other apps (exported components,
  `content://`/`file://` URI handling, insecure `FileProvider` config).
- Local privilege or permission bypasses.

Out of scope: issues that require a rooted device or physical access with an
unlocked device, and reports against unsupported versions.
