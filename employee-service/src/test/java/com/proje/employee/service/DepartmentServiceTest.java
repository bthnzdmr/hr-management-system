package com.proje.employee.service;

import com.proje.employee.dto.DepartmentCreateRequest;
import com.proje.employee.dto.DepartmentResponse;
import com.proje.employee.entity.Department;
import com.proje.employee.exception.DepartmentNameAlreadyExistsException;
import com.proje.employee.exception.DepartmentNotFoundException;
import com.proje.employee.exception.DepartmentRuleViolationException;
import com.proje.employee.repository.DepartmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentService departmentService;

    private Department department(Long id, String name, boolean active) {
        Department department = new Department(name);
        ReflectionTestUtils.setField(department, "id", id);
        department.setActive(active);
        return department;
    }

    @Test
    @DisplayName("Leaves closed departments out of the picker by default")
    void hidesClosedDepartmentsByDefault() {
        // Pasif departman secim listesine girmemelidir: yeni kayit ona atanamaz.
        when(departmentRepository.findByActiveTrueOrderByNameAsc())
                .thenReturn(List.of(department(1L, "Sales", true)));
        when(departmentRepository.countActiveEmployeesPerDepartment())
                .thenReturn(List.<Object[]>of(new Object[]{1L, 4L}));

        List<DepartmentResponse> result = departmentService.getAll(false);

        assertThat(result).containsExactly(new DepartmentResponse(1L, "Sales", true, 4));
        verify(departmentRepository, never()).findAllByOrderByNameAsc();
    }

    @Test
    @DisplayName("Shows closed departments to the management screen")
    void showsClosedDepartmentsWhenAsked() {
        // Kapatilmis bir departmani geri acabilmek icin once gorebilmek gerekir.
        when(departmentRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(department(9L, "Retired", false)));
        when(departmentRepository.countActiveEmployeesPerDepartment()).thenReturn(List.of());

        assertThat(departmentService.getAll(true))
                .containsExactly(new DepartmentResponse(9L, "Retired", false, 0));
    }

    @Test
    @DisplayName("Refuses a name that already exists, whatever its letter case")
    void refusesDuplicateName() {
        // "Sales" ve "sales" ayri departman degildir.
        when(departmentRepository.existsByNameIgnoreCase("sales")).thenReturn(true);

        assertThatThrownBy(() -> departmentService.create(new DepartmentCreateRequest("sales")))
                .isInstanceOf(DepartmentNameAlreadyExistsException.class);

        verify(departmentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Trims the name before storing it")
    void trimsName() {
        when(departmentRepository.existsByNameIgnoreCase("Finance")).thenReturn(false);
        when(departmentRepository.save(org.mockito.ArgumentMatchers.any(Department.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(departmentService.create(new DepartmentCreateRequest("  Finance  ")).name())
                .isEqualTo("Finance");
    }

    @Test
    @DisplayName("Refuses to close a department that still has active employees")
    void refusesToCloseWhileStaffed() {
        // Aksi halde sistem, KAPALI bir departmanda calisan personel uretirdi.
        when(departmentRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(department(1L, "Sales", true)));
        when(departmentRepository.countActiveEmployees(1L)).thenReturn(3L);

        assertThatThrownBy(() -> departmentService.changeStatus(1L, false))
                .isInstanceOf(DepartmentRuleViolationException.class)
                // Mesaj NE YAPILMASI gerektigini soylemeli.
                .hasMessageContaining("Move them to another department");
    }

    @Test
    @DisplayName("Closes an empty department")
    void closesEmptyDepartment() {
        Department empty = department(1L, "Sales", true);
        when(departmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(empty));
        when(departmentRepository.countActiveEmployees(1L)).thenReturn(0L);

        assertThat(departmentService.changeStatus(1L, false).active()).isFalse();
        assertThat(empty.isActive()).isFalse();
    }

    @Test
    @DisplayName("Reopens a closed department without counting anybody")
    void reopensWithoutCheckingStaff() {
        // Kural yalnizca KAPATMAYA uygulanir: acmak kimseyi gecersiz duruma dusurmez.
        Department closed = department(1L, "Sales", false);
        when(departmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(closed));
        when(departmentRepository.countActiveEmployees(1L)).thenReturn(0L);

        assertThat(departmentService.changeStatus(1L, true).active()).isTrue();
    }

    @Test
    @DisplayName("Closing an already closed department is a no-op, not an error")
    void statusChangeIsIdempotent() {
        // Kapali bir departmanda gecmisten kalma aktif personel bulunabilir
        // (kural eklenmeden once kapatilmis olabilir). Tekrarlanan kapatma
        // istegi bu durumda HATA VERMEMELI: uc idempotenttir ve zaten istenen
        // durumdaysa kurala hic bakmaz.
        Department closed = department(1L, "Sales", false);
        when(departmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(closed));
        when(departmentRepository.countActiveEmployees(1L)).thenReturn(3L);

        assertThat(departmentService.changeStatus(1L, false).active()).isFalse();
    }

    @Test
    @DisplayName("Reads the row under a lock so a concurrent assignment cannot slip in")
    void locksTheRowBeforeChecking() {
        // "Once say, bos ise kapat" bir check-then-act olurdu: iki eszamanli
        // istekten biri sayarken digeri o departmana personel atayabilirdi.
        when(departmentRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(department(1L, "Sales", true)));
        when(departmentRepository.countActiveEmployees(1L)).thenReturn(0L);

        departmentService.changeStatus(1L, false);

        verify(departmentRepository).findByIdForUpdate(1L);
        verify(departmentRepository, never()).findById(1L);
    }

    @Test
    @DisplayName("Reports a missing department instead of failing obscurely")
    void reportsMissingDepartment() {
        when(departmentRepository.findByIdForUpdate(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.changeStatus(42L, false))
                .isInstanceOf(DepartmentNotFoundException.class);
    }
}
