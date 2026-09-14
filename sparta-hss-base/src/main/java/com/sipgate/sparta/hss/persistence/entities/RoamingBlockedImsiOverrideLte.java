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
@Table(name = "roaming_blocked_imsi_override_lte")
public final class RoamingBlockedImsiOverrideLte implements RoamingBlockedEntity {
    @Serial
    private static final long serialVersionUID = 20260309;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "imsi", nullable = false, length = 20)
    private String imsi;

    @Column(name = "mcc", nullable = false, length = 3)
    private String mcc;
    @Column(name = "mnc", nullable = false, length = 3)
    private String mnc;

    @Column(name = "roaming", nullable = false, length = 10)
    private String roaming;

    @Column(name = "reason", nullable = true, length = 255)
    private String reason;


    public RoamingBlockedImsiOverrideLte() {
    }

    public RoamingBlockedImsiOverrideLte(
            final Integer id,
            final String imsi,
            final String mcc,
            final String mnc,
            final String roaming,
            final String reason)
    {
        this.id = id;
        this.imsi = imsi;
        this.mcc = mcc;
        this.mnc = mnc;
        this.roaming = roaming;
        this.reason = reason;
    }

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public String getImsi() {
        return imsi;
    }

    public void setImsi(final String imsi) {
        this.imsi = imsi;
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

    public String getRoaming() {
        return roaming;
    }

    public void setRoaming(final String roaming) {
        this.roaming = roaming;
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
        final RoamingBlockedImsiOverrideLte that = (RoamingBlockedImsiOverrideLte) o;
        return Objects.equals(id, that.id) && Objects.equals(imsi, that.imsi) && Objects.equals(mcc, that.mcc) && Objects.equals(mnc, that.mnc) && Objects.equals(roaming, that.roaming) && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, imsi, mcc, mnc, roaming, reason);
    }
}
