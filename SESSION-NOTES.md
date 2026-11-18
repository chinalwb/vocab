# 会话记录

换电脑接着做之前先看这里:每次长会话改了什么、现在的版本、没做完的事。最新的在最上面。细节规则以 `CLAUDE.md` 为准。

---
## 2026-11-18:专项练习的记录随学习进度同步,连答案一起(网页 + iPad 0.3)

- **为什么**:taobao 前一天在自己的浏览器里做了专项练习,用户在自己这边打开看到「0 / 28 已作答题目」。不是丢了:专项记录原来只存在做题的那台设备(网页 localStorage `vk.taobao.specialPractice`,iPad `special-practice.json`),不同步。用户选了**连答案原文一起传**(知道仓库是公开的),方便在别的设备上看和批改。
- **怎么做**:整份记录放进 `progress-taobao.json` 的 `specialPractice`。答题记录按 id 并集,每次练习按 `t` 新者胜,`current` 取两边里 `t` 更新的那个;只看过的不上传;继续一个没做完的练习会刷新 `t`,只看做完的结果不刷新。网页 practice.js `specialOut()` / `specialMerge()`,template.html 的 `normProgress()` / `progressOut()` / `applyRemote()`(只在有练习集的笔记本里,`SP_SYNC`);iPad `SpecialPracticeRecord.merge(_:)` / `synced` / `resume(_:)`,Sync.swift 的 `merge` / `snapshot`。线上统一用网页的 `status`,iPad 早期的 `completed` 也能读。iPad 版本 0.2 (2) → 0.3 (3)。
- **验证**:Node 跑网页的 practice.js、macOS 编译 iPad 的 SpecialPractice.swift,模拟两边轮流同步:一轮之后两边都不再提交;并集、新者胜、`current`、原地更新都对;GitHub 上的坏数据两边都跳过(这一步发现 iPad 对不是对象的值会抛 ObjC 异常闪退,`try?` 接不住,加了 `isValidJSONObject`)。Playwright 打开构建出的页面、模拟 GitHub 接口:用 taobao 真实的 `progress-taobao.json` 起步,只多出 `specialPractice`,别的字段不变;另一个浏览器拉下来显示 2 / 28、草稿也在,不提交回去;我的主页不写这个字段。iPad 整个 App 对 iOS 模拟器 SDK 类型检查通过,新加的两个 XCTest 在 macOS 上跑过;**没在 iPad / 模拟器上跑整个 App**(这台 Mac 没装 xcodegen)。`tests/special_practice.cjs` 加了同步一段,`python3 -m unittest` 通过。
- **还要做**:合进 main 后网页生效;taobao 下次在做题的那个浏览器打开页面(连着同步),昨天的记录就会传上去。**taobao 的 iPad 要装 0.3**:0.2 同步时会删掉这个字段(数据不丢,网页会再传,但会来回提交、我这边时有时无)。

---
## 2026-11-18:⤢ 改成真正的全屏(网页)

- 用户要像 Google Slides 放映那样的全屏:原来 ⤢ 只盖住列表,顶栏和分类栏还在。现在条目铺满整个窗口,并请求浏览器全屏(`setFull()`);Esc / ⤡ / 关掉条目都会退出,J / K、下一个保持全屏。专项练习页原来没有 ⤢,加上了(和 ✕ 同样式,taobao 的皮肤下也一致)。
- **顺手修了一个旧问题**:卡片背面的选词词块在看笔记时虽然看不见,却一直在接键盘,吃掉了 J / K 和 Esc(停靠条目时按 J 不动、Esc 关不掉)。原因是 `checkVisibility()` 不看 `visibility:hidden`。改动之前的页面一样有这个问题。
- 验证:Playwright(装在临时目录,用本机 Chrome)真点击:主页 ⤢ 后滚动区域是 0,0,1440,900、浏览器进入全屏;J 换到下一条仍是全屏;浏览器退出全屏后 ⤢ 跟着退出;Esc 先退出全屏,再按关掉条目;✕ 关掉并退出;专项练习页 ⤢ / ⤡;手机宽度。`tests/special_practice.cjs` 和 `python3 -m unittest` 都通过,无 JS 错误。

---
## 2026-11-18:桌面版左栏换分类时,右边的条目跟着换(网页)

- 用户在 taobao 的页面上发现:右边停着一篇文章时,点左栏「专项练习」,右边还是那篇文章。其实点任何分类都一样,不是专项练习独有的:`render()` 只在右边**什么都没停**时才自动打开列表第一条。
- 改成:左栏点分类 / 学习阶段(`setView()` 置 `autoOpen`)后,停靠的条目不在新列表里,就换成新列表的第一条;还在就不动;新列表空了就关掉。搜索框和「筛选」面板不触发(边搜边看时不打断)。
- 专项练习自动打开时不再抢焦点(`showSpecialPractice()` 接收 `o.noFocus`,原来 ✕ 上会出现焦点框)。
- 验证:无头 Chrome 打开 taobao 的页面,停一篇文章后依次点专项练习 / 句子 / 文章 / 全部,右边分别换成对应分类的第一条,点「全部」时保持不变;三个页面的脚本语法检查通过,`python3 -m unittest` 8 项通过。`tests/special_practice.cjs`(要装 Playwright)没跑。

---
## 2026-11-18:IELTS 原文的 ▶ 换成 🔊 · App 2.35

- IELTS 条目「原文」页的原文和「表达卡片」里「原文中的用法」那句,原来是蓝色实心 ▶、贴在最右边;用户说它和 🔊 一样都是朗读,换成和别处一样的 🔊,紧跟在句子后面。网页去掉了 `originalPlayBtn()` 和 `.original-play` / `.ielts-original` 样式,改用 `spkBtn()`;App 去掉 `OriginalPlayButton`,改用 `SpeakButton`。
- 验证:无头 Chrome 打开 oldest children 那条,两个页签都是 🔊,页面上没有 ▶;`python3 -m unittest` 8 项通过;App `compileDebugKotlin` 通过,没在真机上看。

---
## 2026-11-18:待补充(随手新建)· App 2.34

- **为什么**:不在电脑前遇到生词时没法马上问 Claude。现在网页顶栏「＋ 新建」、App 浏览页顶栏 ＋,填「想学的词或短语」(必填)和「补充」(可选,≤600 字),列表最上面出现一张虚线框的「待补充」卡片,立即同步到 `progress.json` 的 `requests`。
- **回到电脑**:说「处理待补充」→ `python3 build.py --pending` 列出还没回答的 → 一条写一个条目,带 `- 请求:<id>` → 推到 dev。合进 main 后,网页和 App 看到条目带着这个 id,待补充卡片就消失。规则见 `CLAUDE.md` 的「待补充」一节。
- 用户定的:可以公开(原文原样上传);提交就算说了「存」;一次一个条目;只加在我的笔记(学生页没有,他们的同步文件也不写这个字段)。
- **验证**:网页在无头 Chrome 里截图看过(手机宽度、桌面三栏、新建、提交后、点开编辑);合并规则测过(新者胜、旧的被忽略、删除同步、已回答的不显示);build.py 加 `- 请求:` 后别的条目 hash 不变(meta.json 的 hash 和改之前一样)。App `compileDebugKotlin` 通过。**App 没在真机上跑过**:连着的 S23 当时在给别的项目用,没装;装 2.34 后要试一遍新建 / 编辑 / 删除 / 同步。
- **过渡期**:旧版 App(2.33 及以前)和合并前的网页不认识 `requests`,同步时会把它去掉;新版的设备本地还留着,下次同步会再补上去。所以 dev 合进 main 后尽快把手机升到 2.34。

---
## 2026-11-15:iPad 原生专项练习

- App 新增「专项练习」分类与独立 SwiftUI 界面，支持分项／混合、填空／改错／改写、提示、讲解与答案朗读、草稿与提交结果恢复、历史会话、错题和提示题重练。四条核心笔记可以在 App 内往返。
- 专项记录单独保存在 special-practice.json，不并入普通自测、阶段、SM-2 或 GitHub 同步；输入立即原子保存。完成不等于掌握。
- data.json 原有 entries 保持普通笔记，新增可选 practices 数组，旧 App 可忽略，新 iPad 合并显示。meta.hash 包括题库更新。App 不再让旧的在线快照覆盖更新的内置题库。
- 版本 **0.2（2）**。增加 VoCabTests 与 VoCabUITests；5 项原生测试及完整界面测试通过，覆盖恢复、提示和重练；Python 8 项回归通过，模拟器与真机架构构建通过。真机当时未连接，尚未安装。
- 离线验收使用独立模拟器与 -offline YES，数据更新和进度同步都跳过网络请求；安装到真机仍沿用 README 的签名流程，不把团队信息或证书写进仓库。

---
## 2026-11-15:taobao 的专项练习分类

- 新分类「专项练习」采用独立结构化题库 practices.json 与独立网页界面，不套用普通词条自测；第一组 have to 的四个薄弱点各 7 题，共 28 题，另有 4 条核心句子笔记（#28–31），互相链接。
- 支持分项／混合、填空／改错／改写、主动查看提示、逐题讲解、多种允许答案、草稿与已提交结果恢复、结果回顾、未完成练习恢复、错题和提示题重练。首次独立答对与提示后答对分别统计，不自动判断掌握。
- 当前只在网页提供专项界面；记录保存在学生浏览器前缀 vk.taobao.specialPractice，不上传、不同步，不混进普通阶段、自测或 SM-2。原生 data.json 接口保持不变；iPad 上练习可用网页。
- build.py 内联 practice.js / practice.css，工作流监听新资源；内容格式和验收方法已记录到说明与 tests/README.md。修改提交至 dev，合 main 由用户决定。

---
## 2026-11-14:IELTS 阅读页与表达卡片重构

- 用户同意把 IELTS 阅读与表达积累串起来：页签为「原文 / 读懂这句 / 表达卡片 / 关联笔记 / 我的笔记」，概要直接放在原文上方。build.py 统一 IELTS blocks，旧长文的讲解归入「读懂这句」，原文不改。
- 来源元信息 `- 标签:15-1 · Listening · Part 2` 从正文移除，网页展示为三个可搜索的标签按钮；Android 保持胶囊标签。
- Key words 表格提供逐张表达卡：学习分「重点会用 / 理解并积累 / 按需查看」，含本句含义、原文、新语境与中文提示、易混和已有词条关联。原文表达可点击打开卡片；旧词汇列表兼容简单卡片。
- 网页和 Android 都有筛选、选择想学的表达、可选回忆与揭晓。回忆不记成绩、不改变 IELTS 阶段；选择仅保存在本浏览器／设备，不同步。要进入正式复习，优先打开关联的已有词条，否则复制保存请求交给助手查重整理，不由页面直接创建笔记。
- 当前 oldest children 条目已配好五张卡。已有 IELTS 长文自动兼容四个内容页签，原讲解保留。
- 验证：3 个 Python 回归测试通过；根目录及两个学生网页构建通过；Chrome 桌面／390px 手机、浅深色、原文跳转、回忆隐藏与揭晓、选择重载保留、标签搜索、旧文与普通词条检查通过，无 JS 错误；Android `:app:compileDebugKotlin` 通过，尚未做真机运行检查。
- Android 版本 **2.32**（versionCode **106**）。本次提交到 dev，不自动发布 main。

---
## 2026-11-14:IELTS 分类(只读条目)

- 用户要一个自己的 IELTS 分类,用来存备考时遇到的**词 / 短语 / 句子 / 段落**,只阅读和记账,**不自测**。
- 做成固定分类(没走根目录 `site.json` 的 groups:那套只影响网页,还会把左栏其他分类挤掉)。改动:build.py 的 `KIND` / `GROUP_ORDER`;template.html 的 `--il-1..3` 钴蓝配色、`LV` / `LABEL` / `TYPES`、左栏分类、`READING()` 和各处的只读分支;Android `Theme.kt` / `BrowseScreen.kt` / `EntryScreen.kt`(藏轨道和自测按钮)/ `PracticeScreen.kt`;iPad `Model.swift` 的 `groupNames`(iPad 只读 taobao 的笔记,没有改阅读界面)。
- 阅读条目在网页和 App 里:没有四格进度轨道,没有「自测这条」,没有自测面(网页背面仍在 DOM 里,但没有任何入口翻过去),不进练习 / 复习 / 考核;阶段上仍算「学习中」,照常出现在浏览、搜索、收藏、学习历史里。格式写进了 CLAUDE.md 的「IELTS 阅读条目」。
- **来源标签 + 四个页签**(用户 2026-11-14 追加):每条带 `- 标签:15-1 · Listening · Part 2`(build.py 拆成 `tags`),网页卡片 / 条目页显示成小胶囊,搜索能搜到;Android `Entry.tags` 同样显示在条目页。详情页固定四个页签 Original / Explanation / Key words / References,靠正文里四个加粗小标题实现,不多加别的标题。
- 验证:`python3 build.py` 正常(166 条);临时测试笔记本里 3 条 IELTS 条目,标签在 `data.json` 里是 `["15-1","Listening","Part 1"]` 这样,Chrome 无 JS 错误、卡片和条目页都显示胶囊、无轨道 / 无自测按钮;Android `:app:compileDebugKotlin` 通过。
- 版本:Android **2.31**(versionCode 105)。**改动还没提交、没推 dev。**

---
## 2026-11-02:iPad App(真机 + 第三阶段)

- **装到了真机**(taobao 的 iPad Air 4,用户的免费 Personal Team `H5HH6LS79A`;team 只在命令行传,不写进 project.yml)。命令:`xcodebuild … -destination 'id=00008101-001D55202E01001E' -allowProvisioningUpdates DEVELOPMENT_TEAM=H5HH6LS79A build`,再 `xcrun devicectl device install app --device 5492416E-66AB-4CA1-8246-ACFCBBD5B7CB build/Build/Products/Debug-iphoneos/VoCab.app`。免费账号 7 天后要重装。**同步用户试过,没问题。**
- 真机上改的:朗读音色选择(设置 → 朗读);App 图标(`ipad/tools/make_icon.py` 画的,绿底白卡片 Aa + 蓝色 ✓);选中的行改成浅蓝底蓝字(原来系统深蓝高亮下文字看不清);答题纸的词块和「显示答案」固定在页面底部。
- **第三阶段**(用户点名做这五项,其余先放着):「看完每一页笔记才能自测」(只存 iPad,`seen.json`)、学习历史页面(左栏「其他」)、我的笔记、记忆曲线(`Extras.swift`)、长按例句的菜单(复制 / 收藏 / 不再考这句)。⭐ 和我的笔记现在双向同步;阅读的 `readsHidden` 仍然原样带着。
- 用户说 iPad 自带的音色都不好听:建议先下载 Premium 音色(Ava / Zoe);还不行就做「预生成神经网络音频」(edge-tts 或 Kokoro),还没定。
- **还没做**:文章 PDF、实体键盘筛词块、更好的音色。

---
## 2026-11-02:iPad App(第二阶段:复习 / 考核做题 + 同步)

- **复习 / 考核**:左栏点「复习中 / 考核中」,中间是概况卡(大数字、开始按钮、能移到下一阶段的提示)+ 按原因分组的列表(复习:今天到期 / 新加入 / 以后到期;考核:没做过 / 上次没对 / 上次答对)。做题是全屏(`RunView.swift`),规则照网页 `renderQuizCard()`:复习先看一遍笔记,每条最多 3 句(`runSentences`),揭晓前不显示词条名,最后一句后按整条结果给 SM-2 打一次分;连续答对 3 次 + 例句都答对过 → 下一条时自动移到考核;考核答错 → 退回复习;完成页可以撤销移动;算我对会重算分数。条目页:复习阶段、今天到期的条目是「复习这一条 →」;复习 / 考核阶段题都答对过时卡片底部有「移到 X」。
- **答题纸拆成共用的 `AnswerSheet.swift`**,条目页自测(`QuizView`)和做题共用。
- **同步**(`Sync.swift`):设置 → 同步到 GitHub,taobao 自己填 token(存 iPad 钥匙串,我不经手)。和网页同一个 `progress-taobao.json`、同样的合并规则:进度 / SM-2 卡新者胜、自测记录并集(原文不上传)、学习历史合并;不再考这句从同步读进来(iPad 上还不能设置);⭐、我的笔记、阅读原样带着,不会被 iPad 丢掉。时机:启动、回到前台拉一次,改动后 15 秒推,切到后台立即推。
- 新文件:`Review.swift`(SM-2、`dayNo()`、学习历史、`RunSession`)、`AnswerSheet.swift`、`RunView.swift`、`Sync.swift`。调试参数 `-pick review`、`-run review|test`。
- **验证**:模拟器里看过复习列表、复习的「先看一遍」、考核做题页;对过 GitHub 上 `progress-taobao.json` 的字段格式。**同步没有真正跑过**(要 taobao 的 token),第一次连上后要看一下网页那边进度对不对。
- **还没做**:学习历史 / 我的笔记 / 记忆曲线的页面、文章 PDF、在 iPad 上设「不再考这句」和 ⭐、实体键盘筛词块、「看完每一页笔记才能自测」;真机没测。

---
## 2026-11-02:iPad App(第一阶段)

- 用户要一个原生 SwiftUI 的 iPad App,主要给 taobao 用(问过三种做法:可安装的网页 / WKWebView 外壳 / 原生,用户选了原生)。代码在 `ipad/`,`xcodegen` 生成工程,怎么装到 iPad 上见 `ipad/README.md`(免费 Apple ID 7 天要重新装一次)。
- 规则照 Android 的 `SelfTest.kt` / `Model.kt` 和网页移植;存储格式和 Android 一样(`stage.json`、`selftest.json`),为以后同步 `progress-taobao.json` 做准备。
- Pages workflow 现在把学生目录的 `data.json` / `meta.json` 也发布出去(App 启动时从 `/vocab/taobao/` 拉更新);合进 main 之前 App 只用打包进去的数据。
- **还没做**:复习 / 考核做题(SM-2)、同步、我的笔记、学习历史、记忆曲线、文章 PDF、不再考这句 / 收藏、选词时用实体键盘筛词块、「看完每一页笔记才能自测」。只在模拟器上测过(iPad Air),还没在真机上跑。

---


## 2026-10-15 ~ 10-16:自动升级、两步作答、桌面三栏、记忆曲线、复习这一条

### 现在的状态

- **`dev` 上是 App v2.7**(versionCode 81,`92677d6`),**还没合进 `main`**。合了之后 Actions 会发 `app-v2.7`,网页也会跟着更新。
- `main` 在 `2c13fc8`(PR #51)。最新的 Release 是 **`app-v2.6`**(80),网页也是 2.6 对应的版本。`app-v2.4` 没有单独发:2.4 和 2.5 是一起合进 main 的,所以只发了 2.5。
- **手机(S10+)上装的是本地构建的 2.7 正式签名包**,所以 CI 发 2.7 时手机不会提示更新。下次改 App,版本照常往上加。
- 词条到 **#146**。阅读清单这两天新增了 10 条。

### 改了什么(按时间)

10-15 白天:

1. **复习 → 考核 自动升级、考核答错退回复习**(v2.1)
   - 升级条件:复习阶段每句都答对过,并且 SM-2 连续答对 ≥ 3 次(`PROMOTE_REPS`)。满足后,复习做题揭晓时提示「下一题时自动移到考核」。
   - 退回:考核里答错或偷看,退回复习,并给 SM-2 打一次「忘了」。
   - 完成页列出这一轮自动移动的条目,可以「撤销移动」。
2. **「移到 X」按钮在题都答对过时变绿**:先发成 2.1.1,后来改叫 2.2,因为这是界面改动,不是修 bug。同一次定了版本号规则(写进 `CLAUDE.md`)。注意:2.2 漏加了 versionCode,和 2.1.1 一样都是 76。
3. **`CLAUDE.md` 新规则**:原句已经很好时,开头标「🌟 原句就很好」;用户用英文提需求时,也要在末尾做英文检查(另外存了一条 memory)。
4. **网页字体和字号**
   - 字体和 App 一致:英文 Atkinson Hyperlegible,中文 Noto Sans SC。只在 `:root` 的 `--font` 定义一处。
   - 字号整体放大 25%:`--fs: 1.25`,所有 `font-size` 都写成 `calc(Npx * var(--fs))`。
5. **网页桌面三栏书桌**(宽度 ≥ 1100px)
   - 三栏:分类栏 / 条目列表 / 停靠的条目。
   - 配色换成冷灰,加了蓝墨水色 accent。
   - 顶栏有「今天要复习 N 条」和同步状态;错题本用条形图。
   - 分类栏和列表都能收起成最左边的竖条(快捷键 `[` / `]`);⤢ 铺满分类栏右边的整块区域。
   - 设计稿:https://claude.ai/artifact/PxXkUCTP3e9yP5CY7N1NMR
6. **两步作答**(v2.3)
   - 有标准答案的题先选词、再打字,两步都对,这句才算对。
   - **复习 / 考核做题时,一条要把所有例句都做完**,整条只打一次 SM-2 分。
7. 新词条 #145 acronym、#146 convoluted。阅读清单新增 `r-given-how-it-works`、`r-didnt-pick-up`、`r-what-do-you-call`、`r-as-i-understand-it`、`r-sanitize-so-that`、`r-bump-the-version`、`r-make-the-sentences-stick`。

10-15 晚上 ~ 10-16:

8. **README 截图(用户叫停)**:截了 8 张截图(网页用无界面 Chrome,App 用模拟器)。用户决定不推截图,也不改 README。截图和 README 草稿留在本机 `screenshots/`(已 gitignore,换电脑后没有)。
9. **Pages 构建**:触发路径加上了 `readlist.md`。以前只改阅读清单时不会重新构建,App 也就看不到新的阅读条目。
10. 阅读清单新增 `r-lets-not-locally`、`r-heard-of-the-curve`、`r-feel-free-for-testing`。
11. **「还没排期」改名「新加入」**(v2.4)。原名容易被读成「还没轮到、不用管」。
12. **记忆曲线**(v2.5):条目笔记最后一个分节「记忆」。它按复习 / 考核的记录回放 SM-2,估算遗忘曲线。这是估算,不是测量。模型和画法见 `CLAUDE.md`「记忆曲线」。
13. **网页选词时**,键盘的 Delete / Backspace 能撤回最后一个词,效果和 ⌫ 一样。
14. **复习这一条**(v2.6)
    - 起因:用户从复习列表点进条目,在条目页做了好几次自测,以为这就是复习。但条目页的自测从来不打 SM-2 分,所以队列和记忆曲线都没变。
    - 现在:今天该复习的条目,卡片底部的按钮变成「复习这一条 →」,点了就是只有这一条的复习。
15. **长句子答题**(v2.7,还在 dev)
    - 起因:选到第 6、7 行时,最后一行词块被下面的词块面板挡住。
    - 现在:「你的句子」框里有内容后,题目缩小一号(App 24 → 19sp,网页 22 → 18px)。每选或撤回一个词,都把框底部滚进可见区域。
    - 手机和网页都测过。

### 没做 / 已知问题

- **2.7 还没合进 main**(见上)。
- **网页窄屏(约 390px)上,条目标题会和右上角「N / M 下一个 ›」胶囊重叠。** 长标题(句子条目)最明显。这是之前就有的问题,还没修。
- 上一节(10-14 ~ 10-15)的已知问题都还在:白屏没复现、做题页没有「再试一次」、掌握 tab 没按 V2 改、今天的练习只能看今天。
- 手机上的测试都没揭晓答案,中途就退出了,没有留下答题记录。
- 模拟器 `are_test` 上装的是 debug 包,带演示数据(拍截图用的),和手机上的数据无关。

### 在别的电脑上

- `git pull` 拿 dev(10-13 改写过历史,之前 clone 的副本要重新 clone,见 `CLAUDE.md`)。
- **改 App**:递增 `android/app/build.gradle.kts` 的 versionCode / versionName → 推 dev → 用户把 dev 合进 main → Actions 自动签名发 Release。
  - 只有本地 `adb install` 测试才需要签名 key(见 `CLAUDE.md`「在别的电脑上开发 / 发布 App」)。
  - 装之前先确认 `apksigner verify --print-certs` 显示的是 `CN=aix`。**不要卸载手机上的 App。**
- **改网页**:只改 `template.html`。`python3 build.py` 生成 `index.html`,在仓库根目录跑 `python3 -m http.server` 打开看。
  - 桌面三栏要窗口宽度 ≥ 1100px 才出现。
  - 用 puppeteer 截图时不要用 `clip`:它会改视口宽度,触发 `deskMQ`,重新打开第一条。截整页,再裁。

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
