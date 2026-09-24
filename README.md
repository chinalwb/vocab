# vocab

英语学习笔记 —— 在真实阅读(技术文档、书籍、日常工作沟通)中遇到的单词、短语和语法点,按义项整理,每条配例句和中文翻译。

**在线浏览:** https://chinalwb.github.io/vocab/

## 内容

`vocabulary.md` 是唯一的内容来源,包含:

- **单词/短语条目** —— 音标、词性、CEFR 等级、英文释义、中文解释、按义项分组的例句(每句附中文翻译)、常见搭配
- **语法与语域笔记** —— 悬垂修饰语、逗号粘连、条件句、主语从句等易错点,以及"同一句话在不同场合该怎么说"的语域辨析

页面把这些条目按 **CEFR 等级**分组展示成便签卡片,颜色对应等级(A1 绿 → C2 珊瑚红,术语紫,语法粉),支持搜索、按等级筛选、明暗主题切换,点卡片展开完整笔记。

## 新增条目

直接编辑 `vocabulary.md` 就行:

1. 在顶部 `## 目录` 里加一行 `N. [标题](#anchor)`
2. 在文件末尾追加(`<a id="anchor"></a>` 锚点要和目录里的链接对应):

```markdown
---

<a id="your-anchor"></a>
## your-word

- 音标:英 /.../ 美 /.../
- 词性:n.
- CEFR:B2

**English definition:** ...

**含义:** ...

1. English example sentence.
   中文翻译。
2. Another example.
   中文翻译。

**常见搭配:** ...
```

推送到 `main` 后,GitHub Action 会自动重新生成 `index.html`,页面几分钟后更新 —— 用手机上的 GitHub App 或网页编辑器改也一样生效,不需要电脑。

## Android App

`android/` 里是一个自用的 Android App,用来浏览词条、做间隔重复复习。它从 Pages 拉取 `data.json`,词库更新后会自动提示。构建方法见 `CLAUDE.md`。

## 本地生成

```bash
python build.py
```

读取 `vocabulary.md` + `template.html`,输出 `index.html`,以及给 App 用的 `data.json` / `meta.json`。`index.html` 是自动生成的,不要手动改它 —— 改 `template.html`。
