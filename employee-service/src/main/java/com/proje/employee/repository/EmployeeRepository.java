package com.proje.employee.repository;

import com.proje.employee.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmail(String email);

    boolean existsByEmail(String email);

    // Sayfalanmaz: bir yoneticinin dogrudan asti kurumsal olarak sinirlidir ve
    // liste bir ekip gorunumu doldurmak icin kullanilir.
    List<Employee> findByManagerIdOrderByLastNameAsc(Long managerId);

    /**
     * Liste sorgusu: departman ve yonetici ayni sorguda getirilir.
     *
     * Yonetici icin LEFT JOIN kullanilir; INNER olsaydi yoneticisi olmayan
     * personel listeden tamamen duserdi.
     *
     * Bu FETCH zorunludur, susleme degil: cevapta managerFullName alani var ve
     * vekilin ADINI okumak onu yukler. Olculdu -- FETCH olmadan iki satirlik
     * bir sayfada bile sorgu sayisi 2'den 3'e cikiyor.
     *
     * countQuery ayrica yazildi: JOIN FETCH iceren sorgudan sayim sorgusu
     * turetilemez.
     */
    @Query(value = "SELECT e FROM Employee e JOIN FETCH e.department LEFT JOIN FETCH e.manager",
           countQuery = "SELECT count(e) FROM Employee e")
    Page<Employee> findAllWithDepartment(Pageable pageable);

    /**
     * Aramali ve filtreli liste.
     *
     * Parametreler NULL gecilebilir ve o zaman ilgili kosul devre disi kalir;
     * boylece tek sorgu dort kombinasyonu da karsilar ve dort ayri metot
     * yazmaya gerek kalmaz.
     *
     * Arama ad, soyad ve e-postada gecer. LOWER + LIKE kullanilir: veri kucuk
     * oldugu surece yeterli. Buyudugunde dogru arac trigram index'tir
     * (pg_trgm), cunku bastan joker iceren LIKE normal index kullanamaz.
     */
    @Query(value = """
            SELECT e FROM Employee e
            JOIN FETCH e.department
            LEFT JOIN FETCH e.manager
            WHERE (:active IS NULL OR e.active = :active)
              AND (:search IS NULL OR
                   LOWER(e.firstName) LIKE :search OR
                   LOWER(e.lastName) LIKE :search OR
                   LOWER(e.email) LIKE :search)
            """,
           countQuery = """
            SELECT count(e) FROM Employee e
            WHERE (:active IS NULL OR e.active = :active)
              AND (:search IS NULL OR
                   LOWER(e.firstName) LIKE :search OR
                   LOWER(e.lastName) LIKE :search OR
                   LOWER(e.email) LIKE :search)
            """)
    Page<Employee> search(@Param("search") String search,
                          @Param("active") Boolean active,
                          Pageable pageable);
}
