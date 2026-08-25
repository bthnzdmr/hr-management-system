package com.proje.employee.repository;

import com.proje.employee.entity.NotificationKind;
import com.proje.employee.entity.NotificationMute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface NotificationMuteRepository
        extends JpaRepository<NotificationMute, NotificationMute.Key> {

    /**
     * Yalnizca TURLERI doner, satirlari degil.
     *
     * Cagiran tarafin tek sordugu sey bu; butun entity'leri cekip Java'da
     * eslemek gereksiz bir tur daha olurdu.
     */
    @Query("SELECT m.kind FROM NotificationMute m WHERE m.employeeId = :employeeId")
    Set<NotificationKind> findKindsByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Birden fazla personelin susturmalarini TEK sorguda okur.
     *
     * Olay yayininda hem personelin hem yoneticisinin tercihi lazim; ayri ayri
     * sorulsaydi her olay iki gidis donus ederdi.
     */
    @Query("""
            SELECT m FROM NotificationMute m
            WHERE m.employeeId IN :employeeIds
            """)
    List<NotificationMute> findAllByEmployeeIdIn(@Param("employeeIds") Set<Long> employeeIds);

    void deleteByEmployeeId(Long employeeId);
}
