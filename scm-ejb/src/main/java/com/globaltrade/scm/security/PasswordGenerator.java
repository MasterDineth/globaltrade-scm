package com.globaltrade.scm.security;
public class PasswordGenerator {
    public static void main(String[] args) {
        System.out.println("HASH_START:" + SecurityUtil.hashPassword("password123") + ":HASH_END");
    }
}
