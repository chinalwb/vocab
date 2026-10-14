# 会话记录

换电脑接着做之前先看这里:每次长会话改了什么、现在的版本、没做完的事。最新的在最上面。细节规则以 `CLAUDE.md` 为准。

---

## 2026-10-14 ~ 10-15:V2 界面、今天的练习、Android 12 耗电

### 现在的状态

- **App v2.0.1**(versionCode 74),GitHub Release `app-v2.0.1` 已发布,手机上「⋮ → 检查 App 更新」能收到。
- `main` 和 `dev` 内容一致:V2 分支 `ui/claude-code-v2` 已合进 main,main 又合回了 dev;网页的 V2 也已上线(main 触发 Pages 构建)。
- `ui/claude-code-v2` 分支已经合完,可以删掉。
- 设计稿:Claude Design 画布 https://claude.ai/artifact/ACRvmVT47zV7FbSFc8Gn6Q(私有,V1 / V2 两页;另一份对照稿 https://claude.ai/artifact/GNvubRULLWx3fWvYzcc18G)。

### 改了什么(按时间)

1. **深色模式下条目页是黑字**(v1.70):条目页不在 Surface 里,没指定颜色的文字默认黑色。`EntryScreen` 整页提供 `LocalContentColor = onBackground`。
2. 删掉误提交的 vim 交换文件,`.gitignore` 加 `*.swp` / `*.swo`。
3. **设计**:走查了 App 各页,做了 V1(沉浸式做题、滚动收起头部)和 V2(借鉴另一份设计:笔记装进级别色卡片、四格进度轨道、按原因分组的阶段列表)两版画布。用户定了 V2,并改成「你的句子」框在选词 / 打字两种模式下都在题目正下方(加 ⌫ 键)。
4. **App V2 界面**(v2.0):条目页卡片 + ⤢ 全屏、答题纸(`AnswerSheet.kt`)、复习 / 考核做题全屏(隐藏顶栏和 tab 栏,返回手势结束)、复习 / 考核 tab 概况卡 + 分组列表。详见 `CLAUDE.md`「V2 界面」。
5. **今天的练习 · 导出 PDF**(v2.0,只有 App):条目页自测、复习、考核里有标准答案的题,每次揭晓记一对(中文 / 我写的 / 答案),一天一个 Markdown 文件,只在手机上(`filesDir/practice/日期.md`),同一句只留最后一次。⋮ 菜单进去看,导出 PDF 一个文件三部分(答案 / 对照 / 只有中文带横线),可以存到「下载」或分享。详见 `CLAUDE.md`。
6. **Android 12 上浏览页一直重画**(v2.0.1):Haze 在 SDK < 32 时每帧强制重画模糊,浏览页静止时也 60 帧 / 秒、占一个核约 65%,开着屏幕一夜耗电。现在 SDK 32 以下不用真模糊(`GLASS_BLUR`),静止时 0 帧。这个问题从 10-10 加玻璃效果起就有,不是 V2 引入的。
7. **网页同步 V2**(template.html):条目弹层、答题纸、全屏做题、阶段 tab 都和 App 一致;「今天的练习」网页没有。
8. 阅读清单新增 `r-dedicated-branch`、`r-merge-back-into-dev`(用户的两段工作英文,按 C1 改写)。

### 没做 / 已知问题

- **白屏**(10-15 早上):App 回到前台后 Compose 视图尺寸是 0×0,什么都不画;没有崩溃日志,重启 App 就好。可能和第 6 条的整夜重画有关,但没能复现、没确认。再遇到时**先别重启**,用 `adb shell dumpsys activity top` 看 VoCab 那一段的视图树,把状态留下来。
- 复习 / 考核做题页揭晓后没有「再试一次」(复习的 SM-2 只按第一次揭晓打分,要加得先想清楚)。
- 「掌握」tab 没按 V2 改,还是原来的卡片列表。
- V1 设计里的「滚动自动收起头部」没做(有 ⤢ 就重复了)。
- 「今天的练习」只能看今天;想看 / 导出以前某天,得加日期切换。
- 测试留下的数据:deserted 第 2 句有一条故意写错的尝试(也进了 10-15 的练习记录);手机「下载」里有一份 `vocab-practice-2026-10-15.pdf`。

### 在别的电脑上

- `git pull` 拿 dev(10-13 改写过历史,之前 clone 的副本要重新 clone,见 `CLAUDE.md`)。
- 改 App:递增 `android/app/build.gradle.kts` 的 versionCode / versionName → 推 dev → 用户把 dev 合进 main → Actions 自动签名发 Release。本地 `adb install` 测试才需要签名 key(见 `CLAUDE.md`「在别的电脑上开发 / 发布 App」)。
- 改网页:只改 `template.html`,`python3 build.py` 生成 `index.html` 本地看效果(`python3 -m http.server` 打开)。
