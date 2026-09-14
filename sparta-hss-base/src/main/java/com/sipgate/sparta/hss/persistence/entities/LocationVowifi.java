package com.sipgate.sparta.hss.persistence.entities;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "location_vowifi")
public class LocationVowifi implements Serializable {
    @Serial
    private static final long serialVersionUID = 202503072354L;

    private Imsi imsi;
    // ponytail: in SWx aaa_server_name == diameter_host (no separate Server-Name AVP)
    private String aaaServerName;
    private String diameterHost;
    private String diameterRealm;
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

    @Column(name = "aaa_server_name", nullable = false)
    public String getAaaServerName() {
        return aaaServerName;
    }

    public void setAaaServerName(final String aaaServerName) {
        this.aaaServerName = aaaServerName;
    }

    @Column(name = "diameter_host", nullable = false)
    public String getDiameterHost() {
        return diameterHost;
    }

    public void setDiameterHost(final String diameterHost) {
        this.diameterHost = diameterHost;
    }

    @Column(name = "diameter_realm", nullable = false)
    public String getDiameterRealm() {
        return diameterRealm;
    }

    public void setDiameterRealm(final String diameterRealm) {
        this.diameterRealm = diameterRealm;
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

        final var locationVowifi = (LocationVowifi) o;
        return imsi.equals(locationVowifi.imsi)
                && aaaServerName.equals(locationVowifi.aaaServerName)
                && diameterHost.equals(locationVowifi.diameterHost)
                && diameterRealm.equals(locationVowifi.diameterRealm)
                && lastUpdate.equals(locationVowifi.lastUpdate);
    }

    @Override
    public int hashCode() {
        var result = imsi.hashCode();
        result = 31 * result + aaaServerName.hashCode();
        result = 31 * result + diameterHost.hashCode();
        result = 31 * result + diameterRealm.hashCode();
        result = 31 * result + lastUpdate.hashCode();
        return result;
    }
}
