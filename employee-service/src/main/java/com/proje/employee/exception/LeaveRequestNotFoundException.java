package com.proje.employee.exception;

public class LeaveRequestNotFoundException extends RuntimeException {

    public LeaveRequestNotFoundException(Long id) {
        super("Leave request " + id + " was not found");
    }
}
