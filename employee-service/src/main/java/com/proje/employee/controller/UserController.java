package com.proje.employee.controller;

import com.proje.employee.dto.PasswordChangeRequest;
import com.proje.employee.dto.UserCreateRequest;
import com.proje.employee.dto.UserResponse;
import com.proje.employee.dto.UserRoleRequest;
import com.proje.employee.dto.UserStatusRequest;
import com.proje.employee.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Page<UserResponse> getAll(
            @PageableDefault(size = 20, sort = "email", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return userService.getAll(pageable);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        UserResponse response = userService.create(request);

        return ResponseEntity
                .created(URI.create("/api/users/" + response.id()))
                .body(response);
    }

    // Islemi YAPAN kullanici, kimlik dogrulama sonucundan okunur -- istek
    // govdesinden DEGIL. Istemcinin gonderdigi kimlige guvenmek, herkesin
    // kendini baskasi gibi gostermesine izin verirdi.
    @PutMapping("/{id}/role")
    public UserResponse changeRole(@PathVariable Long id,
                                   @Valid @RequestBody UserRoleRequest request,
                                   @AuthenticationPrincipal UserDetails actingUser) {

        return userService.changeRole(id, request.role(), actingUser.getUsername());
    }

    @PutMapping("/{id}/status")
    public UserResponse changeStatus(@PathVariable Long id,
                                     @Valid @RequestBody UserStatusRequest request,
                                     @AuthenticationPrincipal UserDetails actingUser) {

        return userService.changeStatus(id, request.active(), actingUser.getUsername());
    }

    /**
     * Kullanici kendi parolasini degistirir.
     *
     * Yolda id YOK: "/me" ile kastedilen daima token'in sahibidir. Id alsaydi
     * her istekte "bu id gercekten cagiranin mi" kontrolu gerekirdi ve o kontrol
     * bir gun unutulurdu -- yapisal olarak imkansiz kilmak daha guvenli.
     */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changeOwnPassword(
            @Valid @RequestBody PasswordChangeRequest request,
            @AuthenticationPrincipal UserDetails actingUser) {

        userService.changeOwnPassword(actingUser.getUsername(), request);

        return ResponseEntity.noContent().build();
    }
}
