package com.rosogisoft.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rosogisoft.domain.LlmAccessToken;
import com.rosogisoft.domain.MockDefinition;
import com.rosogisoft.domain.RequestLog;
import com.rosogisoft.domain.User;
import com.rosogisoft.security.McpPermissionGuard;
import com.rosogisoft.service.MockService;
import com.rosogisoft.service.RequestLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MocktailMcpTools {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final ObjectMapper objectMapper;
    private final MockService mockService;
    private final RequestLogService requestLogService;

    public ObjectNode listTools() {
        LlmAccessToken token = McpPermissionGuard.currentToken();
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");

        if (token.getMocksAccess().canRead()) {
            addTool(tools, "mocktail_list_mocks",
                    "List mock definitions owned by the authenticated Mocktail user.",
                    listMocksSchema());
            addTool(tools, "mocktail_get_mock",
                    "Get one mock definition owned by the authenticated Mocktail user.",
                    idSchema("Mock id."));
        }

        if (token.getRequestLogsAccess().canRead()) {
            addTool(tools, "mocktail_recent_request_logs",
                    "List recent request logs for the authenticated Mocktail user.",
                    limitSchema());
            addTool(tools, "mocktail_get_request_log",
                    "Get one request log for the authenticated Mocktail user.",
                    idSchema("Request log id."));
        }

        if (token.getRequestLogsAccess().canWrite()) {
            addTool(tools, "mocktail_delete_request_log",
                    "Delete one request log. Requires confirm=true.",
                    confirmIdSchema("Request log id."));
            addTool(tools, "mocktail_clear_request_logs",
                    "Clear all request logs for the authenticated Mocktail user. Requires confirm=true.",
                    confirmOnlySchema());
        }

        return result;
    }

    public ObjectNode callTool(JsonNode params) throws JsonProcessingException {
        String name = params.path("name").asText(null);
        JsonNode args = params.path("arguments");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool name is required.");
        }

        Object payload = switch (name) {
            case "mocktail_list_mocks" -> listMocks(args);
            case "mocktail_get_mock" -> getMock(args);
            case "mocktail_recent_request_logs" -> recentRequestLogs(args);
            case "mocktail_get_request_log" -> getRequestLog(args);
            case "mocktail_delete_request_log" -> deleteRequestLog(args);
            case "mocktail_clear_request_logs" -> clearRequestLogs(args);
            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };

        return toolResult(payload);
    }

    private Object listMocks(JsonNode args) {
        LlmAccessToken token = McpPermissionGuard.currentToken();
        McpPermissionGuard.requireRead(token.getMocksAccess(), "mocks");
        User user = token.getOwner();
        boolean activeOnly = booleanArg(args, "activeOnly", false);
        int limit = intArg(args, "limit", DEFAULT_LIMIT, 1, 500);

        List<Map<String, Object>> mocks = mockService.findAllForUser(user).stream()
                .filter(mock -> !activeOnly || mock.isActive())
                .limit(limit)
                .map(this::mockSummary)
                .toList();
        return Map.of("mocks", mocks, "count", mocks.size());
    }

    private Object getMock(JsonNode args) {
        LlmAccessToken token = McpPermissionGuard.currentToken();
        McpPermissionGuard.requireRead(token.getMocksAccess(), "mocks");
        long id = longArg(args, "id");
        return mockService.findByIdForUser(id, token.getOwner())
                .map(this::mockDetail)
                .orElseThrow(() -> new IllegalArgumentException("Mock not found: " + id));
    }

    private Object recentRequestLogs(JsonNode args) {
        LlmAccessToken token = McpPermissionGuard.currentToken();
        McpPermissionGuard.requireRead(token.getRequestLogsAccess(), "request logs");
        int limit = intArg(args, "limit", DEFAULT_LIMIT, 1, MAX_LIMIT);
        List<Map<String, Object>> logs = requestLogService.findRecentForUser(token.getOwner()).stream()
                .limit(limit)
                .map(this::requestLogSummary)
                .toList();
        return Map.of("logs", logs, "count", logs.size());
    }

    private Object getRequestLog(JsonNode args) {
        LlmAccessToken token = McpPermissionGuard.currentToken();
        McpPermissionGuard.requireRead(token.getRequestLogsAccess(), "request logs");
        long id = longArg(args, "id");
        return requestLogService.findByIdForUser(id, token.getOwner())
                .map(this::requestLogDetail)
                .orElseThrow(() -> new IllegalArgumentException("Request log not found: " + id));
    }

    private Object deleteRequestLog(JsonNode args) {
        LlmAccessToken token = McpPermissionGuard.currentToken();
        McpPermissionGuard.requireWrite(token.getRequestLogsAccess(), "request logs");
        requireConfirm(args);
        long id = longArg(args, "id");
        boolean deleted = requestLogService.deleteForUser(id, token.getOwner());
        return Map.of("deleted", deleted, "id", id);
    }

    private Object clearRequestLogs(JsonNode args) {
        LlmAccessToken token = McpPermissionGuard.currentToken();
        McpPermissionGuard.requireWrite(token.getRequestLogsAccess(), "request logs");
        requireConfirm(args);
        long count = requestLogService.countForUser(token.getOwner());
        requestLogService.clearForUser(token.getOwner());
        return Map.of("cleared", count);
    }

    private Map<String, Object> mockSummary(MockDefinition mock) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", mock.getId());
        map.put("name", mock.getName());
        map.put("httpMethod", mock.getHttpMethod());
        map.put("pathPattern", mock.getPathPattern());
        map.put("requestMatchMode", mock.getRequestMatchMode());
        map.put("responseStatus", mock.getResponseStatus());
        map.put("responseContentType", mock.getResponseContentType());
        map.put("priority", mock.getPriority());
        map.put("active", mock.isActive());
        if (mock.getCollection() != null) {
            map.put("collection", Map.of(
                    "id", mock.getCollection().getId(),
                    "name", mock.getCollection().getName(),
                    "readOnly", mock.getCollection().isReadOnly()
            ));
        }
        return map;
    }

    private Map<String, Object> mockDetail(MockDefinition mock) {
        Map<String, Object> map = mockSummary(mock);
        map.put("requestBodyContains", mock.getRequestBodyContains());
        map.put("requestMatchGroups", mock.getRequestMatchGroups());
        map.put("responseBody", mock.getResponseBody());
        map.put("responseHeaders", mock.getResponseHeaders());
        return map;
    }

    private Map<String, Object> requestLogSummary(RequestLog log) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", log.getId());
        map.put("timestamp", instant(log.getTimestamp()));
        map.put("method", log.getMethod());
        map.put("path", log.getPath());
        map.put("queryParams", log.getQueryParams());
        map.put("contentType", log.getContentType());
        map.put("responseStatus", log.getResponseStatus());
        map.put("remoteAddr", log.getRemoteAddr());
        if (log.getMatchedMock() != null) {
            map.put("matchedMock", Map.of(
                    "id", log.getMatchedMock().getId(),
                    "name", log.getMatchedMock().getName()
            ));
        }
        return map;
    }

    private Map<String, Object> requestLogDetail(RequestLog log) {
        Map<String, Object> map = requestLogSummary(log);
        map.put("requestHeaders", redactHeaders(log.getRequestHeaders()));
        map.put("requestBody", log.getRequestBody());
        map.put("responseBody", log.getResponseBody());
        return map;
    }

    private Map<String, String> redactHeaders(Map<String, String> headers) {
        Map<String, String> redacted = new LinkedHashMap<>();
        if (headers == null) {
            return redacted;
        }
        headers.forEach((key, value) -> {
            String normalized = key.toLowerCase(Locale.ROOT);
            if (normalized.equals("authorization") ||
                    normalized.equals("proxy-authorization") ||
                    normalized.equals("cookie") ||
                    normalized.equals("set-cookie")) {
                redacted.put(key, "[redacted]");
            } else {
                redacted.put(key, value);
            }
        });
        return redacted;
    }

    private ObjectNode toolResult(Object payload) throws JsonProcessingException {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode text = content.addObject();
        text.put("type", "text");
        text.put("text", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
        result.put("isError", false);
        return result;
    }

    private void addTool(ArrayNode tools, String name, String description, ObjectNode inputSchema) {
        ObjectNode tool = tools.addObject();
        tool.put("name", name);
        tool.put("description", description);
        tool.set("inputSchema", inputSchema);
    }

    private ObjectNode listMocksSchema() {
        ObjectNode schema = objectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.set("activeOnly", booleanSchema("Only return active mocks."));
        properties.set("limit", integerSchema("Maximum number of mocks to return.", 1, 500));
        return schema;
    }

    private ObjectNode limitSchema() {
        ObjectNode schema = objectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.set("limit", integerSchema("Maximum number of request logs to return.", 1, MAX_LIMIT));
        return schema;
    }

    private ObjectNode idSchema(String description) {
        ObjectNode schema = objectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.set("id", integerSchema(description, 1, Long.MAX_VALUE));
        schema.putArray("required").add("id");
        return schema;
    }

    private ObjectNode confirmIdSchema(String idDescription) {
        ObjectNode schema = idSchema(idDescription);
        ((ObjectNode) schema.get("properties"))
                .set("confirm", booleanSchema("Must be true to perform deletion."));
        ((ArrayNode) schema.get("required")).add("confirm");
        return schema;
    }

    private ObjectNode confirmOnlySchema() {
        ObjectNode schema = objectSchema();
        ObjectNode properties = schema.putObject("properties");
        properties.set("confirm", booleanSchema("Must be true to clear all request logs."));
        schema.putArray("required").add("confirm");
        return schema;
    }

    private ObjectNode objectSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode integerSchema(String description, long minimum, long maximum) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "integer");
        schema.put("description", description);
        schema.put("minimum", minimum);
        schema.put("maximum", maximum);
        return schema;
    }

    private ObjectNode booleanSchema(String description) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "boolean");
        schema.put("description", description);
        return schema;
    }

    private int intArg(JsonNode args, String name, int defaultValue, int min, int max) {
        if (args == null || args.get(name) == null || args.get(name).isNull()) {
            return defaultValue;
        }
        int value = args.get(name).asInt(defaultValue);
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ".");
        }
        return value;
    }

    private long longArg(JsonNode args, String name) {
        if (args == null || args.get(name) == null || args.get(name).isNull()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        JsonNode value = args.get(name);
        if (value.isIntegralNumber()) {
            return value.asLong();
        }
        if (value.isTextual()) {
            try {
                return Long.parseLong(value.asText());
            } catch (NumberFormatException ignored) {
                // handled below
            }
        }
        throw new IllegalArgumentException(name + " must be an integer.");
    }

    private boolean booleanArg(JsonNode args, String name, boolean defaultValue) {
        if (args == null || args.get(name) == null || args.get(name).isNull()) {
            return defaultValue;
        }
        return args.get(name).asBoolean(defaultValue);
    }

    private void requireConfirm(JsonNode args) {
        if (!booleanArg(args, "confirm", false)) {
            throw new IllegalArgumentException("confirm=true is required for this operation.");
        }
    }

    private String instant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }
}
