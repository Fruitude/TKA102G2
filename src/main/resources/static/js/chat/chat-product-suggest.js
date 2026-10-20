// 後台客服聊天室（templates/admin/chat/index.html）輸入框的商品建議
// 打「#關鍵字」時，列出名稱包含關鍵字的上架商品與規格（AdminChatController 的 products，不含價格），最多 8 筆
// 點選或按 Enter／Tab 後把「#關鍵字」換成商品名稱和所有規格；清單開著時 ↑↓ 移動、Esc 關閉，Enter 不會送出訊息
(function () {
	const input = document.getElementById('send-content');
	const sendForm = document.getElementById('send-form');
	const box = document.getElementById('product-suggest');
	// 沒有 CHAT_REPLY 權限時頁面沒有輸入框
	if (!input || !sendForm || !box) {
		return;
	}
	const productsApi = input.dataset.productsApi;
	const MAX_ITEMS = 8;
	// 商品清單暫存 1 分鐘，打字時不用每個字都呼叫後端
	const CACHE_MILLIS = 60 * 1000;

	let products = null;
	let loadedAt = 0;
	let loading = null;
	// 目前列出的商品與鍵盤選到的位置
	let matches = [];
	let activeIndex = 0;
	let lastKeyword = null;
	// 帶入商品名稱後會觸發 input 事件，這一次不要重新偵測
	let skipNextUpdate = false;

	function normalize(text) {
		return (text || '').normalize('NFKC').toLowerCase();
	}

	// 游標前最近的 #（全形＃也可以）到游標之間、中間沒有空白的文字是關鍵字；有選取範圍時不偵測
	function findTrigger() {
		if (input.selectionStart !== input.selectionEnd) {
			return null;
		}
		const end = input.selectionStart;
		const match = input.value.slice(0, end).match(/[#＃]([^\s#＃]*)$/);
		return match ? { start: match.index, end, keyword: match[1] } : null;
	}

	async function loadProducts() {
		if (products && Date.now() - loadedAt < CACHE_MILLIS) {
			return products;
		}
		if (!loading) {
			loading = fetch(productsApi).then(async response => {
				const body = await response.json().catch(() => null);
				if (!response.ok) {
					throw new Error(body && body.message ? body.message : '商品清單讀取失敗（' + response.status + '）');
				}
				products = body;
				loadedAt = Date.now();
				return body;
			}).finally(() => {
				loading = null;
			});
		}
		return loading;
	}

	async function update() {
		if (!findTrigger()) {
			close();
			return;
		}
		let list;
		try {
			list = await loadProducts();
		} catch (error) {
			showMessage(error.message);
			return;
		}
		// 抓清單期間可能又打了字或離開輸入框，用最新的狀態重新判斷
		const current = findTrigger();
		if (!current || document.activeElement !== input) {
			close();
			return;
		}
		render(list, current.keyword);
	}

	// 名稱開頭就是關鍵字的排前面，其次是名稱包含關鍵字的；全形半形、大小寫視為相同
	function render(list, keyword) {
		const word = normalize(keyword).trim();
		if (!word) {
			showMessage('輸入商品名稱關鍵字，例如 #蘋果');
			return;
		}
		const startsWith = [];
		const contains = [];
		list.forEach(product => {
			const name = normalize(product.name);
			if (name.startsWith(word)) {
				startsWith.push(product);
			} else if (name.includes(word)) {
				contains.push(product);
			}
		});
		matches = startsWith.concat(contains).slice(0, MAX_ITEMS);
		if (!matches.length) {
			showMessage('找不到上架中的商品「' + keyword + '」');
			return;
		}
		if (keyword !== lastKeyword) {
			activeIndex = 0;
			lastKeyword = keyword;
		}
		activeIndex = Math.min(activeIndex, matches.length - 1);
		box.replaceChildren(...matches.map(createItem));
		box.hidden = false;
	}

	// 一個商品一列：商品名稱，下一行列出規格名稱；所有規格都售完時名稱顯示灰色
	// 規格名稱直接用 API 給的：前台商品資料（FrontCatalogService）已在售完規格名稱後加「（售完）」，這裡不再加
	function createItem(product, index) {
		const item = document.createElement('button');
		item.type = 'button';
		item.className = 'product-suggest-item' + (index === activeIndex ? ' active' : '')
			+ (product.skus.every(sku => sku.soldOut) ? ' sold-out' : '');
		item.setAttribute('role', 'option');
		item.setAttribute('aria-selected', index === activeIndex);
		const name = document.createElement('span');
		name.className = 'product-suggest-name';
		name.textContent = product.name;
		const skus = document.createElement('span');
		skus.className = 'product-suggest-skus';
		skus.textContent = product.skus.map(sku => sku.name).join('、') || '沒有規格';
		item.append(name, skus);
		// 先擋掉 mousedown，點選時輸入框才不會失去焦點、游標位置也不會跑掉
		item.addEventListener('mousedown', event => event.preventDefault());
		item.addEventListener('click', () => choose(index));
		return item;
	}

	function showMessage(text) {
		matches = [];
		const message = document.createElement('div');
		message.className = 'product-suggest-empty';
		message.textContent = text;
		box.replaceChildren(message);
		box.hidden = false;
	}

	function close() {
		box.hidden = true;
		box.replaceChildren();
		matches = [];
		activeIndex = 0;
		lastKeyword = null;
	}

	function highlight() {
		Array.from(box.children).forEach((item, index) => {
			item.classList.toggle('active', index === activeIndex);
			item.setAttribute('aria-selected', index === activeIndex);
			if (index === activeIndex) {
				item.scrollIntoView({ block: 'nearest' });
			}
		});
	}

	// 把「#關鍵字」換成「商品名稱（規格1、規格2）」，沒有規格時只有商品名稱；游標停在帶入文字的後面
	// 規格名稱和清單一樣直接用 API 給的（售完的已帶「（售完）」）
	function choose(index) {
		const product = matches[index];
		const current = findTrigger();
		if (!product || !current) {
			close();
			return;
		}
		const skuText = product.skus.map(sku => sku.name).join('、');
		const text = skuText ? product.name + '（' + skuText + '）' : product.name;
		const value = input.value;
		input.value = value.slice(0, current.start) + text + value.slice(current.end);
		const caret = current.start + text.length;
		input.focus();
		input.setSelectionRange(caret, caret);
		close();
		// 讓 index.html 既有的 input 監聽（例如輸入框清空時取消罐頭訊息）照常運作
		skipNextUpdate = true;
		input.dispatchEvent(new Event('input', { bubbles: true }));
	}

	input.addEventListener('input', () => {
		if (skipNextUpdate) {
			skipNextUpdate = false;
			return;
		}
		update();
	});
	input.addEventListener('click', update);
	input.addEventListener('blur', close);
	// 左右鍵、Home、End 移動游標時重新偵測；清單開著時上下鍵由下面的 keydown 處理
	input.addEventListener('keyup', event => {
		if (['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)
			|| (box.hidden && ['ArrowUp', 'ArrowDown'].includes(event.key))) {
			update();
		}
	});

	// 在 send-form 的捕捉階段攔截按鍵，搶在 index.html 既有的「Enter 送出」之前處理
	// 中文輸入法選字中（isComposing）不攔截，避免按 Enter 選字被當成選商品
	sendForm.addEventListener('keydown', event => {
		if (event.target !== input || box.hidden || event.isComposing) {
			return;
		}
		if (event.key === 'Escape') {
			event.preventDefault();
			event.stopPropagation();
			close();
			return;
		}
		if (!matches.length) {
			return;
		}
		if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
			event.preventDefault();
			event.stopPropagation();
			activeIndex = (activeIndex + (event.key === 'ArrowDown' ? 1 : -1) + matches.length) % matches.length;
			highlight();
		} else if ((event.key === 'Enter' && !event.shiftKey) || event.key === 'Tab') {
			event.preventDefault();
			event.stopPropagation();
			choose(activeIndex);
		}
	}, true);
})();
