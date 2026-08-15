package com.proje.employee.controller;

import com.proje.employee.dto.LeaveDecisionRequest;
import com.proje.employee.dto.LeaveRequestCreateRequest;
import com.proje.employee.dto.LeaveRequestResponse;
import com.proje.employee.entity.LeaveStatus;
import com.proje.employee.entity.User;
import com.proje.employee.exception.StaleCredentialsException;
import com.proje.employee.repository.UserRepository;
import com.proje.employee.service.AccessScope;
import com.proje.employee.service.AccessScopeResolver;
import com.proje.employee.service.LeaveRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;
import java.util.List;

@Tag(name = "Leave", description = "Izin istekleri. Kayitlari Ik personel adina girer.")
@RestController
@RequestMapping("/api/leave-requests")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final AccessScopeResolver accessScopeResolver;
    private final UserRepository userRepository;

    public LeaveRequestController(LeaveRequestService leaveRequestService,
                                  AccessScopeResolver accessScopeResolver,
                                  UserRepository userRepository) {
        this.leaveRequestService = leaveRequestService;
        this.accessScopeResolver = accessScopeResolver;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Operation(summary = "Izin istekleri",
            description = "Kapsam role gore daralir: Ik hepsini, yonetici kendisi ve dogrudan "
                    + "astlarini, calisan yalnizca kendi kayitlarini gorur.")
    public Page<LeaveRequestResponse> list(
            @RequestParam(required = false) List<LeaveStatus> status,
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC)
            Pageable pageable,
            Principal caller) {

        return leaveRequestService.list(status, accessScopeResolver.resolve(caller), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Tek izin istegi")
    @ApiResponse(responseCode = "404",
            description = "Kayit yok VEYA kapsam disinda: 403 donmek kaydin varligini "
                    + "dogrulardi ve id deneyerek sayi ogrenilebilirdi")
    public LeaveRequestResponse getById(@PathVariable Long id, Principal caller) {
        return leaveRequestService.getById(id, accessScopeResolver.resolve(caller));
    }

    @PostMapping
    @Operation(summary = "Personel adina izin gir",
            description = "Kaydi giren hesap jetondan okunur; govdede yer almaz.")
    @ApiResponse(responseCode = "201", description = "Olusturuldu")
    @ApiResponse(responseCode = "409",
            description = "Bu personelin o tarihlerde zaten izni var, ya da personel ayrilmis")
    public ResponseEntity<LeaveRequestResponse> create(
            @Valid @RequestBody LeaveRequestCreateRequest request, Principal caller) {

        LeaveRequestResponse created = leaveRequestService.create(request, currentUser(caller));

        return ResponseEntity
                .created(URI.create("/api/leave-requests/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}/decision")
    @Operation(summary = "Istegi sonuclandir",
            description = "Onayla, reddet veya geri cek. Tek uc uc yone birden calisir; "
                    + "yeni bir durum eklemek yeni bir uc gerektirmez.")
    @ApiResponse(responseCode = "409", description = "Istek zaten sonuclanmis")
    public LeaveRequestResponse decide(@PathVariable Long id,
                                       @Valid @RequestBody LeaveDecisionRequest request,
                                       Principal caller) {

        AccessScope scope = accessScopeResolver.resolve(caller);

        return switch (request.status()) {
            case APPROVED -> leaveRequestService.approve(id, currentUser(caller), scope);
            case REJECTED -> leaveRequestService.reject(id, request.note(), currentUser(caller), scope);
            case CANCELLED -> leaveRequestService.cancel(id, scope);
            // PENDING dogrulamada zaten reddedildi; switch'in tam olmasi icin.
            case PENDING -> throw new IllegalStateException("Unreachable: validated by the request");
        };
    }

    /** Istegi yapan hesap. */
    private User currentUser(Principal caller) {
        return userRepository.findByEmail(caller.getName())
                .orElseThrow(() -> new StaleCredentialsException(caller.getName()));
    }
}
