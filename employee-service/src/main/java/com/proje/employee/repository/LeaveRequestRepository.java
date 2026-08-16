package com.proje.employee.repository;

import com.proje.employee.entity.LeaveRequest;
import com.proje.employee.entity.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /** Listeleme sorgusu. */
    @Query(value = """
            SELECT l FROM LeaveRequest l
            LEFT JOIN FETCH l.employee e
            LEFT JOIN FETCH l.decidedBy
            LEFT JOIN FETCH l.createdBy
            WHERE (:statuses IS NULL OR l.status IN :statuses)
              AND (:employeeIds IS NULL OR e.id IN :employeeIds)
            """,
            countQuery = """
            SELECT count(l) FROM LeaveRequest l
            WHERE (:statuses IS NULL OR l.status IN :statuses)
              AND (:employeeIds IS NULL OR l.employee.id IN :employeeIds)
            """)
    Page<LeaveRequest> search(@Param("statuses") Collection<LeaveStatus> statuses,
                             @Param("employeeIds") Collection<Long> employeeIds,
                             Pageable pageable);

    /** Tek kayit; kapsam kontrolu icin personel de yuklenir. */
    @Query("SELECT l FROM LeaveRequest l LEFT JOIN FETCH l.employee LEFT JOIN FETCH l.decidedBy LEFT JOIN FETCH l.createdBy WHERE l.id = :id")
    Optional<LeaveRequest> findByIdWithEmployee(@Param("id") Long id);
}
