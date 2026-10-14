(function(){
 'use strict';if(window.FruitSearch)return;
 var script=document.currentScript,dialogUrl=script.dataset.dialogUrl.replace(/;jsessionid=[^/?#;]+/g,''),resultsUrl=script.dataset.resultsUrl.replace(/;jsessionid=[^/?#;]+/g,'');
 var key='fruitude:product-search:v1',fields=['q','category','origin','vendor','promotion','highRated'],opening=null;
 function values(form){var result={};fields.forEach(function(name){var input=form.elements.namedItem(name);result[name]=input?(input.type==='checkbox'?input.checked:input.value):'';});return result;}
 function save(form){try{localStorage.setItem(key,JSON.stringify(values(form)));}catch(ignored){}}
 function saved(){try{var data=JSON.parse(localStorage.getItem(key));return data&&typeof data==='object'?data:null;}catch(ignored){return null;}}
 function restore(form,data){if(!data)return;fields.forEach(function(name){var input=form.elements.namedItem(name);if(!input)return;if(input.type==='checkbox')input.checked=data[name]===true||data[name]==='true';else if(typeof data[name]==='string'&&data[name].length<=100){input.value=name==='q'?data[name].slice(0,20):data[name];if(input.tagName==='SELECT'&&input.selectedIndex<0)input.value='';}});}
 function update(form){form.elements.q.value=form.elements.q.value.slice(0,20);(form._fruitSyncSelects||[]).forEach(function(sync){sync();});var labels=[];if(form.elements.q.value.trim())labels.push('關鍵字：'+form.elements.q.value.trim());['category','origin','vendor','promotion'].forEach(function(name){var select=form.elements.namedItem(name);if(select.value)labels.push(select.options[select.selectedIndex].textContent);});if(form.elements.highRated.checked)labels.push('平均評分 4～5 分');form.querySelector('#fruit-search-selected').textContent=labels.length?labels.join(' · '):'尚未設定篩選條件，可直接搜尋全部商品';}
 function tab(dialog,name){dialog.querySelectorAll('[data-search-tab]').forEach(function(b){var active=b.dataset.searchTab===name;b.setAttribute('aria-selected',String(active));b.tabIndex=active?0:-1;});dialog.querySelectorAll('[role=tabpanel]').forEach(function(p){p.hidden=p.id!=='fruit-panel-'+name;});}
 function error(message){var node=document.createElement('p');node.className='fruit-search-error';node.setAttribute('role','alert');node.textContent=message;document.body.appendChild(node);setTimeout(function(){node.remove();},5000);}
 function initCategories(form,name){
  var select=form.elements.namedItem(name),panel=select.closest('.fruit-search-panel');
  if(name!=='category'){var original=form.querySelector('.fruit-category-dropdown'),copy=original.cloneNode(true);copy.querySelectorAll('[id]').forEach(function(e){e.id=e.id.replace('category',name);});copy.querySelector('.fruit-category-trigger').setAttribute('aria-controls','fruit-'+name+'-menu');copy.querySelector('.fruit-category-trigger').removeAttribute('aria-labelledby');copy.querySelector('.fruit-category-trigger').setAttribute('aria-label',panel.querySelector('label').textContent);copy.querySelector('.fruit-category-menu').setAttribute('aria-label',panel.querySelector('label').textContent);copy.querySelector('.fruit-category-filter').setAttribute('aria-label','搜尋'+panel.querySelector('label').textContent.replace(/^選擇/,''));copy.querySelector('.fruit-category-filter').placeholder='輸入名稱篩選';copy.querySelector('.fruit-category-options').replaceChildren();copy.querySelector('.fruit-category-empty').textContent='沒有符合的選項';panel.querySelector('label').htmlFor='fruit-'+name+'-trigger';select.hidden=true;select.classList.add('fruit-category-select');select.setAttribute('aria-hidden','true');select.tabIndex=-1;panel.appendChild(copy);}
  var drop=panel.querySelector('.fruit-category-dropdown'),trigger=drop.querySelector('.fruit-category-trigger'),menu=drop.querySelector('.fruit-category-menu'),filter=drop.querySelector('.fruit-category-filter'),options=drop.querySelector('.fruit-category-options'),empty=drop.querySelector('.fruit-category-empty');
  var nodes=Array.from(select.options).filter(function(o){return o.value;}).map(function(o){return {value:o.value,label:o.textContent,parent:name==='category'?(o.dataset.categoryParent||''):'',children:[]};});
  var byId=new Map(nodes.map(function(n){return [n.value,n];})),roots=[];
  nodes.forEach(function(n){var parent=byId.get(n.parent);if(parent&&parent!==n)parent.children.push(n);else roots.push(n);});
  var groups=[],choices=[];
  function normalize(s){return s.normalize('NFKC').toLowerCase().replace(/臺/g,'台');}
  function closeChildren(){groups.forEach(function(g){if(g.box){g.box.hidden=true;g.arrow.setAttribute('aria-expanded','false');}});}
  function close(focus){menu.hidden=true;trigger.setAttribute('aria-expanded','false');closeChildren();if(focus)trigger.focus();}
  function position(){var r=trigger.getBoundingClientRect();menu.style.width=Math.min(r.width+12,innerWidth-16)+'px';menu.style.left=Math.max(8,Math.min(r.left-6,innerWidth-menu.offsetWidth-8))+'px';menu.style.setProperty('--fruit-category-height',Math.max(70,Math.min(230,r.top-72))+'px');menu.style.top=Math.max(8,r.top-menu.offsetHeight-8)+'px';}
  function expand(g){
   if(!g.box)return;g.box.hidden=false;g.arrow.setAttribute('aria-expanded','true');
   if(window.matchMedia('(max-width:520px)').matches)return;
   var r=g.row.getBoundingClientRect(),width=g.box.offsetWidth;
   g.box.style.left=(r.right+width>innerWidth-8?Math.max(8,r.left-width):r.right)+'px';
   g.box.style.top=Math.max(8,Math.min(r.bottom-g.box.offsetHeight,innerHeight-g.box.offsetHeight-8))+'px';
  }
  function choose(value){select.value=value;update(form);close(true);select.dispatchEvent(new Event('change',{bubbles:true}));}
  function choice(value,label){var b=document.createElement('button');b.type='button';b.className='fruit-category-choice';b.setAttribute('role','menuitem');b.dataset.categoryValue=value;b.textContent=label;b.addEventListener('click',function(){choose(value);});choices.push(b);return b;}
  var all=choice('',select.options[0].textContent);options.appendChild(all);
  function build(n,path){if(path.has(n.value))return null;var next=new Set(path);next.add(n.value);
   var element=document.createElement('div');element.className='fruit-category-group';element.dataset.categoryNode=n.value;
   var row=document.createElement('div');row.className='fruit-category-row';var b=choice(n.value,n.label.split(' › ').pop());row.appendChild(b);element.appendChild(row);
   var g={node:n,element:element,row:row,choice:b,box:null,arrow:null};groups.push(g);element._fruitGroup=g;
   if(n.children.length){var arrow=document.createElement('button');arrow.type='button';arrow.className='fruit-category-expand';arrow.setAttribute('aria-label','展開'+b.textContent+'的小分類');arrow.setAttribute('aria-expanded','false');arrow.setAttribute('aria-controls','fruit-category-children-'+n.value);arrow.textContent='選取分類細項 ▸';row.appendChild(arrow);
    var box=document.createElement('div');box.className='fruit-category-children';box.id='fruit-category-children-'+n.value;box.setAttribute('role','menu');box.setAttribute('aria-label',b.textContent+'小分類');box.hidden=true;g.box=box;g.arrow=arrow;
    n.children.forEach(function(child){var item=build(child,next);if(item)box.appendChild(item);});element.appendChild(box);
    function showChildren(){groups.forEach(function(other){if(other!==g&&other.element.parentElement===element.parentElement&&other.box){other.box.hidden=true;other.arrow.setAttribute('aria-expanded','false');}});expand(g);}
    arrow.addEventListener('pointerenter',function(e){if(e.pointerType==='mouse'&&window.matchMedia('(hover:hover)').matches)showChildren();});
    arrow.addEventListener('click',function(){if(window.matchMedia('(max-width:520px)').matches&&!box.hidden){box.hidden=true;arrow.setAttribute('aria-expanded','false');return;}showChildren();});
   }return element;
  }
  (roots.length?roots:nodes).forEach(function(n){var group=build(n,new Set());if(group)options.appendChild(group);});
  function filterChoices(){var q=normalize(filter.value.trim());all.hidden=!!q;closeChildren();
   function visit(g){var self=normalize(g.node.label).includes(q),child=false;(g.box?Array.from(g.box.children).map(function(e){return e._fruitGroup;}).filter(Boolean):[]).forEach(function(c){if(visit(c))child=true;});g.element.hidden=!!q&&!self&&!child;return !g.element.hidden;}
   groups.filter(function(g){return g.element.parentElement===options;}).forEach(visit);
   empty.hidden=groups.some(function(g){return !g.element.hidden;});
   position();if(q)groups.filter(function(g){return !g.element.hidden&&g.box;}).forEach(expand);
  }
  var sync=function(){var selected=select.options[select.selectedIndex];trigger.querySelector('span').textContent=selected?selected.textContent:'不限種類';trigger.title=selected?selected.textContent:'不限種類';choices.forEach(function(c){if(c.dataset.categoryValue===select.value)c.setAttribute('aria-current','true');else c.removeAttribute('aria-current');});};(form._fruitSyncSelects||(form._fruitSyncSelects=[])).push(sync);
  trigger.addEventListener('click',function(){if(!menu.hidden){close(true);return;}menu.hidden=false;trigger.setAttribute('aria-expanded','true');filter.value='';filterChoices();position();filter.focus();});
  filter.addEventListener('input',filterChoices);filter.addEventListener('keydown',function(e){if(e.key==='Enter')e.preventDefault();});
  form.addEventListener('click',function(e){if(!drop.contains(e.target))close(false);});
  form.addEventListener('keydown',function(e){if(menu.hidden)return;if(e.key==='Escape'){e.preventDefault();e.stopPropagation();close(true);}else if(['ArrowDown','ArrowUp','Home','End'].includes(e.key)&&menu.contains(e.target)){e.preventDefault();var visible=choices.filter(function(c){return c.getClientRects().length;});if(!visible.length)return;var i=visible.indexOf(document.activeElement);i=e.key==='Home'?0:e.key==='End'?visible.length-1:(i+(e.key==='ArrowDown'?1:-1)+visible.length)%visible.length;visible[i].focus();}});
  options.addEventListener('scroll',function(){if(!window.matchMedia('(max-width:520px)').matches)closeChildren();},{passive:true});
  var resize=function(){if(!menu.hidden){closeChildren();position();}};window.addEventListener('resize',resize);form.closest('dialog').addEventListener('close',function(){window.removeEventListener('resize',resize);});
 }
 function open(trigger){if(opening)return opening;var old=document.getElementById('fruit-search-dialog');if(old)old.remove();trigger.setAttribute('aria-busy','true');
  opening=fetch(dialogUrl,{credentials:'same-origin',cache:'no-store'}).then(function(r){if(!r.ok)throw Error('搜尋暫時無法載入，請稍後再試。');return r.text();}).then(function(html){
   var document2=new DOMParser().parseFromString(html,'text/html'),dialog=document2.querySelector('#fruit-search-dialog');if(!dialog)throw Error('搜尋暫時無法載入。');dialog=document.importNode(dialog,true);document.body.appendChild(dialog);
   var form=dialog.querySelector('form');form.action=resultsUrl;restore(form,saved());['category','origin','vendor','promotion'].forEach(function(name){initCategories(form,name);});update(form);
   dialog.querySelector('.fruit-search-close').addEventListener('click',function(){dialog.close();});dialog.addEventListener('click',function(e){if(e.target===dialog)dialog.close();});
   dialog.addEventListener('close',function(){trigger.focus();});
   dialog.querySelectorAll('[data-search-tab]').forEach(function(b){b.addEventListener('click',function(){tab(dialog,b.dataset.searchTab);});b.addEventListener('keydown',function(e){if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;e.preventDefault();var tabs=Array.from(dialog.querySelectorAll('[data-search-tab]')),i=tabs.indexOf(b);i=e.key==='Home'?0:e.key==='End'?tabs.length-1:(i+(e.key==='ArrowRight'?1:-1)+tabs.length)%tabs.length;tab(dialog,tabs[i].dataset.searchTab);tabs[i].focus();});});
   form.addEventListener('input',function(){update(form);});form.addEventListener('change',function(){update(form);});form.addEventListener('submit',function(){save(form);});
   dialog.querySelector('.fruit-search-clear').addEventListener('click',function(){fields.forEach(function(n){var input=form.elements.namedItem(n);if(input.type==='checkbox')input.checked=false;else input.value='';});update(form);form.elements.q.focus();});
   dialog.showModal();form.elements.q.focus();
  }).catch(function(){error('系統忙碌中，請稍後再嘗試。');}).finally(function(){trigger.removeAttribute('aria-busy');opening=null;});return opening;
 }
 function initBrandLogo(){
  document.querySelectorAll('.nav-bar .nav-logo img.nav-logo-image').forEach(function(img){
   if(!img.src.includes('6706de395f0ceac28687d5ed_Shutter_Shop.svg'))return;
   var svgText='<svg xmlns="http://www.w3.org/2000/svg" width="140" height="26" viewBox="0 0 140 26" class="nav-logo-image fruit-animated-logo" aria-hidden="true" focusable="false"><g class="fruit-logo-orange"><circle cx="12" cy="16" r="9" fill="#E4572E"/><path d="M12 7 C12 7 13.5 2.5 18 2.5" stroke="#5B8C3A" stroke-width="2.2" fill="none" stroke-linecap="round"/><ellipse cx="18" cy="3.5" rx="3.3" ry="1.7" fill="#5B8C3A" transform="rotate(-20 18 3.5)"/></g><g font-family="Jost, Noto Sans TC, sans-serif" font-size="16" font-weight="600" fill="#2b2b2b"><text class="fruit-logo-letter fruit-logo-letter-1" x="28" y="20">鮮</text><text class="fruit-logo-letter fruit-logo-letter-2" x="44" y="20">果</text><text class="fruit-logo-letter fruit-logo-letter-3" x="60" y="20">鋪</text></g></svg>';
   var svg=document.importNode(new DOMParser().parseFromString(svgText,'image/svg+xml').documentElement,true);
   img.closest('.nav-logo').setAttribute('aria-label','鮮果鋪首頁');img.replaceWith(svg);
  });
  var soundUrl=script.dataset.logoSoundUrl;
  if(soundUrl){
   var sound=new Audio(soundUrl.replace(/;jsessionid=[^/?#;]+/g,''));sound.preload='none';sound.volume=.55;sound.hidden=true;sound.id='fruit-logo-sound';document.body.appendChild(sound);
   function playSound(){if(!sound.paused)return;sound.currentTime=0;var playing=sound.play();if(playing)playing.catch(function(){/* A first hover may be blocked until the browser receives a user gesture. */});}
   function stopSound(){var active=Array.from(document.querySelectorAll('.nav-bar .nav-logo')).some(function(logo){return logo.matches(':hover')||logo===document.activeElement;});if(!active){sound.pause();sound.currentTime=0;}}
   document.querySelectorAll('.nav-bar .nav-logo:has(.fruit-animated-logo)').forEach(function(logo){logo.addEventListener('pointerenter',function(e){if(e.pointerType==='mouse')playSound();});logo.addEventListener('focus',playSound);logo.addEventListener('pointerleave',stopSound);logo.addEventListener('blur',function(){setTimeout(stopSound,0);});});
   window.addEventListener('pagehide',function(){sound.pause();});document.addEventListener('visibilitychange',function(){if(document.hidden)sound.pause();});
  }

 }

 function init(){initBrandLogo();var state=document.getElementById('fruit-search-state');if(state)save(state);
  var buttons=Array.from(document.querySelectorAll('.nav-cta-button-conatiner a')).filter(function(a){return a.querySelector('.search-icon')||a.textContent.trim()==='搜尋';}).concat(Array.from(document.querySelectorAll('[data-open-product-search]')));
  buttons.forEach(function(b){if(b.dataset.fruitSearchBound)return;b.dataset.fruitSearchBound='true';
   if(b.closest('.nav-cta-button-conatiner')){b.classList.add('fruit-nav-search');b.setAttribute('aria-label','搜尋');var label=document.createElement('span');label.className='fruit-nav-search-label';while(b.firstChild)label.appendChild(b.firstChild);b.appendChild(label);var fruits=document.createElement('span');fruits.className='fruit-nav-search-fruits';fruits.setAttribute('aria-hidden','true');['🍊','🍎','🍇','🍓','🍍'].forEach(function(fruit,i){var item=document.createElement('span');item.textContent=fruit;item.style.setProperty('--fruit-index',i);item.style.setProperty('--fruit-tilt',(i%2?-9:8)+'deg');fruits.appendChild(item);});b.appendChild(fruits);}b.setAttribute('aria-haspopup','dialog');if(b.tagName==='A')b.href=resultsUrl;b.addEventListener('click',function(e){e.preventDefault();open(b);});});
 }
 window.FruitSearch={open:open};if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
})();
