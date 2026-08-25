package com.proje.notification.service;

import com.proje.notification.event.LeaveEvent;
import com.proje.notification.event.LeaveEventType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Izin olaylarini maile cevirir.
 *
 * <p><b>ALICIYI BU SERVIS SECER.</b> Olay yalnizca olguyu tasir
 * (`employeeEmail`, `managerEmail`, `actorEmail`); "suna gonder" demek olayi
 * bir emre cevirirdi ve projenin kurucu kurallarindan biri bunun tersi.
 */
@Service
public class LeaveMailService {

    private static final Logger log = LoggerFactory.getLogger(LeaveMailService.class);

    /**
     * Tarih bicimi arayuzdekiyle AYNI ve yerel ayara HIC bakmiyor.
     *
     * `Intl`/varsayilan `Locale` kullanilsaydi ayrac ve sira makineye gore
     * degisir, ayni mail her sunucuda farkli basar ve test edilemezdi -- ayni
     * karar arayuzde ve ucret bicimlendirmesinde de verilmisti.
     */
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    /**
     * Uc SONUC birden sayilir, yalnizca hata degil.
     *
     * "Sifir hata", *her sey yolunda* ile *hic mail gonderilmedi* arasinda
     * ayrim yapmaz. Ustelik mailin gitmemesinin iki mesru sebebi disaridan
     * AYNI gorunur: alici yok (yoneticisi olmayan personel) ya da kisi kendi
     * yaptigi islemi bildirmeye deger bulunmadi.
     *
     * Etiket kumesi KAPALI: istisna sinifiyla etiketlemek kardinalite
     * patlatirdi.
     */
    private final Counter sent;
    private final Counter noRecipient;
    private final Counter selfAction;
    private final Counter muted;

    public LeaveMailService(JavaMailSender mailSender,
                            @Value("${app.mail.from}") String fromAddress,
                            MeterRegistry registry) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;

        // Sayaclar ACILISTA kaydedilir: dokunulmamis bir seri Prometheus'ta hic
        // gorunmez ve ona dayanan bir kural sessizce hicbir zaman atesLENMEZ.
        this.sent = counter(registry, "sent");
        this.noRecipient = counter(registry, "no_recipient");
        this.selfAction = counter(registry, "self_action");

        // AYRI bir sonuc: "kisi istemedi" ile "alici yok" ayni sey degil.
        // Ikisi de mail gitmemesiyle sonuclanir ve disaridan ayni gorunur --
        // tam da bu ayrimi kaybetmemek icin uc sonuc sayilmaya baslanmisti.
        this.muted = counter(registry, "muted");
    }

    private static Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder("hr.leave.notification")
                .description("Leave notifications handled by the consumer")
                .tag("outcome", outcome)
                .register(registry);
    }

    public void send(LeaveEvent event) {
        Optional<String> recipient = recipientFor(event);

        if (recipient.isEmpty()) {
            return;
        }

        MimeMessage message = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(fromAddress);
            helper.setTo(recipient.get());
            helper.setSubject(subject(event));
            helper.setText(body(event), html(event));
        } catch (MessagingException cause) {
            throw new MailPreparationException("Could not build the leave mail", cause);
        }

        mailSender.send(message);
        sent.increment();
    }

    /**
     * Kim bilgilendirilmeli?
     *
     * <ul>
     *   <li>Talep acildi -> KARAR VERECEK kisi, yani yonetici.
     *   <li>Karara baglandi -> talebin sahibi.
     *   <li>Geri cekildi -> sahibi, ama kendisi cekmediyse.
     * </ul>
     *
     * Kisinin kendi yaptigi islemi kendisine bildirmek gurultudur ve gurultulu
     * bildirim, gormezden gelinen bildirimdir.
     */
    private Optional<String> recipientFor(LeaveEvent event) {
        // Kisinin kendi cektigi talebi kendisine bildirmek gurultudur ve
        // gurultulu bildirim, gormezden gelinen bildirimdir.
        if (event.eventType() == LeaveEventType.CANCELLED && selfCancelled(event)) {
            selfAction.increment();
            return Optional.empty();
        }

        String candidate = switch (event.eventType()) {
            case REQUESTED -> event.managerEmail();
            case DECIDED, CANCELLED -> event.employeeEmail();
        };

        if (candidate == null || candidate.isBlank()) {
            // Yoneticisi olmayan personel bir VERI HATASI degil: organizasyonun
            // tepesindeki departman baskanlarinin yoneticisi yoktur. Olay yine
            // de yayinlandi cunku YASANDI.
            noRecipient.increment();
            log.info("No recipient for leave event {} ({})", event.eventId(), event.eventType());
            return Optional.empty();
        }

        // Tercih olay ANINDA okunmustu ve yukte tasiniyor; burada yalnizca
        // KARAR veriliyor. Alan "gonderme" demiyor, "susturmustu" diyor.
        if (isMuted(event)) {
            muted.increment();
            return Optional.empty();
        }

        return Optional.of(candidate);
    }

    /**
     * Alici bu turu susturmus muydu?
     *
     * <p>Talep maili YONETICIYE gider, dolayisiyla bakilacak tercih de
     * yoneticinindir; karar ve geri cekilme mailleri personele gider.
     * Yanlis kisinin tercihine bakmak, susturmayi baskasi adina uygulamak
     * olurdu.
     *
     * <p>Taninmayan bir ad kume ICINDE gelirse zararsizdir: karsilastirma
     * metin uzerinden ve uretici yeni bir tur eklediginde bu servis
     * KIRILMAZ -- ayni gerekce `ignoreUnknown` icin de gecerli.
     */
    private boolean isMuted(LeaveEvent event) {
        Set<String> preference = switch (event.eventType()) {
            case REQUESTED -> event.managerMuted();
            case DECIDED, CANCELLED -> event.employeeMuted();
        };

        return preference != null && preference.contains(kindOf(event.eventType()));
    }

    /** Olay tipinin karsiligi olan tercih adi. */
    private String kindOf(LeaveEventType type) {
        return type == LeaveEventType.REQUESTED ? "LEAVE_REQUEST" : "LEAVE_DECISION";
    }

    private boolean selfCancelled(LeaveEvent event) {
        return event.actorEmployeeId() != null
                && event.actorEmployeeId().equals(event.employeeId());
    }

    private String subject(LeaveEvent event) {
        return switch (event.eventType()) {
            case REQUESTED -> event.employeeFullName() + " requested leave";
            case DECIDED -> "Your leave request was " + statusWord(event);
            case CANCELLED -> "Your leave request was withdrawn";
        };
    }

    private String statusWord(LeaveEvent event) {
        // Locale.ROOT SART: Turkce bir makinede "I" noktasiz "i"ye doner ve
        // "REJECTED" bozulur. Ayni tuzak denetim izinde bir kez yasandi.
        return event.status() == null ? "decided" : event.status().toLowerCase(Locale.ROOT);
    }

    private String span(LeaveEvent event) {
        String from = day(event.startDate());
        String to = day(event.endDate());
        String count = event.days() + (event.days() == 1 ? " day" : " days");

        return from.equals(to) ? from + " (1 day)" : from + " - " + to + " (" + count + ")";
    }

    private String day(LocalDate date) {
        return date == null ? "" : DAY.format(date);
    }

    private String body(LeaveEvent event) {
        StringBuilder text = new StringBuilder(headline(event))
                .append("\n\n")
                .append("Type: ").append(event.leaveType()).append('\n')
                .append("Dates: ").append(span(event)).append('\n');

        if (event.note() != null && !event.note().isBlank()) {
            text.append("Note: ").append(event.note()).append('\n');
        }
        if (event.decisionNote() != null && !event.decisionNote().isBlank()) {
            text.append("Decision note: ").append(event.decisionNote()).append('\n');
        }

        return text.toString();
    }

    private String headline(LeaveEvent event) {
        return switch (event.eventType()) {
            case REQUESTED -> event.employeeFullName() + " asked for time off and needs a decision.";
            case DECIDED -> "Your leave request was " + statusWord(event) + ".";
            case CANCELLED -> "Your leave request was withdrawn before a decision was made.";
        };
    }

    private String html(LeaveEvent event) {
        StringBuilder facts = new StringBuilder();
        facts.append(MailTemplate.facts(
                "Type", event.leaveType(),
                "Dates", span(event)));

        if (event.note() != null && !event.note().isBlank()) {
            facts.append(MailTemplate.note(event.note()));
        }
        if (event.decisionNote() != null && !event.decisionNote().isBlank()) {
            facts.append(MailTemplate.note(event.decisionNote()));
        }

        return MailTemplate.page(headline(event),
                MailTemplate.paragraph(headline(event)) + facts);
    }
}
