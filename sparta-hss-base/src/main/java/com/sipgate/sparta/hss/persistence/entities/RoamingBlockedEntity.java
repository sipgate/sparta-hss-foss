package com.sipgate.sparta.hss.persistence.entities;

import java.io.Serializable;

public sealed interface RoamingBlockedEntity
        extends Serializable
        permits RoamingBlockedImsiOverride, RoamingBlockedImsiOverrideLte, RoamingBlockedLocation, RoamingBlockedLocationLte
{
    String getReason();

    void setReason(String reason);
}
