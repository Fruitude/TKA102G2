package com.fruitude.employee.model;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.Test;

public class EmployeePermissionGroupResolverTest {

	@Test
	public void onePermissionExpandsToTheWholeGroupOnly() {
		EmployeePermissionFunction purchaseView = function(1, "PURCHASE_VIEW", "進銷存系統");
		EmployeePermissionFunction purchaseEdit = function(2, "PURCHASE_EDIT", "進銷存系統");
		EmployeePermissionFunction memberView = function(3, "MEMBER_VIEW", "會員管理");

		assertEquals(Set.of(1, 2), EmployeePermissionGroupResolver.expandIds(
				Set.of(1), List.of(purchaseView, purchaseEdit, memberView)));
	}

	@Test
	public void onePermissionCodeExpandsToEveryCodeInTheGroup() {
		EmployeePermissionFunction purchaseView = function(1, "PURCHASE_VIEW", "進銷存系統");
		EmployeePermissionFunction purchaseEdit = function(2, "PURCHASE_EDIT", "進銷存系統");
		EmployeePermissionFunction memberView = function(3, "MEMBER_VIEW", "會員管理");

		assertEquals(Set.of("PURCHASE_VIEW", "PURCHASE_EDIT"), EmployeePermissionGroupResolver.expandCodes(
				Set.of("PURCHASE_VIEW"), List.of(purchaseView, purchaseEdit, memberView)));
	}

	private EmployeePermissionFunction function(int id, String code, String group) {
		EmployeePermissionFunction function = new EmployeePermissionFunction();
		function.setPermissionId(id);
		function.setPermissionCode(code);
		function.setPermissionGroup(group);
		return function;
	}
}
