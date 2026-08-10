package com.proje.notification.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Employee Service'in dondugu cevabin yalnizca burada kullanilan alanlari.
 *
 * Cevabin tamami kopyalanmaz: ihtiyac duyulmayan alani tanimlamak, o alan
 * degistiginde bu servisi sebepsiz yere kirilgan yapar.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmployeeSummary(Long id, String email, Long managerId) {
}
