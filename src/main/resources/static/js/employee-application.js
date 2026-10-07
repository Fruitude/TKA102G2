(function () {
    'use strict';

    const form = document.getElementById('employee-application-form');
    if (!form) return;
    const contextPath = window.location.pathname.split('/admin')[0];
    const feedback = document.getElementById('employee-application-feedback');
    const button = document.getElementById('employee-application-submit');
    const positionSelect = document.getElementById('application-position');

    // 申請頁只載入啟用職位，避免申請人選到已停用的職位。
    fetch(contextPath + '/api/admin/employees/positions', { headers: { 'Accept': 'application/json' } })
        .then((response) => response.json())
        .then((positions) => {
            positionSelect.replaceChildren(new Option('請選擇申請職位', ''));
            positions.filter((position) => Number(position.positionStatus) === 1).forEach((position) => {
                positionSelect.appendChild(new Option(position.positionName + '（' + position.positionCode + '）', position.positionId));
            });
        })
        .catch(() => {
            positionSelect.replaceChildren(new Option('目前無法載入職位', ''));
        });

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        const password = document.getElementById('application-password').value;
        const confirmPassword = document.getElementById('application-password-confirm').value;
        if (password !== confirmPassword) {
            feedback.textContent = '兩次輸入的密碼不一致';
            feedback.className = 'employee-application-feedback';
            return;
        }
        button.disabled = true;
        feedback.textContent = '';
        try {
            const response = await fetch(contextPath + '/api/admin/session/applications', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                body: JSON.stringify({
                    employeeName: document.getElementById('application-name').value.trim(),
                    employeeAccount: document.getElementById('application-account').value.trim(),
                    employeePassword: password,
                    employeePhone: document.getElementById('application-phone').value.trim(),
                    employeeEmail: document.getElementById('application-email').value.trim(),
                    positionId: Number(positionSelect.value)
                })
            });
            const body = await response.json();
            if (!response.ok) throw new Error(body.message || '申請失敗');
            form.reset();
            feedback.textContent = body.message;
            feedback.className = 'employee-application-feedback employee-application-success';
        } catch (error) {
            feedback.textContent = error.message;
            feedback.className = 'employee-application-feedback';
        } finally {
            button.disabled = false;
        }
    });
}());
