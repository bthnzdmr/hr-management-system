package com.proje.employee.service;

import com.proje.employee.dto.EmployeeCreateRequest;
import com.proje.employee.dto.EmployeeResponse;
import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.exception.DepartmentNotFoundException;
import com.proje.employee.exception.EmailAlreadyExistsException;
import com.proje.employee.exception.EmployeeNotFoundException;
import com.proje.employee.mapper.EmployeeMapper;
import com.proje.employee.repository.DepartmentRepository;
import com.proje.employee.repository.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeMapper employeeMapper;

    public EmployeeService(EmployeeRepository employeeRepository,
                           DepartmentRepository departmentRepository,
                           EmployeeMapper employeeMapper) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.employeeMapper = employeeMapper;
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> getAll(Pageable pageable) {
        return employeeRepository.findAllWithDepartment(pageable)
                .map(employeeMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        return employeeMapper.toResponse(employee);
    }

    @Transactional
    public EmployeeResponse create(EmployeeCreateRequest request) {
        // Kullaniciya anlamli mesaj donmek icin. Dogruluk garantisi bu kontrol
        // degil, veritabanindaki uk_employee_email kisitidir.
        if (employeeRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new DepartmentNotFoundException(request.departmentId()));

        Employee employee = new Employee(
                request.firstName(),
                request.lastName(),
                request.email(),
                department,
                request.jobTitle(),
                request.hireDate());

        employee.setPhone(request.phone());
        employee.setSalary(request.salary());

        if (request.managerId() != null) {
            Employee manager = employeeRepository.findById(request.managerId())
                    .orElseThrow(() -> new EmployeeNotFoundException(request.managerId()));
            employee.setManager(manager);
        }

        return employeeMapper.toResponse(employeeRepository.save(employee));
    }
}
