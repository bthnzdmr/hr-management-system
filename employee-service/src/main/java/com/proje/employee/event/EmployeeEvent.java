package com.proje.employee.event;

import java.time.Instant;

// Iki servis arasindaki sozlesme. Alan eklemek guvenlidir, alan cikarmak veya
// yeniden adlandirmak tuketiciyi kirar.
//
// Maas ve telefon bilincli olarak YOK: olay mail icerigine donusuyor ve hassas
// veri sifresiz SMTP'ye tasinmamali. Tuketicinin detaya ihtiyaci olursa Feign
// ile sorar.
public record EmployeeEvent(

        // Ayni mesaj tekrar teslim edilebilir; tuketici bu kimlikle
        // "bunu zaten isledim" diyebilir.
        String eventId,

        EmployeeEventType eventType,
        Instant occurredAt,

        Long employeeId,
        String firstName,
        String lastName,
        String email,
        String departmentName,
        String jobTitle
) {
}
