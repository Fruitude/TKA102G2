(function () {
    'use strict';

    const card = document.getElementById('top-selling-card');
    if (!card) return;

    const body = document.getElementById('top-selling-body');
    const periodText = document.getElementById('top-selling-period');
    const contextPath = window.location.pathname.split('/admin')[0];
    const numberFormat = new Intl.NumberFormat('zh-TW');
    let latestRequestId = 0;

    function showMessage(message, className) {
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 5;
        cell.className = 'text-center ' + className;
        cell.textContent = message;
        row.appendChild(cell);
        body.replaceChildren(row);
    }

    function createCell(text, className) {
        const cell = document.createElement('td');
        if (className) cell.className = className;
        cell.textContent = text;
        return cell;
    }

    function render(items, label) {
        if (!items.length) {
            showMessage(label + '尚無銷售資料', 'text-muted');
            return;
        }
        const rows = items.map(function (item) {
            const row = document.createElement('tr');
            row.appendChild(createCell(item.rank, 'text-center'));
            row.appendChild(createCell(item.productName || '--'));
            row.appendChild(createCell(item.skuName || '--'));
            row.appendChild(createCell(numberFormat.format(item.totalQuantity), 'text-right'));
            row.appendChild(createCell('$' + numberFormat.format(item.totalAmount), 'text-right'));
            return row;
        });
        body.replaceChildren(...rows);
    }

    // 快速切換期間時，只顯示最後一次請求的結果
    async function loadTopSelling(months, label) {
        const requestId = ++latestRequestId;
        periodText.textContent = label;
        showMessage('載入中...', 'text-muted');
        try {
            const url = new URL(card.dataset.url, window.location.origin);
            url.searchParams.set('months', months);
            url.searchParams.set('limit', 10);
            const response = await fetch(url, {
                credentials: 'same-origin',
                headers: { 'Accept': 'application/json' }
            });
            if (response.status === 401) {
                window.location.replace(contextPath + '/admin/employees/login');
                return;
            }
            if (!response.ok) throw new Error('HTTP ' + response.status);
            const items = await response.json();
            if (requestId === latestRequestId) render(items, label);
        } catch (error) {
            if (requestId === latestRequestId) showMessage('熱銷資料載入失敗，請稍後再試', 'text-danger');
        }
    }

    // 跟著儀表板的期間選單（sales-charts.html）變動；頁面上沒有選單時用預設的近三個月
    const periodSelect = document.getElementById('dashboard-period');

    function loadSelectedPeriod() {
        if (!periodSelect) {
            loadTopSelling(3, '近三個月');
            return;
        }
        loadTopSelling(Number(periodSelect.value) || 3, periodSelect.options[periodSelect.selectedIndex].text);
    }

    if (periodSelect) periodSelect.addEventListener('change', loadSelectedPeriod);
    loadSelectedPeriod();
})();
