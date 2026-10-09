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
        creditAllTotalElements: 0
    };
    let searchTimer = null;
    let creditSearchTimer = null;

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
        button.dataset.memberId = String(memberId);
        const symbol = createElement('i', icon);
        symbol.setAttribute('aria-hidden', 'true');
        button.appendChild(symbol);
        return button;
    }

    const creditTypeMap = {
        1: { text: '退款轉入', className: 'is-refund' },
        2: { text: '訂單折抵', className: 'is-use' },
        3: { text: '後台增加', className: 'is-add' },
        4: { text: '後台扣除', className: 'is-deduct' },
        5: { text: '訂單取消退回', className: 'is-return' }
    };

    // 將當頁會員資料轉成表格列，所有文字都使用 textContent 避免插入不安全的 HTML。
    function renderMembers() {
        const body = byId('member-table-body');
        body.replaceChildren();

        state.members.forEach((member) => {
            const row = document.createElement('tr');

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
            actions.appendChild(makeAction('fas fa-coins', '調整購物金', 'credit', member.memberId));
            actions.appendChild(makeAction(active ? 'fas fa-user-slash' : 'fas fa-user-check',
                active ? '停權會員' : '恢復會員', 'status', member.memberId, active));
            actionCell.appendChild(actions);

            [personCell, emailCell, birthdayCell, createdCell, creditCell, statusCell, actionCell]
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

    function openCredit(member) {
        byId('member-credit-id').value = String(member.memberId);
        byId('member-credit-subject').textContent = member.memberName + '（' + member.memberAccount + '）';
        byId('member-credit-value').value = String(member.shoppingCredit || 0);
        byId('member-credit-reason').value = '';
        byId('member-credit-feedback').textContent = '';
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
        const shoppingCredit = Number(byId('member-credit-value').value);
        const reason = byId('member-credit-reason').value.trim();
        const button = byId('member-credit-save');
        setBusy(button, true, '儲存中...');
        byId('member-credit-feedback').textContent = '';
        try {
            await request('/' + memberId + '/credit', {
                method: 'PATCH',
                body: JSON.stringify({ shoppingCredit: shoppingCredit, reason: reason })
            });
            window.jQuery('#member-credit-modal').modal('hide');
            await loadMembers();
            if (state.activeTab === 'credit-records') {
                await loadCreditAllLogs();
            }
            showFeedback('會員購物金已更新。', 'success');
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
    }

    document.addEventListener('DOMContentLoaded', () => {
        bindEvents();
        loadMembers();
    });
}());
