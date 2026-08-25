package com.proje.employee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Bir personelin SUSTURDUGU bildirim turu.
 *
 * <p>Satirin VARLIGI "gonderme" demektir; yoklugu "gonder". Varsayilan acik ve
 * kodda yazili, tabloda yalnizca ondan SAPMALAR duruyor.
 *
 * <p><b>`Employee`'ye koleksiyon olarak ASILMADI</b> ve gerekcesi olculmus bir
 * derstir: `User.roles` `EAGER` bir koleksiyondu ve sayfalamada personel basina
 * ayri bir SELECT uretiyordu (10 satirlik sayfada 12 sorgu), `@BatchSize` ile
 * kapatildi. Tercihler rollerden farkli olarak yalnizca IKI dar yerde okunuyor
 * -- tercih ucu ve olay yayini -- dolayisiyla her personel sorgusunun tasidigi
 * bir yuk olmalari icin sebep yok. Tercih, Employee'nin degismezlerinin de
 * parcasi degil.
 */
@Entity
@Table(name = "notification_mute")
@IdClass(NotificationMute.Key.class)
public class NotificationMute {

    @Id
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private NotificationKind kind;

    protected NotificationMute() {
    }

    public NotificationMute(Long employeeId, NotificationKind kind) {
        this.employeeId = employeeId;
        this.kind = kind;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public NotificationKind getKind() {
        return kind;
    }

    /** Bilesik birincil anahtar; `Serializable` olmasi JPA'nin sarti. */
    public static class Key implements Serializable {

        private Long employeeId;
        private NotificationKind kind;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(employeeId, key.employeeId) && kind == key.kind;
        }

        @Override
        public int hashCode() {
            return Objects.hash(employeeId, kind);
        }
    }
}
