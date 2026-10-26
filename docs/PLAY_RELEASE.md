# Releasing Dark Mahjong on Google Play

A step-by-step checklist for the first release, with the answer to every Play Console form.
Prepared 26 October 2026 for version **1.0.0** (version code 4). Play's rules change from time
to time, so trust what the Console shows over this page where they differ.

## What's ready in the repo

| Item | Where |
|---|---|
| Signed App Bundle | `dist/DarkMahjong-1.0.0.aab` (not in git; rebuild with `tools/build-play-bundle.sh`) |
| Title, short and full description | `fastlane/metadata/android/en-US/` |
| Release notes | `fastlane/metadata/android/en-US/changelogs/4.txt` |
| App icon, 512 × 512, 32-bit PNG | `fastlane/metadata/android/en-US/images/icon.png` |
| Feature graphic, 1024 × 500 | `fastlane/metadata/android/en-US/images/featureGraphic.png` |
| Phone screenshots, 1200 × 2400 | `fastlane/metadata/android/en-US/images/phoneScreenshots/` |
| Privacy policy | `docs/PRIVACY.md`, published at https://github.com/formless-form/dark-mahjong/blob/dark-mahjong/docs/PRIVACY.md |

## The upload key (keep it safe)

- Keystore: `~/.config/dark-mahjong/upload-keystore.p12` on the server, alias `upload`, RSA 4096, valid until 2054, owner "CN=Grevillea Labs".
- Password: `DARK_MAHJONG_UPLOAD_KEY_PASS` in the server secrets store (`python3 ~/server_secrets.py get DARK_MAHJONG_UPLOAD_KEY_PASS`).
- **Neither is covered by any backup.** Keep a copy of the keystore file and its password somewhere safe off the server (a password manager is ideal).
- Certificate SHA-256 fingerprint: `4D:32:26:B5:29:27:21:6A:DC:20:32:66:F8:12:2A:2D:27:06:02:8C:37:04:70:39:38:69:5E:05:AE:B7:78:AF`
- With **Play App Signing** (the default for new apps), Google keeps the key that signs what players install. This upload key only proves an upload came from you; if it's ever lost, Play support can reset it.

## 1. Developer account (you)

1. Sign up at https://play.google.com/console with your Google account; pay the one-off registration fee.
2. **Developer name: Grevillea Labs** (this is what players see).
3. Account type: **personal** (an organisation account needs a registered business and a D-U-N-S number).
4. Verify your identity as asked (ID, and a contact phone and email).
5. New personal accounts must run a **closed test with at least 12 testers for 14 days in a row** before they can apply for production. Line up testers early: each needs a Google account and must opt in through the test link.

## 2. Create the app

- App name: **Dark Mahjong**
- Default language: **English (United States) – en-US** (matches the listing files)
- App or game: **Game**
- Free or paid: **Free** (this can't be changed to paid later)
- Accept the declarations (developer policies, US export laws).

## 3. App content (Policy → App content)

| Form | Answer |
|---|---|
| Privacy policy | https://github.com/formless-form/dark-mahjong/blob/dark-mahjong/docs/PRIVACY.md |
| App access | All functionality is available without special access |
| Ads | **No**, the app doesn't contain ads |
| Content rating | Category **Game**. Answer **No** to everything: violence, fear, sexuality, language, controlled substances, crude humour, gambling (tile matching isn't gambling: no betting, no prizes), user interaction or chat, sharing location, digital purchases. Expect **Everyone / PEGI 3** |
| Target audience | **13 and over** (13–15, 16–17, 18+). Including under-13s brings in Google's Families policy, which this release doesn't need |
| News app | No |
| Data safety | Collects or shares user data: **No**. (No internet permission, no analytics or crash reporting, no accounts; progress stays on the device) |
| Advertising ID | **No**, the app doesn't use it |
| Government app | No |
| Financial features | None |
| Health | None |

## 4. Store listing (Grow → Store presence → Main store listing)

- App name, short description, full description: from `fastlane/metadata/android/en-US/`.
- App icon, feature graphic, phone screenshots: from `fastlane/metadata/android/en-US/images/`.
- Tablet screenshots: optional; leave empty (the app is for phones held upright).
- Category: **Board**, under Games (Puzzle would also fit). Tags: Mahjong, Solitaire, Board, Relaxing.
- Contact details: email **grevillealabs@gmail.com** (shown publicly; also in the privacy policy), and optionally the website https://github.com/formless-form/dark-mahjong

## 5. Closed test, then production

1. Testing → Closed testing → create a track (e.g. "Friends").
2. Add testers (an email list, or a Google Group) and save.
3. Create a release: upload `dist/DarkMahjong-1.0.0.aab`, accept Play App Signing, release name **1.0.0**, release notes from `changelogs/4.txt`.
4. Countries: all, or start with Australia.
5. Review and roll out; share the opt-in link with testers. Once 12+ have opted in for 14 days, apply for production access, then promote the release.

## Later updates

1. Bump `versionCode` (by one) and `versionName` in `app/build.gradle.kts`.
2. Run `tools/build-play-bundle.sh`.
3. Upload the new `dist/DarkMahjong-<version>.aab` to the track, with release notes in `changelogs/<versionCode>.txt`.

## Licences, for reference

Code GPL v3 with the upstream App Store Exception (source public on GitHub). Fonts OFL. Tile art, music and sounds CC0 (see `NOTICE.md`). Layouts from Mah by ffalt, MIT.
