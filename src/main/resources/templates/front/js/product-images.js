// Image failures stay inside the card. Retry briefly, never reload the product page.
(() => {
    if (window.fruitudeProductImageRetry) return;
    window.fruitudeProductImageRetry = true;
    const attempts = new WeakMap();
    document.addEventListener('error', event => {
        const img = event.target;
        if (!(img instanceof HTMLImageElement) || !img.closest('.products-item-image-wrapper')) return;
        let url;
        try { url = new URL(img.currentSrc || img.src, document.baseURI); } catch (_) { return; }
        if (url.origin !== location.origin || !/\/product\/image\/\d+(?:\/thumbnail)?$/.test(url.pathname)) return;
        let state = attempts.get(img);
        if (!state || state.url !== url.href) {
            state = {url: url.href, count: 0, pending: false};
            attempts.set(img, state);
        }
        if (state.pending) return;
        if (state.count >= 2) {
            url.pathname = url.pathname.slice(0, url.pathname.indexOf('/product/image/')) + '/front/img/image-placeholder.svg';
            url.search = '?v=20261020-orange-lost-2';
            img.src = url.href;
            return;
        }
        state.pending = true;
        const delay = ++state.count * 1500;
        setTimeout(() => {
            state.pending = false;
            // Do not overwrite a newer SKU selection or a card removed by a new batch.
            if (!img.isConnected || img.src !== state.url) return;
            img.src = state.url;
        }, delay);
    }, true);
})();
