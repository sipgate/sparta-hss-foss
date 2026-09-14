package com.sipgate.sparta.hss.persistence.entities;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "imsi_profile")
public class ImsiProfile implements Serializable {
    @Serial
    private static final long serialVersionUID = 202305081054L;

    private Imsi imsi;
    private String name;
    private Date lastUpdate;

    public ImsiProfile() {
    }

    public ImsiProfile(final Imsi imsi, final String name, final Date lastUpdate) {
        this.imsi = imsi;
        this.name = name;
        this.lastUpdate = lastUpdate;
    }

    @Id
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "imsiId", referencedColumnName = "id")
    public Imsi getImsi() {
        return imsi;
    }

    public void setImsi(final Imsi imsi) {
        this.imsi = imsi;
    }

    @Column(name = "name", nullable = false)
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "lastUpdate", nullable = false, length = 19)
    public Date getLastUpdate() {
        return lastUpdate;
    }

    public ImsiProfile setLastUpdate(final Date lastUpdate) {
        this.lastUpdate = lastUpdate;
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        final var that = (ImsiProfile) o;
        return imsi.equals(that.imsi) && name.equals(that.name) && lastUpdate.equals(that.lastUpdate);
    }

    @Override
    public int hashCode() {
        var result = imsi.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + lastUpdate.hashCode();
        return result;
    }
}
