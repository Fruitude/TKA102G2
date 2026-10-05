(() => {
    const tabs = Array.from(document.querySelectorAll('[data-product-tab]'));
    const frame = document.getElementById('product-frame');
    const panel = document.getElementById('product-panel');
    const status = document.getElementById('product-load-status');
    if (!frame || !tabs.length) return;
    let observer;
    let preparedDocument;
    const storageKey = 'fruitude:product-tab:' + location.pathname;

    function savedTab() {
        try {
            return tabs.find(tab => tab.id === sessionStorage.getItem(storageKey));
        } catch (_) {
            return undefined;
        }
    }

    function markActive(tab) {
        tabs.forEach(item => {
            const active = item === tab;
            item.classList.toggle('active', active);
            item.setAttribute('aria-selected', String(active));
            item.tabIndex = active ? 0 : -1;
        });
        panel.setAttribute('aria-labelledby', tab.id);
        frame.title = tab.textContent.trim();
        try {
            sessionStorage.setItem(storageKey, tab.id);
        } catch (_) {
            // Tab switching still works when browser storage is unavailable.
        }
    }

    function selectTab(tab) {
        markActive(tab);
        status.hidden = false;
        status.textContent = '正在載入' + frame.title + '…';
        panel.setAttribute('aria-busy', 'true');
        const url = new URL(tab.dataset.url, location.href);
        if (tab.id === 'tab-overview') {
            try {
                const saved = JSON.parse(localStorage.getItem('fruitude:product-list:' + url.pathname) || '{}');
                if ([10, 20, 50, 100].includes(saved?.pageSize)) url.searchParams.set('size', String(saved.pageSize));
                if (['all', 'on', 'off'].includes(saved?.statusFilter)) url.searchParams.set('statusFilter', saved.statusFilter);
                else if (typeof saved?.hideOffline === 'boolean') url.searchParams.set('statusFilter', saved.hideOffline ? 'on' : 'all');
                const stockFilter = saved?.stockFilter === 'low-stock' ? 'below-safety' : saved?.stockFilter;
                if (['all', 'normal', 'abnormal', 'below-safety', 'high-stock', 'inbound', 'outbound', 'unset-safety'].includes(stockFilter)) {
                    url.searchParams.set('stockFilter', stockFilter);
                }
                ['minComments', 'maxComments'].forEach(name => {
                    if (Object.prototype.hasOwnProperty.call(saved, name)) {
                        const value = String(saved[name] ?? '');
                        url.searchParams.set(name, /^[0-9]+$/.test(value) && Number(value) <= 2147483647 ? value : '');
                    }
                });
                if (Object.prototype.hasOwnProperty.call(saved, 'ratingFilter')) {
                    url.searchParams.set('ratingFilter', ['unrated', '0-1', '1-2', '2-3', '3-4', '4-5'].includes(saved.ratingFilter) ? saved.ratingFilter : '');
                }
                ['parentCategoryId', 'categoryId', 'vendorId'].forEach(name => {
                    if (Object.prototype.hasOwnProperty.call(saved, name)) {
                        const value = String(saved[name] ?? '');
                        url.searchParams.set(name, /^[1-9]\d*$/.test(value) ? value : '');
                    }
                });
            } catch (_) { /* The server also reads saved preference cookies. */ }
        }
        frame.src = url.href;
    }

    tabs.forEach((tab, index) => {
        tab.addEventListener('click', () => selectTab(tab));
        tab.addEventListener('keydown', event => {
            let next;
            if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
            if (event.key === 'ArrowLeft') next = (index + tabs.length - 1) % tabs.length;
            if (event.key === 'Home') next = 0;
            if (event.key === 'End') next = tabs.length - 1;
            if (next !== undefined) {
                event.preventDefault();
                tabs[next].focus();
                selectTab(tabs[next]);
            }
        });
    });

    function prepareContent() {
        if (observer) observer.disconnect();
        const doc = frame.contentDocument;
        if (!doc || !doc.body) return;
        if (doc.querySelector('#product-table') && doc.documentElement.dataset.productListReady !== 'true') {
            doc.addEventListener('product-list-ready', prepareContent, { once: true });
            return;
        }
        if (preparedDocument === doc) return;
        preparedDocument = doc;
        frame.contentWindow.addEventListener('beforeunload', () => {
            status.hidden = false;
            status.textContent = '正在載入' + frame.title + '…';
            panel.setAttribute('aria-busy', 'true');
        });
        const style = doc.createElement('style');
        style.textContent = 'html,body{background:#fff!important}body{margin:0!important;padding:20px!important}' +
            'body>.container,body>main{width:100%!important;max-width:none!important;min-width:0!important;margin:0!important;padding:0!important}' +
            'body>main>nav{display:none!important}[hidden]{display:none!important}';
        if (doc.querySelector('#product-table')) {
            style.textContent += 'html{padding:0!important}body{padding-top:15px!important}.list-controls{margin-top:0!important}';
        }
        doc.head.appendChild(style);

        // The outer page already supplies the return link and function tabs.
        doc.querySelectorAll('a[href]').forEach(link => {
            const path = new URL(link.href).pathname;
            if (path.endsWith('/admin/psi/product') || link.textContent.trim() === '返回首頁') link.hidden = true;
        });
        doc.addEventListener('click', event => {
            const link = event.target.closest('a[href]');
            if (!link || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
            const url = new URL(link.href);
            const target = tabs.find(tab => new URL(tab.dataset.url, location.href).pathname === url.pathname);
            if (target && !url.search) {
                event.preventDefault();
                selectTab(target);
            }
        });
        const current = tabs.find(tab => new URL(tab.dataset.url, location.href).pathname === frame.contentWindow.location.pathname);
        if (current) markActive(current);

        const resize = () => {
            const overviewTable = doc.getElementById('product-table');
            const tabBar = document.querySelector('.product-tabs');
            if (overviewTable && tabBar) {
                const tableRect = overviewTable.getBoundingClientRect();
                const left = frame.getBoundingClientRect().left - tabBar.parentElement.getBoundingClientRect().left + tableRect.left;
                tabBar.style.setProperty('--product-tabs-width', tableRect.width + 'px');
                tabBar.style.setProperty('--product-tabs-left', left + 'px');
            }
            let bottom = 0;
            Array.from(doc.body.children).forEach(child => {
                if (child.tagName !== 'SCRIPT' && child.tagName !== 'STYLE') {
                    bottom = Math.max(bottom, child.getBoundingClientRect().bottom + frame.contentWindow.scrollY);
                }
            });
            frame.style.height = Math.max(520, Math.ceil(bottom) + 24) + 'px';
        };
        if (typeof ResizeObserver !== 'undefined') {
            observer = new ResizeObserver(resize);
            observer.observe(doc.body);
        }
        resize();
        status.hidden = true;
        panel.setAttribute('aria-busy', 'false');
    }

    frame.addEventListener('load', prepareContent);
    const restoredTab = savedTab();
    // Load only the selected tab, rather than loading overview and then restoring another tab.
    selectTab(restoredTab || tabs[0]);
})();
