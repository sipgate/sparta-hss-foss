package com.sipgate.sparta.hss.diameter.common.auth;

/// The HSS-internal AUC: produces AKA authentication vectors for a subscriber's SIM.
///
/// 3GPP does not specify an interface between the HSS and its AUC, so this contract is ours.
/// Implementations own the key material handling and the SQN state: they increment the SQN per
/// vector and re-synchronise it when the input carries an AUTS ([MilenageInput#resyncInfo()]).
/// The built-in implementation is [MilenageAuthenticator]; operators replace it when their key
/// material or algorithm set differs, e.g. an HSM-backed or remote AUC.
public interface Authenticator {

    /// An E-UTRAN (EPS-AKA) vector: includes KASME derived for the visited PLMN
    /// (3GPP TS 33.401 Annex A.2).
    AkaV1Md5 generate4GAuthenticationVector(MilenageLogger milenageLogger, MilenageInput input, String visitedPlmnId)
            throws Exception;

    /// A UMTS AKA vector as used by UTRAN, IMS-AKA and EAP-AKA: RAND, AUTN, XRES, CK, IK
    /// without KASME (3GPP TS 33.102 §6.3).
    AkaV1Md5 generate3GAuthenticationVector(MilenageLogger milenageLogger, MilenageInput input) throws Exception;
}
