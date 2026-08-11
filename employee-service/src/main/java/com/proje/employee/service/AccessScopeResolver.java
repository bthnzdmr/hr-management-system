package com.proje.employee.service;

import com.proje.employee.entity.User;
import com.proje.employee.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;

/**
 * Istegi yapanin kapsamini belirler.
 *
 * Kapsam TOKEN'DAN degil veritabanindan hesaplanir. Token'a "su personelin
 * kaydisin" yazilabilirdi ama o bilgi jetonun omru boyunca donar: kisi baska
 * bir personele baglandiginda ya da yoneticilikten alindiginda, elindeki
 * jeton hala eski kapsami tasirdi.
 *
 * Bedeli her istekte bir sorgu; kazanci kapsamin daima guncel olmasi.
 */
@Service
public class AccessScopeResolver {

    private final UserRepository userRepository;

    public AccessScopeResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AccessScope resolve(Principal principal) {
        User user = userRepository.findByEmail(principal.getName())
                // Token gecerli ama hesap silinmis: hicbir sey goremez.
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user no longer exists: " + principal.getName()));

        return AccessScope.forUser(user);
    }
}
