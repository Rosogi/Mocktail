package com.rosogisoft.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a mock response body template by substituting {{expression}}
 * placeholders with values extracted from the incoming request.
 *
 * Supported expressions:
 *
 *   {{fieldName}}              – top-level field from JSON request body
 *   {{user.address.city}}      – nested JSON path (dot-separated)
 *   {{param.queryParamName}}   – URL query parameter
 *   {{header.HeaderName}}      – request header (case-insensitive)
 *   {{request.method}}         – HTTP method (GET, POST, …)
 *   {{request.path}}           – request path (/api/users/1)
 *
 * If a placeholder cannot be resolved it is left unchanged.
 */
@Slf4j
@Service
public class MockTemplateEngine {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^}]+)}}");
    private static final ObjectMapper MAPPER  = new ObjectMapper();

    /**
     * @param template      Raw mock responseBody (may contain {{…}} placeholders)
     * @param method        HTTP method of the incoming request
     * @param path          Request path
     * @param queryParams   Raw query string (key=val&key2=val2), may be null
     * @param headers       Request headers map
     * @param requestBody   Raw request body string, may be null
     * @return              Rendered response body
     */
    public String render(String template,
                         String method,
                         String path,
                         String queryParams,
                         Map<String, String> headers,
                         String requestBody) {

        if (template == null || !template.contains("{{")) {
            return template; // fast path — no placeholders
        }

        // Parse JSON body once (lazily — only if needed)
        JsonNode bodyJson = parseJson(requestBody);

        // Parse query string into map
        Map<String, String> params = parseQueryString(queryParams);

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder(template.length() + 64);

        while (matcher.find()) {
            String expr        = matcher.group(1).trim();
            String replacement = resolve(expr, method, path, params, headers, bodyJson);
            matcher.appendReplacement(result,
                    replacement != null
                            ? Matcher.quoteReplacement(replacement)
                            : matcher.group(0)); // leave unchanged if unresolvable
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // ---------------------------------------------------------------
    private String resolve(String expr,
                           String method,
                           String path,
                           Map<String, String> params,
                           Map<String, String> headers,
                           JsonNode bodyJson) {
        // ── {{request.method}} / {{request.path}} ──────────────────
        if (expr.equalsIgnoreCase("request.method")) return method;
        if (expr.equalsIgnoreCase("request.path"))   return path;

        // ── {{param.name}} ──────────────────────────────────────────
        if (expr.toLowerCase().startsWith("param.")) {
            String key = expr.substring(6);
            return params.get(key);
        }

        // ── {{header.Name}} ─────────────────────────────────────────
        if (expr.toLowerCase().startsWith("header.")) {
            String headerName = expr.substring(7);
            // Case-insensitive lookup
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(headerName))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }

        // ── {{fieldName}} or {{a.b.c}} — JSON body path ─────────────
        if (bodyJson != null) {
            return resolveJsonPath(bodyJson, expr);
        }

        return null;
    }

    /**
     * Traverse a dot-separated path through a JsonNode tree.
     * e.g. "user.address.city" on {"user":{"address":{"city":"Berlin"}}}
     * returns "Berlin".
     */
    private String resolveJsonPath(JsonNode root, String dotPath) {
        String[] parts = dotPath.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            if (current == null || !current.isObject()) return null;
            current = current.get(part);
        }
        if (current == null || current.isNull()) return null;
        // Return text value; for objects/arrays return compact JSON
        return current.isValueNode() ? current.asText() : current.toString();
    }

    private JsonNode parseJson(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            log.debug("Request body is not valid JSON — skipping JSON path resolution");
            return null;
        }
    }

    private Map<String, String> parseQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) return Map.of();
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (String pair : queryString.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                map.put(pair.substring(0, idx), pair.substring(idx + 1));
            } else if (!pair.isBlank()) {
                map.put(pair, "");
            }
        }
        return map;
    }
}
