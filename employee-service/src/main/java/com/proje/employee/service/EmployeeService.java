package com.proje.employee.service;

import com.proje.employee.dto.EmployeeCreateRequest;
import com.proje.employee.dto.EmployeeResponse;
import com.proje.employee.dto.EmployeeUpdateRequest;
import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.exception.DepartmentNotFoundException;
import com.proje.employee.exception.EmailAlreadyExistsException;
import com.proje.employee.exception.EmployeeNotFoundException;
import com.proje.employee.exception.ManagerCycleException;
import com.proje.employee.mapper.EmployeeMapper;
import com.proje.employee.repository.DepartmentRepository;
import com.proje.employee.repository.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    // Zincirde bozuk veri varsa sonsuz donguye girmemek icin ust sinir.
    private static final int MAX_HIERARCHY_DEPTH = 100;

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

    @Transactional
    public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        if (!employee.getEmail().equals(request.email())
                && employeeRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new DepartmentNotFoundException(request.departmentId()));

        employee.setFirstName(request.firstName());
        employee.setLastName(request.lastName());
        employee.setEmail(request.email());
        employee.setPhone(request.phone());
        employee.setDepartment(department);
        employee.setJobTitle(request.jobTitle());
        employee.setHireDate(request.hireDate());
        employee.setSalary(request.salary());
        employee.setManager(resolveManager(employee, request.managerId()));

        // save() cagrilmadi: entity transaction icinde yonetiliyor, degisiklikler
        // commit sirasinda otomatik yazilir (dirty checking).
        return employeeMapper.toResponse(employee);
    }

    @Transactional
    public void deactivate(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        employee.setActive(false);
    }

    private Employee resolveManager(Employee employee, Long managerId) {
        if (managerId == null) {
            return null;
        }

        if (managerId.equals(employee.getId())) {
            throw new ManagerCycleException(employee.getId(), managerId);
        }

        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow(() -> new EmployeeNotFoundException(managerId));

        assertNoCycle(employee, manager);
        return manager;
    }

    // Yeni yoneticiden yukari dogru yurur. Yolda calisanin kendisine rastlanirsa
    // dongu olusuyor demektir. Veritabani CHECK kisiti bunu goremez cunku
    // birden fazla satirin gezilmesi gerekir.
    private void assertNoCycle(Employee employee, Employee newManager) {
        Employee current = newManager;

        for (int depth = 0; current != null && depth < MAX_HIERARCHY_DEPTH; depth++) {
            if (current.getId().equals(employee.getId())) {
                throw new ManagerCycleException(employee.getId(), newManager.getId());
            }
            current = current.getManager();
        }
    }
}
