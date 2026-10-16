(() => {
 'use strict';
 const root=document.getElementById('ff-chat');if(!root||root.dataset.initialized)return;root.dataset.initialized='true';
 const $=selector=>root.querySelector(selector),launcher=$('.ff-chat-launcher'),panel=$('.ff-chat-panel'),invite=$('.ff-chat-invite');
 const input=$('textarea'),send=$('.ff-chat-send'),messages=$('.ff-chat-messages'),notice=$('.ff-chat-demo-notice');
 const status=$('.ff-chat-preview-tag'),badge=$('.ff-chat-unread'),files=$('.ff-chat-files'),previews=$('.ff-chat-attachments');
 const older=$('.ff-chat-older'),login=$('.ff-chat-login'),sound=$('.ff-chat-open-sound');
 const inviteButton=$('.ff-chat-invite-open'),inviteEyebrow=$('.ff-chat-invite-eyebrow');
 const originalInvite=Array.from(inviteButton.childNodes).map(node=>node.cloneNode(true)),originalEyebrow=inviteEyebrow.textContent,originalInviteLabel=inviteButton.getAttribute('aria-label');
 const api=root.dataset.api,wsPath=root.dataset.ws,reduced=matchMedia('(prefers-reduced-motion: reduce)');
 let open=false,busy=false,member=null,epoch=0,client=null,latest=0,syncedCursor=0,earliest=null,more=false,loading=false,catching=false;
 let unreadCount=0,selected=[],invitationTimer,hideTimer,closeTimer,pullTimer,readTimer,seenThrough=0,readThrough=0,checking=null;
 const rows=new Map();
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
  label.textContent='回覆 '+replyLabel(m.reply?.senderType);text.textContent=m.reply?.messageContent||'原訊息已無法顯示';box.append(label,text);return box;
 }
 function showReplyMenu(event){
  const row=event.target.closest('.ff-chat-message[data-id]');if(!row||busy||member===null)return;
  event.preventDefault();menuTarget=messageData.get(Number(row.dataset.id));if(!menuTarget)return;
  replyMenu.hidden=false;const rect=row.getBoundingClientRect();const x=event.clientX||rect.left+15,y=event.clientY||rect.top+15;
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
 function resetAccount(id){setReply(null);closeReplyMenu();messageData.clear();epoch++;member=id;client?.deactivate();client=null;rows.forEach(row=>row.remove());rows.clear();latest=0;syncedCursor=0;earliest=null;more=false;
  loading=false;catching=false;seenThrough=0;readThrough=0;clearTimeout(readTimer);input.value='';clearFiles();setBadge(0);older.hidden=true;
  login.hidden=id!==null;status.textContent=id===null?'請先登入':'連線中';updateInput();}
 async function authenticate(){
  if(checking)return checking;
  checking=(async()=>{try{
   const data=await call('/unread');if(member!==data.memberId)resetAccount(data.memberId);
   setBadge(data.unreadCount);login.hidden=true;updateInput();connect();if(open)await loadInitial();
  }catch(error){if(error.status===401){resetAccount(null);setNotice('請先登入會員，再與客服聯繫。');}
   else setNotice('系統忙碌中，請稍後再嘗試。');}
  })().finally(()=>checking=null);return checking;
 }
 function connect(){
  if(client||member===null)return;
  if(!window.StompJs){status.textContent='連線失敗';setNotice('即時連線未載入，請重新整理頁面。');return;}
  const token=epoch;
  client=new StompJs.Client({brokerURL:(location.protocol==='https:'?'wss://':'ws://')+location.host+wsPath,reconnectDelay:5000,
   heartbeatIncoming:10000,heartbeatOutgoing:10000});
  client.onConnect=()=>{if(token!==epoch)return;status.textContent='已連線';
   client.subscribe('/topic/chat/member/'+member,frame=>{
    if(token!==epoch)return;
    try{const event=JSON.parse(frame.body);if(event.memberId!==member)return;
     if(event.type==='MESSAGE'&&event.message){const m=event.message;const fresh=!rows.has(m.messageId);append(m);
      if(fresh&&m.senderType!==1)setBadge(unreadCount+1);
      if(open&&!document.hidden)checkVisible();}

     if(event.type==='MEMBER_READ')refreshUnread();
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
  const nearBottom=messages.scrollHeight-messages.scrollTop-messages.clientHeight<65;
  messageData.set(m.messageId,m);if(rows.has(m.messageId))return;
  const row=document.createElement('div'),body=document.createElement('div'),sender=document.createElement('span'),bubble=document.createElement('div'),meta=document.createElement('small');
  row.className='ff-chat-message'+(m.senderType===1?' ff-chat-message--user':'');row.dataset.id=m.messageId;row.dataset.sender=m.senderType;row.dataset.time=(m.createdAt||'').replace('T',' ').slice(0,16);row.tabIndex=0;
  sender.className='ff-chat-sender';sender.textContent=m.senderType===1?'您':m.senderType===2?'客服':'智慧鮮果';
  bubble.className='ff-chat-bubble';const quoted=quote(m);if(quoted)bubble.append(quoted);if(m.messageContent){const text=document.createElement('p');text.textContent=m.messageContent;bubble.append(text);}
  (m.imageIds||[]).forEach(id=>{const link=document.createElement('a'),img=document.createElement('img');link.href=api+'/images/'+id;link.target='_blank';link.rel='noopener';
   img.src=api+'/images/'+id+'/thumbnail';img.alt='聊天圖片';img.loading='lazy';link.append(img);bubble.append(link);});
  meta.className='ff-chat-unsent';meta.textContent=row.dataset.time;
  body.append(sender,bubble,meta);row.append(body);
  const next=Array.from(rows.entries()).filter(([id])=>id>m.messageId).sort((a,b)=>a[0]-b[0])[0];
  messages.insertBefore(row,next?next[1]:null);rows.set(m.messageId,row);latest=Math.max(latest,m.messageId);earliest=earliest===null?m.messageId:Math.min(earliest,m.messageId);
  if(open&&(nearBottom||m.senderType===1))messages.scrollTop=messages.scrollHeight;
 }
 async function loadInitial(){
  if(loading||member===null)return;if(latest){await catchUp();return;}loading=true;const token=epoch;setNotice('正在讀取對話…');
  try{const page=await call('/messages');if(token!==epoch)return;page.messages.forEach(append);more=page.hasMore;older.hidden=!more;syncedCursor=page.messages.length?page.messages[page.messages.length-1].messageId:0;setBadge(page.unreadCount);
   messages.scrollTop=messages.scrollHeight;setNotice('文字最多 1000 字；圖片最多 5 張，單張 5 MB。');checkVisible();
  }catch(error){if(token===epoch)handleError(error);}finally{if(token===epoch)loading=false;}
 }
 async function catchUp(){
  if(catching||member===null)return;catching=true;const token=epoch;let cursor=syncedCursor;
  try{let page;do{page=await call('/messages?afterId='+cursor);if(token!==epoch)return;page.messages.forEach(append);
    if(!page.messages.length)break;cursor=page.messages[page.messages.length-1].messageId;syncedCursor=cursor;
   }while(page.hasMore);if(token===epoch){setBadge(page.unreadCount);checkVisible();}
  }catch(error){if(token===epoch)handleError(error);}finally{if(token===epoch)catching=false;}
 }
 older.addEventListener('click',async()=>{if(loading||!more||earliest===null)return;loading=true;older.disabled=true;const token=epoch,height=messages.scrollHeight,top=messages.scrollTop;
  try{const page=await call('/messages?beforeId='+earliest);if(token!==epoch)return;page.messages.forEach(append);more=page.hasMore;older.hidden=!more;
   messages.scrollTop=top+messages.scrollHeight-height;
  }catch(error){if(token===epoch)handleError(error);}finally{if(token===epoch){loading=false;older.disabled=false;}}
 });
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
 function scheduleInvitation(delay=60000){clearTimeout(invitationTimer);invitationTimer=setTimeout(()=>{
  if(!open&&!document.hidden){
   if(unreadCount>0){inviteEyebrow.textContent='智慧鮮果提醒您';inviteButton.textContent='您有新訊息喔！';inviteButton.setAttribute('aria-label','您有新訊息喔，開啟客服聊天室');}
   else{inviteEyebrow.textContent=originalEyebrow;inviteButton.replaceChildren(...originalInvite.map(node=>node.cloneNode(true)));inviteButton.setAttribute('aria-label',originalInviteLabel);}
   invite.hidden=false;clearTimeout(hideTimer);hideTimer=setTimeout(()=>invite.hidden=true,8500);}scheduleInvitation();},delay);}
 function hideInvitation(){clearTimeout(hideTimer);invite.hidden=true;}
 function setOpen(next){
  clearTimeout(closeTimer);clearTimeout(pullTimer);hideInvitation();const origin=launcher.getBoundingClientRect();open=next;
  launcher.setAttribute('aria-expanded',String(open));launcher.setAttribute('aria-label',open?'收起智慧鮮果客服聊天室':'開啟智慧鮮果客服聊天室');
  root.classList.remove('is-closing');root.classList.toggle('is-open',open);
  if(open){panel.hidden=false;panel.inert=false;panel.style.setProperty('--chat-origin-x',(origin.left+origin.width/2-panel.offsetLeft)+'px');
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
 $('form').addEventListener('submit',async event=>{
  event.preventDefault();if(busy||member===null||(!input.value.trim()&&!selected.length))return;
  if(input.value.trim().length>1000){setNotice('訊息內容最多 1000 字。');return;}
  const body=new FormData();body.append('content',input.value.trim());if(replyTarget)body.append('replyMessageId',replyTarget.messageId);selected.forEach(item=>body.append('images',item.file));busy=true;updateInput();renderFiles();const token=epoch;
  try{const message=await call('/messages',{method:'POST',body});if(token!==epoch)return;append(message);input.value='';setReply(null);clearFiles();setNotice('訊息已送出，客服回覆會顯示在這裡。');}
  catch(error){if(token===epoch)handleError(error);}finally{busy=false;updateInput();renderFiles();if(open&&member!==null)input.focus({preventScroll:true});}
 });
 document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!replyMenu.hidden){event.preventDefault();event.stopPropagation();closeReplyMenu();return;}if(event.key==='Escape'&&open&&root.contains(document.activeElement)){event.preventDefault();event.stopPropagation();setOpen(false);}});
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)authenticate();});window.addEventListener('focus',()=>authenticate());
 window.addEventListener('pagehide',()=>{epoch++;client?.deactivate();client=null;if(sound)sound.pause();[invitationTimer,hideTimer,closeTimer,pullTimer,readTimer].forEach(clearTimeout);});
 window.addEventListener('pageshow',event=>{if(event.persisted){authenticate();scheduleInvitation(5000);}});
 updateInput();scheduleInvitation(2200);authenticate();
})();
