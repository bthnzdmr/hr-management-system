package com.proje.employee.repository;

import com.proje.employee.entity.Department;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByName(String name);

    List<Department> findByActiveTrueOrderByNameAsc();

    List<Department> findAllByOrderByNameAsc();

    /** Buyuk/kucuk harf farki ayri bir departman yaratmamali: "Sales" ve "sales" aynidir. */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Departmani KILITLEYEREK okur.
     *
     * "Once say, bos ise kapat" yazilsaydi klasik bir check-then-act olusurdu:
     * iki eszamanli istekten biri sayarken digeri o departmana personel
     * atayabilir ve sonuc PASIF bir departmanda AKTIF personel olurdu.
     * Kilit, atamayi yapan tarafi bekletir.
     *
     * Son yonetici kuralindaki PESSIMISTIC_WRITE ile ayni aileden: dogruluk
     * kodun sirasiyla degil veritabaninin garantisiyle saglanir.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Department d WHERE d.id = :id")
    Optional<Department> findByIdForUpdate(@Param("id") Long id);

    /**
     * Departman basina AKTIF personel sayisi, tek sorguda.
     *
     * Her departman icin ayri sayim yapmak N+1 olurdu; ayni tuzak hesap
     * listesinde olculdu.
     */
    @Query("""
            SELECT d.id, count(e.id)
            FROM Department d
            LEFT JOIN Employee e ON e.department = d AND e.active = true
            GROUP BY d.id
            """)
    List<Object[]> countActiveEmployeesPerDepartment();

    @Query("SELECT count(e) FROM Employee e WHERE e.department.id = :id AND e.active = true")
    long countActiveEmployees(@Param("id") Long id);
}
