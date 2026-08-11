package com.proje.employee.event;

// Olay bir OLGU bildirir, emir vermez: CREATED dogru, SEND_MAIL yanlis.
// Uretici ne oldugunu duyurur; ne yapilacagina tuketici karar verir.
//
// Yeni bir tip eklendiginde tuketici tarafinda IKI sey birlikte degismelidir:
// kendi enum'u ve RabbitConfig'teki routing key listesi. Aksi halde olay
// yayinlanir ama kimseye teslim edilmez. Tuketicideki RabbitConfigTest bunu korur.
public enum EmployeeEventType {

    CREATED("employee.created"),
    UPDATED("employee.updated"),
    DEACTIVATED("employee.deactivated"),
    REACTIVATED("employee.reactivated");

    private final String routingKey;

    EmployeeEventType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }
}
