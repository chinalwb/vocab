# 验证

```sh
python3 -m unittest discover -s tests -v
python3 build.py
python3 build.py --src users/taobao
python3 build.py --src users/so
```

`test_special_practice.py` 校验题库、核心笔记引用、重复 ID、答案及题型错误，并构建学生页面验证题目只进入网页专项入口、不进入普通例句；原生数据的 entries 保持不变，新增可选 practices 数组，并校验题库更新影响 meta.hash。

交互回归使用 Playwright，安装在工作目录外即可，不需要改项目依赖：

```sh
npm install --prefix /private/tmp/vocab-browser playwright --no-save
NODE_PATH=/private/tmp/vocab-browser/node_modules \
CHROME_BIN='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' \
node tests/special_practice.cjs
```

其他平台可把 `CHROME_BIN` 指向本机 Chrome；已安装 Playwright Chromium 时可省略该变量。浏览器在独立上下文打开本地生成页，不访问真实学习进度、令牌或在线同步。测试覆盖草稿与提交结果恢复、空提交与重复记录、提示标记、错题池、混合模式、核心笔记往返、完整 28 题、重练后移出错题池及手机布局。

专项练习的内容位于学生目录的 `practices.json`；页面逻辑与样式位于根目录 `practice.js` / `practice.css`，构建时内联，无需额外的静态资源部署。网页与 iPad 均提供专项界面；网页学习记录保存在该笔记本前缀下的 `specialPractice` 浏览器键中，iPad 使用独立文件；连上同步后，两边都把整份记录（含答案原文）放进学生同步文件的 `specialPractice`（2026-11-18）。`special_practice.cjs` 最后一段用模拟的 GitHub 接口验证：记录连同答案上传，另一个浏览器拉下来后看到同样的进度，且不会再提交回去。原生测试方法见 `ipad/README.md`。
