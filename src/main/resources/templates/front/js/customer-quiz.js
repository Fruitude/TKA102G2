(() => {
 'use strict';
 const root=document.getElementById('ff-chat');if(!root||root.dataset.quizInitialized)return;root.dataset.quizInitialized='true';
 const dialog=root.querySelector('.ff-quiz-dialog');if(!dialog)return;
 const $=s=>dialog.querySelector(s),api=root.dataset.api.replace(/;jsessionid=[^/?#;]+/g,''),prefix=api.replace(/\/api\/front\/chat$/,'');
 const home=$('.ff-quiz-home'),question=$('.ff-quiz-question'),result=$('.ff-quiz-result'),error=$('.ff-quiz-error'),loading=$('.ff-quiz-loading');
 let run=null,answers={},index=0,busy=false,generation=0,controller=null,lastTheme=null;
 const cardOpenSound=$('.ff-quiz-card-open-sound'),cardReturnSound=$('.ff-quiz-card-return-sound');let cardSoundTimer;
 function stopCardSounds(){clearTimeout(cardSoundTimer);[cardOpenSound,cardReturnSound].forEach(audio=>{audio.pause();try{audio.currentTime=0;}catch(ignored){}});}
 function playCardSounds(opening,restoring){stopCardSounds();
  function play(audio){if(!dialog.open||document.hidden)return;audio.volume=.35;const pending=audio.play();if(pending)pending.catch(()=>{});}
  if(restoring)play(cardReturnSound);
  if(opening){if(restoring)cardSoundTimer=setTimeout(()=>play(cardOpenSound),140);else play(cardOpenSound);}
 }
 document.addEventListener('visibilitychange',()=>{if(document.hidden)stopCardSounds();});
 const music=$('.ff-quiz-music'),musicToggle=$('.ff-quiz-music-toggle'),themeTracks=new Map(),themeStyles=new Map();
 const musicPreference='fruitude:quiz-music-muted:v1';let musicEnabled=true;
 try{musicEnabled=localStorage.getItem(musicPreference)!=='true';}catch(ignored){}
 function syncMusic(){const playing=!music.paused&&!music.muted;musicToggle.textContent=playing?'音樂：開':'播放音樂';musicToggle.setAttribute('aria-pressed',String(playing));musicToggle.setAttribute('aria-label',playing?'關閉背景音樂':'播放背景音樂');}
 function playMusic(){if(!musicEnabled||!dialog.open||document.hidden||!music.getAttribute('src'))return;
  const pending=music.play();if(pending)pending.then(()=>{if(!dialog.open||!musicEnabled||document.hidden)music.pause();}).catch(()=>{syncMusic();});
 }
 function stopMusic(){stopCardSounds();music.pause();music.removeAttribute('src');music.load();musicToggle.hidden=true;syncMusic();}
 function playThemeMusic(id){const track=themeTracks.get(id);if(!track)return;
  music.pause();music.src=track;music.volume=.35;music.loop=true;musicToggle.hidden=false;syncMusic();playMusic();
 }
 music.addEventListener('play',syncMusic);music.addEventListener('pause',syncMusic);music.addEventListener('error',syncMusic);
 musicToggle.addEventListener('click',()=>{musicEnabled=music.paused||music.muted;
  try{localStorage.setItem(musicPreference,String(!musicEnabled));}catch(ignored){}
  if(musicEnabled){music.muted=false;playMusic();}else music.pause();syncMusic();
 });
 document.addEventListener('visibilitychange',()=>{if(document.hidden)music.pause();else playMusic();});
 window.addEventListener('pagehide',stopMusic);
 function node(tag,cls,text){const el=document.createElement(tag);if(cls)el.className=cls;if(text!==undefined)el.textContent=text;return el;}
 function artwork(kind,theme=dialog.dataset.theme||'garden'){
  const ns='http://www.w3.org/2000/svg',svg=document.createElementNS(ns,'svg');svg.setAttribute('viewBox','0 0 160 180');svg.setAttribute('aria-hidden','true');svg.classList.add('ff-quiz-art');
  function shape(tag,attrs,parent=svg){const el=document.createElementNS(ns,tag);Object.entries(attrs).forEach(([k,v])=>el.setAttribute(k,String(v)));parent.append(el);return el;}
  const tones=({garden:[['#eac18b','#f4dcaf'],['#dba797','#f3d8c3'],['#b6c6b0','#e5ead3'],['#e4b995','#efdfc6']],sky:[['#e2b594','#dbe9e9'],['#acc3b7','#e5edda'],['#e8c280','#f7e9c9'],['#a2bacb','#dce6ec']],stars:[['#c3acc4','#e7dfeb'],['#b2bdce','#e3e8ef'],['#dcc597','#f1e8d3'],['#afbdb4','#e2e7df']]})[theme][kind%4];
  shape('path',{d:'M80 9C118 9 141 44 141 83v23c0 37-25 64-61 64s-61-27-61-64V83C19 44 42 9 80 9Z',fill:tones[1],stroke:'#b29b71'});
  shape('circle',{cx:80,cy:76,r:47,fill:'#fffaf0',stroke:'#b29b71'});
  shape('circle',{cx:80,cy:76,r:40,stroke:'#c7ad7c','stroke-dasharray':'1 5'});
  [false,true].forEach(flip=>{const g=shape('g',{transform:flip?'translate(160 0) scale(-1 1)':'translate(0 0)'});
   shape('path',{d:'M48 162C16 144 22 117 29 99C40 75 28 51 41 31M34 137C8 124 15 107 29 108M28 111C47 94 47 79 35 74M32 82C13 70 23 54 34 58',stroke:'#839573','stroke-width':1.6},g);
   shape('path',{d:'M34 136C19 136 11 124 15 116c12 0 20 8 19 20ZM29 110c17-3 23-15 17-24-11 5-17 13-17 24ZM32 81C18 77 17 65 23 61c10 4 13 12 9 20Z',fill:'#b4c0a0',stroke:'#839573'},g);
   const flower=shape('g',{transform:'translate(40 35)'},g);
   [0,72,144,216,288].forEach(angle=>shape('ellipse',{cx:0,cy:-7,rx:4.5,ry:8,transform:'rotate('+angle+')',fill:tones[0],stroke:'#b18b62','stroke-width':.8},flower));
   shape('circle',{cx:0,cy:0,r:3.5,fill:'#c59a51',stroke:'#a18351'},flower);
  });
  const garden=[
   [['circle',{cx:80,cy:76,r:21,fill:'#efd19d'}],['path',{d:'M80 43V36M80 109v7M47 76h-7M113 76h7M57 53l-5-5M103 99l5 5M103 53l5-5M57 99l-5 5'}]],
   [['path',{d:'M80 100C47 91 48 65 57 62l23 16 23-16c9 3 10 29-23 38ZM80 100V61q-21-12 0-24 21 12 0 24M80 100v15m-17 0h34',fill:'#e6b7a5'}]],
   [['path',{d:'M80 114V63M80 92C58 95 48 78 54 64c16 0 28 12 26 28ZM80 76c-2-23 11-34 27-31 2 18-8 30-27 31Z',fill:'#b9c7a3',stroke:'#839573'}],['circle',{cx:68,cy:48,r:6,fill:'#e6b6a2'}]],
   [['path',{d:'M80 78C42 25 34 64 52 82c-17 28 13 35 28 0 15 35 45 28 28 0 18-18 10-57-28-4Z',fill:'#e4b6a0'}],['path',{d:'M80 66v33M80 67l-8-9m8 9 8-9',stroke:'#839573'}]]
  ];
  const sky=[
   [['circle',{cx:80,cy:76,r:29,fill:'#edf2e6'}],['path',{d:'m80 47 10 29-10 29-10-29ZM51 76h58M80 40v-7m0 79v7',fill:'#dfb98a'}]],
   [['path',{d:'m45 100 26-48 17 32 13-21 20 37Z',fill:'#adc3b7'}],['path',{d:'m61 71 10-19 10 19-10-6ZM45 108h76',fill:'#fff9e8'}],['circle',{cx:103,cy:45,r:9,fill:'#e9cb92'}]],
   [['path',{d:'M64 85c-42-63 64-63 32 0ZM64 85l7 18h18l7-18M72 103v12h16v-12',fill:'#e5b49a'}],['path',{d:'M75 85c-20-52 30-52 10 0M64 85h32',fill:'#f6dfb3'}]],
   [['path',{d:'M80 44v60M80 48 52 91h28ZM86 57l22 34H86ZM49 99h64l-11 12H60Z',fill:'#adc5d0'}],['path',{d:'M46 119q9-6 18 0t18 0 18 0 18 0'}]]
  ];
  const stars=[
   [['path',{d:'M91 46a31 31 0 1 0 16 49A31 31 0 0 1 91 46Z',fill:'#e9ce96'}],['path',{d:'M106 50v12m-6-6h12M112 76v7m-3-3h7'}]],
   [['path',{d:'m55 58 37-12 15 40-36 25-20-30 41-35',stroke:'#9b91ab'}],...[[55,58],[92,46],[107,86],[71,111],[51,81]].map(([cx,cy])=>['circle',{cx,cy,r:4,fill:'#e5cc97'}])],
   [['path',{d:'M58 46h44M58 109h44M63 46c0 23 12 26 17 31-5 5-17 9-17 32m34-63c0 23-12 26-17 31 5 5 17 9 17 32M67 57h26L80 73ZM68 101l12-18 12 18Z',fill:'#e9d2a4'}]],
   [['circle',{cx:80,cy:78,r:20,fill:'#c3b7ce'}],['ellipse',{cx:80,cy:78,rx:39,ry:12,transform:'rotate(-25 80 78)',stroke:'#a98a58'}],['circle',{cx:108,cy:46,r:5,fill:'#ead6aa'}],['path',{d:'M51 48v12m-6-6h12'}]]
  ];const motifs=({garden,sky,stars})[theme];motifs[kind%4].forEach(([tag,attrs])=>shape(tag,attrs));
  shape('path',{d:'M53 147q27 26 54 0M63 152q17-12 34 0',stroke:'#ad9567'});return svg;
 }
 function state(name){dialog.dataset.screen=name;if(name==='home')delete dialog.dataset.theme;if(name!=='question')delete dialog.dataset.question;home.hidden=name!=='home';question.hidden=name!=='question';result.hidden=name!=='result';}
 function message(text){error.textContent=text||'';error.hidden=!text;}
 function setBusy(value){busy=value;dialog.setAttribute('aria-busy',String(value));loading.hidden=!value;dialog.querySelectorAll('button:not(.ff-quiz-close):not(.ff-quiz-music-toggle)').forEach(b=>b.disabled=value);if(!value&&run&&!question.hidden)syncNext();}
 async function call(path,body){const response=await fetch(api+'/quizzes'+path,{method:body===undefined?'GET':'POST',credentials:'same-origin',cache:'no-store',signal:controller?.signal,headers:{'X-Requested-With':'XMLHttpRequest',...(body===undefined?{}:{'Content-Type':'application/json'})},...(body===undefined?{}:{body:JSON.stringify(body)})});
  let data;try{data=await response.json();}catch(ignored){}
  if(!response.ok)throw Error(data?.message||(response.status===401?'請先登入會員，再開始測驗。':'系統忙碌中，請稍後再嘗試。'));return data;
 }
 function begin(){controller?.abort();controller=new AbortController();return ++generation;}
 async function open(){if(dialog.open)return;dialog.showModal();const token=begin();run=null;answers={};state('home');message('');setBusy(true);$('.ff-quiz-themes').replaceChildren();
  try{const themes=await call('');if(token!==generation)return;
   themes.forEach((theme,i)=>{const style=['garden','sky','stars'][i%3];themeStyles.set(theme.quizId,style);themeTracks.set(theme.quizId,[music.dataset.trackOne,music.dataset.trackTwo,music.dataset.trackThree][i%3]);const button=node('button','ff-quiz-theme');button.type='button';button.dataset.theme=style;button.append(artwork(i,style),node('span','ff-quiz-theme-number',['I','II','III'][i%3]));const text=node('span','ff-quiz-theme-copy');text.append(node('strong','',theme.name),node('span','',theme.description.replace(/五種/g,'五款').replace(/(從放鬆、陪伴到小小浪漫，|探索、相聚或慢旅行，|從穩定、行動到細膩與彈性，)\s*/g,'$1\n')),node('small','','5 題 · 約 1 分鐘'));button.append(text,node('span','ff-quiz-theme-arrow','→'));button.addEventListener('click',()=>start(theme.quizId));$('.ff-quiz-themes').append(button);});
   if(!themes.length)message('小測驗正在準備中，歡迎稍後再來。');
  }catch(e){if(token===generation&&e.name!=='AbortError')message(e.message);}finally{if(token===generation)setBusy(false);}
 }
 async function start(id){if(busy)return;dialog.dataset.theme=themeStyles.get(id)||'garden';playThemeMusic(id);const token=begin();setBusy(true);message('');
  try{const data=await call('/'+id+'/start',{});if(token!==generation)return;run=data;lastTheme=id;answers={};index=0;renderQuestion();state('question');$('.ff-quiz-question-title').focus({preventScroll:true});
  }catch(e){if(token===generation&&e.name!=='AbortError'){stopMusic();message(e.message);}}finally{if(token===generation)setBusy(false);}
 }
 function syncNext(){$('.ff-quiz-next').disabled=busy||!answers[run.questions[index].questionId];$('.ff-quiz-prev').disabled=busy||index===0;}
 function renderQuestion(){stopCardSounds();dialog.dataset.question=String(index%5+1);const q=run.questions[index];$('.ff-quiz-current').textContent='第 '+(index+1)+' 題 / 共 '+run.questions.length+' 題';$('.ff-quiz-progress').value=index+1;$('.ff-quiz-progress').max=run.questions.length;$('.ff-quiz-subject').textContent=run.name;$('.ff-quiz-question-title').textContent=q.text;
  const choices=$('.ff-quiz-choices');choices.replaceChildren();q.choices.forEach(choice=>{const button=node('button','ff-quiz-choice');button.type='button';button.setAttribute('aria-pressed',String(answers[q.questionId]===choice.key));button.setAttribute('aria-label',choice.key+' '+choice.text);const inner=node('span','ff-quiz-card-inner');inner.setAttribute('aria-hidden','true');['front','back'].forEach(face=>{const surface=node('span','ff-quiz-card-face ff-quiz-card-'+face);surface.append(node('span','ff-quiz-choice-key',face==='front'?choice.key:'你的選擇'),artwork((index+q.choices.indexOf(choice))%4),node('span','ff-quiz-choice-text',choice.text),node('small','ff-quiz-card-caption',face==='front'?'跟隨直覺 · 點選此牌':'心有所選 · 靈感將至'));inner.append(surface);});button.append(inner);button.addEventListener('click',()=>{if(busy)return;const previous=answers[q.questionId],selected=previous!==choice.key;if(selected)answers[q.questionId]=choice.key;else delete answers[q.questionId];choices.querySelectorAll('button').forEach(b=>b.setAttribute('aria-pressed',String(selected&&b===button)));playCardSounds(selected,!!previous);syncNext();});choices.append(button);});
  $('.ff-quiz-next').textContent=index===run.questions.length-1?'看看我的果物推薦 →':'下一題 →';syncNext();
 }
 $('.ff-quiz-prev').addEventListener('click',()=>{if(!busy&&index>0){index--;renderQuestion();}});
 $('.ff-quiz-next').addEventListener('click',async()=>{if(busy||!answers[run.questions[index].questionId])return;if(index<run.questions.length-1){index++;renderQuestion();$('.ff-quiz-question-title').focus({preventScroll:true});return;}
  const token=begin();setBusy(true);message('');try{const data=await call('/'+run.quizId+'/submit',{token:run.token,answers});if(token!==generation)return;renderResult(data);state('result');root.dispatchEvent(new CustomEvent('ff-chat:quiz-completed'));$('.ff-quiz-result-title').focus({preventScroll:true});}catch(e){if(token===generation&&e.name!=='AbortError')message(e.message);}finally{if(token===generation)setBusy(false);}
 });
 function renderResult(data){$('.ff-quiz-result-emblem').replaceChildren(artwork(2));$('.ff-quiz-result-title').textContent=data.headline;$('.ff-quiz-result-description').textContent=data.description;
  $('.ff-quiz-result-count').textContent=data.recommendations.length===5?'為你選好的 5 款心動果物':'目前為你找到 '+data.recommendations.length+' 款可購買的果物';const list=$('.ff-quiz-recommendations');list.replaceChildren();
  data.recommendations.forEach((item,i)=>{const card=node('a','ff-quiz-product');card.href=prefix+'/front/product/view/?'+new URLSearchParams({productId:item.productId});const image=node('img','');image.src=prefix+'/front/api/product-image/'+Number(item.productId);image.alt=item.productName;image.width=72;image.height=72;image.loading='lazy';image.onerror=()=>{image.onerror=null;image.src=prefix+'/front/img/image-placeholder.svg';};
   const copy=node('div','ff-quiz-product-copy');copy.append(node('small','ff-quiz-product-rank','NO. '+(i+1)),node('strong','ff-quiz-product-name',item.productName),node('span','ff-quiz-product-price','NT$ '+Number(item.price).toLocaleString('zh-TW')+' 起'));const frame=node('span','ff-quiz-product-frame');frame.append(image);card.append(frame,copy,node('small','ff-quiz-product-link','探索這份鮮甜 →'));list.append(card);});
 }
 function returnToThemes(){if(busy)return;begin();stopMusic();run=null;answers={};index=0;message('');state('home');setBusy(false);dialog.scrollTop=0;$('#ff-quiz-title').focus({preventScroll:true});}
 $('.ff-quiz-return').addEventListener('click',returnToThemes);
 $('.ff-quiz-again').addEventListener('click',()=>start(lastTheme));$('.ff-quiz-home-button').addEventListener('click',returnToThemes);
 $('.ff-quiz-close').addEventListener('click',()=>dialog.close());let backdrop=false;
 function outside(e){const r=dialog.getBoundingClientRect();return e.target===dialog&&(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom);}
 dialog.addEventListener('pointerdown',e=>{backdrop=e.button===0&&outside(e);});dialog.addEventListener('pointercancel',()=>backdrop=false);dialog.addEventListener('click',e=>{const close=backdrop&&outside(e);backdrop=false;if(close)dialog.close();});
 dialog.addEventListener('close',()=>{stopMusic();generation++;controller?.abort();busy=false;backdrop=false;root.dispatchEvent(new CustomEvent('ff-chat:quiz-closed'));});root.addEventListener('ff-chat:quiz',open);root.addEventListener('ff-chat:account-reset',()=>{if(dialog.open)dialog.close();});
})();
