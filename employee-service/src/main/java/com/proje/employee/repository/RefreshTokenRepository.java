package com.proje.employee.repository;

import com.proje.employee.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Kullanici da getiriliyor: cagiran taraf hemen ardindan e-postasini ve
    // rolunu okuyor, aksi halde tembel vekil ikinci bir sorgu acardi.
    @Query("SELECT t FROM RefreshToken t JOIN FETCH t.user WHERE t.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHash(@Param("hash") String hash);

    /**
     * Jetonu YALNIZCA hala geceriyse iptal eder ve etkilenen satir sayisini doner.
     *
     * "Once oku, iptal mi diye bak, sonra iptal et" yazilsaydi iki es zamanli
     * yenileme ayni jetonu gecerli gorur ve ikisi de yeni jeton uretirdi. Tek
     * bir UPDATE ile kosul ve yazma ayni ifadede olur: 1 donen taraf jetonu
     * kapmistir, 0 donen taraf kaybetmistir.
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now "
            + "WHERE t.tokenHash = :hash AND t.revokedAt IS NULL")
    int revokeIfActive(@Param("hash") String hash, @Param("now") Instant now);

    // Tekrar kullanim tespit edildiginde cagrilir: jetonun kopyasi dolasiyorsa
    // o kullanicinin butun oturumlari suphelidir.
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now "
            + "WHERE t.user.id = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    /**
     * Suresi dolmus satirlari siler.
     *
     * Iptal edilmis ama suresi DOLMAMIS satirlar bilerek korunur: tekrar
     * kullanim tespiti tam da o satirin varligina dayanir. Suresi dolduktan
     * sonra satir hicbir sey anlatmaz -- rotate() zaten once sure kontrolu
     * yapip reddediyor.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
