package com.proje.employee.audit;

import com.proje.employee.config.CorrelationIdFilter;
import com.proje.employee.entity.TerminationReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Aspect GERCEK bir proxy uzerinden sinanir.
 *
 * Aspect metodunu dogrudan cagirmak, "anotasyon gercekten devreye giriyor mu"
 * sorusunu hic sormazdi -- pointcut yanlis yazilsa bile test gecerdi. Burada
 * kurulan proxy, uretimdeki mekanizmanin aynisidir.
 */
@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock
    private AuditEntryRepository auditEntryRepository;

    /** Denetlenen ornek servis: gercek servislerin imza sekillerini taklit eder. */
    static class SampleService {

        @Auditable(action = AuditAction.ROLES_CHANGED, targetType = "USER")
        String changeRoles(Long id, List<String> roles, String actingUserEmail) {
            return "ok";
        }

        @Auditable(action = AuditAction.SALARY_CHANGED, targetType = "EMPLOYEE",
                includeArguments = false)
        String updateSalary(Long id, String secretAmount) {
            return "ok";
        }

        @Auditable(action = AuditAction.ACCOUNT_CREATED, targetType = "USER")
        Created create(String email) {
            return new Created(42L);
        }

        @Auditable(action = AuditAction.ACCOUNT_STATUS_CHANGED, targetType = "USER")
        String failing(Long id) {
            throw new IllegalStateException("business rule violated");
        }

        String notAudited(Long id) {
            return "ok";
        }

        @Auditable(action = AuditAction.EMPLOYEE_STATUS_CHANGED, targetType = "EMPLOYEE")
        String changeStatus(Long id, boolean active, TerminationReason reason) {
            return "ok";
        }

        /** Ayni eylemin iki ayri metodu: sonuc yalnizca ozette yazilidir. */
        @Auditable(action = AuditAction.LEAVE_DECIDED, targetType = "LEAVE_REQUEST",
                includeArguments = false, summary = "approved")
        String approve(Long id) {
            return "ok";
        }

        /** toString'i OLMAYAN bir nesne alan denetimli metot. */
        @Auditable(action = AuditAction.LEAVE_REQUESTED, targetType = "LEAVE_REQUEST")
        String withEntity(Long id, OpaqueEntity author) {
            return "ok";
        }
    }

    /** Entity'lerin cogu boyledir: toString yok, varsayilani kimlik karmasi. */
    static class OpaqueEntity {
    }

    record Created(Long id) {
    }

    private SampleService service() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(new AuditAspect(auditEntryRepository));
        return factory.getProxy();
    }

    private AuditEntry captured() {
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditEntryRepository).save(captor.capture());
        return captor.getValue();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    @DisplayName("Records who changed which account, and to what")
    void recordsRoleChange() {
        // Denetim raporundaki bulgu: rol degisimi hicbir yere yazilmiyordu ve
        // "SYSTEM_ADMIN kendine HR_SPECIALIST verdi" olayi SONRADAN TESPIT
        // EDILEMEZDI. Kaydin cevapladigi soru tam olarak budur.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@example.com", null, List.of()));

        service().changeRoles(7L, List.of("SYSTEM_ADMIN", "HR_SPECIALIST"), "admin@example.com");

        AuditEntry entry = captured();
        assertThat(entry.getActor()).isEqualTo("admin@example.com");
        assertThat(entry.getAction()).isEqualTo(AuditAction.ROLES_CHANGED);
        assertThat(entry.getTargetType()).isEqualTo("USER");
        assertThat(entry.getTargetId()).isEqualTo("7");
        assertThat(entry.getDetail()).contains("HR_SPECIALIST");
    }

    @Test
    @DisplayName("Labels each value so the trail says what changed")
    void labelsArguments() {
        // Etiketsizken kayit "false, RESIGNED" goruntusundeydi: NEYIN false
        // oldugu okunamiyordu ve iz ancak metodun imzasi bilinerek anlasiliyordu.
        service().changeStatus(7L, false, TerminationReason.RESIGNED);

        assertThat(captured().getDetail())
                .isEqualTo("active=false, reason=RESIGNED");
    }

    @Test
    @DisplayName("Records which decision was taken, not merely that one was")
    void recordsTheDecisionItself() {
        // approve/reject/cancel ucu de LEAVE_DECIDED'dir ve sonuc argumanlarda
        // YOKTUR -- karar metodun kendisidir. Ozet olmadan iz "birisi bir karar
        // verdi" demekten oteye gecmiyordu.
        service().approve(7L);

        assertThat(captured().getDetail()).isEqualTo("approved");
    }

    @Test
    @DisplayName("Never writes the salary itself into the audit trail")
    void doesNotLeakSalary() {
        // Butun tasarim maasi dar bir yetki cemberinde tutuyor. Denetim tablosu
        // o cemberi delen bir arka kapi olamaz: kaydi okuyabilen herkes ucreti
        // de okurdu.
        service().updateSalary(7L, "95000");

        AuditEntry entry = captured();
        assertThat(entry.getAction()).isEqualTo(AuditAction.SALARY_CHANGED);
        assertThat(entry.getTargetId()).isEqualTo("7");
        assertThat(entry.getDetail()).isNull();
    }

    @Test
    @DisplayName("Takes the target id from the response when the call had none")
    void readsIdFromResponse() {
        // "create" gibi metotlarda kimlik ancak kayit yazildiktan sonra bellidir.
        service().create("new@example.com");

        assertThat(captured().getTargetId()).isEqualTo("42");
    }

    @Test
    @DisplayName("Does not record a call that threw")
    void doesNotRecordFailedCall() {
        // Istisna firlatan cagri hicbir sey degistirmedi; onu "yapildi" diye
        // yazmak izi YALANCI kilardi.
        assertThatThrownBy(() -> service().failing(7L))
                .isInstanceOf(IllegalStateException.class);

        verify(auditEntryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Leaves unannotated methods alone")
    void ignoresUnannotatedMethods() {
        // Kesisim noktasi anotasyona bagli, paket adina degil: yeni bir servis
        // metodu eklemek kapsami sessizce genisletmemeli.
        service().notAudited(7L);

        verify(auditEntryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Carries the correlation id so the trail joins the application logs")
    void carriesCorrelationId() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "trace-1");

        service().create("new@example.com");

        assertThat(captured().getCorrelationId()).isEqualTo("trace-1");
    }

    @Test
    @DisplayName("Falls back to a system actor when nobody is signed in")
    void recordsSystemActorWithoutAuthentication() {
        // Tohumlayici ve zamanlanmis isler de bu yoldan gecebilir.
        service().create("new@example.com");

        assertThat(captured().getActor()).isEqualTo("system");
    }

    @Test
    @DisplayName("A failing audit write must not bring down the business operation")
    void auditFailureDoesNotBreakTheCall() {
        // Izleme mekanizmasi, sistemin kendisini kirilgan hale getirmemeli:
        // kayit yazilamiyorsa personel guncellemesi de iptal olsaydi denetim
        // eklemek sistemi ZAYIFLATIRDI.
        doThrow(new RuntimeException("audit table is gone"))
                .when(auditEntryRepository).save(org.mockito.ArgumentMatchers.any());

        assertThat(service().create("new@example.com")).isEqualTo(new Created(42L));
    }

    @Test
    @DisplayName("Keeps an object with no readable form out of the trail")
    void skipsIdentityHashes() {
        // Olculdu: "com.proje...User@7f67b234" yaziliyordu -- 500 karakterlik
        // detay butcesini yiyen, hicbir sey anlatmayan gurultu. Daha onemlisi
        // ileriye donuk risk: entity'ye toString eklendigi gun butun alanlari
        // (ornegin passwordHash) ize dokulurdu.
        service().withEntity(7L, new OpaqueEntity());

        // Okunabilir hicbir arguman kalmadigi icin detay BOS: kimlik karmasi
        // yazmaktansa hic yazmamak dogru -- kayit yine dusuyor, yalnizca
        // anlamsiz gurultu tasimiyor.
        assertThat(captured().getDetail()).isNull();
    }
}
