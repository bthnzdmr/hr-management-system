package com.proje.employee.service;

import com.proje.employee.dto.DepartmentResponse;
import com.proje.employee.entity.Department;
import com.proje.employee.repository.DepartmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentService departmentService;

    private Department department(Long id, String name) {
        Department department = new Department(name);
        ReflectionTestUtils.setField(department, "id", id);
        return department;
    }

    @Test
    @DisplayName("Maps departments to a response carrying only id and name")
    void mapsDepartmentsToResponse() {
        when(departmentRepository.findByActiveTrueOrderByNameAsc())
                .thenReturn(List.of(department(2L, "Finance"), department(1L, "Sales")));

        List<DepartmentResponse> result = departmentService.getAllActive();

        assertThat(result).containsExactly(
                new DepartmentResponse(2L, "Finance"),
                new DepartmentResponse(1L, "Sales"));
    }

    @Test
    @DisplayName("Asks the repository only for active departments")
    void requestsOnlyActiveDepartments() {
        when(departmentRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());

        departmentService.getAllActive();

        // Pasif departman secim listesine girmemelidir: yeni kayit ona atanamaz.
        verify(departmentRepository).findByActiveTrueOrderByNameAsc();
    }
}
