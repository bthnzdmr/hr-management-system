package com.proje.employee.exception;

/** Ayni personelin tarihleri kesisen ikinci bir izni. */
public class OverlappingLeaveException extends RuntimeException {

    public OverlappingLeaveException(String message) {
        super(message);
    }
}
