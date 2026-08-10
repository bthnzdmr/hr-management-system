package com.proje.employee.service;

import com.proje.employee.dto.EmployeeCreateRequest;
import com.proje.employee.dto.EmployeeResponse;
import com.proje.employee.dto.EmployeeUpdateRequest;
import com.proje.employee.dto.SalaryResponse;
import com.proje.employee.dto.SalaryUpdateRequest;
import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.event.EmployeeEvent;
import com.proje.employee.event.EmployeeEventType;
import com.proje.employee.event.OutboxWriter;
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

import java.time.Instant;
import java.util.UUID;

@Service
public class EmployeeService {

    // Zincirde bozuk veri varsa sonsuz donguye girmemek icin ust sinir.
    private static final int MAX_HIERARCHY_DEPTH = 100;

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeMapper employeeMapper;
    private final OutboxWriter outboxWriter;

    public EmployeeService(EmployeeRepository employeeRepository,
                           DepartmentRepository departmentRepository,
                           EmployeeMapper employeeMapper,
                           OutboxWriter outboxWriter) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.employeeMapper = employeeMapper;
        this.outboxWriter = outboxWriter;
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

        Employee saved = employeeRepository.save(employee);
        publish(EmployeeEventType.CREATED, saved);

        return employeeMapper.toResponse(saved);
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
        employee.setManager(resolveManager(employee, request.managerId()));

        publish(EmployeeEventType.UPDATED, employee);

        // save() cagrilmadi: entity transaction icinde yonetiliyor, degisiklikler
        // commit sirasinda otomatik yazilir (dirty checking).
        return employeeMapper.toResponse(employee);
    }

    /**
     * Maas yalnizca bu iki metotla okunur ve yazilir.
     *
     * Genel guncelleme maasa hic dokunmaz; boylece maasi okuyamayan bir
     * istemcinin onu yanlislikla silmesi mumkun degildir. Yetki kontrolu uc
     * seviyesindedir (SecurityConfig), bu yuzden servis cagiranin rolunu bilmez.
     */
    @Transactional(readOnly = true)
    public SalaryResponse getSalary(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        return new SalaryResponse(employee.getId(), employee.getSalary());
    }

    @Transactional
    public SalaryResponse updateSalary(Long id, SalaryUpdateRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        employee.setSalary(request.salary());

        // Kayit degistigi icin olay yayinlanir. Olay maasi TASIMAZ; personel
        // yalnizca kaydinin guncellendigini ogrenir.
        publish(EmployeeEventType.UPDATED, employee);

        return new SalaryResponse(employee.getId(), employee.getSalary());
    }

    @Transactional
    public void deactivate(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        employee.setActive(false);
        publish(EmployeeEventType.DEACTIVATED, employee);
    }

    // Olay, is verisiyle ayni transaction icinde outbox tablosuna yazilir.
    // Tek veritabanina tek yazma oldugu icin ikisi ya birlikte kalici olur
    // ya birlikte geri alinir; broker bu noktada hic devrede degildir.
    private void publish(EmployeeEventType type, Employee employee) {
        outboxWriter.write(new EmployeeEvent(
                UUID.randomUUID().toString(),
                type,
                Instant.now(),
                employee.getId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getDepartment().getName(),
                employee.getJobTitle()));
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
