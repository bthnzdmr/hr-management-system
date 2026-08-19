package com.proje.employee.service;

import com.proje.employee.entity.User;
import com.proje.employee.exception.StaleCredentialsException;
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
        return AccessScope.forUser(user(principal));
    }

    /**
     * Cagiranin KENDI personel kimligi; hesap bir personele bagli degilse null.
     *
     * KAPSAMDAN AYRI bir soru ve ayri bir metot olmasinin sebebi olculdu:
     * `AccessScope.visibleEmployeeId()` GORUNURLUGU anlatir ve sinirsiz
     * kapsamda bilerek `null`'dir -- kisiyi kisitlayacak bir filtre yoktur.
     * Kimlik icin onu kullanmak, personele bagli bir Ik uzmaninin KENDI
     * kaydini goremmesine yol aciyordu (olculdu: 404).
     *
     * Gorunurluk ile kimlik farkli sorulardir; ayni alandan cevaplanamaz.
     */
    @Transactional(readOnly = true)
    public Long selfEmployeeId(Principal principal) {
        User user = user(principal);

        return user.getEmployee() == null ? null : user.getEmployee().getId();
    }

    private User user(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                // Token gecerli ama hesap silinmis. Bu bir ISTEMCI durumudur
                // (eskimis kimlik bilgisi), sunucu arizasi degil: 401 doner.
                // Onceden IllegalStateException ile 500 donuyordu ve izleme
                // kirleniyordu -- olmayan bir arizayi bildiren alarm.
                .orElseThrow(() -> new StaleCredentialsException(principal.getName()));
    }
}
