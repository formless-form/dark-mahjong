# Notices & Asset Attribution

> **Dark Mahjong** is a modified fork of [PcmciaKai/mahjong_solitaire](https://github.com/PcmciaKai/mahjong_solitaire)
> (Copyright (C) 2026 PcmciaKai), changed from 2026-10-25 by Gareth (github.com/formless-form):
> rebranded, portrait-only, dark theme, stats/timer/support dialog removed, new title screen,
> win celebration, tilted-camera table with shader-lit tiles and tabletops. The licences below cover the original assets and those added by the fork.

This repository uses a multi-license model to clearly separate source code permissions from media asset rights and third-party attributions.

* **Source Code:** Licensed under the [GNU General Public License v3.0 (GPLv3)](LICENSE) with an [App Store Exception](#1-source-code-license).
* **Graphics:** Dedicated to the [Public Domain (CC0 1.0)](#2-graphics-assets).
* **Sound Effects:** Royalty-free sound effects requiring [Attribution](#3-audio-assets).
* **Background Music:** [AI-generated](#background-music) with Suno, dedicated to the Public Domain (CC0 1.0).
* **Fonts:** Licensed under the [SIL Open Font License 1.1](#4-font-assets).

---

## 1. Source Code License

Copyright (C) 2026 PcmciaKai

The source code is licensed under the GNU General Public License, version 3. The full license text is available in the [LICENSE](LICENSE) file.

### App Store Exception (Additional Permission under GPLv3 Section 7)

As a special exception to the GNU General Public License version 3.0, the copyright holders of this software grant permission to distribute compiled binaries of this program (and derivative works built from it) on the Apple App Store or other digital distribution channels that apply Terms of Service or DRM constraints. All source code modifications must still be made available under the terms of the GNU General Public License v3.0.

---

## 2. Graphics Assets

### Mahjong Tiles

* **Author:** samoheen
* **Source:** [samoheen/mahjong-tiles (GitHub)](https://github.com/samoheen/mahjong-tiles)
* **License:** Public Domain / CC0 1.0 Universal
* **Details:** All tile illustrations (PNG/SVG) are free to copy, modify, and distribute for any purpose without restrictions.

---

## 3. Audio Assets

### Mahjong Sound Pack

* **Author / Publisher:** みんなの創作支援サイトＴスタ (T-STUDIO)
* **Source:** [Free Sound Mahjong Sound Pack (itch.io)](https://t-studio-tst.itch.io/free-sound-mahjong-sound-pack)
* **Credit:** みんなの創作支援サイトＴスタ
* **License:** [T-STUDIO terms of use](https://tnosite.com/agreement-4/)
* **Permission:** The author, Tatsuro, confirmed by private message on 3 October 2026 that
  including these files in this open-source repository is fine, provided they
  are not presented as anyone else's work.
* **Note:** These sound files are NOT covered by the GPL. If you fork or
  redistribute this project, please keep this attribution, and contact
  T-STUDIO before reusing the sounds elsewhere.

### Background Music

* **Files:** `zen_mountain_mist.opus`, `zen_mountain_stream.opus`, `ink_cloud_meditation.opus`, `ink_cloud_study.opus`, `jade_rain_study.opus`
* **Origin:** AI-generated with [Suno](https://suno.com) on a paid (Pro) plan, which grants ownership of the output to the subscriber
* **License:** [Public Domain / CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/)
* **Note:** These tracks were not composed or performed by a human. To the extent any copyright or related rights exist in them, PcmciaKai waives them under CC0 1.0. You may copy, modify, and distribute them for any purpose, including commercially, without asking permission.

---

## 4. Font Assets

### Tomorrow

* **Author:** Tony de Marco & Monica Rizzolli (The Tomorrow Project Authors)
* **Source:** [Tomorrow (Google Fonts)](https://fonts.google.com/specimen/Tomorrow) / [MonicaRizzolli/Tomorrow (GitHub)](https://github.com/MonicaRizzolli/Tomorrow)
* **Files:** `tomorrow_bold.ttf` (main menu heading), `tomorrow_medium.ttf` (all other text)
* **License:** SIL Open Font License, Version 1.1 — see [LICENSES/OFL-1.1-Tomorrow.txt](LICENSES/OFL-1.1-Tomorrow.txt)

---

## 5. Assets added by Dark Mahjong

### Title-screen photograph

* **File:** `app/src/main/res/drawable-nodpi/title_mountains.webp` (portrait crop)
* **Original:** [Sanqing Mountain is surrounded by clouds and mists](https://commons.wikimedia.org/wiki/File:Sanqing_Mountain_is_surrounded_by_clouds_and_mists1.jpg) by **Huangdan2060**, via Wikimedia Commons
* **License:** [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) (public domain dedication)
* **Changes:** cropped to a tall portrait frame without distortion, darkened and colour-graded. No attribution is required; the title screen carries a small courtesy credit.

### Thai straw mat texture

* **File:** `app/src/main/res/drawable-nodpi/thai_mat.webp`
* **Original:** [Cambodian mat motifs](https://commons.wikimedia.org/wiki/File:Cambodian_mat_motifs.jpg) by **Willuconquer**, via Wikimedia Commons: a woven sedge mat of the kind made and sold across Cambodia and Thailand
* **License:** [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)
* **Changes:** downscaled, desaturated and warmed towards natural straw. The modified image is likewise released under CC BY-SA 4.0. Settings shows the credit whenever the Thai Straw table is selected.

### Name seal (seal script)

* **File:** `app/src/main/res/font/seal_script_name.ttf` (subset to the four characters 暗夜麻將)
* **Source:** 全字庫說文解字 TrueType font (`ebas927.ttf`) from CNS11643 中文標準交換碼全字庫, **Ministry of Digital Affairs**, Taiwan — https://www.cns11643.gov.tw
* **License:** [Open Government Data License, version 1.0](https://data.gov.tw/license) (the CNS11643 data may alternatively be used under OFL-1.1). Subset by the Dark Mahjong project.

### Title font

* **Cinzel** by Natanael Gama — [Google Fonts](https://fonts.google.com/specimen/Cinzel)
* **File:** `app/src/main/res/font/cinzel.ttf`
* **License:** SIL Open Font License 1.1 — see [LICENSES/OFL-1.1-Cinzel.txt](LICENSES/OFL-1.1-Cinzel.txt)
