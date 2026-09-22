package com.proje.employee.service;

import com.proje.employee.entity.Employee;
import com.proje.employee.entity.LeaveRequest;
import com.proje.employee.exception.LeaveRequestNotFoundException;
import com.proje.employee.exception.LeaveRuleViolationException;
import org.springframework.stereotype.Component;

/**
 * "Bu kapsam bu izin kaydiyla ne yapabilir?" sorusunun TEK cevabi.
 *
 * <p>Kurallar {@code LeaveRequestService} icinde ozel metotlar olarak
 * dagilikti ve sinif bes isi birden yapiyordu: talep acma, karar verme,
 * iptal, KAPSAM KURALLARI ve olay yayini. Ayri bir sinif iki sey kazandiriyor:
 * kurallar tek yerde toplaniyor ve <b>tek baslarina test edilebiliyorlar</b> --
 * onceden sinamak icin servisin butun isbirlikcilerini (repository, outbox
 * writer, tercih servisi) kurmak gerekiyordu.
 *
 * <p>Davranis DEGISMEDI: metotlarin govdesi oldugu gibi tasindi. Yer
 * degistiren bir kuralin sessizce baska bir sey yapmaya baslamasi, bu
 * projenin defalarca odedigi bedeldir.
 *
 * <p>{@code requirePending} bilerek DISARIDA kaldi: o bir ERISIM kurali
 * degil, kaydin KENDI durumuna dair bir kural -- "nihai bir istegi tekrar
 * karara baglama". Cagiranin kim oldugundan bagimsizdir.
 */
@Component
public class LeaveAccessRules {

    /**
     * Baskasi adina talep acmak Ik'ya aittir; herkes KENDI adina acabilir.
     *
     * Yonetici de ASTI adina acamaz: talebi acan ile karar veren ayni kisi
     * olurdu ve kendi iznine karar verme yasagi bu yoldan atlatilirdi.
     */
    public void requireCanRecordFor(Employee employee, AccessScope scope) {
        if (scope.isUnrestricted()) {
            return;
        }

        if (!employee.getId().equals(scope.employeeId())) {
            throw new LeaveRuleViolationException(
                    "You can only request leave for yourself");
        }
    }

    /** Ik her istege, yonetici yalnizca DOGRUDAN astininkine karar verir. */
    public void requireCanDecide(LeaveRequest leave, AccessScope scope) {
        Employee owner = leave.getEmployee();
        boolean ownRequest = scope.employeeId() != null
                && owner.getId().equals(scope.employeeId());

        // Kendi iznini onaylamak GORUNURLUKTEN once gelir. Once
        // isUnrestricted() sorulunca kural yalnizca yonetici icin isliyordu:
        // personele bagli bir Ik uzmani kendi talebini acip kendisi
        // onaylayabiliyordu. Olculdu.
        if (ownRequest) {
            throw new LeaveRuleViolationException("You cannot decide your own leave");
        }

        if (scope.isUnrestricted()) {
            return;
        }

        if (!canSee(leave, scope)) {
            throw new LeaveRequestNotFoundException(leave.getId());
        }

        boolean directReport = owner.getManager() != null
                && owner.getManager().getId().equals(scope.employeeId());

        if (!directReport) {
            throw new LeaveRuleViolationException(
                    "Only this person's manager or HR can decide this request");
        }
    }

    /**
     * Geri cekmek karar vermek DEGILDIR: kisi kendi talebinden vazgecebilir.
     *
     * Onay ve rette "kendi iznine karar veremezsin" kurali gecerlidir; iptalde
     * degildir, cunku vazgecmek gorevler ayriligini ihlal etmez. Baskasinin
     * talebini geri cekmek ise karar yetkisi ister.
     */
    public void requireCanCancel(LeaveRequest leave, AccessScope scope) {
        boolean ownRequest = scope.employeeId() != null
                && leave.getEmployee().getId().equals(scope.employeeId());

        if (ownRequest) {
            return;
        }

        requireCanDecide(leave, scope);
    }

    /**
     * Bu kapsam bu kaydi GOREBILIR mi?
     *
     * Cagiran taraf gormedigi bir kayit icin 403 DEGIL 404 doner: 403 "bu
     * kayit var ama goremezsin" der ve id deneyerek kayit sayisi
     * ogrenilebilirdi.
     */
    public boolean canSee(LeaveRequest leave, AccessScope scope) {
        if (scope.isUnrestricted()) {
            return true;
        }
        if (scope.isEmpty()) {
            return false;
        }

        Employee owner = leave.getEmployee();

        if (scope.employeeId().equals(owner.getId())) {
            return true;
        }

        return scope.includesDirectReports()
                && owner.getManager() != null
                && scope.employeeId().equals(owner.getManager().getId());
    }
}
