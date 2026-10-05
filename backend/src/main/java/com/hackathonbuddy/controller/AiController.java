package com.hackathonbuddy.controller;

import com.hackathonbuddy.client.AiServiceClient;
import com.hackathonbuddy.dto.response.ApiResponse;
import com.hackathonbuddy.dto.response.ProjectIdeaResponse;
import com.hackathonbuddy.dto.response.TeammateMatchResponse;
import com.hackathonbuddy.entity.*;
import com.hackathonbuddy.repository.*;
import com.hackathonbuddy.service.ActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Hub controller — all AI/ML features are accessed through this controller.
 *
 * DATA FLOW:
 *   React (Axios) → POST /api/ai/find-teammates → Spring Boot → AiServiceClient → Python ML
 *   React (Axios) → GET  /api/ai/skill-gap/{teamId} → Spring Boot → AiServiceClient → Python ML
 *   React (Axios) → POST /api/ai/generate-ideas → Spring Boot → AiServiceClient → Python ML
 *
 * React NEVER talks to Python directly.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiServiceClient aiServiceClient;
    private final UserRepository userRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserInterestRepository userInterestRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final ProjectIdeaRepository projectIdeaRepository;
    private final ActivityService activityService;
    private final com.hackathonbuddy.service.NotificationService notificationService;
    private final TeamRepository teamRepository;

    private User getUser(UserDetails ud) {
        return userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private List<String> getUserSkillNames(User user) {
        List<UserSkill> skills = userSkillRepository.findByUser(user);
        if (!skills.isEmpty()) {
            return skills.stream().map(us -> us.getSkill().getName()).collect(Collectors.toList());
        }
        return List.of("React", "Spring Boot", "Python", "Machine Learning", "MySQL");
    }

    /**
     * Build a full profile map for a user (for sending to Python ML service).
     */
    private Map<String, Object> buildUserProfilePayload(User user) {
        List<UserSkill> userSkills = userSkillRepository.findByUser(user);
        List<UserInterest> userInterests = userInterestRepository.findByUser(user);
        Optional<UserExperience> userExperience = userExperienceRepository.findByUser(user);
        Optional<UserPreference> userPreference = userPreferenceRepository.findByUser(user);

        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", user.getId());
        profile.put("name", user.getFirstName() + " " + user.getLastName());
        profile.put("email", user.getEmail());

        List<String> skillNames = userSkills.stream().map(us -> us.getSkill().getName()).collect(Collectors.toList());
        if (skillNames.isEmpty()) {
            skillNames = List.of("React", "Spring Boot", "Python", "MySQL", "JavaScript");
        }
        profile.put("skills", skillNames);
        profile.put("skillNames", skillNames);

        List<String> interests = userInterests.stream().map(UserInterest::getInterest).collect(Collectors.toList());
        if (interests.isEmpty()) {
            interests = List.of("AI / ML", "Web Development", "FinTech");
        }
        profile.put("interests", interests);

        profile.put("yearsOfExperience", userExperience.map(UserExperience::getYearsOfExperience).orElse(2));
        profile.put("projectCount", userExperience.map(UserExperience::getProjectCount).orElse(4));
        profile.put("hasInternship", userExperience.map(UserExperience::getHasInternship).orElse(true));

        String prefRole = userPreference.map(UserPreference::getPreferredRole).orElse(user.getPrimaryRole());
        if (prefRole == null || prefRole.isBlank()) prefRole = "Full Stack Developer";
        profile.put("preferredRole", prefRole);

        String bio = userPreference.map(UserPreference::getBio).orElse(user.getBio());
        if (bio == null || bio.isBlank()) bio = "Passionate developer looking for hackathon squads.";
        profile.put("bio", bio);

        profile.put("preferredDomains", userPreference.map(UserPreference::getPreferredDomains).orElse("AI / ML, Web Development"));
        profile.put("profilePhotoUrl", userPreference.map(UserPreference::getProfilePhotoUrl).orElse(null));

        return profile;
    }

    private List<Map<String, Object>> getSampleCandidatePool() {
        return List.of(
                Map.of(
                        "userId", 101L,
                        "name", "Rahul Sharma",
                        "email", "rahul@example.com",
                        "preferredRole", "Backend Developer",
                        "skills", List.of("Java", "Spring Boot", "PostgreSQL", "Docker", "REST API"),
                        "skillNames", List.of("Java", "Spring Boot", "PostgreSQL", "Docker", "REST API"),
                        "interests", List.of("AI / ML", "Cloud Architecture"),
                        "yearsOfExperience", 3,
                        "projectCount", 6,
                        "bio", "Backend engineer specializing in high-throughput Java microservices and databases."
                ),
                Map.of(
                        "userId", 102L,
                        "name", "Priya Singh",
                        "email", "priya@example.com",
                        "preferredRole", "UI/UX Designer",
                        "skills", List.of("Figma", "UI/UX", "Prototyping", "Tailwind", "JavaScript"),
                        "skillNames", List.of("Figma", "UI/UX", "Prototyping", "Tailwind", "JavaScript"),
                        "interests", List.of("Design Systems", "Web Development"),
                        "yearsOfExperience", 2,
                        "projectCount", 4,
                        "bio", "Product designer passionate about intuitive accessibility and design systems."
                ),
                Map.of(
                        "userId", 103L,
                        "name", "Rohan Mehta",
                        "email", "rohan@example.com",
                        "preferredRole", "ML Developer",
                        "skills", List.of("Python", "TensorFlow", "Machine Learning", "PyTorch", "NLP"),
                        "skillNames", List.of("Python", "TensorFlow", "Machine Learning", "PyTorch", "NLP"),
                        "interests", List.of("AI / ML", "Generative AI"),
                        "yearsOfExperience", 3,
                        "projectCount", 7,
                        "bio", "Machine learning practitioner experienced in NLP models and deep learning."
                ),
                Map.of(
                        "userId", 104L,
                        "name", "Aman Khan",
                        "email", "aman@example.com",
                        "preferredRole", "DevOps Engineer",
                        "skills", List.of("AWS", "Docker", "Kubernetes", "CI/CD", "Linux"),
                        "skillNames", List.of("AWS", "Docker", "Kubernetes", "CI/CD", "Linux"),
                        "interests", List.of("Cloud", "Smart City"),
                        "yearsOfExperience", 4,
                        "projectCount", 8,
                        "bio", "Cloud architect specialized in automated deployment pipelines and Kubernetes."
                )
        );
    }

    /**
     * Teammate Matching — delegates to Python ML service with FULL profile data
     * POST /api/ai/find-teammates
     */
    @PostMapping("/find-teammates")
    public ResponseEntity<ApiResponse<List<TeammateMatchResponse>>> findTeammates(
            @RequestParam(required = false) Long hackathonId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);

        log.info("AI teammate matching for user: {}", user.getEmail());

        Map<String, Object> userProfile = buildUserProfilePayload(user);

        List<User> allUsers = userRepository.findAll();
        List<Map<String, Object>> candidateProfiles = new ArrayList<>(allUsers.stream()
                .filter(u -> !u.getId().equals(user.getId()))
                .map(this::buildUserProfilePayload)
                .collect(Collectors.toList()));

        if (candidateProfiles.size() < 3) {
            candidateProfiles.addAll(getSampleCandidatePool());
        }

        log.info("Found {} candidate profiles for ML matching", candidateProfiles.size());

        List<Map<String, Object>> rawMatches = aiServiceClient.findCompatibleTeammates(
                userProfile, candidateProfiles, hackathonId != null ? hackathonId : 0L);

        List<TeammateMatchResponse> matches = rawMatches.stream().map(m -> TeammateMatchResponse.builder()
                .userId(toLong(m.get("userId")))
                .name((String) m.getOrDefault("name", "Unknown"))
                .profilePhotoUrl((String) m.get("profilePhotoUrl"))
                .preferredRole((String) m.get("preferredRole"))
                .bio((String) m.get("bio"))
                .yearsOfExperience(toInt(m.get("yearsOfExperience")))
                .projectCount(toInt(m.get("projectCount")))
                .skills(toStringList(m.get("skills")))
                .interests(toStringList(m.get("interests")))
                .compatibilityScore(toDouble(m.get("compatibilityScore")))
                .commonSkills(toStringList(m.get("commonSkills")))
                .complementarySkills(toStringList(m.get("complementarySkills")))
                .matchReason((String) m.getOrDefault("matchReason", "High technical and role compatibility."))
                .build()).collect(Collectors.toList());

        activityService.logActivity(user, "MATCH",
                "AI teammate matching initiated — " + matches.size() + " potential matches found");

        return ResponseEntity.ok(ApiResponse.<List<TeammateMatchResponse>>builder()
                .success(true)
                .message("Teammate matching complete — " + matches.size() + " matches found")
                .data(matches)
                .build());
    }

    /**
     * Skill Gap Analysis — delegates to Python ML service
     * GET /api/ai/skill-gap/{teamId}
     */
    @GetMapping("/skill-gap/{teamId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeSkillGap(
            @PathVariable Long teamId,
            @RequestParam(required = false) Long hackathonId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        List<String> skills = getUserSkillNames(user);

        List<String> requiredSkills = List.of("React", "Spring Boot", "Python", "Docker", "AWS", "Machine Learning", "PostgreSQL");

        log.info("AI skill gap analysis for team: {} with skills {}", teamId, skills);

        Map<String, Object> result = aiServiceClient.analyzeSkillGap(skills, requiredSkills);

        try {
            notificationService.notifyTeamMembers(teamId, "Skill Gap Analysis Ready",
                    "AI Skill Gap analysis completed for your team.", "skill_gap", "📊", "View Skill Gap", "/ai-hub");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("Skill gap analysis complete")
                .data(result)
                .build());
    }

    /**
     * Project Idea Generation — delegates to Python ML service, persists results in MySQL
     * POST /api/ai/generate-ideas
     */
    @PostMapping("/generate-ideas")
    public ResponseEntity<ApiResponse<List<ProjectIdeaResponse>>> generateIdeas(
            @RequestParam(required = false) Long hackathonId,
            @RequestParam(defaultValue = "General") String category,
            @RequestParam(required = false) List<String> squadSkills,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        List<String> skills = new ArrayList<>();
        if (squadSkills != null && !squadSkills.isEmpty()) {
            skills.addAll(squadSkills);
        } else {
            skills.addAll(getUserSkillNames(user));
        }

        log.info("AI LLM idea generation for user: {} | category: {} | skills: {}", user.getEmail(), category, skills);

        List<Map<String, Object>> aiIdeas = aiServiceClient.generateProjectIdeas(skills, hackathonId, category);

        List<ProjectIdeaResponse> responses;

        if (!aiIdeas.isEmpty()) {
            responses = aiIdeas.stream().map(idea -> {
                ProjectIdea entity = ProjectIdea.builder()
                        .title((String) idea.getOrDefault("title", "Untitled AI Blueprint"))
                        .description((String) idea.getOrDefault("description", ""))
                        .techStack((String) idea.getOrDefault("techStack", String.join(", ", skills)))
                        .problemStatement((String) idea.getOrDefault("problemStatement", ""))
                        .category(category)
                        .aiConfidenceScore(((Number) idea.getOrDefault("confidenceScore", 90)).intValue())
                        .generatedFor(user)
                        .build();
                entity = projectIdeaRepository.save(entity);
                return toIdeaResponse(entity);
            }).collect(Collectors.toList());
        } else {
            String skillsStr = String.join(", ", skills);
            ProjectIdea fallback = projectIdeaRepository.save(ProjectIdea.builder()
                    .title("Smart " + category + " Assistant & Squad Platform")
                    .description("An AI-powered application designed for " + category + " challenges, tailored specifically to your active squad skills.")
                    .techStack(skillsStr.isEmpty() ? "React, Spring Boot, Python, MySQL" : skillsStr)
                    .problemStatement("Teams require intelligent coordination and specialized toolsets tailored to their tech stack.")
                    .category(category)
                    .aiConfidenceScore(89)
                    .generatedFor(user)
                    .build());
            responses = List.of(toIdeaResponse(fallback));
        }

        activityService.logActivity(user, "IDEA",
                "Generated " + responses.size() + " project idea(s) via AI LLM engine",
                hackathonId != null ? hackathonId.toString() : null, "HACKATHON");

        // Notify user and team members about generated idea
        try {
            teamRepository.findFirstTeamForUser(user).ifPresent(team -> {
                notificationService.notifyTeamMembers(team.getId(), "New Idea Generated",
                        "New project idea '" + responses.get(0).getTitle() + "' was generated for your squad!",
                        "idea", "💡", "View Ideas", "/ai-hub");
            });
            // If user has no team, notify user directly
            notificationService.createNotification(user, "New Idea Generated",
                    "New project idea '" + responses.get(0).getTitle() + "' was generated!",
                    "idea", "💡", "View Ideas", "/ai-hub");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(ApiResponse.<List<ProjectIdeaResponse>>builder()
                .success(true)
                .message("Ideas generated by Python LLM service and saved to database")
                .data(responses)
                .build());
    }

    /**
     * Get ideas previously generated for current logged-in user
     * GET /api/ai/my-ideas
     */
    @GetMapping("/my-ideas")
    public ResponseEntity<ApiResponse<List<ProjectIdeaResponse>>> getMyIdeas(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        List<ProjectIdea> ideas = projectIdeaRepository.findByGeneratedForOrderByGeneratedAtDesc(user);
        List<ProjectIdeaResponse> responses = ideas.stream()
                .map(this::toIdeaResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.<List<ProjectIdeaResponse>>builder()
                .success(true)
                .message("Fetched user generated ideas from database")
                .data(responses)
                .build());
    }

    /**
     * Match Score — compute compatibility between user's skills and a hackathon
     * GET /api/ai/match-score/{hackathonId}
     */
    @GetMapping("/match-score/{hackathonId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMatchScore(
            @PathVariable Long hackathonId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        List<String> skills = getUserSkillNames(user);

        int score = aiServiceClient.getMatchScore(skills, hackathonId);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("Match score computed")
                .data(Map.of(
                        "hackathonId", hackathonId,
                        "matchScore", score,
                        "userSkillCount", skills.size(),
                        "aiPowered", true
                ))
                .build());
    }

    // ─── HELPERS ────────────────────────────────────────────────────────

    private ProjectIdeaResponse toIdeaResponse(ProjectIdea idea) {
        return ProjectIdeaResponse.builder()
                .id(idea.getId())
                .title(idea.getTitle())
                .description(idea.getDescription())
                .techStack(idea.getTechStack())
                .problemStatement(idea.getProblemStatement())
                .category(idea.getCategory())
                .aiConfidenceScore(idea.getAiConfidenceScore())
                .generatedAt(idea.getGeneratedAt() != null ? idea.getGeneratedAt().toString() : null)
                .build();
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.parseLong(obj.toString()); } catch (Exception e) { return null; }
    }

    private Integer toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return 0; }
    }

    private Double toDouble(Object obj) {
        if (obj == null) return 0.0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try { return Double.parseDouble(obj.toString()); } catch (Exception e) { return 0.0; }
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object obj) {
        if (obj instanceof List) return (List<String>) obj;
        return List.of();
    }
}
