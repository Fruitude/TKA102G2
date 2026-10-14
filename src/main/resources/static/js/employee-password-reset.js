(function () {
    'use strict';

    const contextPath = window.location.pathname.split('/admin')[0];
    const token = new URLSearchParams(window.location.search).get('resetToken') || '';
    const validation = document.getElementById('employee-reset-validation');
    const form = document.getElementById('employee-reset-form');
    const feedback = document.getElementById('employee-reset-feedback');
    const submit = document.getElementById('employee-reset-submit');
    const success = document.getElementById('employee-reset-success');

    async function request(url, options) {
        const response = await fetch(url, Object.assign({
            headers: { 'Content-Type': 'application/json' }
        }, options || {}));
        const body = await response.json();
        if (!response.ok) throw new Error(body.message || '操作失敗，請稍後再試');
        return body;
    }

    async function validateToken() {
        if (!token) {
            validation.textContent = '密碼設定連結不完整，請聯絡管理人員重新寄送。';
            validation.className = 'text-danger mb-0';
            return;
        }
        try {
            await request(contextPath + '/api/admin/employees/password/reset/validate?token='
                + encodeURIComponent(token));
            validation.hidden = true;
            form.hidden = false;
        } catch (error) {
            validation.textContent = error.message;
            validation.className = 'text-danger mb-0';
        }
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        const newPassword = document.getElementById('employee-reset-password').value;
        const confirmPassword = document.getElementById('employee-reset-confirm-password').value;
        feedback.textContent = '';
        if (newPassword !== confirmPassword) {
            feedback.textContent = '新密碼與確認密碼不一致';
            return;
        }
        submit.disabled = true;
        submit.textContent = '更新中…';
        try {
            await request(contextPath + '/api/admin/employees/password/reset', {
                method: 'POST',
                body: JSON.stringify({
                    token: token,
                    newPassword: newPassword,
                    confirmPassword: confirmPassword
                })
            });
            form.hidden = true;
            success.hidden = false;
        } catch (error) {
            feedback.textContent = error.message;
            submit.disabled = false;
            submit.innerHTML = '<i class="fas fa-key mr-1" aria-hidden="true"></i>確認更新密碼';
        }
    });

    validateToken();
}());
