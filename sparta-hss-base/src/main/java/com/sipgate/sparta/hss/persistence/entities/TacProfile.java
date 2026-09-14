package com.sipgate.sparta.hss.persistence.entities;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "tac_profile")
public class TacProfile implements Serializable {
    @Serial
    private static final long serialVersionUID = 202305081054L;

    private String tac;
    private String name;
    private Date lastUpdate;

    public TacProfile() {
    }

    public TacProfile(final String tac, final String name, final Date lastUpdate) {
        this.tac = tac;
        this.name = name;
        this.lastUpdate = lastUpdate;
    }

    @Id
    @Column(name = "tac", nullable = false, length = 8)
    public String getTac() {
        return tac;
    }

    public void setTac(final String tac) {
        this.tac = tac;
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

    public TacProfile setLastUpdate(final Date lastUpdate) {
        this.lastUpdate = lastUpdate;
        return this;
    }
}
