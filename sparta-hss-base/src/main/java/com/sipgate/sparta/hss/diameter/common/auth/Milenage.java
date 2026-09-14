package com.sipgate.sparta.hss.diameter.common.auth;

/**
 * 3GPP Milenage authentication algorithms f1, f1*, f2, f3, f4, f5, f5* and OPc computation,
 * as specified in 3GPP TS 35.206 (Annex 1: algorithm specification). Block cipher is
 * AES-128 (Rijndael, 128-bit block/key) via {@link Rijndael32Bit}. Reference test sets:
 * 3GPP TS 35.208. Rotations r1..r5 and constants c1..c5 per TS 35.206 Annex 1.
 */
public class Milenage {

    /**
     * Algorithm f1. Computes network authentication code MAC-A from key K, random challenge RAND, sequence number SQN
     * and authentication management field AMF (3GPP TS 35.206).
     *
     * @return MAC-A
     */
    public static byte[] f1(final byte[] secretKey, final byte[] rand, final byte[] op_c,
                            final byte[] sqn, final byte[] amf)
            throws ArrayIndexOutOfBoundsException {
        final var rijndael = new Rijndael32Bit();
        rijndael.init(secretKey);
        var temp = new byte[16];
        final var in1 = new byte[16];
        var out1 = new byte[16];
        final var rijndaelInput = new byte[16];
        final var mac = new byte[8];

        for (var i = 0; i < rand.length && i < op_c.length; i++) {
            rijndaelInput[i] = (byte) (rand[i] ^ op_c[i]);
        }

        temp = rijndael.encrypt(rijndaelInput);

        for (var i = 0; i < sqn.length; i++) {
            in1[i] = sqn[i];
            in1[i + 8] = sqn[i];
        }
        // IN1 = SQN[0..5] ‖ AMF[6..7] ‖ SQN[8..13] ‖ AMF[14..15] (TS 35.206)
        for (var i = 0; i < amf.length; i++) {
            in1[i + 6] = amf[i];
            in1[i + 14] = amf[i];
        }

        /*
         * XOR op_c and in1, rotate by r1=64, and XOR on the constant c1 (which
         * is all zeroes)
         */
        for (var i = 0; i < in1.length; i++) {
            rijndaelInput[(i + 8) % 16] = (byte) (in1[i] ^ op_c[i]);
        }

        /* XOR on the value temp computed before */
        for (var i = 0; i < temp.length; i++) {
            rijndaelInput[i] ^= temp[i];
        }

        out1 = rijndael.encrypt(rijndaelInput);
        for (var i = 0; i < out1.length && i < op_c.length; i++) {
            out1[i] = (byte) (out1[i] ^ op_c[i]);
        }

        for (var i = 0; i < 8; i++) {
            mac[i] = (byte) out1[i]; // MAC-A = OUT1[0..7] (TS 35.206)
        }

        return mac;

    }

    /**
     * Algorithm f2. Takes key K and random challenge RAND, and returns response RES (3GPP TS 35.206).
     *
     * @return RES (or XRES)
     */
    public static byte[] f2(final byte[] secretKey, final byte[] rand, final byte[] op_c)
            throws ArrayIndexOutOfBoundsException {

        final var rijndael = new Rijndael32Bit();
        rijndael.init(secretKey);
        var temp = new byte[16];
        var out = new byte[16];
        final var rijndaelInput = new byte[16];
        final var res = new byte[8];

        for (var i = 0; i < rand.length && i < op_c.length; i++) {
            rijndaelInput[i] = (byte) (rand[i] ^ op_c[i]);
        }
        temp = rijndael.encrypt(rijndaelInput);

        /*
         * To obtain output block OUT2: XOR OPc and TEMP, rotate by r2=0, and
         * XOR on the constant c2 (which is all zeroes except that the last bit
         * is 1).
         */

        for (var i = 0; i < temp.length && i < op_c.length; i++) {
            rijndaelInput[i] = (byte) (temp[i] ^ op_c[i]);
        }
        rijndaelInput[15] ^= 1;

        out = rijndael.encrypt(rijndaelInput);
        for (var i = 0; i < out.length && i < op_c.length; i++) {
            out[i] = (byte) (out[i] ^ op_c[i]);
        }

        for (var i = 0; i < res.length; i++) {
            res[i] = (byte) out[i + 8];
        }

        return res;

    }

    /**
     * Algorithm f3. Takes key K and random challenge RAND, and returns confidentiality key CK.
     *
     * @return CK confidentiality key
     */
    public static byte[] f3(final byte[] secretKey, final byte[] rand, final byte[] op_c)
            throws ArrayIndexOutOfBoundsException {
        final var rijndael = new Rijndael32Bit();
        rijndael.init(secretKey);
        var temp = new byte[16];
        var out = new byte[16];
        final var rijndaelInput = new byte[16];
        final var ck = new byte[16];

        for (var i = 0; i < 16; i++) {
            rijndaelInput[i] = (byte) (rand[i] ^ op_c[i]);
        }

        temp = rijndael.encrypt(rijndaelInput);

        /*
         * To obtain output block OUT3: XOR OPc and TEMP,
         * rotate by r3=32, and XOR on the constant c3 (which
         * is all zeroes except that the next to last bit is 1).
         */

        for (var i = 0; i < 16; i++) {
            rijndaelInput[(i + 12) % 16] = (byte) (temp[i] ^ op_c[i]);
        }
        rijndaelInput[15] ^= 2;

        out = rijndael.encrypt(rijndaelInput);
        for (var i = 0; i < 16; i++) {
            out[i] = (byte) (out[i] ^ op_c[i]);
        }

        for (var i = 0; i < 16; i++) {
            ck[i] = (byte) out[i];
        }

        return ck;
    }

    /**
     * Algorithm f4. Takes key K and random challenge RAND, and returns integrity key IK (3GPP TS 35.206).
     * AK is produced by f5, not f4.
     *
     * @return IK integrity key
     */
    public static byte[] f4(final byte[] secretKey, final byte[] rand, final byte[] op_c)
            throws ArrayIndexOutOfBoundsException {
        final var rijndael = new Rijndael32Bit();
        rijndael.init(secretKey);
        var temp = new byte[16];
        var out = new byte[16];
        final var rijndaelInput = new byte[16];
        final var ik = new byte[16];

        for (var i = 0; i < 16; i++) {
            rijndaelInput[i] = (byte) (rand[i] ^ op_c[i]);
        }

        temp = rijndael.encrypt(rijndaelInput);

        /*
         * To obtain output block OUT4: XOR OPc and TEMP, rotate by r4=64, and
         * XOR on the constant c4 (which is all zeroes except that the 2nd from
         * last bit is 1).
         */

        for (var i = 0; i < 16; i++) {
            rijndaelInput[(i + 8) % 16] = (byte) (temp[i] ^ op_c[i]);
        }
        rijndaelInput[15] ^= 4;

        out = rijndael.encrypt(rijndaelInput);
        for (var i = 0; i < 16; i++) {
            out[i] = (byte) (out[i] ^ op_c[i]);
        }

        for (var i = 0; i < 16; i++) {
            ik[i] = (byte) out[i];
        }

        return ik;
    }

    /**
     * Algorithm f5. Takes key K and random challenge RAND, and returns anonymity key AK.
     *
     * @return AK anonymity key
     */
    public static byte[] f5(final byte[] secretKey, final byte[] rand, final byte[] op_c)
            throws ArrayIndexOutOfBoundsException {
        final var rijndael = new Rijndael32Bit();
        rijndael.init(secretKey);
        var temp = new byte[16];
        var out = new byte[16];
        final var rijndaelInput = new byte[16];
        final var ak = new byte[6];

        for (var i = 0; i < 16; i++) {
            rijndaelInput[i] = (byte) (rand[i] ^ op_c[i]);
        }
        temp = rijndael.encrypt(rijndaelInput);

        /*
         * To obtain output block OUT2: XOR OPc and TEMP, rotate by r2=0, and
         * XOR on the constant c2 (which is all zeroes except that the last bit
         * is 1).
         */

        for (var i = 0; i < 16; i++) {
            rijndaelInput[i] = (byte) (temp[i] ^ op_c[i]);
        }
        rijndaelInput[15] ^= 1;

        out = rijndael.encrypt(rijndaelInput);
        for (var i = 0; i < 16; i++) {
            out[i] = (byte) (out[i] ^ op_c[i]);
        }

        for (var i = 0; i < 6; i++) {
            ak[i] = (byte) out[i]; // AK = OUT2[0..5] (TS 35.206); f5 reuses OUT2 (r2=0, c2) with f2
        }

        return ak;
    }

    /**
     * Algorithm f1*. Computes resynch authentication code MAC-S from key K, random challenge RAND, sequence number SQN
     * and authentication management field AMF (3GPP TS 35.206).
     *
     * @return MAC-S resynch authentication code
     */
    public static byte[] f1star(final byte[] secretKey, final byte[] rand, final byte[] op_c, final byte[] sqn,
                                final byte[] amf)
            throws ArrayIndexOutOfBoundsException {
        final var rijndael = new Rijndael32Bit();
        rijndael.init(secretKey);
        var temp = new byte[16];
        final var in1 = new byte[16];
        var out1 = new byte[16];
        final var rijndaelInput = new byte[16];
        final var mac = new byte[8];

        for (var i = 0; i < 16; i++) {
            rijndaelInput[i] = (byte) (rand[i] ^ op_c[i]);
        }
        temp = rijndael.encrypt(rijndaelInput);

        for (var i = 0; i < 6; i++) {
            in1[i] = sqn[i];
            in1[i + 8] = sqn[i];
        }
        for (var i = 0; i < 2; i++) {
            in1[i + 6] = amf[i];
            in1[i + 14] = amf[i];
        }

        /*
         * XOR op_c and in1, rotate by r1=64, and XOR
         * on the constant c1 (which is all zeroes)
         */

        for (var i = 0; i < 16; i++) {
            rijndaelInput[(i + 8) % 16] = (byte) (in1[i] ^ op_c[i]);
        }

        /* XOR on the value temp computed before */

        for (var i = 0; i < 16; i++) {
            rijndaelInput[i] ^= temp[i];
        }

        out1 = rijndael.encrypt(rijndaelInput);
        for (var i = 0; i < 16; i++) {
            out1[i] ^= op_c[i];
        }

        for (var i = 0; i < 8; i++) {
            mac[i] = (byte) out1[i + 8]; // MAC-S = OUT1[8..15] (same OUT1 as f1, TS 35.206)
        }

        return mac;
    }

    /**
     * Algorithm f5*. Takes key K and random challenge RAND, and return resynch anonymity key AK (3GPP TS 35.206).
     *
     * @return AK
     */
    public static byte[] f5star(final byte[] secretKey, final byte[] rand, final byte[] op_c)
            throws ArrayIndexOutOfBoundsException {
        final var rijndael = new Rijndael32Bit();
        rijndael.init(secretKey);
        var temp = new byte[16];
        var out = new byte[16];
        final var rijndaelInput = new byte[16];
        final var ak = new byte[6];

        for (var i = 0; i < 16; i++) {
            rijndaelInput[i] = (byte) (rand[i] ^ op_c[i]);
        }
        temp = rijndael.encrypt(rijndaelInput);

        /*
         * To obtain output block OUT5: XOR OPc and TEMP,
         * rotate by r5=96, and XOR on the constant c5 (which
         * is all zeroes except that the 3rd from last bit is 1).
         */

        for (var i = 0; i < 16; i++) {
            rijndaelInput[(i + 4) % 16] = (byte) (temp[i] ^ op_c[i]);
        }
        rijndaelInput[15] ^= 8;

        out = rijndael.encrypt(rijndaelInput);
        for (var i = 0; i < 16; i++) {
            out[i] = (byte) (out[i] ^ op_c[i]);
        }

        for (var i = 0; i < 6; i++) {
            ak[i] = (byte) out[i];
        }

        return ak;
    }

    /**
     * Computes OPc = E_K(OP) ⊕ OP (3GPP TS 35.206, Annex 1: algorithm specification). OPc is derived
     * from the operator variant value OP and the subscriber key K; the key schedule is performed here.
     *
     * @return OPc
     */
    public static byte[] computeOpC(final byte[] secretKey, final byte[] op)
            throws ArrayIndexOutOfBoundsException {
        final var rijndael = new Rijndael32Bit();
        rijndael.init(secretKey);
        final var byteOp_c = rijndael.encrypt(op);
        final var op_c = new byte[byteOp_c.length];
        for (var i = 0; i < byteOp_c.length; i++) {
            op_c[i] = (byte) (byteOp_c[i] ^ op[i]); // OPc = E_K(OP) ⊕ OP (TS 35.206, Annex 1)
        }

        return op_c;
    }
}
