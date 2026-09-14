package com.sipgate.sparta.hss.diameter.common;

/// Encoding helpers for the BCD/TBCD octet-string formats used on the Diameter wire that the old
/// XML interface delivered as plain digit strings.
///
/// Over the TCP-XML interface the DRA handed the HSS already-decoded digit strings (e.g. a
/// Visited-PLMN-Id of `"26207"`). On a direct Diameter connection these AVPs are octet
/// strings, so the values have to be decoded/encoded here to keep the business logic working on the
/// same digit-string representation as before.
public final class Tbcd {

  private static final int NIBBLE_FILLER = 0x0F;

  private Tbcd() {}


    // TODO: Merge with MccMncFormatter
  /// Decodes the 3-octet Visited-PLMN-Id (3GPP TS 23.003 §12.1, the layout produced by
  /// [com.sipgate.sparta.hss.diameter.common.auth.MncMccFormatter#mccMncToSnId]) into the
  /// `MCC`+`MNC` digit string the handlers expect (e.g. `"26207"`).
  ///
  /// <pre>
  ///   octet 1: MCC digit 2 | MCC digit 1
  ///   octet 2: MNC digit 3 | MCC digit 3   (MNC digit 3 == 0xF for a 2-digit MNC)
  ///   octet 3: MNC digit 2 | MNC digit 1
  /// </pre>
  public static String decodePlmnId(final byte[] plmn) {
    if (plmn == null || plmn.length != 3) {
      return null;
    }
    final int mcc1 = plmn[0] & 0x0F;
    final int mcc2 = (plmn[0] >> 4) & 0x0F;
    final int mcc3 = plmn[1] & 0x0F;
    final int mnc3 = (plmn[1] >> 4) & 0x0F;
    final int mnc1 = plmn[2] & 0x0F;
    final int mnc2 = (plmn[2] >> 4) & 0x0F;

    final var mcc = "" + mcc1 + mcc2 + mcc3;
    final var mnc = mnc3 == NIBBLE_FILLER ? ("" + mnc1 + mnc2) : ("" + mnc1 + mnc2 + mnc3);
    return mcc + mnc;
  }

  /// TBCD-encodes a string of decimal digits (3GPP TS 29.002): digits are swapped within each octet
  /// and an odd-length string is padded with a 0xF high nibble in the final octet. Used for the
  /// MSISDN AVP (3GPP TS 29.329 §6.3.2).
  public static byte[] encodeTbcd(final String digits) {
    if (digits == null) {
      return null;
    }
    final var length = digits.length();
    final var out = new byte[(length + 1) / 2];
    for (var i = 0; i < length; i++) {
      final var nibble = digits.charAt(i) - '0';
      if ((i & 1) == 0) {
        out[i / 2] = (byte) nibble;
      } else {
        out[i / 2] |= (byte) (nibble << 4);
      }
    }
    if ((length & 1) == 1) {
      out[out.length - 1] |= (byte) (NIBBLE_FILLER << 4);
    }
    return out;
  }
}
