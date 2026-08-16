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
public record AccessScope(Kind kind, Long employeeId, boolean allSalaries) {

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
                || user.hasRole(Role.PAYROLL_SPECIALIST)
                || user.hasRole(Role.SYSTEM_ADMIN)
                || user.hasRole(Role.SERVICE)) {
            return new AccessScope(Kind.ALL, null, user.hasRole(Role.PAYROLL_SPECIALIST));
        }

        // Personel kaydina bagli olmayan bir insan hesabi kimseyi goremez.
        // Bos liste donmek, "hata yok ama veri de yok" demektir ve dogrusu
        // budur: baglanti kurulmadan kimin kaydi oldugu bilinemez.
        Long employeeId = user.getEmployee() == null ? null : user.getEmployee().getId();

        if (user.hasRole(Role.MANAGER)) {
            return new AccessScope(Kind.TEAM, employeeId, false);
        }
        return new AccessScope(Kind.SELF, employeeId, false);
    }

    /**
     * Kapsam dogrudan astlari da iceriyor mu?
     *
     * Onceden cagiran taraf "scope.kind() == Kind.TEAM" diye SORUYORDU ve ayni
     * karsilastirma iki ayri yerde tekrarlaniyordu. Bu, AccessScope'un kendi
     * semantiginin disari SIZMASIYDI: kapsam turu degistiginde her cagiran
     * yeri duzeltmek gerekirdi.
     */
    public boolean includesDirectReports() {
        return kind == Kind.TEAM;
    }

    /** Sorguya verilecek kimlik; sinirsiz kapsamda filtre yok demektir. */
    public Long visibleEmployeeId() {
        return isUnrestricted() ? null : employeeId;
    }

    public boolean isUnrestricted() {
        return kind == Kind.ALL;
    }

    /**
     * Baskasinin ucretini okuyabilir mi?
     *
     * AYRI bir eksen olmak ZORUNDA. Once "isUnrestricted()" soruluyordu ve
     * roller BIRLESINCE en dar degil EN GENIS yetki kazaniyordu: uc kurali
     * rollerin VEYA'si oldugu icin EMPLOYEE kapiyi aciyor, HR_SPECIALIST ise
     * satir filtresini kaldiriyordu. Olculdu -- [EMPLOYEE, HR_SPECIALIST,
     * MANAGER, SYSTEM_ADMIN] tasiyan tohum hesabi BUTUN maaslari okuyordu.
     */
    public boolean includesAllSalaries() {
        return allSalaries;
    }

    /** Hicbir seye erisemeyen kapsam: rolu var ama personel kaydi yok. */
    public boolean isEmpty() {
        return kind != Kind.ALL && employeeId == null;
    }
}
