package com.rosogisoft.web.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class MockDefinitionForm {

    private String  name               = "";
    private String  httpMethod         = "GET";
    private String  pathPattern        = "/";
    private String  requestBodyContains;

    private int     responseStatus     = 200;
    private String  responseBody       = "";
    private String  responseContentType = "application/json";

    /**
     * Extra headers as a plain text, one per line, format: "Key: Value"
     * The UI textarea sends this; we parse it into a map.
     */
    private String  responseHeadersRaw = "";

    private int     priority           = 0;
    private boolean active             = true;
    private Long    collectionId;

    /**
     * Parse the raw headers text into a proper map.
     * Lines that are empty or don't contain ":" are silently skipped.
     */
    public Map<String, String> getParsedHeaders() {
        Map<String, String> result = new LinkedHashMap<>();
        if (responseHeadersRaw == null || responseHeadersRaw.isBlank()) {
            return result;
        }
        for (String line : responseHeadersRaw.split("\\r?\\n")) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                String key   = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                if (!key.isEmpty()) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }
}

