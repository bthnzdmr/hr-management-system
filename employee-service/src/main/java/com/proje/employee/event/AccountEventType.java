package com.proje.employee.event;

// Personel olaylarindan AYRI bir aile: bu bir hesap olgusudur, bir personel
// olgusu degil. Ayni exchange'e yayinlanir (topic exchange birden fazla anahtar
// ailesini tasir) ama kendi kuyruguna baglanir.
//
// EmployeeEventType'a eklenemezdi: tuketicideki RabbitConfigTest o enum'un her
// degerinin personel kuyruguna bagli olmasini iddia ediyor ve parola sifirlama
// oraya teslim edilmemeli.
public enum AccountEventType {

    PASSWORD_RESET_REQUESTED("account.password-reset-requested"),
    INVITED("account.invited");

    private final String routingKey;

    AccountEventType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }
}
