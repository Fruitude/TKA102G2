(function () {
    'use strict';

    const form = document.getElementById('employee-login-form');
    if (!form) return;
    const contextPath = window.location.pathname.split('/admin')[0];
    const feedback = document.getElementById('employee-login-feedback');
    const button = document.getElementById('employee-login-submit');
    const forgotLink = document.getElementById('employee-forgot-link');
    const forgotPanel = document.getElementById('employee-forgot-panel');
    const forgotForm = document.getElementById('employee-forgot-form');
    const forgotInput = document.getElementById('employee-forgot-identifier');
    const forgotFeedback = document.getElementById('employee-forgot-feedback');
    const forgotSubmit = document.getElementById('employee-forgot-submit');
    const forgotBack = document.getElementById('employee-forgot-back');

    forgotLink.addEventListener('click', () => {
        form.hidden = true;
        forgotPanel.hidden = false;
        forgotInput.focus();
    });

    forgotBack.addEventListener('click', () => {
        forgotPanel.hidden = true;
        form.hidden = false;
        forgotFeedback.textContent = '';
    });

    forgotForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (!forgotForm.reportValidity()) return;
        forgotSubmit.disabled = true;
        forgotFeedback.textContent = '';
        try {
            const response = await fetch(contextPath + '/api/admin/employees/password/forgot', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ identifier: forgotInput.value.trim() })
            });
            const body = await response.json();
            if (!response.ok) throw new Error(body.message || '寄信失敗，請稍後再試');
            forgotFeedback.textContent = body.message;
            forgotFeedback.className = 'admin-login-feedback text-success';
        } catch (error) {
            forgotFeedback.textContent = error.message;
            forgotFeedback.className = 'admin-login-feedback text-danger';
        } finally {
            forgotSubmit.disabled = false;
        }
    });

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        button.disabled = true;
        feedback.textContent = '';
        try {
            const response = await fetch(contextPath + '/api/admin/session/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    account: document.getElementById('employee-login-account').value.trim(),
                    password: document.getElementById('employee-login-password').value
                })
            });
            const body = await response.json();
            if (!response.ok) throw new Error(body.message || '登入失敗');
            const requestedPage = new URLSearchParams(window.location.search).get('next');
            const adminRoot = contextPath + '/admin';
            // 只接受本站後台路徑，避免 next 參數被利用來導向外部網站。
            const allowedRoots = [adminRoot, contextPath + '/product/', contextPath + '/productcategory/',
                contextPath + '/productsku/', contextPath + '/productimage/'];
            const destination = requestedPage && allowedRoots.some((root) => requestedPage.startsWith(root))
                ? requestedPage : adminRoot;
            window.location.assign(destination);
        } catch (error) {
            feedback.textContent = error.message;
            button.disabled = false;
        }
    });
}());
