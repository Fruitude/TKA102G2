package com.fruitude.employee.model;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * 將底層權限轉換成完整功能群組，確保同一個後台選單群組只能整組授權或整組取消。
 */
@Component
public class EmployeePermissionGroupResolver {

	private final EmployeePermissionFunctionRepository functionRepository;

	public EmployeePermissionGroupResolver(EmployeePermissionFunctionRepository functionRepository) {
		this.functionRepository = functionRepository;
	}

	/** 儲存表單使用：拒絕不存在的權限編號，再展開所選群組的全部權限。 */
	public Set<Integer> expandRequestedIds(Collection<Integer> requestedIds) {
		Set<Integer> requested = cleanIds(requestedIds);
		List<EmployeePermissionFunction> functions = functionRepository
				.findAllByOrderByPermissionGroupAscPermissionIdAsc();
		Set<Integer> existingIds = functions.stream().map(EmployeePermissionFunction::getPermissionId)
				.collect(Collectors.toSet());
		if (!existingIds.containsAll(requested)) {
			throw new IllegalArgumentException("包含不存在的權限功能，請重新整理後再試");
		}
		return expandIds(requested, functions);
	}

	/** 舊資料讀取使用：只要已有群組內任一權限，實際結果即包含整個群組。 */
	public Set<Integer> expandStoredIds(Collection<Integer> storedIds) {
		return expandIds(cleanIds(storedIds),
				functionRepository.findAllByOrderByPermissionGroupAscPermissionIdAsc());
	}

	/** 登入門禁使用：依現有權限代碼找出群組，再回傳群組內全部代碼。 */
	public Set<String> expandStoredCodes(Collection<String> storedCodes) {
		Set<String> selectedCodes = storedCodes == null ? new LinkedHashSet<>() : storedCodes.stream()
				.filter(code -> code != null && !code.isBlank())
				.collect(Collectors.toCollection(LinkedHashSet::new));
		List<EmployeePermissionFunction> functions = functionRepository
				.findAllByOrderByPermissionGroupAscPermissionIdAsc();
		return expandCodes(selectedCodes, functions);
	}

	static Set<String> expandCodes(Collection<String> selectedCodes,
			List<EmployeePermissionFunction> functions) {
		Set<String> selected = selectedCodes == null ? new LinkedHashSet<>() : selectedCodes.stream()
				.filter(code -> code != null && !code.isBlank())
				.collect(Collectors.toCollection(LinkedHashSet::new));
		Set<String> selectedGroups = functions.stream()
				.filter(function -> selected.contains(function.getPermissionCode()))
				.map(EmployeePermissionFunction::getPermissionGroup)
				.collect(Collectors.toSet());
		return functions.stream()
				.filter(function -> selectedGroups.contains(function.getPermissionGroup()))
				.map(EmployeePermissionFunction::getPermissionCode)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	static Set<Integer> expandIds(Collection<Integer> selectedIds,
			List<EmployeePermissionFunction> functions) {
		Set<Integer> selected = cleanIds(selectedIds);
		Set<String> selectedGroups = functions.stream()
				.filter(function -> selected.contains(function.getPermissionId()))
				.map(EmployeePermissionFunction::getPermissionGroup)
				.collect(Collectors.toSet());
		return functions.stream()
				.filter(function -> selectedGroups.contains(function.getPermissionGroup()))
				.map(EmployeePermissionFunction::getPermissionId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static Set<Integer> cleanIds(Collection<Integer> ids) {
		if (ids == null) return new LinkedHashSet<>();
		return ids.stream().filter(id -> id != null)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}
}
