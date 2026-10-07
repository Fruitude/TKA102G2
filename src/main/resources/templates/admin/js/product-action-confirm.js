(() => {
    let pending = false;
    const storagePrefix = 'fruitude:product-confirm:';
    function today() {
        const date = new Date();
        return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0');
    }
    function skippedToday(action) {
        try { return localStorage.getItem(storagePrefix + action) === today(); }
        catch (_) { return false; }
    }
    const style = document.createElement('style');
    style.textContent = `
        .product-action-confirm { position: fixed; top: 12px; bottom: auto; left: 0; right: 0; margin: 0 auto; overflow-y: auto; max-width: min(440px, 90vw); box-sizing: border-box; padding: 22px; border: 1px solid #666; border-radius: 8px; background: white; color: #222; font: inherit; box-shadow: 0 8px 30px #0003; }
        .product-action-confirm::backdrop { background: rgb(0 0 0 / 40%); }
        .product-action-confirm h2 { display: flex; align-items: center; gap: 8px; margin: 0 0 14px; font-size: 18px; }
        .product-action-confirm .confirm-warning-icon { width: 24px; height: 24px; flex-shrink: 0; }
        .product-action-confirm p { margin: 0 0 18px; line-height: 1.6; white-space: pre-line; }
        .product-action-confirm label { display: flex; align-items: center; gap: 6px; font-size: 14px; }
        .product-action-confirm footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 18px; }
        .product-action-confirm button { border: 1px solid #666; background: #fff; color: #000; font: inherit; padding: 5px 14px; border-radius: 4px; cursor: pointer; }
        .product-action-confirm button:hover { background: #f3f4f6; }
    `;
    document.head.appendChild(style);
    const dialog = document.createElement('dialog');
    dialog.className = 'product-action-confirm';
    dialog.setAttribute('aria-labelledby', 'product-action-confirm-title');
    dialog.setAttribute('aria-describedby', 'product-action-confirm-message');
    dialog.innerHTML = `<h2 id="product-action-confirm-title"><svg class="confirm-warning-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2 23 22H1Z" fill="#facc15" stroke="#ca8a04" stroke-linejoin="round"/><path d="M12 8v6" stroke="#222" stroke-width="2" stroke-linecap="round"/><circle cx="12" cy="18" r="1" fill="#222"/></svg><span>確認操作</span></h2>
        <p id="product-action-confirm-message"></p>
        <label><input type="checkbox" id="product-confirm-skip-today">今天不再提示</label>
        <footer><button type="button" data-confirm-accept>確定</button><button type="button" data-confirm-cancel>取消</button></footer>`;
    document.body.appendChild(dialog);
    const message = dialog.querySelector('p');
    const skip = dialog.querySelector('input');
    const cancel = dialog.querySelector('[data-confirm-cancel]');
    const accept = dialog.querySelector('[data-confirm-accept]');
    cancel.addEventListener('click', () => dialog.close('cancel'));
    accept.addEventListener('click', () => dialog.close('accept'));
    dialog.addEventListener('cancel', event => { event.preventDefault(); dialog.close('cancel'); });

    function positionDialog() {
        if (!dialog.open) return;
        let visibleTop = 0, visibleBottom = window.innerHeight;
        try {
            if (window.frameElement) {
                const frameRect = window.frameElement.getBoundingClientRect();
                visibleTop = Math.max(0, -frameRect.top);
                visibleBottom = Math.min(visibleBottom, window.parent.innerHeight - frameRect.top);
            }
        } catch (_) { /* Standalone placement still works with cross-origin parents. */ }
        dialog.style.maxHeight = Math.max(100, visibleBottom - visibleTop - 24) + 'px';
        const searchTop = document.getElementById('product-search-form')?.getBoundingClientRect().top ?? visibleTop + 12;
        const top = Math.max(visibleTop + 12, Math.min(searchTop, visibleBottom - dialog.getBoundingClientRect().height - 12));
        dialog.style.top = top + 'px';
    }
    window.addEventListener('resize', positionDialog);
    window.addEventListener('scroll', positionDialog, {passive: true});
    try {
        if (window.parent !== window) {
            window.parent.addEventListener('scroll', positionDialog, {passive: true});
            window.parent.addEventListener('resize', positionDialog);
            window.addEventListener('pagehide', () => {
                window.parent.removeEventListener('scroll', positionDialog);
                window.parent.removeEventListener('resize', positionDialog);
            }, {once: true});
        }
    } catch (_) { /* Parent viewport access may be restricted. */ }

    window.confirmProductAction = (action, text) => {
        if (pending) return Promise.resolve(false);
        if (skippedToday(action)) return Promise.resolve(true);
        pending = true;
        const focused = document.activeElement;
        message.textContent = text;
        skip.checked = false;
        dialog.returnValue = '';
        return new Promise(resolve => {
            dialog.addEventListener('close', () => {
                const confirmed = dialog.returnValue === 'accept';
                if (confirmed && skip.checked) {
                    try { localStorage.setItem(storagePrefix + action, today()); }
                    catch (_) { /* Confirmation remains usable when storage is blocked. */ }
                }
                pending = false;
                focused?.focus({preventScroll: true});
                resolve(confirmed);
            }, {once: true});
            dialog.showModal();
            positionDialog();
            cancel.focus({preventScroll: true});
        });
    };

    const approvedForms = new WeakSet();
    document.querySelectorAll('form[data-product-delete]').forEach(form => {
        form.addEventListener('submit', async event => {
            if (approvedForms.delete(form)) return;
            event.preventDefault();
            const submitter = event.submitter;
            const confirmed = await window.confirmProductAction('delete',
                '刪除有可能造成客戶歷史訂單顯示錯誤，或客戶未完成訂單顯示錯誤，您確定要刪除這筆商品嗎？\n【建議使用下架功能】');
            if (!confirmed) return;
            approvedForms.add(form);
            form.requestSubmit(submitter || undefined);
        });
    });
})();
