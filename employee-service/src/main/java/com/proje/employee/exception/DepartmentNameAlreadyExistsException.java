package com.proje.employee.exception;

public class DepartmentNameAlreadyExistsException extends RuntimeException {

    public DepartmentNameAlreadyExistsException(String name) {
        super("A department named \"" + name + "\" already exists");
    }
}
