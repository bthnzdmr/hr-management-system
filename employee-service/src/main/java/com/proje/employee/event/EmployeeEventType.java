package com.proje.employee.event;

// Olay bir OLGU bildirir, emir vermez: CREATED dogru, SEND_MAIL yanlis.
// Uretici ne olduğunu duyurur; ne yapilacagina tuketici karar verir.
public enum EmployeeEventType {

    CREATED("employee.created"),
    UPDATED("employee.updated"),
    DEACTIVATED("employee.deactivated");

    private final String routingKey;

    EmployeeEventType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }
}
