import http from 'k6/http';
import { check, sleep } from 'k6';

// 1000 個虛擬使用者 (VU) 在 3 秒內陸續登入 test0001 ~ test1000，每人只登入一次。
// 執行：k6 run k6/login-test.js（密碼預設 12345678，可用 -e PASSWORD=... 覆蓋）
// 可選：-e BASE_URL=http://localhost:8080
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081/TKA102G2';
const PASSWORD = __ENV.PASSWORD || '12345678';

export const options = {
	scenarios: {
		simultaneous_login: {
			executor: 'per-vu-iterations',
			vus: 1000,
			iterations: 1,
			maxDuration: '2m',
		},
	},
	thresholds: {
		http_req_failed: ['rate<0.01'],
		http_req_duration: ['p(95)<5000'],
	},
};

export default function () {
	// 隨機延遲 0~3 秒，避免 1000 條新連線在同一瞬間湧入而被作業系統擋掉
	sleep(Math.random() * 3);

	// __VU 從 1 開始，對應 test0001 ~ test1000
	const account = 'test' + String(__VU).padStart(4, '0');

	// 先開啟登入頁 /front/about/login/，再送出與該頁表單相同的登入請求
	const page = http.get(`${BASE_URL}/front/about/login/`, { tags: { name: 'login_page' } });
	check(page, { 'login page 200': (r) => r.status === 200 });

	const res = http.post(
		`${BASE_URL}/api/members/login`,
		{ account: account, password: PASSWORD },
		{ tags: { name: 'member_login' } }
	);

	check(res, {
		'status is 200': (r) => r.status === 200,
		'returns member id': (r) => {
			try {
				return r.json('memberId') !== undefined;
			} catch (e) {
				return false;
			}
		},
	});
}
