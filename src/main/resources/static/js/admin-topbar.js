(function () {
    'use strict';

    const topbar = document.getElementById('admin-user-toggle');
    if (!topbar) return;

    const contextPath = window.location.pathname.split('/admin')[0];
    const sessionApi = contextPath + '/api/admin/session';
    const auditApi = contextPath + '/api/admin/employees/audit-logs?page=0&size=5';
    let currentEmployee = null;
    let notificationsLoaded = false;
	let pendingProfile = null;

    const byId = (id) => document.getElementById(id);

    async function request(url, options) {
        const response = await fetch(url, Object.assign({
            headers: { 'Content-Type': 'application/json' }
        }, options || {}));
        const contentType = response.headers.get('content-type') || '';
        const body = contentType.includes('application/json') ? await response.json() : null;
        if (!response.ok) throw new Error(body && body.message ? body.message : '操作失敗，請稍後再試');
        return body;
    }

    function employeeInitial(name) {
        const cleanName = (name || '').trim();
        return cleanName ? cleanName.charAt(0).toUpperCase() : '?';
    }

    function formatDate(value) {
        if (!value) return '尚無紀錄';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return value;
        return new Intl.DateTimeFormat('zh-TW', {
            month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
        }).format(date);
    }

    // 依 Session 狀態更新右上角，未登入時不顯示任何假的員工姓名。
    function renderSession(employee) {
        currentEmployee = employee && employee.authenticated ? employee : null;
        const authenticated = Boolean(currentEmployee);
        byId('admin-user-name').textContent = authenticated ? currentEmployee.employeeName : '尚未登入';
        byId('admin-user-position').textContent = authenticated ? currentEmployee.positionName : '後台帳號';
        byId('admin-menu-user-name').textContent = authenticated ? currentEmployee.employeeName : '尚未登入';
        byId('admin-menu-user-account').textContent = authenticated
            ? currentEmployee.employeeAccount + ' · ' + currentEmployee.positionName : '請先登入員工帳號';
        byId('admin-user-avatar').textContent = authenticated ? employeeInitial(currentEmployee.employeeName) : '';
        if (!authenticated) {
            const icon = document.createElement('i');
            icon.className = 'fas fa-user';
            byId('admin-user-avatar').appendChild(icon);
        }
        byId('admin-profile-button').disabled = !authenticated;
        byId('admin-password-button').disabled = !authenticated;
        byId('admin-login-link').hidden = authenticated;
        byId('admin-logout-button').hidden = !authenticated;
    }

    function appendProfileRow(list, label, value) {
        const term = document.createElement('dt');
        const detail = document.createElement('dd');
        term.textContent = label;
        detail.textContent = value || '尚無資料';
        list.appendChild(term);
        list.appendChild(detail);
    }

	function appendProfileInput(list, label, id, type, value) {
		const term = document.createElement('dt');
		const detail = document.createElement('dd');
		const input = document.createElement('input');
		term.textContent = label;
		input.className = 'form-control';
		input.id = id;
		input.type = type;
		input.value = value || '';
		input.required = true;
		input.maxLength = type === 'email' ? 100 : 20;
		input.autocomplete = type === 'email' ? 'email' : 'tel';
		detail.appendChild(input);
		list.appendChild(term);
		list.appendChild(detail);
	}

    function openProfile() {
        if (!currentEmployee) return;
        const list = byId('admin-profile-list');
        list.replaceChildren();
        appendProfileRow(list, '員工姓名', currentEmployee.employeeName);
        appendProfileRow(list, '登入帳號', currentEmployee.employeeAccount);
        appendProfileRow(list, '職位', currentEmployee.positionName);
		appendProfileInput(list, '聯絡電話', 'admin-profile-phone', 'tel', currentEmployee.employeePhone);
		appendProfileInput(list, '電子信箱', 'admin-profile-email', 'email', currentEmployee.employeeEmail);
        appendProfileRow(list, '上次登入', currentEmployee.lastLoginAt ? formatDate(currentEmployee.lastLoginAt) : '本次首次登入');
		pendingProfile = null;
		byId('admin-profile-confirmation').hidden = true;
		byId('admin-profile-edit-actions').hidden = false;
		byId('admin-profile-confirm-actions').hidden = true;
		byId('admin-profile-feedback').textContent = '';
        window.jQuery('#admin-profile-modal').modal('show');
    }

	// 第一階段只整理並顯示變更內容，不會在這一步寫入資料庫。
	function reviewProfile(event) {
		event.preventDefault();
		const form = event.currentTarget;
		if (!form.reportValidity()) return;
		const feedback = byId('admin-profile-feedback');
		feedback.textContent = '';
		const employeePhone = byId('admin-profile-phone').value.trim();
		const employeeEmail = byId('admin-profile-email').value.trim();
		if (employeePhone === currentEmployee.employeePhone && employeeEmail === currentEmployee.employeeEmail) {
			feedback.textContent = '電話與電子信箱都沒有變更';
			return;
		}
		pendingProfile = { employeePhone: employeePhone, employeeEmail: employeeEmail };
		const changes = [];
		if (employeePhone !== currentEmployee.employeePhone) {
			changes.push('聯絡電話：' + currentEmployee.employeePhone + ' → ' + employeePhone);
		}
		if (employeeEmail !== currentEmployee.employeeEmail) {
			changes.push('電子信箱：' + currentEmployee.employeeEmail + ' → ' + employeeEmail);
		}
		byId('admin-profile-confirmation-text').textContent = changes.join('\n');
		byId('admin-profile-confirmation').hidden = false;
		byId('admin-profile-edit-actions').hidden = true;
		byId('admin-profile-confirm-actions').hidden = false;
		byId('admin-profile-phone').disabled = true;
		byId('admin-profile-email').disabled = true;
	}

	function returnToProfileEdit() {
		pendingProfile = null;
		byId('admin-profile-confirmation').hidden = true;
		byId('admin-profile-edit-actions').hidden = false;
		byId('admin-profile-confirm-actions').hidden = true;
		byId('admin-profile-phone').disabled = false;
		byId('admin-profile-email').disabled = false;
	}

	// 第二階段按下「確認修改」後，才將電話與電子信箱送到後端保存。
	async function confirmProfileSave() {
		if (!pendingProfile) return;
		const button = byId('admin-profile-save');
		const feedback = byId('admin-profile-feedback');
		button.disabled = true;
		feedback.textContent = '';
		try {
			const employee = await request(sessionApi + '/profile', {
				method: 'PUT',
				body: JSON.stringify(pendingProfile)
			});
			renderSession(employee);
			notificationsLoaded = false;
			pendingProfile = null;
			window.jQuery('#admin-profile-modal').modal('hide');
		} catch (error) {
			feedback.textContent = error.message;
		} finally {
			button.disabled = false;
		}
	}

    function createNotification(item) {
        const wrapper = document.createElement('div');
        wrapper.className = 'admin-notification-item';
        const symbol = document.createElement('span');
        symbol.className = 'admin-notification-symbol';
        const icon = document.createElement('i');
        icon.className = 'fas fa-history';
        symbol.appendChild(icon);
        const copy = document.createElement('div');
        copy.className = 'admin-notification-copy';
        const title = document.createElement('strong');
        title.textContent = (item.employeeName || '系統管理員') + ' · ' + (item.targetDisplay || '後台資料');
        const detail = document.createElement('span');
        detail.textContent = item.detailContent || '資料已更新';
        const time = document.createElement('time');
        time.textContent = formatDate(item.createdAt);
        copy.append(title, detail, time);
        wrapper.append(symbol, copy);
        return wrapper;
    }

    async function loadNotifications() {
        if (notificationsLoaded) return;
        const list = byId('admin-notification-list');
        list.replaceChildren();
        const loading = document.createElement('div');
        loading.className = 'admin-topbar-empty';
        loading.textContent = '載入中...';
        list.appendChild(loading);
        try {
            const result = await request(auditApi);
            const logs = result.logs || [];
            list.replaceChildren();
            if (logs.length === 0) {
                loading.textContent = '目前沒有異動通知';
                list.appendChild(loading);
            } else {
                logs.forEach((item) => list.appendChild(createNotification(item)));
            }
            notificationsLoaded = true;
        } catch (error) {
            loading.textContent = '通知暫時無法載入';
            list.replaceChildren(loading);
        }
    }

    async function savePassword(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (!form.reportValidity()) return;
        const feedback = byId('admin-password-feedback');
        const button = byId('admin-password-save');
        const newPassword = byId('admin-new-password').value;
        const confirmPassword = byId('admin-confirm-password').value;
        if (newPassword !== confirmPassword) {
            feedback.textContent = '新密碼與確認密碼不一致';
            return;
        }
        button.disabled = true;
        feedback.textContent = '';
        try {
            await request(sessionApi + '/password', {
                method: 'PUT',
                body: JSON.stringify({
                    currentPassword: byId('admin-current-password').value,
                    newPassword: newPassword,
                    confirmPassword: confirmPassword
                })
            });
            window.jQuery('#admin-password-modal').modal('hide');
            form.reset();
        } catch (error) {
            feedback.textContent = error.message;
        } finally {
            button.disabled = false;
        }
    }

    async function logout() {
        const button = byId('admin-logout-button');
        button.disabled = true;
        try {
            await request(sessionApi + '/logout', { method: 'POST' });
            window.location.assign(contextPath + '/admin/employees/login');
        } catch (error) {
            button.disabled = false;
        }
    }

    byId('admin-notification-toggle').addEventListener('click', loadNotifications);
    byId('admin-profile-button').addEventListener('click', openProfile);
    byId('admin-password-button').addEventListener('click', () => {
        if (!currentEmployee) return;
        byId('admin-password-form').reset();
        byId('admin-password-feedback').textContent = '';
        window.jQuery('#admin-password-modal').modal('show');
    });
    byId('admin-password-form').addEventListener('submit', savePassword);
	byId('admin-profile-form').addEventListener('submit', reviewProfile);
	byId('admin-profile-back').addEventListener('click', returnToProfileEdit);
	byId('admin-profile-save').addEventListener('click', confirmProfileSave);
    byId('admin-logout-button').addEventListener('click', logout);

    request(sessionApi).then(renderSession).catch(() => renderSession(null));
}());
