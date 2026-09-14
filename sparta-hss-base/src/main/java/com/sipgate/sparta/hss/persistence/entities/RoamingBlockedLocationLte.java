package com.sipgate.sparta.hss.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serial;
import java.util.Objects;

@Entity
@Table(name = "roaming_blocked_location_lte")
public final class RoamingBlockedLocationLte implements RoamingBlockedEntity {
    @Serial
    private static final long serialVersionUID = 20260309;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "mcc", nullable = false, length = 3)
    private String mcc;
    @Column(name = "mnc", nullable = false, length = 3)
    private String mnc;

    @Column(name = "reason", nullable = true, length = 255)
    private String reason;


    public RoamingBlockedLocationLte() {
    }

    public RoamingBlockedLocationLte(final Integer id, final String mcc, final String mnc, final String reason) {
        this.id = id;
        this.mcc = mcc;
        this.mnc = mnc;
        this.reason = reason;
    }

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public String getMcc() {
        return mcc;
    }

    public void setMcc(final String mcc) {
        this.mcc = mcc;
    }

    public String getMnc() {
        return mnc;
    }

    public void setMnc(final String mnc) {
        this.mnc = mnc;
    }

    @Override
    public String getReason() {
        return reason;
    }

    @Override
    public void setReason(final String reason) {
        this.reason = reason;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final RoamingBlockedLocationLte that = (RoamingBlockedLocationLte) o;
        return Objects.equals(id, that.id) && Objects.equals(mcc, that.mcc) && Objects.equals(mnc, that.mnc) && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, mcc, mnc, reason);
    }
}
