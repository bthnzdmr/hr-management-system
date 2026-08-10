package com.proje.employee.service;

import com.proje.employee.dto.DepartmentResponse;
import com.proje.employee.repository.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    /**
     * Secim listeleri icin kullanilan referans verisi.
     *
     * Sayfalama yoktur: departman sayisi kurumsal olarak sinirlidir ve liste
     * bir acilir kutuyu doldurmak icin kullanilir. Buyuyebilen listelerde
     * (personel gibi) sayfalama zorunludur.
     *
     * Pasif departmanlar donmez: yeni kayit onlara atanmamalidir. Mevcut
     * personelin pasif bir departmani olabilir, o baglanti korunur.
     */
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAllActive() {
        return departmentRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(department -> new DepartmentResponse(department.getId(), department.getName()))
                .toList();
    }
}
