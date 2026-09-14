package com.sipgate.sparta.hss.persistence;

/// An inclusive IMSI interval, compared as strings like the `imsi` column.
public record ImsiRange(String from, String to) {}
