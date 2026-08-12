package com.proje.employee.repository;

import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // users.employee_id benzersizdir, dolayisiyla en fazla bir hesap doner.
    Optional<User> findByEmployeeId(Long employeeId);

    // Personel LEFT JOIN FETCH ile getirilir: cevapta personelin adi da var ve
    // tembel vekilden ad okumak her satir icin ayri sorgu acardi (N+1).
    // LEFT sart: personel kaydi olmayan sistem hesaplari listeden dusmemeli.
    @Query(value = "SELECT u FROM User u LEFT JOIN FETCH u.employee",
            countQuery = "SELECT count(u) FROM User u")
    Page<User> findAllWithEmployee(Pageable pageable);

    /**
     * Aktif yoneticileri KILITLEYEREK okur.
     *
     * "Son yonetici kalmasin" kurali klasik bir check-then-act'tir: iki yonetici
     * ayni anda birbirini dusurmeye kalkarsa ikisi de "hala baska bir admin var"
     * gorur ve sistem yoneticisiz kalir. Satirlar kilitlendigi icin ikinci
     * transaction birincinin bitmesini bekler ve guncel sayiyi gorur.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE :role MEMBER OF u.roles AND u.active = true")
    List<User> findActiveByRoleForUpdate(@Param("role") Role role);
}
