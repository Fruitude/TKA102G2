(() => {
 'use strict';
 const root=document.getElementById('ff-chat');if(!root || root.dataset.initialized)return;root.dataset.initialized='true';
 const launcher=root.querySelector('.ff-chat-launcher'),panel=root.querySelector('.ff-chat-panel'),invite=root.querySelector('.ff-chat-invite');
 const input=root.querySelector('textarea'),send=root.querySelector('.ff-chat-send'),messages=root.querySelector('.ff-chat-messages');
 const openSound=root.querySelector('.ff-chat-open-sound');
 function playOpenSound(){
  if(!openSound)return;
  openSound.volume=.55;openSound.currentTime=0;
  const playing=openSound.play();if(playing)playing.catch(()=>{});
 }
 const reduced=window.matchMedia('(prefers-reduced-motion: reduce)');
 let open=false,invitationTimer,hideTimer,closeTimer,pullTimer;
 function scheduleInvitation(delay=50000+Math.random()*20000){
  clearTimeout(invitationTimer);invitationTimer=setTimeout(()=>{
   if(!open && !document.hidden){invite.hidden=false;clearTimeout(hideTimer);hideTimer=setTimeout(()=>invite.hidden=true,8500);}
   scheduleInvitation();
  },delay);
 }
 function hideInvitation(){clearTimeout(hideTimer);invite.hidden=true;}
 function setOpen(next){
  clearTimeout(closeTimer);clearTimeout(pullTimer);hideInvitation();const origin=launcher.getBoundingClientRect();open=next;
  launcher.setAttribute('aria-expanded',String(open));launcher.setAttribute('aria-label',open?'收起智慧鮮果客服聊天室':'開啟智慧鮮果客服聊天室');
  root.classList.remove('is-closing');root.classList.toggle('is-open',open);
  if(open){
   panel.hidden=false;panel.inert=false;
   panel.style.setProperty("--chat-origin-x",(origin.left+origin.width/2-panel.offsetLeft)+"px");
   panel.style.setProperty("--chat-origin-y",(origin.top+origin.height/2-panel.offsetTop)+"px");
   launcher.inert=true;root.classList.add('is-pulling');pullTimer=setTimeout(()=>root.classList.remove('is-pulling'),740);
   requestAnimationFrame(()=>{if(open)input.focus({preventScroll:true});});
  } else {
   root.classList.remove('is-pulling');launcher.inert=false;panel.inert=true;root.classList.add('is-closing');
   closeTimer=setTimeout(()=>{if(!open){panel.hidden=true;root.classList.remove('is-closing');}},reduced.matches?0:210);
   launcher.focus({preventScroll:true});
  }
 }
 function updateInput(){send.disabled=!input.value.trim();input.style.height='auto';input.style.height=Math.min(90,Math.max(30,input.scrollHeight))+'px';}
 function openFromClick(){if(open)return;playOpenSound();setOpen(true);}
 launcher.addEventListener('click',openFromClick);
 invite.addEventListener('click',event=>{if(!event.target.closest('.ff-chat-invite-close'))openFromClick();});root.querySelector('.ff-chat-close').addEventListener('click',()=>setOpen(false));
 root.querySelector('.ff-chat-invite-close').addEventListener('click',()=>{hideInvitation();scheduleInvitation(120000);});
 input.addEventListener('input',updateInput);
 root.querySelectorAll('[data-chat-topic]').forEach(button=>button.addEventListener('click',()=>{input.value=button.dataset.chatTopic;updateInput();input.focus({preventScroll:true});}));
 input.addEventListener('keydown',event=>{if(event.key==='Enter' && !event.shiftKey && !event.isComposing){event.preventDefault();root.querySelector('form').requestSubmit();}});
 root.querySelector('form').addEventListener('submit',event=>{
  event.preventDefault();const text=input.value.trim().slice(0,500);if(!text)return;
  const message=document.createElement('div');message.className='ff-chat-message ff-chat-message--user';
  const body=document.createElement('div'),bubble=document.createElement('div'),unsent=document.createElement('small');
  bubble.className='ff-chat-bubble';bubble.textContent=text;unsent.className='ff-chat-unsent';unsent.textContent='尚未送出 · 畫面預覽';
  body.append(bubble,unsent);message.append(body);messages.append(message);messages.scrollTop=messages.scrollHeight;
  input.value='';updateInput();input.focus({preventScroll:true});
 });
 document.addEventListener('keydown',event=>{if(event.key==='Escape' && open && root.contains(document.activeElement)){event.preventDefault();event.stopPropagation();setOpen(false);}});
 window.addEventListener('pagehide',()=>{if(openSound)openSound.pause();[invitationTimer,hideTimer,closeTimer,pullTimer].forEach(clearTimeout);root.classList.remove('is-pulling');});
 window.addEventListener('pageshow',event=>{if(event.persisted){if(!open)panel.hidden=true;scheduleInvitation(5000);}});
 updateInput();scheduleInvitation(2200);
})();
