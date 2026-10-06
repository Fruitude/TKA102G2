(() => {
    const table = document.getElementById('product-table');
    if (!table) return;
    const rows = Array.from(table.querySelectorAll('[data-product-row]'));
    const primaryFilters = document.querySelector('.filter-row-primary');
    const secondaryFilters = document.querySelector('.filter-row-secondary');
    function alignFilterColumns() {
        if (!primaryFilters || !secondaryFilters) return;
        const upper = Array.from(primaryFilters.children);
        const lower = Array.from(secondaryFilters.children);
        const widths = upper.slice(0, 3).map((control, index) =>
            Math.max(control.getBoundingClientRect().width, lower[index].getBoundingClientRect().width) + 'px');
        primaryFilters.parentElement.style.setProperty('--filter-columns', widths.join(' ') + ' max-content');
    }
    alignFilterColumns();
    if (primaryFilters && typeof ResizeObserver !== 'undefined') {
        const filterObserver = new ResizeObserver(alignFilterColumns);
        filterObserver.observe(primaryFilters);
        window.addEventListener('pagehide', () => filterObserver.disconnect(), { once: true });
    }
    if (document.fonts) document.fonts.ready.then(alignFilterColumns);
    const sizeSelect = document.getElementById('page-size');
    const statusFilter = document.getElementById('status-filter');
    const stockFilter = document.getElementById('stock-filter');
    const parentCategory = document.getElementById('parent-category');
    const childCategory = document.getElementById('child-category');
    const vendor = document.getElementById('filter-vendor');
    const minComments = document.getElementById('min-comments');
    const maxComments = document.getElementById('max-comments');
    const ratingFilter = document.getElementById('rating-filter');
    const reviewControls = [[minComments, 'minComments', 'fruitudeProductMinComments'],
        [maxComments, 'maxComments', 'fruitudeProductMaxComments'], [ratingFilter, 'ratingFilter', 'fruitudeProductRatingFilter']];
    const pager = document.getElementById('product-pagination');
    const summary = document.getElementById('page-summary');
    const empty = document.getElementById('empty-products');
    const storageKey = 'fruitude:product-list:' + location.pathname;
    const slideshows = [];

    const page = Number(table.dataset.page) || 1;
    const totalPages = Number(table.dataset.totalPages) || 0;
    let sortBy = table.dataset.sortBy || '';
    let sortDirection = table.dataset.sortDirection === 'desc' ? 'desc' : 'asc';
    const sortButtons = Array.from(table.querySelectorAll('[data-sort]'));
    sortButtons.forEach(button => {
        const selected = button.dataset.sort === sortBy;
        const nextDirection = selected && sortDirection === 'asc' ? 'desc' : 'asc';
        button.title = '點擊依' + (button.dataset.sort === 'comments' ? '總評數' : '平均分') + (nextDirection === 'asc' ? '升冪' : '降冪') + '排列';
        button.querySelectorAll('[data-direction]').forEach(arrow => {
            arrow.classList.toggle('active', selected && arrow.dataset.direction === sortDirection);
        });
        button.addEventListener('click', () => {
            sortDirection = button.dataset.sort === sortBy && sortDirection === 'asc' ? 'desc' : 'asc';
            sortBy = button.dataset.sort;
            navigate(1);
        });
    });
    const modifiedSort = document.getElementById('modified-sort');
    if (modifiedSort) {
        const selected = sortBy === 'modified';
        modifiedSort.title = selected
            ? '點擊改為' + (sortDirection === 'desc' ? '最舊' : '最新') + '修改的商品優先'
            : '點擊依最新修改時間排序';
        modifiedSort.addEventListener('click', () => {
            sortDirection = sortBy === 'modified' && sortDirection === 'desc' ? 'asc' : 'desc';
            sortBy = 'modified';
            navigate(1);
        });
    }
    const totalProducts = Number(table.dataset.totalProducts) || 0;

    function savePreferences() {
        try {
            localStorage.setItem(storageKey, JSON.stringify({
                pageSize: Number(sizeSelect.value), statusFilter: statusFilter.value,
                stockFilter: stockFilter?.value || 'all',
                parentCategoryId: parentCategory?.value || '',
                categoryId: childCategory?.value || '', vendorId: vendor?.value || '',
                minComments: minComments?.value || '', maxComments: maxComments?.value || '', ratingFilter: ratingFilter?.value || ''
            }));
        } catch (_) { /* Pagination also works without storage. */ }
        const cookieOptions = ';path=/;max-age=31536000;SameSite=Lax';
        reviewControls.forEach(([control, , name]) => {
            document.cookie = name + '=' + encodeURIComponent(control?.value || '') + cookieOptions;
        });
        document.cookie = 'fruitudeProductPageSize=' + sizeSelect.value + cookieOptions;
        document.cookie = 'fruitudeProductStatus=' + statusFilter.value + cookieOptions;
        document.cookie = 'fruitudeProductStockFilter=' + (stockFilter?.value || 'all') + cookieOptions;
        document.cookie = 'fruitudeProductHideOffline=;path=/;max-age=0;SameSite=Lax';
        [[parentCategory, 'fruitudeProductParentCategory'], [childCategory, 'fruitudeProductCategory'],
            [vendor, 'fruitudeProductVendor']].forEach(([control, name]) => {
            document.cookie = name + '=' + encodeURIComponent(control?.value || '') + cookieOptions;
        });
    }

    function button(label, target, disabled = false, current = false) {
        const item = document.createElement('button');
        item.type = 'button';
        item.textContent = label;
        item.disabled = disabled;
        if (current) item.setAttribute('aria-current', 'page');
        item.addEventListener('click', () => {
            navigate(target);
        });
        pager.appendChild(item);
    }

    function validReviewRange() {
        if (!minComments || !maxComments) return true;
        [minComments, maxComments].forEach(control => {
            const normalized = control.value.replace(/[０-９]/g, digit => String.fromCharCode(digit.charCodeAt(0) - 0xFEE0));
            if (normalized !== control.value) {
                const start = control.selectionStart, end = control.selectionEnd;
                control.value = normalized;
                if (start !== null && end !== null) control.setSelectionRange(start, end);
            }
        });
        const error = document.getElementById('comment-range-error');
        const messageFor = value => value === '' ? '' : !/^[0-9]+$/.test(value)
            ? '請輸入數字' : Number(value) > 2147483647 ? '評論數不可超過 2147483647' : '';
        const minMessage = messageFor(minComments.value);
        let maxMessage = messageFor(maxComments.value);
        if (!minMessage && !maxMessage && minComments.value && maxComments.value
                && Number(minComments.value) > Number(maxComments.value)) maxMessage = '上限不可小於下限';
        [[minComments, minMessage], [maxComments, maxMessage]].forEach(([control, message]) => {
            control.setCustomValidity(message);
            control.setAttribute('aria-invalid', message ? 'true' : 'false');
        });
        if (error) {
            error.textContent = minMessage || maxMessage;
            error.hidden = !error.textContent;
        }
        return !minMessage && !maxMessage;
    }

    function navigate(target) {
        if (!validReviewRange()) return;
        clearTimeout(reviewTimer);
        savePreferences();
        const url = new URL(location.href);
        url.searchParams.set('page', String(target));
        url.searchParams.set('sortBy', sortBy);
        url.searchParams.set('sortDirection', sortDirection);
        url.searchParams.set('size', sizeSelect.value);
        url.searchParams.delete('hideOffline');
        url.searchParams.set('statusFilter', statusFilter.value);
        url.searchParams.set('stockFilter', stockFilter?.value || 'all');
        [[parentCategory, 'parentCategoryId'], [childCategory, 'categoryId'], [vendor, 'vendorId']].forEach(([control, name]) => {
            // Explicit empty values clear a remembered filter on the server as well.
            url.searchParams.set(name, control?.value || '');
        });
        reviewControls.forEach(([control, name]) => url.searchParams.set(name, control?.value || ''));
        document.body.classList.add('products-loading');
        location.assign(url.href);
    }

    function render() {
        rows.forEach(row => {
            row.querySelectorAll('img[data-src]').forEach(img => {
                img.src = img.dataset.src;
                img.removeAttribute('data-src');
                const imageIds = (img.dataset.imageIds || '').split(',').filter(id => /^[1-9]\d*$/.test(id));
                if (imageIds.length > 1) {
                    const base = new URL(img.dataset.imageBase, location.href);
                    slideshows.push({img, track: img.closest('.product-thumbnail-track'),
                        urls: imageIds.map(id => new URL(id + '/thumbnail', base).href), index: 0, busy: false});
                }
            });
        });
        empty.hidden = rows.length > 0;
        empty.cells[0].textContent = statusFilter.value !== 'all' || (stockFilter && stockFilter.value !== 'all') || parentCategory?.value || vendor?.value || minComments?.value || maxComments?.value || ratingFilter?.value ? '沒有符合條件的商品' : '目前沒有商品資料';
        summary.textContent = totalProducts ? '' : '共 0 個商品';
        pager.replaceChildren();
        if (!totalPages) return;
        button('上一頁', page - 1, page === 1);
        const numbers = new Set();
        if (totalPages <= 10) {
            for (let i = 1; i <= totalPages; i++) numbers.add(i);
        } else {
            numbers.add(1);
            const nearEnd = page >= totalPages - 4;
            const rangeStart = page < 5 ? 1 : nearEnd ? totalPages - 8 : Math.max(1, page - 3);
            const rangeEnd = page < 5 ? 9 : nearEnd ? totalPages : Math.min(totalPages, page + 4);
            for (let i = rangeStart; i <= rangeEnd; i++) numbers.add(i);
            numbers.add(totalPages);
        }
        let previous = 0;
        [...numbers].sort((a, b) => a - b).forEach(number => {
            if (previous && number - previous > 1) {
                const gap = document.createElement('span');
                gap.textContent = '…';
                pager.appendChild(gap);
            }
            button(String(number), number, false, number === page);
            previous = number;
        });
        button('下一頁', page + 1, page === totalPages);
    }

    [sizeSelect, statusFilter].forEach(control => control.addEventListener('change', () => {
        navigate(1);
    }));
    parentCategory?.addEventListener('change', () => {
        childCategory.value = '';
        navigate(1);
    });
    [childCategory, vendor].forEach(control => control?.addEventListener('change', () => navigate(1)));
    stockFilter?.addEventListener('change', () => navigate(1));
    ratingFilter?.addEventListener('change', () => navigate(1));
    let reviewTimer;
    function scheduleReviewSearch(control) {
        clearTimeout(reviewTimer);
        if (!validReviewRange()) return;
        reviewTimer = setTimeout(() => {
            try { sessionStorage.setItem(storageKey + ':focus', JSON.stringify({id: control.id, position: control.selectionStart})); } catch (_) {}
            navigate(1);
        }, 1500);
    }
    [minComments, maxComments].forEach(control => {
        control?.addEventListener('compositionstart', () => clearTimeout(reviewTimer));
        control?.addEventListener('input', event => {
            clearTimeout(reviewTimer);
            if (!event.isComposing) scheduleReviewSearch(control);
        });
        control?.addEventListener('compositionend', () => scheduleReviewSearch(control));
    });
    const categoryDropdown = document.getElementById('category-dropdown');
    if (categoryDropdown) {
        const trigger = document.getElementById('category-trigger');
        const menu = document.getElementById('category-menu');
        const caption = document.getElementById('category-caption');
        const search = document.getElementById('category-search');
        const noResults = document.getElementById('category-no-results');
        const normalize = text => text.trim().toLocaleLowerCase();
        const groups = Array.from(menu.querySelectorAll('.category-group'));
        const choices = Array.from(menu.querySelectorAll('[data-category-parent]'));
        const selected = choices.find(item => (item.dataset.categoryParent || '') === parentCategory.value
            && (item.dataset.categoryChild || '') === childCategory.value);
        caption.textContent = selected?.textContent.trim() || '全部分類';
        trigger.title = caption.textContent;
        if (selected) selected.setAttribute('aria-current', 'true');
        function closeChildren() {
            groups.forEach(group => {
                group.querySelector('.category-submenu').hidden = true;
                group.querySelector('.category-expand').setAttribute('aria-expanded', 'false');
            });
        }
        function closeMenu(returnFocus = false) {
            menu.hidden = true;
            trigger.setAttribute('aria-expanded', 'false');
            closeChildren();
            if (returnFocus) trigger.focus();
        }
        function positionMenu() {
            const rect = trigger.getBoundingClientRect();
            menu.style.left = Math.max(4, Math.min(rect.left, window.innerWidth - menu.offsetWidth - 4)) + 'px';
            menu.style.top = Math.max(4, rect.bottom + 4 + menu.offsetHeight > window.innerHeight
                ? rect.top - menu.offsetHeight - 4 : rect.bottom + 4) + 'px';
        }
        function openMenu() {
            menu.hidden = false;
            trigger.setAttribute('aria-expanded', 'true');
            positionMenu();
        }
        function openChildren(group) {
            closeChildren();
            const submenu = group.querySelector('.category-submenu');
            submenu.hidden = false;
            submenu.classList.remove('open-left');
            if (submenu.getBoundingClientRect().right > window.innerWidth - 4) submenu.classList.add('open-left');
            group.querySelector('.category-expand').setAttribute('aria-expanded', 'true');
        }
        function filterCategories() {
            const query = normalize(search.value);
            closeChildren();
            let firstChildMatch;
            groups.forEach(group => {
                const parentMatch = normalize(group.querySelector('.category-parent').textContent).includes(query);
                const children = Array.from(group.querySelectorAll('.category-child'));
                children.forEach(child => { child.hidden = !parentMatch && !normalize(child.textContent).includes(query); });
                const childMatch = children.some(child => !child.hidden);
                group.hidden = !parentMatch && !childMatch;
                if (query && !parentMatch && childMatch && !firstChildMatch) firstChildMatch = group;
            });
            menu.querySelector('.category-all').hidden = !!query;
            noResults.hidden = !query || groups.some(group => !group.hidden);
            if (firstChildMatch) openChildren(firstChildMatch);
            positionMenu();
        }
        search.addEventListener('input', filterCategories);
        trigger.addEventListener('click', () => {
            if (!menu.hidden) { closeMenu(); return; }
            search.value = ''; openMenu(); filterCategories(); search.focus();
        });
        trigger.addEventListener('keydown', event => {
            if (event.key === 'ArrowDown') {
                event.preventDefault(); openMenu();
                if (selected?.closest('.category-submenu')) openChildren(selected.closest('.category-group'));
                (selected || choices[0])?.focus();
            }
        });
        choices.forEach(item => item.addEventListener('click', () => {
            parentCategory.value = item.dataset.categoryParent || '';
            childCategory.value = item.dataset.categoryChild || '';
            closeMenu();
            navigate(1);
        }));
        groups.forEach(group => {
            const expand = group.querySelector('.category-expand');
            const submenu = group.querySelector('.category-submenu');
            if (!submenu.querySelector('[data-category-child]')) {
                const message = document.createElement('div');
                message.className = 'category-no-children'; message.textContent = '尚無小分類'; submenu.appendChild(message);
            }
            expand.addEventListener('click', () => openChildren(group));
            group.addEventListener('pointerenter', () => openChildren(group));
            group.addEventListener('pointerleave', () => {
                if (!group.contains(document.activeElement)) closeChildren();
            });
        });
        menu.addEventListener('keydown', event => {
            const group = event.target.closest('.category-group');
            if (event.key === 'Escape') { event.preventDefault(); closeMenu(true); }
            if (event.key === 'ArrowRight' && group) {
                event.preventDefault(); openChildren(group); group.querySelector('.category-child')?.focus();
            }
            if (event.key === 'ArrowLeft' && event.target.closest('.category-submenu')) {
                event.preventDefault(); closeChildren(); group.querySelector('.category-expand').focus();
            }
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                const scope = event.target.closest('.category-submenu') || menu;
                const items = Array.from(scope.querySelectorAll('button')).filter(item => !item.closest('[hidden]'));
                const index = items.indexOf(event.target);
                items[(index + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length]?.focus();
            }
        });
        document.addEventListener('click', event => { if (!categoryDropdown.contains(event.target)) closeMenu(); });
        document.addEventListener('focusin', event => { if (!categoryDropdown.contains(event.target)) closeMenu(); });
        window.addEventListener('resize', () => closeMenu());
        document.addEventListener('scroll', event => { if (!menu.contains(event.target)) closeMenu(); }, true);
    }
    const vendorDropdown = document.getElementById('vendor-dropdown');
    if (vendorDropdown && vendor) {
        const trigger = document.getElementById('vendor-trigger');
        const menu = document.getElementById('vendor-menu');
        const search = document.getElementById('vendor-search');
        const options = document.getElementById('vendor-options');
        const noResults = document.getElementById('vendor-no-results');
        const caption = document.getElementById('vendor-caption');
        caption.textContent = vendor.selectedOptions[0]?.textContent || '全部供應廠商';
        trigger.title = caption.textContent;
        const buttons = Array.from(vendor.options, option => {
            const item = document.createElement('button');
            item.type = 'button'; item.className = 'category-child';
            item.setAttribute('role', 'menuitem'); item.textContent = option.textContent;
            if (option.selected) item.setAttribute('aria-current', 'true');
            item.addEventListener('click', () => {
                vendor.value = option.value;
                close();
                vendor.dispatchEvent(new Event('change'));
            });
            options.appendChild(item);
            return item;
        });
        function close(returnFocus = false) {
            menu.hidden = true; trigger.setAttribute('aria-expanded', 'false');
            if (returnFocus) trigger.focus();
        }
        function position() {
            const rect = trigger.getBoundingClientRect();
            menu.style.left = Math.max(4, Math.min(rect.left, window.innerWidth - menu.offsetWidth - 4)) + 'px';
            menu.style.top = Math.max(4, rect.bottom + 4 + menu.offsetHeight > window.innerHeight
                ? rect.top - menu.offsetHeight - 4 : rect.bottom + 4) + 'px';
        }
        function filter() {
            const query = search.value.trim().toLocaleLowerCase();
            buttons.forEach(item => { item.hidden = !item.textContent.toLocaleLowerCase().includes(query); });
            noResults.hidden = buttons.some(item => !item.hidden);
            position();
        }
        function open() {
            search.value = ''; menu.hidden = false; trigger.setAttribute('aria-expanded', 'true');
            filter(); search.focus();
        }
        trigger.addEventListener('click', () => menu.hidden ? open() : close());
        trigger.addEventListener('keydown', event => {
            if (event.key === 'ArrowDown') { event.preventDefault(); open(); }
        });
        search.addEventListener('input', filter);
        menu.addEventListener('keydown', event => {
            if (event.key === 'Escape') { event.preventDefault(); close(true); }
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                const visible = buttons.filter(item => !item.hidden);
                const index = visible.indexOf(event.target);
                visible[(index + (event.key === 'ArrowDown' ? 1 : -1) + visible.length) % visible.length]?.focus();
            }
        });
        document.addEventListener('click', event => { if (!vendorDropdown.contains(event.target)) close(); });
        document.addEventListener('focusin', event => { if (!vendorDropdown.contains(event.target)) close(); });
        window.addEventListener('resize', () => close());
        document.addEventListener('scroll', event => { if (!menu.contains(event.target)) close(); }, true);
    }
    // A status update stays visible. The next page request applies the database filter again.
    render();
    if (slideshows.length) {
        const advance = () => {
            if (document.hidden) return;
            slideshows.forEach(slideshow => {
                if (!slideshow.img.complete || slideshow.busy || !slideshow.track) return;
                const rect = slideshow.img.getBoundingClientRect();
                // Avoid fetching carousel images for rows outside the visible viewport.
                let visibleTop = 0, visibleBottom = window.innerHeight;
                try {
                    if (window.frameElement) {
                        const frameRect = window.frameElement.getBoundingClientRect();
                        visibleTop = Math.max(0, -frameRect.top);
                        visibleBottom = Math.min(visibleBottom, window.parent.innerHeight - frameRect.top);
                    }
                } catch (_) { /* Standalone or cross-origin page: use this viewport. */ }
                if (rect.bottom <= visibleTop || rect.top >= visibleBottom) return;
                slideshow.busy = true;
                const nextIndex = (slideshow.index + 1) % slideshow.urls.length;
                const incoming = document.createElement('img');
                incoming.alt = slideshow.img.alt;
                incoming.setAttribute('aria-hidden', 'true');
                incoming.decoding = 'async';
                incoming.onload = () => {
                    const track = slideshow.track;
                    track.appendChild(incoming);
                    let finished = false;
                    const finish = () => {
                        if (finished) return;
                        finished = true;
                        clearTimeout(fallback);
                        track.removeEventListener('transitionend', onTransitionEnd);
                        slideshow.img.remove();
                        track.classList.remove('is-sliding');
                        incoming.removeAttribute('aria-hidden');
                        slideshow.img = incoming;
                        slideshow.index = nextIndex;
                        slideshow.busy = false;
                    };
                    const onTransitionEnd = event => {
                        if (event.target === track && event.propertyName === 'transform') finish();
                    };
                    track.addEventListener('transitionend', onTransitionEnd);
                    // Lay out both images before starting their shared leftward movement.
                    track.getBoundingClientRect();
                    track.classList.add('is-sliding');
                    const fallback = setTimeout(finish, 550);
                };
                incoming.onerror = () => { slideshow.busy = false; };
                // Keep the current image visible until the next image has loaded.
                incoming.src = slideshow.urls[nextIndex];
            });
        };
        let timer = setInterval(advance, 5000);
        window.addEventListener('pagehide', () => clearInterval(timer));
        window.addEventListener('pageshow', event => {
            if (event.persisted) timer = setInterval(advance, 5000);
        });
    }
    document.body.classList.remove('products-loading');
    document.documentElement.dataset.productListReady = 'true';
    document.dispatchEvent(new Event('product-list-ready'));
    try {
        const focus = JSON.parse(sessionStorage.getItem(storageKey + ':focus') || 'null');
        sessionStorage.removeItem(storageKey + ':focus');
        const control = focus && [minComments, maxComments].find(item => item?.id === focus.id);
        if (control) { control.focus(); control.setSelectionRange(focus.position, focus.position); }
    } catch (_) {}

})();
