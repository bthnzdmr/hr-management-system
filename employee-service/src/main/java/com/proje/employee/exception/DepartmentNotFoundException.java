package com.proje.employee.exception;

public class DepartmentNotFoundException extends RuntimeException {

    public DepartmentNotFoundException(Long departmentId) {
        super("Department not found with id: " + departmentId);
    }
}
