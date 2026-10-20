(() => {
 'use strict';
 const root=document.getElementById('ff-chat');if(!root||root.dataset.initialized)return;root.dataset.initialized='true';
 const $=selector=>root.querySelector(selector),launcher=$('.ff-chat-launcher'),panel=$('.ff-chat-panel'),invite=$('.ff-chat-invite');
 const input=$('textarea'),send=$('.ff-chat-send'),messages=$('.ff-chat-messages'),notice=$('.ff-chat-demo-notice');
 const status=$('.ff-chat-preview-tag'),badge=$('.ff-chat-unread'),files=$('.ff-chat-files'),previews=$('.ff-chat-attachments');
 const older=$('.ff-chat-older'),login=$('.ff-chat-login'),sound=$('.ff-chat-open-sound');
 const inviteButton=$('.ff-chat-invite-open'),inviteEyebrow=$('.ff-chat-invite-eyebrow');
 const originalInvite=Array.from(inviteButton.childNodes).map(node=>node.cloneNode(true)),originalEyebrow=inviteEyebrow.textContent,originalInviteLabel=inviteButton.getAttribute('aria-label');
 const imageDialog=$('.ff-chat-image-dialog'),fullImage=$('.ff-chat-image-full');
 $('.ff-chat-image-close').addEventListener('click',()=>imageDialog.close());
 let imageBackdropPress=false;
 function imageBackdrop(event){const rect=imageDialog.getBoundingClientRect();return event.target===imageDialog&&(event.clientX<rect.left||event.clientX>rect.right||event.clientY<rect.top||event.clientY>rect.bottom);}
 imageDialog.addEventListener('pointerdown',event=>{imageBackdropPress=event.button===0&&imageBackdrop(event);});
 imageDialog.addEventListener('pointercancel',()=>{imageBackdropPress=false;});
 imageDialog.addEventListener('click',event=>{const close=imageBackdropPress&&imageBackdrop(event);imageBackdropPress=false;if(close)imageDialog.close();});
 imageDialog.addEventListener('close',()=>{imageBackdropPress=false;fullImage.removeAttribute('src');});
 const api=root.dataset.api,wsPath=root.dataset.ws,reduced=matchMedia('(prefers-reduced-motion: reduce)');
 let open=false,busy=false,member=null,epoch=0,client=null,latest=0,syncedCursor=0,earliest=null,more=false,loading=false,catching=false;
 let unreadCount=0,selected=[],invitationTimer,hideTimer,closeTimer,pullTimer,readTimer,seenThrough=0,readThrough=0,checking=null;
 const welcome=$('.ff-chat-message--helper'),topics=$('.ff-chat-topics');
 let welcomeToken=null,welcomeShown=false;
 function setWelcomeToken(token){if(token===welcomeToken)return;welcomeToken=token;welcomeShown=false;
  try{welcomeShown=sessionStorage.getItem('fruitude:chat-welcome:'+api+':'+member)===token;}catch(ignored){}
 }
 function showWelcome(){
  if(!open||member===null||!welcomeToken)return;
  if(!welcomeShown){messages.append(welcome,topics);welcomeShown=true;
   try{sessionStorage.setItem('fruitude:chat-welcome:'+api+':'+member,welcomeToken);}catch(ignored){}
   welcome.hidden=false;topics.hidden=false;messages.scrollTop=messages.scrollHeight;
  }else if(!rows.size){welcome.hidden=false;topics.hidden=false;}
 }
 const rows=new Map();
 // Saved quiz cards are private member history, separate from messages, read receipts and staff topics.
 const quizRows=new Map();let quizBefore=null,quizMore=false,quizLoading=false,quizDirty=false;
 const quizOlder=document.createElement('button');quizOlder.type='button';quizOlder.className='ff-chat-older';quizOlder.textContent='載入較早測驗結果';quizOlder.hidden=true;older.after(quizOlder);
 function placeInTimeline(row){const next=Array.from(messages.children).find(node=>node!==row&&node.dataset.timeline&&node.dataset.timeline>row.dataset.timeline);messages.insertBefore(row,next||null);}
 function appendQuiz(entry){
  if(quizRows.has(entry.answerId))return;const row=document.createElement('div');row.className='ff-chat-message ff-chat-quiz-record';row.dataset.timeline=entry.createdAt||'';
  const avatar=welcome.querySelector('.ff-chat-message-avatar');if(avatar)row.append(avatar.cloneNode(true));
  const body=document.createElement('div'),label=document.createElement('span'),bubble=document.createElement('div'),theme=document.createElement('small'),title=document.createElement('strong'),description=document.createElement('p'),list=document.createElement('div'),time=document.createElement('small');
  label.className='ff-chat-sender';label.textContent='智慧鮮果 · 測驗結果';bubble.className='ff-chat-bubble ff-chat-quiz-summary';theme.className='ff-chat-quiz-theme';theme.textContent=entry.theme;title.textContent=entry.result.headline;description.textContent=entry.result.description;list.className='ff-chat-quiz-products';
  const prefix=api.replace(/\/api\/front\/chat$/,'');
  entry.result.recommendations.forEach(item=>{const link=document.createElement('a'),image=document.createElement('img'),copy=document.createElement('span'),name=document.createElement('strong'),price=document.createElement('small');
   link.href=prefix+'/front/product/view/?'+new URLSearchParams({productId:item.productId});image.src=prefix+'/front/api/product-image/'+Number(item.productId);image.alt=item.productName;image.width=48;image.height=48;image.loading='lazy';image.onerror=()=>{image.onerror=null;image.src=prefix+'/front/img/image-placeholder.svg';};
   name.textContent=item.productName;price.textContent='NT$ '+Number(item.price).toLocaleString('zh-TW')+' 起';copy.append(name,price);link.append(image,copy);list.append(link);
  });
  time.className='ff-chat-unsent';time.textContent=(entry.createdAt||'').replace('T',' ').slice(0,16);bubble.append(theme,title,description,list);body.append(label,bubble,time);row.append(body);placeInTimeline(row);quizRows.set(entry.answerId,row);
 }
 async function loadQuizHistory(earlier=false){
  if(member===null||quizLoading)return;quizLoading=true;quizOlder.disabled=true;const token=epoch,height=messages.scrollHeight,top=messages.scrollTop;
  try{const page=await call('/quizzes/results'+(earlier&&quizBefore?'?beforeId='+quizBefore:''));if(token!==epoch)return;
   page.results.forEach(appendQuiz);if(earlier||quizBefore===null){quizMore=page.hasMore;quizOlder.hidden=!quizMore;}if(page.results.length)quizBefore=Math.min(quizBefore??Infinity,...page.results.map(item=>item.answerId));
   if(earlier)messages.scrollTop=top+messages.scrollHeight-height;
  }catch(error){if(token===epoch)handleError(error);}finally{if(token===epoch){quizLoading=false;quizOlder.disabled=false;}}
 }
 quizOlder.addEventListener('click',()=>loadQuizHistory(true));
 root.addEventListener('ff-chat:quiz-completed',()=>{quizDirty=true;});
 root.addEventListener('ff-chat:quiz-closed',async()=>{if(!quizDirty)return;quizDirty=false;await loadQuizHistory();if(open)messages.scrollTop=messages.scrollHeight;});
 const messageData=new Map();let replyTarget=null,menuTarget=null;
 const replyDraft=document.createElement('div'),replyMenu=document.createElement('div'),replyAction=document.createElement('button');
 replyDraft.className='ff-chat-reply-draft';replyDraft.hidden=true;previews.before(replyDraft);
 replyMenu.className='ff-chat-reply-menu';replyMenu.hidden=true;replyMenu.setAttribute('role','menu');
 replyAction.type='button';replyAction.textContent='回覆該訊息';replyAction.setAttribute('role','menuitem');replyMenu.append(replyAction);root.append(replyMenu);
 function replyLabel(sender){return sender===1?'您':sender===2?'客服':sender===0?'智慧鮮果':'原訊息';}
 function replyText(m){const text=m.messageContent?.trim()||'[圖片訊息]';return Array.from(text).slice(0,100).join('')+(Array.from(text).length>100?'…':'');}
 function closeReplyMenu(){replyMenu.hidden=true;menuTarget=null;}
 function setReply(target){
  replyTarget=target;replyDraft.replaceChildren();replyDraft.hidden=!target;
  if(!target)return;
  const label=document.createElement('strong'),text=document.createElement('p'),cancel=document.createElement('button');
  label.textContent='回覆 '+replyLabel(target.senderType);text.textContent=replyText(target);cancel.type='button';cancel.textContent='×';cancel.setAttribute('aria-label','取消回覆');cancel.disabled=busy;
  cancel.addEventListener('click',()=>{if(!busy)setReply(null);});replyDraft.append(label,text,cancel);
 }
 function quote(m){
  if(!m.replyMessageId)return null;const box=document.createElement('div'),label=document.createElement('strong'),text=document.createElement('p');box.className='ff-chat-quoted';
  label.textContent='回覆 '+replyLabel(m.reply?.senderType);text.textContent=m.reply?.messageContent||'原訊息已無法顯示';box.append(label,text);
  if(m.reply?.senderType!=null){box.tabIndex=0;box.setAttribute('role','button');box.setAttribute('aria-label','跳到原訊息');
   const jump=event=>{if(event.type==='keydown'&&!['Enter',' '].includes(event.key))return;event.preventDefault();event.stopPropagation();jumpToReply(m.replyMessageId);};
   box.addEventListener('click',jump);box.addEventListener('keydown',jump);
  }else box.setAttribute('aria-disabled','true');
  return box;
 }
 function showReplyMenu(event){
  const row=event.target.closest('.ff-chat-message[data-id]');if(!row||busy||member===null)return;
  event.preventDefault();menuTarget=messageData.get(Number(row.dataset.id));if(!menuTarget)return;
  replyMenu.style.left='0px';replyMenu.style.top='0px';replyMenu.hidden=false;const rect=row.getBoundingClientRect();const x=event.clientX||rect.left+15,y=event.clientY||rect.top+15;
  replyMenu.style.left=Math.max(8,Math.min(x,innerWidth-replyMenu.offsetWidth-8))+'px';replyMenu.style.top=Math.max(8,Math.min(y,innerHeight-replyMenu.offsetHeight-8))+'px';replyAction.focus({preventScroll:true});
 }
 messages.addEventListener('contextmenu',showReplyMenu);
 messages.addEventListener('keydown',event=>{if(event.key==='ContextMenu'||(event.shiftKey&&event.key==='F10'))showReplyMenu(event);});
 replyAction.addEventListener('click',()=>{if(menuTarget&&!busy){setReply(menuTarget);input.focus({preventScroll:true});}closeReplyMenu();});
 document.addEventListener('pointerdown',event=>{if(!replyMenu.contains(event.target))closeReplyMenu();});
 window.addEventListener('resize',closeReplyMenu);messages.addEventListener('scroll',closeReplyMenu,{passive:true});

 function setNotice(text){notice.textContent=text;}
 function setBadge(n){const previous=unreadCount;unreadCount=Number(n)||0;badge.hidden=unreadCount<1;badge.textContent=unreadCount>99?'99+':String(unreadCount);if(!open&&previous===0&&unreadCount>0)scheduleInvitation(2000);if(previous>0&&unreadCount===0)hideInvitation();}
 async function call(path,options={}){
  const response=await fetch(api+path,{...options,credentials:'same-origin',cache:'no-store',headers:{'X-Requested-With':'XMLHttpRequest',...options.headers}});
  const body=await response.json().catch(()=>({}));
  if(!response.ok){const error=new Error(body.message||'系統忙碌中，請稍後再嘗試。');error.status=response.status;throw error;}
  return body;
 }
 function updateInput(){send.disabled=busy||member===null||(!input.value.trim()&&!selected.length);input.disabled=busy;
  files.disabled=busy||member===null;input.style.height='auto';input.style.height=Math.min(90,Math.max(30,input.scrollHeight))+'px';}
 function clearFiles(){selected.forEach(item=>URL.revokeObjectURL(item.url));selected=[];renderFiles();}
 function renderFiles(){previews.replaceChildren();selected.forEach((item,index)=>{
  const box=document.createElement('div'),img=document.createElement('img'),remove=document.createElement('button');
  img.src=item.url;img.alt=item.file.name;remove.type='button';remove.textContent='×';remove.disabled=busy;remove.setAttribute('aria-label','移除圖片 '+item.file.name);
  remove.addEventListener('click',()=>{URL.revokeObjectURL(item.url);selected.splice(index,1);renderFiles();updateInput();});box.append(img,remove);previews.append(box);
 });previews.hidden=!selected.length;}
 function resetAccount(id){root.dispatchEvent(new CustomEvent('ff-chat:account-reset'));setReply(null);closeReplyMenu();messageData.clear();welcomeToken=null;welcomeShown=false;welcome.hidden=id!==null;topics.hidden=id!==null;epoch++;member=id;client?.deactivate();client=null;rows.forEach(row=>row.remove());rows.clear();quizRows.forEach(row=>row.remove());quizRows.clear();quizBefore=null;quizMore=false;quizLoading=false;quizDirty=false;quizOlder.hidden=true;latest=0;syncedCursor=0;earliest=null;more=false;
  loading=false;catching=false;seenThrough=0;readThrough=0;clearTimeout(readTimer);input.value='';clearFiles();setBadge(0);older.hidden=true;
  login.hidden=id!==null;status.textContent=id===null?'請先登入':'連線中';updateInput();}
 async function authenticate(){
  if(checking)return checking;
  checking=(async()=>{try{
   const data=await call('/unread');if(member!==data.memberId)resetAccount(data.memberId);
   setWelcomeToken(data.welcomeToken||String(data.memberId));setBadge(data.unreadCount);login.hidden=true;updateInput();connect();if(open)await loadInitial();
  }catch(error){if(error.status===401){resetAccount(null);setNotice('');}
   else setNotice('系統忙碌中，請稍後再嘗試。');}
  })().finally(()=>checking=null);return checking;
 }
 function connect(){
  if(client||member===null)return;
  if(!window.StompJs){status.textContent='連線失敗';setNotice('即時連線未載入，請重新整理頁面。');return;}
  const token=epoch;
  client=new StompJs.Client({brokerURL:(location.protocol==='https:'?'wss://':'ws://')+location.host+wsPath,reconnectDelay:5000,
   heartbeatIncoming:10000,heartbeatOutgoing:10000});
  client.onConnect=()=>{if(token!==epoch)return;status.textContent='';
   client.subscribe('/topic/chat/member/'+member,frame=>{
    if(token!==epoch)return;
    try{const event=JSON.parse(frame.body);if(event.memberId!==member)return;
     if(event.type==='MESSAGE'&&event.message){const m=event.message;const fresh=!rows.has(m.messageId);append(m);
      if(fresh&&m.senderType!==1)setBadge(unreadCount+1);
      if(open&&!document.hidden)checkVisible();}

     if(event.type==='MEMBER_READ')refreshUnread();
     // 客服把訊息對會員隱藏：從畫面移除；取消隱藏：依時間插回原位置。兩者都由 /unread 重算未讀數，不自己加減
     if(event.type==='HIDDEN'){const id=event.readThroughId,row=rows.get(id);if(row){row.remove();rows.delete(id);}messageData.delete(id);
      if(replyTarget&&replyTarget.messageId===id)setReply(null);refreshUnread();}
     if(event.type==='SHOWN'&&event.message){append(event.message);refreshUnread();}
    }catch(error){setNotice('訊息更新失敗，請重新開啟聊天室。');}
   });
   if(open){if(latest)catchUp();else loadInitial();}else refreshUnread();
  };
  client.onWebSocketClose=()=>{if(token===epoch)status.textContent='重新連線中';};
  client.onStompError=()=>{if(token===epoch){status.textContent='連線中斷';authenticate();}};
  client.activate();
 }
 async function refreshUnread(){const token=epoch;try{const data=await call('/unread');if(token!==epoch)return;
  if(data.memberId!==member){resetAccount(data.memberId);connect();if(open)loadInitial();return;}setBadge(data.unreadCount);
 }catch(error){if(token===epoch&&error.status===401)resetAccount(null);}}

 function append(m){
  if(m.inquiryProduct || (m.senderType===0 && m.messageContent?.startsWith('客戶詢問來源\n商品：')))return;
  const nearBottom=messages.scrollHeight-messages.scrollTop-messages.clientHeight<65;
  messageData.set(m.messageId,m);if(rows.has(m.messageId))return;
  const row=document.createElement('div'),body=document.createElement('div'),sender=document.createElement('span'),bubble=document.createElement('div'),meta=document.createElement('small');
  row.className='ff-chat-message'+(m.senderType===1?' ff-chat-message--user':'');row.dataset.id=m.messageId;row.dataset.timeline=m.createdAt||'';row.dataset.sender=m.senderType;row.dataset.time=(m.createdAt||'').replace('T',' ').slice(0,16);row.tabIndex=0;
  sender.className='ff-chat-sender';sender.textContent=m.senderType===1?'您':m.senderType===2?'客服':'智慧鮮果';
  bubble.className='ff-chat-bubble';const quoted=quote(m);if(quoted)bubble.append(quoted);if(m.messageContent){const text=document.createElement('p');text.textContent=m.messageContent;bubble.append(text);}
  const imageIds=m.imageIds||[],imageGrid=document.createElement('div');imageGrid.className='ff-chat-image-grid'+(imageIds.length===1?' ff-chat-image-grid--single':'');
  imageIds.forEach(id=>{const link=document.createElement('a'),img=document.createElement('img');link.href=api+'/images/'+id;link.setAttribute('aria-label','放大聊天圖片');link.addEventListener('click',event=>{event.preventDefault();fullImage.src=link.href;if(!imageDialog.open)imageDialog.showModal();});
   img.src=api+'/images/'+id+'/thumbnail';img.alt='聊天圖片';img.loading='lazy';link.append(img);imageGrid.append(link);});
  if(imageIds.length)bubble.append(imageGrid);
  meta.className='ff-chat-unsent';meta.textContent=row.dataset.time;
  if(m.senderType!==1&&m.senderType!==2){const avatar=$('.ff-chat-message--helper .ff-chat-message-avatar');if(avatar)row.append(avatar.cloneNode(true));}
  body.append(sender,bubble,meta);row.append(body);
  placeInTimeline(row);rows.set(m.messageId,row);latest=Math.max(latest,m.messageId);earliest=earliest===null?m.messageId:Math.min(earliest,m.messageId);
  if(open&&!jumping&&(nearBottom||m.senderType===1))messages.scrollTop=messages.scrollHeight;
 }
 async function loadInitial(){
  if(loading||member===null)return;if(latest){await catchUp();await loadQuizHistory();return;}loading=true;const token=epoch;setNotice('正在讀取對話…');
  try{const page=await call('/messages');if(token!==epoch)return;page.messages.forEach(append);more=page.hasMore;older.hidden=!more;syncedCursor=page.messages.length?page.messages[page.messages.length-1].messageId:0;setBadge(page.unreadCount);
   await loadQuizHistory();if(token!==epoch)return;showWelcome();messages.scrollTop=messages.scrollHeight;setNotice('');checkVisible();
  }catch(error){if(token===epoch)handleError(error);}finally{if(token===epoch)loading=false;}
 }
 async function catchUp(){
  if(catching||member===null)return;catching=true;const token=epoch;let cursor=syncedCursor;
  try{let page;do{page=await call('/messages?afterId='+cursor);if(token!==epoch)return;page.messages.forEach(append);
    if(!page.messages.length)break;cursor=page.messages[page.messages.length-1].messageId;syncedCursor=cursor;
   }while(page.hasMore);if(token===epoch){setBadge(page.unreadCount);showWelcome();checkVisible();}
  }catch(error){if(token===epoch)handleError(error);}finally{if(token===epoch)catching=false;}
 }
 let jumping=false,locatedTimer;
 async function loadOlderMessages(){
  if(loading||!more||earliest===null)return false;loading=true;older.disabled=true;
  const token=epoch,height=messages.scrollHeight,top=messages.scrollTop,before=earliest;
  try{const page=await call('/messages?beforeId='+before);if(token!==epoch)return false;
   page.messages.forEach(append);more=page.hasMore;older.hidden=!more;messages.scrollTop=top+messages.scrollHeight-height;
   return earliest<before;
  }finally{if(token===epoch){loading=false;older.disabled=false;}}
 }
 older.addEventListener('click',async()=>{if(jumping)return;try{await loadOlderMessages();}catch(error){handleError(error);}});
 async function jumpToReply(id){
  if(jumping)return;if(loading){setNotice('正在讀取訊息，請稍候再試。');return;}
  jumping=true;const token=epoch;
  try{
   while(!rows.has(id)&&more&&earliest!==null&&earliest>id){if(!await loadOlderMessages())break;if(token!==epoch||!open)return;}
   if(token!==epoch||!open)return;
   const row=rows.get(id);if(!row){setNotice('原訊息已無法顯示。');return;}
   setNotice('');clearTimeout(locatedTimer);messages.querySelectorAll('.ff-chat-message--located').forEach(node=>node.classList.remove('ff-chat-message--located'));
   const bounds=messages.getBoundingClientRect(),target=row.getBoundingClientRect();
   messages.scrollTo({top:messages.scrollTop+target.top-bounds.top-Math.max(0,(messages.clientHeight-row.offsetHeight)/2),behavior:reduced.matches?'instant':'smooth'});
   row.classList.add('ff-chat-message--located');row.focus({preventScroll:true});locatedTimer=setTimeout(()=>row.classList.remove('ff-chat-message--located'),2200);checkVisible();
  }catch(error){if(token===epoch)handleError(error);}finally{jumping=false;}
 }
 function checkVisible(){
  if(!open||document.hidden||member===null)return;const bounds=messages.getBoundingClientRect();
  rows.forEach((row,id)=>{const rect=row.getBoundingClientRect();if(row.dataset.sender!=='1'&&rect.top<bounds.bottom&&rect.bottom>bounds.top)seenThrough=Math.max(seenThrough,id);});
  if(seenThrough<=readThrough)return;clearTimeout(readTimer);readTimer=setTimeout(markRead,200);
 }
 async function markRead(){if(!open||document.hidden||member===null)return;const through=seenThrough,token=epoch;
  try{const body=new URLSearchParams({throughId:String(through)});const data=await call('/read',{method:'POST',body});if(token!==epoch)return;readThrough=Math.max(readThrough,through);setBadge(data.unreadCount);
  }catch(error){if(token===epoch&&error.status===401)resetAccount(null);}}
 messages.addEventListener('scroll',checkVisible,{passive:true});
 function handleError(error){if(error.status===401){resetAccount(null);setNotice('登入已逾時，請重新登入。');}else setNotice(error.message||'系統忙碌中，請稍後再嘗試。');}
 let invitationVariant=0,cartWasCovering=false;
 const invitePath=location.pathname.replace(/;jsessionid=[^/]+/g,''),frontPage=invitePath.replace(/^.*?\/front(?=\/|$)/,''),cartReturnKey='fruitude:chat-cart-return:'+api;
 let cartReturnPending=false;
 try{const saved=JSON.parse(sessionStorage.getItem(cartReturnKey)||'null');cartReturnPending=!!saved&&saved.path===invitePath+location.search&&saved.expires>Date.now();if(saved&&saved.expires<=Date.now())sessionStorage.removeItem(cartReturnKey);}catch(ignored){}
 function favoritesAvailable(){const grid=document.getElementById('favorite-grid');return member!==null&&!!grid&&!grid.hidden&&!!grid.querySelector('.favorite-card');}
 function cartCoversLauncher(){const apple=launcher.getBoundingClientRect();return Array.from(document.querySelectorAll('.w-commerce-commercecartcontainerwrapper')).some(wrapper=>{
  if(wrapper.hidden||getComputedStyle(wrapper).display==='none')return false;
  const rect=wrapper.getBoundingClientRect();return rect.width>0&&rect.height>0&&rect.left<apple.right&&rect.right>apple.left&&rect.top<apple.bottom&&rect.bottom>apple.top;
 });}
 function watchCart(){const covering=cartCoversLauncher();if(covering){
   if(!cartWasCovering){cartReturnPending=true;try{sessionStorage.setItem(cartReturnKey,JSON.stringify({path:invitePath+location.search,expires:Date.now()+30*60*1000}));}catch(ignored){}}
   hideInvitation();
  }else if(cartWasCovering)scheduleInvitation(700);
  cartWasCovering=covering;
 }
 const cartObserver=new MutationObserver(watchCart);document.querySelectorAll('.w-commerce-commercecartcontainerwrapper').forEach(wrapper=>cartObserver.observe(wrapper,{attributes:true,attributeFilter:['class','style','hidden']}));
 window.addEventListener('resize',watchCart);window.addEventListener('pageshow',watchCart);watchCart();
 function setInvitation(lines,eyebrow,context){inviteEyebrow.textContent=eyebrow;invite.dataset.context=context;
  const content=[];lines.forEach((line,i)=>{if(i)content.push(document.createElement('br'));content.push(document.createTextNode(line));});inviteButton.replaceChildren(...content);inviteButton.setAttribute('aria-label',lines.join('')+'開啟客服聊天室');
 }
 function scheduleInvitation(delay=60000){clearTimeout(invitationTimer);invitationTimer=setTimeout(()=>{
  if(!open&&!document.hidden&&!cartCoversLauncher()){
   if(unreadCount>0)setInvitation(['您有新訊息喔！'],'智慧鮮果提醒您','unread');
   else if(cartReturnPending){setInvitation(['剛剛購物車擋到我了！','我知道你一定是因為','沒有買蘋果，','才又回來了吧 ～'],'小蘋果又回來囉','cart-return');cartReturnPending=false;try{const saved=JSON.parse(sessionStorage.getItem(cartReturnKey)||'null');if(saved?.path===invitePath+location.search)sessionStorage.removeItem(cartReturnKey);}catch(ignored){}}
   else{
    const options=[];
    if(/^\/farmers(?:\/|$)/.test(frontPage))options.push({lines:['我們這些小農的農產品','都很棒喔！請你有機會','一定要嘗試看看！'],eyebrow:'小蘋果的小農推薦',context:'farmers'});
    if(/^\/about\/member\/favorites(?:\/|$)/.test(frontPage)&&favoritesAvailable())options.push({lines:['我看您收藏了些商品，','心動不如馬上行動，','趕緊下單吧！'],eyebrow:'小蘋果看見你的心動',context:'favorites'});
    options.push({lines:['想不到要吃什麼水果，','也可以找我推薦喔！'],eyebrow:'讓智慧鮮果幫您挑選',context:'recommend'},null);
    const chosen=options[invitationVariant++%options.length];
    if(chosen)setInvitation(chosen.lines,chosen.eyebrow,chosen.context);
    else{invite.dataset.context='welcome';inviteEyebrow.textContent=originalEyebrow;inviteButton.replaceChildren(...originalInvite.map(node=>node.cloneNode(true)));inviteButton.setAttribute('aria-label',originalInviteLabel);}
   }
   invite.hidden=false;clearTimeout(hideTimer);hideTimer=setTimeout(()=>invite.hidden=true,8500);
  }scheduleInvitation();},delay);}
 function hideInvitation(){clearTimeout(hideTimer);invite.hidden=true;}
 window.addEventListener('favorite:changed',()=>{if(invite.dataset.context==='favorites'&&!favoritesAvailable())hideInvitation();});
 function productBuySource(){return document.querySelector('[data-node-type="x-commerce-add-to-cart-form"] .w-commerce-commercebuynowbutton');}
 function syncProductBuy(){const button=$('.ff-chat-product-buy'),source=productBuySource();if(button)button.disabled=!source||source.hidden||source.disabled||source.getAttribute('aria-disabled')==='true'||source.style.display==='none'||source.style.visibility==='hidden'||source.inert||source.getAttribute('aria-hidden')==='true';}
 $('.ff-chat-product-buy').addEventListener('click',()=>{syncProductBuy();if($('.ff-chat-product-buy').disabled)return;productBuySource().click();syncProductBuy();});
 const buySource=productBuySource();if(buySource)new MutationObserver(syncProductBuy).observe(buySource,{attributes:true,attributeFilter:['disabled','hidden','style','aria-disabled','aria-hidden','inert']});
 function updateProductContext(){
  const hint=$('.ff-chat-product-context'),name=document.getElementById('pv-name')?.textContent.trim();
  if(!hint)return;
  hint.hidden=!name;panel.classList.toggle('has-product-context',!!name);if(!name)return;
  const select=document.getElementById('pv-sku'),option=select?.options[select.selectedIndex];
  $('.ff-chat-product-name').textContent=name+(option?.textContent.trim()?' '+option.textContent.trim():'');
  syncProductBuy();
  const price=Number(option?.dataset.price),priceLabel=$('.ff-chat-product-price');
  priceLabel.hidden=!Number.isFinite(price);priceLabel.textContent=Number.isFinite(price)?'NT$ '+price.toLocaleString('zh-TW'):'';
  const image=$('.ff-chat-product-image'),first=document.querySelector('#pv-thumbnails .pv-thumbnail img');
  if(!image.dataset.placeholder)image.dataset.placeholder=image.getAttribute('src');
  image.onerror=()=>{image.onerror=null;image.src=image.dataset.placeholder;};
  image.alt=name;image.src=first?.getAttribute('src')||option?.dataset.imageUrl||image.dataset.placeholder;
 }
 document.addEventListener('change',event=>{if(open&&event.target.id==='pv-sku')updateProductContext();});
 function updatePanelBounds(){
  const nav=document.querySelector('.nav-bar'),bottom=nav?nav.getBoundingClientRect().bottom:0;
  root.style.setProperty('--chat-top-clearance',Math.min(innerHeight,Math.max(0,bottom)+12)+'px');
 }
 let boundsFrame=0;
 function queuePanelBounds(){if(!open||boundsFrame)return;boundsFrame=requestAnimationFrame(()=>{boundsFrame=0;updatePanelBounds();});}
 window.addEventListener('resize',queuePanelBounds);
 window.addEventListener('scroll',queuePanelBounds,{passive:true});
 const navigation=document.querySelector('.nav-bar');if(navigation)new ResizeObserver(queuePanelBounds).observe(navigation);
 function setOpen(next){
  clearTimeout(closeTimer);clearTimeout(pullTimer);hideInvitation();const origin=launcher.getBoundingClientRect();open=next;
  launcher.setAttribute('aria-expanded',String(open));launcher.setAttribute('aria-label',open?'收起智慧鮮果客服聊天室':'開啟智慧鮮果客服聊天室');
  root.classList.remove('is-closing');root.classList.toggle('is-open',open);
  if(open){updatePanelBounds();updateProductContext();panel.hidden=false;panel.inert=false;panel.style.setProperty('--chat-origin-x',(origin.left+origin.width/2-panel.offsetLeft)+'px');
   panel.style.setProperty('--chat-origin-y',(origin.top+origin.height/2-panel.offsetTop)+'px');launcher.inert=true;root.classList.add('is-pulling');
   pullTimer=setTimeout(()=>root.classList.remove('is-pulling'),740);authenticate().then(()=>{if(open){messages.scrollTop=messages.scrollHeight;checkVisible();}});requestAnimationFrame(()=>{if(open)input.focus({preventScroll:true});});
  }else{closeReplyMenu();root.classList.remove('is-pulling');launcher.inert=false;panel.inert=true;root.classList.add('is-closing');
   closeTimer=setTimeout(()=>{if(!open){panel.hidden=true;root.classList.remove('is-closing');}},reduced.matches?0:210);launcher.focus({preventScroll:true});}
 }
 function openFromClick(){if(open)return;if(sound){sound.volume=.55;sound.currentTime=0;const playing=sound.play();if(playing)playing.catch(()=>{});}setOpen(true);}
 launcher.addEventListener('click',openFromClick);invite.addEventListener('click',event=>{if(!event.target.closest('.ff-chat-invite-close'))openFromClick();});
 $('.ff-chat-close').addEventListener('click',()=>setOpen(false));$('.ff-chat-invite-close').addEventListener('click',()=>{hideInvitation();scheduleInvitation(120000);});
 input.addEventListener('input',updateInput);root.querySelectorAll('[data-chat-topic]').forEach(button=>button.addEventListener('click',()=>{input.value=button.dataset.chatTopic;updateInput();input.focus({preventScroll:true});}));
 input.addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.shiftKey&&!event.isComposing){event.preventDefault();$('form').requestSubmit();}});
 files.addEventListener('change',()=>{for(const file of files.files){if(selected.length>=5){setNotice('一則訊息最多 5 張圖片。');break;}
  if((file.type&&!['image/png','image/jpeg','image/gif'].includes(file.type))||file.size>5*1024*1024){setNotice('圖片限 PNG、JPEG、GIF，單張最多 5 MB。');continue;}
  selected.push({file,url:URL.createObjectURL(file)});}files.value='';renderFiles();updateInput();});

 function isQuizRequest(text){const value=text.normalize('NFKC').replace(/[\s\p{Z}]+/gu,'');if(/(?:不要|不用|不想|不需要).*(?:推薦|測驗)/u.test(value))return false;return /(?:推薦.*(?:水果|果物)|(?:水果|果物).*推薦|水果測驗|心理測驗|小測驗)/u.test(value);}
 function chatSearchAction(text){
  let command=text.normalize('NFKC').trim().replace(/[。！？!?]+$/u,'');
  command=command.replace(/^(?:請問)?(?:可以|能不能|可不可以)?(?:請|麻煩)?(?:我)?(?:想要|想|要)?(?:幫我|幫忙)?/u,'').trim();
  const match=command.match(/^(?:搜尋|搜索|查找|找找|找)(?:一下|看看)?[\s:：]*(.+)$/u);
  if(!match)return null;
  let q=match[1].replace(/(?:可以嗎|好嗎|嗎|謝謝)$/u,'').trim();
  if(!q||/功能|怎麼|如何|問題|失敗|不能|無法/u.test(q)||/^(?:訂單|物流|客服|退款|退貨|會員|付款|信用卡)/u.test(q))return null;
  if(/^(?:熱銷|暢銷|人氣|熱賣)(?:商品|水果)?$/u.test(q))return {url:root.dataset.bestSellersUrl};
  q=q.replace(/的(?:商品|水果)$/u,'').trim();
  if(q.length>20)return {error:'搜尋關鍵字最多 20 個字，請縮短後再試。'};
  return {url:root.dataset.searchUrl+'?'+new URLSearchParams({q}).toString(),q};
 }
 function followChatSearch(action){
  if(action.q)try{localStorage.setItem('fruitude:product-search:v1',JSON.stringify({q:action.q,category:'',origin:'',vendor:'',promotion:'',highRated:false}));}catch(ignored){}
  window.location.assign(action.url);
 }
 $('form').addEventListener('submit',async event=>{
  event.preventDefault();if(busy||member===null||(!input.value.trim()&&!selected.length))return;
  if(input.value.trim().length>1000){setNotice('訊息內容最多 1000 字。');return;}
  const wantsQuiz=isQuizRequest(input.value.trim()),searchAction=wantsQuiz?null:chatSearchAction(input.value.trim());if(searchAction?.error){setNotice(searchAction.error);return;}
  const productSku=document.getElementById('pv-sku');
  const body=new FormData();body.append('content',input.value.trim());if(!wantsQuiz&&document.getElementById('pv-name')&&productSku?.value)body.append('productSkuId',productSku.value);if(replyTarget)body.append('replyMessageId',replyTarget.messageId);selected.forEach(item=>body.append('images',item.file));busy=true;updateInput();renderFiles();const token=epoch;
  try{const message=await call('/messages',{method:'POST',body});if(token!==epoch)return;const quizAction=message.action==='FRUIT_QUIZ';if(!quizAction)append(message);input.value='';setReply(null);clearFiles();setNotice('');if(quizAction||wantsQuiz)root.dispatchEvent(new CustomEvent('ff-chat:quiz'));else if(searchAction)followChatSearch(searchAction);}
  catch(error){if(token===epoch)handleError(error);}finally{busy=false;updateInput();renderFiles();if(open&&member!==null&&!root.querySelector('.ff-quiz-dialog')?.open)input.focus({preventScroll:true});}
 });
 document.addEventListener('keydown',event=>{if(imageDialog.open||root.querySelector('.ff-quiz-dialog')?.open)return;if(event.key==='Escape'&&!replyMenu.hidden){event.preventDefault();event.stopPropagation();closeReplyMenu();return;}if(event.key==='Escape'&&open&&root.contains(document.activeElement)){event.preventDefault();event.stopPropagation();setOpen(false);}});
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)authenticate();});window.addEventListener('focus',()=>authenticate());
 window.addEventListener('pagehide',()=>{epoch++;client?.deactivate();client=null;if(sound)sound.pause();[invitationTimer,hideTimer,closeTimer,pullTimer,readTimer].forEach(clearTimeout);});
 window.addEventListener('pageshow',event=>{if(event.persisted){authenticate();scheduleInvitation(5000);}});
 updateInput();scheduleInvitation(2200);authenticate();
})();
