(() => {
    let frame, closePreview;
    function showStandalone(previewFrame,focus) {
        const panel=document.createElement('section');panel.id='product-edit-preview';
        panel.setAttribute('aria-label','前台外觀預覽');
        panel.innerHTML='<header><h2>前台外觀預覽 · 尚未儲存</h2><button type="button">關閉預覽</button></header>';
        const overflow=document.body.style.overflow;document.body.style.overflow='hidden';
        const close=()=>{panel.remove();frame=null;document.body.style.overflow=overflow;focus?.focus({preventScroll:true});};
        panel.querySelector('button').addEventListener('click',close);
        panel.appendChild(previewFrame);document.body.appendChild(panel);panel.querySelector('button').focus();
        return close;
    }
    function render(doc,snapshot) {
        doc.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();closePreview?.();}});
        const context=snapshot.context || '';
        for(const file of ['front-theme.css','product-detail.css']) {
            const link=doc.createElement('link');link.rel='stylesheet';link.href=context+'/front/css/'+file;doc.head.appendChild(link);
        }
        function node(tag,cls,text){const element=doc.createElement(tag);if(cls)element.className=cls;if(text!=null)element.textContent=text;return element;}
        const main=doc.getElementById('pv-main');
        const grid=node('div','shop-product-grid'),gallery=node('div','shop-product-gallery'),details=node('div','shop-product-info');grid.append(gallery,details);main.appendChild(grid);
        const imageBox=node('div','shop-product-image-wrapper'),image=node('img','shop-product-image');image.alt=snapshot.product.productName || '商品圖片';imageBox.appendChild(image);gallery.appendChild(imageBox);
        const navigation=node('div','pv-gallery-navigation'),previous=node('button','pv-image-arrow','‹'),thumbs=node('div','pv-thumbnails'),next=node('button','pv-image-arrow','›');
        previous.type=next.type='button';previous.setAttribute('aria-label','上一張商品圖片');next.setAttribute('aria-label','下一張商品圖片');navigation.append(previous,thumbs,next);gallery.appendChild(navigation);
        const titleRow=node('div','pv-title-row'),title=node('h1',null,snapshot.product.productName || '未填寫商品名稱');title.id='pv-name';
        const actions=node('div','pv-title-actions'),rating=node('span','pv-rating'),heart=node('button','pv-favorite');heart.type='button';heart.disabled=true;heart.setAttribute('aria-label','收藏外觀預覽');
        heart.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"/></svg>';
        actions.append(rating,heart);titleRow.append(title,actions);details.appendChild(titleRow);
        const description=node('p','paragraph-18',snapshot.product.productDesc || '');description.id='pv-description';details.appendChild(description);
        const supplier=node('div','pv-supplier-info paragraph-18'),supplierRow=node('div','pv-supplier-row');
        supplierRow.append(node('span','pv-supplier-label','供應：'),node('span','pv-supplier-link',snapshot.vendor?.label || '未提供'));
        if(snapshot.vendor?.hasLogo){const logo=node('img','pv-supplier-logo');logo.src=context+'/vendor/DBGifReader?vendorId='+encodeURIComponent(snapshot.vendor.id);logo.alt=snapshot.vendor.label+' LOGO';logo.width=logo.height=40;supplierRow.appendChild(logo);}
        const origin=node('div','pv-supplier-row');origin.append(node('span','pv-supplier-label','產地：'),node('span',null,(snapshot.vendor?.origin || '').trim().replace(/^(?:台灣|臺灣)[\s、，,／/：:-]*/,'') || '未提供'));supplier.append(supplierRow,origin);details.appendChild(supplier);
        const price=node('div','paragraph-18');price.id='pv-price';details.appendChild(price);
        details.appendChild(node('label','form_field-label','商品規格'));
        const controls=node('div','pv-spec-quantity-row'),select=node('select','form-input');select.id='pv-sku';select.setAttribute('aria-label','預覽商品規格');
        for(const sku of snapshot.skus){const option=node('option',null,sku.anotherName?.trim() || sku.skuName || '未填寫規格名稱');option.value=sku.key;select.appendChild(option);}
        if(snapshot.skus.some(sku=>sku.key===snapshot.selected))select.value=snapshot.selected;
        const stepper=node('div','pv-qty-stepper'),minus=node('button',null,'−'),quantity=node('input'),plus=node('button',null,'+');quantity.id='pv-qty';quantity.type='number';quantity.value='1';quantity.min='1';quantity.max='10';quantity.readOnly=true;quantity.setAttribute('aria-label','預覽購買數量');minus.type=plus.type='button';minus.setAttribute('aria-label','減少數量');plus.setAttribute('aria-label','增加數量');stepper.append(minus,quantity,plus);controls.append(select,stepper);details.appendChild(controls);
        const purchase=node('div','preview-purchase');for(const text of ['加入購物車','立即購買']){const button=node('button',null,text);button.type='button';button.disabled=true;purchase.appendChild(button);}details.appendChild(purchase);
        details.appendChild(node('p','preview-note','購物按鈕僅供外觀預覽。'));
        const images=snapshot.skus.flatMap(sku=>(sku.images || []).map(url=>({url,skuKey:sku.key})));
        let imageIndex=-1;
        function selectFirstImage(){imageIndex=images.findIndex(image=>image.skuKey===select.value);}
        function selected(){return snapshot.skus.find(sku=>sku.key===select.value) || snapshot.skus[0];}
        function updatePrice(){const value=selected()?.price;price.textContent=value!=null && Number.isFinite(Number(value))?'NT$ '+(Number(value)*Number(quantity.value)).toLocaleString('en-US'):'NT$ —';minus.disabled=Number(quantity.value)<=1;plus.disabled=Number(quantity.value)>=10;}
        function updateImages(){
            image.src=images[imageIndex]?.url || context+'/front/img/image-placeholder.svg';thumbs.replaceChildren();
            const first=Math.max(0,Math.min(images.length-3,imageIndex-1));
            images.slice(first,first+3).forEach(({url},index)=>{const button=node('button','pv-thumbnail'),thumbnail=node('img');button.type='button';button.setAttribute('aria-label','預覽商品圖片 '+(first+index+1));button.setAttribute('aria-pressed',String(first+index===imageIndex));thumbnail.src=url;thumbnail.alt='';button.appendChild(thumbnail);button.addEventListener('click',()=>{imageIndex=first+index;updateImages();});thumbs.appendChild(button);});
            previous.disabled=imageIndex<=0;next.disabled=imageIndex>=images.length-1;
        }
        previous.addEventListener('click',()=>{imageIndex--;updateImages();});next.addEventListener('click',()=>{imageIndex++;updateImages();});
        select.addEventListener('change',()=>{selectFirstImage();quantity.value='1';updatePrice();updateImages();});
        minus.addEventListener('click',()=>{quantity.value=String(Math.max(1,Number(quantity.value)-1));updatePrice();});plus.addEventListener('click',()=>{quantity.value=String(Math.min(10,Number(quantity.value)+1));updatePrice();});
        selectFirstImage();updatePrice();updateImages();
        fetch(context+'/front/api/product-reviews?productId='+encodeURIComponent(snapshot.product.productId)+'&size=1',{cache:'no-store',credentials:'same-origin'})
            .then(response=>response.ok?response.json():null).then(value=>{if(!value||!rating.isConnected)return;rating.setAttribute('aria-label','評分 '+Number(value.average).toFixed(1)+'／5');const empty=node('span','pv-rating-empty','★★★★★'),filled=node('span','pv-rating-filled','★★★★★');filled.style.width=Math.max(0,Math.min(5,Number(value.average)))*20+'%';rating.append(empty,filled);}).catch(()=>{});
    }
    window.ProductEditPreview={open(snapshot,focus){
        if(frame?.isConnected)return;
        frame=document.createElement('iframe');frame.title='未儲存商品的前台預覽';
        const previewFrame=frame;
        frame.addEventListener('load',()=>{if(frame===previewFrame && previewFrame.isConnected)render(previewFrame.contentDocument,snapshot);},{once:true});
        frame.srcdoc=`<!doctype html><html lang="zh-Hant"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>
        *{box-sizing:border-box}body{margin:0;font-family:Arial,'Microsoft JhengHei',sans-serif;color:#263323;background:#fff}#pv-main{min-height:100vh;padding:24px;background:radial-gradient(circle at 0 0,#fff0a6 0 150px,transparent 151px),radial-gradient(circle at 28% 110%,#dcecc8 0 140px,transparent 141px)}#pv-main .shop-product-grid{display:grid}#pv-main .pv-title-row{--pv-title-size:28px;gap:10px}#pv-description{font-size:14px;line-height:1.65;margin:24px 0 16px}.pv-supplier-info{font-size:14px}.pv-supplier-row{display:flex;align-items:center;gap:6px;margin:7px 0}.pv-supplier-link{color:#497339;text-decoration:underline}#pv-price{color:#497339;font-size:20px;margin:36px 0}.form_field-label{font-size:13px}.form-input{padding:7px;border:0;border-radius:5px;background:#ffe56d;color:#263323;font:inherit}.preview-purchase{display:grid;gap:12px;margin-top:44px}.preview-purchase button{min-height:44px;font:inherit;color:#666;background:#ffb080;border:0}.preview-purchase button+button{background:#fff;border:1px solid #666}.preview-note{font-size:12px;color:#777}.pv-favorite:disabled{opacity:1}.pv-rating{font-size:20px!important}
        </style></head><body class="front-theme"><main id="pv-main"></main></body></html>`;
        let host;
        try { if(window.parent!==window)host=window.parent.ProductDetailPreviewHost; } catch(_) {}
        closePreview=host?.open(window,frame,focus,()=>{frame=null;}) || showStandalone(frame,focus);
    }};
})();
