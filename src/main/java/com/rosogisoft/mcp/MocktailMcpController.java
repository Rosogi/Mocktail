package com.rosogisoft.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rosogisoft.config.AppVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class MocktailMcpController {

    private static final String PROTOCOL_VERSION = "2025-06-18";

    private final ObjectMapper objectMapper;
    private final AppVersion appVersion;
    private final MocktailMcpTools tools;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> handle(@RequestBody JsonNode body) {
        if (body == null) {
            return ResponseEntity.badRequest().body(error(NullNode.getInstance(), -32600, "Invalid Request"));
        }

        if (body.isArray()) {
            ArrayNode responses = objectMapper.createArrayNode();
            body.forEach(request -> {
                JsonNode response = handleOne(request);
                if (response != null) {
                    responses.add(response);
                }
            });
            return responses.isEmpty()
                    ? ResponseEntity.status(HttpStatus.ACCEPTED).build()
                    : ResponseEntity.ok(responses);
        }

        JsonNode response = handleOne(body);
        return response == null
                ? ResponseEntity.status(HttpStatus.ACCEPTED).build()
                : ResponseEntity.ok(response);
    }

    private JsonNode handleOne(JsonNode request) {
        JsonNode id = request.get("id") != null ? request.get("id") : NullNode.getInstance();
        String method = request.path("method").asText(null);
        boolean notification = request.get("id") == null;

        if (method == null || method.isBlank()) {
            return error(id, -32600, "Invalid Request");
        }

        if (method.startsWith("notifications/")) {
            return null;
        }

        try {
            return switch (method) {
                case "initialize" -> result(id, initializeResult());
                case "ping" -> result(id, objectMapper.createObjectNode());
                case "tools/list" -> result(id, tools.listTools());
                case "tools/call" -> result(id, tools.callTool(request.path("params")));
                default -> notification ? null : error(id, -32601, "Method not found: " + method);
            };
        } catch (AccessDeniedException e) {
            return error(id, -32001, e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(id, -32602, e.getMessage());
        } catch (Exception e) {
            return error(id, -32603, "Internal error: " + e.getMessage());
        }
    }

    private ObjectNode initializeResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "mocktail");
        serverInfo.put("version", appVersion.getVersion());
        result.put("instructions", "Mocktail MCP exposes user-scoped request logs and read-only mock definitions.");
        return result;
    }

    private ObjectNode result(JsonNode id, JsonNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return response;
    }
}
