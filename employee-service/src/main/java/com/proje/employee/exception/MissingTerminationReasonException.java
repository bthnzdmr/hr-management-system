package com.proje.employee.exception;

/**
 * Pasiflestirme istegi ayrilma sebebi tasimiyordu.
 *
 * Varsayilan bir sebep atamak daha kolay olurdu ama devir oraninin en anlamli
 * kirilimini -- istege bagli ayrilma ile isten cikarma ayrimini -- sessizce
 * bozardi. Eksik veri, yanlis veriden iyidir.
 */
public class MissingTerminationReasonException extends RuntimeException {

    public MissingTerminationReasonException() {
        super("A termination reason is required when deactivating an employee");
    }
}
