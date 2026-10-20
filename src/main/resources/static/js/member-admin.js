(function () {
    'use strict';

    const contextPath = window.location.pathname.split('/admin/members')[0];
    const apiBase = contextPath + '/api/admin/members';
    const state = {
        activeTab: 'members',
        members: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        pendingStatus: null,
        creditHistoryMember: null,
        creditHistoryPage: 0,
        creditHistoryTotalPages: 0,
        creditAllLogs: [],
        creditAllPage: 0,
        creditAllTotalPages: 0,
        creditAllTotalElements: 0,
        creditRequests: [],
        creditRequestPage: 0,
        creditRequestTotalPages: 0,
        creditRequestTotalElements: 0,
        creditRequestPendingCount: 0,
        highlightedRequestId: null,
        currentEmployeeId: null,
        canAdjustCredit: false,
        canReviewCredit: false,
        pendingReview: null
    };
    let searchTimer = null;
    let creditSearchTimer = null;
    let creditRequestSearchTimer = null;

    const byId = (id) => document.getElementById(id);

    // 統一呼叫會員後台 API，錯誤時優先顯示後端提供的中文訊息。
    async function request(path, options) {
        const response = await fetch(apiBase + path, Object.assign({
            headers: { 'Content-Type': 'application/json' }
        }, options || {}));
        const contentType = response.headers.get('content-type') || '';
        const body = contentType.includes('application/json') ? await response.json() : null;
        if (!response.ok) {
            throw new Error(body && body.message ? body.message : '操作失敗，請稍後再試');
        }
        return body;
    }

    function createElement(tagName, className, text) {
        const element = document.createElement(tagName);
        if (className) element.className = className;
        if (text !== undefined && text !== null) element.textContent = text;
        return element;
    }

    function showFeedback(message, type) {
        const feedback = byId('member-feedback');
        feedback.textContent = message || '';
        feedback.className = 'member-feedback' + (type ? ' is-' + type : '');
        feedback.hidden = !message;
    }

    function formatDate(value, includeTime) {
        if (!value) return '尚無資料';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return value;
        const options = { year: 'numeric', month: '2-digit', day: '2-digit' };
        if (includeTime) {
            options.hour = '2-digit';
            options.minute = '2-digit';
            options.hour12 = false;
        }
        return new Intl.DateTimeFormat('zh-TW', options).format(date);
    }

    function formatCredit(value) {
        return 'NT$ ' + Number(value || 0).toLocaleString('zh-TW');
    }

    function memberInitial(name) {
        const cleanName = (name || '').trim();
        return cleanName ? cleanName.charAt(0).toUpperCase() : '?';
    }

    function makeAction(icon, label, action, memberId, danger) {
        const button = createElement('button', 'member-action' + (danger ? ' is-danger' : ''));
        button.type = 'button';
        button.title = label;
        button.setAttribute('aria-label', label);
        button.dataset.action = action;
        if (memberId !== undefined && memberId !== null) button.dataset.memberId = String(memberId);
        const symbol = createElement('i', icon);
        symbol.setAttribute('aria-hidden', 'true');
        button.appendChild(symbol);
        return button;
    }

    function hasPermission(code) {
        if (code === 'MEMBER_CREDIT') return state.canAdjustCredit;
        if (code === 'MEMBER_CREDIT_REVIEW') return state.canReviewCredit;
        return false;
    }

    const creditTypeMap = {
        1: { text: '退款轉入', className: 'is-refund' },
        2: { text: '訂單折抵', className: 'is-use' },
        3: { text: '後台增加', className: 'is-add' },
        4: { text: '後台扣除', className: 'is-deduct' },
        5: { text: '訂單取消退回', className: 'is-return' },
        6: { text: '搶購物金', className: 'is-add' }
    };

    // 將當頁會員資料轉成表格列，所有文字都使用 textContent 避免插入不安全的 HTML。
    function renderMembers() {
        const body = byId('member-table-body');
        body.replaceChildren();

        state.members.forEach((member) => {
            const row = document.createElement('tr');

            const memberIdCell = createElement('td', 'member-id', String(member.memberId));
            const personCell = document.createElement('td');
            const person = createElement('div', 'member-person');
            person.appendChild(createElement('span', 'member-avatar', memberInitial(member.memberName)));
            const identity = document.createElement('div');
            identity.appendChild(createElement('div', 'member-primary', member.memberName));
            identity.appendChild(createElement('div', 'member-secondary', member.memberAccount));
            person.appendChild(identity);
            personCell.appendChild(person);

            const emailCell = createElement('td', 'member-email', member.memberEmail);
            const birthdayCell = createElement('td', '', formatDate(member.memberBirthday, false));
            const createdCell = createElement('td', '', formatDate(member.createdAt, true));
            const creditCell = createElement('td', 'member-credit', formatCredit(member.shoppingCredit));

            const active = Number(member.memberStatus) === 1;
            const statusCell = document.createElement('td');
            statusCell.appendChild(createElement('span', 'member-badge ' + (active ? 'is-active' : 'is-disabled'),
                active ? '正常' : '停權'));

            const actionCell = createElement('td', 'text-right');
            const actions = createElement('div', 'member-actions');
            actions.appendChild(makeAction('fas fa-eye', '查看會員資料', 'detail', member.memberId));
            actions.appendChild(makeAction('fas fa-history', '查看購物金紀錄', 'credit-history', member.memberId));
            if (hasPermission('MEMBER_CREDIT')) {
                actions.appendChild(makeAction('fas fa-coins', '申請調整購物金', 'credit', member.memberId));
            }
            actions.appendChild(makeAction(active ? 'fas fa-user-slash' : 'fas fa-user-check',
                active ? '停權會員' : '恢復會員', 'status', member.memberId, active));
            actionCell.appendChild(actions);

            [memberIdCell, personCell, emailCell, birthdayCell, createdCell, creditCell, statusCell, actionCell]
                .forEach((cell) => row.appendChild(cell));
            body.appendChild(row);
        });

        byId('member-empty').hidden = state.members.length !== 0;
        byId('member-total-count').textContent = String(state.totalElements);
        const visiblePage = state.totalPages === 0 ? 0 : state.page + 1;
        byId('member-page-summary').textContent = '第 ' + visiblePage + ' / ' + state.totalPages + ' 頁';
        byId('member-page-previous').disabled = state.page <= 0;
        byId('member-page-next').disabled = state.totalPages === 0 || state.page >= state.totalPages - 1;
    }

    // 從後端取得一頁會員；搜尋與狀態條件會一起送出，不在瀏覽器載入全部會員。
    async function loadMembers() {
        const parameters = new URLSearchParams();
        const keyword = byId('member-search').value.trim();
        const status = byId('member-status-filter').value;
        if (keyword) parameters.set('keyword', keyword);
        if (status) parameters.set('status', status);
        parameters.set('page', String(state.page));
        parameters.set('size', String(state.size));

        try {
            const result = await request('?' + parameters.toString());
            state.members = result.members || [];
            state.page = Number(result.page || 0);
            state.totalElements = Number(result.totalElements || 0);
            state.totalPages = Number(result.totalPages || 0);
            renderMembers();
            byId('member-online-count').textContent = String(result.onlineCount || 0);
            showFeedback('', '');
        } catch (error) {
            state.members = [];
            state.totalElements = 0;
            state.totalPages = 0;
            renderMembers();
            showFeedback(error.message, 'error');
        }
    }

    // 渲染集中查詢的全部會員購物金異動流水
    function renderCreditAllLogs() {
        const body = byId('credit-all-table-body');
        body.replaceChildren();

        state.creditAllLogs.forEach((item) => {
            const row = document.createElement('tr');
            const timeCell = createElement('td', '', formatDate(item.createdAt, true));

            const memberCell = document.createElement('td');
            const person = createElement('div', 'member-person');
            person.appendChild(createElement('span', 'member-avatar', memberInitial(item.memberName)));
            const identity = document.createElement('div');
            identity.appendChild(createElement('div', 'member-primary', item.memberName || ('會員 #' + item.memberId)));
            identity.appendChild(createElement('div', 'member-secondary', item.memberAccount || ''));
            person.appendChild(identity);
            memberCell.appendChild(person);

            const typeConfig = creditTypeMap[item.transactionType] || { text: '其他', className: '' };
            const typeCell = document.createElement('td');
            typeCell.appendChild(createElement('span', 'credit-type-badge ' + typeConfig.className, typeConfig.text));

            const amount = Number(item.amount || 0);
            const amountCell = document.createElement('td');
            const amountSpan = createElement('span', 'credit-amount-num ' + (amount >= 0 ? 'is-positive' : 'is-negative'),
                (amount >= 0 ? '+' : '') + formatCredit(amount));
            amountCell.appendChild(amountSpan);

            const flowCell = document.createElement('td');
            flowCell.appendChild(createElement('span', 'credit-flow-text',
                formatCredit(item.balanceBefore) + ' → ' + formatCredit(item.balanceAfter)));

            const refText = item.refundOrderId ? '退款單 #' + item.refundOrderId
                : (item.ordersId ? '訂單 #' + item.ordersId : '—');
            const refCell = createElement('td', '', refText);

            const reasonCell = createElement('td', '', item.reason || '—');

            const operatorCell = createElement('td', '', item.employeeName
                || (item.employeeId ? '員工 #' + item.employeeId : '系統自動'));

            [timeCell, memberCell, typeCell, amountCell, flowCell, refCell, reasonCell, operatorCell]
                .forEach((cell) => row.appendChild(cell));
            body.appendChild(row);
        });

        byId('credit-all-empty').hidden = state.creditAllLogs.length !== 0;
        const visiblePage = state.creditAllTotalPages === 0 ? 0 : state.creditAllPage + 1;
        byId('credit-all-page-summary').textContent = '第 ' + visiblePage + ' / ' + state.creditAllTotalPages
            + ' 頁，共 ' + state.creditAllTotalElements + ' 筆';
        byId('credit-all-page-previous').disabled = state.creditAllPage <= 0;
        byId('credit-all-page-next').disabled = state.creditAllTotalPages === 0
            || state.creditAllPage >= state.creditAllTotalPages - 1;
    }

    // 載入全部會員購物金流水
    async function loadCreditAllLogs() {
        const parameters = new URLSearchParams();
        const keyword = byId('credit-all-search').value.trim();
        const type = byId('credit-all-type-filter').value;
        const startDate = byId('credit-all-start-date').value;
        const endDate = byId('credit-all-end-date').value;

        if (keyword) parameters.set('keyword', keyword);
        if (type) parameters.set('type', type);
        if (startDate) parameters.set('startDate', startDate);
        if (endDate) parameters.set('endDate', endDate);
        parameters.set('page', String(state.creditAllPage));
        parameters.set('size', '20');

        try {
            const result = await request('/credit-transactions?' + parameters.toString());
            state.creditAllLogs = result.transactions || [];
            state.creditAllPage = Number(result.page || 0);
            state.creditAllTotalPages = Number(result.totalPages || 0);
            state.creditAllTotalElements = Number(result.totalElements || 0);
            renderCreditAllLogs();
        } catch (error) {
            state.creditAllLogs = [];
            state.creditAllPage = 0;
            state.creditAllTotalPages = 0;
            state.creditAllTotalElements = 0;
            renderCreditAllLogs();
            showFeedback(error.message, 'error');
        }
    }

    function addDetailRow(list, label, value) {
        const term = createElement('dt', '', label);
        const detail = createElement('dd', '', value);
        list.appendChild(term);
        list.appendChild(detail);
    }

    function openDetail(member) {
        const list = byId('member-detail-list');
        list.replaceChildren();
        addDetailRow(list, '會員編號', String(member.memberId));
        addDetailRow(list, '姓名', member.memberName);
        addDetailRow(list, '登入帳號', member.memberAccount);
        addDetailRow(list, '電子郵件', member.memberEmail);
        addDetailRow(list, '生日', formatDate(member.memberBirthday, false));
        addDetailRow(list, '加入時間', formatDate(member.createdAt, true));
        addDetailRow(list, '購物金', formatCredit(member.shoppingCredit));
        addDetailRow(list, '會員狀態', Number(member.memberStatus) === 1 ? '正常' : '停權');
        window.jQuery('#member-detail-modal').modal('show');
    }

    function selectedAdjustmentType() {
        const checked = document.querySelector('input[name="member-credit-type"]:checked');
        return Number(checked ? checked.value : 1);
    }

    function currentCreditBalance() {
        return Number(byId('member-credit-current').dataset.balance || 0);
    }

    function updateCreditPreview() {
        const preview = byId('member-credit-preview');
        const amount = Number(byId('member-credit-amount').value);
        const current = currentCreditBalance();
        if (!Number.isInteger(amount) || amount <= 0) {
            preview.textContent = '送出後不會立刻改餘額，需主管核准才入帳。';
            preview.classList.remove('is-invalid');
            return;
        }
        const next = selectedAdjustmentType() === 1 ? current + amount : current - amount;
        preview.textContent = '預估核准後餘額：' + formatCredit(next) + '（送出後需待主管審核）';
        preview.classList.toggle('is-invalid', next < 0);
    }

    function openCredit(member) {
        byId('member-credit-id').value = String(member.memberId);
        byId('member-credit-subject').textContent = member.memberName + '（' + member.memberAccount + '）';
        const current = byId('member-credit-current');
        current.dataset.balance = String(member.shoppingCredit || 0);
        current.textContent = '目前購物金：' + formatCredit(member.shoppingCredit);
        document.querySelector('input[name="member-credit-type"][value="1"]').checked = true;
        byId('member-credit-amount').value = '';
        byId('member-credit-reason-category').value = '';
        byId('member-credit-reason').value = '';
        byId('member-credit-feedback').textContent = '';
        updateCreditPreview();
        window.jQuery('#member-credit-modal').modal('show');
    }

    function renderCreditHistory(transactions) {
        const body = byId('member-credit-history-body');
        body.replaceChildren();
        transactions.forEach((item) => {
            const row = document.createElement('tr');
            const amount = Number(item.amount || 0);
            const reference = item.refundOrderId ? '退款單 #' + item.refundOrderId
                : (item.ordersId ? '訂單 #' + item.ordersId : '後台調整');
            const typeConfig = creditTypeMap[item.transactionType] || { text: '其他' };

            row.appendChild(createElement('td', '', formatDate(item.createdAt, true)));
            row.appendChild(createElement('td', '', typeConfig.text));
            row.appendChild(createElement('td', 'credit-history-amount ' + (amount >= 0 ? 'is-positive' : 'is-negative'),
                (amount >= 0 ? '+' : '') + formatCredit(amount)));
            row.appendChild(createElement('td', '', formatCredit(item.balanceBefore) + ' → ' + formatCredit(item.balanceAfter)));
            row.appendChild(createElement('td', '', reference));
            row.appendChild(createElement('td', 'credit-history-reason', item.reason || '—'));
            body.appendChild(row);
        });
        byId('member-credit-history-empty').hidden = transactions.length !== 0;
        const visiblePage = state.creditHistoryTotalPages === 0 ? 0 : state.creditHistoryPage + 1;
        byId('member-credit-history-summary').textContent = '第 ' + visiblePage + ' / '
            + state.creditHistoryTotalPages + ' 頁';
        byId('member-credit-history-previous').disabled = state.creditHistoryPage <= 0;
        byId('member-credit-history-next').disabled = state.creditHistoryTotalPages === 0
            || state.creditHistoryPage >= state.creditHistoryTotalPages - 1;
    }

    async function loadCreditHistory() {
        if (!state.creditHistoryMember) return;
        try {
            const result = await request('/' + state.creditHistoryMember.memberId + '/credit-transactions?page='
                + state.creditHistoryPage + '&size=20');
            state.creditHistoryPage = Number(result.page || 0);
            state.creditHistoryTotalPages = Number(result.totalPages || 0);
            renderCreditHistory(result.transactions || []);
        } catch (error) {
            renderCreditHistory([]);
            showFeedback(error.message, 'error');
        }
    }

    function openCreditHistory(member) {
        state.creditHistoryMember = member;
        state.creditHistoryPage = 0;
        byId('member-credit-history-subject').textContent = member.memberName + '（' + member.memberAccount + '）';
        renderCreditHistory([]);
        window.jQuery('#member-credit-history-modal').modal('show');
        loadCreditHistory();
    }

    function openStatus(member) {
        const disabling = Number(member.memberStatus) === 1;
        state.pendingStatus = {
            memberId: member.memberId,
            memberStatus: disabling ? 0 : 1
        };
        byId('member-status-title').textContent = disabling ? '停權會員' : '恢復會員';
        byId('member-status-message').textContent = disabling
            ? '確定要停權「' + member.memberName + '」嗎？停權後將無法登入。'
            : '確定要恢復「' + member.memberName + '」嗎？恢復後可重新登入。';
        byId('member-status-feedback').textContent = '';
        byId('member-status-confirm').className = disabling ? 'btn btn-danger' : 'btn btn-primary';
        window.jQuery('#member-status-modal').modal('show');
    }

    function setBusy(button, busy, busyText) {
        if (busy) {
            button.dataset.originalHtml = button.innerHTML;
            button.disabled = true;
            button.textContent = busyText;
        } else {
            button.disabled = false;
            if (button.dataset.originalHtml) button.innerHTML = button.dataset.originalHtml;
        }
    }

    async function saveCredit(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (!form.reportValidity()) return;
        const memberId = Number(byId('member-credit-id').value);
        const amount = Number(byId('member-credit-amount').value);
        const reasonCategory = byId('member-credit-reason-category').value.trim();
        const reasonDetail = byId('member-credit-reason').value.trim();
        const button = byId('member-credit-save');
        setBusy(button, true, '送出中...');
        byId('member-credit-feedback').textContent = '';
        try {
            const created = await request('/' + memberId + '/credit-adjustment-requests', {
                method: 'POST',
                body: JSON.stringify({
                    adjustmentType: selectedAdjustmentType(),
                    amount: amount,
                    reasonCategory: reasonCategory,
                    reasonDetail: reasonDetail
                })
            });
            window.jQuery('#member-credit-modal').modal('hide');
            state.highlightedRequestId = created && created.creditAdjustmentRequestId
                ? created.creditAdjustmentRequestId : null;
            byId('credit-request-status-filter').value = '0';
            state.creditRequestPage = 0;
            switchTab('credit-requests');
			window.dispatchEvent(new Event('admin:review-queue-changed'));
            showFeedback('已送出，待主管審核。', 'success');
        } catch (error) {
            byId('member-credit-feedback').textContent = error.message;
        } finally {
            setBusy(button, false);
        }
    }

    async function saveStatus() {
        if (!state.pendingStatus) return;
        const button = byId('member-status-confirm');
        setBusy(button, true, '處理中...');
        byId('member-status-feedback').textContent = '';
        try {
            await request('/' + state.pendingStatus.memberId + '/status', {
                method: 'PATCH',
                body: JSON.stringify({ memberStatus: state.pendingStatus.memberStatus })
            });
            const successMessage = state.pendingStatus.memberStatus === 1 ? '會員已恢復。' : '會員已停權。';
            state.pendingStatus = null;
            window.jQuery('#member-status-modal').modal('hide');
            await loadMembers();
            showFeedback(successMessage, 'success');
        } catch (error) {
            byId('member-status-feedback').textContent = error.message;
        } finally {
            setBusy(button, false);
        }
    }

    function switchTab(tabKey) {
        state.activeTab = tabKey;
        document.querySelectorAll('.member-tab').forEach((tab) => {
            tab.classList.toggle('is-active', tab.dataset.tab === tabKey);
        });
        document.querySelectorAll('.member-panel').forEach((panel) => {
            const matches = panel.dataset.panel === tabKey;
            panel.classList.toggle('is-active', matches);
            panel.hidden = !matches;
        });
        if (tabKey === 'credit-records') {
            loadCreditAllLogs();
        }
        if (tabKey === 'credit-requests') {
            loadCreditRequests();
        }
    }

    function bindEvents() {
        // 分頁切換
        document.querySelectorAll('.member-tab').forEach((tab) => {
            tab.addEventListener('click', () => {
                switchTab(tab.dataset.tab);
            });
        });

        // 會員清單搜尋
        byId('member-search').addEventListener('input', () => {
            window.clearTimeout(searchTimer);
            searchTimer = window.setTimeout(() => {
                state.page = 0;
                loadMembers();
            }, 250);
        });
        byId('member-status-filter').addEventListener('change', () => {
            state.page = 0;
            loadMembers();
        });
        byId('member-page-previous').addEventListener('click', () => {
            if (state.page > 0) {
                state.page -= 1;
                loadMembers();
            }
        });
        byId('member-page-next').addEventListener('click', () => {
            if (state.page < state.totalPages - 1) {
                state.page += 1;
                loadMembers();
            }
        });
        byId('member-table-body').addEventListener('click', (event) => {
            const button = event.target.closest('[data-action]');
            if (!button) return;
            const member = state.members.find((item) => item.memberId === Number(button.dataset.memberId));
            if (!member) return;
            if (button.dataset.action === 'detail') openDetail(member);
            if (button.dataset.action === 'credit-history') openCreditHistory(member);
            if (button.dataset.action === 'credit') openCredit(member);
            if (button.dataset.action === 'status') openStatus(member);
        });
        byId('member-credit-form').addEventListener('submit', saveCredit);
        byId('member-status-confirm').addEventListener('click', saveStatus);

        // 單一會員購物金歷史彈窗分頁
        byId('member-credit-history-previous').addEventListener('click', () => {
            if (state.creditHistoryPage > 0) { state.creditHistoryPage -= 1; loadCreditHistory(); }
        });
        byId('member-credit-history-next').addEventListener('click', () => {
            if (state.creditHistoryPage < state.creditHistoryTotalPages - 1) {
                state.creditHistoryPage += 1;
                loadCreditHistory();
            }
        });

        // 全部購物金紀錄搜尋與篩選
        byId('credit-all-search').addEventListener('input', () => {
            window.clearTimeout(creditSearchTimer);
            creditSearchTimer = window.setTimeout(() => {
                state.creditAllPage = 0;
                loadCreditAllLogs();
            }, 250);
        });
        ['credit-all-type-filter', 'credit-all-start-date', 'credit-all-end-date'].forEach((id) => {
            byId(id).addEventListener('change', () => {
                state.creditAllPage = 0;
                loadCreditAllLogs();
            });
        });
        byId('credit-all-refresh-button').addEventListener('click', loadCreditAllLogs);
        const creditResetBtn = byId('credit-all-reset-button');
        if (creditResetBtn) {
            creditResetBtn.addEventListener('click', () => {
                byId('credit-all-search').value = '';
                byId('credit-all-type-filter').value = '';
                byId('credit-all-start-date').value = '';
                byId('credit-all-end-date').value = '';
                state.creditAllPage = 0;
                loadCreditAllLogs();
            });
        }
        byId('credit-all-page-previous').addEventListener('click', () => {
            if (state.creditAllPage > 0) {
                state.creditAllPage -= 1;
                loadCreditAllLogs();
            }
        });
        byId('credit-all-page-next').addEventListener('click', () => {
            if (state.creditAllPage < state.creditAllTotalPages - 1) {
                state.creditAllPage += 1;
                loadCreditAllLogs();
            }
        });

        byId('credit-request-search').addEventListener('input', () => {
            window.clearTimeout(creditRequestSearchTimer);
            creditRequestSearchTimer = window.setTimeout(() => {
                state.creditRequestPage = 0;
                loadCreditRequests();
            }, 250);
        });
        byId('credit-request-status-filter').addEventListener('change', () => {
            state.creditRequestPage = 0;
            loadCreditRequests();
        });
        byId('credit-request-refresh-button').addEventListener('click', loadCreditRequests);
        byId('credit-request-reset-button').addEventListener('click', () => {
            byId('credit-request-search').value = '';
            byId('credit-request-status-filter').value = '';
            state.creditRequestPage = 0;
            loadCreditRequests();
        });
        byId('credit-request-page-previous').addEventListener('click', () => {
            if (state.creditRequestPage > 0) {
                state.creditRequestPage -= 1;
                loadCreditRequests();
            }
        });
        byId('credit-request-page-next').addEventListener('click', () => {
            if (state.creditRequestPage < state.creditRequestTotalPages - 1) {
                state.creditRequestPage += 1;
                loadCreditRequests();
            }
        });
        byId('credit-request-table-body').addEventListener('click', (event) => {
            const button = event.target.closest('[data-action]');
            if (!button) return;
            const requestItem = state.creditRequests.find((item) =>
                item.creditAdjustmentRequestId === Number(button.dataset.requestId));
            if (!requestItem) return;
            if (button.dataset.action === 'review') openCreditReview(requestItem);
            if (button.dataset.action === 'cancel-request') cancelCreditRequest(requestItem);
        });
        document.querySelectorAll('input[name="member-credit-type"]').forEach((input) => {
            input.addEventListener('change', updateCreditPreview);
        });
        byId('member-credit-amount').addEventListener('input', updateCreditPreview);
        byId('member-credit-review-approve').addEventListener('click', () => submitCreditReview(true));
        byId('member-credit-review-reject').addEventListener('click', () => submitCreditReview(false));
    }

    async function loadSessionPermissions() {
        try {
            const response = await fetch(contextPath + '/api/admin/session', {
                headers: { 'Content-Type': 'application/json' }
            });
            const body = response.ok ? await response.json() : null;
            const codes = body && Array.isArray(body.permissionCodes) ? body.permissionCodes : [];
            state.currentEmployeeId = body && body.employeeId ? Number(body.employeeId) : null;
            state.canAdjustCredit = codes.indexOf('MEMBER_CREDIT') !== -1;
            state.canReviewCredit = codes.indexOf('MEMBER_CREDIT_REVIEW') !== -1;
        } catch (error) {
            state.canAdjustCredit = false;
            state.canReviewCredit = false;
        }
    }

    function requestStatusMap() {
        return {
            0: { text: '待審核', className: 'is-pending' },
            1: { text: '已核准', className: 'is-approved' },
            2: { text: '已退回', className: 'is-rejected' },
            3: { text: '已取消', className: 'is-cancelled' }
        };
    }

    function renderCreditRequests() {
        const body = byId('credit-request-table-body');
        body.replaceChildren();
        const statusMap = requestStatusMap();
        state.creditRequests.forEach((item) => {
            const row = document.createElement('tr');
            if (state.highlightedRequestId === item.creditAdjustmentRequestId) {
                row.className = 'is-highlighted';
            }
            const typeText = Number(item.adjustmentType) === 1 ? '增加' : '扣除';
            const typeClass = Number(item.adjustmentType) === 1 ? 'is-add' : 'is-deduct';
            const status = statusMap[item.requestStatus] || { text: '其他', className: '' };
            const typeCell = document.createElement('td');
            typeCell.appendChild(createElement('span', 'credit-type-badge ' + typeClass, typeText));
            const amountCell = document.createElement('td');
            amountCell.appendChild(createElement('span',
                'credit-amount-num ' + (Number(item.adjustmentType) === 1 ? 'is-positive' : 'is-negative'),
                (Number(item.adjustmentType) === 1 ? '+' : '-') + formatCredit(item.amount)));
            const reasonCell = document.createElement('td');
            reasonCell.appendChild(createElement('div', 'member-primary', item.reasonCategory || '—'));
            reasonCell.appendChild(createElement('div', 'member-secondary', item.reasonDetail || ''));
            const statusCell = document.createElement('td');
            statusCell.appendChild(createElement('span', 'member-badge ' + status.className, status.text));
            const actionCell = createElement('td', 'text-right');
            const actions = createElement('div', 'member-actions');
            const pending = Number(item.requestStatus) === 0;
            if (pending && hasPermission('MEMBER_CREDIT_REVIEW')
                    && Number(item.requestedByEmployeeId) !== Number(state.currentEmployeeId)) {
                const reviewButton = makeAction('fas fa-check', '審核申請', 'review');
                reviewButton.dataset.requestId = String(item.creditAdjustmentRequestId);
                actions.appendChild(reviewButton);
            }
            if (pending && Number(item.requestedByEmployeeId) === Number(state.currentEmployeeId)) {
                const cancelButton = makeAction('fas fa-times', '取消申請', 'cancel-request', null, true);
                cancelButton.dataset.requestId = String(item.creditAdjustmentRequestId);
                actions.appendChild(cancelButton);
            }
            actionCell.appendChild(actions);

            const memberCell = document.createElement('td');
            const person = createElement('div', 'member-person');
            person.appendChild(createElement('span', 'member-avatar', memberInitial(item.memberName)));
            const identity = document.createElement('div');
            identity.appendChild(createElement('div', 'member-primary', item.memberName || ('會員 #' + item.memberId)));
            identity.appendChild(createElement('div', 'member-secondary', item.memberAccount || ''));
            person.appendChild(identity);
            memberCell.appendChild(person);

            [
                createElement('td', '', formatDate(item.requestedAt, true)),
                memberCell,
                typeCell,
                amountCell,
                createElement('td', '', formatCredit(item.balanceAtRequest)),
                reasonCell,
                createElement('td', '', item.requestedByEmployeeName || ('員工 #' + item.requestedByEmployeeId)),
                statusCell,
                actionCell
            ].forEach((cell) => row.appendChild(cell));
            body.appendChild(row);
        });
        byId('credit-request-empty').hidden = state.creditRequests.length !== 0;
        const visiblePage = state.creditRequestTotalPages === 0 ? 0 : state.creditRequestPage + 1;
        byId('credit-request-page-summary').textContent = '第 ' + visiblePage + ' / ' + state.creditRequestTotalPages
            + ' 頁，共 ' + state.creditRequestTotalElements + ' 筆';
        byId('credit-request-page-previous').disabled = state.creditRequestPage <= 0;
        byId('credit-request-page-next').disabled = state.creditRequestTotalPages === 0
            || state.creditRequestPage >= state.creditRequestTotalPages - 1;
        const count = byId('credit-request-pending-count');
        count.textContent = String(state.creditRequestPendingCount);
        count.hidden = state.creditRequestPendingCount <= 0;
    }

    async function loadCreditRequests() {
        const parameters = new URLSearchParams();
        const keyword = byId('credit-request-search').value.trim();
        const status = byId('credit-request-status-filter').value;
        if (keyword) parameters.set('keyword', keyword);
        if (status) parameters.set('status', status);
        parameters.set('page', String(state.creditRequestPage));
        parameters.set('size', '20');
        try {
            const result = await request('/credit-adjustment-requests?' + parameters.toString());
            state.creditRequests = result.requests || [];
            state.creditRequestPage = Number(result.page || 0);
            state.creditRequestTotalPages = Number(result.totalPages || 0);
            state.creditRequestTotalElements = Number(result.totalElements || 0);
            if (status === '' || status === '0') {
                state.creditRequestPendingCount = status === '0'
                    ? state.creditRequestTotalElements
                    : state.creditRequests.filter((item) => Number(item.requestStatus) === 0).length;
            }
            renderCreditRequests();
            if (status !== '0') {
                refreshPendingCount();
            }
        } catch (error) {
            state.creditRequests = [];
            state.creditRequestTotalPages = 0;
            state.creditRequestTotalElements = 0;
            renderCreditRequests();
            showFeedback(error.message, 'error');
        }
    }

    async function refreshPendingCount() {
        try {
            const result = await request('/credit-adjustment-requests?status=0&page=0&size=1');
            state.creditRequestPendingCount = Number(result.totalElements || 0);
            const count = byId('credit-request-pending-count');
            count.textContent = String(state.creditRequestPendingCount);
            count.hidden = state.creditRequestPendingCount <= 0;
        } catch (error) {
            // 待審核數量只是提示，失敗時不擋住申請列表。
        }
    }

    function openCreditReview(item) {
        state.pendingReview = item;
        byId('member-credit-review-id').value = String(item.creditAdjustmentRequestId);
        byId('member-credit-review-subject').textContent =
            (item.memberName || '會員') + '（' + (item.memberAccount || '') + '）';
        const list = byId('member-credit-review-summary');
        list.replaceChildren();
        addDetailRow(list, '調整類型', Number(item.adjustmentType) === 1 ? '增加' : '扣除');
        addDetailRow(list, '調整金額', formatCredit(item.amount));
        addDetailRow(list, '申請時餘額', formatCredit(item.balanceAtRequest));
        addDetailRow(list, '原因分類', item.reasonCategory || '—');
        addDetailRow(list, '詳細說明', item.reasonDetail || '—');
        addDetailRow(list, '申請人', item.requestedByEmployeeName || ('員工 #' + item.requestedByEmployeeId));
        byId('member-credit-review-note').value = '';
        byId('member-credit-review-feedback').textContent = '';
        window.jQuery('#member-credit-review-modal').modal('show');
    }

    async function submitCreditReview(approve) {
        if (!state.pendingReview) return;
        const note = byId('member-credit-review-note').value.trim();
        const button = byId(approve ? 'member-credit-review-approve' : 'member-credit-review-reject');
        setBusy(button, true, approve ? '核准中...' : '退回中...');
        byId('member-credit-review-feedback').textContent = '';
        try {
            await request('/credit-adjustment-requests/' + state.pendingReview.creditAdjustmentRequestId + '/review', {
                method: 'PATCH',
                body: JSON.stringify({ approved: approve, reviewNote: note })
            });
            window.jQuery('#member-credit-review-modal').modal('hide');
            state.pendingReview = null;
            await loadCreditRequests();
            await loadMembers();
            if (state.activeTab === 'credit-records') await loadCreditAllLogs();
			window.dispatchEvent(new Event('admin:review-queue-changed'));
            showFeedback(approve ? '已核准，購物金已入帳。' : '已退回申請。', 'success');
        } catch (error) {
            byId('member-credit-review-feedback').textContent = error.message;
        } finally {
            setBusy(button, false);
        }
    }

    async function cancelCreditRequest(item) {
        try {
            await request('/credit-adjustment-requests/' + item.creditAdjustmentRequestId + '/cancel', {
                method: 'PATCH'
            });
            await loadCreditRequests();
			window.dispatchEvent(new Event('admin:review-queue-changed'));
            showFeedback('已取消申請。', 'success');
        } catch (error) {
            showFeedback(error.message, 'error');
        }
    }

    document.addEventListener('DOMContentLoaded', async () => {
        bindEvents();
        await loadSessionPermissions();
        loadMembers();
		const requestedTab = new URLSearchParams(window.location.search).get('tab');
		if (['members', 'credit-requests', 'credit-records'].indexOf(requestedTab) !== -1) {
			switchTab(requestedTab);
		} else {
			refreshPendingCount();
		}
    });
}());
