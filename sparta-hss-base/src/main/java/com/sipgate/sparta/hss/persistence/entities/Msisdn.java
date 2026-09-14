package com.sipgate.sparta.hss.persistence.entities;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;


@Entity
@Table(name = "msisdn",
        uniqueConstraints = {@UniqueConstraint(columnNames = "msisdn")})
public class Msisdn implements Serializable {

    @Serial
    private static final long serialVersionUID = 202503072354L;

    private Integer id;
    private String msisdn;
    private Set<Sim> sims = new HashSet<>(0);

    public Msisdn() {
    }

    public Msisdn(final String msisdn) {
        this.msisdn = msisdn;
    }

    public Msisdn(final Set<Sim> sims) {
        this.sims = sims;
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

    @Column(name = "msisdn", unique = true, nullable = false, length = 45)
    public String getMsisdn() {
        return this.msisdn;
    }

    public void setMsisdn(final String msisdn) {
        this.msisdn = msisdn;
    }

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "msisdn")
    public Set<Sim> getSims() {
        return this.sims;
    }

    public void setSims(final Set<Sim> sims) {
        this.sims = sims;
    }

    @Transient
    public Sim getSim() {
        if ((sims != null) && (!sims.isEmpty())) {
            return sims.iterator().next();
        }
        return null;
    }

}
