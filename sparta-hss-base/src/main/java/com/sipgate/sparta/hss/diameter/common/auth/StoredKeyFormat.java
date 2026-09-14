package com.sipgate.sparta.hss.diameter.common.auth;

/// What the SIM's operator-key column holds. 3GPP TS 35.205 defines both deployments: the
/// pre-computed OPc (recommended, a database leak does not disclose the operator-wide OP), or
/// OP with OPc derived on every invocation (OPc = E_K(OP) &#8853; OP, TS 35.206 Annex 1).
public enum StoredKeyFormat {

    /// The column holds OP; OPc is derived per authentication.
    OP,

    /// The column holds the pre-computed OPc.
    OPC
}
