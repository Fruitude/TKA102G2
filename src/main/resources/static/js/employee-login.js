(function () {
    'use strict';

    const form = document.getElementById('employee-login-form');
    if (!form) return;
    const contextPath = window.location.pathname.split('/admin')[0];
    const feedback = document.getElementById('employee-login-feedback');
    const button = document.getElementById('employee-login-submit');

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
            window.location.assign(contextPath + '/admin');
        } catch (error) {
            feedback.textContent = error.message;
            button.disabled = false;
        }
    });
}());
