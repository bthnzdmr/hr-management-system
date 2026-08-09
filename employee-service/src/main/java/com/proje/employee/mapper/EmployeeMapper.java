package com.proje.employee.mapper;

import com.proje.employee.dto.EmployeeResponse;
import com.proje.employee.entity.Employee;
import org.springframework.stereotype.Component;

@Component
public class EmployeeMapper {

    public EmployeeResponse toResponse(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getDepartment().getId(),
                employee.getDepartment().getName(),
                // Vekil nesnenin id'sine erismek onu yuklemez; id zaten bilinir.
                employee.getManager() == null ? null : employee.getManager().getId(),
                employee.getJobTitle(),
                employee.getHireDate(),
                employee.isActive());
    }
}
