# VoCab for iPad (taobao)

A native SwiftUI port of the web page, for taobao's notes (`users/taobao/`). iPad only, iPadOS 17+.

## Install on an iPad from this Mac

1. `cd ipad && xcodegen` (once, and after adding or removing files) → opens as `VoCab.xcodeproj`.
2. `open VoCab.xcodeproj`. In the target's **Signing & Capabilities**, pick your Apple ID team
   (Xcode → Settings → Accounts to add it).
3. Plug in the iPad, trust this Mac, and turn on **Settings → Privacy & Security → Developer Mode** on the iPad.
4. Pick the iPad as the run destination and press ⌘R. The first time, the iPad asks you to trust the
   developer: **Settings → General → VPN & Device Management**.

With a free Apple ID the app stops opening after **7 days**: plug the iPad in and press ⌘R again
(progress is kept). A paid developer account lasts a year and allows TestFlight.

Each build runs `build.py --src users/taobao` and bundles the notes, so it works offline. At launch
it also fetches `https://chinalwb.github.io/vocab/taobao/meta.json` / `data.json` (published by
the Pages workflow) and picks up new entries without a reinstall.

## What's in it

Phase 1: browse (sidebar: stages and groups; search, `#12`), the entry page (four-stage track, notes as
cards or all at once, 🔊), 自测 (① 选词 → ② 打字, fill in only the **bold** part, 考核 types the
whole sentence, 算我对, 打乱, 下一句 / 下一条, 学习中 → 复习 by itself), essays (选词 only,
朗读全文 with the pause from 设置, 中英 / 英 / 中).

Phase 2: the 复习 / 考核 lists (grouped by why each entry is there, a summary card with 开始复习 /
开始考核) and full-screen runs with SM-2 — 先看一遍 → 翻面做题, up to 3 sentences per entry, 复习 → 考核
by itself after 3 right in a row, a 考核 miss back to 复习, 完成 with 撤销移动; 复习这一条 on an entry
due today. 同步 with `progress-taobao.json` (设置 → 同步到 GitHub, taobao's own fine-grained token, kept
in the Keychain): stages, SM-2 cards, 自测 results (never the text), 学习历史; 不再考这句 is read from it.
⭐, 我的笔记 and 阅读 are carried through untouched.

Not yet: screens for 学习历史 / 我的笔记 / 记忆曲线, essay PDF, setting 不再考这句 / ⭐ on the iPad,
hardware-keyboard chip filtering, the "see every notes page first" gate.

Testing in the simulator, as launch arguments: `-open <anchor>` (and `-quiz YES`) opens an entry;
`-pick review` shows a stage's list; `-run review` / `-run test` starts its run.
