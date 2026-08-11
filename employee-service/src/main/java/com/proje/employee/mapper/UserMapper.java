package com.proje.employee.mapper;

import com.proje.employee.dto.UserResponse;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        Employee employee = user.getEmployee();

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                // Vekilin id'sini okumak onu yuklemez.
                employee == null ? null : employee.getId(),
                // ADI okumak vekili YUKLER; liste sorgusu bu yuzden personeli
                // LEFT JOIN FETCH ile getiriyor, aksi halde her satir icin ayri
                // bir SELECT calisirdi (N+1).
                employee == null ? null : employee.getFirstName() + " " + employee.getLastName(),
                user.getCreatedAt());
        // Parola ozeti bilerek yok: bir kez cevaba girerse her yere girer.
    }
}
