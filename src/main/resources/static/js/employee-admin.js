(function () {
    'use strict';

    const contextPath = window.location.pathname.split('/admin/employees')[0];
    const apiBase = contextPath + '/api/admin/employees';
    const state = {
        employees: [],
        applications: [],
        positions: [],
        functions: [],
        permissionCoverage: null,
        auditLogs: [],
        auditPage: 0,
        auditTotalPages: 0,
        auditTotalElements: 0,
        selectedEmployeeId: null,
        selectedPositionId: null,
        permissionPositionFilter: "",
        permissionSearchKeyword: '',
        pendingStatus: null,
        pendingPositionStatus: null
    };

    const byId = (id) => document.getElementById(id);

    // 權限群組名稱來自資料庫（與後台左側選單一致），這裡只決定顯示順序：依左側選單由上到下，
    // 不在清單內的群組（例如目前沒有選單的測驗管理）排在最後。
    const GROUP_ORDER = ['後台帳號管理', '會員管理', '購物金審核', '評論管理', '訂單管理', '活動管理', '資訊管理',
        '商品管理', '客服聊天室', '進銷存系統', '會計作業系統', '物流系統'];

    function compareGroupNames(a, b) {
        const order = (name) => {
            const index = GROUP_ORDER.indexOf(name);
            return index < 0 ? GROUP_ORDER.length : index;
        };
        return order(a) - order(b) || a.localeCompare(b, 'zh-Hant');
    }

    // 依左側選單順序排列群組；群組內的功能維持原本的順序。
    function sortedGroups(groups) {
        return new Map(Array.from(groups.entries()).sort((a, b) => compareGroupNames(a[0], b[0])));
    }

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

	function maskEmail(email) {
		const parts = String(email || '').split('@');
		if (parts.length !== 2) return '已登記信箱';
		return (parts[0] ? parts[0].charAt(0) : '*') + '***@' + parts[1];
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
            const permissionCount = createElement('button', 'employee-permission-count',
                String(employee.permissionCount || 0) + ' 個群組');
            permissionCount.type = 'button';
            permissionCount.dataset.action = 'view-permissions';
            permissionCount.dataset.employeeId = String(employee.employeeId);
            permissionCount.title = '查看' + employee.employeeName + '擁有的權限群組';
            permissionCount.setAttribute('aria-label', '查看' + employee.employeeName + '擁有的權限群組');
            permissionCell.appendChild(permissionCount);

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
            actions.appendChild(makeAction('fas fa-key', '寄送密碼設定信', 'password', employee.employeeId));
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

    // 顯示待審核申請；核准後同一筆 employee 資料會出現在員工帳號頁。
    function renderApplications() {
        const body = byId('employee-application-table-body');
        body.replaceChildren();
        state.applications.forEach((application) => {
            const row = document.createElement('tr');
            const personCell = document.createElement('td');
            const person = createElement('div', 'employee-person');
            person.appendChild(createElement('span', 'employee-avatar', employeeInitial(application.employeeName)));
            const identity = document.createElement('div');
            identity.appendChild(createElement('div', 'employee-primary', application.employeeName));
            identity.appendChild(createElement('div', 'employee-secondary', application.employeeAccount));
            person.appendChild(identity);
            personCell.appendChild(person);
            const contactCell = document.createElement('td');
            contactCell.appendChild(createElement('div', 'employee-primary', application.employeeEmail));
            contactCell.appendChild(createElement('div', 'employee-secondary', application.employeePhone));
            const positionCell = document.createElement('td');
            const positionSelect = createElement('select', 'form-control employee-application-position');
            positionSelect.setAttribute('aria-label', application.employeeName + '的核准職位');
            const activePositions = state.positions.filter((position) => Number(position.positionStatus) === 1);
            positionSelect.appendChild(new Option('請選擇核准職位', ''));
            activePositions.forEach((position) => {
                const option = new Option(position.positionName + '（' + position.positionCode + '）', position.positionId);
                option.selected = Number(position.positionId) === Number(application.positionId);
                positionSelect.appendChild(option);
            });
            positionCell.appendChild(positionSelect);
            positionCell.appendChild(createElement('div', 'employee-secondary employee-application-original-position',
                '原申請：' + application.positionName + '（' + application.positionCode + '）'));
            const createdCell = createElement('td', '', formatDate(application.createdAt));
            const actionCell = createElement('td', 'text-right');
            const actions = createElement('div', 'employee-application-actions');
            actions.appendChild(makeAction('fas fa-check', '核准申請', 'application-approve', application.employeeId));
            actions.appendChild(makeAction('fas fa-times', '退回申請', 'application-reject', application.employeeId, true));
            actionCell.appendChild(actions);
            [personCell, contactCell, positionCell, createdCell, actionCell].forEach((cell) => row.appendChild(cell));
            body.appendChild(row);
        });
        byId('employee-application-empty').hidden = state.applications.length !== 0;
        byId('employee-application-count').textContent = String(state.applications.length);
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
			const permissionCell = document.createElement('td');
			const permissionCount = createElement('button', 'employee-permission-count',
				String(position.permissionCount || 0) + ' 個群組');
			permissionCount.type = 'button';
			permissionCount.dataset.positionAction = 'permissions';
			permissionCount.dataset.positionId = String(position.positionId);
			permissionCount.title = '查看並設定' + position.positionName + '的基本權限';
			permissionCell.appendChild(permissionCount);
            const active = Number(position.positionStatus) === 1;
            const statusCell = document.createElement('td');
            statusCell.appendChild(createElement('span', 'employee-badge ' + (active ? 'is-active' : 'is-disabled'),
                active ? '啟用' : '停用'));
            const updatedCell = createElement('td', '', formatDate(position.updatedAt));
            const actionCell = createElement('td', 'text-right');
            const actions = createElement('div', 'employee-actions');
            actions.appendChild(makePositionAction('fas fa-key', '設定職位基本權限', 'permissions', position.positionId));
            actions.appendChild(makePositionAction('fas fa-pen', '編輯職位', 'edit', position.positionId));
            actions.appendChild(makePositionAction(active ? 'fas fa-ban' : 'fas fa-check',
                active ? '停用職位' : '啟用職位', 'status', position.positionId, active));
            actionCell.appendChild(actions);
            [codeCell, nameCell, descriptionCell, permissionCell, statusCell, updatedCell, actionCell]
                .forEach((cell) => row.appendChild(cell));
            body.appendChild(row);
        });
        byId('position-empty').hidden = filtered.length !== 0;
    }

    // 功能負責頁只呈現群組；底層權限代碼保留在群組明細，供搜尋與門禁使用。
    function buildResponsibilityGroups(coverage) {
        const groups = new Map();
        ((coverage && coverage.functions) || []).forEach((permission) => {
            const groupName = permission.permissionGroup || '其他';
            if (!groups.has(groupName)) {
                groups.set(groupName, {
                    groupName: groupName,
                    permissions: [],
                    employees: new Map()
                });
            }
            const group = groups.get(groupName);
            group.permissions.push(permission);
            (permission.responsibleEmployees || []).forEach((employee) => {
                const key = employee.employeeId || employee.employeeAccount;
                group.employees.set(String(key), employee);
            });
        });
        return Array.from(sortedGroups(groups).values()).map((group) => ({
            groupName: group.groupName,
            permissions: group.permissions,
            responsibleEmployees: Array.from(group.employees.values()),
            responsibleCount: group.employees.size,
            covered: group.employees.size > 0
        }));
    }

    // 顯示所有後台權限群組是否至少由一位正常員工負責。
    function renderPermissionCoverage() {
        const coverage = state.permissionCoverage;
        const root = byId('permission-coverage');
        if (!coverage) {
            root.hidden = true;
            return;
        }

        const responsibilityGroups = buildResponsibilityGroups(coverage);
        const uncoveredGroups = responsibilityGroups.filter((group) => !group.covered);
        const coveredCount = responsibilityGroups.length - uncoveredGroups.length;
        const complete = uncoveredGroups.length === 0;
        root.hidden = false;
        root.className = 'permission-coverage ' + (complete ? 'is-complete' : 'is-warning');
        byId('permission-coverage-icon').className = 'fas permission-coverage-icon '
            + (complete ? 'fa-check-circle' : 'fa-exclamation-triangle');
        byId('permission-coverage-title').textContent = complete
            ? '所有權限群組皆有人負責'
            : uncoveredGroups.length + ' 個群組尚無負責人';
        byId('permission-coverage-description').textContent = coveredCount + ' / '
            + responsibilityGroups.length + ' 個群組已有正常員工承接';

        const toggle = byId('permission-coverage-toggle');
        toggle.hidden = complete;
        toggle.setAttribute('aria-expanded', 'false');
        toggle.querySelector('span').textContent = '查看未指派群組';
        toggle.querySelector('i').className = 'fas fa-chevron-down';

        const details = byId('permission-coverage-details');
        details.hidden = true;
        details.replaceChildren();
        uncoveredGroups.forEach((uncoveredGroup) => {
            const group = createElement('section', 'permission-coverage-group');
            group.appendChild(createElement('h3', '', uncoveredGroup.groupName));
            const list = createElement('div', 'permission-coverage-list');
            const item = createElement('span', 'permission-coverage-item');
            item.appendChild(createElement('strong', '', uncoveredGroup.permissions.length + ' 項功能尚未指派'));
            list.appendChild(item);
            group.appendChild(list);
            details.appendChild(group);
        });
        renderResponsibilityAssignments();
    }

    // 依群組列出實際負責員工，不讓同群組的底層功能重複占用表格列。
    function renderResponsibilityGroupOptions() {
        const select = byId('responsibility-group-filter');
        const current = select.value;
        const functions = state.permissionCoverage ? state.permissionCoverage.functions || [] : [];
        const groups = Array.from(new Set(functions.map((permission) =>
            permission.permissionGroup || '其他'))).sort(compareGroupNames);
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

    function renderResponsibilityAssignments() {
        const coverage = state.permissionCoverage;
        if (!coverage) return;
        const responsibilityGroups = buildResponsibilityGroups(coverage);
        const coveredCount = responsibilityGroups.filter((item) => item.covered).length;
        const uncoveredCount = responsibilityGroups.length - coveredCount;
        byId('responsibility-covered-count').textContent = String(coveredCount);
        byId('responsibility-uncovered-count').textContent = String(uncoveredCount);
        byId('responsibility-total-count').textContent = String(responsibilityGroups.length);
        byId('coverage-tab-count').textContent = String(uncoveredCount);

        const keyword = byId('responsibility-search').value.trim().toLowerCase();
        const status = byId('responsibility-status-filter').value;
        const group = byId('responsibility-group-filter').value;
        const filtered = responsibilityGroups.filter((permissionGroup) => {
            const peopleText = permissionGroup.responsibleEmployees.map((employee) =>
                [employee.employeeName, employee.employeeAccount, employee.positionName].join(' ')).join(' ');
            const functionText = permissionGroup.permissions.map((permission) =>
                [permission.permissionCode, permission.permissionName, permission.permissionDescription].join(' ')).join(' ');
            const searchable = [permissionGroup.groupName, functionText, peopleText]
                .filter(Boolean).join(' ').toLowerCase();
            return (!keyword || searchable.includes(keyword))
                && (!group || permissionGroup.groupName === group)
                && (!status || (status === 'covered' ? permissionGroup.covered : !permissionGroup.covered));
        });

        const body = byId('responsibility-table-body');
        body.replaceChildren();
        filtered.forEach((permissionGroup) => {
            const row = document.createElement('tr');
            const functionCell = createElement('td', 'responsibility-function');
            functionCell.appendChild(createElement('strong', 'responsibility-group-name', permissionGroup.groupName));
            functionCell.appendChild(createElement('small', '', permissionGroup.permissions.length + ' 項功能'));
            const detailsCell = document.createElement('td');
            const functionList = createElement('div', 'responsibility-function-list');
            permissionGroup.permissions.forEach((permission) => {
                const item = createElement('span', 'responsibility-function-item');
                item.appendChild(createElement('code', 'permission-code', permission.permissionCode));
                item.appendChild(createElement('span', '', permission.permissionName));
                functionList.appendChild(item);
            });
            detailsCell.appendChild(functionList);
            const peopleCell = document.createElement('td');
            const people = createElement('div', 'responsibility-people');
            permissionGroup.responsibleEmployees.forEach((employee) => {
                const person = createElement('span', 'responsibility-person');
                person.appendChild(createElement('strong', '', employee.employeeName + '（' + employee.employeeAccount + '）'));
                person.appendChild(createElement('small', '', employee.positionName));
                people.appendChild(person);
            });
            if (!permissionGroup.responsibleEmployees.length) {
                people.appendChild(createElement('span', 'responsibility-unassigned', '尚未指派員工'));
            }
            peopleCell.appendChild(people);
            const statusCell = document.createElement('td');
            statusCell.appendChild(createElement('span', 'employee-badge ' + (permissionGroup.covered ? 'is-active' : 'is-uncovered'),
                permissionGroup.covered ? permissionGroup.responsibleCount + ' 人負責' : '尚未指派'));
            [functionCell, detailsCell, peopleCell, statusCell].forEach((cell) => row.appendChild(cell));
            body.appendChild(row);
        });
        byId('responsibility-result-count').textContent = filtered.length + ' 個群組';
        const empty = byId('responsibility-empty');
        empty.textContent = status === 'uncovered' && !keyword
            ? '目前沒有尚未指派負責人的群組。'
            : '找不到符合條件的權限群組。';
        empty.hidden = filtered.length !== 0;
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

        // 若當前選取的員工不在篩選結果中，自動選中結果的第一位並載入功能權限。
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
                ? (employee.permissionCount + ' 個群組') : '';
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

    function updatePermissionCount(containerId, countId) {
        const checked = document.querySelectorAll('#' + containerId + ' input[type="checkbox"]:checked').length;
        byId(countId).textContent = checked + ' 個群組已選';
    }

    // 畫面只顯示功能群組；每個勾選框在儲存時會展開成該群組的全部底層權限編號。
	function renderPermissionGroups(selectedIds, containerId, countId, collapsed, lockedIds) {
        containerId = containerId || 'permission-groups';
        countId = countId || 'permission-selection-count';
		const selected = new Set((selectedIds || []).map(Number));
		const locked = new Set((lockedIds || []).map(Number));
        const groups = new Map();
        state.functions.forEach((permission) => {
            const groupName = permission.permissionGroup || '其他';
            if (!groups.has(groupName)) groups.set(groupName, []);
            groups.get(groupName).push(permission);
        });

        const container = byId(containerId);
        container.replaceChildren();
		sortedGroups(groups).forEach((permissions, groupName) => {
			const permissionIds = permissions.map((permission) => Number(permission.permissionId));
			const isSelected = permissionIds.some((permissionId) => selected.has(permissionId));
			const isPositionDefault = permissionIds.some((permissionId) => locked.has(permissionId));
            const group = createElement('section', 'permission-group permission-group-choice'
                + (isSelected ? ' is-all-selected' : ''));
            const label = createElement('label', 'permission-option permission-group-option'
                + (isPositionDefault ? ' is-position-default' : ''));
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.checked = isSelected;
            checkbox.disabled = isPositionDefault;
            checkbox.dataset.permissionIds = permissionIds.join(',');
            const text = document.createElement('span');
            text.appendChild(createElement('strong', '', groupName));
            if (isPositionDefault) text.appendChild(createElement('em', 'permission-default-label', '職位預設'));
            label.appendChild(checkbox);
            label.appendChild(text);
            group.appendChild(label);
            container.appendChild(group);
        });
        if (!state.functions.length) container.textContent = '目前沒有可分配的權限功能。';
        updatePermissionCount(containerId, countId);
    }

    function selectedGroupPermissionIds(containerId, includeDisabled) {
        const selector = '#' + containerId + ' input[type="checkbox"]:checked'
            + (includeDisabled ? '' : ':not(:disabled)');
        return Array.from(document.querySelectorAll(selector))
            .flatMap((checkbox) => (checkbox.dataset.permissionIds || '').split(','))
            .filter(Boolean).map(Number);
    }

    function renderPositionPermissionGroups(selectedIds) {
        renderPermissionGroups(selectedIds, 'position-permission-groups', 'position-permission-selection-count', true);
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
            const actor = createElement('td', 'audit-person');
            actor.appendChild(createElement('div', 'employee-primary', log.employeeName || '系統管理員'));
            actor.appendChild(createElement('div', 'employee-secondary', log.employeeId ? '員工 #' + log.employeeId : '尚未連結員工登入'));
            const type = document.createElement('td');
            type.appendChild(createElement('span', 'audit-module', auditModuleLabels[log.targetModule] || log.targetModule));
            type.appendChild(createElement('span', 'audit-action', auditActionLabels[log.actionType] || log.actionType));
            const target = createElement('td', 'audit-person');
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

    async function loadApplications() {
        state.applications = await request('/applications');
        renderApplications();
    }

    async function loadPositions() {
        state.positions = await request('/positions');
        renderPositions();
        renderPositionOptions(null);
    }

    async function loadFunctions() {
        // 固定權限仍需提供給權限配置、職位預設與功能負責頁使用。
        state.functions = await request('/permission-functions');
    }

    async function loadPermissionCoverage() {
        state.permissionCoverage = await request('/permission-coverage');
        renderResponsibilityGroupOptions();
        renderPermissionCoverage();
    }

    async function loadSelectedPermissions() {
        renderSelectedEmployee();
        if (state.selectedEmployeeId === null) {
            renderPermissionGroups([]);
            return;
        }
		try {
			const result = await request('/' + state.selectedEmployeeId + '/permissions');
			renderPermissionGroups(result.permissionIds, 'permission-groups', 'permission-selection-count', true,
				result.positionPermissionIds);
        } catch (error) {
            renderPermissionGroups([]);
            setFeedback(error.message, 'error');
        }
    }

    async function openPositionPermissions(position) {
        state.selectedPositionId = position.positionId;
        byId('position-permission-name').textContent = position.positionName + '（' + position.positionCode + '）';
        setModalFeedback('position-permission-modal', '');
        renderPositionPermissionGroups([]);
        showModal('position-permission-modal');
        try {
            const result = await request('/positions/' + position.positionId + '/permissions');
            renderPositionPermissionGroups(result.permissionIds);
        } catch (error) {
            setModalFeedback('position-permission-modal', error.message);
        }
    }

    // 員工列表只提供唯讀功能清單，不能在此視窗勾選或儲存權限。
    async function openEmployeePermissionView(employee) {
        byId('employee-permission-view-name').textContent = employee.employeeName
            + '（' + employee.employeeAccount + '）';
        const list = byId('employee-permission-view-list');
        list.replaceChildren(createElement('p', 'employee-permission-view-loading', '正在載入功能…'));
        setModalFeedback('employee-permission-view-modal', '');
        showModal('employee-permission-view-modal');
        try {
            const result = await request('/' + employee.employeeId + '/permissions');
            const effectiveIds = new Set((result.permissionIds || []).map(Number));
            const positionIds = new Set((result.positionPermissionIds || []).map(Number));
            const employeeIds = new Set((result.employeePermissionIds || []).map(Number));
            const groups = new Map();
            state.functions.forEach((permission) => {
                const groupName = permission.permissionGroup || '其他';
                if (!groups.has(groupName)) groups.set(groupName, []);
                groups.get(groupName).push(permission);
            });
            list.replaceChildren();
            sortedGroups(groups).forEach((permissions, groupName) => {
                const ids = permissions.map((permission) => Number(permission.permissionId));
                if (!ids.some((permissionId) => effectiveIds.has(permissionId))) return;
                const item = createElement('div', 'employee-permission-view-item');
                const icon = createElement('i', 'fas fa-check-circle');
                icon.setAttribute('aria-hidden', 'true');
                item.appendChild(icon);
                const content = document.createElement('div');
                content.appendChild(createElement('strong', '', groupName));
                const sources = createElement('div', 'employee-permission-view-sources');
                if (ids.some((permissionId) => positionIds.has(permissionId))) {
                    sources.appendChild(createElement('span', 'permission-source-badge is-position', '職位預設'));
                }
                if (ids.some((permissionId) => employeeIds.has(permissionId))) {
                    sources.appendChild(createElement('span', 'permission-source-badge is-personal', '個別增加'));
                }
                content.appendChild(sources);
                item.appendChild(content);
                list.appendChild(item);
            });
            if (!list.children.length) {
                list.appendChild(createElement('p', 'employee-permission-view-empty', '目前沒有任何功能權限。'));
            }
        } catch (error) {
            list.replaceChildren();
            setModalFeedback('employee-permission-view-modal', error.message);
        }
    }

    function openEmployeeForm(employee) {
        const editing = Boolean(employee);
        const form = byId('employee-form');
        const profileFieldIds = ['employee-name', 'employee-account', 'employee-phone', 'employee-email'];
        form.reset();
        form.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));
        byId('employee-id').value = editing ? employee.employeeId : '';
        byId('employee-form-title').textContent = editing ? '編輯員工' : '新增員工';
        byId('employee-name').value = editing ? employee.employeeName : '';
        byId('employee-account').value = editing ? employee.employeeAccount : '';
        byId('employee-phone').value = editing ? employee.employeePhone : '';
        byId('employee-email').value = editing ? employee.employeeEmail : '';
        profileFieldIds.forEach((id) => {
            byId(id).disabled = editing;
        });
        renderPositionOptions(editing ? employee.positionId : null);
        byId('employee-password-group').hidden = editing;
        byId('employee-password').required = !editing;
        byId('employee-form-save').innerHTML = editing
            ? '<i class="fas fa-save mr-1"></i>儲存職位'
            : '<i class="fas fa-save mr-1"></i>儲存員工';
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
		byId('password-employee-email').textContent = maskEmail(employee.employeeEmail);
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
                if (tab.dataset.tab === 'positions') loadPermissionCoverage()
                    .catch((error) => setFeedback(error.message, 'error'));
                if (tab.dataset.tab === 'coverage') loadPermissionCoverage()
                    .catch((error) => setFeedback(error.message, 'error'));
                if (tab.dataset.tab === 'audit') loadAuditLogs();
                if (tab.dataset.tab === 'applications') loadApplications().catch((error) => setFeedback(error.message, 'error'));
            });
        });

        byId('employee-search').addEventListener('input', renderEmployees);
        byId('employee-status-filter').addEventListener('change', renderEmployees);
        byId('position-search').addEventListener('input', renderPositions);
        byId('position-status-filter').addEventListener('change', renderPositions);
        byId('responsibility-search').addEventListener('input', renderResponsibilityAssignments);
        byId('responsibility-status-filter').addEventListener('change', renderResponsibilityAssignments);
        byId('responsibility-group-filter').addEventListener('change', renderResponsibilityAssignments);
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
        byId('permission-coverage-toggle').addEventListener('click', () => {
            const toggle = byId('permission-coverage-toggle');
            const details = byId('permission-coverage-details');
            const expanded = toggle.getAttribute('aria-expanded') === 'true';
            toggle.setAttribute('aria-expanded', expanded ? 'false' : 'true');
            toggle.querySelector('span').textContent = expanded ? '查看未指派功能' : '收起未指派功能';
            toggle.querySelector('i').className = 'fas ' + (expanded ? 'fa-chevron-down' : 'fa-chevron-up');
            details.hidden = expanded;
        });
        byId('employee-application-refresh').addEventListener('click', () => loadApplications().catch((error) => setFeedback(error.message, 'error')));

        byId('employee-application-table-body').addEventListener('click', (event) => {
            const button = event.target.closest('[data-action]');
            if (!button) return;
            const application = state.applications.find((item) => item.employeeId === Number(button.dataset.employeeId));
            if (!application) return;
            if (button.dataset.action === 'application-approve') {
                const select = button.closest('tr').querySelector('.employee-application-position');
                approveApplication(application, select ? Number(select.value) : null);
            }
            if (button.dataset.action === 'application-reject') openRejectApplication(application);
        });

        byId('employee-table-body').addEventListener('click', (event) => {
            const button = event.target.closest('[data-action]');
            if (!button) return;
            const employee = state.employees.find((item) => item.employeeId === Number(button.dataset.employeeId));
            if (!employee) return;
            if (button.dataset.action === 'edit') openEmployeeForm(employee);
            if (button.dataset.action === 'password') openPasswordForm(employee);
            if (button.dataset.action === 'status') openStatusForm(employee);
            if (button.dataset.action === 'view-permissions') openEmployeePermissionView(employee);
        });

        byId('position-table-body').addEventListener('click', (event) => {
            const button = event.target.closest('[data-position-action]');
            if (!button) return;
            const position = state.positions.find((item) => item.positionId === Number(button.dataset.positionId));
            if (!position) return;
            if (button.dataset.positionAction === 'permissions') openPositionPermissions(position);
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

        ['permission-groups', 'position-permission-groups'].forEach((containerId) => {
            const container = byId(containerId);
            const countId = containerId === 'permission-groups'
                ? 'permission-selection-count' : 'position-permission-selection-count';
            container.addEventListener('change', (event) => {
                if (event.target.matches('input[type="checkbox"]')) {
                    event.target.closest('.permission-group').classList
                        .toggle('is-all-selected', event.target.checked);
                    updatePermissionCount(containerId, countId);
                }
            });
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
        byId('position-permission-save-button').addEventListener('click', savePositionPermissions);
        byId('employee-application-reject-form').addEventListener('submit', rejectApplication);
    }

    async function approveApplication(application, positionId) {
        if (!positionId) {
            setFeedback('請先選擇核准後的員工職位。', 'error');
            return;
        }
        try {
            await request('/' + application.employeeId + '/application/approve', {
                method: 'PUT', body: JSON.stringify({ positionId: positionId })
            });
            await Promise.all([loadApplications(), loadEmployees(true), loadPermissionCoverage()]);
			window.dispatchEvent(new Event('admin:review-queue-changed'));
            setFeedback('員工申請已核准，帳號現在可以登入。', 'success');
        } catch (error) {
            setFeedback(error.message, 'error');
        }
    }

    function openRejectApplication(application) {
        byId('employee-application-reject-id').value = application.employeeId;
        byId('employee-application-reject-name').textContent = application.employeeName + '（' + application.employeeAccount + '）';
        byId('employee-application-reject-reason').value = '';
        setModalFeedback('employee-application-reject-modal', '');
        showModal('employee-application-reject-modal');
    }

    async function rejectApplication(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (!form.reportValidity()) return;
        const button = form.querySelector('button[type="submit"]');
        withBusy(button, true, '處理中…');
        try {
            await request('/' + byId('employee-application-reject-id').value + '/application/reject', {
                method: 'PUT', body: JSON.stringify({ reason: byId('employee-application-reject-reason').value.trim() })
            });
            hideModal('employee-application-reject-modal');
            await loadApplications();
			window.dispatchEvent(new Event('admin:review-queue-changed'));
            setFeedback('員工申請已退回。', 'success');
        } catch (error) {
            setModalFeedback('employee-application-reject-modal', error.message);
        } finally {
            withBusy(button, false);
        }
    }

    async function saveEmployee(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (!form.reportValidity()) return;
        const employeeId = byId('employee-id').value;
        const positionId = Number(byId('employee-position').value);
        const body = employeeId ? { positionId } : {
            employeeName: byId('employee-name').value,
            employeeAccount: byId('employee-account').value,
            employeePhone: byId('employee-phone').value,
            employeeEmail: byId('employee-email').value,
            employeePassword: byId('employee-password').value,
            positionId
        };
        const button = byId('employee-form-save');
        withBusy(button, true, '儲存中…');
        setModalFeedback('employee-form-modal', '');
        form.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));
        try {
            await request(employeeId ? '/' + employeeId : '', {
                method: employeeId ? 'PUT' : 'POST', body: JSON.stringify(body)
            });
            hideModal('employee-form-modal');
            await Promise.all([loadEmployees(true), loadPermissionCoverage()]);
			setFeedback(employeeId ? '員工職位已更新。' : '員工帳號已新增。', 'success');
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
            await Promise.all([loadEmployees(true), loadPermissionCoverage()]);
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
		const button = form.querySelector('button[type="submit"]');
		withBusy(button, true, '寄送中…');
        setModalFeedback('password-modal', '');
        try {
			const result = await request('/' + byId('password-employee-id').value + '/password-reset-email', {
				method: 'POST'
			});
			hideModal('password-modal');
			setFeedback(result.message || '員工密碼設定驗證信已寄出。', 'success');
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
            await Promise.all([loadEmployees(true), loadPermissionCoverage()]);
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
		const permissionIds = selectedGroupPermissionIds('permission-groups', false);
        const button = byId('permission-save-button');
        withBusy(button, true, '儲存中…');
        try {
            await request('/' + state.selectedEmployeeId + '/permissions', {
                method: 'PUT', body: JSON.stringify({ permissionIds: permissionIds })
            });
			await Promise.all([loadEmployees(true), loadPermissionCoverage()]);
            await loadSelectedPermissions();
            setFeedback('員工權限已儲存。', 'success');
        } catch (error) {
            setFeedback(error.message, 'error');
        } finally {
            withBusy(button, false);
        }
    }

    async function savePositionPermissions() {
        if (state.selectedPositionId === null) return;
        const permissionIds = selectedGroupPermissionIds('position-permission-groups', true);
        const button = byId('position-permission-save-button');
        withBusy(button, true, '儲存中…');
        setModalFeedback('position-permission-modal', '');
        try {
            const result = await request('/positions/' + state.selectedPositionId + '/permissions', {
                method: 'PUT', body: JSON.stringify({ permissionIds: permissionIds })
            });
			const position = state.positions.find((item) => item.positionId === state.selectedPositionId);
			if (position) position.permissionCount = document
				.querySelectorAll('#position-permission-groups input:checked').length;
			await Promise.all([loadEmployees(true), loadPermissionCoverage()]);
			renderPositions();
			hideModal('position-permission-modal');
			setFeedback('職位基本權限已儲存，使用此職位的員工已立即生效。', 'success');
        } catch (error) {
            setModalFeedback('position-permission-modal', error.message);
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
            await Promise.all([loadEmployees(false), loadFunctions(), loadApplications(), loadPermissionCoverage()]);
            renderPermissionGroups([]);
        } catch (error) {
            setFeedback(error.message, 'error');
        }
    });
}());
