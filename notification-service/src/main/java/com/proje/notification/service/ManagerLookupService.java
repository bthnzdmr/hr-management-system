package com.proje.notification.service;

import com.proje.notification.client.EmployeeClient;
import com.proje.notification.client.ServiceTokenProvider;
import com.proje.notification.client.dto.EmployeeSummary;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Personelin yoneticisinin e-posta adresini bulur.
 *
 * Bu bir ZENGINLESTIRMEDIR, mail gondermenin on kosulu degildir: cagri
 * basarisiz olursa bos deger doner ve mail CC'siz gider. Aksi halde Employee
 * Service'in gecici bir arizasi bildirimi tamamen engellerdi -- kuyrugu tam da
 * bu bagi koparmak icin secmistik.
 */
@Service
public class ManagerLookupService {

    private static final Logger log = LoggerFactory.getLogger(ManagerLookupService.class);

    private final EmployeeClient employeeClient;
    private final ServiceTokenProvider tokenProvider;

    public ManagerLookupService(EmployeeClient employeeClient, ServiceTokenProvider tokenProvider) {
        this.employeeClient = employeeClient;
        this.tokenProvider = tokenProvider;
    }

    public Optional<String> managerEmail(Long employeeId) {
        try {
            return lookup(employeeId);

        } catch (FeignException.Unauthorized e) {
            // Onbellekteki token sunucu tarafinda gecersiz (ornegin imzalama
            // anahtari degismis). Atilir ve bir kez yeniden denenir.
            log.info("Service token rejected, renewing and retrying once");
            tokenProvider.invalidate();
            return lookupQuietly(employeeId);

        } catch (Exception e) {
            log.warn("Manager lookup failed for employee {}, mail will be sent without CC: {}",
                    employeeId, e.toString());
            return Optional.empty();
        }
    }

    private Optional<String> lookup(Long employeeId) {
        EmployeeSummary employee = employeeClient.findById(employeeId);

        if (employee.managerId() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(employeeClient.findById(employee.managerId()).email());
    }

    private Optional<String> lookupQuietly(Long employeeId) {
        try {
            return lookup(employeeId);
        } catch (Exception e) {
            log.warn("Manager lookup failed after token renewal for employee {}: {}",
                    employeeId, e.toString());
            return Optional.empty();
        }
    }
}
