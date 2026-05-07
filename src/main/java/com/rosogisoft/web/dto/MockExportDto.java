package com.rosogisoft.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Root object for mock export / import files.
 *
 * File structure:
 * {
 *   "version": 1,
 *   "exportedAt": "2024-...",
 *   "collections": [
 *     {
 *       "name": "My collection",
 *       "description": "...",
 *       "mocks": [ { ... }, { ... } ]
 *     }
 *   ],
 *   "mocks": [          <-- mocks NOT belonging to any collection
 *     { ... }
 *   ]
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MockExportDto {

    private int    version     = 1;
    private String exportedAt;
    private String exportedBy;

    private List<CollectionDto> collections;
    private List<MockDto>       mocks;   // uncollected mocks

    // ── Collection ────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CollectionDto {
        private String        name;
        private String        description;
        private List<MockDto> mocks;
    }

    // ── Single mock ───────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MockDto {
        private String              name;
        private String              httpMethod;
        private String              pathPattern;
        private String              requestBodyContains;
        private String              requestMatchMode;
        private String              requestMatchGroups;
        private int                 responseStatus;
        private String              responseBody;
        private String              responseContentType;
        private Map<String, String> responseHeaders;
        private int                 priority;
        private boolean             active;
    }
}
