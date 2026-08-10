package com.proje.notification.client;

import com.proje.notification.client.dto.EmployeeSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Employee Service'in okuma ucu.
 *
 * name alani IP veya port degil, Eureka'daki servis adidir; adresi Eureka'dan
 * cozulur ve birden fazla kopya varsa yuk dengelenir.
 */
@FeignClient(
        name = "employee-service",
        contextId = "employeeClient",
        path = "/api/employees",
        configuration = AuthorizedFeignConfig.class)
public interface EmployeeClient {

    @GetMapping("/{id}")
    EmployeeSummary findById(@PathVariable("id") Long id);
}
