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
@Table(name = "roaming_blocked_imsi_override")
public final class RoamingBlockedImsiOverride implements RoamingBlockedEntity {
    @Serial
    private static final long serialVersionUID = 20260309;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "imsi", nullable = false, length = 20)
    private String imsi;

    @Column(name = "gt_prefix", nullable = false, length = 20)
    private String gtPrefix;

    @Column(name = "roaming", nullable = false, length = 10)
    private String roaming;

    @Column(name = "reason", nullable = true, length = 255)
    private String reason;


    public RoamingBlockedImsiOverride() {
    }

    public RoamingBlockedImsiOverride(
            final Integer id,
            final String imsi,
            final String gtPrefix,
            final String roaming,
            final String reason)
    {
        this.id = id;
        this.imsi = imsi;
        this.gtPrefix = gtPrefix;
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

    public String getGtPrefix() {
        return gtPrefix;
    }

    public void setGtPrefix(final String gtPrefix) {
        this.gtPrefix = gtPrefix;
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
        final RoamingBlockedImsiOverride that = (RoamingBlockedImsiOverride) o;
        return Objects.equals(id, that.id) && Objects.equals(imsi, that.imsi) && Objects.equals(gtPrefix, that.gtPrefix) && Objects.equals(roaming, that.roaming) && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, imsi, gtPrefix, roaming, reason);
    }
}
