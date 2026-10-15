-- 將職位預設與員工個別權限補齊為完整功能群組。
-- 規則：同一個 permission_group 只要已有任一權限，即補入該群組全部權限。
-- 本檔只新增缺少的關聯，不刪除既有資料，可重複執行。

START TRANSACTION;

INSERT INTO employee_position_permission (position_id, permission_id, assigned_at)
SELECT assigned.position_id, group_function.permission_id, NOW(6)
FROM (
    SELECT DISTINCT epp.position_id, current_function.permission_group
    FROM employee_position_permission epp
    JOIN employee_permission_function current_function
      ON current_function.permission_id = epp.permission_id
) assigned
JOIN employee_permission_function group_function
  ON group_function.permission_group = assigned.permission_group
WHERE NOT EXISTS (
    SELECT 1
    FROM employee_position_permission existing
    WHERE existing.position_id = assigned.position_id
      AND existing.permission_id = group_function.permission_id
);

INSERT INTO employee_permission (employee_id, permission_id, assigned_at)
SELECT assigned.employee_id, group_function.permission_id, NOW()
FROM (
    SELECT DISTINCT ep.employee_id, current_function.permission_group
    FROM employee_permission ep
    JOIN employee_permission_function current_function
      ON current_function.permission_id = ep.permission_id
) assigned
JOIN employee_permission_function group_function
  ON group_function.permission_group = assigned.permission_group
WHERE NOT EXISTS (
    SELECT 1
    FROM employee_permission existing
    WHERE existing.employee_id = assigned.employee_id
      AND existing.permission_id = group_function.permission_id
);

COMMIT;

-- 執行後應回傳 0 筆：職位仍有部分群組。
SELECT epp.position_id, position.position_name, permission_function.permission_group,
       COUNT(DISTINCT epp.permission_id) AS assigned_count,
       totals.total_count
FROM employee_position_permission epp
JOIN employee_position position ON position.position_id = epp.position_id
JOIN employee_permission_function permission_function
  ON permission_function.permission_id = epp.permission_id
JOIN (
    SELECT permission_group, COUNT(*) AS total_count
    FROM employee_permission_function
    GROUP BY permission_group
) totals ON totals.permission_group = permission_function.permission_group
GROUP BY epp.position_id, position.position_name, permission_function.permission_group, totals.total_count
HAVING assigned_count <> totals.total_count;

-- 執行後應回傳 0 筆：員工仍有部分群組。
SELECT ep.employee_id, employee.employee_name, permission_function.permission_group,
       COUNT(DISTINCT ep.permission_id) AS assigned_count,
       totals.total_count
FROM employee_permission ep
JOIN employee employee ON employee.employee_id = ep.employee_id
JOIN employee_permission_function permission_function
  ON permission_function.permission_id = ep.permission_id
JOIN (
    SELECT permission_group, COUNT(*) AS total_count
    FROM employee_permission_function
    GROUP BY permission_group
) totals ON totals.permission_group = permission_function.permission_group
GROUP BY ep.employee_id, employee.employee_name, permission_function.permission_group, totals.total_count
HAVING assigned_count <> totals.total_count;
