package com.proje.employee.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Kidem merdiveni ve devir tavani. */
class LeavePolicyTest {

    private final LeavePolicy policy = LeavePolicyFixture.standard();

    // ------------------------------------------------------------- merdiven

    @Test
    @DisplayName("Annual leave is not earned before one full year of service")
    void nothingIsEarnedInTheFirstYear() {
        // Is Kanunu m.53: hak bir yil DOLMADAN dogmaz. Merdivenin ilk basamagi
        // bu yuzden sifir ve bu bir eksiklik degil, kuralin kendisi.
        assertThat(policy.entitledDaysFor(0)).isZero();
    }

    @Test
    @DisplayName("Each tier applies from the year it starts")
    void eachTierAppliesFromItsFirstYear() {
        assertThat(policy.entitledDaysFor(1)).isEqualTo(14);
        assertThat(policy.entitledDaysFor(5)).isEqualTo(20);
        assertThat(policy.entitledDaysFor(15)).isEqualTo(26);
    }

    @Test
    @DisplayName("A year inside a tier keeps that tier's days")
    void yearsInsideATierKeepItsDays() {
        // Sinir kosullari: 4 hala alt basamakta, 5 ust basamakta. Karsilastirma
        // ">=" yerine ">" olsaydi herkes bir yil gec terfi ederdi.
        assertThat(policy.entitledDaysFor(4)).isEqualTo(14);
        assertThat(policy.entitledDaysFor(14)).isEqualTo(20);
    }

    @Test
    @DisplayName("Service beyond the last tier stays at the last tier")
    void serviceBeyondTheLadderStaysAtTheTop() {
        // Merdivenin ustu acik: 40 yillik kidem icin ayri bir basamak yok ve
        // olmamali. Kapali olsaydi en kidemli kisi CEVAPSIZ kalirdi.
        assertThat(policy.entitledDaysFor(40)).isEqualTo(26);
    }

    // ----------------------------------------------------------------- devir

    @Test
    @DisplayName("Unused days carry over up to the cap")
    void carryOverIsCapped() {
        assertThat(policy.carryOverFrom(12)).isEqualTo(5);
    }

    @Test
    @DisplayName("Fewer remaining days than the cap all carry over")
    void lessThanTheCapCarriesOverWhole() {
        assertThat(policy.carryOverFrom(3)).isEqualTo(3);
    }

    @Test
    @DisplayName("A negative balance carries nothing over instead of a negative debt")
    void aNegativeBalanceCarriesNothing() {
        // Bakiye NEGATIF olabilir (hakkindan fazla izin onaylanmis olabilir).
        // Bunu devretmek bir sonraki yila BORC tasimak olurdu -- kolonun
        // CHECK kisiti da zaten reddeder ve tahakkuk isi acilista patlardi.
        assertThat(policy.carryOverFrom(-4)).isZero();
    }

    // --------------------------------------------------- yapilandirma hatasi

    @Test
    @DisplayName("A ladder that does not start at zero refuses to start the application")
    void aLadderNotStartingAtZeroIsRefused() {
        // Sessizce duzeltmek daha kolay olurdu ama kidemi merdivenin altinda
        // kalan herkes -- yani her yeni ise alinan -- cevapsiz kalirdi.
        assertThatThrownBy(() -> new LeavePolicy(20, 5,
                List.of(new LeavePolicy.Tier(1, 14))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must start at 0");
    }

    @Test
    @DisplayName("No ladder at all means everybody gets the default")
    void noLadderFallsBackToTheDefault() {
        // EKSIK merdiven bozuk merdiven DEGIL: bu ozellik yazilmadan onceki
        // davranis tam olarak buydu, yani surpriz uretmiyor. Zorunlu
        // yazildiginda merdiveni tasimayan her baglam acilmaz olmustu --
        // butun test yigini dahil.
        LeavePolicy flat = new LeavePolicy(20, 5, List.of());

        assertThat(flat.entitledDaysFor(0)).isEqualTo(20);
        assertThat(flat.entitledDaysFor(30)).isEqualTo(20);
    }

    @Test
    @DisplayName("Tiers written out of order are sorted, not rejected")
    void tiersOutOfOrderAreSorted() {
        // Sira bir YAZIM detayi, politika degil: yapilandirmayi elle yazan
        // birinin siralamayi bozmasi, hakki yanlis hesaplamak icin sebep olmaz.
        LeavePolicy shuffled = new LeavePolicy(20, 5, List.of(
                new LeavePolicy.Tier(5, 20),
                new LeavePolicy.Tier(0, 0),
                new LeavePolicy.Tier(15, 26),
                new LeavePolicy.Tier(1, 14)));

        assertThat(shuffled.entitledDaysFor(0)).isZero();
        assertThat(shuffled.entitledDaysFor(3)).isEqualTo(14);
        assertThat(shuffled.entitledDaysFor(20)).isEqualTo(26);
    }

    @Test
    @DisplayName("A negative cap refuses to start the application")
    void aNegativeCapIsRefused() {
        assertThatThrownBy(() -> new LeavePolicy(20, -1,
                List.of(new LeavePolicy.Tier(0, 14))))
                .isInstanceOf(IllegalStateException.class);
    }
}
