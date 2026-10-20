(() => {
    let dialog, frame, feedback, returnFocus, previousOverflow, currentDetailUrl, editing=false, saving=false, refreshAfterClose=false, returnToDetail=false;
    let previewFrame, previewFocus, previewClosed, closeGuardUntil=0, previewTransition=false;
    function alignTitleWithImage() {
        if (!dialog.open || frame.hidden) return;
        const imageBox=frame.contentDocument?.querySelector('.gallery');if(!imageBox)return;
        const imageLeft=frame.getBoundingClientRect().left+imageBox.getBoundingClientRect().left;
        dialog.style.setProperty('--detail-title-left',(imageLeft-dialog.querySelector('header').getBoundingClientRect().left)+'px');
        if (editing) {
            const modeLeft = dialog.querySelector('.detail-mode').getBoundingClientRect().left;
            dialog.style.setProperty('--detail-title-width',Math.min(imageBox.getBoundingClientRect().width, Math.max(120, modeLeft-imageLeft-12))+'px');
        }
    }
    function editor() { return frame.contentWindow?.detailEditor; }
    function validateTitleName() {
        const title=dialog.querySelector('h2');
        const empty=editing && !title.textContent.trim();
        const validation=editing?editor()?.getNameValidation?.():null;
        const message=editing?(empty?'請填寫商品名稱':validation?.message||''):'';
        const invalid=empty||!!validation?.invalid;
        const error=dialog.querySelector('.detail-name-error');error.textContent=message;error.hidden=!message;error.classList.toggle('is-pending',!!validation?.pending);
        title.setAttribute('aria-invalid',String(invalid));
        dialog.querySelector('.detail-title-editor').classList.toggle('has-name-error',invalid);
        return !invalid;
    }
    function applyMode() {
        dialog.classList.toggle('is-editing',editing);
        dialog.querySelector('.detail-mode').hidden=!editing;
        dialog.querySelector('.detail-edit').hidden=editing;
        dialog.querySelector('.detail-save').hidden=!editing;
        dialog.querySelector('.detail-close-bottom').textContent=editing?'取消':'關閉';
        const title=dialog.querySelector('h2');title.contentEditable=editing?'plaintext-only':'false';
        title.title=editing?'點擊修改商品名稱':'';
        dialog.querySelector('.detail-title-pencil').hidden=!editing;
        validateTitleName();
        alignTitleWithImage();
    }
    function closeDialog() {
        if(previewTransition || performance.now()<closeGuardUntil)return;
        if(previewFrame){closePreview();return;}
        if(saving)return;
        if(editing&&editor()?.isDirty()&&!window.confirm('尚有未儲存的修改，確定要取消嗎？'))return;
        if(editing && returnToDetail){returnToDetail=false;loadMode(false);return;}
        dialog.close();
    }
    async function animatePreviewFrame(element,entering) {
        if(window.matchMedia('(prefers-reduced-motion: reduce)').matches)return;
        const animation=element.animate(entering
            ?[{opacity:0,transform:'translateY(8px)'},{opacity:1,transform:'translateY(0)'}]
            :[{opacity:1,transform:'translateY(0)'},{opacity:0,transform:'translateY(-8px)'}],
            {duration:entering?240:160,easing:entering?'ease-out':'ease-in',fill:'forwards'});
        try { await animation.finished; } catch(_) {} finally { animation.cancel(); }
    }
    async function closePreview() {
        if(!previewFrame || previewTransition)return;
        previewTransition=true;
        await animatePreviewFrame(previewFrame,false);
        closeGuardUntil=performance.now()+650;
        previewFrame.remove();previewFrame=null;
        dialog.style.removeProperty('--detail-preview-height');
        dialog.classList.remove('is-previewing');frame.hidden=false;
        dialog.querySelector('.detail-title-editor').hidden=false;
        dialog.querySelector('.detail-preview-title').remove();
        dialog.querySelector('.detail-close-top').setAttribute('aria-label','關閉商品詳細資料');
        applyMode();
        const closed=previewClosed;previewClosed=null;closed?.();
        previewFocus?.focus({preventScroll:true});previewFocus=null;
        await animatePreviewFrame(frame,true);
        previewTransition=false;
    }
    window.ProductDetailPreviewHost={open(source,content,focus,onClose){
        if(!dialog?.open || !editing || saving || previewFrame || frame.contentWindow!==source)return null;
        previewFrame=content;previewFocus=focus;previewClosed=onClose;
        dialog.style.setProperty('--detail-preview-height',dialog.getBoundingClientRect().height+'px');
        previewTransition=true;content.hidden=true;dialog.classList.add('is-previewing');
        dialog.querySelector('.detail-title-editor').hidden=true;
        const label=document.createElement('span');label.className='detail-preview-title';label.textContent='前台外觀預覽 · 尚未儲存';
        dialog.querySelector('header').prepend(label);
        dialog.querySelectorAll('.detail-mode,.detail-edit,.detail-save').forEach(element=>element.hidden=true);
        dialog.querySelector('.detail-close-bottom').textContent='關閉預覽';
        dialog.querySelector('.detail-close-top').setAttribute('aria-label','關閉預覽');
        content.className='detail-preview-frame';dialog.insertBefore(content,dialog.querySelector('footer'));
        dialog.querySelector('.detail-close-top').focus();
        animatePreviewFrame(frame,false).then(async()=>{
            frame.hidden=true;content.hidden=false;
            await animatePreviewFrame(content,true);
            previewTransition=false;
        });
        return closePreview;
    }};
    let modeVersion = 0;
    function loadMode(mode) {
        const version = ++modeVersion;
        const animate = dialog.open && !frame.hidden && editing !== mode && !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        editing=mode;applyMode();
        const url=new URL(currentDetailUrl,location.href);url.searchParams.set('modal','true');
        if(editing)url.searchParams.set('edit','true');else url.searchParams.delete('edit');
        dialog.querySelector('.detail-edit').disabled=true;dialog.querySelector('.detail-save').disabled=true;
        dialog.querySelector('.detail-save-error').hidden=true;
        function beginLoad() {
            if(version!==modeVersion || (animate && !dialog.open)) return;
            frame.hidden=true;feedback.hidden=false;feedback.textContent=editing?'正在載入商品修改資料…':'正在載入商品資料…';
            frame.src=url.href;
        }
        if(animate) {
            const animation=frame.animate([{opacity:1,transform:'translateY(0)'},{opacity:0,transform:'translateY(-8px)'}],{duration:160,easing:'ease-in',fill:'forwards'});
            animation.finished.then(()=>{animation.cancel();beginLoad();}).catch(()=>{});
        } else beginLoad();
    }
    function createDialog() {
        const style=document.createElement('style');style.textContent=`
            .product-detail-modal { width:min(900px,95vw);max-width:95vw;max-height:92vh;padding:0;border:0;border-radius:8px;background:#fff;color:#333;box-shadow:0 12px 40px #0003; }
            .product-detail-modal.is-previewing[open] { height:var(--detail-preview-height); display:flex; flex-direction:column; overflow:hidden; box-sizing:border-box; }
            .product-detail-modal.is-previewing .detail-preview-frame { flex:1; min-height:0; height:auto; }
            .product-detail-modal.is-previewing header, .product-detail-modal.is-previewing footer { flex-shrink:0; box-sizing:border-box; }
            .product-detail-modal .detail-preview-title { margin-right:auto;font-size:18px; }
            .product-detail-modal::backdrop { background:rgb(0 0 0 / 45%); }
            .product-detail-modal header { display:flex;align-items:center;justify-content:flex-end;position:relative;min-height:64px;padding:10px 16px;border-bottom:1px solid #e3e6f0; transition:background-color .28s ease,color .28s ease; }
            .product-detail-modal h2 { position:absolute;left:var(--detail-title-left,16px);right:56px;min-width:0;margin:0;padding-left:1em;font-size:18px;line-height:22px;font-family:inherit;text-align:left;overflow-wrap:anywhere;display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden; }
            .product-detail-modal.is-editing header { background:#4e73df;color:#fff; }
            .product-detail-modal .detail-title-editor { display:contents; }
            .product-detail-modal.is-editing .detail-title-editor { position:absolute;left:var(--detail-title-left,16px);width:var(--detail-title-width,280px);min-height:34px;display:flex;align-items:center;gap:4px;background:#fff;border:1px solid #cbd5e1;border-radius:4px;box-sizing:border-box; }
            .product-detail-modal.is-editing h2 { position:static;flex:1;min-width:0;right:auto;padding:5px 8px;color:#333;cursor:text; }
            .product-detail-modal.is-editing .detail-title-editor:focus-within { outline:2px solid #c9d7ff;outline-offset:1px; }
            .product-detail-modal .detail-title-pencil { flex:0 0 28px;width:28px;height:30px;padding:4px;border:0;background:#fff;color:#7b8794;cursor:pointer; }
            .product-detail-modal .detail-title-pencil:hover { color:#4e73df; }
            .product-detail-modal .detail-title-pencil svg { width:16px;height:16px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round; }
            .product-detail-modal .detail-mode { display:inline-flex;align-items:center;gap:6px;font-size:13px;margin-right:12px;white-space:nowrap; }
            .product-detail-modal .detail-mode-icon { width:20px;height:20px;fill:none;stroke:currentColor;stroke-width:2;stroke-linejoin:round;flex-shrink:0; }
            .product-detail-modal .detail-close-top { flex-shrink:0;border:0;padding:0;width:32px;height:32px;font-size:28px;line-height:32px;color:#666;background:transparent;cursor:pointer; }
            .product-detail-modal.is-editing .detail-close-top { color:white; }
            .product-detail-modal iframe { display:block;width:100%;height:min(65vh,500px);border:0;background:#fff; }
            .product-detail-modal [hidden] { display:none!important; }
            .product-detail-modal .detail-feedback { margin:0;padding:24px;height:min(65vh,500px);box-sizing:border-box; }
            .product-detail-modal footer { display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px;padding:8px 16px;border-top:1px solid #e3e6f0; }
            .product-detail-modal footer button { font:inherit;padding:6px 12px;border:1px solid #ddd;border-radius:4px;background:#f8f9fc;color:#333;cursor:pointer; }
            .product-detail-modal footer button:hover:not(:disabled) { border-color:#4e73df;color:#4e73df; }
            .product-detail-modal footer button:disabled { opacity:.5;cursor:wait; }
            .product-detail-modal .detail-save { color:white;background:#4e73df;border-color:#4e73df; }
            .product-detail-modal .detail-save:hover:not(:disabled) { color:white;background:#365bc7; }
            .product-detail-modal .detail-save-error { flex-basis:100%;margin:0;color:#b42318;font-size:13px; }
            .product-detail-modal .detail-name-error { position:absolute;left:calc(100% + 8px);top:50%;transform:translateY(-50%);padding:1px 4px;border-radius:3px;background:#fff;color:#b42318;font-size:12px;line-height:16px;white-space:nowrap; }
            .product-detail-modal .detail-title-editor.has-name-error { border-color:#b42318; }
            .product-detail-modal .detail-name-error { width:max-content;max-width:none;white-space:nowrap; }
            .product-detail-modal .detail-name-error.is-pending { color:#6b7280; }
            .product-detail-modal button:focus-visible { outline:2px solid #4e73df;outline-offset:2px; }
        `;document.head.appendChild(style);
        dialog=document.createElement('dialog');dialog.className='product-detail-modal';dialog.id='product-detail-modal';dialog.setAttribute('aria-labelledby','product-detail-title');
        dialog.innerHTML=`<header><div class="detail-title-editor"><h2 id="product-detail-title">商品</h2><button type="button" class="detail-title-pencil" aria-label="修改商品名稱" title="修改商品名稱" hidden><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m16 3 5 5-12 12-6 1 1-6L16 3Z"/><path d="m13 6 5 5M4 15l5 5"/></svg></button><small class="detail-name-error" id="detail-name-error" role="alert" hidden>請填寫商品名稱</small></div><span class="detail-mode" hidden><svg class="detail-mode-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m9.5 2-.5 3-2 .9-2.5-1.1-2 3.4 2 1.9-.2 2.2-2 1.8 2 3.5 2.6-1 2 .9.5 3h4l.5-3 2-.9 2.6 1 2-3.5-2-1.8-.2-2.2 2-1.9-2-3.4-2.5 1.1-2-.9-.5-3z"/><circle cx="11.5" cy="11" r="3"/></svg>修改模式</span><button type="button" class="detail-close-top" aria-label="關閉商品詳細資料">×</button></header>
            <p class="detail-feedback" role="status" aria-live="polite">正在載入商品資料…</p><iframe title="商品詳細資料" hidden></iframe>
            <footer><p class="detail-save-error" role="alert" hidden></p><button type="button" class="detail-edit" disabled>修改</button><button type="button" class="detail-save" hidden disabled>儲存</button><button type="button" class="detail-close-bottom">關閉</button></footer>`;
        document.body.appendChild(dialog);frame=dialog.querySelector('iframe');feedback=dialog.querySelector('.detail-feedback');
        new ResizeObserver(alignTitleWithImage).observe(frame);
        dialog.querySelectorAll('.detail-close-top, .detail-close-bottom').forEach(button=>button.addEventListener('click',closeDialog));
        dialog.addEventListener('cancel',event=>{event.preventDefault();closeDialog();});
        dialog.querySelector('.detail-edit').addEventListener('click',()=>{returnToDetail=true;loadMode(true);});
        const title=dialog.querySelector('h2');title.setAttribute('aria-describedby','detail-name-error');
        dialog.querySelector('.detail-title-pencil').addEventListener('click',()=>title.focus());
        title.addEventListener('input',()=>{if(editing){editor()?.setName(title.textContent);validateTitleName();}});
        title.addEventListener('blur',()=>{if(editing)editor()?.checkName?.();});
        title.addEventListener('keydown',event=>{if(editing&&event.key==='Enter'){event.preventDefault();title.blur();}});
        dialog.querySelector('.detail-save').addEventListener('click',async()=>{
            const error=dialog.querySelector('.detail-save-error');error.hidden=true;saving=true;
            dialog.querySelector('.detail-save').disabled=true;
            try {
                if(!validateTitleName()){title.focus();throw new Error(dialog.querySelector('.detail-name-error').textContent);}
                editor().setName(title.textContent);
                await editor().save();
                refreshAfterClose=true;
                loadMode(false);
            }catch(failure){error.textContent=failure.message||'儲存失敗';error.hidden=false;}
            finally{saving=false;dialog.querySelector('.detail-save').disabled=false;}
        });
        dialog.addEventListener('close',()=>{
            document.body.style.overflow=previousOverflow;frame.removeAttribute('src');returnFocus?.focus({preventScroll:true});
            if(refreshAfterClose){
                refreshAfterClose=false;
                const owner=returnFocus?.ownerDocument.defaultView;
                if(owner && owner!==frame.contentWindow)owner.location.reload();else location.reload();
            }
        });
        let backdropPress = false;
        function isBackdrop(event) {
            const rect = dialog.getBoundingClientRect();
            return event.target === dialog && (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom);
        }
        dialog.addEventListener('pointerdown', event => { backdropPress = event.button === 0 && isBackdrop(event); });
        dialog.addEventListener('pointercancel', () => { backdropPress = false; });
        dialog.addEventListener('click', event => {
            const close = backdropPress && isBackdrop(event);
            backdropPress = false;
            if (close) closeDialog();
        });
        frame.addEventListener('load',async()=>{
            if(!dialog.open||!frame.getAttribute('src'))return;
            try {
                if(!frame.contentDocument?.querySelector('.detail-layout'))throw new Error('商品資料載入失敗');
                if(editing){if(!editor())throw new Error('修改功能未載入，請重新整理頁面');await editor().ready;}
                frame.contentWindow.addEventListener('product:name-validation',validateTitleName);
                const name=editing?editor().getName():frame.contentDocument.querySelector('h1')?.textContent.trim();
                if(name){title.textContent=name;frame.title=name;}
                validateTitleName();
                frame.hidden=false;feedback.hidden=true;
                if(!window.matchMedia('(prefers-reduced-motion: reduce)').matches)
                    frame.animate([{opacity:0,transform:'translateY(8px)'},{opacity:1,transform:'translateY(0)'}],{duration:240,easing:'ease-out'});dialog.querySelector('.detail-edit').disabled=false;dialog.querySelector('.detail-save').disabled=false;alignTitleWithImage();
                frame.contentDocument.addEventListener('product-status-updated',event=>returnFocus?.ownerDocument.dispatchEvent(new CustomEvent('product-status-updated',{detail:event.detail})));
                frame.contentDocument.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();closeDialog();}});
            }catch(failure){feedback.textContent=failure.message||'商品資料載入失敗，請關閉後再試。';frame.hidden=true;}
        });
    }
    window.openProductDetails=(url,source,edit=false)=>{
        if(!dialog)createDialog();returnToDetail=false;returnFocus=source;currentDetailUrl=url;
        dialog.querySelector('h2').textContent=source?.dataset.detailName||'商品';
        previousOverflow=document.body.style.overflow;document.body.style.overflow='hidden';
        dialog.style.removeProperty('--detail-title-left');dialog.style.removeProperty('--detail-title-width');loadMode(edit);dialog.showModal();dialog.querySelector('.detail-close-top').focus();
    };
    document.addEventListener('click',event=>{
            const button=event.target.closest('[data-product-detail], [data-product-edit]');
            if(!button)return;
            event.preventDefault();let host=window;
            try{if(window.parent!==window&&typeof window.parent.openProductDetails==='function')host=window.parent;}catch(_){}
            host.openProductDetails(button.dataset.detailUrl,button,button.hasAttribute('data-product-edit'));
    });
})();
