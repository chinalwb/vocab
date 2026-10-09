# progress

学习进度,由网页和 App 通过 GitHub API 自动读写,不要手动改。

- `stages`:进度(学习中 / 自测 / 已掌握),`{anchor: {s, t}}`
- `cards`:SM-2 复习卡,`{anchor: {ease, interval, reps, lapses, due, t}}`
- `attempts`:自测记录,只有时间、对错、是否偷看、来源 —— **不含自己写的句子原文**(原文只留在设备本地)

这个分支和 main / dev 没有共同历史,不会触发网页构建。
