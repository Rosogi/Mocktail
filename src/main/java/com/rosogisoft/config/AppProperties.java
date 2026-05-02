package com.rosogisoft.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mocktail.ports")
public class AppProperties {

    private int rangeStart = 9000;
    private int rangeEnd = 9999;

    public int getRangeStart () {
        return rangeStart;
    }

    public void setRangeStart (int rangeStart) {
        this.rangeStart = rangeStart;
    }

    public int getRangeEnd () {
        return rangeEnd;
    }

    public void setRangeEnd (int rangeEnd) {
        this.rangeEnd = rangeEnd;
    }
}
