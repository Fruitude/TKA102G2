package com.fruitude.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.Set;

import org.junit.Test;

/** 後台網址與權限代碼對應測試，避免新增路由時誤放行敏感功能。 */
public class AdminAccessInterceptorTest {

	@Test
	public void anonymousEmployeeApplicationCanReadPositionOptionsOnly() throws Exception {
		var service = new com.fruitude.employee.model.EmployeeAdminAccessService(null, null, null, null, null) {
			@Override public java.util.Optional<AccessProfile> findAccessProfile(Integer id) {
				return java.util.Optional.empty();
			}
		};
		var interceptor = new AdminAccessInterceptor(service);
		var publicResponse = new org.springframework.mock.web.MockHttpServletResponse();
		assertTrue(interceptor.preHandle(new org.springframework.mock.web.MockHttpServletRequest(
				"GET", "/api/admin/session/application-positions"), publicResponse, new Object()));
		assertEquals(200, publicResponse.getStatus());

		var adminResponse = new org.springframework.mock.web.MockHttpServletResponse();
		assertFalse(interceptor.preHandle(new org.springframework.mock.web.MockHttpServletRequest(
				"GET", "/api/admin/employees/positions"), adminResponse, new Object()));
		assertEquals(401, adminResponse.getStatus());
	}

    @Test
    public void anonymousStorefrontCanReadImagesButCannotManageThem() throws Exception {
        var service = new com.fruitude.employee.model.EmployeeAdminAccessService(null,null,null,null,null) {
            @Override public java.util.Optional<AccessProfile> findAccessProfile(Integer id) {
                return java.util.Optional.empty();
            }
        };
        var interceptor = new AdminAccessInterceptor(service);
        for (String path : new String[]{"/product/image/17","/product/image/17/thumbnail"}) {
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            assertTrue(path,interceptor.preHandle(new org.springframework.mock.web.MockHttpServletRequest("GET",path),response,new Object()));
            assertEquals(200,response.getStatus());
        }
        for (String path : new String[]{"/product/image/list","/product/image/17/thumbnail/extra"}) {
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            assertFalse(path,interceptor.preHandle(new org.springframework.mock.web.MockHttpServletRequest("GET",path),response,new Object()));
            assertEquals(302,response.getStatus());
        }
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new org.springframework.mock.web.MockHttpServletRequest("POST","/product/image/17"),response,new Object()));
        assertEquals(302,response.getStatus());
    }

	@Test
	public void memberCreditWriteRequiresCreditPermission() {
		assertEquals(Set.of("MEMBER_CREDIT"), AdminAccessInterceptor.requiredPermissions(
				"/api/admin/members/7/credit-adjustment-requests", "POST"));
		assertEquals(Set.of("MEMBER_CREDIT"), AdminAccessInterceptor.requiredPermissions(
				"/api/admin/members/credit-adjustment-requests/9/cancel", "PATCH"));
	}

	@Test
	public void memberCreditReviewRequiresReviewPermission() {
		assertEquals(Set.of("MEMBER_CREDIT_REVIEW"), AdminAccessInterceptor.requiredPermissions(
				"/api/admin/members/credit-adjustment-requests/9/review", "PATCH"));
		assertEquals(Set.of("MEMBER_CREDIT_REVIEW"), AdminAccessInterceptor.requiredPermissions(
				"/api/admin/members/credit-adjustment-requests/9/review;source=button", "PATCH"));
		assertEquals(Set.of("MEMBER_CREDIT_REVIEW"), AdminAccessInterceptor.requiredPermissions(
				"/api/admin/members/credit-adjustment-requests/9;source=list/review", "PATCH"));
	}

	@Test
	public void memberCreditAdjustmentListAllowsRelatedMemberPermissions() {
		assertEquals(Set.of("MEMBER_VIEW", "MEMBER_CREDIT", "MEMBER_CREDIT_REVIEW", "MEMBER_STATUS"),
				AdminAccessInterceptor.requiredPermissions(
				"/api/admin/members/credit-adjustment-requests", "GET"));
	}

	@Test
	public void employeePermissionWriteRequiresPermissionManage() {
		assertEquals(Set.of("PERMISSION_MANAGE"), AdminAccessInterceptor.requiredPermissions(
				"/api/admin/employees/3/permissions", "PUT"));
	}

	@Test
	public void employeePasswordEmailRequiresEmployeeEditPermission() {
		assertEquals(Set.of("EMPLOYEE_EDIT"), AdminAccessInterceptor.requiredPermissions(
				"/api/admin/employees/3/password-reset-email", "POST"));
	}

	@Test
	public void adminHomeOnlyRequiresSuccessfulEmployeeLogin() {
		assertTrue(AdminAccessInterceptor.requiredPermissions("/admin", "GET").isEmpty());
	}

	@Test
	public void productImageManagementUsesImagePermission() {
		assertEquals(Set.of("PRODUCT_IMAGE"), AdminAccessInterceptor.requiredPermissions(
				"/product/image/list", "GET"));
	}

	@Test
	public void commentRoutesSeparateViewAndModerationPermissions() {
		assertEquals(Set.of("COMMENT_VIEW"), AdminAccessInterceptor.requiredPermissions(
				"/api/admin/comments", "GET"));
		assertEquals(Set.of("COMMENT_MODERATE"), AdminAccessInterceptor.requiredPermissions(
				"/api/admin/comments/12/moderation", "PUT"));
	}

	@Test
	public void farmerRoutesSeparateViewAndManagementPermissions() {
		assertEquals(Set.of("FARMER_VIEW"), AdminAccessInterceptor.requiredPermissions(
				"/admin/info/farmers/", "GET"));
		assertEquals(Set.of("FARMER_MANAGE"), AdminAccessInterceptor.requiredPermissions(
				"/admin/info/farmers/update", "POST"));
	}

	@Test
	public void inventoryPagesUseInventoryViewPermission() {
		assertEquals(Set.of("INVENTORY_VIEW"), AdminAccessInterceptor.requiredPermissions(
				"/admin/psi/stock", "GET"));
		assertEquals(Set.of("INVENTORY_VIEW"), AdminAccessInterceptor.requiredPermissions(
				"/admin/product/stock", "GET"));
	}

	@Test
	public void shippingRoutesSeparateViewAndProcessingPermissions() {
		assertEquals(Set.of("SHIPPING_VIEW"), AdminAccessInterceptor.requiredPermissions(
				"/admin/psi/shipping", "GET"));
		assertEquals(Set.of("SHIPPING_PROCESS"), AdminAccessInterceptor.requiredPermissions(
				"/admin/psi/shipping/ship", "POST"));
	}

	@Test
	public void logisticsRoutesSeparateViewAndManagementPermissions() {
		assertEquals(Set.of("LOGISTICS_VIEW", "LOGISTICS_MANAGE"), AdminAccessInterceptor.requiredPermissions(
				"/admin/logistics", "GET"));
		assertEquals(Set.of("LOGISTICS_MANAGE"), AdminAccessInterceptor.requiredPermissions(
				"/api/admin/logistics/shipments", "POST"));
	}
}
