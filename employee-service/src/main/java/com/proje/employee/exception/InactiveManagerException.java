package com.proje.employee.exception;

public class InactiveManagerException extends RuntimeException {

    public InactiveManagerException(Long managerId) {
        super("Employee " + managerId + " is inactive and cannot be assigned as a manager");
    }
}
