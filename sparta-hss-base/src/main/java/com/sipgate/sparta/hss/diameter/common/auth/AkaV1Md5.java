package com.sipgate.sparta.hss.diameter.common.auth;

public record AkaV1Md5(byte[] rand, byte[] autn, byte[] xres, byte[] kasme, byte[] confidentialityKey, byte[] integrityKey) {}