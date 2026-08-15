package com.proje.employee.exception;

/** Izin isteginin is kurallarina takilmasi (ornegin ayrilmis personele izin). */
public class LeaveRuleViolationException extends RuntimeException {

    public LeaveRuleViolationException(String message) {
        super(message);
    }
}
