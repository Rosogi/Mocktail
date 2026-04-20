package com.rosogisoft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MocktailApplication {

    public static void main (String[] args) {
        System.setProperty("com.sun.jndi.ldap.connect.pool", "false");
        System.setProperty("com.sun.jndi.ldap.connect.timeout", "5000");
        System.setProperty("com.sun.jndi.ldap.read.timeout", "10000");
        SpringApplication.run(MocktailApplication.class, args);
    }
}
