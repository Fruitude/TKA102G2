(() => {
    window.ProductEditImages = {
        create(data, base, mark) {
            const groups = new Map(), urls = new Set();
            for (const image of data.images || []) {
                const key = String(image.skuId);
                if (!groups.has(key)) groups.set(key, []);
                groups.get(key).push({...image, originalOrder:image.sortOrder ?? 0});
            }
            for (const images of groups.values()) images.sort((a,b) => a.originalOrder-b.originalOrder || a.imageId-b.imageId);
            function setOrder(images) { images.filter(image=>!image.remove).forEach((image,index)=>image.sortOrder=index); }
            const root=document.createElement('section');root.className='edit-images';
            document.querySelector('.gallery').appendChild(root);
            function node(tag,text) { const n=document.createElement(tag);if(text)n.textContent=text;return n; }
            function icon(tag, cls, label, paths) {
                const control = node(tag); control.className = 'edit-image-icon ' + cls;
                control.title = label; control.setAttribute('aria-label', label);
                control.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true">' + paths + '</svg>';
                return control;
            }
            function validate(file) {
                if (file.size > 5*1024*1024) throw new Error('每張圖片不可超過5 MB');
                if (!['image/png','image/jpeg','image/gif'].includes(file.type)) throw new Error('只接受 PNG、JPEG 或 GIF 圖片');
            }
            function preview(image,file) {
                if(image.preview){URL.revokeObjectURL(image.preview);urls.delete(image.preview);}
                image.file=file;image.preview=URL.createObjectURL(file);urls.add(image.preview);
            }
            function imageOffset(key) {
                let count = 0;
                for (const button of document.querySelectorAll('.sku-option')) {
                    const skuKey = String(button.dataset.skuId);
                    if (skuKey === key) break;
                    count += (groups.get(skuKey) || []).filter(image => !image.remove).length;
                }
                return count;
            }
            function choose(key, label) {
                if (!groups.has(key)) groups.set(key, []);
                const images=groups.get(key);root.replaceChildren();
                root.appendChild(node('h3',label));
                const switchHint=node('p','請點選右邊規格做切換');switchHint.className='edit-images-switch-hint';root.appendChild(switchHint);
                const info=node('p','格式僅接受 PNG/JPEG/GIF\n每張最多5 MB\n可拖曳圖片調整順序\n按儲存後才會生效。');info.className='edit-images-hint';
                const error=node('p');error.className='edit-images-error';error.setAttribute('role','alert');error.hidden=true;root.appendChild(error);
                function fail(e){error.textContent=e.message;error.hidden=false;}
                let dragged = null;
                function clearDrag() {
                    root.querySelectorAll('.edit-image-row').forEach(row=>row.classList.remove('is-dragging','drop-before','drop-after'));
                }
                function moveImage(image, target, after) {
                    const active = images.filter(item=>!item.remove);
                    const previous = active.slice();
                    active.splice(active.indexOf(image),1);
                    active.splice(active.indexOf(target)+(after?1:0),0,image);
                    if (active.every((item,index)=>item===previous[index])) return;
                    const removed = images.filter(item=>item.remove);
                    images.splice(0,images.length,...active,...removed); setOrder(images); mark(); choose(key,label);
                }
                let imageNumber = imageOffset(key);
                for(const image of images.filter(i=>!i.remove)) {
                    const row=node('div');row.className='edit-image-row';
                    const img=node('img');img.alt='規格圖片預覽';img.src=image.preview || base+'image/'+image.imageId+'?v='+encodeURIComponent(image.revision||'');
                    const replace=icon('label','edit-image-replace','替換規格圖片','<path d="M20 7h-6m6 0V1m0 6a9 9 0 0 0-15-2M4 17h6m-6 0v6m0-6a9 9 0 0 0 15 2"/>');
                    const file=node('input');file.type='file';file.accept='image/png,image/jpeg,image/gif';file.setAttribute('aria-label','替換規格圖片');
                    file.addEventListener('change',()=>{if(!file.files[0])return;try{validate(file.files[0]);preview(image,file.files[0]);mark();choose(key,label);}catch(e){file.value='';fail(e);}});replace.appendChild(file);
                    img.draggable=true; img.tabIndex=0; img.title='上下拖曳調整順序（也可按 Alt＋↑／↓）';
                    img.setAttribute('aria-label','規格圖片，上下拖曳排序');
                    img.addEventListener('dragstart',event=>{
                        dragged=image; event.dataTransfer.effectAllowed='move'; event.dataTransfer.setData('text/plain','sku-image'); row.classList.add('is-dragging');
                    });
                    img.addEventListener('dragend',()=>{dragged=null;clearDrag();});
                    row.addEventListener('dragover',event=>{
                        if(!dragged || dragged===image)return;
                        event.preventDefault(); event.dataTransfer.dropEffect='move'; clearDrag();
                        const after=event.clientY>row.getBoundingClientRect().top+row.getBoundingClientRect().height/2;
                        row.classList.add(after?'drop-after':'drop-before');
                    });
                    row.addEventListener('dragleave',()=>row.classList.remove('drop-before','drop-after'));
                    row.addEventListener('drop',event=>{
                        if(!dragged || dragged===image)return;
                        event.preventDefault(); const source=dragged;
                        const after=event.clientY>row.getBoundingClientRect().top+row.getBoundingClientRect().height/2;
                        dragged=null;clearDrag();moveImage(source,image,after);
                    });
                    img.addEventListener('keydown',event=>{
                        if(!event.altKey || !['ArrowUp','ArrowDown'].includes(event.key))return;
                        event.preventDefault(); const active=images.filter(item=>!item.remove), index=active.indexOf(image);
                        const target=active[index+(event.key==='ArrowUp'?-1:1)];
                        if(target) { moveImage(image,target,event.key==='ArrowDown'); root.querySelectorAll('.edit-image-row img')[images.filter(item=>!item.remove).indexOf(image)]?.focus({preventScroll:true}); }
                    });
                    const remove=icon('button','edit-image-delete','刪除規格圖片','<path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7"/>');remove.type='button';remove.addEventListener('click',()=>{image.remove=true;setOrder(images);mark();choose(key,label);});
                    const active=images.filter(item=>!item.remove), index=active.indexOf(image);
                    const up=icon('button','edit-image-up','圖片上移','<path d="m6 14 6-6 6 6"/>');up.type='button';up.disabled=index===0;
                    up.addEventListener('click',()=>moveImage(image,active[index-1],false));
                    const down=icon('button','edit-image-down','圖片下移','<path d="m6 10 6 6 6-6"/>');down.type='button';down.disabled=index===active.length-1;
                    down.addEventListener('click',()=>moveImage(image,active[index+1],true));
                    const number = node('span', String(++imageNumber));number.className='edit-image-number';
                    number.setAttribute('aria-label','商品第'+imageNumber+'張圖片');
                    row.append(number,up,img,down,replace,remove);root.appendChild(row);
                }
                if(!images.some(i=>!i.remove))root.appendChild(node('p','此規格尚無圖片'));
                const addLabel=node('label','+');addLabel.className='sku-add edit-image-add';addLabel.title='新增規格圖片';addLabel.setAttribute('aria-label','新增規格圖片');
                const add=node('input');add.type='file';add.multiple=true;add.accept='image/png,image/jpeg,image/gif';add.setAttribute('aria-label','新增規格圖片');
                add.addEventListener('change',()=>{const files=Array.from(add.files);try{files.forEach(validate);for(const file of files){const image={sortOrder:Math.max(-1,...images.filter(i=>!i.remove).map(i=>i.sortOrder??0))+1};preview(image,file);images.push(image);}if(files.length)mark();choose(key,label);}catch(e){add.value='';fail(e);}});addLabel.appendChild(add);root.appendChild(addLabel);root.appendChild(info);
            }
            const original=window.showSkuInfo;
            window.showSkuInfo=button=>{original(button);choose(String(button.dataset.skuId),button.querySelector('span')?.textContent || '新增規格');};
            const selected=document.querySelector('.sku-option.active');if(selected)choose(String(selected.dataset.skuId),selected.querySelector('span')?.textContent || '');else root.appendChild(node('p','請選擇規格以編輯圖片'));
            window.addEventListener('pagehide',()=>urls.forEach(url=>URL.revokeObjectURL(url)),{once:true});
            return {
                removeSku(key){groups.delete(key);},
                build(skus){
                    const changes=[],files=[];
                    for(const [key,images] of groups){
                        const isNew=key.startsWith('new-'),sku=isNew?skus.find(s=>String(s.key)===key):null;
                        if(isNew&&!sku)continue;
                        for(const image of images){
                            if(!image.imageId&&image.remove)continue;
                            if(!image.remove&&!image.file&&image.sortOrder===image.originalOrder)continue;
                            if(!image.remove&&(!Number.isInteger(image.sortOrder)||image.sortOrder<0))throw new Error('圖片排序須為0或正整數');
                            const change={imageId:image.imageId??null,skuId:isNew?null:Number(key),skuName:isNew?sku.skuName.trim():null,sortOrder:image.sortOrder,revision:image.revision??null,remove:!!image.remove,fileIndex:null};
                            if(image.file&&!image.remove){change.fileIndex=files.length;files.push(image.file);}
                            changes.push(change);
                        }
                    }
                    if(changes.length>100)throw new Error('一次最多調整100張圖片');
                    if(files.reduce((n,f)=>n+f.size,0)>28*1024*1024)throw new Error('本次圖片總大小不可超過28 MB');
                    return {changes,files};
                }
            };
        }
    };
})();
