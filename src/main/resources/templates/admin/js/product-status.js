(() => {
    // Confirmation is required each time: this decision must never use today's suppression preference.
    window.requestProductStatus = async (url, body) => {
        const send = () => fetch(url, {method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body});
        let response = await send();
        if (response.status === 409) {
            const copy = response.clone();
            const message = (response.headers.get('Content-Type') || '').includes('application/json')
                ? (await copy.json()).message : await copy.text();
            if (!window.confirm(message)) throw new Error('已取消上架，商品與規格未變更。');
            body.set('activateSkus', 'true'); response = await send();
        }
        return response;
    };
    document.querySelectorAll('form[data-product-status-form]').forEach(form => {
        form.addEventListener('submit', event => {
            const productStatus = form.querySelector('select[name="status"]');
            if (!productStatus || productStatus.value !== '1') return;
            const skuStates = [...form.querySelectorAll('select[name^="productSkus["][name$=".status"]')];
            const sellable = skuStates.length ? skuStates.some(s => {
                if (s.value === '1' || s.value === '2') return true;
                if (s.value === '6') {
                    const prefix=s.name.slice(0,-6);
                    return Number(form.elements.namedItem(prefix+'stock')?.value||0)-Number(form.elements.namedItem(prefix+'outboundQty')?.value||0)>0;
                }
                if (s.value !== '3') return false;
                const prefix = s.name.slice(0, -6);
                const number = key => Number(form.elements.namedItem(prefix + key)?.value || 0);
                return number('stock') + number('inboundQty') - number('outboundQty') > 0;
            }) : form.dataset.hasSellableSku === 'true';
            const ready = skuStates.length ? skuStates.some(s => s.value === '5') : form.dataset.hasReadySku === 'true';
            if (sellable || ready) return;
            if (!window.confirm('商品沒有上架／缺貨／即將售完規格，是否將未永久停產的規格一併上架？')) {
                event.preventDefault(); return;
            }
            let confirmed = form.querySelector('input[name="activateSkus"]');
            if (!confirmed) { confirmed = document.createElement('input'); confirmed.type = 'hidden'; confirmed.name = 'activateSkus'; form.append(confirmed); }
            confirmed.value = 'true';
        });
    });
})();
