package com.proje.employee.config;

import java.util.List;

/**
 * Testler icin izin politikasi.
 *
 * Merdiven `application.yml`'dekiyle AYNI (Is Kanunu m.53): testin kendi
 * uydurdugu bir merdiven kullanmasi, sinanan seyin uretimde gecerli olmadigi
 * anlamina gelirdi.
 */
public final class LeavePolicyFixture {

    private static final List<LeavePolicy.Tier> LADDER = List.of(
            new LeavePolicy.Tier(0, 0),
            new LeavePolicy.Tier(1, 14),
            new LeavePolicy.Tier(5, 20),
            new LeavePolicy.Tier(15, 26));

    private LeavePolicyFixture() {
    }

    public static LeavePolicy standard() {
        return new LeavePolicy(20, 5, LADDER);
    }

    public static LeavePolicy withDefaultDays(int defaultAnnualDays) {
        return new LeavePolicy(defaultAnnualDays, 5, LADDER);
    }

    public static LeavePolicy withCarryOverCap(int cap) {
        return new LeavePolicy(20, cap, LADDER);
    }
}
