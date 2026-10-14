(() => {
    if (new URLSearchParams(location.search).get('edit') !== 'true') return;
    let imageEditor, data, dirty = false, statusChanged = false, sequence = 0;
    const changed = new Map();
    const numericFields = new Map();
    const nameFields = new Map();
    const MAX_PRICE = 10000000;
    const productId = new URLSearchParams(location.search).get('productId');
    const base = location.pathname.replace(/getOne_For_Display$/, '');
    const labels = ['下架', '上架', '缺貨', '即將售完', '售完', '預備上架', '即將停產', '永久停產'];
    const statusHelp = '上架時，系統會依庫存、待進貨良率、待出貨、安全庫存及允許超賣量，自動判斷缺貨、即將售完或售完；即將停產在待進貨為0且庫存－待出貨不大於0時，自動轉為永久停產。';
    function node(tag, cls, text) {
        const element = document.createElement(tag);
        if (cls) element.className = cls;
        if (text != null) element.textContent = text;
        return element;
    }
    function mark(entry) { dirty = true; if (entry) changed.set(entry.key, entry); }
    function valueRow(root, label) {
        return [...root.querySelectorAll('.item')].find(row => row.querySelector('.name')?.textContent.trim() === label)?.querySelector('.value');
    }
    function input(value, type, update, maxLength) {
        const control = node('input', 'edit-input'); control.type = type; control.value = value ?? '';
        if (type === 'number') { control.min = '0'; control.step = '1'; }
        if (maxLength) control.maxLength = maxLength;
        control.addEventListener('input', () => update(type === 'number' ? (control.value === '' ? null : Number(control.value)) : control.value));
        return control;
    }
    function numeric(entry, panel, label, field) {
        const value = valueRow(panel, label); if (!value) return;
        const box = node('div', 'edit-price-field');
        const control = node('input', 'edit-input');
        const isPrice = field === 'price', minimum = isPrice ? 1 : 0, maximum = isPrice ? MAX_PRICE : 2147483647;
        control.type = 'number'; control.min = String(minimum); control.max = String(maximum); control.step = '1'; control.required = true;
        control.value = entry[field] ?? ''; control.setAttribute('aria-label', label);
        const error = node('small', 'edit-price-error'); error.id = 'number-error-' + entry.key + '-' + field;
        error.setAttribute('role', 'alert'); control.setAttribute('aria-describedby', error.id);
        function validate() {
            control.setCustomValidity('');
            let message = '';
            if (control.validity.badInput) message = '請輸入有效的數字。';
            else if (control.validity.valueMissing) message = isPrice ? '請填寫價格。' : '請填寫' + label + '。';
            else if (control.validity.rangeUnderflow) message = isPrice ? '不可低於 1 元。' : '不可小於 0。';
            else if (control.validity.rangeOverflow) message = isPrice ? '不可超過 10,000,000 元。' : '不可超過 2,147,483,647。';
            else if (control.validity.stepMismatch) message = isPrice ? '須為正整數。' : '須為 0 或正整數。';
            entry[field] = message ? null : control.valueAsNumber;
            error.textContent = message; error.hidden = !message;
            control.setAttribute('aria-invalid', String(Boolean(message))); control.setCustomValidity(message);
            return message;
        }
        control.addEventListener('input', () => { validate(); mark(entry); });
        control.addEventListener('blur', validate);
        box.append(control, error); value.replaceChildren(box);
        numericFields.set(entry.key + ':' + field, {control, validate, panel}); validate();
    }
    function nameEditor(button, entry, isNew) {
        const name = button.querySelector('span') || button.appendChild(node('span'));
        const pencil = node('span','edit-pencil');
        pencil.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m16 3 5 5-12 12-6 1 1-6L16 3Z"/><path d="m13 6 5 5M4 15l5 5"/></svg>'; pencil.title = '修改規格名稱'; pencil.setAttribute('aria-hidden','true');
        button.appendChild(pencil);
        let editor;
        function begin() {
            const panel = document.getElementById(button.dataset.panelId);
            if (!panel) return;
            if (editor) { editor.querySelector('input').focus({preventScroll:true}); return; }
            editor = node('div','edit-sku-names');
            function refreshName() { name.textContent = entry.anotherName?.trim() || entry.skuName?.trim() || '未填規格名稱'; }
            for (const [label, field] of [['規格名稱','skuName'],['規格別名','anotherName']]) {
                const row = node('label','edit-sku-name-field');
                const caption = node('span','edit-sku-name-caption',label); row.appendChild(caption);
                const control = input(entry[field], 'text', text => { entry[field] = text; control.setCustomValidity(''); mark(entry); refreshName(); }, 50);
                control.setAttribute('aria-label', label);
                if (field === 'skuName') {
                    control.required = true;
                    const error = node('small', 'edit-sku-name-error', '請填寫規格名稱');
                    error.id = 'sku-name-error-' + entry.key; error.setAttribute('role', 'alert');
                    control.setAttribute('aria-describedby', error.id);
                    function validate() {
                        const empty = !control.value.trim();
                        error.hidden = !empty; control.setAttribute('aria-invalid', String(empty));
                        control.setCustomValidity(empty ? '請填寫規格名稱' : '');
                        return empty;
                    }
                    control.addEventListener('input', validate); control.addEventListener('blur', validate);
                    caption.appendChild(error); nameFields.set(entry.key, {control, validate, panel}); validate();
                }
                else control.placeholder = '選填；顯示時優先使用別名';
                row.appendChild(control); editor.appendChild(row);
            }
            panel.prepend(editor);
            if (isNew) { const first = editor.querySelector('input'); first.focus({preventScroll:true}); }
        }
        pencil.addEventListener('click', event => { event.stopPropagation(); showSkuInfo(button); begin(); });
        button.title = '選取規格後，可直接修改規格名稱與別名';
        begin();
    }
    function prepareSku(entry, panel, button, isNew = false) {
        numeric(entry, panel, '規格價格', 'price');
        for (const [label, field] of [['庫存量','stock'],['安全庫存量','safetyStock'],['待進貨','inboundQty'],['待出貨','outboundQty'],['允許超賣量','maxBackorderQty']]) {
            const value = valueRow(panel, label);
            if (isNew) { numeric(entry, panel, label, field); continue; }
            if (value) { value.textContent = String(entry[field] ?? 0); value.title = '數量請至進銷存系統調整'; }
        }
        const statusValue = valueRow(panel, '規格狀態');
        const select = node('select','edit-input edit-sku-status');select.setAttribute('aria-label','規格狀態');
        for (const status of [0,1,5,6,2,3,4,7]) {
            const option = node('option','',labels[status]); option.value=String(status);
            option.disabled=[2,3,4,7].includes(status) || (status===5 && (isNew || entry.status!==5));
            select.appendChild(option);
        }
        select.value=String(entry.status);
        select.disabled=[6,7].includes(entry.status) && !data.canRestore;
        select.addEventListener('change',()=>{
            const next=Number(select.value);
            if(entry.wasSoldOut && next!==4 && !entry.restockConfirmed) {
                if(!window.confirm(next===1?'該商品規格原為售完商品，請確認是否有貨源。':'該商品規格原為售完商品，請確認是否變更規格狀態。')) {select.value=String(entry.status);return;}
                entry.restockConfirmed=true;
            }
            for(const status of [0,1,2,3,4,5,6,7])panel.classList.remove('status-'+status);
            panel.classList.add('status-'+next);entry.status=next;mark(entry);
        });
        const holder=node('span','edit-sku-status-choice');
        select.hidden=true;select.tabIndex=-1;select.setAttribute('aria-hidden','true');
        statusValue.replaceChildren(select,holder);statusValue.title=statusHelp;
        const choices=Array.from(select.options).map(option=>({id:option.value,label:option.textContent,disabled:option.disabled}));
        const statusChoice=window.ProductEditChoices.create(holder,'規格狀態',choices,select.value,id=>{
            select.value=id;select.dispatchEvent(new Event('change',{bubbles:true}));
            statusChoice.setValue(select.value);
        });
        statusChoice.trigger.disabled=select.disabled;statusChoice.trigger.setAttribute('aria-label','規格狀態');
        statusChoice.trigger.title=statusHelp;
        nameEditor(button, entry, isNew);
        const notice = valueRow(panel, '異常狀態');
        if (notice) notice.title = '儲存後將依最新庫存重新計算';
    }
    function searchable(root, label, choices, selected, update, hierarchical = false) {
        const value = valueRow(root, label); value.classList.add('edit-choice-value');
        window.ProductEditChoices.create(value, label, choices, selected, id => { update(id); mark(); }, hierarchical);
    }
    function addSku() {
        const key = 'new-' + (++sequence);
        const entry = {key, skuId:null, skuName:'', anotherName:null, price:null, stock:0, safetyStock:0, inboundQty:0, outboundQty:0, maxBackorderQty:10, status:0};
        const panel = node('div', 'sku-panel status-0'); panel.id = 'sku-panel-' + key;
        for (const label of ['規格價格','規格狀態','庫存量','安全庫存量','待進貨','待出貨','允許超賣量']) {
            const row = node('div','item'); row.appendChild(node('span','name',label)); const value = node('span','value'); row.appendChild(value);
            if (label === '規格狀態') value.classList.add('sku-status-value');
            panel.appendChild(row);
        }
        const remove = node('button','edit-remove-sku','移除此新增規格'); remove.type='button';
        remove.addEventListener('click',()=>{ changed.delete(key); for (const fieldKey of numericFields.keys()) if(fieldKey.startsWith(key + ':')) numericFields.delete(fieldKey); nameFields.delete(key); imageEditor?.removeSku(key); button.remove(); panel.remove(); dirty=true; const first=document.querySelector('.sku-option'); if(first)showSkuInfo(first); });
        panel.appendChild(remove); document.querySelector('.sku-section').appendChild(panel);
        const button = node('button','sku-option'); button.type='button'; button.dataset.panelId=panel.id;button.dataset.skuId=key;button.appendChild(node('span','new-sku-name',''));
        button.onclick=()=>showSkuInfo(button); document.querySelector('.sku-add').before(button);
        document.querySelector('.no-sku')?.remove(); mark(entry); showSkuInfo(button); prepareSku(entry,panel,button,true);
    }
    async function initialize() {
        const response = await fetch(base + 'editData?productId=' + encodeURIComponent(productId), {cache:'no-store'});
        if (!response.ok) throw new Error('無法載入修改資料，請重新啟動 Spring 後再試。');
        data = await response.json(); document.body.classList.add('product-edit-mode');
        const meta=document.querySelector('.product-meta');
        searchable(meta,'分類',data.categories,data.product.categoryId,id=>data.product.categoryId=id,true);
        searchable(meta,'廠商',data.vendors,data.product.vendorId,id=>data.product.vendorId=id);
        const description=valueRow(meta,'描述');description.classList.add('edit-description-value');
        const area=node('textarea','edit-input');area.value=data.product.productDesc||'';area.rows=3;area.maxLength=255;area.setAttribute('aria-label','商品描述');
        area.addEventListener('input',()=>{data.product.productDesc=area.value;mark();});description.replaceChildren(area);
        const h1=document.querySelector('h1');h1.contentEditable='true';h1.addEventListener('input',()=>window.detailEditor.setName(h1.textContent));
        if (!new URLSearchParams(location.search).has('modal')) {
            const box=node('div','edit-product-name');h1.before(box);box.appendChild(h1);
            const pencil=node('button','edit-pencil');pencil.type='button';pencil.setAttribute('aria-label','修改商品名稱');pencil.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m16 3 5 5-12 12-6 1 1-6L16 3Z"/><path d="m13 6 5 5M4 15l5 5"/></svg>';
            pencil.addEventListener('click',()=>h1.focus());box.appendChild(pencil);
            const nameError=node('small','edit-name-error','請填寫商品名稱');nameError.id='edit-product-name-error';nameError.setAttribute('role','alert');nameError.hidden=true;box.appendChild(nameError);h1.setAttribute('aria-describedby',nameError.id);
            const image=document.querySelector('.gallery');
            if(image)new ResizeObserver(()=>box.style.width=image.getBoundingClientRect().width+'px').observe(image);
        }
        window.changeDetailProductStatus = toggle => {data.product.status=toggle.checked?1:0;statusChanged=true;mark();};
        data.skus.forEach(sku=>{
            const entry={...sku,key:String(sku.skuId),originalStatus:sku.status,wasSoldOut:sku.status===4};const panel=document.getElementById('sku-panel-'+sku.skuId);
            const button=document.querySelector('.sku-option[data-sku-id="'+sku.skuId+'"]'); if(panel&&button)prepareSku(entry,panel,button);
        });
        let options=document.querySelector('.sku-options');
        if(!options){options=node('div','sku-options');document.querySelector('.sku-title').after(options);}
        const add=node('button','sku-add','+');add.type='button';add.setAttribute('aria-label','新增規格');add.title='新增規格';add.addEventListener('click',addSku);options.appendChild(add);
        imageEditor = window.ProductEditImages.create(data, base, () => mark());
        return data;
    }
    window.detailEditor = {
        ready:null,
        isDirty:()=>dirty,
        getName:()=>data?.product.productName || '',
        setName:name=>{if(data){data.product.productName=name;mark();
            const empty=!name.trim();const h1=document.querySelector('h1');h1?.setAttribute('aria-invalid',String(empty));
            const error=document.getElementById('edit-product-name-error');if(error)error.hidden=!empty;
        }},
        preview(returnFocus) {
            const existing=data.skus.map(sku=>({...sku,key:String(sku.skuId),...(changed.get(String(sku.skuId)) || {})}));
            const skus=[...existing,...[...changed.values()].filter(sku=>sku.skuId==null)]
                .map(sku=>({...sku,images:imageEditor.preview(sku.key)}));
            const vendor=data.vendors.find(value=>value.id===data.product.vendorId);
            window.ProductEditPreview.open({product:{...data.product},vendor:vendor?{...vendor}:null,skus,
                selected:document.querySelector('.sku-option.active')?.dataset.skuId,
                context:location.pathname.split('/product/')[0]},returnFocus);
        },
        async save() {
            for (const field of nameFields.values()) {
                if (field.validate()) {
                    const button = [...document.querySelectorAll('.sku-option')].find(item => item.dataset.panelId === field.panel.id);
                    if (button) showSkuInfo(button);
                    field.control.focus(); throw new Error('請填寫規格名稱');
                }
            }
            for (const [key, field] of numericFields) {
                const message = field.validate();
                if (message) {
                    const button = [...document.querySelectorAll('.sku-option')].find(item => item.dataset.panelId === field.panel.id);
                    if (button) showSkuInfo(button);
                    field.control.focus();
                    throw new Error(message);
                }
            }
            if (!data.product.productName?.trim()) throw new Error('請填寫商品名稱');
            if (!data.product.categoryId || !data.product.vendorId) throw new Error('請從分類與廠商的下拉選項中選取有效項目');
            const skus=[...changed.values()].map(({key,wasSoldOut,originalStatus,stock,safetyStock,inboundQty,outboundQty,maxBackorderQty,status,restockConfirmed,...sku})=>({...sku,...(sku.skuId == null ? {stock,safetyStock,inboundQty,outboundQty,maxBackorderQty} : {}),status:sku.skuId==null || status!==originalStatus ? status : null,restockConfirmed:!!restockConfirmed}));
            for(const sku of skus){
                if(!sku.skuName?.trim())throw new Error('請填寫規格名稱');
            }
            const edited = new Map(skus.filter(s => s.skuId != null).map(s => [s.skuId, s]));
            const names = new Set();
            for (const sku of [...data.skus.map(s => edited.get(s.skuId) || s), ...skus.filter(s => s.skuId == null)]) {
                const name = sku.skuName?.trim();
                if (!name) throw new Error('請填寫規格名稱');
                if (names.has(name)) throw new Error('同個商品的規格名稱不可重複：' + name);
                names.add(name);
            }
            const payload={product:data.product,skus,productStatusChanged:statusChanged,activateSkus:false};
            const imageChanges=imageEditor.build([...changed.values()]);
            const send=()=>{
                if(!imageChanges.changes.length)return fetch(base+'saveModal',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
                const form=new FormData();form.append('payload',JSON.stringify({edit:payload,images:imageChanges.changes}));
                imageChanges.files.forEach(file=>form.append('files',file));
                return fetch(base+'saveModalWithImages',{method:'POST',body:form});
            };
            const read=response=>response.json().catch(()=>({message:response.status===413?'圖片總大小超過上傳限制':'儲存失敗，請稍後再試。'}));
            let response=await send(), result=await read(response);
            if(response.status===409&&result.kind==='confirm'){
                if(!window.confirm(result.message))throw new Error('已取消儲存，資料尚未變更。');
                payload.activateSkus=true;response=await send();result=await read(response);
            }
            if(!response.ok)throw new Error(result.message||'儲存失敗，請稍後再試。');
            dirty=false;return result;
        }
    };
    window.detailEditor.ready = initialize();
})();
