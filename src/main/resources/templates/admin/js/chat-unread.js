(() => {
 'use strict';
 const badge=document.getElementById('admin-chat-unread');
 if(!badge||badge.dataset.initialized)return;badge.dataset.initialized='true';
 const toggle=badge.closest('button'),menuItem=toggle.closest('.dropdown'),chatUrl=badge.dataset.chatUrl,chatPath=new URL(chatUrl,location.href).pathname;
 const list=document.getElementById('admin-chat-list'),empty=document.getElementById('admin-chat-empty'),summary=document.getElementById('admin-chat-summary');
 let client=null,pending=false,dirty=false,stopped=false,library=null,listRequest=0;
 function display(count){
  const total=Math.max(0,Number(count)||0);badge.hidden=total===0;badge.textContent=total>99?'99+':String(total);
  summary.textContent=total+' 則未讀';
  toggle.setAttribute('aria-label',total?'客服訊息，未讀 '+total+' 則':'客服訊息');
 }
 function formatTime(value){
  if(!value)return '';const time=new Date(value);if(Number.isNaN(time.getTime()))return '';
  const pad=number=>String(number).padStart(2,'0');
  return pad(time.getMonth()+1)+'/'+pad(time.getDate())+' '+pad(time.getHours())+':'+pad(time.getMinutes());
 }
 function renderConversations(rows){
  list.replaceChildren();
  rows.forEach(item=>{
   const row=document.createElement('a');row.className='admin-chat-row';row.href=chatUrl;
   const symbol=document.createElement('span');symbol.className='admin-chat-symbol';
   const icon=document.createElement('i');icon.className='fas fa-user';icon.setAttribute('aria-hidden','true');symbol.append(icon);
   const copy=document.createElement('span');copy.className='admin-chat-copy';
   const name=document.createElement('strong');name.textContent=item.memberName||('會員 #'+item.memberId);
   const time=document.createElement('span');time.textContent=formatTime(item.lastMessageAt);copy.append(name,time);
   const count=document.createElement('span');count.className='admin-chat-count';count.textContent=String(item.unreadCount);
   row.append(symbol,copy,count);list.append(row);
  });
  empty.textContent='目前沒有新訊息';empty.hidden=rows.length>0;
 }
 // Only members with unread messages are listed, newest conversation first.
 async function loadConversations(){
  if(stopped)return;const request=++listRequest;
  try{
   const response=await fetch(badge.dataset.listApi,{credentials:'same-origin',cache:'no-store'});
   if(request!==listRequest)return;
   if(!response.ok||!response.headers.get('content-type')?.includes('application/json'))throw new Error('unavailable');
   const rows=(await response.json()).filter(item=>Number(item.unreadCount)>0)
    .sort((a,b)=>String(b.lastMessageAt||'').localeCompare(String(a.lastMessageAt||'')));
   if(request===listRequest)renderConversations(rows);
  }catch(error){if(request===listRequest){list.replaceChildren();empty.textContent='訊息暫時無法載入';empty.hidden=false;}}
 }
 async function refresh(){
  if(stopped)return;if(pending){dirty=true;return;}pending=true;
  try{
   const response=await fetch(badge.dataset.api,{credentials:'same-origin',cache:'no-store'});
   if(response.status===401||response.status===403){display(0);stopped=true;client?.deactivate();return;}
   if(!response.ok||!response.headers.get('content-type')?.includes('application/json'))return;
   display((await response.json()).unreadCount);
   if(menuItem.classList.contains('show'))loadConversations();
  }catch(error){/* Preserve the last known total during a temporary connection failure. */}
  finally{pending=false;if(dirty&&!stopped){dirty=false;refresh();}}
 }
 function loadLibrary(){
  if(window.StompJs)return Promise.resolve();if(library)return library;
  library=new Promise((resolve,reject)=>{
   const script=document.createElement('script');script.src='https://cdn.jsdelivr.net/npm/@stomp/stompjs@7.0.0/bundles/stomp.umd.min.js';
   script.onload=resolve;script.onerror=()=>{script.remove();library=null;reject(new Error('STOMP unavailable'));};document.head.append(script);
  });return library;
 }
 async function connect(){
  // The chat page already owns a connection and forwards its events to this badge.
  if(location.pathname.replace(/\/$/,'')===chatPath.replace(/\/$/,''))return;
  if(client||stopped)return;
  try{await loadLibrary();if(client||stopped||!window.StompJs)return;
   client=new StompJs.Client({brokerURL:(location.protocol==='https:'?'wss://':'ws://')+location.host+badge.dataset.ws,reconnectDelay:5000});
   client.onConnect=()=>{client.subscribe('/topic/chat/staff',frame=>{
    try{const type=JSON.parse(frame.body).type;if(type==='MESSAGE'||type==='READ')refresh();}catch(error){}
   });refresh();};client.activate();
  }catch(error){/* Initial count and focus refresh remain available if the library cannot load. */}
 }
 toggle.addEventListener('click',loadConversations);
 document.addEventListener('fruitude:admin-chat-update',refresh);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden){refresh();connect();}});
 window.addEventListener('focus',()=>{refresh();connect();});
 window.addEventListener('pagehide',()=>{client?.deactivate();client=null;});
 window.addEventListener('pageshow',event=>{if(event.persisted){stopped=false;refresh();connect();}});
 refresh();connect();
})();
