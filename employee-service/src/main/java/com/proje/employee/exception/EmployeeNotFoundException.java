package com.proje.employee.exception;

public class EmployeeNotFoundException extends RuntimeException {

    public EmployeeNotFoundException(Long employeeId) {
        super("Employee not found with id: " + employeeId);
    }

    private EmployeeNotFoundException(String message) {
        super(message);
    }

    /**
     * Hesabin bagli oldugu bir personel kaydi yok.
     *
     * Ayri bir mesaj cunku ortada bir ID YOK: "id: null" demek istemciye
     * anlamsiz bir metin gondermek olurdu. Durum bir hata degil, gecerli bir
     * hal -- servis hesaplari ve dis denetciler personele bagli degildir.
     */
    public static EmployeeNotFoundException forCaller() {
        return new EmployeeNotFoundException("This account is not linked to an employee record");
    }
}
