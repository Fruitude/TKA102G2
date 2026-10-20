(function () {
    'use strict';

    const root = document.getElementById('dashboard-sales-charts');
    if (!root || typeof Chart === 'undefined') return;

    const periodSelect = document.getElementById('dashboard-period');
    const contextPath = window.location.pathname.split('/admin')[0];
    const numberFormat = new Intl.NumberFormat('zh-TW');
    const vendorColors = ['#4e73df', '#1cc88a', '#36b9cc'];
    const otherColor = '#b7b9cc';
    const charts = {};

    // 字型與顏色比照 SB Admin 2 demo（chart-*-demo.js）
    Chart.defaults.global.defaultFontFamily = 'Nunito, -apple-system, system-ui, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';
    Chart.defaults.global.defaultFontColor = '#858796';

    const tooltipStyle = {
        backgroundColor: 'rgb(255,255,255)',
        bodyFontColor: '#858796',
        titleFontColor: '#6e707e',
        titleMarginBottom: 10,
        borderColor: '#dddfeb',
        borderWidth: 1,
        xPadding: 15,
        yPadding: 15,
        displayColors: false,
        caretPadding: 10
    };

    const gridStyle = {
        color: 'rgb(234, 236, 244)',
        zeroLineColor: 'rgb(234, 236, 244)',
        drawBorder: false,
        borderDash: [2],
        zeroLineBorderDash: [2]
    };

    function formatMoney(value) {
        return '$' + numberFormat.format(value);
    }

    // Y 軸刻度用：一萬以上顯示成「$12萬」，提示框仍用 formatMoney 顯示完整金額
    function formatMoneyShort(value) {
        return value >= 10000 ? '$' + numberFormat.format(Math.round(value / 1000) / 10) + '萬' : formatMoney(value);
    }

    function shorten(text, maxLength) {
        return text.length > maxLength ? text.slice(0, maxLength) + '…' : text;
    }

    function showMessage(name, message, isError) {
        const element = root.querySelector('.dashboard-chart-message[data-chart="' + name + '"]');
        element.textContent = message || '';
        element.className = 'dashboard-chart-message small text-center mt-2 mb-0 '
            + (isError ? 'text-danger' : 'text-muted');
        element.hidden = !message;
    }

    async function fetchJson(baseUrl, params) {
        const url = new URL(baseUrl, window.location.origin);
        Object.keys(params).forEach(function (key) { url.searchParams.set(key, params[key]); });
        const response = await fetch(url, {
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json' }
        });
        if (response.status === 401) {
            window.location.replace(contextPath + '/admin/employees/login');
            throw new Error('unauthenticated');
        }
        if (!response.ok) throw new Error('HTTP ' + response.status);
        return response.json();
    }

    // 建立或更新圖表：第一次 new Chart，之後只換資料再 update()
    function renderChart(name, canvasId, config) {
        if (charts[name]) {
            charts[name].data = config.data;
            charts[name].update();
            return;
        }
        charts[name] = new Chart(document.getElementById(canvasId), config);
    }

    // 由期間第一個月開始逐月累加，回傳每個月的累計值
    function runningTotal(items, field) {
        let sum = 0;
        return items.map(function (item) {
            sum += item[field] || 0;
            return sum;
        });
    }

    function renderMonthly(items) {
        const hasSales = items.some(function (item) { return item.totalQuantity > 0; });
        showMessage('monthly', hasSales ? '' : '此期間尚無銷售資料');
        renderChart('monthly', 'dashboard-monthly-chart', {
            type: 'line',
            data: {
                labels: items.map(function (item) { return item.month; }),
                datasets: [{
                    label: '累計銷售金額',
                    yAxisID: 'amount',
                    lineTension: 0.3,
                    backgroundColor: 'rgba(78, 115, 223, 0.05)',
                    borderColor: 'rgba(78, 115, 223, 1)',
                    pointRadius: 3,
                    pointBackgroundColor: 'rgba(78, 115, 223, 1)',
                    pointBorderColor: 'rgba(78, 115, 223, 1)',
                    pointHoverRadius: 4,
                    data: runningTotal(items, 'totalAmount')
                }, {
                    label: '累計銷售數量',
                    yAxisID: 'quantity',
                    lineTension: 0.3,
                    fill: false,
                    borderColor: 'rgba(28, 200, 138, 1)',
                    pointRadius: 3,
                    pointBackgroundColor: 'rgba(28, 200, 138, 1)',
                    pointBorderColor: 'rgba(28, 200, 138, 1)',
                    pointHoverRadius: 4,
                    data: runningTotal(items, 'totalQuantity')
                }]
            },
            options: {
                maintainAspectRatio: false,
                // 卡片只有 col-xl-5 寬，左右留白和 Y 軸間距縮小，金額刻度改用「萬」縮短，讓折線區更寬
                layout: { padding: { left: 0, right: 0, top: 25, bottom: 0 } },
                scales: {
                    xAxes: [{ gridLines: { display: false, drawBorder: false } }],
                    yAxes: [{
                        id: 'amount',
                        position: 'left',
                        ticks: { beginAtZero: true, maxTicksLimit: 5, padding: 4, callback: formatMoneyShort },
                        gridLines: gridStyle
                    }, {
                        id: 'quantity',
                        position: 'right',
                        ticks: { beginAtZero: true, maxTicksLimit: 5, padding: 4, precision: 0 },
                        gridLines: { drawOnChartArea: false, drawBorder: false }
                    }]
                },
                // 圖例改成 12px 正方形；色塊預設用 backgroundColor（藍線近乎透明），改用線的顏色填滿
                legend: {
                    display: true,
                    position: 'bottom',
                    labels: {
                        boxWidth: 12,
                        generateLabels: function (chart) {
                            const labels = Chart.defaults.global.legend.labels.generateLabels(chart);
                            labels.forEach(function (label) { label.fillStyle = label.strokeStyle; });
                            return labels;
                        }
                    }
                },
                tooltips: Object.assign({}, tooltipStyle, {
                    intersect: false,
                    mode: 'index',
                    displayColors: true,
                    callbacks: {
                        label: function (tooltipItem, data) {
                            const label = data.datasets[tooltipItem.datasetIndex].label;
                            const value = tooltipItem.yLabel;
                            return label + '：' + (tooltipItem.datasetIndex === 0 ? formatMoney(value) : numberFormat.format(value));
                        }
                    }
                })
            }
        });
    }

    function renderSku(items) {
        showMessage('sku', items.length ? '' : '此期間尚無銷售資料');
        // X 軸標籤用陣列，Chart.js 2.x 會分兩行顯示：第一行商品名稱、第二行規格（無規格留空）
        const productLabels = items.map(function (item) {
            return [item.productName || '--', item.skuName || ''];
        });
        // 提示框標題保留完整「商品 / SKU」，不受 X 軸截斷影響
        const fullLabels = items.map(function (item) {
            return (item.productName || '--') + (item.skuName ? ' / ' + item.skuName : '');
        });
        renderChart('sku', 'dashboard-sku-chart', {
            type: 'bar',
            data: {
                labels: productLabels,
                datasets: [{
                    label: '銷售數量',
                    backgroundColor: '#4e73df',
                    hoverBackgroundColor: '#2e59d9',
                    borderColor: '#4e73df',
                    data: items.map(function (item) { return item.totalQuantity; })
                }]
            },
            options: {
                maintainAspectRatio: false,
                layout: { padding: { left: 10, right: 25, top: 25, bottom: 0 } },
                scales: {
                    xAxes: [{
                        gridLines: { display: false, drawBorder: false },
                        maxBarThickness: 80,
                        barPercentage: 0.7,
                        ticks: {
                            autoSkip: false,
                            minRotation: 0,
                            maxRotation: 0,
                            fontSize: 10,
                            callback: function (label) {
                                return label.map(function (line) { return shorten(line, 10); });
                            }
                        }
                    }],
                    yAxes: [{
                        ticks: { beginAtZero: true, maxTicksLimit: 5, padding: 10, precision: 0 },
                        gridLines: gridStyle
                    }]
                },
                legend: { display: false },
                tooltips: Object.assign({}, tooltipStyle, {
                    callbacks: {
                        title: function (tooltipItems) { return fullLabels[tooltipItems[0].index]; },
                        label: function (tooltipItem) {
                            const item = items[tooltipItem.index];
                            return '數量：' + numberFormat.format(item.totalQuantity) + '　金額：' + formatMoney(item.totalAmount);
                        }
                    }
                })
            }
        });
    }

    function renderVendor(items) {
        showMessage('vendor', items.length ? '' : '此期間尚無銷售資料');
        const total = items.reduce(function (sum, item) { return sum + item.totalAmount; }, 0);
        const colors = items.map(function (item, index) {
            return item.other ? otherColor : vendorColors[index % vendorColors.length];
        });
        renderChart('vendor', 'dashboard-vendor-chart', {
            type: 'pie',
            data: {
                labels: items.map(function (item) { return item.vendorName; }),
                datasets: [{
                    data: items.map(function (item) { return item.totalAmount; }),
                    backgroundColor: colors,
                    hoverBorderColor: 'rgba(234, 236, 244, 1)'
                }]
            },
            options: {
                maintainAspectRatio: false,
                // 圖例色塊預設 40px 寬會擠壓供應商名稱，寬度設成跟字級 12px 一樣變成正方形
                legend: { display: true, position: 'bottom', labels: { boxWidth: 12 } },
                tooltips: Object.assign({}, tooltipStyle, {
                    callbacks: {
                        label: function (tooltipItem, data) {
                            const value = data.datasets[0].data[tooltipItem.index];
                            const percent = total > 0 ? Math.round(value * 1000 / total) / 10 : 0;
                            return data.labels[tooltipItem.index] + '：' + formatMoney(value) + '（' + percent + '%）';
                        }
                    }
                })
            }
        });
    }

    // 三支 API 各自處理錯誤，其中一張失敗不影響其他圖表
    // 快速切換期間時，只畫最後一次請求的結果，避免舊回應覆蓋新資料
    async function loadPart(requestId, name, loader, render) {
        try {
            const items = await loader();
            if (requestId === latestRequestId) render(items);
        } catch (error) {
            if (requestId === latestRequestId && error.message !== 'unauthenticated') {
                showMessage(name, '資料載入失敗，請稍後再試', true);
            }
        }
    }

    let latestRequestId = 0;

    function loadDashboard() {
        const requestId = ++latestRequestId;
        const months = Number(periodSelect.value) || 3;

        return Promise.all([
            loadPart(requestId, 'monthly', function () { return fetchJson(root.dataset.monthlyUrl, { months: months }); }, renderMonthly),
            loadPart(requestId, 'sku', function () { return fetchJson(root.dataset.skuUrl, { months: months, limit: 3 }); }, renderSku),
            loadPart(requestId, 'vendor', function () { return fetchJson(root.dataset.vendorUrl, { months: months, limit: 3 }); }, renderVendor)
        ]);
    }

    periodSelect.addEventListener('change', loadDashboard);
    loadDashboard();
})();
