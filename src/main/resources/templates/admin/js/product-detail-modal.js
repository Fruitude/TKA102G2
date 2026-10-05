(() => {
    let dialog, frame, feedback, returnFocus, previousOverflow;

    function createDialog() {
        const style = document.createElement('style');
        style.textContent = `
            .product-detail-modal { width: min(900px, 95vw); max-width: 95vw; max-height: 92vh; padding: 0; border: 0; border-radius: 8px; background: #fff; color: #333; box-shadow: 0 12px 40px #0003; }
            .product-detail-modal::backdrop { background: rgb(0 0 0 / 45%); }
            .product-detail-modal header { display: flex; align-items: center; justify-content: space-between; padding: 10px 16px; border-bottom: 1px solid #e3e6f0; }
            .product-detail-modal h2 { margin: 0; font-size: 18px; font-family: inherit; }
            .product-detail-modal .detail-close-top { border: 0; padding: 0; width: 32px; height: 32px; font-size: 28px; line-height: 32px; color: #666; background: transparent; cursor: pointer; }
            .product-detail-modal iframe { display: block; width: 100%; height: min(65vh, 500px); border: 0; background: #fff; }
            .product-detail-modal [hidden] { display: none !important; }
            .product-detail-modal .detail-feedback { margin: 0; padding: 24px; height: min(65vh, 500px); box-sizing: border-box; }
            .product-detail-modal footer { display: flex; justify-content: flex-end; padding: 8px 16px; border-top: 1px solid #e3e6f0; }
            .product-detail-modal .detail-close-bottom { font: inherit; padding: 6px 12px; border: 1px solid #ddd; border-radius: 4px; background: #f8f9fc; color: #333; cursor: pointer; }
            .product-detail-modal button:focus-visible { outline: 2px solid #4e73df; outline-offset: 2px; }
        `;
        document.head.appendChild(style);
        dialog = document.createElement('dialog');
        dialog.className = 'product-detail-modal';
        dialog.id = 'product-detail-modal';
        dialog.setAttribute('aria-labelledby', 'product-detail-title');
        dialog.innerHTML = `
            <header><h2 id="product-detail-title">商品詳細資料</h2><button type="button" class="detail-close-top" aria-label="關閉商品詳細資料">×</button></header>
            <p class="detail-feedback" role="status" aria-live="polite">正在載入商品資料…</p>
            <iframe title="商品詳細資料" hidden></iframe>
            <footer><button type="button" class="detail-close-bottom">關閉</button></footer>
        `;
        document.body.appendChild(dialog);
        frame = dialog.querySelector('iframe');
        feedback = dialog.querySelector('.detail-feedback');
        dialog.querySelectorAll('button').forEach(button => button.addEventListener('click', () => dialog.close()));
        dialog.addEventListener('close', () => {
            document.body.style.overflow = previousOverflow;
            frame.removeAttribute('src');
            returnFocus?.focus({preventScroll: true});
        });
        dialog.addEventListener('click', event => {
            const rect = dialog.getBoundingClientRect();
            if (event.target === dialog && (event.clientX < rect.left || event.clientX > rect.right
                || event.clientY < rect.top || event.clientY > rect.bottom)) dialog.close();
        });
        frame.addEventListener('load', () => {
            if (!dialog.open || !frame.getAttribute('src')) return;
            try {
                if (!frame.contentDocument?.querySelector('.detail-layout')) throw new Error('No product');
                frame.hidden = false;
                feedback.hidden = true;
                // Escape still closes the parent dialog when focus is inside the detail iframe.
                frame.contentDocument.addEventListener('keydown', event => {
                    if (event.key === 'Escape') { event.preventDefault(); dialog.close(); }
                });
            } catch (_) {
                feedback.textContent = '商品資料載入失敗，請關閉後再試。';
                frame.hidden = true;
            }
        });
    }

    window.openProductDetails = (url, source) => {
        if (!dialog) createDialog();
        returnFocus = source;
        previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        frame.hidden = true;
        feedback.hidden = false;
        feedback.textContent = '正在載入商品資料…';
        frame.src = url;
        dialog.showModal();
        dialog.querySelector('.detail-close-top').focus();
    };

    document.querySelectorAll('[data-product-detail]').forEach(button => {
        button.addEventListener('click', () => {
            // Use the outer product page so its sidebar and tab bar are dimmed as well.
            let host = window;
            try {
                if (window.parent !== window && typeof window.parent.openProductDetails === 'function') host = window.parent;
            } catch (_) { /* Direct list pages also provide their own dialog. */ }
            host.openProductDetails(button.dataset.detailUrl, button);
        });
    });
})();
