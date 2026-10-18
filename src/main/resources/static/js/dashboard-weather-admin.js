(function () {
    'use strict';

    // 後台首頁標題：有天氣資料就顯示「臺北 晴 24.0°C 濕度 78%（05:00 觀測）」，否則維持原本的「首頁」
    const heading = document.getElementById('dashboard-heading');
    if (!heading) return;

    function render(weather) {
        const parts = [];
        if (weather.stationName) parts.push(weather.stationName);
        if (weather.weather) parts.push(weather.weather);
        parts.push(Number(weather.temperature).toFixed(1) + '°C');
        if (weather.humidity != null) parts.push('濕度 ' + weather.humidity + '%');

        const nodes = [document.createTextNode(parts.join(' '))];
        if (weather.observedTime) {
            const time = document.createElement('small');
            time.className = 'text-muted ml-2';
            time.textContent = '（' + weather.observedTime + ' 觀測）';
            nodes.push(time);
        }
        heading.replaceChildren(...nodes);
    }

    async function loadWeather() {
        try {
            const response = await fetch(heading.dataset.url, {
                credentials: 'same-origin',
                headers: { 'Accept': 'application/json' }
            });
            if (!response.ok) return;
            const weather = await response.json();
            if (weather.available && weather.temperature != null) render(weather);
        } catch (error) {
            // 取得失敗時保留「首頁」
        }
    }

    // 頁面開著時每 5 分鐘重抓一次（後端也快取 5 分鐘，不會多打氣象署）；之後抓不到時保留目前顯示的天氣
    const REFRESH_INTERVAL_MS = 5 * 60 * 1000;

    loadWeather();
    setInterval(loadWeather, REFRESH_INTERVAL_MS);

    // 瀏覽器會延後背景分頁的計時器，切回這個分頁時立刻重抓一次
    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'visible') loadWeather();
    });
})();
