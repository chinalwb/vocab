// 专项练习: web-only structured questions; no ordinary quiz, stage or SM-2 records.
// Typed answers and drafts stay in this browser, under the notebook's store prefix.
let specialRecords = {};
try { specialRecords = JSON.parse(store.get("vk.specialPractice", "{}")) || {}; } catch {}
function specialState(anchor){
  return specialRecords[anchor] ||= { runs: {}, current: "", attempts: [] };
}
function refreshSpecialCards(){
  document.querySelectorAll("[data-sp-entry]").forEach(el=>{
    const it=byAnchor[el.dataset.spEntry]?.it;if(!it)return;
    const summary=specialSummary(it);el.textContent=`${summary.done} / ${it.practice.questions.length} 题 · ${summary.done===0?"未开始":summary.done===it.practice.questions.length?"已完成":"进行中"}`;
  });
}
function saveSpecial(){ store.set("vk.specialPractice", JSON.stringify(specialRecords)); refreshSpecialCards(); }
function specialId(){ return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`; }
function practiceNormalize(text){
  return String(text).normalize("NFKC").toLowerCase().replace(/[’‘]/g,"'")
    .replace(/\b(don'?t|doesn'?t|didn'?t|isn'?t|aren'?t|wasn'?t|weren'?t|couldn'?t|shouldn'?t|haven'?t|hasn'?t|hadn'?t|mustn'?t|wouldn'?t)\b/g,
      m => ({dont:"do not",doesnt:"does not",didnt:"did not",isnt:"is not",arent:"are not",wasnt:"was not",werent:"were not",couldnt:"could not",shouldnt:"should not",havent:"have not",hasnt:"has not",hadnt:"had not",mustnt:"must not",wouldnt:"would not"})[m.replace("'","")])
    .replace(/\b(can'?t|cannot)\b/g,"can not")
    .replace(/[^\p{L}\p{N}\s]/gu," ").replace(/\s+/g," ").trim();
}
function practiceCorrect(answer, question){
  const text=practiceNormalize(answer);
  return !!text && question.answers.some(a=>practiceNormalize(a)===text);
}
function specialSummary(it){
  const state=specialState(it.anchor), records=state.attempts;
  const first=new Map(),last=new Map();
  records.forEach(a=>{if(!first.has(a.q))first.set(a.q,a);last.set(a.q,a);});
  const ids=new Set(it.practice.questions.map(q=>q.id));
  return {
    done:[...last.keys()].filter(id=>ids.has(id)).length,
    firstRight:[...first.values()].filter(a=>ids.has(a.q)&&a.ok&&!a.help).length,
    review:it.practice.questions.filter(q=>{const a=last.get(q.id);return a&&(!a.ok||a.help);}).map(q=>q.id),
  };
}
function specialRun(it){return specialState(it.anchor).runs[specialState(it.anchor).current];}
function specialSaveDraft(it,run,q,value){run.drafts[q.id]=value;run.t=Date.now();saveSpecial();}
function startSpecial(it,mode,skill=""){
  const p=it.practice,st=specialState(it.anchor),summary=specialSummary(it);
  let order=p.questions.filter(q=>mode==="review"?summary.review.includes(q.id):!skill||q.skill===skill).map(q=>q.id);
  if(!order.length)return;
  if(mode==="focused") { const keys=Object.keys(p.skills); order.sort((a,b)=>keys.indexOf(p.questions.find(q=>q.id===a).skill)-keys.indexOf(p.questions.find(q=>q.id===b).skill)); }
  if(mode==="mixed")order=shuffled(order,Math.random()*4294967296);
  const id=specialId();
  st.current=id;st.runs[id]={id,mode,skill,order,index:0,drafts:{},help:{},submissions:{},status:"active",started:Date.now(),t:Date.now()};
  saveSpecial();showSpecialPractice(it,"PRACTICE",0,"run");
}
function specialHomeHtml(it){
  const p=it.practice,st=specialState(it.anchor),summary=specialSummary(it),run=specialRun(it);
  const notes=[...new Set(p.questions.map(q=>q.note))];
  const completed=Object.values(st.runs).filter(r=>r.status==="completed").length;
  return `<div class="sp-intro"><span class="sp-kicker">专项练习 · ${p.questions.length} 题</span><h3>一次练一点，慢慢练熟。</h3><p>${esc(p.description)}</p></div>`+
    `<div class="sp-metrics"><div><b>${summary.done} / ${p.questions.length}</b><span>已作答题目</span></div><div><b>${summary.firstRight}</b><span>首次独立答对</span></div><div><b>${summary.review.length}</b><span>需要重练</span></div></div>`+
    `<p class="sp-status">${summary.done===0?"尚未开始":summary.done===p.questions.length?"整组题目已完成；完成不等于已经掌握，隔几天可以再练一次。":"练习进行中，随时可以停下来。"}${completed?` 已完成 ${completed} 次练习。`:""}</p>`+
    (run?.status==="active"?`<div class="sp-resume"><p>上次做到第 ${Math.min(run.index+1,run.order.length)} / ${run.order.length} 题</p><button type="button" class="btn" data-sp="continue">继续练习 →</button></div>`:"")+
    `<section class="sp-start"><h3>选择怎么练</h3><label for="sp-skill">分项练习：先集中练一个点</label><select id="sp-skill"><option value="">全部四个知识点 · 按分组顺序</option>${Object.entries(p.skills).map(([key,label])=>`<option value="${esc(key)}">${esc(label)} · ${p.questions.filter(q=>q.skill===key).length} 题</option>`).join("")}</select>`+
    `<div class="sp-actions"><button type="button" class="btn" data-sp="focused">开始分项练习</button><button type="button" class="btn" data-sp="mixed">开始混合练习 · ${p.questions.length} 题</button><button type="button" class="btn" data-sp="review"${summary.review.length?"":" disabled"}>重做错题与提示题 · ${summary.review.length}</button></div></section>`+
    `<details class="sp-notes"><summary>先看核心笔记</summary><ul>${notes.map(a=>`<li><a class="xref" href="#${esc(a)}" data-ref="${esc(a)}">${esc(byAnchor[a]?.it.title||a)}</a></li>`).join("")}</ul></details>`+
    `<details class="sp-past"><summary>练习记录与未完成的练习</summary>${Object.values(st.runs).sort((a,b)=>b.started-a.started).map(r=>`<div class="sp-run-record"><span>${new Date(r.started).toLocaleString("zh-CN")} · ${r.mode==="mixed"?"混合":r.mode==="review"?"重练":"分项"} · ${Object.keys(r.submissions).length}/${r.order.length} 题</span><button type="button" class="linkish" data-sp="resume" data-run="${esc(r.id)}">${r.status==="completed"?"查看结果":"继续"}</button></div>`).join("")||"<p>还没有练习记录。</p>"}</details>`+
    `<p class="sp-storage">进度、草稿和答题记录自动保存在此浏览器；暂不随现有学习进度同步。提示后答对也会列入重练，不会直接算作掌握。</p>`;
}
function specialQuestionHtml(it,run){
  const p=it.practice,st=specialState(it.anchor),q=p.questions.find(q=>q.id===run.order[run.index]);
  if(!q)return '<p>这道题已更新，请返回练习首页重新开始。</p><button class="btn" type="button" data-sp="home">返回练习首页</button>';
  const attempt=st.attempts.find(a=>a.id===run.submissions[q.id]);
  const kind={fill:"填空",correct:"改错",rewrite:"按要求改写"}[q.type];
  const helped=!!run.help[q.id];
  return `<div class="sp-question-head"><span>第 ${run.index+1} / ${run.order.length} 题 · ${kind}</span><button class="linkish" type="button" data-sp="home">保存并返回首页</button></div>`+
    `<progress max="${run.order.length}" value="${Object.keys(run.submissions).length}" aria-label="本次练习完成进度"></progress>`+
    (run.mode==="focused"?`<span class="sp-skill">${esc(p.skills[q.skill])}</span>`:"")+
    `<h3 class="sp-prompt">${esc(q.prompt).replace(/\n/g,"<br>")}</h3>`+
    `<form id="sp-answer-form"><label for="sp-answer">${q.type==="fill"?"填写空缺部分":"写出完整句子"}</label><textarea id="sp-answer" rows="3" autocomplete="off" autocapitalize="sentences" spellcheck="false"${attempt?" readonly":""}>${esc(attempt?.answer??run.drafts[q.id]??"")}</textarea>`+
    (!attempt?`<p class="sp-input-error" role="alert" hidden>先写下你的答案，再提交。</p><div class="sp-actions"><button class="btn" type="submit">提交答案</button><button class="linkish" type="button" data-sp="hint" aria-expanded="${helped}">${helped?"已查看提示":"需要一点提示"}</button></div>`:"")+`</form>`+
    (helped&&!attempt?`<aside class="sp-hint"><p>${esc(q.hint)}</p><a class="xref" href="#${esc(q.note)}" data-ref="${esc(q.note)}">打开对应核心笔记 →</a></aside>`:"")+
    (attempt?`<section class="sp-feedback ${attempt.ok?"correct":"retry"}" role="status"><h3>${attempt.ok?(attempt.help?"提示后答对了，再独立练一次":"答对了！"):"这次还没写对，一起看一下"}</h3><p class="sp-answer-label">${q.answers.length>1?"参考答案（以下写法均可）":"参考答案"}</p>${q.answers.map(a=>`<p class="sp-model">${esc(a)} ${spkBtn(a)}</p>`).join("")}<p>${esc(q.explanation)}</p>`+
      (!attempt.ok?`<details><summary>看看和参考答案差在哪里</summary>${verdictHtml(practiceNormalize(attempt.answer),q.answers.map(practiceNormalize).join("\n"),"")}</details>`:"")+
      `<a class="xref" href="#${esc(q.note)}" data-ref="${esc(q.note)}">看看对应核心笔记 →</a><div class="sp-actions"><button class="btn" type="button" data-sp="next">${run.index===run.order.length-1?"查看本次结果":"下一题 →"}</button></div></section>`:"")+
    `<p class="sp-storage">自动保存。${attempt?"每题本次只记录一次提交；错题可以稍后重新练。":"可以按 Ctrl / ⌘ + Enter 提交。"}</p>`;
}
function specialResultsHtml(it,run){
  const st=specialState(it.anchor),p=it.practice;
  const attempts=run.order.map(id=>st.attempts.find(a=>a.id===run.submissions[id])).filter(Boolean);
  const independent=attempts.filter(a=>a.ok&&!a.help).length,helped=attempts.filter(a=>a.ok&&a.help).length,wrong=attempts.filter(a=>!a.ok).length;
  return `<div class="sp-intro"><span class="sp-kicker">本次练习完成</span><h3>做完了，看看哪些地方还需要练。</h3></div><div class="sp-metrics"><div><b>${independent}</b><span>独立答对</span></div><div><b>${helped}</b><span>提示后答对</span></div><div><b>${wrong}</b><span>需要订正</span></div></div>`+
    `<p>这次完成 ${attempts.length} 题。刚看完答案不等于已经掌握，隔几天换个时间再试。</p><div class="sp-actions"><button class="btn" type="button" data-sp="review"${specialSummary(it).review.length?"":" disabled"}>重练错题与提示题</button><button class="btn" type="button" data-sp="home">返回练习首页</button></div>`+
    `<div class="sp-results">${attempts.map((a,i)=>{const q=p.questions.find(q=>q.id===a.q);return `<details><summary>第 ${i+1} 题 · ${a.ok?(a.help?"提示后答对":"独立答对"):"需要订正"}</summary><p>${esc(q.prompt).replace(/\n/g,"<br>")}</p><p>我的答案：${esc(a.answer)}</p><p>参考答案：${esc(q.answers[0])}</p><p>${esc(q.explanation)}</p></details>`;}).join("")}</div>`;
}
// [o.noFocus]: opened on its own (the first of the list, docked) — don't take the focus, like show()
function showSpecialPractice(it,level,i,page="home",o={}){
  stopReading();if(scrim)scrim.remove();
  scrim=document.createElement("div");const docked=dockable()&&!document.body.classList.contains("running");
  scrim.className="scrim"+(docked?" docked":"");scrim._entry={it,level,i};
  const sheet=document.createElement("div");sheet.className="sheet sp-sheet";sheet.style.setProperty("--sw","var(--cl-1)");
  sheet.setAttribute("role","dialog");sheet.setAttribute("aria-modal",String(!docked));sheet.setAttribute("aria-label",it.title);
  const run=specialRun(it),body=page==="run"&&run?run.status==="completed"?specialResultsHtml(it,run):specialQuestionHtml(it,run):specialHomeHtml(it);
  sheet.innerHTML=`<button class="x" type="button" aria-label="关闭">✕</button>`+
    (trail.length?`<button class="back" type="button">← 返回上一条</button>`:"")+`<div class="sp-title"><span class="sp-kicker">专项练习</span><h2>${esc(it.title)}</h2></div><div class="sp-body">${body}</div>`;
  scrim.append(sheet);document.body.append(scrim);markSel(docked?it.anchor:"");
  sheet.querySelector(".x").onclick=close;sheet.querySelector(".back")?.addEventListener("click",goBack);
  scrim.addEventListener("click",e=>{if(e.target===scrim&&!docked)close();});
  wireXrefsIn(sheet,follow);
  const form=sheet.querySelector("form");
  if(form&&run){
    const q=it.practice.questions.find(q=>q.id===run.order[run.index]),answer=sheet.querySelector("#sp-answer");
    answer.addEventListener("input",()=>{specialSaveDraft(it,run,q,answer.value);sheet.querySelector(".sp-input-error")?.setAttribute("hidden","");});
    form.addEventListener("submit",e=>{
      e.preventDefault();if(run.submissions[q.id])return;
      if(!answer.value.trim()){sheet.querySelector(".sp-input-error").hidden=false;answer.focus();return;}
      const a={id:specialId(),q:q.id,run:run.id,t:Date.now(),answer:answer.value.trim(),ok:practiceCorrect(answer.value,q),help:!!run.help[q.id]};
      specialState(it.anchor).attempts.push(a);run.submissions[q.id]=a.id;run.t=a.t;saveSpecial();showSpecialPractice(it,level,i,"run");
    });
    answer.addEventListener("keydown",e=>{e.stopPropagation();if(e.key==="Enter"&&(e.ctrlKey||e.metaKey)){e.preventDefault();form.requestSubmit();}});
  }
  const feedback=sheet.querySelector(".sp-feedback h3");
  if(feedback){feedback.tabIndex=-1;feedback.focus({preventScroll:true});feedback.scrollIntoView({block:"nearest"});}
  else if(!o.noFocus)sheet.querySelector(".x").focus({preventScroll:true});
  sheet.addEventListener("click",e=>{
    const b=e.target.closest("[data-sp]");if(!b)return;
    const action=b.dataset.sp;
    if(action==="focused")return startSpecial(it,"focused",sheet.querySelector("#sp-skill").value);
    if(action==="mixed"||action==="review")return startSpecial(it,action);
    if(action==="continue")return showSpecialPractice(it,level,i,"run");
    if(action==="resume"){specialState(it.anchor).current=b.dataset.run;saveSpecial();return showSpecialPractice(it,level,i,"run");}
    if(action==="home")return showSpecialPractice(it,level,i);
    if(!run)return;
    if(action==="hint"){
      run.help[run.order[run.index]]=true;run.t=Date.now();saveSpecial();showSpecialPractice(it,level,i,"run");
    }
    if(action==="next"){
      if(!run.submissions[run.order[run.index]])return;
      if(run.index===run.order.length-1)run.status="completed";else run.index++;
      run.t=Date.now();saveSpecial();showSpecialPractice(it,level,i,"run");
    }
  });
}

refreshSpecialCards();
