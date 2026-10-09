package com.fruitude.product.model;

import com.fruitude.employee.model.*;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class ProductStatusAccess {
    private final EmployeeRepository employees;
    private final EmployeePositionRepository positions;
    public ProductStatusAccess(EmployeeRepository employees, EmployeePositionRepository positions) {
        this.employees = employees; this.positions = positions;
    }
    public boolean canRestoreDiscontinued() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return false;
        var session = attributes.getRequest().getSession(false);
        Object id = session == null ? null : session.getAttribute("loggedInEmployeeId");
        if (!(id instanceof Number number)) return false;
        var employee = employees.findById(number.intValue()).orElse(null);
        if (employee == null || !Byte.valueOf((byte)1).equals(employee.getEmployeeStatus())
                || !Byte.valueOf((byte)1).equals(employee.getEmployeeReviewStatus()) || employee.getPositionId() == null) return false;
        var position = positions.findById(employee.getPositionId()).orElse(null);
        return position != null && Byte.valueOf((byte)1).equals(position.getPositionStatus())
            && "ADMIN".equalsIgnoreCase(position.getPositionCode());
    }
    public void requireRestorePermission(Byte previous, Byte next, int discontinuedStatus) {
        if (previous != null && previous == discontinuedStatus && !previous.equals(next) && !canRestoreDiscontinued())
            throw new ProductStatusAccessException();
    }
}
