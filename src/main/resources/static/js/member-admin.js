(function () {
    'use strict';

    const contextPath = window.location.pathname.split('/admin/members')[0];
    const apiBase = contextPath + '/api/admin/members';
    const state = {
        members: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        pendingStatus: null
    };
    let searchTimer = null;

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
            showFeedback('', '');
        } catch (error) {
            state.members = [];
            state.totalElements = 0;
            state.totalPages = 0;
            renderMembers();
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
        byId('member-credit-feedback').textContent = '';
        window.jQuery('#member-credit-modal').modal('show');
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
        const button = byId('member-credit-save');
        setBusy(button, true, '儲存中...');
        byId('member-credit-feedback').textContent = '';
        try {
            await request('/' + memberId + '/credit', {
                method: 'PATCH',
                body: JSON.stringify({ shoppingCredit: shoppingCredit })
            });
            window.jQuery('#member-credit-modal').modal('hide');
            await loadMembers();
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

    function bindEvents() {
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
            if (button.dataset.action === 'credit') openCredit(member);
            if (button.dataset.action === 'status') openStatus(member);
        });
        byId('member-credit-form').addEventListener('submit', saveCredit);
        byId('member-status-confirm').addEventListener('click', saveStatus);
    }

    document.addEventListener('DOMContentLoaded', () => {
        bindEvents();
        loadMembers();
    });
}());
