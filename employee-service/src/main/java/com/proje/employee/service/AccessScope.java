package com.proje.employee.service;

import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;

/**
 * Bir isteğin personel verisinde NE KADARINI gorebilecegi.
 *
 * SecurityConfig "bu uca girebilir misin" sorusunu cevaplar; bu sinif "hangi
 * satirlari gorebilirsin" sorusunu. Ikisi farkli sorulardir: uc bazli bir kural
 * "bu kayit senin ekibinde mi" diye soramaz, cunku kural satiri hic gormez.
 *
 * Dikey yetkilendirme (hangi uc) ile yatay yetkilendirme (hangi satir)
 * ayrimidir; ikincisi olmadan giris yapan herkes herkesi gorur.
 */
public record AccessScope(Kind kind, Long employeeId) {

    public enum Kind {
        /** Tum kayitlar. Ik uzmani, sistem yoneticisi ve servis hesabi. */
        ALL,
        /** Kendi kaydi ve dogrudan astlari. */
        TEAM,
        /** Yalnizca kendi kaydi. */
        SELF
    }

    public static AccessScope forUser(User user) {
        if (user.hasRole(Role.HR_SPECIALIST)
                || user.hasRole(Role.SYSTEM_ADMIN)
                || user.hasRole(Role.SERVICE)) {
            return new AccessScope(Kind.ALL, null);
        }

        // Personel kaydina bagli olmayan bir insan hesabi kimseyi goremez.
        // Bos liste donmek, "hata yok ama veri de yok" demektir ve dogrusu
        // budur: baglanti kurulmadan kimin kaydi oldugu bilinemez.
        Long employeeId = user.getEmployee() == null ? null : user.getEmployee().getId();

        if (user.hasRole(Role.MANAGER)) {
            return new AccessScope(Kind.TEAM, employeeId);
        }
        return new AccessScope(Kind.SELF, employeeId);
    }

    public boolean isUnrestricted() {
        return kind == Kind.ALL;
    }

    /** Hicbir seye erisemeyen kapsam: rolu var ama personel kaydi yok. */
    public boolean isEmpty() {
        return kind != Kind.ALL && employeeId == null;
    }
}
