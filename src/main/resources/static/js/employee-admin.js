(function () {
    'use strict';

    const contextPath = window.location.pathname.split('/admin/employees')[0];
    const apiBase = contextPath + '/api/admin/employees';
    const state = {
        employees: [],
        positions: [],
        functions: [],
        auditLogs: [],
        auditPage: 0,
        auditTotalPages: 0,
        auditTotalElements: 0,
        selectedEmployeeId: null,
        permissionPositionFilter: "",
        permissionSearchKeyword: '',
        pendingStatus: null,
        pendingPositionStatus: null
    };

    const byId = (id) => document.getElementById(id);

    // 統一呼叫後端 API，並將後端的中文錯誤訊息交給畫面顯示。
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

    function setFeedback(message, type) {
        const feedback = byId('employee-feedback');
        feedback.textContent = message || '';
        feedback.className = 'employee-feedback' + (type ? ' is-' + type : '');
        feedback.hidden = !message;
        if (message) feedback.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    function setModalFeedback(modalId, message) {
        const feedback = document.querySelector('#' + modalId + ' [data-modal-feedback]');
        if (feedback) feedback.textContent = message || '';
    }

    function showModal(modalId) {
        window.jQuery('#' + modalId).modal('show');
    }

    function hideModal(modalId) {
        window.jQuery('#' + modalId).modal('hide');
    }

    function withBusy(button, busy, busyText) {
        if (!button) return;
        if (busy) {
            button.dataset.originalHtml = button.innerHTML;
            button.disabled = true;
            button.textContent = busyText;
        } else {
            button.disabled = false;
            if (button.dataset.originalHtml) button.innerHTML = button.dataset.originalHtml;
        }
    }

    function formatDate(value) {
        if (!value) return '尚無紀錄';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return value;
        return new Intl.DateTimeFormat('zh-TW', {
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit', hour12: false
        }).format(date);
    }

    function employeeInitial(name) {
        const cleanName = (name || '').trim();
        return cleanName ? cleanName.charAt(0).toUpperCase() : '?';
    }

    function makeAction(icon, label, action, employeeId, danger) {
        const button = createElement('button', 'employee-action' + (danger ? ' is-danger' : ''));
        button.type = 'button';
        button.title = label;
        button.setAttribute('aria-label', label);
        button.dataset.action = action;
        button.dataset.employeeId = String(employeeId);
        const symbol = createElement('i', icon);
        symbol.setAttribute('aria-hidden', 'true');
        button.appendChild(symbol);
        return button;
    }

    function makePositionAction(icon, label, action, positionId, danger) {
        const button = createElement('button', 'employee-action' + (danger ? ' is-danger' : ''));
        button.type = 'button';
        button.title = label;
        button.setAttribute('aria-label', label);
        button.dataset.positionAction = action;
        button.dataset.positionId = String(positionId);
        const symbol = createElement('i', icon);
        symbol.setAttribute('aria-hidden', 'true');
        button.appendChild(symbol);
        return button;
    }

    // 依搜尋字與狀態顯示員工，原始清單仍保留供權限配置選單使用。
    function renderEmployees() {
        const keyword = byId('employee-search').value.trim().toLowerCase();
        const status = byId('employee-status-filter').value;
        const filtered = state.employees.filter((employee) => {
            const searchable = [employee.employeeName, employee.employeeAccount, employee.employeeEmail,
                employee.positionName, employee.positionCode]
                .filter(Boolean).join(' ').toLowerCase();
            return (!keyword || searchable.includes(keyword))
                && (!status || String(employee.employeeStatus) === status);
        });

        const body = byId('employee-table-body');
        body.replaceChildren();
        filtered.forEach((employee) => {
            const row = document.createElement('tr');

            const personCell = document.createElement('td');
            const person = createElement('div', 'employee-person');
            person.appendChild(createElement('span', 'employee-avatar', employeeInitial(employee.employeeName)));
            const identity = document.createElement('div');
            identity.appendChild(createElement('div', 'employee-primary', employee.employeeName));
            identity.appendChild(createElement('div', 'employee-secondary', employee.employeeAccount));
            person.appendChild(identity);
            personCell.appendChild(person);

            const contactCell = document.createElement('td');
            const contact = createElement('div', 'employee-contact');
            contact.appendChild(createElement('div', 'employee-primary', employee.employeeEmail));
            contact.appendChild(createElement('div', 'employee-secondary', employee.employeePhone));
            contactCell.appendChild(contact);

            const positionCell = document.createElement('td');
            positionCell.appendChild(createElement('div', 'employee-primary', employee.positionName));
            positionCell.appendChild(createElement('div', 'employee-secondary', employee.positionCode));

            const permissionCell = document.createElement('td');
            permissionCell.appendChild(createElement('span', 'employee-permission-count',
                String(employee.permissionCount || 0) + ' 項'));

            const statusCell = document.createElement('td');
            const active = Number(employee.employeeStatus) === 1;
            statusCell.appendChild(createElement('span', 'employee-badge ' + (active ? 'is-active' : 'is-disabled'),
                active ? '正常' : '停用'));

            const timeCell = document.createElement('td');
            timeCell.appendChild(createElement('div', 'employee-primary', '建立 ' + formatDate(employee.createdAt)));
            timeCell.appendChild(createElement('div', 'employee-secondary', '登入 ' + formatDate(employee.lastLoginAt)));

            const actionCell = createElement('td', 'text-right');
            const actions = createElement('div', 'employee-actions');
            actions.appendChild(makeAction('fas fa-pen', '編輯員工', 'edit', employee.employeeId));
            actions.appendChild(makeAction('fas fa-key', '重設密碼', 'password', employee.employeeId));
            actions.appendChild(makeAction(active ? 'fas fa-user-slash' : 'fas fa-user-check',
                active ? '停用帳號' : '啟用帳號', 'status', employee.employeeId, active));
            actionCell.appendChild(actions);

            [personCell, contactCell, positionCell, permissionCell, statusCell, timeCell, actionCell]
                .forEach((cell) => row.appendChild(cell));
            body.appendChild(row);
        });

        byId('employee-empty').hidden = filtered.length !== 0;
        byId('employee-total-count').textContent = String(state.employees.length);
    }

    // 職位主檔獨立管理，員工表單只顯示目前啟用的職位。
    function renderPositions() {
        const keyword = byId('position-search').value.trim().toLowerCase();
        const status = byId('position-status-filter').value;
        const filtered = state.positions.filter((position) => {
            const searchable = [position.positionCode, position.positionName, position.positionDescription]
                .filter(Boolean).join(' ').toLowerCase();
            return (!keyword || searchable.includes(keyword))
                && (!status || String(position.positionStatus) === status);
        });

        const body = byId('position-table-body');
        body.replaceChildren();
        filtered.forEach((position) => {
            const row = document.createElement('tr');
            const codeCell = document.createElement('td');
            codeCell.appendChild(createElement('code', 'permission-code', position.positionCode));
            const nameCell = createElement('td', 'employee-primary', position.positionName);
            const descriptionCell = createElement('td', '', position.positionDescription || '—');
            const active = Number(position.positionStatus) === 1;
            const statusCell = document.createElement('td');
            statusCell.appendChild(createElement('span', 'employee-badge ' + (active ? 'is-active' : 'is-disabled'),
                active ? '啟用' : '停用'));
            const updatedCell = createElement('td', '', formatDate(position.updatedAt));
            const actionCell = createElement('td', 'text-right');
            const actions = createElement('div', 'employee-actions');
            actions.appendChild(makePositionAction('fas fa-pen', '編輯職位', 'edit', position.positionId));
            actions.appendChild(makePositionAction(active ? 'fas fa-ban' : 'fas fa-check',
                active ? '停用職位' : '啟用職位', 'status', position.positionId, active));
            actionCell.appendChild(actions);
            [codeCell, nameCell, descriptionCell, statusCell, updatedCell, actionCell]
                .forEach((cell) => row.appendChild(cell));
            body.appendChild(row);
        });
        byId('position-empty').hidden = filtered.length !== 0;
    }

    function renderPositionOptions(selectedId) {
        const select = byId('employee-position');
        select.replaceChildren();
        const prompt = createElement('option', '', '請選擇職位');
        prompt.value = '';
        select.appendChild(prompt);
        state.positions.filter((position) => Number(position.positionStatus) === 1)
            .forEach((position) => {
                const option = createElement('option', '', position.positionName + '（' + position.positionCode + '）');
                option.value = String(position.positionId);
                select.appendChild(option);
            });
        select.value = selectedId ? String(selectedId) : '';
    }

    function renderPermissionPositionFilter() {
        const select = byId('permission-position-filter');
        const pillsContainer = byId('permission-position-pills');
        if (!select) return;

        const currentVal = state.permissionPositionFilter;
        select.replaceChildren();

        const allOption = document.createElement('option');
        allOption.value = '';
        allOption.textContent = '全部職位部門（' + state.employees.length + ' 位）';
        select.appendChild(allOption);

        if (pillsContainer) pillsContainer.replaceChildren();

        if (pillsContainer) {
            const allPill = createElement('button', 'permission-pill' + (!currentVal ? ' is-active' : ''), '全部 (' + state.employees.length + ')');
            allPill.type = 'button';
            allPill.dataset.posFilter = '';
            pillsContainer.appendChild(allPill);
        }

        state.positions.forEach((position) => {
            const count = state.employees.filter((emp) => emp.positionId === position.positionId).length;
            const option = createElement('option', '', position.positionName + '（' + count + ' 位）');
            option.value = String(position.positionId);
            select.appendChild(option);

            if (pillsContainer) {
                const isActive = currentVal === String(position.positionId);
                const pill = createElement('button', 'permission-pill' + (isActive ? ' is-active' : ''), position.positionName + ' (' + count + ')');
                pill.type = 'button';
                pill.dataset.posFilter = String(position.positionId);
                pillsContainer.appendChild(pill);
            }
        });

        select.value = currentVal || '';
    }

    function renderEmployeeOptions() {
        const previous = state.selectedEmployeeId;
        if (!state.employees.length) {
            state.selectedEmployeeId = null;
        } else {
            const stillExists = state.employees.some((employee) => employee.employeeId === previous);
            state.selectedEmployeeId = stillExists ? previous : state.employees[0].employeeId;
        }

        renderPermissionPositionFilter();
        renderEmployeeSearchResults();
        renderSelectedEmployee();
    }

    // 權限配置名單：支援「職位部門下拉/標籤篩選」與「姓名/帳號關鍵字即時模糊搜尋」雙重篩選。
    function renderEmployeeSearchResults() {
        const keyword = (state.permissionSearchKeyword || '').trim().toLowerCase();
        const posFilter = state.permissionPositionFilter;
        const results = byId('permission-employee-results');
        const clearBtn = byId('permission-employee-search-clear');
        if (clearBtn) clearBtn.hidden = !keyword;
        results.replaceChildren();

        const matched = state.employees.filter((employee) => {
            if (posFilter && String(employee.positionId) !== posFilter) {
                return false;
            }
            if (keyword) {
                const searchable = [employee.employeeName, employee.employeeAccount, employee.employeeEmail,
                    employee.positionName, employee.positionCode]
                    .filter(Boolean).join(' ').toLowerCase();
                if (!searchable.includes(keyword)) return false;
            }
            return true;
        });

        const countEl = byId('permission-employee-count');
        if (countEl) {
            const labelPrefix = posFilter
                ? ((state.positions.find((p) => String(p.positionId) === posFilter)?.positionName || '職位') + '：')
                : (keyword ? '符合 ' : '共 ');
            countEl.textContent = labelPrefix + matched.length + ' 位';
        }

        // 若當前選取的員工不在篩選結果中，自動選中結果的第一位並載入細項權限
        if (matched.length > 0) {
            const isCurrentInMatched = matched.some((e) => e.employeeId === state.selectedEmployeeId);
            if (!isCurrentInMatched) {
                state.selectedEmployeeId = matched[0].employeeId;
                loadSelectedPermissions();
            }
        } else {
            state.selectedEmployeeId = null;
            renderSelectedEmployee();
            renderPermissionGroups([]);
        }

        if (!matched.length) {
            results.appendChild(createElement('p', 'permission-employee-no-result', '此職位或篩選條件下找不到員工'));
            return;
        }

        matched.forEach((employee) => {
            const selected = employee.employeeId === state.selectedEmployeeId;
            const button = createElement('button', 'permission-employee-result' + (selected ? ' is-selected' : ''));
            button.type = 'button';
            button.setAttribute('role', 'option');
            button.setAttribute('aria-selected', selected ? 'true' : 'false');
            button.dataset.pickerEmployeeId = String(employee.employeeId);

            const rowTop = createElement('div', 'permission-employee-row-top');
            rowTop.appendChild(createElement('span', 'permission-employee-name', employee.employeeName));
            rowTop.appendChild(createElement('span', 'permission-employee-role', employee.positionName || '未指定職位'));

            const rowBottom = createElement('div', 'permission-employee-row-bottom');
            rowBottom.appendChild(createElement('span', 'permission-employee-account', employee.employeeAccount));
            const countText = (employee.permissionCount !== undefined && employee.permissionCount !== null)
                ? (employee.permissionCount + ' 項權限') : '';
            rowBottom.appendChild(createElement('span', 'permission-employee-perms', countText));

            button.appendChild(rowTop);
            button.appendChild(rowBottom);
            results.appendChild(button);
        });
    }

    function renderSelectedEmployee() {
        const employee = state.employees.find((item) => item.employeeId === state.selectedEmployeeId);
        const nameEl = byId('permission-target-name');
        const roleEl = byId('permission-target-role');
        const saveBtn = byId('permission-save-button');
        if (!employee) {
            if (nameEl) nameEl.textContent = '未選擇員工';
            if (roleEl) roleEl.textContent = '--';
            if (saveBtn) saveBtn.disabled = true;
            return;
        }
        if (nameEl) nameEl.textContent = employee.employeeName + ' (' + employee.employeeAccount + ')';
        if (roleEl) roleEl.textContent = employee.positionName || '未指定職位';
        if (saveBtn) saveBtn.disabled = false;
    }

    function updatePermissionCount() {
        const checked = document.querySelectorAll('#permission-groups input[type="checkbox"]:checked').length;
        byId('permission-selection-count').textContent = checked + ' 項已選';
    }

    // 權限依資料庫群組呈現，同一群組可用「全選／清除」快速操作。
    function renderPermissionGroups(selectedIds) {
        const selected = new Set((selectedIds || []).map(Number));
        const groups = new Map();
        state.functions.forEach((permission) => {
            const groupName = permission.permissionGroup || '其他';
            if (!groups.has(groupName)) groups.set(groupName, []);
            groups.get(groupName).push(permission);
        });

        const container = byId('permission-groups');
        container.replaceChildren();
        groups.forEach((permissions, groupName) => {
            const group = createElement('section', 'permission-group');
            const heading = createElement('div', 'permission-group-heading');
            heading.appendChild(createElement('h3', '', groupName));
            const toggle = createElement('button', 'permission-group-toggle', '全選／清除');
            toggle.type = 'button';
            toggle.dataset.groupToggle = groupName;
            heading.appendChild(toggle);
            group.appendChild(heading);

            permissions.forEach((permission) => {
                const label = createElement('label', 'permission-option');
                const checkbox = document.createElement('input');
                checkbox.type = 'checkbox';
                checkbox.value = String(permission.permissionId);
                checkbox.checked = selected.has(Number(permission.permissionId));
                const text = document.createElement('span');
                text.appendChild(createElement('strong', '', permission.permissionName));
                text.appendChild(createElement('small', '', permission.permissionDescription || permission.permissionCode));
                label.appendChild(checkbox);
                label.appendChild(text);
                group.appendChild(label);
            });
            container.appendChild(group);
        });
        if (!state.functions.length) container.textContent = '目前沒有可分配的權限功能。';
        updatePermissionCount();
    }

    function renderFunctionGroupOptions() {
        const select = byId('function-group-filter');
        const current = select.value;
        const groups = Array.from(new Set(state.functions.map((item) => item.permissionGroup).filter(Boolean))).sort();
        select.replaceChildren();
        const all = createElement('option', '', '全部群組');
        all.value = '';
        select.appendChild(all);
        groups.forEach((group) => {
            const option = createElement('option', '', group);
            option.value = group;
            select.appendChild(option);
        });
        select.value = groups.includes(current) ? current : '';
    }

    function renderFunctions() {
        const keyword = byId('function-search').value.trim().toLowerCase();
        const group = byId('function-group-filter').value;
        const filtered = state.functions.filter((permission) => {
            const searchable = [permission.permissionCode, permission.permissionName, permission.permissionDescription]
                .filter(Boolean).join(' ').toLowerCase();
            return (!keyword || searchable.includes(keyword))
                && (!group || permission.permissionGroup === group);
        });

        const body = byId('function-table-body');
        body.replaceChildren();
        filtered.forEach((permission) => {
            const row = document.createElement('tr');
            const codeCell = document.createElement('td');
            codeCell.appendChild(createElement('code', 'permission-code', permission.permissionCode));
            const nameCell = createElement('td', 'employee-primary', permission.permissionName);
            const descriptionCell = createElement('td', '', permission.permissionDescription || '—');
            const groupCell = createElement('td', '', permission.permissionGroup);
            const actionCell = createElement('td', 'text-right');
            const button = createElement('button', 'employee-action');
            button.type = 'button';
            button.title = '編輯權限功能';
            button.setAttribute('aria-label', '編輯權限功能');
            button.dataset.functionId = String(permission.permissionId);
            const icon = createElement('i', 'fas fa-pen');
            icon.setAttribute('aria-hidden', 'true');
            button.appendChild(icon);
            actionCell.appendChild(button);
            [codeCell, nameCell, descriptionCell, groupCell, actionCell].forEach((cell) => row.appendChild(cell));
            body.appendChild(row);
        });
        byId('function-result-count').textContent = filtered.length + ' 項';
        byId('function-empty').hidden = filtered.length !== 0;
    }

    const auditModuleLabels = {
        EMPLOYEE: '員工帳號', POSITION: '職位管理', EMPLOYEE_PERMISSION: '權限配置',
        PERMISSION_FUNCTION: '權限功能', 員工帳號: '員工帳號', 員工: '員工帳號',
        職位管理: '職位管理', 職位: '職位管理', 權限配置: '權限配置', 員工權限: '權限配置',
        權限功能: '權限功能'
    };
    const auditActionLabels = {
        CREATE: '新增', UPDATE: '修改', STATUS: '狀態變更', PASSWORD: '重設密碼', ASSIGN: '權限配置',
        新增: '新增', 修改: '修改', 啟用: '啟用', 停用: '停用', 狀態變更: '狀態變更',
        重設密碼: '重設密碼', 指派: '權限配置', 權限配置: '權限配置'
    };

    // 更改紀錄採後端分頁，避免紀錄累積後一次下載大量資料。
    function renderAuditLogs() {
        const body = byId('audit-table-body');
        body.replaceChildren();
        state.auditLogs.forEach((log) => {
            const row = document.createElement('tr');
            const time = createElement('td', 'audit-time', formatDate(log.createdAt));
            const actor = document.createElement('td');
            actor.appendChild(createElement('div', 'employee-primary', log.employeeName || '系統管理員'));
            actor.appendChild(createElement('div', 'employee-secondary', log.employeeId ? '員工 #' + log.employeeId : '尚未連結員工登入'));
            const type = document.createElement('td');
            type.appendChild(createElement('span', 'audit-module', auditModuleLabels[log.targetModule] || log.targetModule));
            type.appendChild(createElement('span', 'audit-action', auditActionLabels[log.actionType] || log.actionType));
            const target = document.createElement('td');
            target.appendChild(createElement('div', 'employee-primary', log.targetDisplay));
            target.appendChild(createElement('div', 'employee-secondary', '#' + log.targetId));
            const detail = createElement('td', 'audit-detail', log.detailContent || '—');
            const ip = createElement('td', 'audit-ip', log.ipAddress || '—');
            [time, actor, type, target, detail, ip].forEach((cell) => row.appendChild(cell));
            body.appendChild(row);
        });
        byId('audit-empty').hidden = state.auditLogs.length !== 0;
        const visiblePage = state.auditTotalPages === 0 ? 0 : state.auditPage + 1;
        byId('audit-page-summary').textContent = '第 ' + visiblePage + ' / ' + state.auditTotalPages
            + ' 頁，共 ' + state.auditTotalElements + ' 筆';
        byId('audit-page-previous').disabled = state.auditPage <= 0;
        byId('audit-page-next').disabled = state.auditTotalPages === 0
            || state.auditPage >= state.auditTotalPages - 1;
    }

    async function loadAuditLogs() {
        const parameters = new URLSearchParams();
        const keyword = byId('audit-search').value.trim();
        const module = byId('audit-module-filter').value;
        const action = byId('audit-action-filter').value;
        const startDate = byId('audit-start-date').value;
        const endDate = byId('audit-end-date').value;
        if (keyword) parameters.set('keyword', keyword);
        if (module) parameters.set('module', module);
        if (action) parameters.set('action', action);
        if (startDate) parameters.set('startDate', startDate);
        if (endDate) parameters.set('endDate', endDate);
        parameters.set('page', String(state.auditPage));
        parameters.set('size', '20');
        try {
            const result = await request('/audit-logs?' + parameters.toString());
            state.auditLogs = result.logs || [];
            state.auditPage = Number(result.page || 0);
            state.auditTotalPages = Number(result.totalPages || 0);
            state.auditTotalElements = Number(result.totalElements || 0);
            renderAuditLogs();
        } catch (error) {
            state.auditLogs = [];
            state.auditTotalPages = 0;
            state.auditTotalElements = 0;
            renderAuditLogs();
            setFeedback(error.message, 'error');
        }
    }

    async function loadEmployees(preserveSelection) {
        const selected = preserveSelection ? state.selectedEmployeeId : null;
        state.employees = await request('');
        state.selectedEmployeeId = selected;
        renderEmployees();
        renderEmployeeOptions();
    }

    async function loadPositions() {
        state.positions = await request('/positions');
        renderPositions();
        renderPositionOptions(null);
    }

    async function loadFunctions() {
        state.functions = await request('/permission-functions');
        renderFunctionGroupOptions();
        renderFunctions();
    }

    async function loadSelectedPermissions() {
        renderSelectedEmployee();
        if (state.selectedEmployeeId === null) {
            renderPermissionGroups([]);
            return;
        }
        try {
            const result = await request('/' + state.selectedEmployeeId + '/permissions');
            renderPermissionGroups(result.permissionIds);
        } catch (error) {
            renderPermissionGroups([]);
            setFeedback(error.message, 'error');
        }
    }

    function openEmployeeForm(employee) {
        const editing = Boolean(employee);
        const form = byId('employee-form');
        form.reset();
        form.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));
        byId('employee-id').value = editing ? employee.employeeId : '';
        byId('employee-form-title').textContent = editing ? '編輯員工' : '新增員工';
        byId('employee-name').value = editing ? employee.employeeName : '';
        byId('employee-account').value = editing ? employee.employeeAccount : '';
        byId('employee-phone').value = editing ? employee.employeePhone : '';
        byId('employee-email').value = editing ? employee.employeeEmail : '';
        renderPositionOptions(editing ? employee.positionId : null);
        byId('employee-password-group').hidden = editing;
        byId('employee-password').required = !editing;
        setModalFeedback('employee-form-modal', '');
        showModal('employee-form-modal');
    }

    function openPositionForm(position) {
        const editing = Boolean(position);
        byId('position-form').reset();
        byId('position-id').value = editing ? position.positionId : '';
        byId('position-form-title').textContent = editing ? '編輯職位' : '新增職位';
        byId('position-code').value = editing ? position.positionCode : '';
        byId('position-code').readOnly = editing;
        byId('position-name').value = editing ? position.positionName : '';
        byId('position-description').value = editing ? (position.positionDescription || '') : '';
        setModalFeedback('position-form-modal', '');
        showModal('position-form-modal');
    }

    function openPositionStatusForm(position) {
        const active = Number(position.positionStatus) === 1;
        state.pendingPositionStatus = { positionId: position.positionId, status: active ? 0 : 1 };
        byId('position-status-modal-title').textContent = active ? '停用員工職位' : '啟用員工職位';
        byId('position-status-modal-message').textContent = active
            ? '確定要停用「' + position.positionName + '」嗎？仍有員工使用時系統不會允許停用。'
            : '確定要啟用「' + position.positionName + '」嗎？';
        const confirm = byId('position-status-confirm-button');
        confirm.className = 'btn ' + (active ? 'btn-danger' : 'btn-success');
        confirm.textContent = active ? '停用職位' : '啟用職位';
        setModalFeedback('position-status-modal', '');
        showModal('position-status-modal');
    }

    function openPasswordForm(employee) {
        byId('password-form').reset();
        byId('password-employee-id').value = employee.employeeId;
        byId('password-employee-name').textContent = employee.employeeName + '（' + employee.employeeAccount + '）';
        setModalFeedback('password-modal', '');
        showModal('password-modal');
    }

    function openStatusForm(employee) {
        const active = Number(employee.employeeStatus) === 1;
        state.pendingStatus = { employeeId: employee.employeeId, status: active ? 0 : 1 };
        byId('status-modal-title').textContent = active ? '停用員工帳號' : '啟用員工帳號';
        byId('status-modal-message').textContent = active
            ? '確定要停用「' + employee.employeeName + '」的帳號嗎？停用後將無法登入。'
            : '確定要啟用「' + employee.employeeName + '」的帳號嗎？';
        const confirm = byId('status-confirm-button');
        confirm.className = 'btn ' + (active ? 'btn-danger' : 'btn-success');
        confirm.textContent = active ? '停用帳號' : '啟用帳號';
        setModalFeedback('status-modal', '');
        showModal('status-modal');
    }

    function openFunctionForm(permission) {
        byId('function-form').reset();
        byId('function-id').value = permission.permissionId;
        byId('function-code').value = permission.permissionCode;
        byId('function-name').value = permission.permissionName;
        byId('function-description').value = permission.permissionDescription || '';
        byId('function-group').value = permission.permissionGroup;
        setModalFeedback('function-modal', '');
        showModal('function-modal');
    }

    function bindEvents() {
        document.querySelectorAll('.employee-tab').forEach((tab) => {
            tab.addEventListener('click', () => {
                document.querySelectorAll('.employee-tab').forEach((item) => item.classList.remove('is-active'));
                document.querySelectorAll('.employee-panel').forEach((panel) => {
                    const active = panel.dataset.panel === tab.dataset.tab;
                    panel.classList.toggle('is-active', active);
                    panel.hidden = !active;
                });
                tab.classList.add('is-active');
                if (tab.dataset.tab === 'assignments') loadSelectedPermissions();
                if (tab.dataset.tab === 'audit') loadAuditLogs();
            });
        });

        byId('employee-search').addEventListener('input', renderEmployees);
        byId('employee-status-filter').addEventListener('change', renderEmployees);
        byId('position-search').addEventListener('input', renderPositions);
        byId('position-status-filter').addEventListener('change', renderPositions);
        byId('function-search').addEventListener('input', renderFunctions);
        byId('function-group-filter').addEventListener('change', renderFunctions);
        let auditSearchTimer = null;
        byId('audit-search').addEventListener('input', () => {
            window.clearTimeout(auditSearchTimer);
            auditSearchTimer = window.setTimeout(() => { state.auditPage = 0; loadAuditLogs(); }, 250);
        });
        ['audit-module-filter', 'audit-action-filter', 'audit-start-date', 'audit-end-date'].forEach((id) => {
            byId(id).addEventListener('change', () => { state.auditPage = 0; loadAuditLogs(); });
        });
        byId('audit-refresh-button').addEventListener('click', loadAuditLogs);
        const auditResetBtn = byId('audit-reset-button');
        if (auditResetBtn) {
            auditResetBtn.addEventListener('click', () => {
                byId('audit-search').value = '';
                byId('audit-module-filter').value = '';
                byId('audit-action-filter').value = '';
                byId('audit-start-date').value = '';
                byId('audit-end-date').value = '';
                state.auditPage = 0;
                loadAuditLogs();
            });
        }
        byId('audit-page-previous').addEventListener('click', () => {
            if (state.auditPage > 0) { state.auditPage -= 1; loadAuditLogs(); }
        });
        byId('audit-page-next').addEventListener('click', () => {
            if (state.auditPage < state.auditTotalPages - 1) { state.auditPage += 1; loadAuditLogs(); }
        });
        byId('employee-add-button').addEventListener('click', () => openEmployeeForm(null));
        byId('position-add-button').addEventListener('click', () => openPositionForm(null));

        byId('employee-table-body').addEventListener('click', (event) => {
            const button = event.target.closest('[data-action]');
            if (!button) return;
            const employee = state.employees.find((item) => item.employeeId === Number(button.dataset.employeeId));
            if (!employee) return;
            if (button.dataset.action === 'edit') openEmployeeForm(employee);
            if (button.dataset.action === 'password') openPasswordForm(employee);
            if (button.dataset.action === 'status') openStatusForm(employee);
        });

        byId('function-table-body').addEventListener('click', (event) => {
            const button = event.target.closest('[data-function-id]');
            if (!button) return;
            const permission = state.functions.find((item) => item.permissionId === Number(button.dataset.functionId));
            if (permission) openFunctionForm(permission);
        });

        byId('position-table-body').addEventListener('click', (event) => {
            const button = event.target.closest('[data-position-action]');
            if (!button) return;
            const position = state.positions.find((item) => item.positionId === Number(button.dataset.positionId));
            if (!position) return;
            if (button.dataset.positionAction === 'edit') openPositionForm(position);
            if (button.dataset.positionAction === 'status') openPositionStatusForm(position);
        });

        const posFilterSelect = byId('permission-position-filter');
        if (posFilterSelect) {
            posFilterSelect.addEventListener('change', (e) => {
                state.permissionPositionFilter = e.target.value;
                renderPermissionPositionFilter();
                renderEmployeeSearchResults();
            });
        }

        const pillsContainer = byId('permission-position-pills');
        if (pillsContainer) {
            pillsContainer.addEventListener('click', (e) => {
                const pill = e.target.closest('[data-pos-filter]');
                if (!pill) return;
                state.permissionPositionFilter = pill.dataset.posFilter || '';
                renderPermissionPositionFilter();
                renderEmployeeSearchResults();
            });
        }

        byId('permission-employee-search').addEventListener('input', (event) => {
            state.permissionSearchKeyword = event.target.value;
            renderEmployeeSearchResults();
        });
        byId('permission-employee-search-clear').addEventListener('click', () => {
            state.permissionSearchKeyword = '';
            byId('permission-employee-search').value = '';
            renderEmployeeSearchResults();
            byId('permission-employee-search').focus();
        });
        byId('permission-employee-results').addEventListener('click', (event) => {
            const button = event.target.closest('[data-picker-employee-id]');
            if (!button) return;
            state.selectedEmployeeId = Number(button.dataset.pickerEmployeeId);
            // 保留搜尋字與結果清單，方便管理員連續查看同一搜尋結果中的不同員工。
            byId('permission-employee-search').value = state.permissionSearchKeyword;
            renderEmployeeSearchResults();
            loadSelectedPermissions();
        });

        byId('permission-groups').addEventListener('change', (event) => {
            if (event.target.matches('input[type="checkbox"]')) updatePermissionCount();
        });

        byId('permission-groups').addEventListener('click', (event) => {
            const toggle = event.target.closest('[data-group-toggle]');
            if (!toggle) return;
            const group = toggle.closest('.permission-group');
            const checkboxes = Array.from(group.querySelectorAll('input[type="checkbox"]'));
            const shouldCheck = checkboxes.some((checkbox) => !checkbox.checked);
            checkboxes.forEach((checkbox) => { checkbox.checked = shouldCheck; });
            updatePermissionCount();
        });

        byId('employee-form').addEventListener('submit', saveEmployee);
        byId('employee-form').addEventListener('input', (event) => {
            if (event.target.classList.contains('is-invalid')) {
                event.target.classList.remove('is-invalid');
                setModalFeedback('employee-form-modal', '');
            }
        });
        byId('position-form').addEventListener('submit', savePosition);
        byId('password-form').addEventListener('submit', savePassword);
        byId('status-confirm-button').addEventListener('click', saveStatus);
        byId('position-status-confirm-button').addEventListener('click', savePositionStatus);
        byId('permission-save-button').addEventListener('click', savePermissions);
        byId('function-form').addEventListener('submit', saveFunction);
    }

    async function saveEmployee(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (!form.reportValidity()) return;
        const employeeId = byId('employee-id').value;
        const body = {
            employeeName: byId('employee-name').value,
            employeeAccount: byId('employee-account').value,
            employeePhone: byId('employee-phone').value,
            employeeEmail: byId('employee-email').value,
            positionId: Number(byId('employee-position').value)
        };
        if (!employeeId) body.employeePassword = byId('employee-password').value;
        const button = byId('employee-form-save');
        withBusy(button, true, '儲存中…');
        setModalFeedback('employee-form-modal', '');
        form.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));
        try {
            await request(employeeId ? '/' + employeeId : '', {
                method: employeeId ? 'PUT' : 'POST', body: JSON.stringify(body)
            });
            hideModal('employee-form-modal');
            await loadEmployees(true);
            setFeedback(employeeId ? '員工資料已更新。' : '員工帳號已新增。', 'success');
        } catch (error) {
            setModalFeedback('employee-form-modal', error.message);
            const msg = error.message || '';
            if (msg.includes('帳號')) {
                byId('employee-account').classList.add('is-invalid');
                byId('employee-account').focus();
            } else if (msg.includes('信箱')) {
                byId('employee-email').classList.add('is-invalid');
                byId('employee-email').focus();
            } else if (msg.includes('電話')) {
                byId('employee-phone').classList.add('is-invalid');
                byId('employee-phone').focus();
            } else if (msg.includes('姓名')) {
                byId('employee-name').classList.add('is-invalid');
                byId('employee-name').focus();
            } else if (msg.includes('密碼')) {
                const pwd = byId('employee-password');
                if (pwd && !pwd.hidden) { pwd.classList.add('is-invalid'); pwd.focus(); }
            } else if (msg.includes('職位')) {
                byId('employee-position').classList.add('is-invalid');
                byId('employee-position').focus();
            }
        } finally {
            withBusy(button, false);
        }
    }

    async function savePosition(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (!form.reportValidity()) return;
        const positionId = byId('position-id').value;
        const body = {
            positionCode: byId('position-code').value,
            positionName: byId('position-name').value,
            positionDescription: byId('position-description').value
        };
        const button = byId('position-form-save');
        withBusy(button, true, '儲存中…');
        setModalFeedback('position-form-modal', '');
        try {
            await request(positionId ? '/positions/' + positionId : '/positions', {
                method: positionId ? 'PUT' : 'POST', body: JSON.stringify(body)
            });
            hideModal('position-form-modal');
            await loadPositions();
            await loadEmployees(true);
            setFeedback(positionId ? '職位資料已更新。' : '員工職位已新增。', 'success');
        } catch (error) {
            setModalFeedback('position-form-modal', error.message);
        } finally {
            withBusy(button, false);
        }
    }

    async function savePassword(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (!form.reportValidity()) return;
        const button = form.querySelector('button[type="submit"]');
        withBusy(button, true, '更新中…');
        setModalFeedback('password-modal', '');
        try {
            await request('/' + byId('password-employee-id').value + '/password', {
                method: 'PUT', body: JSON.stringify({ employeePassword: byId('new-employee-password').value })
            });
            hideModal('password-modal');
            setFeedback('員工密碼已重設。', 'success');
        } catch (error) {
            setModalFeedback('password-modal', error.message);
        } finally {
            withBusy(button, false);
        }
    }

    async function saveStatus() {
        if (!state.pendingStatus) return;
        const button = byId('status-confirm-button');
        withBusy(button, true, '處理中…');
        setModalFeedback('status-modal', '');
        try {
            await request('/' + state.pendingStatus.employeeId + '/status', {
                method: 'PUT', body: JSON.stringify({ employeeStatus: state.pendingStatus.status })
            });
            hideModal('status-modal');
            await loadEmployees(true);
            setFeedback(state.pendingStatus.status === 1 ? '員工帳號已啟用。' : '員工帳號已停用。', 'success');
            state.pendingStatus = null;
        } catch (error) {
            setModalFeedback('status-modal', error.message);
        } finally {
            withBusy(button, false);
        }
    }

    async function savePositionStatus() {
        if (!state.pendingPositionStatus) return;
        const pending = state.pendingPositionStatus;
        const button = byId('position-status-confirm-button');
        withBusy(button, true, '處理中…');
        setModalFeedback('position-status-modal', '');
        try {
            await request('/positions/' + pending.positionId + '/status', {
                method: 'PUT', body: JSON.stringify({ positionStatus: pending.status })
            });
            hideModal('position-status-modal');
            await loadPositions();
            setFeedback(pending.status === 1 ? '員工職位已啟用。' : '員工職位已停用。', 'success');
            state.pendingPositionStatus = null;
        } catch (error) {
            setModalFeedback('position-status-modal', error.message);
        } finally {
            withBusy(button, false);
        }
    }

    async function savePermissions() {
        if (state.selectedEmployeeId === null) return;
        const permissionIds = Array.from(document.querySelectorAll('#permission-groups input:checked'))
            .map((checkbox) => Number(checkbox.value));
        const button = byId('permission-save-button');
        withBusy(button, true, '儲存中…');
        try {
            await request('/' + state.selectedEmployeeId + '/permissions', {
                method: 'PUT', body: JSON.stringify({ permissionIds: permissionIds })
            });
            await loadEmployees(true);
            await loadSelectedPermissions();
            setFeedback('員工權限已儲存。', 'success');
        } catch (error) {
            setFeedback(error.message, 'error');
        } finally {
            withBusy(button, false);
        }
    }

    async function saveFunction(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (!form.reportValidity()) return;
        const button = form.querySelector('button[type="submit"]');
        withBusy(button, true, '儲存中…');
        setModalFeedback('function-modal', '');
        try {
            await request('/permission-functions/' + byId('function-id').value, {
                method: 'PUT',
                body: JSON.stringify({
                    permissionName: byId('function-name').value,
                    permissionDescription: byId('function-description').value,
                    permissionGroup: byId('function-group').value
                })
            });
            hideModal('function-modal');
            await loadFunctions();
            setFeedback('權限功能資料已更新。', 'success');
        } catch (error) {
            setModalFeedback('function-modal', error.message);
        } finally {
            withBusy(button, false);
        }
    }

    document.addEventListener('DOMContentLoaded', async () => {
        bindEvents();
		// 右上角通知的「查看全部」會帶入 tab=audit，直接開啟更改紀錄頁籤。
		const requestedTab = new URLSearchParams(window.location.search).get('tab');
		const requestedButton = requestedTab
			? document.querySelector('.employee-tab[data-tab="' + requestedTab + '"]') : null;
		if (requestedButton) requestedButton.click();
        try {
            await loadPositions();
            await Promise.all([loadEmployees(false), loadFunctions()]);
            renderPermissionGroups([]);
        } catch (error) {
            setFeedback(error.message, 'error');
        }
    });
}());
