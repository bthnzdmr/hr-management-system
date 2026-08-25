package com.proje.employee.controller;

import com.proje.employee.audit.AuditAction;
import com.proje.employee.audit.Auditable;
import com.proje.employee.dto.NotificationPreferenceResponse;
import com.proje.employee.dto.NotificationPreferenceUpdateRequest;
import com.proje.employee.service.AccessScope;
import com.proje.employee.service.AccessScopeResolver;
import com.proje.employee.service.NotificationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * Kisinin KENDI bildirim tercihleri.
 *
 * <p>Yol `/me` ile bitiyor ve baska bir kisinin tercihini okuyan ya da yazan
 * bir uc YOK. Baskasinin bildirimini susturabilmek, kendisini ilgilendiren bir
 * olayi ondan gizleyebilmek demektir -- Ik bile bunu yapamamali.
 */
@RestController
@RequestMapping("/api/notification-preferences")
@Tag(name = "Notification preferences", description = "Which notifications a person wants to receive")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferences;
    private final AccessScopeResolver accessScopeResolver;

    public NotificationPreferenceController(NotificationPreferenceService preferences,
                                            AccessScopeResolver accessScopeResolver) {
        this.preferences = preferences;
        this.accessScopeResolver = accessScopeResolver;
    }

    @GetMapping("/me")
    @Operation(summary = "Read my notification preferences")
    @ApiResponse(responseCode = "409",
            description = "The account is not linked to an employee record")
    public NotificationPreferenceResponse mine(Principal caller) {
        return preferences.forEmployee(employeeId(caller));
    }

    /**
     * Tercihleri komple degistirir.
     *
     * <p>Denetleniyor: bir bildirimin neden gitmedigi sorulabilmeli. Yalnizca
     * kisinin kendi tercihi oldugu icin bu bir gizlilik sizintisi degil --
     * kaydedilen sey kisinin kendi karari.
     */
    @PutMapping("/me")
    @Auditable(action = AuditAction.NOTIFICATION_PREFERENCES_CHANGED,
            targetType = "NOTIFICATION_PREFERENCE")
    @Operation(summary = "Replace my notification preferences")
    @ApiResponse(responseCode = "409",
            description = "The account is not linked to an employee record")
    public NotificationPreferenceResponse replaceMine(
            @Valid @RequestBody NotificationPreferenceUpdateRequest request, Principal caller) {

        return preferences.replace(employeeId(caller), request.enabled());
    }

    private Long employeeId(Principal caller) {
        AccessScope scope = accessScopeResolver.resolve(caller);
        return preferences.requireEmployeeId(scope);
    }
}
