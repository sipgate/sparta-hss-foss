package com.sipgate.sparta.hss.persistence.entities;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "imsi",
        uniqueConstraints = @UniqueConstraint(columnNames = "imsi"))
public class Imsi implements Serializable {

    @Serial
    private static final long serialVersionUID = 202503072354L;

    private int id;
    private String imsi;
    private Sim sim;
    private ImsiState state;
    private ImsiType type;
    private Date assignedAt;
    private ImsiProfile imsiProfile;

    public Imsi() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    public int getId() {
        return id;
    }

    public void setId(final int id) {
        this.id = id;
    }

    @Column(name = "imsi", unique = true, nullable = false, length = 15)
    public String getImsi() {
        return imsi;
    }

    public void setImsi(final String imsi) {
        this.imsi = imsi;
    }

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "simId", referencedColumnName = "id")
    public Sim getSim() {
        return sim;
    }

    public void setSim(final Sim simByAssignedTo) {
        this.sim = simByAssignedTo;
    }

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id", referencedColumnName = "imsiId")
    public ImsiProfile getImsiProfile() {
        return imsiProfile;
    }

    public Imsi setImsiProfile(final ImsiProfile imsiProfile) {
        this.imsiProfile = imsiProfile;
        return this;
    }

    @Column(name = "state", nullable = false)
    @Enumerated(EnumType.STRING)
    public ImsiState getState() {
        return state;
    }

    public void setState(final ImsiState state) {
        this.state = state;
    }

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    public ImsiType getType() {
        return type;
    }

    public void setType(final ImsiType type) {
        this.type = type;
    }

    @Column(name = "assignedAt")
    public Date getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(final Date assignedAt) {
        this.assignedAt = assignedAt;
    }

    @Override
    public String toString() {
        return "[imsi=" + imsi + ", state=" + state + ", type=" + type + "]";
    }

    public enum ImsiState {
        active
    }

    public enum ImsiType {
        main
    }

}
