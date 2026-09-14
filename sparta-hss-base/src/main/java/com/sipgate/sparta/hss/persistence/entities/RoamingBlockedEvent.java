package com.sipgate.sparta.hss.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import static jakarta.persistence.GenerationType.IDENTITY;

@Entity
@Table(name = "roaming_blocked_event", indexes = {
    @Index(columnList = "imsi"),
    @Index(columnList = "created_at")
})
public class RoamingBlockedEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 20260623;

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Integer id;

    @Column(name = "imsi", nullable = false, length = 20)
    private String imsi;

    @Column(name = "mcc", nullable = false, length = 20)
    private String mcc;

    @Column(name = "component", nullable = false, length = 20)
    private String component;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public RoamingBlockedEvent() {
    }

    public RoamingBlockedEvent(final String imsi, final String mcc, final String component, final Instant createdAt) {
        this.imsi = imsi;
        this.mcc = mcc;
        this.component = component;
        this.createdAt = createdAt;
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

    public String getComponent() {
        return component;
    }

    public void setComponent(final String component) {
        this.component = component;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final RoamingBlockedEvent that = (RoamingBlockedEvent) o;
        return Objects.equals(id, that.id)
            && Objects.equals(imsi, that.imsi)
            && Objects.equals(mcc, that.mcc)
            && Objects.equals(component, that.component)
            && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, imsi, mcc, component, createdAt);
    }
}
