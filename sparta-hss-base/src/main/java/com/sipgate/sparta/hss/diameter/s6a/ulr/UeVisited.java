package com.sipgate.sparta.hss.diameter.s6a.ulr;

/// Published when a ULR carries terminal information, i.e. a UE was seen on a location update
/// with the given device type (TAC) and software version.
public record UeVisited(
    String imsi,
    String tac,
    String softwareVersion,
    String profileName,
    String profileReason
) {}
