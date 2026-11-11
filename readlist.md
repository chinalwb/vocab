# 阅读 · C1 对比

只读、不考:同一个意思,常用说法和 C1 说法放在一起读,感受差别在哪。App 里在 ⋮ → 阅读。

也收写不出可考例句的笔记(语法笔记、速查表、标点规则),2026-10-28 从 vocabulary.md 搬来的 9 条就是这类;它们按自己原来的分节写,不一定有常用说法 / C1 说法。

格式(build.py 解析,新的一条追加在文件末尾,App 里最新的排最前):

- `<a id="r-…"></a>` + `## 中文意思`(详情页最上面那句)
- `- 日期:YYYY-MM-DD`、`- 关键词:a · b`(列表页靠它一眼认出是哪条,取 C1 版里最值得学的一两个说法,用 ` · ` 分隔)
- `**常用说法:**` 后面一个引用块(低一级的说法)
- `**C1 说法:**` 后面一个引用块,高级的地方用 **加粗**
- `**差别:**` 后面一个列表,每条讲一个用法
- `**我的原句:**` 后面一个引用块(可选),错的地方用 `==…==` 标出(App 里红底高亮);下面一个列表,每处错误一条:`- ❌/⚠️ [类型] ==原文== → **改法** —— 原因`(类型同错题本那 10 个;这里的错误不进错题本)

<a id="r-table"></a>
## table (sth)

- 日期:2026-08-19
- 关键词:table a discussion

⚠️ **英式和美式意思正好相反**,经典的跨语境坑。

**美式英语:** table a discussion = **搁置**这个议题,先不讨论。
**英式英语:** table a discussion = **提出**这个议题,拿出来讨论。

（所以开跨国会议时这个词很容易造成误会）

---

<a id="r-dangling-modifier"></a>
## 语法笔记:悬垂修饰语(dangling modifier)

- 日期:2026-08-21
- 关键词:when used · if you use it

**问题描述:** "if / when + 动名词(-ing)" 这种缩略状语结构,省略主语时,隐含的主语必须能从主句里清楚地推断出来。如果推不出来(比如主句主语是个物,而 -ing 动作的执行者其实是"人"),就会造成逻辑不清、悬垂修饰语的问题。

**改正后:**
- ✅ *What is the model name for Gemini 3.5 Flash Lite when used in Python code?*
- ✅ *What is the model name of Gemini 3.5 Flash Lite if you use it in Python code?*

**我的原句:**

> What is the model name of Gemini 3.5 Flash Lite ==if using it== in ==python== code?

- ⚠️ [逻辑] ==if using it== → **if you use it** / **when used** —— 技术写作里这种省略主语的条件句很常见;更严谨的写法是补上主语。
- ❌ [词形] ==python== → **Python** —— 编程语言名是专有名词。
- ✅ **model name of X** —— 意思清楚,日常使用完全够用;技术语境也常说 **model name for X**。

---

<a id="r-postpone-family-quickref"></a>
## "推迟/暂缓" 类词组一句话辨析速查

- 日期:2026-08-21
- 关键词:hold off · put on hold · push back

**含义:** 一组"推迟 / 暂缓 / 搁置"的说法,按**要不要定新时间、做没做到一半、还打不打算做、场合正式程度**来选。

**怎么选(先问自己这几个问题):**

- **只是等一会儿?** → **hold on**(几秒到几分钟,不是搁置计划)
- **有明确的新时间?**
  - 口语 / 工作沟通 → **push back (to …)**
  - 书面 / 正式通知 → **postpone (until …)**
  - 制度化流程(税、入学、判决)→ **defer**
- **还没开始,先不做,时间待定?** → **hold off (on)**
- **已经开始了,先停下?** → **put on hold**
- **还要做,只是不急?**
  - 降低优先级 → **put on the back burner**(口语)/ **deprioritize**(工作)
  - 可能不会再捡起来 → **shelve**(无限期搁置)
- **带"拖延、不想做"的意味?** → **put off**(偏自己拖)/ **stall**(故意拖时间,负面)
- **被别的事耽搁了(被动)?** → **hold up**
- **会议上"先不议"?** → **table** —— ⚠️ 美式是"搁置",英式是"提出讨论",跨国团队慎用

**逐个看(每个配一句例句):**

- ① **hold on** —— 等一下 / 坚持住(短暂等待)。
  *Hold on, let me check the logs first.*(等一下,我先看看日志。)
- ② **hold off (on)** —— 先别启动 / 先别做,等更合适的时机。
  *Let's hold off on the release until the crash is fixed.*(崩溃修好之前,先别发版。)
- ③ **put off** —— 把一件事推到以后(最通用,常带拖延语感)。
  *I keep putting off updating the documentation.*(我一直在拖着没更新文档。)
- ④ **postpone** —— 正式、中性的"推迟到另一个时间"(书面首选)。
  *The launch has been postponed until next month.*(发布已推迟到下个月。)
- ⑤ **defer** —— 制度化 / 官方场景的"推迟";另有 defer to = 听从,勿混淆。
  *She deferred her admission for a year.*(她把入学推迟了一年。)
- ⑥ **push back** —— 口语化的"推迟",常带新时间(商务场合高频)。
  *Can we push the demo back to Thursday?*(演示能推到周四吗?)
- ⑦ **put on hold** —— 暂停 / 搁置(多指已经开始的事)。
  *The migration is on hold until we get more people.*(迁移先暂停,等有更多人手再说。)
- ⑧ **hold up** —— 耽搁(被动受阻)/ 打劫,两个不相关的意思。
  *Sorry I'm late — I got held up in a meeting.*(抱歉迟到了,被一个会耽搁了。)
- ⑨ **table** —— 美:搁置议题;英:提出讨论。
  *(US) Let's table this discussion until next week.*(这个讨论下周再说。)
- ⑩ **shelve** —— 无限期搁置 / 雪藏,暗示可能不会再捡起来。
  *The redesign was shelved after the budget cut.*(预算削减后,改版方案被搁置了。)
- ⑪ **stall** —— 故意拖延、拖时间(负面)。
  *The vendor keeps stalling instead of giving us a date.*(供应商一直在拖,不给我们一个日期。)
- ⑫ **put sth on the back burner** —— 降低优先级、以后再处理,语感比 shelve 轻。
  *Let's put the dark mode on the back burner for now.*(暗色模式先放一放。)

**相关条目:** ① [hold on](#hold-on) · ② [hold off](#hold-off) · ③ [put off](#put-off) · ④ [postpone](#postpone) · ⑤ [defer](#defer) · ⑥ [push back](#push-back) · ⑦ [put on hold](#put-on-hold) · ⑧ [hold up](#hold-up) · ⑩ [shelve](#shelve) · ⑪ [stall](#stall) · ⑫ [back burner](#back-burner) · [deprioritize](#deprioritize) · [自测:这个先放一放吧](#t-hold-off-for-now)

---

<a id="r-wh-question-order"></a>
## 语法笔记:why 疑问句 vs how come vs 间接疑问句

- 日期:2026-08-25
- 关键词:Why does it…? · How come it…?

**规则总览:** 疑问词提问是否需要 do-support(do/does/did)+ 倒装,要分三种情况看。

**① 直接疑问句(why/what/how 等)—— 需要 do-support + 倒装**
- ❌ *Why it uses the word "would"?*
- ✅ *Why does it use the word "would"?*
说明:主语第三人称单数用 does,主动词还原成原形(use,不是 uses)。

**② how come 疑问句 —— 特例,不需要倒装,保持陈述句语序**
- ✅ *How come it uses the word "would"?*
- ❌ *How come does it use the word "would"?*(错,how come 后面不能再加 does)
说明:how come 意思等同于 why,但语气更口语化、略带疑惑/惊讶,且语法上不倒装,动词保持正常变位(uses)。

**③ 间接疑问句 / 嵌入疑问句(嵌在 tell me / I wonder / do you know 等后面)—— 回到陈述句语序,不倒装**
- ✅ *He didn't tell me why that sentence uses the word "would".*
- ❌ *He didn't tell me why does that sentence use the word "would".*(错,间接疑问句里不能再加 does)

**一句话记忆:** 独立提问用倒装(why does...);how come 天生不倒装;疑问句被"包"进另一句话里(间接疑问句)时,也变回陈述句语序(why it uses...)。

**相关条目:** [句子:hit an error / under what circumstances](#s-rate-limit-error) —— 间接疑问句语序用对了的一个实例(in what situations users would hit...)。

**我的原句:**

> ==Why it uses== the word "would"?

- ❌ [结构] ==Why it uses== → **Why does it use** —— 直接疑问句要 do-support + 倒装。

---

<a id="r-synonym-preposition"></a>
## synonym / antonym / synonymous 的介词搭配

- 日期:2026-08-25
- 关键词:synonyms for · synonymous with

**标准搭配:**
- **synonym(s) for X** —— 最常见、最自然的说法。
  *What are some synonyms for "immense"?*
- **synonym(s) of X** —— 也正确,偏书面/词典式的表达。
  *"Huge" is a synonym of "immense".*
- **antonym(s) for/of X** —— 同理,antonym 也搭配 for 或 of。
  *What's the antonym for "immense"?*

**容易混的点:** 如果用的是形容词 **synonymous**(而不是名词 synonym),搭配的介词是 **with**,不是 for/of:

> *"Huge" is synonymous with "immense".*
> "巨大"和"immense"是同义词。(这里 synonymous with 是固定搭配)

**一句话总结:** 名词 synonym/antonym 后面接 **for** 或 **of**;形容词 synonymous 后面接 **with**——三者都不用 to。

**我的原句:**

> ==synonyms to== immense

- ⚠️ [介词] ==synonyms to== → **synonyms for / of** —— 有人这么用,但标准搭配是 for 或 of。

---

<a id="r-comma-splice-shared-code"></a>
## 语法笔记:逗号粘连(comma splice)+ shared code 连字符误用

- 日期:~2026-09-05
- 关键词:comma splice · shared code

**问题一:逗号粘连(comma splice / run-on sentence)**
"The user was using an iPhone"(陈述句)+ "though the feature was built on shared code"(从句)+ "please help check it out"(祈使句)三段只靠逗号硬连在一起。祈使句不能直接用逗号接在前面的陈述/从句后面,需要用句号断开,或改写成疑问句形式的礼貌请求。

**问题二:shared-code 不该加连字符**
这里 "shared code"(共享代码)是介词 on 后面的**名词短语**,shared 只是普通形容词修饰 code,不是放在另一个名词前面的复合形容词,所以不需要连字符。只有当它整体作为形容词去修饰后面的名词时才加连字符,比如 "a shared-code feature"。

**改正后:**
> The user was using an iPhone, even though the feature was built on shared code. Could you help check it out?
> 这个用户用的是 iPhone,不过这个功能是基于共享代码构建的——能麻烦帮忙查一下吗?

这里把 though 换成语气更明确的 even though,去掉了多余的连字符,并把最后的祈使句断成独立一句(或改成疑问句 "Could you help check it out?",更像礼貌请求而不是命令)。

**我的原句:**

> The user was using an iPhone, though the feature was built on ==shared-code==, ==please help check it out==.

- ❌ [标点] ==, please help check it out== → **. Could you help check it out?** —— 陈述句 + 从句 + 祈使句只靠逗号硬连(逗号粘连),用句号断开。
- ❌ [词形] ==shared-code== → **shared code** —— 作介词宾语的名词短语不加连字符。

---

<a id="r-gemini-grammar"></a>
## Gemini 也会犯语法错误吗?我原本以为它的英文几乎完美。

- 日期:2026-10-14
- 关键词:get grammar wrong · free of

**常用说法:**

> But does Gemini make grammar mistakes too? I'm pretty sure it's very good at English and rarely makes mistakes — at least not obvious grammatical ones.

**C1 说法:**

> But can Gemini **get grammar wrong** too? **I'd assumed** its English was **close to flawless** — at least **free of** obvious grammatical errors.

**差别:**

- **get grammar wrong** —— get + 宾语 + 形容词,"把某事弄错";对应 *make grammar mistakes*。反过来是 *get it right*。
- **I'd assumed** —— 过去完成时:"我原本以为",暗示现在开始怀疑;对应 *I'm pretty sure*。
- **close to flawless** —— "几乎完美",比 *very good* 精确。
- **free of** —— "没有(某种不好的东西)":*free of errors / free of charge*;对应 *rarely makes mistakes*。

**我的原句:**

> ==However,== does Gemini also make grammar mistakes? ==I deeply believe== Gemini is pretty good at ==it== and ==doesn't make any mistakes, at least no obvious grammar mistakes==.

- ⚠️ [语气] ==However,== → **But** —— *however* 用在陈述句里转折,问句开头用 *But* 更自然。
- ⚠️ [搭配] ==I deeply believe== → **I'm pretty sure** —— *deeply believe* 多用于信念、价值观。
- ⚠️ [逻辑] ==it== → **English** —— *it* 指英语还是语法不清楚,直接写名词。
- ❌ [逻辑] ==doesn't make any mistakes, at least no obvious …== → **rarely makes mistakes — at least not obvious ones** —— 先说"完全不错",又用 *at least* 收回一半,前后矛盾。

---

<a id="r-comes-naturally"></a>
## 两版里,第一版对我来说更顺手;第二版高出一档,是我想达到的水平。

- 日期:2026-10-14
- 关键词:of the two · a notch above

**常用说法:**

> Comparing these two versions, the first one comes more naturally to me. The second one is fine too, but it feels more advanced — it's the level I'm aiming for.

**C1 说法:**

> **Of the two**, the first comes more naturally to me. The second feels **a notch above where I am** — it's **the level I'm working toward**.

**差别:**

- **Of the two** —— "两者之中",比较时的简洁开头;对应 *Comparing these two versions*。
- **a notch above** —— "高出一档";notch 是刻度上的一格。对应 *more advanced*。
- **where I am** —— 用"我所在的位置"代指"我现在的水平",很地道。
- **working toward** —— 强调正在一步步努力;*aiming for* 只说目标。

**我的原句:**

> If we compare ==these two sentences==, the first one ==runs natural for me==. ==The latter one== is ==ok== too, but it just feels advanced and ==is some level I am chasing==.

- ⚠️ [搭配] ==these two sentences== → **these two versions** —— 每一版都不止一句。
- ❌ [搭配] ==runs natural for me== → **comes more naturally to me** —— *run* + 形容词只用于 *run dry / run late* 等少数搭配;比较两者要用比较级。
- ⚠️ [结构] ==The latter one== → **The latter** / **The second one** —— *latter* 本身就是代词,不再加 *one*。
- ⚠️ [词形] ==ok== → **OK** / **okay**。
- ❌ [搭配] ==is some level I am chasing== → **is the level I'm aiming for** —— *some level* 显得随意;追求某个水平用 *aim for / work toward*。

---

<a id="r-reading-list"></a>
## 给 App 加一个只读的阅读清单:每句配上 C1 说法,让我体会 C1 不同在哪。

- 日期:2026-10-14
- 关键词:get a feel for · what sets … apart

**常用说法:**

> Let's add a new read-only section to the app: a reading list. It pairs sentences with their C1 versions so I can see how C1 phrasing differs from lower levels.

**C1 说法:**

> Let's add a **read-only reading list** to the app — **side-by-side pairs** of everyday and C1 phrasing, **so I can get a feel for what sets C1 apart**.

**差别:**

- **a read-only reading list** —— 形容词直接放在名词前,一个名词短语说完;对应 *a new read-only section … : a reading list* 两步走。
- **side-by-side pairs** —— "并排成对",一个短语交代了呈现方式;对应 *It pairs sentences with their C1 versions* 一整句。
- **get a feel for** —— "找到感觉、慢慢体会",比 *see* 更贴合"感受";*feel* 在这里是名词。
- **what sets C1 apart** —— *set … apart* = 使与众不同;对应 *how C1 phrasing differs from lower levels*,不用再说比较对象。
- **— (破折号)** —— 接补充说明,把两句并成一句,读起来更紧凑。

**我的原句:**

> Let's introduce a new ==channel== for the app: ==read-list==. ==This is a read-only channel.== ==Sentences here are for identifying== the differences between expressions ==in C1 and lower levels==.

- ⚠️ [搭配] ==channel== → **section** —— App 里的板块叫 *section / page*;*channel* 多指频道。
- ⚠️ [词形] ==read-list== → **reading list** —— 固定说法,不加连字符。
- ⚠️ [直译] ==This is a read-only channel.== → 并进前一句:**a new read-only section** —— 单独成句显得啰嗦。
- ⚠️ [结构] ==Sentences here are for identifying== → **so I can see** —— 做比较的是读的人,不是句子。
- ⚠️ [介词] ==in C1 and lower levels== → **at C1 / at lower levels** —— 说某个级别用 *at*。

---

<a id="r-list-not-page"></a>
## 另外,阅读清单应该是一个列表,而不是单独一页。每条要有个关键词,让我一眼分清、决定要不要点开。详情页从上到下是中文、普通说法、C1 说法,我的原句折叠在最下面。这样说清楚吗?

- 日期:2026-10-14
- 关键词:at a glance · tucked away

**常用说法:**

> Also, the reading list isn't a single page — it's a list of sentences. Each new item should have a keyword or something similar to tell it apart from the others, so I can scan the list and decide whether to open an item. On the details page, the Chinese should be at the top, with the lower-level version below it, then the C1 version. My original sentence should be collapsed at the bottom. Is that clear?

**C1 说法:**

> **One more thing:** the reading list should be **a list, not a single page**. Each entry needs a keyword **so I can tell them apart at a glance** and decide which ones are **worth opening**. **The detail view should run top to bottom**: the Chinese, the everyday version, then the C1 version, **with my original sentence tucked away** at the bottom. **Does that make sense?**

**差别:**

- **One more thing:** —— 口语里追加要求的自然开头;对应 *Also,*。
- **a list, not a single page** —— "是 A 不是 B"直接并列,比 *isn't … — it's …* 更短。
- **tell them apart at a glance** —— *tell … apart* = 区分开;*at a glance* = 一眼就能;对应 *tell it apart from the others* + *scan the list*。
- **worth opening** —— *worth + doing*,"值得点开";对应 *decide whether to open an item*。
- **run top to bottom** —— 描述页面从上到下的排布;后面冒号直接列出顺序,不用 *at the top … below it … then …* 一个个交代。
- **tucked away** —— "收在一边、不占地方",对应 *collapsed*,更生动。
- **Does that make sense?** —— 确认对方理解的常用问法,比 *Is that clear?* 更客气。

**我的原句:**

> What's more, read-list is ==not a single page, instead, it is== a page that lists sentences. Each time a new item is added, there should be a "keyword" or ==something alike== to ==identify it from others==. This way, I can browse the read-list page and see whether I need to tap on it to see the details page. And ==in the details page==, it should show the Chinese ==at top==, ==lower-level translation== below it. ==Subsequently==, the C1 translation is shown too, with my original sentence ==collpased== at the bottom. Is this clear?

- ❌ [标点] ==not a single page, instead, it is== → **not a single page; instead, it's** —— 两个完整句子只用逗号连接(逗号粘连)。
- ❌ [搭配] ==something alike== → **something similar** / **something like that** —— *alike* 不能放在名词后作定语。
- ⚠️ [介词] ==identify it from others== → **tell it apart from the others** —— *identify* 不搭配 *from*。
- ⚠️ [介词] ==in the details page== → **on the details page** —— 页面上用 *on*。
- ⚠️ [冠词] ==at top== → **at the top**。
- ⚠️ [冠词] ==lower-level translation== → **the lower-level version** —— 可数名词要加冠词。
- ⚠️ [语气] ==Subsequently== → **Then** —— 太正式,描述页面顺序用 *Then* 就够了。
- ❌ [词形] ==collpased== → **collapsed** —— 拼写。

---

<a id="r-could-use-color"></a>
## 截个图。我想给这些条目加个背景色。

- 日期:2026-10-14
- 关键词:could use · grab a screenshot

**常用说法:**

> Take a screenshot. I'd like to add a background color to these entries.

**C1 说法:**

> **Grab** a screenshot. **These cards could use** a background color.

**差别:**

- **Grab a screenshot** —— *grab* 口语里表示"随手拿/弄一下",比 *take* 更轻快;*grab a coffee / grab a seat* 同理。
- **could use** —— "需要、有了会更好",婉转地提建议:*The intro could use some work.*(开头还可以再改改。)对应 *I'd like to add*。
- **These cards …** —— 主语从"我"换成物:不说"我想加",而说"它们需要",语气更客观;*cards* 也比 *entries* 更具体,说的就是屏幕上那些卡片。

**我的原句:**

> ==take== a screenshot ==-== I'd like to add some background color ==for== these entries

- ⚠️ [词形] ==take== → **Take** —— 句首大写。
- ⚠️ [标点] ==-== → **.** —— 两个完整句子之间用句号;连字符不能当分隔用。
- ⚠️ [介词] ==for== → **to** —— 给某物加东西是 *add … to*。

---

<a id="r-dedicated-branch"></a>
## 开个专门的分支按 V2 重构界面;做完再加个功能:每天的练习记下来,今天的能导出成 PDF(三个版本)。没完全弄清需求前别急着写代码。

- 日期:2026-10-15
- 关键词:on a dedicated branch · side by side

**常用说法:**

> OK, now please refactor the UI based on your V2 design. Do it on a separate branch, and let's name it ui/claude-code-v2. After that, let's add a new feature for the whole app: record the user's practice in a file (maybe .md), one file per day. Save the user's input and the correct sentence as a pair, and if the user practices a sentence several times, keep the last one. Finally, we want to export today's practice as a PDF with three versions: one with only the correct sentences (Chinese and English), one with the user's input and the correct sentences for comparison, and one with only the Chinese for more practice. Is this clear? You don't need to start coding until you fully understand my requirements.

**C1 说法:**

> OK — go ahead and implement your V2 **on a dedicated branch**; **let's call it** ui/claude-code-v2. **Once the refactor is in**, let's add an **app-wide** feature: log each day's practice to a file (Markdown, perhaps), storing the user's input and the correct sentence as a pair and **keeping only the latest attempt** at each sentence. On top of that, I'd like to export today's practice as a PDF in three versions: the correct sentences (Chinese and English), the user's input and the correct sentence **side by side**, and the Chinese alone for extra practice. **Does that make sense?** **No need to jump into the code** until the requirements are clear.

**差别:**

- **on a dedicated branch** —— dedicated = 专用的、专门为某事开的:*a dedicated server / a dedicated team*。对应 *a separate branch*。
- **let's call it X** —— 给东西起名最自然的说法;对应 *name the branch as X*。
- **Once the refactor is in** —— be in = 做完、合进去了;*once* 比 *after that* 更强调"一完成就接着做"。
- **app-wide** —— "整个 App 范围的",名词 + *-wide* 很常用:*company-wide / system-wide*。对应 *for the whole app*。
- **keeping only the latest attempt** —— *latest* 强调时间上最新;*the last one* 也能懂,但可能被理解成"排在最后的那个"。
- **side by side** —— "并排",放在一起对照时最常用;对应 *for comparison*。
- **Does that make sense?** —— 确认对方理解,比 *Is this clear?* 柔和,不像在考对方。
- **No need to jump into the code** —— jump into = 急着一头扎进去做;对应 *You don't need to start coding*。

**我的原句:**

> OK - Now refactor the UI as per your V2. Do it on a ==dedicateed== branch. ==Lets== ==name the branch as== ui/claude-code-v2. And after implementing the UI refactor, ==lets== add a new feature for the entire app, see details ==as follow==: Record user's practice to some file (maybe .md). Save it by day. We save both the user's input and the correct one as a pair. If the user has practiced some of them multiple times, keep the last one. Finally, we want to have a feature to export ==Today's== practice as a PDF file. It contains ==3 copies==: one is only the correct sentences (both Chinese and English), the 2nd one is for saving the user's input and the correct sentences for comparing; the last one is only Chinese for ==praticing== more. Is this clear? You don't need to rush to code until you understand my requirements fully.

- ❌ [词形] ==dedicateed== → **dedicated** —— 拼写。
- ❌ [标点] ==Lets== → **Let's** —— let us 的缩写要加撇号;*lets* 是"允许"的第三人称单数(两处)。
- ⚠️ [搭配] ==name the branch as== → **name the branch** / **call it** —— name 后面直接跟名字,"命名为"不用 as。
- ❌ [搭配] ==as follow== → **as follows** —— 固定说法,总带 s。
- ❌ [词形] ==Today's== → **today's** —— 句中不大写。
- ⚠️ [搭配] ==3 copies== → **three versions** —— *copies* 指一模一样的几份;这三份内容不同。
- ❌ [词形] ==praticing== → **practicing** —— 拼写。

---

<a id="r-merge-back-into-dev"></a>
## 把这个分支合进 main,再把 main 合回 dev。发布新的 APK,并更新网页版,让它的界面和 App 保持一致。

- 日期:2026-10-15
- 关键词:merge main back into dev · stays in sync with

**常用说法:**

> Merge this branch into main, then merge main into dev. Release the new APK, and update the web version so the UI is the same as the app.

**C1 说法:**

> Merge this branch into main, then **merge main back into dev**. **Publish the new APK**, and update the web app **so its UI stays in sync with** the app.

**差别:**

- **merge main back into dev** —— *back* 表示合回原来的分支;说清楚是 main 合进 dev,对应 *sync it to dev*(*it* 指代不清)。
- **Publish the new APK** —— APK 是缩写,要大写;*new* 已经说了"新版本",不用再加 *version*。
- **so its UI stays in sync with the app** —— *so* + 从句说目的,主语 *its UI* 明确,*stay in sync with* 交代和谁保持一致;对应 *to make the UI stay in sync*。

**我的原句:**

> Merge this branch into main, then ==sync it== to dev. Publish the ==new version apk== and update the web app to make the UI stay in sync.

- ⚠️ [逻辑] ==sync it== → **merge main back** —— *it* 指 this branch 还是 main 不清楚。
- ⚠️ [词形] ==new version apk== → **new APK** —— 缩写要大写;*new* 已经表示新版本。

<a id="r-given-how-it-works"></a>
## 按照 SRS 的原理,你建议怎么把条目从「复习」自动移到「考核」?

- 日期:2026-10-15
- 关键词:given how … works · how would you suggest we

**常用说法:**

> According to how SRS works, what is your advice for moving entries from Review to Test automatically?

**C1 说法:**

> **Given how SRS works**, **how would you suggest we automatically promote** entries from Review to Test?

**差别:**

- **Given how SRS works** —— *given* 是"考虑到……、既然……",把原理当作前提;*according to* 多用于"据某人 / 某来源说",后面接 *how it works* 不太贴切。
- **how would you suggest we …** —— 直接问"你建议我们怎么做",*would* 让语气更客气;对应 *what is your advice for moving*,名词化的问法更绕。
- **automatically promote** —— *promote* 是"升级、晋级",比 *move* 更准确地说出"进到下一阶段";副词放在动词前面,不用拖到句尾。

**我的原句:**

> So, ==according to how SRS works==, ==what is your advice for== moving entries from 复习 to 考核 automatically?

- ⚠️ [搭配] ==according to how SRS works== → **given how SRS works** —— *according to* 引出信息来源,表示"考虑到这个原理"用 *given*。
- ⚠️ [直译] ==what is your advice for== → **how would you suggest we** —— "你的建议是什么"直译成名词结构,问做法时用 *how would you suggest we + 动词原形* 更自然。

<a id="r-didnt-pick-up"></a>
## 我在新浏览器里打开网页版,发现它没有同步我的进度。我猜是因为它没有我的 GitHub PAT,所以找不到进度文件了。可我想填 PAT 的时候,却找不到原来那个输入框。

- 日期:2026-10-15
- 关键词:didn't pick up · lost track of · the input field that used to be there

**常用说法:**

> I opened the web app at chinalwb.github.io/vocab in a new browser, but it wasn't in sync with my progress. I think it couldn't find the progress file because it didn't have my GitHub PAT. When I tried to enter the PAT, I couldn't find the input field.

**C1 说法:**

> I opened the web app at chinalwb.github.io/vocab in a new browser, and it **didn't pick up** my progress. I **suspect** it **lost track of** the progress file because it didn't have my GitHub PAT. But when I went to enter the PAT, I couldn't find **the input field that used to be there**.

**差别:**

- **didn't pick up my progress** —— *pick up* 是"(自动)获取、接上",说的是新浏览器根本没拿到进度;对应 *wasn't in sync with*,只描述状态。
- **I suspect** —— 猜测一件不太好的事、还算有把握时用 *suspect*,比 *I think / I guess* 更书面、更精确。
- **lost track of** —— 固定搭配"不再掌握……的下落",不加 *the*;对应 *couldn't find*。
- **the input field that used to be there** —— *used to be there* 点出"原来有、现在不见了",正是要说的意思;对应常用说法里只说 *the input field*。

**我的原句:**

> I opened ==https://chinalwb.github.io/vocab/ web app== in a new browser, but I found it ==didn't stay in sync== with my progress. I guess it was because it lost ==the track of== the ==rogress== file ==as it may not have== the PAT for my ==Github==. When I tried to fill in the PAT, I ==didn't find== the input ==which previously worked well==.

- ⚠️ [冠词] ==https://chinalwb.github.io/vocab/ web app== → **the web app at https://…** —— 网址当修饰语时前面要有冠词,更自然的是 *the web app at + 网址*。
- ⚠️ [逻辑] ==didn't stay in sync== → **didn't pick up my progress** —— *stay in sync* 暗示原来同步、后来断了;新浏览器从没同步过。
- ❌ [冠词] ==the track of== → **lost track of** —— 固定搭配 *lose track of*,不加 *the*。
- ❌ [词形] ==rogress== → **progress** —— 拼写。
- ⚠️ [动词] ==as it may not have== → **because it didn't have** —— 前文是过去时,这里也用过去时;*as* 表原因容易有歧义,用 *because*。
- ⚠️ [词形] ==Github== → **GitHub** —— 专有名词大小写。
- ⚠️ [动词] ==didn't find== → **couldn't find** —— 表示"找不到"用 *couldn't*,强调能力 / 结果。
- ⚠️ [逻辑] ==which previously worked well== → **that used to be there** —— 要说的是"原来有、现在不见了",不是"原来好用"。

<a id="r-what-do-you-call"></a>
## 用一个短语里各个词的首字母组成的那种词,叫什么?

- 日期:2026-10-15
- 关键词:what do you call · formed from the initial letters

**常用说法:**

> What's the word for a short form made from the first letters of a phrase?

**C1 说法:**

> **What do you call** a word **formed from the initial letters** of a phrase?

**差别:**

- **What do you call …?** —— 问"这个东西叫什么"最地道的句型;对应 *What's the word for …?*,后者也自然,但没这么口语化。
- **a word formed from the initial letters** —— *formed from* 说清这个词是"由首字母组成的",*initial letters* 比 *first letters* 更书面;答案是 [acronym](#acronym)。

**我的原句:**

> What is the word ==which means it is== ==the short form of the initial letters== of a phrase?

- ⚠️ [直译] ==which means it is== → **for** —— "意思是它是……"逐字翻译,绕了一圈;*the word for + 名词* 就够了。
- ⚠️ [逻辑] ==the short form of the initial letters== → **a short form made from the first letters** —— 缩写是"由首字母组成的短形式",不是"首字母的短形式"。

<a id="r-as-i-understand-it"></a>
## 我看了这个 MR 改动的文件,按我的理解说一下它是怎么工作的;有理解错的地方请指出来。

- 日期:2026-10-15
- 关键词:as I understand it · in turn · the single entry point for

**常用说法:**

> I looked at the changed files in this MR. This is how it works, I think:
> • PushManager is the main class for showing and cancelling notifications.
> • Showing: there is a new tag parameter, so every notification has a tag now.
> • Cancelling: PushManager adds an onSessionShown listener. It is called when the user opens a session. ChatScreen calls OnScreenSession.register() when a session is shown, and then the listener clears the notifications of that session.
> Please tell me if I'm wrong.

**C1 说法:**

> I walked through the changed files in this MR. Here's how it works, **as I understand it**:
> • PushManager is **the single entry point for** displaying and cancelling notifications.
> • Displaying: a new `tag` parameter has been added, so every notification **is now posted with** a tag.
> • Cancelling: PushManager registers an `onSessionShown` listener, **which fires when** the user opens a session. ChatScreen calls `OnScreenSession.register()` when a session is shown, **which in turn triggers** the listener to clear **that session's** notifications.
> **Let me know if I've got any of this wrong.**

**差别:**

- **as I understand it** —— 插入语"按我的理解",放句末或句中,先说明这是自己的理解、可能有偏差;对应 *I think*。也说 *as far as I can tell*。
- **the single entry point for** —— "所有调用都经过它的唯一入口",比 *the main class for* 更准确地说出它在架构里的位置。
- **is now posted with a tag** —— Android 里通知是 *post* 出去的;*now* 点出这是这次 MR 带来的变化;对应 *has a tag now*。
- **which fires when** —— 监听器、事件"触发"用 *fire*,描述代码行为用一般现在时;对应 *It is called when*。
- **which in turn triggers** —— *in turn* = "进而、接着又",用在 A 引起 B、B 再引起 C 的连锁里;对应 *and then*,*then* 只说先后,*in turn* 说的是因果一环扣一环。例句:
  - *The config change restarts the service, which in turn clears the cache.* 配置一改,服务就会重启,服务重启又会把缓存清掉。
  - *Faster builds mean quicker reviews, which in turn let us ship more often.* 构建更快,评审就更快,我们也就能更频繁地发版。
  - 另一个意思是"依次、轮流":*Each reviewer commented in turn.* 每个评审人依次发表了意见。
- **that session's notifications** —— 说清是哪些通知;对应 *the notifications of that session*,所有格更简洁。
- **Let me know if I've got any of this wrong.** —— 对着作者复述自己的理解时常用的收尾,语气谦和,也请对方纠正;对应 *Please tell me if I'm wrong*。

**我的原句:**

> I walked through the changed files in this MR. Here is how it works ==by my understanding==. First off, PushManager is the ==exis== for displaying and cancelling notifications. ==There is a new parameter called "tag" added== for displaying notifications. So every notification is delivered with a tag. As for cancelling, PushManager sets up an ==on session shown listener==, which ==will be called== when ==user== opens ==as sessions==. So in ChatScreen, ==it== calls OnScreenSession.==registerer== when a session is shown. ==Subsequently==, the session shown listener is fired to ==clear off== the related notifications.

- ⚠️ [搭配] ==by my understanding== → **as I understand it** —— 固定说法是 *as I understand it* 或 *to my understanding*。
- ❌ [词形] ==exis== → **the single entry point** —— *exis* 不是英文单词;*axis*(轴线)也不能表示"核心入口"。
- ⚠️ [结构] ==There is a new parameter called "tag" added== → **A new `tag` parameter has been added** —— *There is … added* 结构拖沓,直接用被动句。
- ⚠️ [词形] ==on session shown listener== → **an `onSessionShown` listener** —— 多个词组成的修饰语要加连字符,或者直接写代码里的名字。
- ⚠️ [动词] ==will be called== → **fires** —— 描述代码的行为用一般现在时。
- ❌ [冠词] ==user== → **the user** —— 单数可数名词前要有冠词。
- ❌ [词形] ==as sessions== → **a session** —— 打错了;用户一次打开的是一个会话。
- ⚠️ [逻辑] ==it== → **ChatScreen** —— *it* 指代不清,写出是谁在调用。
- ⚠️ [词形] ==registerer== → **`register()`** —— 方法名照代码原样写,加上括号和反引号。
- ⚠️ [语气] ==Subsequently== → **which in turn** —— *subsequently* 偏书面;MR 评论里用 *which in turn* 或 *That then* 更自然,也点出因果。
- ⚠️ [搭配] ==clear off== → **clear** —— 清除通知说 *clear* 或 *dismiss*,不说 *clear off*。

<a id="r-sanitize-so-that"></a>
## 把记忆脱敏后推到 GitHub,这样我在别的电脑上工作时也适用同样的规则。

- 日期:2026-10-15
- 关键词:sanitize · so that

**常用说法:**

> Remove the private details from the memory and upload it to GitHub, so I can use the same rules on other computers.

**C1 说法:**

> **Sanitize** the memory **and push it to GitHub, so that** the same rules apply when I work on other computers.

**差别:**

- **sanitize** —— 去掉数据里的敏感信息(人名、公司名、账号等)再交出去,工程里的标准说法;对应 *remove the private details*。不要用 *desensitize*,它是"使脱敏、使麻木"(过敏治疗、对暴力麻木)。
- **push it to GitHub** —— 代码、文件进远程仓库用 *push*,比 *upload* 更贴近 git 的实际操作。
- **, so that** —— 引出目的"以便……";*Therefore* 只引出结论"因此",不能表示目的。口语里也可以只说 *so*。
- **the same rules apply** —— 规则"适用、生效"用 *apply*(不及物),主语是规则本身;对应 *I can use the same rules*。

**我的原句:**

> ==Desensitize== the memory==, upload== it to GitHub. ==Therefore== the same rules still apply when I work on other computers.

- ⚠️ [搭配] ==Desensitize== → **Sanitize** —— 去掉敏感信息用 *sanitize* 或 *strip out sensitive details*;*desensitize* 是"使脱敏、使麻木"。
- ⚠️ [标点] ==, upload== → **and upload** —— 两个祈使句只用逗号连起来是逗号拼接,用 *and* 连接。
- ⚠️ [逻辑] ==Therefore== → **so that** —— *therefore* 引出结论,这里要表达的是目的。

<a id="r-bump-the-version"></a>
## 把版本号命名规则加进 CLAUDE.md:一般两段 主.次;热修复用三段;次版本可以直接改,改主版本前先问我。

- 日期:2026-10-15
- 关键词:bump the minor version · check with me before

**常用说法:**

> Add this version naming rule to CLAUDE.md: the version name has two parts, major.minor, like 2.1. For a hotfix, the pattern is major.minor.hotfix. You can change the minor number, but ask me before you change the major number.

**C1 说法:**

> Add this **versioning convention** to CLAUDE.md: a version name **normally has two parts**, major.minor (**e.g.** 2.1); for a hotfix, **use** major.minor.hotfix. **Feel free to bump the minor version**, but **check with me before bumping** the major one.

**差别:**

- **versioning convention** —— 软件里"版本号规则"的固定说法;*convention* 是团队约定俗成的做法,比 *rule* 更贴切。
- **normally has two parts** —— 版本号的各段叫 *parts* 或 *components*,比 *sections* 常见;*normally* 对应 *generally*,放在动词前更自然。
- **(e.g. 2.1)** —— 举例放括号里,一句话里就不用堆两个冒号;对应 *like 2.1*。
- **for a hotfix, use …** —— 直接给做法,主语和指代都不会出错;对应 *if some changes are for a hotfix, name it…*。
- **bump the minor version** —— *bump* = 把版本号加一,工程里的固定说法。例句:
  - *Bump the version to 2.2 before you merge.* 合并之前把版本号升到 2.2。
  - *The release script bumps the patch version automatically.* 发布脚本会自动把补丁版本号加一。
- **check with me before bumping** —— "做之前先问我一声",比 *confirm with me* 更口语;*before* 后面接动名词 *bumping*。

**我的原句:**

> Add this version naming convention to ==the CLAUDE.me==: generally, the version name consists of two ==sections==: major.minor, like 2.1; if some changes are for a hotfix, ==name it== in this pattern: major.minor.hotfix. Feel free to update the minor section, but please confirm with me before updating the major.

- ⚠️ [冠词] ==the CLAUDE.me== → **CLAUDE.md** —— 文件名前不加 *the*;*.me* 是笔误。
- ⚠️ [搭配] ==sections== → **parts** —— 版本号的各段一般叫 *parts* 或 *components*。
- ⚠️ [结构] ==name it== → **use major.minor.hotfix** —— 前面是复数 *changes*,*it* 指代对不上。

<a id="r-make-the-sentences-stick"></a>
## 调整自测流程:每句先选词、再打字,两步都对才算对,打字时可以切回选词看提示;复习时要把一条的所有句子都做完;网页和手机都改。

- 日期:2026-10-15
- 关键词:make the sentences stick · go through all of its sentences

**常用说法:**

> Let's change the self-test flow. For each sentence, the user does word-bank mode first and then typing mode. The sentence is correct only if both are correct. In typing mode, the user can go back to word-bank mode for help, so please keep the switch. The Show Answer button should work in both modes. I want the user to remember the sentences well.
>
> Also, when reviewing an item, the user should answer all of its sentences. Now it only asks one. Please check this.
>
> These two changes are for both the web app and the mobile app.

**C1 说法:**

> Let's **tweak** the self-test flow. For each sentence, the user should **start with** word-bank mode and then **move on to** typing mode, and a sentence counts as correct **only if both steps are** answered correctly. In typing mode, the user can switch back to word-bank mode **for a quick hint**, **so keep** the switch between the two modes. The Show Answer button should be available **in both modes**. The goal is to **make the sentences stick**.
>
> **Also**, when reviewing an item, the user should **go through all of its sentences** **— right now it only asks one**. Could you **double-check** this?
>
> Both changes **apply to** the web and mobile apps.

**差别:**

- **分三段** —— 第一段讲两步作答,第二段讲复习要做完所有句子,第三段说两边都改;对应原来一整段,读的人一眼看出有两个需求。
- **should / can** —— 需求里统一用 *should*(应该这样)和 *can*(允许这样);对应原句里混用的 *needs to*、*may*、*is available*。
- **tweak** —— 小幅调整,比 *change* 更准确地说明"只是改一点"。
- **start with … and then move on to …** —— 讲先后步骤的固定搭配;对应 *does … first and then …*。
- **only if both steps are** —— *only if* 强调"缺一不可";对应 *only if both are correct*,补上 *steps* 指代更清楚。
- **for a quick hint, so keep the switch** —— 先说为什么,再提要求,把中途补充的那句并进正文;对应 *for help, so please keep the switch*。
- **in both modes** —— "在某种模式下"用 *in*;对应 *work in both modes*。
- **make the sentences stick** —— *stick* = 记住、记牢,比说 *remember well* 更地道。例句:
  - *Writing it out by hand helps the new words stick.* 手写一遍,新词更容易记住。
  - *Short daily reviews make vocabulary stick.* 每天复习一会儿,单词才记得牢。
- **Also** —— 书面需求里比 *One more thing* 简洁,后者偏口语。
- **go through all of its sentences** —— *go through* = 逐一做一遍;对应 *answer all of its sentences*。
- **— right now it only asks one** —— 用破折号把现状补在要求后面,比另起一句 *Now it …* 紧凑。
- **double-check** —— 动词带连字符,直接接宾语;对应 *check this*。
- **apply to** —— "适用于"的固定搭配;对应 *are for*。

**我的原句:**

> Let's tweak the self-test flow a bit like this: for each sentence, ==user== needs to ==go with== the word-bank mode first, then the typing mode. Count the sentence as correct if both are answered correctly. The user may go back to word-bank mode from typing mode for a temporary ==assistant==. ==Show Answer button== is available ==for both modes==. The goal is to make the sentence ==sticky==. / One more thing: when reviewing an item, the user needs to cover all of the sentences too. Currently, it just asks for one of them. Please ==double check on== this. --> These two enhancements ==apply on== both web and mobile. … The switch between word-bank and typing mode should be kept.

- ❌ [冠词] ==user== → **the user** —— 特指使用者,要加冠词。
- ⚠️ [搭配] ==go with== → **start with** —— *go with* 多指"选择、采用",讲先后步骤用 *start with*。
- ❌ [词形] ==assistant== → **assistance / a hint** —— *assistant* 是"助手"(人),这里要的是"帮助"。
- ❌ [冠词] ==Show Answer button== → **The Show Answer button** —— 句首缺 *The*。
- ⚠️ [介词] ==for both modes== → **in both modes** —— "在某种模式下"用 *in*。
- ⚠️ [搭配] ==sticky== → **make the sentences stick** —— 说"记得牢"用动词 *stick* 更地道,指所有句子用复数。
- ⚠️ [搭配] ==double check on== → **double-check** —— 动词带连字符,不带 *on*。
- ❌ [介词] ==apply on== → **apply to** —— "适用于"是 *apply to*。

<a id="r-lets-not-locally"></a>
## 截图先别推了,README 也不改了;截图留在本地,加进 .gitignore 就行。

- 日期:2026-10-15
- 关键词:let's not · keep … locally, though

**常用说法:**

> No, don't push the screenshots, and don't update the README. But keep the screenshots on my computer and add them to .gitignore.

**C1 说法:**

> No, **let's not** push the screenshots, and **let's drop** the README update. We can keep the screenshots **locally, though** **— just** add them to .gitignore.

**差别:**

- **let's not** —— 「咱们别…」,*let's* 的否定直接在后面加 *not*,语气是商量;对应 *don't*(命令口气)。规则见 [let's not vs don't](#lets-not)。
- **drop the README update** —— *drop* = 放弃一个计划或任务,工作里很常用;*the README update* 是顺的名词短语。对应 *don't update the README*。例句:
  - *We dropped the feature to ship on time.* 为了按时发布,我们砍掉了这个功能。
  - *Let's drop this for now and revisit it next sprint.* 这个先不做了,下个迭代再看。
- **locally** —— 「在本地」用副词 *locally*;对应 *on my computer*。
- **, though** —— 放在句末表示轻微转折(「不过」),比句首的 *But* 柔和。
- **— just add them to .gitignore** —— 破折号后面补上具体做法;*just* = 「这样就行」,让要求听起来很轻。

**我的原句:**

> no - ==let's do not push== the screenshots and ==stop the update README task==. But we can keep these screenshots ==at local== and add them to .gitignore

- ❌ [动词] ==let's do not push== → **let's not push** —— *let's* 的否定直接加 *not*,不用 *do*。
- ⚠️ [结构] ==stop the update README task== → **drop the README update** —— 把动词 *update* 塞进名词串里,读起来卡。
- ❌ [直译] ==at local== → **locally** —— 「在本地」的直译;英文用副词 *locally*,或说 *keep them local*。

<a id="r-heard-of-the-curve"></a>
## 我听说过艾宾浩斯遗忘曲线。能给每个条目都显示这条曲线吗?

- 日期:2026-10-15
- 关键词:I've heard of · show it for each item

**常用说法:**

> I know the Ebbinghaus forgetting curve. Can we draw this curve for every item?

**C1 说法:**

> **I've heard of** the Ebbinghaus forgetting curve. **Could we** show it **for each item**?

**差别:**

- **I've heard of** —— 「听说过、知道有这么个东西」,用现在完成时讲经历;对应 *I know*(听起来像很熟)。*hear of* = 知道它存在,*hear about* = 听到关于它的具体内容。例句:
  - *Have you heard of FSRS?* 你听说过 FSRS 吗?
  - *I've never heard of that library.* 我从没听说过那个库。
- **Could we …?** —— 提议、请求时比 *Can we* 更客气一点。
- **show it for each item** —— *show* 本身就是「给人看」,不用再加 *visually*;*for each item* 强调一个一个都有。对应 *draw this curve for every item*。
- **the Ebbinghaus forgetting curve** —— 是通用名词,小写,前面加 *the*;不写成专有名词 *Forgetting Curve*。

**我的原句:**

> I ==heard about== this: 艾宾浩斯遗忘曲线(Forgetting Curve). Can we show the curve visually for each item?

- ⚠️ [动词] ==heard about== → **'ve heard of** —— 讲「听说过」的经历用现在完成时;*hear of* 指知道它存在。

<a id="r-feel-free-for-testing"></a>
## 手机已经连上了,你可以用它来测试。

- 日期:2026-10-15
- 关键词:feel free to · for testing

**常用说法:**

> My phone is connected. You can use it to test.

**C1 说法:**

> My phone is connected **— feel free to** use it **for testing**.

**差别:**

- **— feel free to** —— 「尽管、随便用」,让对方放心去做,语气友好;对应 *You can*。用破折号把两句接成一句。例句:
  - *Feel free to reach out if anything's unclear.* 有不清楚的地方随时找我。
  - *Feel free to push it to dev once it's done.* 弄好了直接推到 dev 就行。
- **for testing** —— 「用来测试」说的是这件事,用动名词 *testing*,或者说 *to test*;对应 *to test*。*for test* 不成立。

**我的原句:**

> The phone (S10+) is ==conneted== - you can use it ==for test==

- ❌ [词形] ==conneted== → **connected** —— 拼写漏了一个 c。
- ❌ [动词] ==for test== → **for testing** —— 说「测试」这件事用动名词,或者写成 *to test*。

<a id="r-by-simple-i-dont-mean"></a>
## 这一条只留第 1 点;「简单」不是少写字,而是讲得清楚,一条只练一个点。

- 日期:2026-10-18
- 关键词:By "X", I don't mean … · focus on

**常用说法:**

> Only keep point 1 in this entry, so it is about pronouns. And create two more entries for 4 and 5. My student is only 10, so make every entry simple. Simple doesn't mean fewer words. It means clear and easy to follow. One entry is for one point. Is this clear?

**C1 说法:**

> Keep only point 1 (it's / its / their) in this entry, **so that it focuses on** pronouns. Then create two more entries for points 4 and 5. **Since** my student is only 10, let's keep every entry as simple as possible. **By "simple", I don't mean** cutting the word count; **I mean** making each entry **as clear and easy to follow as possible**. Each entry should **focus on just one point**. **Is that clear?**

**差别:**

- **so that it focuses on** —— 「好让它专门讲……」,说目的;对应 *so it is about*。*focus on* 比 *be about* 更明确地表示「只讲这一个」。
- **Since …** —— 先把原因放在句首,再说要做什么;对应 *…, so …*。
- **By "simple", I don't mean …; I mean …** —— 先排除对方可能的误解,再说真正的意思,专门用来澄清一个词;对应 *Simple doesn't mean … It means …*。例句:
  - *By "soon", I don't mean today; I mean this week.* 我说的「尽快」不是今天,是这周内。
  - *By "short", I mean under 200 words.* 我说的「短」是指 200 词以内。
- **as clear and easy to follow as possible** —— 两个要求合进一个 *as … as possible*,不用说两次 *make*;对应 *clear and easy to follow*。
- **Each entry should focus on just one point.** —— 用 *should* 把它说成一条规则;对应 *One entry is for one point*。
- **Is that clear?** —— *that* 指刚刚说完的那些话;对应 *Is this clear?*

**我的原句:**

> Only keep point 1 (it's / its / their) for this item. ==Makes== it ==dedicated for== the pronoun ==praticing==. And create two other items for 4 and 5. Note: Since the student is just a ==10 years old kid==, let's make every entry as simple as possible. ==With respect to== "Simple", it doesn't mean ==we are to reduce== the word count for each item==, instead,== we want to make it as clear as possible to make it easy to follow. And one item is just for practicing one point. Is this clear?

- ❌ [结构] ==Makes== → **so that it focuses on …** —— 这是没有主语的半句话;说目的,用 *so that* 接一个完整的句子。
- ⚠️ [介词] ==dedicated for== → **dedicated to** —— 标准搭配是 *dedicated to*(前一天也写过一次 *dedicated for*)。
- ❌ [词形] ==praticing== → **practicing** —— 漏了一个 c。
- ❌ [词形] ==10 years old kid== → **10-year-old kid** —— 放在名词前面要用连字符连起来,*year* 不加 s。
- ⚠️ [语气] ==With respect to== → **By "simple", I don't mean …** —— *with respect to* 是公文腔,口语里澄清一个词用 *By "X", I mean …*。
- ⚠️ [动词] ==we are to reduce== → **cutting** —— *be to do* 是「按规定要做」,用在这里太正式。
- ❌ [标点] ==, instead,== → **; instead,** —— 逗号连接了两个完整的句子,改成分号或断成两句。

<a id="r-already-among-the-candidates"></a>
## 1 和 2 照做,3 先不做;选词时,如果下一个正确的词已经在候选里,就让它留在原来的位置,好点得更快。

- 日期:2026-10-19
- 关键词:If X is already among …, I'd like it to stay … · tap through

**常用说法:**

> Please do 1 and 2, and don't do 3 yet. I have another request for the word bank: now we show 4 words each time. If the next correct word is already shown, please keep it in the same place, so users can tap faster. Tell me if something is not clear.

**C1 说法:**

> **Go ahead with** 1 and 2, and **skip 3 for now**. **One more request for** the word bank: we show four candidates for the next word each time. **If the correct next word is already among the current candidates, I'd like it to stay in the same position**, so users can **tap through** faster. **Let me know if anything's unclear.**

**差别:**

- **Go ahead with …** —— 「那就照……做吧」,同意对方的方案时用;对应 *Please do*。
- **skip 3 for now** —— 「3 先跳过」,*for now* 表示以后可能再做;对应 *don't do 3 yet*。
- **One more request for …:** —— 用冒号引出要说的事,比完整句子更利落;对应 *I have another request for*。
- **If … is already among the current candidates** —— 先把条件说清楚,*be among* 是「在……之中」;对应 *is already shown*。
- **I'd like it to stay in the same position** —— *would like sth to do*,说希望它怎样;对应 *please keep it in the same place*。
- **tap through** —— 「一路点下去」,比 *tap* 多了连续点完的意思;对应 *tap faster*。
- **Let me know if anything's unclear.** —— 结尾的固定客气话;对应 *Tell me if something is not clear*。

**我的原句:**

> Go ahead with 1 and 2, skip 3 for now. Another improvement request on the word-bank mode: currently, we show 4 candidates for the next word each time. I'd like to keep ==the candidate's position if that candidate shows up now== and it will be the right one for the next word. That way, ==user== can tap on words faster. -- Let me know if this is unclear to you

- ⚠️ [逻辑] ==the candidate's position if that candidate shows up now== → **If the correct next word is already among the current candidates, I'd like it to stay in the same position** —— 先说「哪个词」,再说要求;原句读到句尾才知道指的是哪个候选词,*shows up now* 也说不清是哪一组。
- ❌ [冠词] ==user== → **users** / **the user** —— 可数名词单数前要有冠词,泛指用户用复数最自然。

<a id="r-grow-and-follow-the-cursor"></a>
## 效果很好。还有一点:内容放不下时输入框要变大;在安卓上,用户一直打字时,框要跟着光标滚——一般在末尾,也可能在中间。

- 日期:2026-10-19
- 关键词:no longer fits · scroll to follow the cursor · if that's where …

**常用说法:**

> It works well. Another thing: the input box should become bigger when the text is too long. And on Android, when the user keeps typing, the box should scroll with the cursor. The cursor is usually at the end, but it can be in the middle if the user types there.

**C1 说法:**

> It works great. **One more thing:** the input box should **grow** when the text **no longer fits**. And on Android, **as the user keeps typing**, the box should **scroll to follow the cursor** — **usually at the end, but sometimes in the middle if that's where they're typing**.

**差别:**

- **One more thing:** —— 补充一个问题时的固定开头,语气轻;对应 *Another thing*。
- **grow** —— 一个词说「变大」,比 *become bigger* 简洁;对应 *become bigger*。
- **no longer fits** —— 「已经放不下了」,*no longer* 带出「原来放得下、现在不行」的变化;对应 *is too long*。
- **as the user keeps typing** —— *as* 表示「随着……」,两件事同时进行;对应 *when the user keeps typing*。
- **scroll to follow the cursor** —— *follow* 说出「跟着走」;界面上的输入位置叫 *cursor*;对应 *scroll with the cursor*。
- **— usually at the end, but sometimes in the middle if that's where they're typing** —— 用破折号接一个补充说明,不用另起一句;*if that's where …* 是「如果……就在那里」;对应 *The cursor is usually at the end, but it can be in the middle if the user types there*。

**我的原句:**

> It works great. Another issue: the input box should expand its size if the typed content can't fit in. And on Android, if ==user== keeps typing, the input box should auto-scroll to ==follow the focus==, which usually stays at the bottom, but it could be in the middle too if ==user== types there.

- ❌ [冠词] ==user== → **the user** —— 可数名词单数前要有冠词(两处都是)。
- ⚠️ [搭配] ==follow the focus== → **follow the cursor** —— *focus* 指哪个控件被选中,输入位置叫 *cursor*。

<a id="r-the-only-ones-that"></a>
## 只有这三种通知会触发这个 bug 吗?

- 日期:2026-10-20
- 关键词:the only ones that … · Is it only … that …?

**常用说法:**

> Do only these three types of notifications trigger the bug?

**C1 说法:**

> Are these three notification types **the only ones that** trigger the bug?
> **Is it only** these three notification types **that** trigger the bug?

**差别:**

- **notification types** —— 名词修饰名词,比 *types of notifications* 更紧凑;对应 *types of notifications*。
- **the only ones that …** —— 把「只有」放进 *the only ones* 里,问句结构简单清楚;对应 *Do only these … trigger*。
- **Is it only … that …?** —— 强调句,把「只有这三种」推到前面,语气是「真的就只有这几种吗?」;对应 *Do only these …*。

**我的原句:**

> Only these 3 ==types notification== ==triggers== the bug?

- ❌ [结构] ==types notification== → **types of notifications** / **notification types** —— *type* 后面要用 *of* 连接,或者把 *notification* 放前面当修饰语。
- ❌ [动词] ==triggers== → **trigger** —— 主语核心词是复数 *types*,动词用原形。

<a id="r-set-up-as-a-new-user"></a>
## 把她设成新用户,预置一条 Note;再按她的情况(雅思 7 分)建四个分类,每类一两条,而且都能自测。

- 日期:2026-10-23
- 关键词:go ahead and pick · aiming for · support self-testing

**常用说法:**

> Please make my friend a new user and add this as a preset entry: "…" Put it in a category called Note. Please also make four more categories for her. She is an advanced learner and wants to get 7.0 in IELTS, so you can choose names that are good for her. Add one or two entries to each. Put my message in Note, with my original and your better versions. I want her to see her own list in that folder on the web app. All entries should be testable, the same as the other ones.

**C1 说法:**

> **Set up** my friend **as a new user**, with this as a preset entry: "…" Put it in a category called Note. Also, please create **four more** preset categories for her — she's an advanced learner **aiming for IELTS 7.0**, so **go ahead and pick** names **that make sense for her**, with one or two entries in each. **As before**, put my message in Note, with my original and your **improved versions**. **The goal is for her to** browse her own list under that directory on the web app. And every entry should **support self-testing, just like the others**.

**差别:**

- **Set up … as a new user** —— 「把……设成新用户」,开账号、建目录都能用;对应 *make my friend a new user*。
- **four more** —— 「再加四个」,*more* 放在数字后面;对应 *four more*(常用说法里已经对了),而不是 *another four*。
- **— she's …** —— 用破折号插进对她的介绍,说明为什么这样选分类;对应 *She is …*(另起一句)。
- **aiming for IELTS 7.0** —— 「目标是雅思 7 分」,*aim for* 接目标分数;对应 *wants to get 7.0 in IELTS*。
- **go ahead and pick** —— 「你直接选就行」,把决定权交给对方;对应 *you can choose*。
- **that make sense for her** —— 「对她来说合适的」;对应 *that are good for her*。
- **As before** —— 「和之前一样」,提醒对方沿用刚才的做法;对应 *Put my message …*(没说明是沿用)。
- **improved versions** —— 说改好的句子用 *improved*,*optimized* 多用于程序和流程;对应 *better versions*。
- **The goal is for her to …** —— 「目的是让她能……」,*for sb to do* 把目的和人连在一起;对应 *I want her to …*。
- **support self-testing, just like the others** —— 「和其他条目一样能自测」,*support* 说功能上支持;对应 *should be testable, the same as the other ones*。

**我的原句:**

> Create a new entry for my friend as a new user. Add this as ==preset item==: "…" / Put it in a category: Note. BTW, please also create ==another 4 preset categories== for her, who is an ==andvanced== English ==leanrer== ==targeting to== IELTS 7.0. You can ==go ahead to pick== any category ==names make sense== to her. Add one or two entries for each of these categories. Again, put mine into the Note category, with my original sentence and the ones that you optimized. ==The goal is she can== browse the vocab list under that directory on the web app. All of the entries in these categories should ==be able for self-test== ==as the other general ones==.

- ❌ [冠词] ==preset item== → **a preset item** —— 可数名词单数前要有冠词。
- ⚠️ [结构] ==another 4 preset categories== → **four more preset categories** —— *another* 后面一般接单数名词。
- ❌ [词形] ==andvanced== → **advanced** —— 拼写错误。
- ❌ [词形] ==leanrer== → **learner** —— 拼写错误。
- ❌ [动词] ==targeting to== → **targeting** / **aiming for** —— *target* 是及物动词,后面不加 *to*。
- ⚠️ [搭配] ==go ahead to pick== → **go ahead and pick** —— *go ahead* 后面接 *and* + 动词原形。
- ❌ [结构] ==names make sense== → **names that make sense** —— 定语从句里 *that* 做主语,不能省。
- ⚠️ [结构] ==The goal is she can== → **The goal is for her to** —— 书面里 *that* 不宜省,或者用 *for sb to do*。
- ❌ [结构] ==be able for self-test== → **support self-testing** —— *be able* 后面要接 *to do*,而且条目自己「能」做不了什么。
- ⚠️ [介词] ==as the other general ones== → **like the others** —— 表示「和……一样」用 *like*。

<a id="r-one-or-two-each"></a>
## 给这三个短语建一个新条目,每个配一两句例句;句子照旧要短,好让我专注在短语的用法上。

- 日期:2026-10-23
- 关键词:one or two … each · how the phrases are used

**常用说法:**

> Please add a new entry for these phrases: tend toward, lean toward and focus mostly on. Add one or two sample sentences for each one. Like last time, make the sentences short, so I can focus on the usage of the phrases. Thanks.

**C1 说法:**

> **Could you** add a new entry for *tend toward*, *lean toward* and *focus mostly on*, **with one or two sample sentences each**? **As before**, keep the sentences short so I can focus on **how the phrases are used**. Thanks.

**差别:**

- **Could you …?** —— 用问句提请求,比祈使句 *Please add* 更客气;对应 *Please add*。
- **with one or two sample sentences each** —— 把要求接在同一句后面,*each* 放句末表示「每个各……」;对应 *Add one or two sample sentences for each one*(另起一句)。
- **As before** —— 「和之前一样」,比 *Like last time* 更利落;对应 *Like last time*。
- **how the phrases are used** —— 用一个从句说「用法」,比名词堆叠更自然;对应 *the usage of the phrases*。

**我的原句:**

> tend toward / lean toward / focus mostly -- I'd like to have a new entry for these phrases. Add ==one sentence or two sample sentences== for each. Again, please try to make the sentences short so I can focus on ==the phrases usage==. Thanks.

- ⚠️ [结构] ==one sentence or two sample sentences== → **one or two sample sentences** —— *sentence* 重复了,两边结构也不一致。
- ❌ [词形] ==the phrases usage== → **how the phrases are used** —— 缺所有格撇号(*the phrases' usage*);用从句说更自然。

<a id="r-get-through"></a>
## 给网页和 App 加个功能:长按一句会弹出菜单,我们想在里面加一项,把这句从所有阶段的考试里去掉。有些条目多达十来句例句,做完要很久。

- 日期:2026-10-23
- 关键词:both the web and mobile apps · get through

**常用说法:**

> Please add a feature to the web app and the mobile app. When you press a sentence for a long time, a menu appears. We want to add an item to the menu that removes the sentence from tests in all stages. Some entries have about twelve sample sentences, so it takes a long time to finish them.

**C1 说法:**

> Please add a new feature to **both the web and mobile apps**. **When you long-press a sentence, a menu pops up**; we'd like a new item in it **that removes the sentence from tests in all stages**. Some entries have **as many as a dozen** sample sentences, **which take a long time to get through**.

**差别:**

- **both the web and mobile apps** —— *both* 后面直接并列两个东西,不加 *of*;对应 *the web app and the mobile app*。
- **When you long-press a sentence, a menu pops up** —— *long-press* 是界面操作的固定说法;*pop up* 说菜单弹出;对应 *press a sentence for a long time, a menu appears*。
- **that removes the sentence from tests in all stages** —— 用定语从句说清这一项做什么,*the sentence* 不用 *it*,免得指代不清;对应 *an item … that removes the sentence*。
- **as many as a dozen** —— 「多达十来个」,强调数量多;*a dozen* 后面不加 *of*;对应 *about twelve*。
- **which take a long time to get through** —— *get through* 是「一项项做完」;对应 *so it takes a long time to finish them*。

**我的原句:**

> Add a new feature for ==both of the web and mobile apps==: ==When long pressing on a sentence, an action list will pop up== now. We want to add a new item to that list for removing it from tests in all phases. That is because I found ==some entry== contains as many as ==a dozen of sample sentences==, which ==takes long== to finish.

- ⚠️ [结构] ==both of the web and mobile apps== → **both the web and mobile apps** —— 并列两个东西时 *both* 后面不加 *of*。
- ⚠️ [逻辑] ==When long pressing on a sentence, an action list will pop up== → **When you long-press a sentence, a menu pops up** —— *long pressing* 的主语是用户,不是 *action list*(悬垂修饰)。
- ❌ [冠词] ==some entry== → **some entries** —— 「有些条目」用复数。
- ❌ [结构] ==a dozen of sample sentences== → **a dozen sample sentences** —— *a dozen* 直接接名词。
- ⚠️ [结构] ==takes long== → **take a long time** —— 肯定句里 *long* 单独作副词不自然;主语是 *sentences*,动词用复数。

<a id="r-move-along-with-it"></a>
## 横滑切换 tab 的功能挺好用,不过有一点可以改进:用户滑动时,底部 tab 的指示器应该跟着动。

- 日期:2026-10-23
- 关键词:one thing could be better · move along with it

**常用说法:**

> The feature for swiping to switch tabs works well, but it can be better: when the user swipes, the bottom tab indicator should move too.

**C1 说法:**

> The **tab-swiping feature** works well, but **one thing could be better**: **as the user swipes**, the bottom tab indicator should **move along with it**.

**差别:**

- **tab-swiping feature** —— 用一个复合词放在 *feature* 前面,短而清楚;对应 *The feature for swiping to switch tabs*。
- **one thing could be better** —— 先肯定再提一点改进,*could* 让语气更客气;对应 *it can be better*。
- **as the user swipes** —— *as* 表示「一边……一边……」,两件事同时发生;对应 *when the user swipes*。
- **move along with it** —— 「跟着一起动」,*along with* 说出「同步」;对应 *move too*。

**我的原句:**

> ==The swipe to switch tabs feature== works fine but it can be improved ==as follow==: when ==user== is swiping, the bottom tab indicator should be moved accordingly

- ⚠️ [结构] ==The swipe to switch tabs feature== → **the tab-swiping feature** —— 一长串词放在名词前当修饰语,读起来吃力。
- ❌ [词形] ==as follow== → **as follows** —— 固定说法,*follows* 要加 *s*。
- ❌ [冠词] ==user== → **the user** —— 单数可数名词前要有冠词。

<a id="r-comma-splice"></a>
## comma splice:怎么一眼判断

- 日期:2026-10-23
- 关键词:comma splice

**含义:** comma splice(逗号粘连):两个**独立分句**(各有主语和谓语、能单独成句)只用一个逗号连起来。

**快速判断:** 看逗号两边,如果两边都能单独成句,又没有 *and*、*but*、*so*、*though* 这类连词,就是 comma splice。

**怎么改:** 换成句号或分号,或者加一个连词。

- 是:*Let's add a new entry for this, each phrase has one sample sentence.* —— 逗号后面有主语 *each phrase* 和谓语 *has*,能单独成句。
- 改:*Let's add a new entry for this; each phrase has one sample sentence.* 或 *…, and each phrase gets one sample sentence.*
- 不是:*Let's add a new entry for this, with one sample sentence each.* —— 逗号后面是介词短语,不能单独成句。
- 不是:*…, though I know …* —— *though* 引出的是从句,有连词就不算。

---

<a id="r-so-they-dont-get-too-wide"></a>
## 可以把标签加宽，让文字完整显示，但还是要设一个最大宽度，免得太宽。

- 日期:2026-10-24
- 关键词:widen · so they don't get too wide

**常用说法:**

> We can make the tabs wider so that the text is shown completely, but we still need a width limit so that they don't become too wide.

**C1 说法:**

> We **could widen** the tabs **so the full label shows**, but they still need **a max width** **so they don't get too wide**.

**差别:**

- **could widen** —— *widen* 一个词说「加宽」;*could* 是提建议的口气，比 *can* 客气;对应 *can make the tabs wider*。
- **so the full label shows** —— *the full label* 比 *the text completely* 更具体;*show* 在这里作不及物动词，表示「显示出来」;对应 *to show the text completely*。
- **a max width** —— 界面里「最大宽度」的常用说法;对应 *a width limit*。
- **so they don't get too wide** —— 用 *so* 从句说出「免得……」,*they* 指 tabs;对应 *to avoid it getting too wide*。

**我的原句:**

> we can make the tabs wider to show the text completely. But a width limit is still necessary to avoid ==it getting too wide==.

- ⚠️ [逻辑] ==it getting too wide== → **so they don't get too wide** —— 指的是 *tabs*,复数要用 *they / them*;*avoid sb doing* 能懂，但 *so they don't …* 或 *keep them from …* 更常见。

<a id="r-narrows-the-choices-down-to-one"></a>
## 我打的字母把候选词筛到只剩一个时，按空格就应该选中它。

- 日期:2026-10-25
- 关键词:narrow the choices down to one · pressing Space should

**常用说法:**

> When the user's typed content has narrowed down the candidates to a single one, pressing Space should select the remaining option.

**C1 说法:**

> **Once** what I've typed **narrows the choices down to one**, **pressing Space** should **select it**.

**差别:**

- **Once** —— 「一旦……」,强调到了这一步就该发生;对应 *When*。
- **narrows the choices down to one** —— *narrow sth down to …* 是「筛到只剩……」的固定说法,*one* 比 *a single one* 简洁;对应 *has narrowed down the candidates to a single one*。
- **pressing Space** —— 动名词作主语，按键名首字母大写;对应 *press space*。
- **select it** —— 前面已经说了只剩一个，用 *it* 指它就行;对应 *pick up the remaining option*。

**我的原句:**

> When the user's typed content has narrowed down the candidates to a single one, ==press space should== ==pick up== the remaining option.

- ❌ [结构] ==press space should== → **pressing Space should** —— 动词原形不能直接作主语，要用动名词;也可以让 Space 作主语:*Space should …*。
- ⚠️ [搭配] ==pick up== → **pick / select** —— *pick up* 多指「捡起、接人、学会」;从几个选项里挑一个，用 *pick* 或 *select*。

<a id="r-in-production-does-that-mean"></a>
## 部署之后，如果在生产环境能调通接口，是不是就说明一切正常了？

- 日期:2026-10-26
- 关键词:in production · So if …, does that mean …

**常用说法:**

> If I can call the endpoints on the production environment after the deployment, does it mean everything is working well?

**C1 说法:**

> **So if** I can call the endpoints **in production** after the deployment, **does that mean** **everything's working**?

**差别:**

- **So if** —— 用来确认对方的意思:「那照这么说，如果……」;对应 *If*。
- **in production** —— 说「在某个环境里」用 *in*(*in staging*、*in the test environment*);说服务器才用 *on*(*on the server*);对应 *on the production environment*。
- **does that mean** —— *that* 指前面整个条件;对应 *does it mean*。
- **everything's working** —— 说「一切正常」不用加 *well*;对应 *everything is working well*。

**我的原句:**

> Does it mean if I can call the endpoints ==on production env== after the deployment, ==that'll mean== everything is working well? Am I right?

- ❌ [结构] ==that'll mean== → **If …, does that mean …?** —— 开头的 *Does it mean* 和后面的 *that'll mean* 重复了，只留一个。
- ⚠️ [冠词] ==on production env== → **in production** —— 少了 *the*;而且说环境时，*in production* 最常见。

<a id="r-iron-out-known-mistakes"></a>
## 我想稍微调整一下：每周五，你能根据我最近学的单词和短语给我布置一些翻译作业吗？目标是改掉我已经知道的错误，而不是找新的错误。你觉得呢？其实我在做一个帮人提高写作的产品，用户可能会像我这样用它：通过这些 app 从自己的错误中学习。所以我想听听你的建议，怎样才能进步得更快，不只是我，也包括我的用户。

- 日期:2026-10-26
- 关键词:iron out · the way I've been using · rapid progress

**常用说法:**

> I'd like to change it a little: every Friday, can you give me some translation homework about the words and phrases I studied recently? The goal is to fix the mistakes I already know, not to find new ones. What do you think? Actually, I'm making a product that helps people write better. Users may use it like I use this one, learning from my mistakes with the apps. So I want your advice on how to make big progress, for me and also for my users.

**C1 说法:**

> I'd like to **tweak** it a bit: every Friday, could you give me some translation homework **based on** the words and phrases I've studied recently? The goal is to **iron out** the mistakes I already know about, not to find new ones. What do you think? Actually, I'm building a product to help people improve their writing. Users may use it **the way I've been using this setup** — **learning from my mistakes** through the apps. So I'd like your advice on how to make **rapid progress**, **not only for me but for my users too**.

**差别:**

- **tweak** —— 小幅调整;对应 *change it a little*。
- **based on** —— 「以……为依据」;对应 *about*。
- **iron out** —— 把问题、错误逐个理顺、消除;对应 *fix*。
- **the way I've been using this setup** —— *the way* + 从句表示「像……那样」,现在完成进行时说明一直在这么用;对应 *like I use this one*。
- **learning from my mistakes** —— 「从错误中学习」的固定说法;对应 *learning from my mistakes with the apps*。
- **rapid progress** —— 「快速的进步」;对应 *big progress*。
- **not only for me but for my users too** —— 「对谁来说」用 *for*;对应 *for me and also for my users*。

**我的原句:**

> I'd like to change it a bit: Every Friday, you can assign ==a translation homework== to me, which can be ==related with== the words and phrases I studied recently. The goal is to ==resolve== all of the known mistakes but not the unknown ones. What do you think? -- Actually, I am working on a product for improving users' writing skills. They may use the app ==like how I have been doing here== and how I've been ==studying from the mistakes== with the apps. So I want to get some advice from you to make ==dominant progress== not only ==on me== but also on my users.

- ❌ [冠词] ==a translation homework== → **some translation homework** —— *homework* 不可数，不加 *a*。
- ❌ [介词] ==related with== → **related to** —— 固定搭配。
- ⚠️ [搭配] ==resolve== → **fix / iron out** —— *resolve* 多搭配 *issue*、*conflict*。
- ❌ [结构] ==like how I have been doing here== → **the way I've been using it here** —— *doing* 缺宾语,*like how* 太松散。
- ⚠️ [直译] ==studying from the mistakes== → **learning from my mistakes** —— 固定说法。
- ❌ [搭配] ==dominant progress== → **significant / rapid progress** —— *dominant* 是「占主导地位的」,不修饰 *progress*。
- ⚠️ [介词] ==on me== → **for me** —— 「对谁来说」用 *for*。


<a id="r-revisit-them-later"></a>
## 三套设计都不错，先都留着，以后可能还会再看。先用方案 B;学生端把缩放选项从顶栏挪到设置里，其他都挺好。以后可能要拿不同版本做 A/B 测试。

- 日期:2026-10-27
- 关键词:revisit them later · run A/B tests

**常用说法:**

> All three versions look good. Keep them all for now; we may come back to them later. Let's go with Option B first. Move the zoom options from the top bar to Settings on the student side. Everything else looks good. We may want different versions for A/B testing later.

**C1 说法:**

> **All three** look good — let's keep them for now, **since we may revisit them later**. We'll go with Option B first. **On the student side**, move the zoom options **from the top bar into Settings**; **everything else** looks good. **Later on**, we may want to **run A/B tests** with different versions.

**差别:**

- **All three** —— 前面说过是设计稿，直接说 *All three* 就够;对应 *All of these three versions*。
- **since we may revisit them later** —— 用 *since* 把「先留着」和原因连成一句;*revisit* 是「回头再看、重新考虑」;对应 *We may come back to these designs later*。
- **On the student side** —— 放在句首先交代范围;*student* 作定语，不用所有格、不大写;对应 *on the Student's side*。
- **from the top bar into Settings** —— *top bar* 前加 *the*;*into* 强调放进设置页里面;对应 *from topbar to Settings*。
- **everything else** —— 明确指「其余所有部分」;对应 *Others*。
- **run A/B tests** —— 「做 A/B 测试」的常用搭配，可数的 *tests*;对应 *have different versions for A/B testings*。

**我的原句:**

> ==All of these three versions== look good. Keep them all for now. We may come back to these designs later. Let's go with Option B first. Move the ==zooming options== from ==topbar== to Settings on ==the Student's side==. ==Others== look good. We may want to have different versions for ==A/B testings== later.

- ⚠️ [结构] ==All of these three versions== → **All three versions** —— *all of these three* 不自然;要么 *all three*,要么 *all of these*。
- ⚠️ [搭配] ==zooming options== → **zoom options** —— 界面上的名称用名词 *zoom* 作定语。
- ❌ [冠词] ==topbar== → **the top bar** —— 可数名词单数要加冠词，一般写成两个词。
- ⚠️ [词形] ==the Student's side== → **the student side** —— 这里 *student* 是定语，不用所有格，也不大写。
- ⚠️ [搭配] ==Others== → **Everything else** —— *Others* 指代不清;说「其他都好」用 *everything else*。
- ❌ [冠词] ==A/B testings== → **A/B testing** —— *testing* 在这里不可数;也可以说 *run A/B tests*。

---

<a id="r-hold-off-considering"></a>
## 句子:hold off on / considering switching / put on hold

- 日期:2026-10-27
- 关键词:put on hold · weigh a switch

**改完是:**

> Let's hold off on this task. The PM team is considering switching to another tool.
> 这个任务先缓一缓。PM 团队在考虑换成另一个工具。

**C1 写法:**

> Let's put this on hold for now. The PM team is weighing a switch to another tool.
> 这个先暂停。PM 团队正在权衡要不要换成另一个工具。

**涉及的搭配:**

- **hold off on sth** —— 推迟、先不做某事。
- **put sth on hold** —— 把某事暂停、搁置,说任务时很常用。
- **consider doing sth** —— 考虑做某事(不是 *consider to do*)。
- **weigh a switch to sth** —— 权衡要不要换成某物,比 *consider* 更具体。

**我的原句:**

> Let's hold off on this task. ==PM team== is ==considering to switch== to another tool.

- ✅ **Let's hold off on this task.** —— 原句就很好。
- ❌ [冠词] ==PM team== → **The PM team** —— 指的是自己公司那个具体的团队,要加定冠词。(第二次漏 the)
- ❌ [动词] ==considering to switch== → **considering switching** —— *consider* 后面只接 -ing,不接 to do。同类只接 -ing 的动词:suggest、avoid、finish、mind、keep。

---

<a id="r-share-on-wechat"></a>
## 句子:share sth on WeChat / share sth with sb / settle on

- 日期:2026-10-27
- 关键词:share sth on WeChat · settle on

**改完是:**

> I was struggling to choose between "share sth on WeChat" and "share sth to WeChat". I think "share something with somebody" is a correct pattern, but when it comes to describing sharing something on a platform, I wasn't that confident about it and finally settled on "share … on WeChat".

**C1 写法:**

> I couldn't decide between "share sth on WeChat" and "share sth to WeChat". I know we say "share something with someone", but I wasn't sure which preposition goes with a platform, so I went with "on".
> 我拿不准该用 share on WeChat 还是 share to WeChat。我知道「和某人分享」是 share with,但不确定平台该搭配哪个介词,最后选了 on。

**介词怎么选:**

- **share sth on WeChat** —— 在某个平台上分享;说平台用 *on*(on Facebook、on Moments)。写句子用这个。
- **Share to WeChat** —— App 分享按钮上的常见文字,带「发送到」的方向感;做按钮可以,写句子用 *on* 更自然。
- **share sth via WeChat** —— 通过微信分享,强调渠道。
- **share sth with sb** —— 和某人分享,不是 *share sth to sb*。

**涉及的搭配:**

- **choose / decide between A and B** —— 在 A 和 B 之间选。
- **settle on sth** —— 考虑之后选定;**go with sth** 更口语。
- **be confident about sth** —— 对某事有把握。

**我的原句:**

> I was struggling to ==find== the correct ==prepsition== ==between== "share sth. on WeChat" and "share sth. to WeChat". I think "==share something to somebody==" ==should a correct pattern==, but when it comes to ==describing about== sharing something on ==some platform==, I wasn't that ==confident with== it and finally ==came up with== "share .. on WeChat".

- ❌ [词形] ==prepsition== → **preposition** —— 拼写错误。
- ⚠️ [逻辑] ==find … between A and B== → **choose between A and B** —— 二选一用 *choose between*。
- ❌ [结构] ==should a correct pattern== → **is a correct pattern** —— 少了 *be*;陈述判断直接用 *is*。
- ❌ [搭配] ==share something to somebody== → **share something with somebody** —— 和人分享用 *with*。
- ❌ [动词] ==describing about== → **describing** —— *describe* 直接跟宾语,不加 *about*。
- ⚠️ [冠词] ==some platform== → **a platform** —— 泛指「一个平台」用 *a*。
- ⚠️ [介词] ==confident with== → **confident about** —— 对某事有把握用 *about*。
- ⚠️ [搭配] ==came up with== → **settled on** —— *come up with* 是「想出、发明」;在已有选项里挑一个用 *settle on*。
