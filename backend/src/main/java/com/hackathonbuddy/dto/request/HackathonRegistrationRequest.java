package com.hackathonbuddy.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HackathonRegistrationRequest {
    private String hackathonTitle;
    private String hackathonCategory;
    private String participationType; // INDIVIDUAL, TEAM
    private String teamName;
    private String role;
    private String experienceLevel;
    private String githubUrl;
    private String motivation;
    private Boolean agreeTerms;
}
