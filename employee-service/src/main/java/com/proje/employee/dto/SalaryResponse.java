package com.proje.employee.dto;

import com.proje.employee.audit.AuditLabel;
import com.proje.employee.entity.Employee;

import java.math.BigDecimal;

/**
 * Maas yalnizca bu cevapta doner; genel personel cevabinda yer almaz.
 * Boylece SERVICE rolundeki istemciler (Notification Service dahil) maasi hic gormez.
 *
 * <p>Ad da tasiniyor ve sebebi denetim izi: kayit yalnizca `employeeId`
 * tasisaydi iz "EMPLOYEE #906" derdi ve KIMIN ucretinin degistigi ancak ayri
 * bir aramayla ogrenilirdi. Kod tabaninin en hassas kabul ettigi alanda,
 * "kime" sorusunun cevabi bir tik uzakta olmamali.
 *
 * <p>Ad SIZINTI DEGIL: cagiran zaten o personelin kaydini okuyabiliyor --
 * korunan sey tutardir, kimlik degil.
 */
public record SalaryResponse(Long employeeId, String employeeFullName, BigDecimal salary)
        implements AuditLabel {

    /** Ucret DISARIDAN veriliyor: idempotent yolda degismemis deger donuluyor. */
    public static SalaryResponse of(Employee employee, BigDecimal salary) {
        return new SalaryResponse(employee.getId(),
                employee.getFirstName() + " " + employee.getLastName(),
                salary);
    }

    /**
     * Denetim izinde gorunen ad. Tutar BURAYA DA girmez -- etiket, detay kadar
     * gorunur bir alandir.
     */
    @Override
    public String auditLabel() {
        return employeeFullName;
    }
}
