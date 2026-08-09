package com.proje.employee.exception;

public class ManagerCycleException extends RuntimeException {

    public ManagerCycleException(Long employeeId, Long managerId) {
        super("Assigning manager " + managerId + " to employee " + employeeId
                + " would create a management cycle");
    }
}
