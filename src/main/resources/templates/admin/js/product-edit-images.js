(() => {
    window.ProductEditImages = {
        create(data, base, mark) {
            const groups = new Map(), urls = new Set();
            for (const image of data.images || []) {
                const key = String(image.skuId);
                if (!groups.has(key)) groups.set(key, []);
                groups.get(key).push({...image, originalOrder:image.sortOrder ?? 0});
            }
            const root=document.createElement('section');root.className='edit-images';
            document.querySelector('.gallery').appendChild(root);
            function node(tag,text) { const n=document.createElement(tag);if(text)n.textContent=text;return n; }
            function validate(file) {
                if (file.size > 5*1024*1024) throw new Error('每張圖片不可超過5 MB');
                if (!['image/png','image/jpeg','image/gif'].includes(file.type)) throw new Error('只接受 PNG、JPEG 或 GIF 圖片');
            }
            function preview(image,file) {
                if(image.preview){URL.revokeObjectURL(image.preview);urls.delete(image.preview);}
                image.file=file;image.preview=URL.createObjectURL(file);urls.add(image.preview);
            }
            function choose(key, label) {
                if (!groups.has(key)) groups.set(key, []);
                const images=groups.get(key);root.replaceChildren();
                root.appendChild(node('h3','規格圖片：'+label));
                const info=node('p','PNG／JPEG／GIF，每張最多5 MB；按儲存後才會生效。');info.className='edit-images-hint';root.appendChild(info);
                const error=node('p');error.className='edit-images-error';error.setAttribute('role','alert');error.hidden=true;root.appendChild(error);
                function fail(e){error.textContent=e.message;error.hidden=false;}
                for(const image of images.filter(i=>!i.remove)) {
                    const row=node('div');row.className='edit-image-row';
                    const img=node('img');img.alt='規格圖片預覽';img.src=image.preview || base+'image/'+image.imageId+'?v='+encodeURIComponent(image.revision||'');
                    const replace=node('label','替換');replace.className='edit-image-replace';
                    const file=node('input');file.type='file';file.accept='image/png,image/jpeg,image/gif';file.setAttribute('aria-label','替換規格圖片');
                    file.addEventListener('change',()=>{if(!file.files[0])return;try{validate(file.files[0]);preview(image,file.files[0]);mark();choose(key,label);}catch(e){file.value='';fail(e);}});replace.appendChild(file);
                    const orderLabel=node('label','排序');orderLabel.className='edit-image-order';
                    const order=node('input');order.type='number';order.min='0';order.step='1';order.value=image.sortOrder??0;order.setAttribute('aria-label','圖片排序');
                    order.addEventListener('input',()=>{image.sortOrder=order.value===''?null:Number(order.value);mark();});orderLabel.appendChild(order);
                    const remove=node('button','刪除');remove.type='button';remove.addEventListener('click',()=>{image.remove=true;mark();choose(key,label);});
                    row.append(img,replace,orderLabel,remove);root.appendChild(row);
                }
                if(!images.some(i=>!i.remove))root.appendChild(node('p','此規格尚無圖片'));
                const addLabel=node('label','新增圖片');addLabel.className='edit-image-add';
                const add=node('input');add.type='file';add.multiple=true;add.accept='image/png,image/jpeg,image/gif';add.setAttribute('aria-label','新增規格圖片');
                add.addEventListener('change',()=>{const files=Array.from(add.files);try{files.forEach(validate);for(const file of files){const image={sortOrder:Math.max(-1,...images.filter(i=>!i.remove).map(i=>i.sortOrder??0))+1};preview(image,file);images.push(image);}if(files.length)mark();choose(key,label);}catch(e){add.value='';fail(e);}});addLabel.appendChild(add);root.appendChild(addLabel);
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
