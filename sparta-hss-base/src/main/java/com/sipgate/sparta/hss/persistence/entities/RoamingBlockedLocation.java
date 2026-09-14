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
@Table(name = "roaming_blocked_location")
public final class RoamingBlockedLocation implements RoamingBlockedEntity {
    @Serial
    private static final long serialVersionUID = 20260309;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "gt_prefix", nullable = false, length = 20)
    private String gtPrefix;

    @Column(name = "reason", nullable = true, length = 255)
    private String reason;


    public RoamingBlockedLocation() {
    }

    public RoamingBlockedLocation(final Integer id, final String gtPrefix, final String reason) {
        this.id = id;
        this.gtPrefix = gtPrefix;
        this.reason = reason;
    }


    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public String getGtPrefix() {
        return gtPrefix;
    }

    public void setGtPrefix(final String gtPrefix) {
        this.gtPrefix = gtPrefix;
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
        final RoamingBlockedLocation that = (RoamingBlockedLocation) o;
        return Objects.equals(id, that.id) && Objects.equals(gtPrefix, that.gtPrefix) && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, gtPrefix, reason);
    }
}
