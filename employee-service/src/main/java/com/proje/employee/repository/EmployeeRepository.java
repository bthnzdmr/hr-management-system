package com.proje.employee.repository;

import com.proje.employee.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<Employee> findByDepartmentId(Long departmentId, Pageable pageable);

    // Departman ayni sorguda getirilir; liste dolasilirken tekrar veritabanina gidilmez.
    // countQuery ayrica yazildi: JOIN FETCH iceren sorgudan sayim sorgusu turetilemez.
    @Query(value = "SELECT e FROM Employee e JOIN FETCH e.department",
           countQuery = "SELECT count(e) FROM Employee e")
    Page<Employee> findAllWithDepartment(Pageable pageable);
}
