package com.rosogisoft.domain;

public enum LlmAccessLevel {
    NONE,
    READ,
    READ_WRITE;

    public boolean canRead() {
        return this == READ || this == READ_WRITE;
    }

    public boolean canWrite() {
        return this == READ_WRITE;
    }
}
