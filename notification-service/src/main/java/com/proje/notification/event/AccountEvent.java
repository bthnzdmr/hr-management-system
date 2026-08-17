package com.proje.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Hesap olayi. Alan eklemek geriye donuk uyumludur; taninmayan alanlar yok
 * sayilir, boylece uretici olaya alan eklediginde tuketici kirilmaz.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountEvent(

        UUID eventId,
        AccountEventType eventType,
        Instant occurredAt,

        Long userId,
        String email,
        String resetToken,
        Instant expiresAt
) {

    /** Jeton metinsel temsile HIC girmez; uretici tarafinda ayni maskeleme var. */
    @Override
    public String toString() {
        return "AccountEvent[eventId=" + eventId + ", eventType=" + eventType
                + ", userId=" + userId + ", resetToken=***, expiresAt=" + expiresAt + "]";
    }
}
