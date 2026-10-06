(function () {
    'use strict';

    const root = document.getElementById('comment-admin-root');
    if (!root) return;

    const contextPath = window.location.pathname.split('/admin')[0];
    const apiUrl = contextPath + '/api/admin/comments';
    const state = { tab: 'all', page: 0, size: 20, totalPages: 0, rows: new Map(), selected: null };
    let searchTimer = null;

    const byId = (id) => document.getElementById(id);

    async function request(url, options) {
        const response = await fetch(url, Object.assign({
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' }
        }, options || {}));
        const contentType = response.headers.get('content-type') || '';
        const body = contentType.includes('application/json') ? await response.json() : {};
        if (response.status === 401) {
            window.location.replace(contextPath + '/admin/employees/login');
            throw new Error('請先登入後台員工帳號');
        }
        if (!response.ok) throw new Error(body.message || '操作失敗，請稍後再試');
        return body;
    }

    function showFeedback(message, type) {
        const feedback = byId('comment-feedback');
        feedback.textContent = message || '';
        feedback.className = 'comment-feedback' + (type ? ' is-' + type : '');
        feedback.hidden = !message;
    }

    function formatDate(value) {
        if (!value) return '--';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return value;
        return new Intl.DateTimeFormat('zh-TW', {
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit', hour12: false
        }).format(date);
    }

    function stars(value) {
        const rating = Math.max(0, Math.min(5, Number(value) || 0));
        return '★'.repeat(rating) + '☆'.repeat(5 - rating);
    }

    function statusMeta(value) {
        if (Number(value) === 2) return { text: '待審核', className: 'is-reported' };
        if (Number(value) === 3) return { text: '已隱藏', className: 'is-hidden' };
        return { text: '顯示中', className: 'is-visible' };
    }

    function appendText(parent, className, text) {
        const element = document.createElement('div');
        element.className = className;
        element.textContent = text || '--';
        parent.appendChild(element);
        return element;
    }

    function createRow(comment) {
        const row = document.createElement('tr');
        const status = statusMeta(comment.commentStatus);
        if (Number(comment.commentStatus) === 2) row.classList.add('is-reported');

        const dateCell = row.insertCell();
        dateCell.textContent = formatDate(comment.commentDate);

        const productCell = row.insertCell();
        appendText(productCell, 'comment-primary', comment.productName || '商品資料已移除');
        appendText(productCell, 'comment-secondary', '訂單 #' + comment.ordersId + ' · SKU #' + comment.skuId);

        const memberCell = row.insertCell();
        appendText(memberCell, 'comment-primary', comment.memberName || '會員資料已移除');
        appendText(memberCell, 'comment-secondary', comment.memberAccount ? '@' + comment.memberAccount : '會員 #' + (comment.memberId || '--'));

        const ratingCell = row.insertCell();
        const rating = document.createElement('span');
        rating.className = 'comment-stars';
        rating.textContent = stars(comment.commentStar);
        rating.setAttribute('aria-label', (comment.commentStar || 0) + ' 星');
        ratingCell.appendChild(rating);

        const textCell = row.insertCell();
        appendText(textCell, 'comment-excerpt', comment.commentText);

        const statusCell = row.insertCell();
        const badge = document.createElement('span');
        badge.className = 'comment-status ' + status.className;
        badge.textContent = status.text;
        statusCell.appendChild(badge);

        const actionCell = row.insertCell();
        actionCell.className = 'text-right';
        const viewButton = document.createElement('button');
        viewButton.type = 'button';
        viewButton.className = 'comment-view-button' + (Number(comment.commentStatus) === 2 ? ' is-reported' : '');
        viewButton.dataset.commentId = comment.ordersDetailId;
        viewButton.title = Number(comment.commentStatus) === 2 ? '處理檢舉' : '查看評論';
        viewButton.setAttribute('aria-label', viewButton.title);
        const icon = document.createElement('i');
        icon.className = Number(comment.commentStatus) === 2 ? 'fas fa-gavel' : 'fas fa-eye';
        icon.setAttribute('aria-hidden', 'true');
        viewButton.appendChild(icon);
        actionCell.appendChild(viewButton);
        return row;
    }

    function renderSummary(summary) {
        const values = summary || {};
        byId('summary-total').textContent = Number(values.total || 0).toLocaleString('zh-TW');
        byId('summary-visible').textContent = Number(values.visible || 0).toLocaleString('zh-TW');
        byId('summary-reported').textContent = Number(values.reported || 0).toLocaleString('zh-TW');
        byId('summary-hidden').textContent = Number(values.hidden || 0).toLocaleString('zh-TW');
        byId('reported-heading-count').textContent = Number(values.reported || 0).toLocaleString('zh-TW');
        byId('reported-tab-count').textContent = Number(values.reported || 0).toLocaleString('zh-TW');
    }

    function renderResult(result) {
        const comments = result.comments || [];
        const body = byId('comment-table-body');
        state.rows.clear();
        body.replaceChildren();
        comments.forEach((comment) => {
            state.rows.set(String(comment.ordersDetailId), comment);
            body.appendChild(createRow(comment));
        });
        renderSummary(result.summary);
        state.totalPages = Number(result.totalPages || 0);
        state.page = Number(result.page || 0);
        byId('comment-empty').hidden = comments.length > 0;
        document.querySelector('.comment-table-wrap').hidden = comments.length === 0;
        const currentPage = state.totalPages === 0 ? 0 : state.page + 1;
        byId('comment-page-summary').textContent = '第 ' + currentPage + ' / ' + state.totalPages
            + ' 頁，共 ' + Number(result.totalElements || 0).toLocaleString('zh-TW') + ' 筆';
        byId('comment-page-previous').disabled = Boolean(result.first);
        byId('comment-page-next').disabled = Boolean(result.last);
    }

    function buildQuery() {
        const query = new URLSearchParams();
        const keyword = byId('comment-search').value.trim();
        const status = state.tab === 'reported' ? '2' : byId('comment-status-filter').value;
        const rating = byId('comment-rating-filter').value;
        const startDate = byId('comment-start-date').value;
        const endDate = byId('comment-end-date').value;
        if (keyword) query.set('keyword', keyword);
        if (status) query.set('status', status);
        if (rating) query.set('rating', rating);
        if (startDate) query.set('startDate', startDate);
        if (endDate) query.set('endDate', endDate);
        query.set('page', String(state.page));
        query.set('size', String(state.size));
        return query.toString();
    }

    async function loadComments() {
        showFeedback('', '');
        try {
            renderResult(await request(apiUrl + '?' + buildQuery()));
        } catch (error) {
            showFeedback(error.message, 'error');
        }
    }

    function switchTab(tab) {
        state.tab = tab;
        state.page = 0;
        document.querySelectorAll('[data-comment-tab]').forEach((button) => {
            button.classList.toggle('is-active', button.dataset.commentTab === tab);
        });
        const reported = tab === 'reported';
        byId('comment-report-notice').hidden = !reported;
        byId('comment-status-control').hidden = reported;
        byId('comment-empty-title').textContent = reported ? '目前沒有待處理檢舉' : '找不到符合條件的評論';
        byId('comment-empty-copy').textContent = reported ? '新的檢舉會顯示在這裡。' : '請調整搜尋或篩選條件。';
        loadComments();
    }

    function openComment(comment) {
        state.selected = comment;
        const reported = Number(comment.commentStatus) === 2;
        byId('comment-modal-kicker').textContent = reported ? '待審核檢舉' : '評論明細';
        byId('comment-modal-title').textContent = comment.productName || '商品評論';
        byId('detail-member').textContent = (comment.memberName || '會員資料已移除')
            + (comment.memberAccount ? ' (@' + comment.memberAccount + ')' : '');
        byId('detail-order').textContent = '#' + comment.ordersId + ' / 明細 #' + comment.ordersDetailId;
        byId('detail-rating').textContent = stars(comment.commentStar) + ' ' + (comment.commentStar || 0) + ' 星';
        byId('detail-date').textContent = formatDate(comment.commentDate);
        byId('detail-text').textContent = comment.commentText || '此評論沒有文字內容';
        byId('comment-moderation').hidden = !reported;
        byId('comment-decision-actions').hidden = !reported;
        byId('comment-moderation-note').value = '';
        byId('comment-modal-feedback').textContent = '';
        window.jQuery('#comment-detail-modal').modal('show');
    }

    async function moderate(action, button) {
        if (!state.selected) return;
        const note = byId('comment-moderation-note');
        if (!note.reportValidity()) return;
        document.querySelectorAll('[data-moderation-action]').forEach((item) => item.disabled = true);
        byId('comment-modal-feedback').textContent = '正在儲存判定結果...';
        try {
            await request(apiUrl + '/' + state.selected.ordersDetailId + '/moderation', {
                method: 'PUT', body: JSON.stringify({ action: action, note: note.value.trim() })
            });
            window.jQuery('#comment-detail-modal').modal('hide');
            showFeedback(action === 'HIDE' ? '評論已隱藏並保存處理紀錄' : '評論已保留並保存處理紀錄', 'success');
            await loadComments();
        } catch (error) {
            byId('comment-modal-feedback').textContent = error.message;
        } finally {
            document.querySelectorAll('[data-moderation-action]').forEach((item) => item.disabled = false);
            button.blur();
        }
    }

    document.querySelectorAll('[data-comment-tab]').forEach((button) => {
        button.addEventListener('click', () => switchTab(button.dataset.commentTab));
    });
    byId('comment-status-filter').addEventListener('change', () => { state.page = 0; loadComments(); });
    byId('comment-rating-filter').addEventListener('change', () => { state.page = 0; loadComments(); });
    byId('comment-start-date').addEventListener('change', () => { state.page = 0; loadComments(); });
    byId('comment-end-date').addEventListener('change', () => { state.page = 0; loadComments(); });
    byId('comment-search').addEventListener('input', () => {
        clearTimeout(searchTimer);
        searchTimer = window.setTimeout(() => { state.page = 0; loadComments(); }, 350);
    });
    byId('comment-refresh').addEventListener('click', () => { state.page = 0; loadComments(); });
    byId('comment-reset').addEventListener('click', () => {
        ['comment-search', 'comment-status-filter', 'comment-rating-filter', 'comment-start-date', 'comment-end-date']
            .forEach((id) => byId(id).value = '');
        state.page = 0;
        loadComments();
    });
    byId('comment-page-previous').addEventListener('click', () => {
        if (state.page > 0) { state.page -= 1; loadComments(); }
    });
    byId('comment-page-next').addEventListener('click', () => {
        if (state.page + 1 < state.totalPages) { state.page += 1; loadComments(); }
    });
    byId('comment-table-body').addEventListener('click', (event) => {
        const button = event.target.closest('[data-comment-id]');
        if (button) openComment(state.rows.get(button.dataset.commentId));
    });
    document.querySelectorAll('[data-moderation-action]').forEach((button) => {
        button.addEventListener('click', () => moderate(button.dataset.moderationAction, button));
    });

    loadComments();
})();
