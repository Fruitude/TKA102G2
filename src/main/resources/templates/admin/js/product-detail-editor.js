(() => {
    if (new URLSearchParams(location.search).get('edit') !== 'true') return;
    let imageEditor, data, dirty = false, statusChanged = false, sequence = 0;
    const changed = new Map();
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
        const control = input(entry[field], 'number', result => { entry[field] = result; mark(entry); });
        if (field === 'price') control.min = '1';
        control.setAttribute('aria-label', label); value.replaceChildren(control);
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
            const original = {...entry};
            const wasChanged = changed.has(entry.key);
            editor = node('div','edit-sku-names');
            function refreshName() { name.textContent = entry.anotherName?.trim() || entry.skuName?.trim() || '未填規格名稱'; }
            for (const [label, field] of [['規格名稱','skuName'],['規格別名','anotherName']]) {
                const row = node('label','edit-sku-name-field'); row.appendChild(node('span','',label));
                const control = input(entry[field], 'text', text => { entry[field] = text; control.setCustomValidity(''); mark(entry); refreshName(); }, 50);
                control.setAttribute('aria-label', label);
                if (field === 'skuName') control.required = true;
                else control.placeholder = '選填；顯示時優先使用別名';
                row.appendChild(control); editor.appendChild(row);
            }
            const done = node('button','edit-sku-names-done','完成'); done.type = 'button';
            done.addEventListener('click', () => {
                const control = editor.querySelector('input');
                if (!entry.skuName?.trim()) { control.setCustomValidity('規格名稱不可空白'); control.reportValidity(); control.focus(); return; }
                refreshName(); editor.remove(); editor = null;
            });
            const cancel = node('button','edit-sku-names-done','取消'); cancel.type = 'button';
            cancel.addEventListener('click', () => {
                entry.skuName = original.skuName; entry.anotherName = original.anotherName;
                if (!wasChanged && Object.keys(entry).every(key => entry[key] === original[key])) changed.delete(entry.key);
                refreshName(); editor.remove(); editor = null;
            });
            editor.appendChild(done); editor.appendChild(cancel); panel.prepend(editor);
            const first = editor.querySelector('input'); first.focus({preventScroll:true}); first.select();
        }
        pencil.addEventListener('click', event => { event.stopPropagation(); showSkuInfo(button); begin(); });
        button.addEventListener('dblclick', event => { event.preventDefault(); showSkuInfo(button); begin(); });
        button.title = '單擊選取規格，雙擊修改規格名稱與別名';
        if (isNew) begin();
    }
    function prepareSku(entry, panel, button, isNew = false) {
        numeric(entry, panel, '規格價格', 'price');
        for (const [label, field] of [['庫存量','stock'],['安全庫存量','safetyStock'],['待進貨','inboundQty'],['待出貨','outboundQty'],['允許超賣量','maxBackorderQty']]) {
            const value = valueRow(panel, label);
            if (value) { value.textContent = String(entry[field] ?? 0); value.title = '數量請至進銷存系統調整'; }
        }
        const statusValue = valueRow(panel, '規格狀態');
        const statusText = node('span','sku-status-text',labels[entry.status] || '未設定');
        statusText.dataset.skuId = entry.skuId ?? entry.key; statusText.dataset.currentStatus = String(entry.status);
        statusValue.replaceChildren(statusText); statusValue.title = statusHelp;
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
        const entry = {key, skuId:null, skuName:'', anotherName:null, price:null, stock:0, safetyStock:0, inboundQty:0, outboundQty:0, maxBackorderQty:10, status:5};
        const panel = node('div', 'sku-panel status-5'); panel.id = 'sku-panel-' + key;
        for (const label of ['規格價格','規格狀態','庫存量','安全庫存量','待進貨','待出貨','允許超賣量']) {
            const row = node('div','item'); row.appendChild(node('span','name',label)); const value = node('span','value'); row.appendChild(value);
            if (label === '規格狀態') value.classList.add('sku-status-value');
            panel.appendChild(row);
        }
        const remove = node('button','edit-remove-sku','移除此新增規格'); remove.type='button';
        remove.addEventListener('click',()=>{ changed.delete(key); imageEditor?.removeSku(key); button.remove(); panel.remove(); dirty=true; const first=document.querySelector('.sku-option'); if(first)showSkuInfo(first); });
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
            const image=document.querySelector('.main-image-box');
            if(image)new ResizeObserver(()=>box.style.width=image.getBoundingClientRect().width+'px').observe(image);
        }
        window.changeDetailProductStatus = toggle => {data.product.status=toggle.checked?1:0;statusChanged=true;mark();};
        data.skus.forEach(sku=>{
            const entry={...sku,key:String(sku.skuId),wasSoldOut:sku.status===4};const panel=document.getElementById('sku-panel-'+sku.skuId);
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
        setName:name=>{if(data){data.product.productName=name;mark();}},
        async save() {
            if (!data.product.productName?.trim()) throw new Error('請填寫商品名稱');
            if (!data.product.categoryId || !data.product.vendorId) throw new Error('請從分類與廠商的下拉選項中選取有效項目');
            const skus=[...changed.values()].map(({key,wasSoldOut,stock,safetyStock,inboundQty,outboundQty,maxBackorderQty,status,restockConfirmed,...sku})=>({...sku,restockConfirmed:false}));
            for(const sku of skus){
                if(!sku.skuName?.trim())throw new Error('規格名稱不可空白');
                if(!Number.isInteger(sku.price)||sku.price<=0)throw new Error('規格價格須為大於0的整數');
            }
            const edited = new Map(skus.filter(s => s.skuId != null).map(s => [s.skuId, s]));
            const names = new Set();
            for (const sku of [...data.skus.map(s => edited.get(s.skuId) || s), ...skus.filter(s => s.skuId == null)]) {
                const name = sku.skuName?.trim();
                if (!name) throw new Error('規格名稱不可空白');
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
