package com.proje.employee.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bu metodun basarili calismasi denetim izine yazilir.
 *
 * Kesisim noktasi ANOTASYONA baglidir, paket adina degil. Paket bazli bir
 * pointcut ("service paketindeki her sey") iki sey birden yapardi: gereksiz
 * okumalari da kaydeder ve yeni bir servis eklendiginde sessizce kapsam
 * degistirirdi. Anotasyon, neyin denetlendigini KOD OKUNARAK gorulebilir kilar.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** Yapilan is: ROLES_CHANGED, ACCOUNT_CREATED, EMPLOYEE_TERMINATED... */
    AuditAction action();

    /** Neye yapildi: USER, EMPLOYEE. */
    String targetType();

    /**
     * Arguman ozeti denetim kaydina yazilsin mi?
     *
     * Varsayilan true: "hangi roller verildi", "hangi ayrilis sebebi girildi"
     * gibi bilgiler izin en degerli kismidir.
     *
     * Maas guncellemesinde FALSE: "kim, kimin ucretini degistirdi" denetlenmeye
     * deger ama TUTARIN KENDISI ize dusmemelidir. Butun tasarim maasi dar bir
     * yetki cemberinde tutuyor; denetim tablosu o cemberi delen bir arka kapi
     * olamaz -- kaydi okuyabilen herkes ucreti de okurdu.
     */
    boolean includeArguments() default true;
}
