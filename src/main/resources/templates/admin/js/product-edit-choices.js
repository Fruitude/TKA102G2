(() => {
    let sequence = 0;
    const normalize = text => String(text ?? '').trim().toLocaleLowerCase();
    function element(tag, cls, text) {
        const el = document.createElement(tag); if (cls) el.className = cls; if (text != null) el.textContent = text; return el;
    }
    window.ProductEditChoices = {
        create(value, label, choices, selected, update, hierarchical = false, placement = 'overlay') {
            const dropdown = element('div','edit-choice-dropdown');
            const trigger = element('button','edit-choice-trigger'); trigger.type = 'button';
            trigger.setAttribute('aria-haspopup','menu'); trigger.setAttribute('aria-expanded','false');
            const caption = element('span','edit-choice-caption'); trigger.append(caption,element('span','edit-choice-arrow','▾'));
            const menu = element('div','edit-choice-menu'); menu.id='edit-choice-'+(++sequence);menu.hidden=true;menu.setAttribute('role','menu');menu.setAttribute('aria-label',label);
            trigger.setAttribute('aria-controls',menu.id);
            const search = element('input','edit-choice-search');search.type='search';search.autocomplete='off';search.placeholder='輸入'+label+'名稱';search.setAttribute('aria-label','搜尋'+label);
            const options = element('div','edit-choice-options');const empty=element('p','edit-choice-empty','沒有符合的'+label);empty.hidden=true;empty.setAttribute('role','status');
            menu.append(search,options,empty);dropdown.append(trigger,menu);value.replaceChildren(dropdown);
            const byId=new Map(choices.map(c=>[c.id,c]));const records=[];const visited=new Set();
            const counts=new Map();choices.forEach(c=>counts.set(c.label,(counts.get(c.label)||0)+1));
            function fullLabel(choice) {
                const parts=[choice.label],seen=new Set([choice.id]);let parent=byId.get(choice.parentId);
                while(hierarchical && parent && !seen.has(parent.id)){parts.unshift(parent.label);seen.add(parent.id);parent=byId.get(parent.parentId);}
                return parts.join(' / ');
            }
            function setCaption(id) {
                const choice=byId.get(id);caption.textContent=choice?fullLabel(choice):'請選擇'+label;trigger.title=caption.textContent;
                records.forEach(r=>r.button.setAttribute('aria-current',String(r.choice.id===id)));
            }
            function closeChildren() { records.forEach(r=>{if(r.submenu){r.submenu.hidden=true;r.expand.setAttribute('aria-expanded','false');}}); }
            function close(returnFocus=false){menu.hidden=true;trigger.setAttribute('aria-expanded','false');closeChildren();if(returnFocus)trigger.focus();}
            function position() {
                const rect=trigger.getBoundingClientRect();menu.style.width=Math.max(180,rect.width+12)+'px';
                menu.style.left=Math.max(4,Math.min(rect.left-6,window.innerWidth-menu.offsetWidth-4))+'px';menu.style.top=Math.max(4,rect.top-6)+'px';
                options.style.maxHeight=Math.max(60,Math.min(240,window.innerHeight-rect.top-search.offsetHeight-24))+'px';
                if (placement==='adjacent') {
                    const below=window.innerHeight-rect.bottom-8,above=rect.top-8;
                    const upwards=below<Math.min(240,menu.offsetHeight) && above>below;
                    options.style.maxHeight=Math.max(60,Math.min(240,(upwards?above:below)-search.offsetHeight-16))+'px';
                    menu.style.left=Math.max(4,Math.min(rect.left,window.innerWidth-menu.offsetWidth-4))+'px';
                    menu.style.top=Math.max(4,upwards?rect.top-menu.offsetHeight-4:rect.bottom+4)+'px';
                }
            }
            function openChildren(record) {
                records.forEach(r=>{if(r.submenu && r!==record && !r.element.contains(record.element)){r.submenu.hidden=true;r.expand.setAttribute('aria-expanded','false');}});
                record.submenu.hidden=false;record.expand.setAttribute('aria-expanded','true');
                const rect=record.row.getBoundingClientRect();const width=record.submenu.offsetWidth;
                record.submenu.style.left=Math.max(4,rect.right+width>window.innerWidth-4?rect.left-width:rect.right)+'px';
                record.submenu.style.top=Math.max(4,Math.min(rect.top,window.innerHeight-record.submenu.offsetHeight-4))+'px';
            }
            function build(choice, container, root=false) {
                if(visited.has(choice.id))return null;visited.add(choice.id);
                const group=element('div','edit-choice-group'),row=element('div','edit-choice-row');
                const button=element('button','edit-choice-item',choice.label+(counts.get(choice.label)>1?' (#'+choice.id+')':''));button.type='button';button.setAttribute('role','menuitem');button.dataset.choiceId=choice.id;
                button.disabled=!!choice.disabled;
                const record={choice,element:group,row,button,children:[]};records.push(record);row.appendChild(button);group.appendChild(row);container.appendChild(group);
                button.addEventListener('click',()=>{if(choice.disabled)return;selected=choice.id;setCaption(selected);update(selected);close(true);});
                const children=hierarchical?choices.filter(c=>c.parentId===choice.id && c.id!==choice.id):[];
                if(hierarchical && (root||children.length)) {
                    record.expand=element('button','edit-choice-expand','▸');record.expand.type='button';record.expand.setAttribute('aria-label','展開'+choice.label+'的小分類');record.expand.setAttribute('aria-expanded','false');
                    record.submenu=element('div','edit-choice-submenu');record.submenu.hidden=true;record.submenu.setAttribute('role','menu');record.submenu.id=menu.id+'-children-'+choice.id;record.expand.setAttribute('aria-controls',record.submenu.id);row.appendChild(record.expand);group.appendChild(record.submenu);
                    children.forEach(c=>{const child=build(c,record.submenu);if(child)record.children.push(child);});
                    if(!record.children.length)record.submenu.appendChild(element('p','edit-choice-empty','尚無小分類'));
                    record.expand.addEventListener('click',()=>openChildren(record));group.addEventListener('pointerenter',()=>openChildren(record));
                    group.addEventListener('pointerleave',()=>{if(!group.contains(document.activeElement)){record.submenu.hidden=true;record.expand.setAttribute('aria-expanded','false');}});
                }
                return record;
            }
            const roots=[];
            choices.filter(c=>!hierarchical||c.parentId==null||!byId.has(c.parentId)).forEach(c=>{const r=build(c,options,hierarchical);if(r)roots.push(r);});
            choices.filter(c=>!visited.has(c.id)).forEach(c=>{const r=build(c,options,hierarchical);if(r)roots.push(r);});
            function filter() {
                const query=normalize(search.value);closeChildren();
                function visit(record,parentMatch=false) {
                    const match=parentMatch||normalize(record.choice.label).includes(query);
                    const childMatches=record.children.map(c=>visit(c,match));record.element.hidden=!(match||childMatches.some(Boolean));return !record.element.hidden;
                }
                const matches=roots.map(r=>visit(r));empty.hidden=matches.some(Boolean);
                if(query){const childMatch=roots.find(r=>!r.element.hidden&&!normalize(r.choice.label).includes(query)&&r.children.some(c=>!c.element.hidden));if(childMatch)openChildren(childMatch);}
                position();
            }
            function open(){search.value='';menu.hidden=false;trigger.setAttribute('aria-expanded','true');filter();search.focus();}
            trigger.addEventListener('click',()=>menu.hidden?open():close());trigger.addEventListener('keydown',e=>{if(e.key==='ArrowDown'){e.preventDefault();open();}});
            search.addEventListener('input',filter);
            menu.addEventListener('keydown',e=>{
                if(e.key==='Escape'){e.preventDefault();close(true);return;}
                const record=records.find(r=>r.row.contains(e.target));
                if(e.key==='ArrowRight'&&record?.submenu){e.preventDefault();openChildren(record);record.children.find(c=>!c.element.hidden)?.button.focus();return;}
                const submenu=e.target.closest('.edit-choice-submenu');
                if(e.key==='ArrowLeft'&&submenu){e.preventDefault();const parent=records.find(r=>r.submenu===submenu);submenu.hidden=true;parent.expand.setAttribute('aria-expanded','false');parent.button.focus();return;}
                if(e.key==='ArrowDown'||e.key==='ArrowUp'){
                    e.preventDefault();const items=Array.from((submenu||options).querySelectorAll('button')).filter(b=>!b.closest('[hidden]'));
                    const index=items.indexOf(e.target);items[(index+(e.key==='ArrowDown'?1:-1)+items.length)%items.length]?.focus();
                }
            });
            document.addEventListener('click',e=>{if(!dropdown.contains(e.target))close();});document.addEventListener('focusin',e=>{if(!dropdown.contains(e.target))close();});
            document.addEventListener('scroll',e=>{if(!menu.contains(e.target))close();else if(e.target===options)closeChildren();},true);
            window.addEventListener('resize',()=>close());setCaption(selected);
            return {dropdown,trigger,menu,search,records,close,setValue(value){selected=value;setCaption(value);}};
        }
    };
})();
