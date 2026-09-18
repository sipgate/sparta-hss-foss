package com.sipgate.sparta.hss.persistence.entities;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "location_ip_sm_gw")
public class LocationIpSmGw implements Serializable {
    @Serial
    private static final long serialVersionUID = 202609161530L;

    private Imsi imsi;
    private String ipSmGwName;
    private String ipSmGwRealm;
    private Date lastUpdate = new Date();

    @Id
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imsiId", referencedColumnName = "id")
    public Imsi getImsi() {
        return imsi;
    }

    public void setImsi(final Imsi imsi) {
        this.imsi = imsi;
    }

    @Column(name = "ipSmGwName", nullable = false)
    public String getIpSmGwName() {
        return ipSmGwName;
    }

    public void setIpSmGwName(final String ipSmGwName) {
        this.ipSmGwName = ipSmGwName;
    }

    @Column(name = "ipSmGwRealm", nullable = false)
    public String getIpSmGwRealm() {
        return ipSmGwRealm;
    }

    public void setIpSmGwRealm(final String ipSmGwRealm) {
        this.ipSmGwRealm = ipSmGwRealm;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "lastUpdate", nullable = false, length = 19)
    public Date getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(final Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        final var locationIpSmGw = (LocationIpSmGw) o;
        return imsi.equals(locationIpSmGw.imsi)
                && ipSmGwName.equals(locationIpSmGw.ipSmGwName)
                && ipSmGwRealm.equals(locationIpSmGw.ipSmGwRealm)
                && lastUpdate.equals(locationIpSmGw.lastUpdate);
    }

    @Override
    public int hashCode() {
        var result = imsi.hashCode();
        result = 31 * result + ipSmGwName.hashCode();
        result = 31 * result + ipSmGwRealm.hashCode();
        result = 31 * result + lastUpdate.hashCode();
        return result;
    }
}
