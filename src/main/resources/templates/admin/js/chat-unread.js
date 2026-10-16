(() => {
 'use strict';
 const badge=document.getElementById('admin-chat-unread');
 if(!badge||badge.dataset.initialized)return;badge.dataset.initialized='true';
 const link=badge.closest('a'),chatPath=new URL(link.href).pathname;
 let client=null,pending=false,dirty=false,stopped=false,library=null;
 function display(count){
  const total=Math.max(0,Number(count)||0);badge.hidden=total===0;badge.textContent=String(total);
  badge.setAttribute('aria-label','客服未讀訊息 '+total+' 則');link.setAttribute('aria-label',total?'客服聊天室，未讀 '+total+' 則':'客服聊天室');
 }
 async function refresh(){
  if(stopped)return;if(pending){dirty=true;return;}pending=true;
  try{
   const response=await fetch(badge.dataset.api,{credentials:'same-origin',cache:'no-store'});
   if(response.status===401||response.status===403){display(0);stopped=true;client?.deactivate();return;}
   if(!response.ok||!response.headers.get('content-type')?.includes('application/json'))return;
   display((await response.json()).unreadCount);
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
 document.addEventListener('fruitude:admin-chat-update',refresh);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden){refresh();connect();}});
 window.addEventListener('focus',()=>{refresh();connect();});
 window.addEventListener('pagehide',()=>{client?.deactivate();client=null;});
 window.addEventListener('pageshow',event=>{if(event.persisted){stopped=false;refresh();connect();}});
 refresh();connect();
})();
