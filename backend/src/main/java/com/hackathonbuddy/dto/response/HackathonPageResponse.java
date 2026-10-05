package com.hackathonbuddy.dto.response;

import com.hackathonbuddy.model.Hackathon;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HackathonPageResponse {
    private List<Hackathon> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private Map<String, ProviderMetaInfo> providers;
}
