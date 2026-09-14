package com.sipgate.sparta.hss.diameter.common.auth;

import java.util.regex.Pattern;

public class MncMccFormatter {

    /// 3GPP TS 23.003 EPC home network realm/domain: `epc.mnc<MNC>.mcc<MCC>.3gppnetwork.org`,
    /// where MCC is 3 digits and MNC is 2 or 3 digits (it may or may not be left-padded with a `0`).
    /// Matching is case-insensitive because domain names are.
    private static final Pattern ORIGIN_REALM_PATTERN =
        Pattern.compile("(?i)mnc(\\d{2,3})\\.mcc(\\d{3})\\.3gppnetwork\\.org$");

    /// Extracts MCC and MNC from a 3GPP EPC origin realm of the form
    /// `epc.mnc<MNC>.mcc<MCC>.3gppnetwork.org`.
    ///
    /// @param originRealm the Origin-Realm AVP value, may be `null`
    /// @return the parsed [MccMnc], or `null` if the realm does not match the expected format
    public static MccMnc originRealmToMccMnc(final String originRealm) {
        if (originRealm == null) {
            return null;
        }
        final var matcher = ORIGIN_REALM_PATTERN.matcher(originRealm);
        if (!matcher.find()) {
            return null;
        }
        final var mnc = matcher.group(1);
        final var mcc = matcher.group(2);
        return new MccMnc(mcc, mnc);
    }

    /**
     *
     * @param mcc always 3 digits
     * @param mnc 2 or 3 digits, i.e. you must give "nn" or "nnn", never "n"
     * @return serving network ID binary encoded
     */
    public static byte[] mccMncToSnId(final String mcc, final String mnc) {

        assert mcc.length() == 3 : "MCC must be exactly 3 letters long, MCC: " + mcc;
        assert mnc.length() == 2 || mnc.length() == 3 : "MNC must be 2 or 3 letters long, MNC: " + mnc;

        /*
         * From ETSI TS 133 401, Section A.2 KASME derivation function
         * SN id (serving network ID) is composed of MCC and MNC
         * Octets:
         *   1      MCC digit 2 | MCC digit 1
         *   2      MNC digit 3 | MCC digit 3
         *   3      MNC digit 2 | MNC digit 1
         *
         *   Hint: MNCs lower than 100 need "special treatment" (they will be padded with 0xF0)
         *   (See ETSI TS 124 301)
         *
         * Examples
         *   MCC MNC                           |     MCC MNC
         *   262  03                           |     405 872
         *   ^^^  ^^---------- MNC digit 2     |     ^^^ ^^^---------- MNC digit 3
         *   |||  `----------- MNC digit 1     |     ||| |`----------- MNC digit 2
         *   |||                               |     ||| `------------ MNC digit 1
         *   ||`-------------- MCC digit 3     |     ||`-------------- MCC digit 3
         *   |`--------------- MCC digit 2     |     |`--------------- MCC digit 2
         *   `---------------- MCC digit 1     |     `---------------- MCC digit 1
         *
         */

        final var sn_id = new byte[3];

        // MCC digit 2 | MCC digit 1
        sn_id[0] = (byte) ((digit2(mcc) << 4) | digit1(mcc));

        if (mnc.length() == 2) {
            // padding | MCC digit 3
            sn_id[1] = (byte) (0xF0 | digit3(mcc));
        } else {
            // MNC digit 3 | MCC digit 3
            sn_id[1] = (byte) ((digit3(mnc) << 4) | digit3(mcc));
        }

        // MNC digit 2 | MNC digit 1
        sn_id[2] = (byte) ((digit2(mnc) << 4) | digit1(mnc));

        return sn_id;
    }

    public record MccMnc(String mcc, String mnc) {
        public String plmn() {
            return mcc + mnc;
        }
    }

    /**
     *
     * @param snId 3 bytes serving network ID
     * @return MccMnc record containing mcc, mnc, and plmn
     */
    public static MccMnc snIdToMccMnc(final byte[] snId) {
        if (snId == null || snId.length != 3) {
            throw new IllegalArgumentException("SN ID must be exactly 3 bytes long");
        }

        final int mcc1 = snId[0] & 0x0F;
        final int mcc2 = (snId[0] & 0xF0) >>> 4;
        final int mcc3 = snId[1] & 0x0F;

        final int mnc3 = (snId[1] & 0xF0) >>> 4;
        final int mnc1 = snId[2] & 0x0F;
        final int mnc2 = (snId[2] & 0xF0) >>> 4;

        final String mcc = "" + mcc1 + mcc2 + mcc3;
        final String mnc = (mnc3 == 0x0F) ? ("" + mnc1 + mnc2) : ("" + mnc1 + mnc2 + mnc3);

        return new MccMnc(mcc, mnc);
    }


    private static int digit1(final String code) {return digitAt1b(code, 1);}
    private static int digit2(final String code) {return digitAt1b(code, 2);}
    private static int digit3(final String code) {return digitAt1b(code, 3);}
    private static int digitAt1b(final String code, final int oneBasedPos) {
        return code.charAt(oneBasedPos - 1) - '0';
    }

    public static String plmnToMcc(final String plmn) {
        if (plmn.length() >= 5) {
            return plmn.substring(0, 3);
        }

        throw new IllegalArgumentException("PLMN too short for MCC, PLMN: " + plmn);
    }

    public static String plmnToMnc(final String plmn) {
        if (plmn.length() >= 5) {
            return (plmn.substring(3));
        }

        throw new IllegalArgumentException("PLMN too short for MNC, PLMN: " + plmn);
    }
}
