package com.hackathonbuddy.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * HTTP client that proxies all AI/ML requests from Spring Boot → Python ML Service.
 *
 * Architecture:
 *   React (port 5173)
 *       │  Axios
 *       ▼
 *   Spring Boot (port 8081)   ← THIS CLIENT LIVES HERE
 *       │  RestTemplate
 *       ▼
 *   Python ML Service (port 8000)
 *       ├── /api/ml/match-teammates   (full profile matching)
 *       ├── /api/ml/analyze-skill-gap
 *       ├── /api/ml/generate-idea
 *       └── /api/ml/match-score
 *
 * React NEVER calls Python directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiServiceClient {

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate;

    // ---------- Teammate Matching (Full Profile) ----------

    /**
     * Ask Python ML service to find compatible teammates using full profile data.
     *
     * @param userProfile      Full profile of the requesting user (skills, interests, experience, role, domains)
     * @param candidateProfiles List of all other users' profiles
     * @param hackathonId      The hackathon context (0 for general)
     * @return List of matched user profiles with compatibility scores
     */
    public List<Map<String, Object>> findCompatibleTeammates(
            Map<String, Object> userProfile,
            List<Map<String, Object>> candidateProfiles,
            Long hackathonId) {
        try {
            String url = aiServiceUrl + "/api/ml/match-teammates";
            Map<String, Object> payload = Map.of(
                    "user", userProfile != null ? userProfile : Map.of(),
                    "candidates", candidateProfiles != null ? candidateProfiles : List.of(),
                    "hackathonId", hackathonId != null ? hackathonId : 0L
            );
            ResponseEntity<Map> response = restTemplate.postForEntity(url, buildRequest(payload), Map.class);
            if (response.getBody() != null && response.getBody().containsKey("matches")) {
                return (List<Map<String, Object>>) response.getBody().get("matches");
            }
            log.info("AI teammate match response: {}", response.getBody());
            return List.of();
        } catch (RestClientException e) {
            log.warn("AI service unavailable for teammate matching: {}", e.getMessage());
            return List.of(); // Graceful fallback
        }
    }

    /**
     * Skill-only matching fallback.
     */
    public List<Map<String, Object>> findCompatibleTeammates(List<String> userSkills, Long hackathonId) {
        try {
            String url = aiServiceUrl + "/api/ml/match-teammates";
            Map<String, Object> payload = Map.of(
                    "userSkills", userSkills != null ? userSkills : List.of(),
                    "hackathonId", hackathonId != null ? hackathonId : 0L
            );
            ResponseEntity<Map> response = restTemplate.postForEntity(url, buildRequest(payload), Map.class);
            if (response.getBody() != null && response.getBody().containsKey("matches")) {
                return (List<Map<String, Object>>) response.getBody().get("matches");
            }
            return List.of();
        } catch (RestClientException e) {
            log.warn("AI service unavailable for teammate matching: {}", e.getMessage());
            return List.of();
        }
    }

    // ---------- Skill Gap Analysis ----------

    /**
     * Ask Python ML service to compute team skill gap.
     *
     * @param teamSkills     Combined skills of all current team members
     * @param hackathonTags  Required skills/tags for the hackathon
     * @return Skill gap analysis result
     */
    public Map<String, Object> analyzeSkillGap(List<String> teamSkills, List<String> hackathonTags) {
        try {
            String url = aiServiceUrl + "/api/ml/analyze-skill-gap";
            Map<String, Object> payload = Map.of(
                    "teamSkills", teamSkills != null ? teamSkills : List.of(),
                    "requiredSkills", hackathonTags != null ? hackathonTags : List.of()
            );
            ResponseEntity<Map> response = restTemplate.postForEntity(url, buildRequest(payload), Map.class);
            log.info("AI skill gap analysis completed successfully");
            if (response.getBody() != null) {
                return (Map<String, Object>) response.getBody();
            }
        } catch (RestClientException e) {
            log.warn("AI service unavailable for skill gap analysis: {}", e.getMessage());
        }
        return Map.of(
                "coveragePercent", 82,
                "missingSkills", List.of("Docker", "AWS", "Kubernetes"),
                "fallback", true
        );
    }

    // ---------- Project Idea Generation ----------

    /**
     * Ask Python ML service to generate project ideas.
     *
     * @param userSkills  Current user's skills
     * @param hackathonId The hackathon to generate ideas for
     * @param category    Hackathon category (AI/ML, FinTech, etc.)
     * @return List of generated project ideas
     */
    public List<Map<String, Object>> generateProjectIdeas(List<String> userSkills, Long hackathonId, String category) {
        try {
            String url = aiServiceUrl + "/api/ml/generate-ideas";
            Map<String, Object> payload = Map.of(
                    "userSkills", userSkills != null ? userSkills : List.of(),
                    "hackathonId", hackathonId != null ? hackathonId : 0L,
                    "category", category != null ? category : "General"
            );
            ResponseEntity<Map> response = restTemplate.postForEntity(url, buildRequest(payload), Map.class);
            if (response.getBody() != null && response.getBody().containsKey("ideas")) {
                List<Map<String, Object>> ideas = (List<Map<String, Object>>) response.getBody().get("ideas");
                log.info("AI generated {} project ideas", ideas.size());
                return ideas;
            }
        } catch (RestClientException e) {
            log.warn("AI service unavailable for idea generation: {}", e.getMessage());
        }
        return List.of();
    }

    // ---------- Hackathon Recommendation Score ----------

    /**
     * Ask Python ML service to compute match score for a user-hackathon pair.
     *
     * @param userSkills   User's skill list
     * @param hackathonId  Hackathon to score
     * @return Match score 0–100
     */
    public int getMatchScore(List<String> userSkills, Long hackathonId) {
        try {
            String url = aiServiceUrl + "/api/ml/match-score";
            Map<String, Object> payload = Map.of(
                    "userSkills", userSkills != null ? userSkills : List.of(),
                    "hackathonId", hackathonId != null ? hackathonId : 0L
            );
            ResponseEntity<Map> response = restTemplate.postForEntity(url, buildRequest(payload), Map.class);
            if (response.getBody() != null && response.getBody().containsKey("score")) {
                return ((Number) response.getBody().get("score")).intValue();
            }
        } catch (RestClientException e) {
            log.debug("AI match score unavailable, using default: {}", e.getMessage());
        }
        return 75; // Default fallback score
    }

    // ---------- Helper ----------

    private HttpEntity<Map<String, Object>> buildRequest(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
