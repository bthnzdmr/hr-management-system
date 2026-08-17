package com.proje.employee.repository;

import com.proje.employee.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Query("SELECT t FROM PasswordResetToken t JOIN FETCH t.user WHERE t.tokenHash = :hash")
    Optional<PasswordResetToken> findByTokenHash(@Param("hash") String hash);

    /**
     * Bekleyen en yeni jetonun uretilme ani, yoksa null.
     *
     * Hiz sinirinin dayanagi: ayri bir sayac tutmak yerine "bu hesaba yakin
     * zamanda mail gitti mi" sorusu tablonun kendisine soruluyor.
     */
    @Query("SELECT MAX(t.createdAt) FROM PasswordResetToken t "
            + "WHERE t.user.id = :userId AND t.usedAt IS NULL")
    Instant findLatestUnusedIssuedAt(@Param("userId") Long userId);

    /**
     * Jetonu YALNIZCA hala kullanilabilirse tuketir ve etkilenen satiri doner.
     *
     * Kosul ve yazma tek ifadede: "once oku, gecerli mi bak, sonra kullan"
     * yazilsaydi ayni baglantiyi iki kez acan biri iki kez parola
     * belirleyebilirdi. 1 donen taraf jetonu kapmistir. Ayni desen yenileme
     * jetonu dondurmesinde.
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now "
            + "WHERE t.tokenHash = :hash AND t.usedAt IS NULL AND t.expiresAt > :now")
    int consumeIfUsable(@Param("hash") String hash, @Param("now") Instant now);

    /**
     * Kullanicinin bekleyen diger jetonlarini duserir.
     *
     * Sifirlama basarili oldugunda eski baglantilar da olmelidir: aksi halde
     * saldirganin daha once tetikledigi bir jeton, kurban parolasini
     * degistirdikten SONRA hala kullanilabilirdi.
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now "
            + "WHERE t.user.id = :userId AND t.usedAt IS NULL")
    int invalidateAllFor(@Param("userId") Long userId, @Param("now") Instant now);

    // Her sifirlama istegi bir satir birakir ve hicbiri kendiliginden silinmez;
    // refresh_token ile ayni buyume, ayni cozum.
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
