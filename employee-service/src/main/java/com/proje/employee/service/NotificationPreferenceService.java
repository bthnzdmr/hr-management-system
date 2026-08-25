package com.proje.employee.service;

import com.proje.employee.dto.NotificationPreferenceResponse;
import com.proje.employee.entity.NotificationKind;
import com.proje.employee.entity.NotificationMute;
import com.proje.employee.exception.UserRuleViolationException;
import com.proje.employee.repository.NotificationMuteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bildirim tercihleri.
 *
 * <p>Tercih KISININ KENDISINE aittir: Ik bile baskasininkine dokunamaz.
 * Baskasinin bildirimini susturabilmek, izin karari gibi kendisini
 * ilgilendiren bir olayi ondan gizleyebilmek demektir.
 */
@Service
public class NotificationPreferenceService {

    private final NotificationMuteRepository mutes;

    public NotificationPreferenceService(NotificationMuteRepository mutes) {
        this.mutes = mutes;
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse forEmployee(Long employeeId) {
        Set<NotificationKind> muted = mutes.findKindsByEmployeeId(employeeId);

        // Cevap BUTUN turleri tasir: arayuzun ayrica "hangi secenekler var"
        // diye sormasi gerekmesin ve liste iki yerde yasamasin.
        List<NotificationPreferenceResponse.Item> items = Arrays.stream(NotificationKind.values())
                .map(kind -> new NotificationPreferenceResponse.Item(
                        kind, kind.label(), !muted.contains(kind)))
                .toList();

        return new NotificationPreferenceResponse(items);
    }

    /**
     * Tercihleri komple degistirir.
     *
     * <p>Once hepsi silinip istenenler yaziliyor. "Farki hesapla" yaklasimi
     * daha az satir yazardi ama iki es zamanli istek arasinda kimsenin
     * istemedigi bir ara duruma dusulebilirdi; komple yazmak istegi
     * IDEMPOTENT yapar.
     */
    @Transactional
    public NotificationPreferenceResponse replace(Long employeeId, Set<NotificationKind> enabled) {
        Set<NotificationKind> muted = EnumSet.allOf(NotificationKind.class);
        muted.removeAll(enabled);

        mutes.deleteByEmployeeId(employeeId);
        mutes.flush();

        muted.forEach(kind -> mutes.save(new NotificationMute(employeeId, kind)));

        return forEmployee(employeeId);
    }

    /**
     * Bir olay yayinlanirken kimin neyi susturdugunu TEK sorguda okur.
     *
     * <p>Personel ve yoneticisi ayri ayri sorulsaydi her olay iki gidis donus
     * ederdi -- toplu okuma, dongu icinde ag cagrisi yapmama kuralinin
     * veritabani karsiligi.
     */
    @Transactional(readOnly = true)
    public Map<Long, Set<NotificationKind>> mutedFor(Set<Long> employeeIds) {
        Map<Long, Set<NotificationKind>> byEmployee = new HashMap<>();

        if (employeeIds.isEmpty()) {
            return byEmployee;
        }

        for (NotificationMute mute : mutes.findAllByEmployeeIdIn(employeeIds)) {
            byEmployee.computeIfAbsent(mute.getEmployeeId(),
                    id -> EnumSet.noneOf(NotificationKind.class)).add(mute.getKind());
        }

        return byEmployee;
    }

    /** Personel kaydi olmayan bir hesap tercih tutamaz. */
    public Long requireEmployeeId(AccessScope scope) {
        if (scope.employeeId() == null) {
            throw new UserRuleViolationException(
                    "This account is not linked to an employee record, so it receives no notifications");
        }
        return scope.employeeId();
    }
}
