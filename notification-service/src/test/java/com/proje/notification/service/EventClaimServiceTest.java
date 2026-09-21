package com.proje.notification.service;

import com.proje.notification.entity.ProcessedEvent;
import com.proje.notification.event.EmployeeEvent;
import com.proje.notification.event.EmployeeEventType;
import com.proje.notification.listener.EmployeeEventListener;
import com.proje.notification.repository.ProcessedEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Sahiplenme mantigi GERCEK veritabanina karsi sinanir.
 *
 * <p>Bu test iki olcumden dogdu.
 *
 * <p><b>Birincisi (uretim kusuru):</b> sahiplenme dinleyicinin kendi
 * transaction'i icinde yapiliyordu. Birincil anahtar ihlal edildiginde
 * Hibernate transaction'i <b>rollback-only</b> isaretler; istisna yakalanip
 * {@code false} donulse bile dinleyici normal bitince commit denenir ve
 * {@code UnexpectedRollbackException} firlar. Mesaj reddedilir, bir retry
 * hakki yanar ve tamamen normal bir yaris korkutucu bir yigin izi basar.
 * Birim test bunu goremezdi: Mockito sahtesinde geri alinacak bir transaction
 * yoktur.
 *
 * <p><b>Ikincisi (TEST kusuru):</b> ilk yazdigim test ayni olayi iki kez
 * sahiplenmeye calisiyordu ve <em>kirilamiyordu</em> -- {@code claim}
 * icindeki hizli yol ({@code existsById}) ikinci cagride hemen doner ve
 * cakisma hic olusmaz. {@code REQUIRES_NEW} kaldirilinca bile test yesil
 * kaliyordu.
 *
 * <p>Cozum, hizli yolu bilerek atlatmak: {@code existsById} sahte olarak
 * {@code false} dondurulur, satir ise veritabaninda GERCEKTEN vardir. Boylece
 * {@code saveAndFlush} gercek bir birincil anahtar ihlaline girer -- yani iki
 * tuketicinin ayni anda kontrolu gectigi anin ta kendisi.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventClaimServiceTest {

    @Autowired
    private EventClaimService eventClaimService;

    /** Dis transaction'i uretimdeki gibi kuran taraf. */
    @Autowired
    private EmployeeEventListener listener;

    @MockitoSpyBean
    private ProcessedEventRepository processedEventRepository;

    @MockitoBean
    private NotificationMailService mailService;

    @MockitoBean
    private ManagerLookupService managerLookupService;

    private UUID written;

    private EmployeeEvent event(UUID eventId) {
        return new EmployeeEvent(eventId, EmployeeEventType.CREATED, Instant.now(),
                42L, "Grace", "Hopper", "grace@example.com", "Sales", "Engineer");
    }

    @AfterEach
    void cleanUp() {
        // Test transaction ICINDE calismiyor (REQUIRES_NEW'in gercekten yeni
        // bir transaction acmasi icin dis transaction gercek olmali), bu
        // yuzden yazilanlar kalicidir ve elle temizlenir.
        if (written != null) {
            processedEventRepository.deleteById(written);
            written = null;
        }
    }

    @Test
    @DisplayName("Claims an event the first time it is seen")
    void claimsNewEvent() {
        written = UUID.randomUUID();

        assertThat(eventClaimService.claim(written, "CREATED", 42L)).isTrue();
        assertThat(processedEventRepository.findById(written)).isPresent();
    }

    @Test
    @DisplayName("Refuses to claim an event that is already recorded")
    void refusesSecondClaim() {
        written = UUID.randomUUID();
        eventClaimService.claim(written, "CREATED", 42L);

        assertThat(eventClaimService.claim(written, "CREATED", 42L)).isFalse();
    }

    @Test
    @DisplayName("A lost race does not poison the caller's transaction")
    void lostRaceDoesNotPoisonTheOuterTransaction() {
        written = UUID.randomUUID();
        // Satir gercekten var...
        processedEventRepository.saveAndFlush(
                new ProcessedEvent(written, "CREATED", 42L));

        // ...ama hizli yol onu GORMUYOR: iki tuketicinin de kontrolu gectigi
        // an bu sekilde taklit edilir. saveAndFlush gercek bir birincil
        // anahtar ihlaline girer.
        doReturn(false).when(processedEventRepository).existsById(written);

        // REQUIRES_NEW olmasaydi ihlal DINLEYICININ transaction'ini
        // rollback-only isaretler ve bu cagri UnexpectedRollbackException
        // firlatirdi -- mesaj reddedilir, bir retry hakki yanardi.
        assertThatCode(() -> listener.onEmployeeEvent(event(written), null))
                .doesNotThrowAnyException();

        // Kaybeden taraf maili HENUZ gondermemis olmalidir.
        verify(mailService, never()).send(any(), any());
    }

    @Test
    @DisplayName("A mail failure releases the claim so the redelivery can retry")
    void mailFailureReleasesTheClaim() {
        written = UUID.randomUUID();
        doThrow(new IllegalStateException("smtp down")).when(mailService).send(any(), any());

        assertThatThrownBy(() -> listener.onEmployeeEvent(event(written), null))
                .isInstanceOf(IllegalStateException.class);

        // Sahiplenme KENDI transaction'inda commit edilir, yani dinleyicideki
        // geri alma ona dokunmaz. Telafi edilmezse yeniden teslim "zaten
        // islendi" der, mesaj ACK'lenir ve mail SESSIZCE kaybolur -- DLQ'ya
        // bile dusmez. Olculdu.
        assertThat(processedEventRepository.findById(written)).isEmpty();
    }

    @Test
    @DisplayName("The row written before the race is still there afterwards")
    void theWinningRowSurvives() {
        written = UUID.randomUUID();
        processedEventRepository.saveAndFlush(
                new ProcessedEvent(written, "CREATED", 42L));
        doReturn(false).when(processedEventRepository).existsById(written);

        listener.onEmployeeEvent(event(written), null);

        // REQUIRES_NEW ihlali YALNIZCA kendi kucuk transaction'ini geri alir;
        // kazananin kaydi yerinde kalmalidir.
        //
        // findById SAHTELENMEZ: sahtelenseydi iddia kendi kurgusunu dogrular
        // ve hicbir sey kanitlamazdi. Gercek sorgu calisir.
        assertThat(processedEventRepository.findById(written)).isPresent();
    }
}
