const {chromium}=require('playwright');const assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_BIN || undefined,headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const url=pathToFileURL(path.resolve(__dirname,'../users/taobao/index.html')).href;
 await page.goto(url,{waitUntil:'domcontentloaded'});
 const open=()=>page.evaluate(()=>{const t=byAnchor['p-have-to-questions'];open(t.it,t.level,t.i)});
 await open();
 assert.equal(await page.locator('.sp-sheet').count(),1);
 assert.equal(await page.locator('.sp-body .lv').count(),0);
 assert(await page.evaluate(()=>stageOf(byAnchor['p-have-to-questions'].it,'PRACTICE'))==='');
 await page.locator('#sp-skill').selectOption('base');await page.locator('[data-sp="focused"]').click();
 assert((await page.locator('.sp-question-head').textContent()).includes('/ 7'));
 await page.locator('#sp-answer-form').evaluate(f=>f.requestSubmit());
 assert(await page.locator('.sp-input-error').isVisible());
 assert.equal(await page.evaluate(()=>specialState('p-have-to-questions').attempts.length),0);
 await page.locator('#sp-answer').fill('have');await page.reload({waitUntil:'domcontentloaded'});await open();await page.locator('[data-sp="continue"]').click();
 assert.equal(await page.locator('#sp-answer').inputValue(),'have');
 await page.locator('#sp-answer-form').evaluate(f=>f.requestSubmit());assert((await page.locator('.sp-feedback').textContent()).includes('答对了'));
 assert.equal(await page.evaluate(()=>specialState('p-have-to-questions').attempts.length),1);
 await page.reload({waitUntil:'domcontentloaded'});await open();await page.locator('[data-sp="continue"]').click();
 assert.equal(await page.locator('.sp-feedback').count(),1);
 assert.equal(await page.evaluate(()=>specialState('p-have-to-questions').attempts.length),1);
 await page.locator('[data-sp="next"]').click();
 await page.locator('#sp-answer').fill('How much did he had to pay for the train ticket?');await page.locator('#sp-answer-form').evaluate(f=>f.requestSubmit());
 assert((await page.locator('.sp-feedback').textContent()).includes('还没写对'));
 await page.locator('[data-sp="next"]').click();await page.locator('[data-sp="hint"]').click();
 await page.locator('#sp-answer').fill('What time did Mia have to get up?');await page.locator('#sp-answer-form').evaluate(f=>f.requestSubmit());
 assert((await page.locator('.sp-feedback').textContent()).includes('提示后答对'));
 // Finish the remaining four questions through the same submit path.
 for(let i=0;i<4;i++){
   await page.locator('[data-sp="next"]').click();
   const answer=await page.evaluate(()=>{const it=byAnchor['p-have-to-questions'].it,r=specialRun(it);return it.practice.questions.find(q=>q.id===r.order[r.index]).answers[0]});
   await page.locator('#sp-answer').fill(answer);await page.locator('#sp-answer-form').evaluate(f=>f.requestSubmit());
 }
 await page.locator('[data-sp="next"]').click();
 assert.equal(await page.locator('.sp-results details').count(),7);
 await page.locator('[data-sp="home"]').click();
 assert.equal(await page.evaluate(()=>specialSummary(byAnchor['p-have-to-questions'].it).review.length),2);
 await page.locator('[data-sp="review"]').click();
 assert((await page.locator('.sp-question-head').textContent()).includes('/ 2'));
 await page.locator('[data-sp="home"]').click();await page.locator('[data-sp="mixed"]').click();
 const count=await page.evaluate(()=>specialRun(byAnchor['p-have-to-questions'].it).order.length);assert.equal(count,28);
 assert.equal(await page.locator('.sp-skill').count(),0);
 // Opening a core note retains the exercise and offers a return path.
 await page.locator('[data-sp="hint"]').click();await page.locator('.sp-hint .xref').click();
 assert.equal(await page.locator('.sp-sheet').count(),0);await page.locator('.sheet .back').click();await page.locator('[data-sp="continue"]').click();
 assert.equal(await page.locator('.sp-hint').count(),1);
 await page.setViewportSize({width:390,height:844});await open();await page.locator('[data-sp="continue"]').click();
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);

 await page.emulateMedia({colorScheme:'dark'});
 // Grading respects subject/tense, and permits contractions/case/punctuation variants.
 const checks=await page.evaluate(()=>{
   const qs=byAnchor['p-have-to-questions'].it.practice.questions;
   return [practiceCorrect('DO NOT',qs.find(q=>q.id==='agreement-1')), !practiceCorrect('does not',qs.find(q=>q.id==='agreement-1')),
     practiceCorrect('how much did he have to pay for the train ticket',qs.find(q=>q.id==='base-2')),
     !practiceCorrect('What time do you have to leave?',qs.find(q=>q.id==='person-1')),
     practiceCorrect('did not have to',qs.find(q=>q.id==='past-6'))];});
 assert(checks.every(Boolean),checks);
 // Finish every question in the mixed run, including its current hinted question.
 for(let n=0;n<28;n++){
   const answer=await page.evaluate(()=>{const it=byAnchor['p-have-to-questions'].it,r=specialRun(it);return it.practice.questions.find(q=>q.id===r.order[r.index]).answers[0]});
   await page.locator('#sp-answer').fill(answer);await page.locator('#sp-answer-form').evaluate(f=>f.requestSubmit());
   await page.locator('[data-sp="next"]').click();
 }
 assert.equal(await page.locator('.sp-results details').count(),28);
 await page.reload({waitUntil:'domcontentloaded'});await open();
 assert.equal(await page.evaluate(()=>specialSummary(byAnchor['p-have-to-questions'].it).done),28);
 assert.equal(await page.evaluate(()=>specialSummary(byAnchor['p-have-to-questions'].it).review.length),1);
 const runs=await page.evaluate(()=>Object.values(specialState('p-have-to-questions').runs).map(r=>r.status));
 assert(runs.includes('active')&&runs.includes('completed'));
 await page.locator('[data-sp="review"]').click();
 assert((await page.locator('.sp-question-head').textContent()).includes('/ 1'));
 await page.locator('#sp-answer').fill(await page.evaluate(()=>{const it=byAnchor['p-have-to-questions'].it,r=specialRun(it);return it.practice.questions.find(q=>q.id===r.order[r.index]).answers[0]}));
 await page.locator('#sp-answer-form').evaluate(f=>f.requestSubmit());await page.locator('[data-sp="next"]').click();
 assert.equal(await page.evaluate(()=>specialSummary(byAnchor['p-have-to-questions'].it).review.length),0);
 assert.equal(await page.evaluate(()=>Object.keys(stLog).length),0);
 assert.equal(errors.length,0,errors.join('\n'));
 // 同步 (用户 2026-11-18): the records ride in progress-taobao.json, answers and all; GitHub is mocked.
 const records=await page.evaluate(()=>localStorage.getItem('vk.taobao.specialPractice'));
 const synced=async(remote,local)=>{
   const ctx=await browser.newContext({viewport:{width:1440,height:1000}}),p=await ctx.newPage(),puts=[],errs=[];p.on('pageerror',e=>errs.push(e.message));
   await ctx.route('https://api.github.com/**',route=>{const q=route.request();
     if(q.method()==='GET')return route.fulfill({json:{sha:'s',content:Buffer.from(JSON.stringify(remote)).toString('base64')}});
     puts.push(JSON.parse(Buffer.from(JSON.parse(q.postData()).content,'base64').toString()));return route.fulfill({json:{}});});
   await p.addInitScript(l=>{localStorage.setItem('vk.taobao.gh','github_pat_test');if(l)localStorage.setItem('vk.taobao.specialPractice',l);},local);
   await p.goto(url,{waitUntil:'domcontentloaded'});
   await p.waitForFunction(()=>JSON.parse(localStorage.getItem('vk.taobao.sync')||'{}').ok);
   const done=await p.evaluate(()=>specialSummary(byAnchor['p-have-to-questions'].it).done),out=await p.evaluate(()=>specialOut());
   assert.equal(errs.length,0,errs.join('\n'));await ctx.close();return {puts,done,out};
 };
 const up=await synced({v:1},records);
 assert.equal(up.puts.length,1);assert.deepEqual(up.puts[0].specialPractice,up.out);
 assert(up.puts[0].specialPractice['p-have-to-questions'].attempts.some(a=>a.answer==='What time did Mia have to get up?'));
 const down=await synced(up.puts[0],null);
 assert.equal(down.puts.length,0,'nothing changed: no commit back');assert.equal(down.done,28);
 console.log('Practice browser checks passed: filters, 28-question mixed mode, draft/answer resume, blank/duplicate submissions, hints, wrong-only pool, core-note return, grading, mobile and dark mode, sync up and down.');
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
