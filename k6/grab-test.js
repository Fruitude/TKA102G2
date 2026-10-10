import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

// 登入 + 搶購物金合併版（login-test.js 的登入流程 + 搶購）：
// 1000 個虛擬使用者 (VU) 先登入 test0001 ~ test1000，登入成功後才在活動開始的同一瞬間透過 API 搶購物金，
// 結束後顯示「登入幾個、幾個搶到、幾個沒搶到」。
// 執行：k6 run k6/grab-test.js
//   -e PROMO_ID=3        指定搶購物金活動編號（不給就用 /api/promos/grab/state 回傳的第一場）
//   -e PASSWORD=12345678 登入密碼
//   -e BASE_URL=http://localhost:8081/TKA102G2
// 事前：活動要「啟用」且尚未結束；活動還沒開始時，所有 VU 會等到開始時間才一起送出搶購。
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081/TKA102G2';
const PASSWORD = __ENV.PASSWORD || '12345678';
const PROMO_ID = __ENV.PROMO_ID ? Number(__ENV.PROMO_ID) : null;

const loginOk = new Counter('login_ok');             // 登入成功
const grabWon = new Counter('grab_won');             // 搶到（WON）
const grabLost = new Counter('grab_lost');           // 名額已滿沒搶到（LOST）
const grabAlready = new Counter('grab_already');     // 這場已經參加過（ALREADY_WON / ALREADY_LOST）
const grabRejected = new Counter('grab_rejected');   // 活動未開始／已結束／不存在（409、404）
const grabError = new Counter('grab_error');         // 登入失敗或其他非預期錯誤

export const options = {
	scenarios: {
		wallet_grab: {
			executor: 'per-vu-iterations',
			vus: 1000,
			iterations: 1,
			maxDuration: '10m',
		},
	},
	thresholds: {
		'checks{check:login status is 200}': ['rate>0.99'],
		http_req_duration: ['p(95)<5000'],
	},
};

export default function () {
	// 隨機延遲 0~3 秒再登入，避免 1000 條新連線同一瞬間湧入
	sleep(Math.random() * 3);

	// __VU 從 1 開始，對應 test0001 ~ test1000
	const account = 'test' + String(__VU).padStart(4, '0');

	// 步驟 1：登入（同 login-test.js：先開登入頁，再送出與該頁表單相同的登入請求）
	const page = http.get(`${BASE_URL}/front/about/login/`, { tags: { name: 'login_page' } });
	check(page, { 'login page 200': (r) => r.status === 200 });

	const login = http.post(
		`${BASE_URL}/api/members/login`,
		{ account: account, password: PASSWORD },
		{ tags: { name: 'member_login' } }
	);
	const loggedIn = check(login, {
		'login status is 200': (r) => r.status === 200,
		'login returns member id': (r) => {
			try {
				return r.json('memberId') !== undefined;
			} catch (e) {
				return false;
			}
		},
	});
	if (!loggedIn) {
		grabError.add(1); // 登入失敗的人不會去搶
		return;
	}
	loginOk.add(1);

	// 步驟 2：登入後才搶購物金

	// 取活動與伺服器時間，算出距離開始還要等多久
	const stateRes = http.get(`${BASE_URL}/api/promos/grab/state`, { tags: { name: 'grab_state' } });
	let state;
	try {
		state = stateRes.json();
	} catch (e) {
		grabError.add(1);
		return;
	}
	const events = (state && state.events) || [];
	// 沒指定 PROMO_ID 時，選第一場「名額還沒滿」的活動（已經搶完的舊活動會被跳過）
	const event = PROMO_ID
		? events.find((e) => e.promoProjectId === PROMO_ID)
		: events.find((e) => e.granted < e.quota) || events[0];
	if (!event) {
		grabError.add(1);
		return;
	}
	const waitMs = event.startMillis - state.serverNow;
	if (waitMs > 0) {
		sleep(waitMs / 1000 + 0.05);
	}

	const res = http.post(`${BASE_URL}/api/promos/${event.promoProjectId}/grab`, null, {
		tags: { name: 'grab' },
	});

	if (res.status === 200) {
		let result = '';
		try {
			result = res.json('result');
		} catch (e) {
			// 保持空字串，當作錯誤
		}
		if (result === 'WON') grabWon.add(1);
		else if (result === 'LOST') grabLost.add(1);
		else if (result === 'ALREADY_WON' || result === 'ALREADY_LOST') grabAlready.add(1);
		else grabError.add(1);
	} else if (res.status === 409 || res.status === 404) {
		grabRejected.add(1);
	} else {
		grabError.add(1);
	}
}

function count(data, name) {
	const m = data.metrics[name];
	return m && m.values && m.values.count ? m.values.count : 0;
}

export function handleSummary(data) {
	const logins = count(data, 'login_ok');
	const won = count(data, 'grab_won');
	const lost = count(data, 'grab_lost');
	const already = count(data, 'grab_already');
	const rejected = count(data, 'grab_rejected');
	const error = count(data, 'grab_error');
	const grab = data.metrics['http_req_duration{name:grab}'];
	const p95 = grab && grab.values ? grab.values['p(95)'] : null;

	const lines = [
		'',
		'========== 登入 + 搶購物金結果 ==========',
		`登入成功：${logins} 個`,
		`搶到購物金：${won} 個`,
		`沒搶到（名額已滿）：${lost} 個`,
		`已參加過（重複搶）：${already} 個`,
		`活動未開始／已結束：${rejected} 個`,
		`登入或其他錯誤：${error} 個`,
		`搶購 API p95 回應時間：${p95 == null ? 'N/A' : p95.toFixed(0) + ' ms'}`,
		'=========================================',
		'',
	];
	return { stdout: lines.join('\n') };
}
