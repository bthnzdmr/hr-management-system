package com.proje.employee.exception;

/**
 * Ayni personelin tarihleri kesisen ikinci bir izni.
 *
 * Bu kural KODDA degil veritabaninda yasar (`ex_leave_no_overlap`); burada
 * yalnizca veritabaninin firlattigi teknik istisna, kullanicinin anlayacagi
 * bir hataya cevriliyor.
 */
public class OverlappingLeaveException extends RuntimeException {

    public OverlappingLeaveException(String message) {
        super(message);
    }
}
