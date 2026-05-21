package com.rosogisoft.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MockFunctionRuntime {

    private static final String ALNUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ConcurrentHashMap<String, AtomicLong> SEQUENCES = new ConcurrentHashMap<>();

    private final TemplateRenderContext context;

    public MockFunctionRuntime(TemplateRenderContext context) {
        this.context = context;
    }

    public String uuid(Object key) {
        if (key == null) {
            return UUID.randomUUID().toString();
        }
        String cacheKey = "uuid:" + key;
        return String.valueOf(context.functionCache().computeIfAbsent(cacheKey, ignored -> UUID.randomUUID().toString()));
    }

    public long random_int(Object min, Object max) {
        long minValue = toLong(min);
        long maxValue = toLong(max);
        if (maxValue < minValue) {
            long tmp = minValue;
            minValue = maxValue;
            maxValue = tmp;
        }
        long bound = maxValue - minValue + 1;
        if (bound <= 0) {
            return minValue;
        }
        return minValue + Math.floorMod(RANDOM.nextLong(), bound);
    }

    public String random_digits(Object length) {
        int size = Math.max(0, Math.min(512, (int) toLong(length)));
        StringBuilder result = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            result.append(RANDOM.nextInt(10));
        }
        return result.toString();
    }

    public String random_alnum(Object length) {
        int size = Math.max(0, Math.min(512, (int) toLong(length)));
        StringBuilder result = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            result.append(ALNUM.charAt(RANDOM.nextInt(ALNUM.length())));
        }
        return result.toString();
    }

    public long now_epoch_millis() {
        return Instant.now().toEpochMilli();
    }

    public long sequence(Object name) {
        String sequenceName = name != null ? String.valueOf(name) : "default";
        String owner = context.ownerId() != null ? String.valueOf(context.ownerId()) : "anonymous";
        return SEQUENCES.computeIfAbsent(owner + ":" + sequenceName, ignored -> new AtomicLong()).incrementAndGet();
    }

    public String upper(Object value) {
        return stringify(value).toUpperCase(Locale.ROOT);
    }

    public String lower(Object value) {
        return stringify(value).toLowerCase(Locale.ROOT);
    }

    public String replace(Object value, Object search, Object replacement) {
        return stringify(value).replace(stringify(search), stringify(replacement));
    }

    public String substring(Object value, Object start, Object end) {
        String text = stringify(value);
        int from = Math.max(0, Math.min(text.length(), (int) toLong(start)));
        int to = end == null ? text.length() : Math.max(from, Math.min(text.length(), (int) toLong(end)));
        return text.substring(from, to);
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
