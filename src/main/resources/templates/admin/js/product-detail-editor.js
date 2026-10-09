(() => {
    if (new URLSearchParams(location.search).get('edit') !== 'true') return;
    let data, dirty = false, statusChanged = false, sequence = 0;
    const changed = new Map();
    const productId = new URLSearchParams(location.search).get('productId');
    const base = location.pathname.replace(/getOne_For_Display$/, '');
    const labels = ['下架', '上架', '缺貨', '即將下架', '永久停產', '預備上架'];
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
    function stateOptions(entry, select) {
        [...select.options].forEach(option => {
            option.disabled = (option.value === '5' && entry.status !== 5)
                || (entry.status === 4 && option.value !== '4' && !data.canRestore);
        });
    }
    function updateState(entry, panel, select) {
        const next = Number(select.value);
        if (next === 4 && entry.status !== 4 && !window.confirm('請確認該規格是否永久停產，永久停產後無法再調整規格狀態。')) {
            select.value = String(entry.status); return;
        }
        entry.status = next; mark(entry);
        for (let state = 0; state <= 5; state++) panel.classList.remove('status-' + state);
        panel.classList.add('status-' + next);
        select.style.setProperty('--status-label-length', labels[next].length);
        stateOptions(entry, select);
    }
    function nameEditor(button, entry, isNew) {
        const name = button.querySelector('span') || button.appendChild(node('span'));
        let control;
        function begin() {
            if (control) { control.focus(); return; }
            control = input(isNew ? entry.skuName : (entry.anotherName || entry.skuName), 'text', text => {
                if (isNew) entry.skuName = text; else entry.anotherName = text;
                mark(entry);
            }, 50);
            control.classList.add('edit-sku-name');
            control.setAttribute('aria-label', isNew ? '新增規格名稱' : '規格別名');
            control.addEventListener('click', event => event.stopPropagation());
            control.addEventListener('dblclick', event => event.stopPropagation());
            control.addEventListener('keydown', event => {
                event.stopPropagation();
                if (event.key === 'Enter') { event.preventDefault(); control.blur(); }
            });
            control.addEventListener('blur', () => {
                name.textContent = (isNew ? entry.skuName : entry.anotherName)?.trim() || (isNew ? '' : entry.skuName);
                control.replaceWith(name); control = null;
            });
            name.replaceWith(control); control.focus({preventScroll:true}); control.select();
        }
        button.addEventListener('dblclick', event => { event.preventDefault(); begin(); });
        button.title = '單擊選取規格，雙擊修改規格別名';
        if (isNew) begin();
    }
    function prepareSku(entry, panel, button, isNew = false) {
        for (const [label, field] of [['規格價格','price'],['庫存量','stock'],['安全庫存量','safetyStock'],['待進貨','inboundQty'],['待出貨','outboundQty']]) numeric(entry, panel, label, field);
        const select = panel.querySelector('.sku-status-select');
        select.onchange = () => updateState(entry, panel, select);
        stateOptions(entry, select);
        nameEditor(button, entry, isNew);
        const notice = valueRow(panel, '異常狀態');
        if (notice) notice.title = '儲存後將依最新庫存重新計算';
    }
    function searchable(root, label, choices, selected, update) {
        const value = valueRow(root, label); value.classList.add('edit-choice-value');
        const control = input('', 'text', () => { update(null); mark(); });
        const id = 'edit-options-' + label; control.setAttribute('list', id); control.setAttribute('aria-label', label); control.autocomplete = 'off';
        const list = node('datalist'); list.id = id;
        const counts = new Map(); choices.forEach(choice => counts.set(choice.label, (counts.get(choice.label) || 0) + 1));
        const options = choices.map(choice => ({...choice, text: choice.label + (counts.get(choice.label) > 1 ? ' (#' + choice.id + ')' : '')}));
        options.forEach(choice => { const option = node('option'); option.value = choice.text; list.appendChild(option); });
        control.value = options.find(choice => choice.id === selected)?.text || '';
        const choose = () => { update(options.find(choice => choice.text === control.value)?.id ?? null); mark(); };
        control.addEventListener('input', choose); control.addEventListener('change', choose);
        value.replaceChildren(control, list);
    }
    function addSku() {
        const key = 'new-' + (++sequence);
        const entry = {key, skuId:null, skuName:'', anotherName:null, price:null, stock:0, safetyStock:0, inboundQty:0, outboundQty:0, status:5};
        const panel = node('div', 'sku-panel status-5'); panel.id = 'sku-panel-' + key;
        for (const label of ['規格價格','規格狀態','庫存量','安全庫存量','待進貨','待出貨']) {
            const row = node('div','item'); row.appendChild(node('span','name',label)); const value = node('span','value'); row.appendChild(value);
            if (label === '規格狀態') {
                value.classList.add('sku-status-value'); const select = node('select','sku-status-select');
                select.setAttribute('aria-label',label);
                labels.forEach((text,index) => { const option=node('option','',text);option.value=String(index);select.appendChild(option); });
                select.value='5';value.appendChild(select);
            }
            panel.appendChild(row);
        }
        const remove = node('button','edit-remove-sku','移除此新增規格'); remove.type='button';
        remove.addEventListener('click',()=>{ changed.delete(key); button.remove(); panel.remove(); dirty=true; const first=document.querySelector('.sku-option'); if(first)showSkuInfo(first); });
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
        searchable(meta,'分類',data.categories,data.product.categoryId,id=>data.product.categoryId=id);
        searchable(meta,'廠商',data.vendors,data.product.vendorId,id=>data.product.vendorId=id);
        const description=valueRow(meta,'描述');description.classList.add('edit-description-value');
        const area=node('textarea','edit-input');area.value=data.product.productDesc||'';area.rows=3;area.maxLength=255;area.setAttribute('aria-label','商品描述');
        area.addEventListener('input',()=>{data.product.productDesc=area.value;mark();});description.replaceChildren(area);
        const h1=document.querySelector('h1');h1.contentEditable='true';h1.addEventListener('input',()=>window.detailEditor.setName(h1.textContent));
        window.changeDetailProductStatus = toggle => {data.product.status=toggle.checked?1:0;statusChanged=true;mark();};
        data.skus.forEach(sku=>{
            const entry={...sku,key:String(sku.skuId)};const panel=document.getElementById('sku-panel-'+sku.skuId);
            const button=document.querySelector('.sku-option[data-sku-id="'+sku.skuId+'"]'); if(panel&&button)prepareSku(entry,panel,button);
        });
        let options=document.querySelector('.sku-options');
        if(!options){options=node('div','sku-options');document.querySelector('.sku-title').after(options);}
        const add=node('button','sku-add','+');add.type='button';add.setAttribute('aria-label','新增規格');add.title='新增規格';add.addEventListener('click',addSku);options.appendChild(add);
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
            const skus=[...changed.values()].map(({key,...sku})=>sku);
            for(const sku of skus){
                if(sku.skuId==null&&!sku.skuName?.trim())throw new Error('請填寫新增規格名稱');
                if(!Number.isInteger(sku.price)||sku.price<=0)throw new Error('規格價格須為大於0的整數');
                for(const field of ['stock','safetyStock','inboundQty','outboundQty'])if(!Number.isInteger(sku[field])||sku[field]<0)throw new Error('庫存及進出貨數量須為0以上整數');
            }
            const payload={product:data.product,skus,productStatusChanged:statusChanged,activateSkus:false};
            const send=()=>fetch(base+'saveModal',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
            let response=await send(), result=await response.json();
            if(response.status===409&&result.kind==='confirm'){
                if(!window.confirm(result.message))throw new Error('已取消儲存，資料尚未變更。');
                payload.activateSkus=true;response=await send();result=await response.json();
            }
            if(!response.ok)throw new Error(result.message||'儲存失敗，請稍後再試。');
            dirty=false;return result;
        }
    };
    window.detailEditor.ready = initialize();
})();
