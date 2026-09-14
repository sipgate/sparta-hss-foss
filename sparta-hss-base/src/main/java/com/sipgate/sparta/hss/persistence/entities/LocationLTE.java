package com.sipgate.sparta.hss.persistence.entities;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

@Entity
@Table(name = "location_lte")
public class LocationLTE implements Serializable {

    @Serial
    private static final long serialVersionUID = 202304191054L;

    private Imsi imsi;

    private String mmeHostname;
    private String mmeRealm;
    private String visitedPlmnId;
    private String tac;
    private TacProfile tacProfile;
    private Date lastUpdate;

    public LocationLTE() {
    }

    public LocationLTE(final Imsi imsi, final String mmeHostname, final String mmeRealm, final String visitedPlmnId, final String tac) {
        this.imsi = imsi;
        this.mmeHostname = mmeHostname;
        this.mmeRealm = mmeRealm;
        this.visitedPlmnId = visitedPlmnId;
        this.tac = tac;
    }

    @Id
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imsiId", referencedColumnName = "id")
    public Imsi getImsi() {
        return imsi;
    }

    public LocationLTE setImsi(final Imsi imsi) {
        this.imsi = imsi;
        return this;
    }

    @Column(name = "mmeHostname", nullable = false)
    public String getMmeHostname() {
        return mmeHostname;
    }

    public LocationLTE setMmeHostname(final String mmeHostname) {
        this.mmeHostname = mmeHostname;
        return this;
    }

    @Column(name = "mmeRealm", nullable = false)
    public String getMmeRealm() {
        return mmeRealm;
    }

    public LocationLTE setMmeRealm(final String mmeRealm) {
        this.mmeRealm = mmeRealm;
        return this;
    }

    @Column(name = "visitedPlmnId")
    public String getVisitedPlmnId() {
        return visitedPlmnId;
    }

    public LocationLTE setVisitedPlmnId(final String visitedPlmnId) {
        this.visitedPlmnId = visitedPlmnId;
        return this;
    }

    @Column(name = "tac", length = 8)
    public String getTac() {
        return tac;
    }

    public LocationLTE setTac(final String tac) {
        this.tac = tac;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tac", referencedColumnName = "tac", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "none", value = ConstraintMode.NO_CONSTRAINT)
    )
    public TacProfile getTacProfile() {
        return tacProfile;
    }

    public void setTacProfile(final TacProfile tacProfile) {
        this.tacProfile = tacProfile;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "lastUpdate", nullable = false, length = 19)
    public Date getLastUpdate() {
        return lastUpdate;
    }

    public LocationLTE setLastUpdate(final Date lastUpdate) {
        this.lastUpdate = lastUpdate;
        return this;
    }

    public LocationLTE copy() {
        final var copy = new LocationLTE(imsi, mmeHostname, mmeRealm, visitedPlmnId, tac);
        copy.setLastUpdate(lastUpdate);
        copy.setTacProfile(tacProfile);
        return copy;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        final var that = (LocationLTE) o;
        return imsi.equals(that.imsi)
                && mmeHostname.equals(that.mmeHostname)
                && mmeRealm.equals(that.mmeRealm)
                && Objects.equals(visitedPlmnId, that.visitedPlmnId)
                && Objects.equals(tac, that.tac)
                && Objects.equals(tacProfile, that.tacProfile)
                && lastUpdate.equals(that.lastUpdate);
    }

    @Override
    public int hashCode() {
        var result = imsi.hashCode();
        result = 31 * result + mmeHostname.hashCode();
        result = 31 * result + mmeRealm.hashCode();
        result = 31 * result + Objects.hashCode(visitedPlmnId);
        result = 31 * result + Objects.hashCode(tac);
        result = 31 * result + Objects.hashCode(tacProfile);
        result = 31 * result + lastUpdate.hashCode();
        return result;
    }
}
