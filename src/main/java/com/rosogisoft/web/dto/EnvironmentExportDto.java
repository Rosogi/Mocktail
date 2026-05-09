package com.rosogisoft.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnvironmentExportDto {

    private int version = 1;
    private String type;
    private String exportedAt;
    private String exportedBy;
    private EnvironmentPackageDto environment;
    private List<VariableDto> globals;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EnvironmentPackageDto {
        private String name;
        private String description;
        private List<VariableDto> variables;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VariableDto {
        private String key;
        private String value;
        private String description;
        private boolean hidden;
    }
}
