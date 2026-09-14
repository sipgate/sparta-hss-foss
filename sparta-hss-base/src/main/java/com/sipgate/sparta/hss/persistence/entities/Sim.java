package com.sipgate.sparta.hss.persistence.entities;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "sim")
public class Sim implements Serializable {

    @Serial
    private static final long serialVersionUID = 202503072354L;

    private Integer id;
    private Msisdn msisdn;
    private byte[] secretKey;
    private Long sqn;
    private byte[] op;
    private Set<Imsi> imsis;
    private String iccid;

    public Sim() {
    }

    public Sim(final String activeMainImsi) {
        this(activeMainImsi, null);
    }

    public Sim(final String activeMainImsi, final byte[] secretKey) {
        this(null, activeMainImsi, secretKey, null);
    }

    public Sim(final Msisdn msisdn, final String activeMainImsi, final byte[] secretKey, final Long sqn) {
        this.msisdn = msisdn;

        final Set<Imsi> imsis = new HashSet<>();
        final var imsi = new Imsi();
        imsi.setType(Imsi.ImsiType.main);
        imsi.setState(Imsi.ImsiState.active);
        imsi.setImsi(activeMainImsi);
        imsis.add(imsi);
        this.imsis = imsis;

        this.secretKey = secretKey;
        this.sqn = sqn;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    public Integer getId() {
        return this.id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "msisdn")
    public Msisdn getMsisdn() {
        return this.msisdn;
    }

    public void setMsisdn(final Msisdn msisdn) {
        this.msisdn = msisdn;
    }

    @Column(name = "secretKey", nullable = false)
    public byte[] getSecretKey() {
        return this.secretKey;
    }

    public void setSecretKey(final byte[] secretKey) {
        this.secretKey = secretKey;
    }

    @Column(name = "sqn")
    public Long getSqn() {
        return this.sqn;
    }

    public void setSqn(final Long sqn) {
        this.sqn = sqn;
    }

    /// The Milenage operator key: OP or the pre-computed OPc, as declared by the deployment's
    /// [com.sipgate.sparta.hss.diameter.common.auth.StoredKeyFormat].
    @Column(name = "op", nullable = false)
    public byte[] getOp() {
        return op;
    }

    public void setOp(final byte[] op) {
        this.op = op;
    }

    @OneToMany(mappedBy = "sim", fetch = FetchType.EAGER)
    public Set<Imsi> getImsis() {
        return imsis;
    }

    public void setImsis(final Set<Imsi> imsis) {
        this.imsis = imsis;
    }

    @Transient
    public Imsi getFirstImsiObject() {
        return imsis.stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No active main Imsi found for sim id " + this.id.toString()));
    }

    public String toString() {
        return String.join("imsis: ", imsis.toString()) + " msisdn: " + msisdn + " iccid: " + iccid;
    }

    @Column(name = "iccid", nullable = false)
    public String getIccid() {
        return iccid;
    }

    public void setIccid(final String iccid) {
        this.iccid = iccid;
    }
}
