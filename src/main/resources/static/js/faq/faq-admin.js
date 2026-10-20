// 後台 FAQ 管理頁（templates/admin/chat/faq.html）用的 script
// 資料來自 AdminFaqController 的 /api/admin/chat/faq/...；新增、修改失敗時錯誤顯示在彈出視窗裡，視窗不關閉
// 左邊類別樹：像資料夾一樣一層一層展開，同一層依編號排序；點類別名稱選取，右邊列出它和所有子孫類別的 FAQ
// 右邊 FAQ 列表：用問題或 keyword 搜尋（不搜答案）；列表全是選取類別自己的 FAQ 且搜尋框空白時才能拖拉排序（SortableJS）
(function () {
	const page = document.getElementById('faq-page');
	const basePath = page.dataset.basePath;
	// 即時回覆的題目（FaqLiveAnswer.LIVE_QUESTIONS，AdminFaqController 用 | 串接）：不能刪除、不能改問題文字
	const liveQuestions = new Set((page.dataset.liveQuestions || '').split('|').filter(question => question));
	const pageError = document.getElementById('faq-page-error');
	const categoryTree = document.getElementById('category-tree');
	const faqBody = document.getElementById('faq-body');
	const faqTableArea = document.getElementById('faq-table-area');
	const faqSearch = document.getElementById('faq-search');
	const dragHint = document.getElementById('faq-drag-hint');
	const categoryColumn = document.getElementById('faq-category-column');
	const currentPath = document.getElementById('faq-current-path');
	const currentStatus = document.getElementById('faq-current-status');
	const currentDesc = document.getElementById('faq-current-desc');
	const toggleCategoryButton = document.getElementById('toggle-category-button');
	const deleteCategoryButton = document.getElementById('delete-category-button');
	const editCategoryButton = document.getElementById('edit-category-button');
	const addChildCategoryButton = document.getElementById('add-child-category-button');

	const categoryModal = $('#category-modal');
	const categoryForm = document.getElementById('category-form');
	const categoryError = document.getElementById('category-error');
	const categoryNameInput = document.getElementById('category-name');
	const categoryParentSelect = document.getElementById('category-parent');
	const categoryDescInput = document.getElementById('category-desc');
	const categoryStatusHint = document.getElementById('category-status-hint');

	const faqModal = $('#faq-modal');
	const faqForm = document.getElementById('faq-form');
	const faqError = document.getElementById('faq-error');
	const faqCategorySelect = document.getElementById('faq-category');
	const faqQuestionInput = document.getElementById('faq-question');
	const faqAnswerInput = document.getElementById('faq-answer');
	const faqKeywordInput = document.getElementById('faq-keyword');
	const faqQuestionLocked = document.getElementById('faq-question-locked');

	const faqDeleteModal = $('#faq-delete-modal');
	const faqDeleteError = document.getElementById('faq-delete-error');
	const faqDeleteQuestion = document.getElementById('faq-delete-question');
	const faqDeleteCategory = document.getElementById('faq-delete-category');
	const faqDeleteConfirm = document.getElementById('faq-delete-confirm');
	// 刪除確認視窗目前要刪除的 FAQ 編號
	let deletingFaqId = null;

	let categories = [];
	let faqs = [];
	// 目前選取的類別編號；null 代表「全部類別」
	let selectedCategoryId = null;
	// 「全部類別」有沒有展開；一開始展開，看得到最上層類別
	let allExpanded = true;
	// 目前展開的類別編號；一開始全部收合，只顯示最上層。收合「全部類別」時保留，重新展開後維持原樣
	const expandedIds = new Set();
	// 彈出視窗目前修改的編號；新增時是 null
	let editingCategoryId = null;
	let editingFaqId = null;

	// 拖拉排序：只能拖左邊的圖示；放開後由 saveOrder 送出新順序
	const sortable = Sortable.create(faqBody, {
		handle: '.faq-drag-handle',
		animation: 150,
		ghostClass: 'faq-drag-ghost',
		onEnd: saveOrder
	});

	async function callApi(path, options) {
		const response = await fetch(basePath + path, options);
		const body = await response.json().catch(() => null);
		if (!response.ok) {
			throw new Error(body && body.message ? body.message : '操作失敗（' + response.status + '）');
		}
		return body;
	}

	function postJson(path, data) {
		return callApi(path, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify(data)
		});
	}

	// message 是空字串時隱藏錯誤框
	function showError(box, message) {
		box.textContent = message;
		box.classList.toggle('d-none', !message);
	}

	function formatTime(value) {
		return value ? value.replace('T', ' ').substring(0, 16) : '';
	}

	function findCategory(faqCategoryId) {
		return categories.find(category => category.faqCategoryId === faqCategoryId);
	}

	// 從自己往上到最上層的類別（自己在第一個）；visited 防止資料有迴圈時無限循環
	function ancestorsOf(category) {
		const result = [];
		const visited = new Set();
		let current = category;
		while (current && !visited.has(current.faqCategoryId)) {
			visited.add(current.faqCategoryId);
			result.push(current);
			current = findCategory(current.parentCategoryId);
		}
		return result;
	}

	// 例如「購物流程 › 付款」
	function categoryPath(category) {
		return ancestorsOf(category).reverse().map(item => item.categoryName).join(' › ');
	}

	// 和 CannedReplyService.faqReply 相同：自己和所有上層都是顯示（status=1）才會自動回覆
	function isEffectivelyShown(category) {
		return ancestorsOf(category).every(item => item.status === 1);
	}

	// 某個類別底下的子類別；parentCategoryId 是 null 時回傳最上層（上層不存在的也當作最上層，才不會看不到）
	function childrenOf(parentCategoryId) {
		return categories.filter(category => parentCategoryId === null
			? category.parentCategoryId == null || !findCategory(category.parentCategoryId)
			: category.parentCategoryId === parentCategoryId);
	}

	// 把這個類別和它所有上層（包含「全部類別」）都展開，讓它底下的項目在樹上看得到
	function expandFrom(faqCategoryId) {
		allExpanded = true;
		ancestorsOf(findCategory(faqCategoryId)).forEach(item => expandedIds.add(item.faqCategoryId));
	}

	function textCell(text) {
		const tableCell = document.createElement('td');
		tableCell.textContent = text == null ? '' : text;
		return tableCell;
	}

	function createButton(text, className, onClick) {
		const element = document.createElement('button');
		element.type = 'button';
		element.className = 'btn btn-sm ' + className;
		element.textContent = text;
		element.addEventListener('click', onClick);
		return element;
	}

	function createOption(value, text) {
		const element = document.createElement('option');
		element.value = value;
		element.textContent = text;
		return element;
	}

	function createIcon(className) {
		const icon = document.createElement('i');
		icon.className = 'fas fa-fw ' + className;
		icon.setAttribute('aria-hidden', 'true');
		return icon;
	}

	function emptyRow(body, columns, text) {
		const row = document.createElement('tr');
		const tableCell = textCell(text);
		tableCell.colSpan = columns;
		tableCell.className = 'text-muted';
		row.append(tableCell);
		body.append(row);
	}

	// 重新載入類別與 FAQ；失敗時顯示錯誤，畫面保留原本的資料
	// 選取的類別已經不存在時改回「全部類別」
	async function loadAll() {
		try {
			[categories, faqs] = await Promise.all([
				callApi('api/admin/chat/faq/categories'),
				callApi('api/admin/chat/faq/items')
			]);
		} catch (error) {
			showError(pageError, error.message);
			return;
		}
		if (selectedCategoryId !== null && !findCategory(selectedCategoryId)) {
			selectedCategoryId = null;
		}
		renderAll();
	}

	function renderAll() {
		renderTree();
		renderCategoryHeader();
		renderFaqs();
	}

	function selectCategory(faqCategoryId) {
		selectedCategoryId = faqCategoryId;
		renderAll();
	}

	function toggleExpanded(faqCategoryId) {
		if (!expandedIds.delete(faqCategoryId)) {
			expandedIds.add(faqCategoryId);
		}
		renderTree();
	}

	// 類別樹：最上面是「全部類別」，最上層類別放在它底下；展開的類別才畫出它的子類別
	function renderTree() {
		const rootList = document.createElement('ul');
		rootList.setAttribute('role', 'group');
		rootList.append(createAllItem());
		categoryTree.replaceChildren(rootList);
		if (!categories.length) {
			const empty = document.createElement('div');
			empty.className = 'text-muted small mt-2 px-2';
			empty.textContent = '尚無類別，請先新增父類別';
			categoryTree.append(empty);
		}
	}

	// 「全部類別」：有類別時才有展開箭頭，點箭頭展開／收合，點名稱只選取
	function createAllItem() {
		const topCategories = childrenOf(null);
		const expanded = topCategories.length > 0 && allExpanded;
		const item = document.createElement('li');
		item.setAttribute('role', 'treeitem');
		item.setAttribute('aria-selected', selectedCategoryId === null);
		const node = document.createElement('div');
		node.className = 'faq-tree-node' + (selectedCategoryId === null ? ' active' : '');

		let toggle;
		if (topCategories.length) {
			toggle = document.createElement('button');
			toggle.type = 'button';
			toggle.setAttribute('aria-label', (expanded ? '收合' : '展開') + '全部類別');
			toggle.append(createIcon(expanded ? 'fa-caret-down' : 'fa-caret-right'));
			toggle.addEventListener('click', event => {
				event.stopPropagation();
				allExpanded = !allExpanded;
				renderTree();
			});
			item.setAttribute('aria-expanded', expanded);
		} else {
			toggle = document.createElement('span');
		}
		toggle.classList.add('faq-tree-toggle');

		const name = document.createElement('span');
		name.className = 'faq-tree-name';
		name.textContent = '全部類別';
		node.append(toggle, createIcon((expanded ? 'fa-folder-open' : 'fa-folder') + ' faq-tree-folder'), name);
		node.addEventListener('click', () => selectCategory(null));
		item.append(node);

		if (expanded) {
			const childList = document.createElement('ul');
			childList.setAttribute('role', 'group');
			topCategories.forEach(category => childList.append(createTreeItem(category, new Set())));
			item.append(childList);
		}
		return item;
	}

	// path 是從最上層到這裡已經畫過的類別，用來跳過資料有迴圈時重複出現的類別
	function createTreeItem(category, path) {
		const itemPath = new Set(path).add(category.faqCategoryId);
		const children = childrenOf(category.faqCategoryId).filter(child => !itemPath.has(child.faqCategoryId));
		const expanded = children.length > 0 && expandedIds.has(category.faqCategoryId);
		const selected = selectedCategoryId === category.faqCategoryId;

		const item = document.createElement('li');
		item.setAttribute('role', 'treeitem');
		item.setAttribute('aria-selected', selected);
		const node = document.createElement('div');
		node.className = 'faq-tree-node' + (selected ? ' active' : '') + (isEffectivelyShown(category) ? '' : ' inactive');

		// 有子類別的才有展開箭頭，沒有的放一樣寬的空白讓名稱對齊
		let toggle;
		if (children.length) {
			toggle = document.createElement('button');
			toggle.type = 'button';
			toggle.setAttribute('aria-label', (expanded ? '收合' : '展開') + category.categoryName);
			toggle.append(createIcon(expanded ? 'fa-caret-down' : 'fa-caret-right'));
			toggle.addEventListener('click', event => {
				event.stopPropagation();
				toggleExpanded(category.faqCategoryId);
			});
			item.setAttribute('aria-expanded', expanded);
		} else {
			toggle = document.createElement('span');
		}
		toggle.classList.add('faq-tree-toggle');

		const name = document.createElement('span');
		name.className = 'faq-tree-name';
		name.textContent = category.categoryName;
		node.append(toggle, createIcon((expanded ? 'fa-folder-open' : 'fa-folder') + ' faq-tree-folder'), name);
		// 自己停用標「（停用）」；自己是顯示但上層停用標「（上層停用）」，兩種都不會自動回覆
		if (!isEffectivelyShown(category)) {
			const note = document.createElement('small');
			note.className = 'faq-tree-name';
			note.textContent = category.status === 1 ? '（上層停用）' : '（停用）';
			node.append(note);
		}
		node.addEventListener('click', () => selectCategory(category.faqCategoryId));
		item.append(node);

		if (expanded) {
			const childList = document.createElement('ul');
			childList.setAttribute('role', 'group');
			children.forEach(child => childList.append(createTreeItem(child, itemPath)));
			item.append(childList);
		}
		return item;
	}

	// 右邊標題列：目前類別的路徑、狀態、描述；左邊標題列：類別操作按鈕。全部類別時隱藏狀態，操作按鈕變灰不能按
	function renderCategoryHeader() {
		const category = findCategory(selectedCategoryId);
		currentPath.textContent = category ? categoryPath(category) : '全部類別';
		currentStatus.hidden = !category;
		currentDesc.hidden = !(category && category.categoryDesc);
		currentDesc.textContent = category && category.categoryDesc ? category.categoryDesc : '';
		// 選「全部類別」時四個類別操作按鈕仍然顯示，只是全部變灰不能按
		const noCategoryReason = category ? '' : '請先在下方選取類別';
		[editCategoryButton, addChildCategoryButton, toggleCategoryButton, deleteCategoryButton].forEach(button => {
			button.disabled = !category;
			button.title = noCategoryReason;
		});
		if (!category) {
			toggleCategoryButton.textContent = '停用';
			toggleCategoryButton.className = 'btn btn-sm btn-outline-secondary';
			return;
		}
		const shown = category.status === 1;
		if (!shown) {
			currentStatus.className = 'badge badge-secondary ml-1';
			currentStatus.textContent = '停用';
		} else if (!isEffectivelyShown(category)) {
			currentStatus.className = 'badge badge-warning ml-1';
			currentStatus.textContent = '上層停用';
		} else {
			currentStatus.className = 'badge badge-success ml-1';
			currentStatus.textContent = '顯示';
		}
		toggleCategoryButton.textContent = shown ? '停用' : '啟用';
		toggleCategoryButton.className = 'btn btn-sm ' + (shown ? 'btn-outline-secondary' : 'btn-outline-success');

		// 只能刪除空的類別：有子類別或 FAQ 時按鈕變灰不能按，滑鼠移上去顯示原因
		const deleteBlockedReason = categories.some(item => item.parentCategoryId === category.faqCategoryId)
			? '底下還有子類別，請先刪除或移走子類別'
			: faqs.some(faq => faq.faqCategoryId === category.faqCategoryId)
				? '這個類別還有 FAQ，請先把 FAQ 移到其他類別'
				: '';
		deleteCategoryButton.disabled = !!deleteBlockedReason;
		deleteCategoryButton.title = deleteBlockedReason;
	}

	// 選了類別時列出自己和所有子孫類別的 FAQ；順序照 API（類別編號、顯示順序），同一類別的排在一起
	function renderFaqs() {
		const showAll = selectedCategoryId === null;
		const searchText = faqSearch.value.trim().toLowerCase();
		const categoryIds = showAll ? null : selfAndDescendants(selectedCategoryId);
		const categoryFaqs = showAll ? faqs : faqs.filter(faq => categoryIds.has(faq.faqCategoryId));
		// 列表裡有子類別的 FAQ（混著不同類別）時，要顯示類別欄，也不能拖拉
		const mixed = !showAll && categoryFaqs.some(faq => faq.faqCategoryId !== selectedCategoryId);
		const rows = categoryFaqs.filter(faq => !searchText || faq.question.toLowerCase().includes(searchText)
			|| (faq.keyword || '').toLowerCase().includes(searchText));
		const showCategory = showAll || mixed;
		const columns = showCategory ? 6 : 5;

		categoryColumn.hidden = !showCategory;
		faqBody.replaceChildren();
		if (!categories.length) {
			emptyRow(faqBody, columns, '請先新增類別');
		} else if (!rows.length) {
			emptyRow(faqBody, columns, searchText ? '沒有符合的 FAQ' : showAll ? '還沒有 FAQ' : '這個類別還沒有 FAQ');
		}
		rows.forEach(faq => {
			const row = document.createElement('tr');
			row.dataset.faqId = faq.faqId;
			const handleCell = document.createElement('td');
			handleCell.className = 'faq-drag-handle text-muted';
			handleCell.append(createIcon('fa-grip-vertical'));
			// 最後修改時間：faq-time 讓 faq.html 單獨調整這一欄的字級
			const timeCell = textCell(formatTime(faq.updatedAt || faq.createdAt));
			timeCell.className = 'faq-time';
			// 操作欄：faq-action 讓 faq.html 調整按鈕字級並垂直置中
			const actionCell = document.createElement('td');
			actionCell.className = 'faq-action';
			// 刪除鈕：即時回覆的題目變灰不能按，滑鼠移上去顯示原因
			const deleteButton = createButton('刪除', 'btn-danger',() => openFaqDeleteModal(faq));
			if (liveQuestions.has(faq.question)) {
				deleteButton.disabled = true;
				deleteButton.title = '這題會自動回覆即時活動資料，不能刪除';
			}
			actionCell.append(createButton('修改', 'btn-primary', () => openFaqModal(faq)), deleteButton);
			row.append(handleCell);
			if (showCategory) {
				const category = findCategory(faq.faqCategoryId);
				row.append(textCell(category ? categoryPath(category) : ''));
			}
			row.append(textCell(faq.question), textCell(faq.keyword), timeCell, actionCell);
			faqBody.append(row);
		});

		// 只有選了單一類別、列表全是這個類別自己的 FAQ、而且沒有搜尋時才能拖拉：
		// sort_order 是同一類別內的順序，混著不同類別或搜尋時只列出部分 FAQ，拖拉後的順序都沒有意義
		const canDrag = !showAll && !mixed && !searchText && rows.length > 1;
		sortable.option('disabled', !canDrag);
		faqBody.classList.toggle('faq-sortable', canDrag);
		dragHint.textContent = showAll
			? '在左邊選擇單一類別後才能拖拉排序'
			: mixed
				? '列表包含子類別的 FAQ，無法拖拉排序'
				: searchText
					? '搜尋中無法拖拉排序，清除搜尋文字後即可拖拉'
					: '拖拉左側圖示調整順序；兩題的 keyword 符合程度相同時，排在前面的優先回覆';
	}

	// 放開後把這個類別的新順序整批送出；之後重新載入，失敗時畫面回到資料庫裡的順序
	async function saveOrder(event) {
		if (event.oldIndex === event.newIndex) {
			return;
		}
		const faqIds = Array.from(faqBody.querySelectorAll('tr[data-faq-id]'), row => Number(row.dataset.faqId));
		try {
			await postJson('api/admin/chat/faq/categories/' + selectedCategoryId + '/order', faqIds);
			showError(pageError, '');
		} catch (error) {
			showError(pageError, error.message);
		}
		await loadAll();
	}

	// 自己和自己底下所有子孫類別的編號；上層類別選單要排除，避免設成迴圈
	function selfAndDescendants(faqCategoryId) {
		const result = new Set([faqCategoryId]);
		let added = true;
		while (added) {
			added = false;
			categories.forEach(category => {
				if (result.has(category.parentCategoryId) && !result.has(category.faqCategoryId)) {
					result.add(category.faqCategoryId);
					added = true;
				}
			});
		}
		return result;
	}

	// category 是要修改的類別，新增時是 null；defaultParentId 是新增子類別時預先帶入的上層類別
	function openCategoryModal(category, defaultParentId) {
		editingCategoryId = category ? category.faqCategoryId : null;
		document.getElementById('category-modal-title').textContent = category ? '修改類別'
			: defaultParentId != null ? '新增子類別' : '新增父類別';
		const excluded = category ? selfAndDescendants(category.faqCategoryId) : new Set();
		categoryParentSelect.replaceChildren(createOption('', '（無，最上層類別）'),
			...categories.filter(item => !excluded.has(item.faqCategoryId))
				.map(item => createOption(item.faqCategoryId, categoryPath(item) + (item.status === 1 ? '' : '（停用）'))));
		categoryNameInput.value = category ? category.categoryName : '';
		const parentCategoryId = category ? category.parentCategoryId : defaultParentId;
		categoryParentSelect.value = parentCategoryId != null ? parentCategoryId : '';
		categoryDescInput.value = category ? category.categoryDesc || '' : '';
		categoryStatusHint.hidden = !!category;
		showError(categoryError, '');
		categoryModal.modal('show');
	}

	categoryForm.addEventListener('submit', async event => {
		event.preventDefault();
		const submitButton = categoryForm.querySelector('[type="submit"]');
		const data = {
			categoryName: categoryNameInput.value,
			categoryDesc: categoryDescInput.value,
			parentCategoryId: categoryParentSelect.value ? Number(categoryParentSelect.value) : null
		};
		submitButton.disabled = true;
		try {
			await postJson('api/admin/chat/faq/categories' + (editingCategoryId ? '/' + editingCategoryId : ''), data);
			categoryModal.modal('hide');
			showError(pageError, '');
			// 展開上層，剛存的類別才看得到；最上層類別只要展開「全部類別」
			if (data.parentCategoryId !== null) {
				expandFrom(data.parentCategoryId);
			} else {
				allExpanded = true;
			}
			await loadAll();
		} catch (error) {
			showError(categoryError, error.message);
		} finally {
			submitButton.disabled = false;
		}
	});

	// 停用有子類別的類別時先確認，因為子類別的 FAQ 也會一起停止自動回覆
	async function toggleCategory(category) {
		const enabled = category.status !== 1;
		const hasChildren = categories.some(item => item.parentCategoryId === category.faqCategoryId);
		if (!enabled && hasChildren
			&& !confirm('停用「' + category.categoryName + '」後，底下子類別的 FAQ 也不會自動回覆，確定要停用嗎？')) {
			return;
		}
		try {
			await callApi('api/admin/chat/faq/categories/' + category.faqCategoryId + '/status?enabled=' + enabled,
				{ method: 'POST' });
			showError(pageError, '');
		} catch (error) {
			showError(pageError, error.message);
		}
		await loadAll();
	}

	// 刪除類別：先確認；成功後改回「全部類別」，失敗時（例如別人剛在底下新增了 FAQ）顯示後端回傳的原因
	async function deleteCategory(category) {
		if (!confirm('確定要刪除「' + category.categoryName + '」嗎？刪除後無法復原')) {
			return;
		}
		try {
			await callApi('api/admin/chat/faq/categories/' + category.faqCategoryId + '/delete', { method: 'POST' });
			showError(pageError, '');
			selectedCategoryId = null;
			expandedIds.delete(category.faqCategoryId);
		} catch (error) {
			showError(pageError, error.message);
		}
		await loadAll();
	}

	function openFaqModal(faq) {
		if (!categories.length) {
			showError(pageError, '請先新增類別');
			return;
		}
		editingFaqId = faq ? faq.faqId : null;
		document.getElementById('faq-modal-title').textContent = faq ? '修改 FAQ' : '新增 FAQ';
		faqCategorySelect.replaceChildren(...categories.map(category =>
			createOption(category.faqCategoryId, categoryPath(category) + (category.status === 1 ? '' : '（停用）'))));
		faqCategorySelect.value = faq ? faq.faqCategoryId
			: selectedCategoryId !== null ? selectedCategoryId : categories[0].faqCategoryId;
		faqQuestionInput.value = faq ? faq.question : '';
		// 修改即時回覆的題目時問題欄位唯讀；keyword、答案、類別仍然可以改
		const questionLocked = !!faq && liveQuestions.has(faq.question);
		faqQuestionInput.readOnly = questionLocked;
		faqQuestionLocked.hidden = !questionLocked;
		faqAnswerInput.value = faq ? faq.answer : '';
		faqKeywordInput.value = faq ? faq.keyword || '' : '';
		showError(faqError, '');
		faqModal.modal('show');
	}

	function openFaqDeleteModal(faq) {
		deletingFaqId = faq.faqId;
		const category = findCategory(faq.faqCategoryId);
		faqDeleteQuestion.textContent = faq.question;
		faqDeleteCategory.textContent = category ? categoryPath(category) : '';
		showError(faqDeleteError, '');
		faqDeleteModal.modal('show');
	}

	// 確認刪除：成功時關閉視窗並重新載入；失敗時錯誤顯示在視窗裡，視窗不關閉
	faqDeleteConfirm.addEventListener('click', async () => {
		faqDeleteConfirm.disabled = true;
		try {
			await callApi('api/admin/chat/faq/items/' + deletingFaqId + '/delete', { method: 'POST' });
			faqDeleteModal.modal('hide');
			showError(pageError, '');
			await loadAll();
		} catch (error) {
			showError(faqDeleteError, error.message);
		} finally {
			faqDeleteConfirm.disabled = false;
		}
	});

	faqForm.addEventListener('submit', async event => {
		event.preventDefault();
		const submitButton = faqForm.querySelector('[type="submit"]');
		const data = {
			faqCategoryId: Number(faqCategorySelect.value),
			question: faqQuestionInput.value,
			answer: faqAnswerInput.value,
			keyword: faqKeywordInput.value
		};
		submitButton.disabled = true;
		try {
			await postJson('api/admin/chat/faq/items' + (editingFaqId ? '/' + editingFaqId : ''), data);
			faqModal.modal('hide');
			showError(pageError, '');
			// 選單一類別時切到剛存的類別並展開它的上層，換了類別的 FAQ 才看得到；選全部類別時維持不變
			if (selectedCategoryId !== null) {
				selectedCategoryId = data.faqCategoryId;
				const category = findCategory(data.faqCategoryId);
				if (category && category.parentCategoryId != null) {
					expandFrom(category.parentCategoryId);
				} else {
					allExpanded = true;
				}
			}
			await loadAll();
		} catch (error) {
			showError(faqError, error.message);
		} finally {
			submitButton.disabled = false;
		}
	});

	document.getElementById('add-category-button').addEventListener('click', () => openCategoryModal(null, null));
	addChildCategoryButton.addEventListener('click', () => openCategoryModal(null, selectedCategoryId));
	editCategoryButton.addEventListener('click', () => openCategoryModal(findCategory(selectedCategoryId), null));
	toggleCategoryButton.addEventListener('click', () => toggleCategory(findCategory(selectedCategoryId)));
	deleteCategoryButton.addEventListener('click', () => deleteCategory(findCategory(selectedCategoryId)));
	document.getElementById('add-faq-button').addEventListener('click', () => openFaqModal(null));
	faqSearch.addEventListener('input', renderFaqs);

	// lg 以上左右並排時，FAQ 表格區的高度填滿到視窗底部，捲軸出現在表格區內（同 chat/index.html 的 fitChatPanels）
	// 比 lg 窄時上下排列，不限制高度，整頁一起捲動
	const sideBySide = window.matchMedia('(min-width: 992px)');
	function fitFaqPanel() {
		if (!sideBySide.matches) {
			faqTableArea.style.height = '';
			return;
		}
		const areaTop = faqTableArea.getBoundingClientRect().top + window.scrollY;
		const bottom = Math.max(innerHeight - 25, areaTop + 240);
		faqTableArea.style.height = (bottom - areaTop) + 'px';
	}
	window.addEventListener('resize', fitFaqPanel);
	sideBySide.addEventListener('change', fitFaqPanel);
	// 錯誤訊息、右邊標題列（類別描述、拖拉提示會顯示或隱藏）的高度改變都會影響位置，跟著重算
	const panelObserver = new ResizeObserver(fitFaqPanel);
	[pageError, faqTableArea.previousElementSibling].forEach(element => panelObserver.observe(element));
	fitFaqPanel();

	loadAll();
})();
