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
public class KnownRemoteHostsExportDto {

    @Builder.Default
    private int version = 1;
    private String type;
    private String exportedAt;
    private String exportedBy;
    private List<HostDto> hosts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HostDto {
        private String address;
        private String displayName;
        private String description;
    }
}
