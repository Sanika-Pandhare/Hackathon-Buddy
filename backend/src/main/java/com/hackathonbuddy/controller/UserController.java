package com.hackathonbuddy.controller;

import com.hackathonbuddy.dto.response.ApiResponse;
import com.hackathonbuddy.dto.response.TeammateMatchResponse;
import com.hackathonbuddy.dto.response.UserResponse;
import com.hackathonbuddy.entity.*;
import com.hackathonbuddy.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserExperienceRepository userExperienceRepository;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        UserResponse response = UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .build();
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder()
                .success(true).message("Profile retrieved").data(response).build());
    }

    @GetMapping("/candidates")
    public ResponseEntity<ApiResponse<List<TeammateMatchResponse>>> getCandidates(
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = userDetails != null ? userRepository.findByEmail(userDetails.getUsername()).orElse(null) : null;
        List<User> users = userRepository.findAll();

        List<TeammateMatchResponse> candidates = users.stream()
                .filter(u -> currentUser == null || !u.getId().equals(currentUser.getId()))
                .map(u -> {
                    List<String> skills = userSkillRepository.findByUser(u)
                            .stream().map(us -> us.getSkill().getName()).collect(Collectors.toList());
                    if (skills.isEmpty()) {
                        skills = List.of("React", "Spring Boot", "Python", "MySQL");
                    }
                    Optional<UserPreference> pref = userPreferenceRepository.findByUser(u);
                    Optional<UserExperience> exp = userExperienceRepository.findByUser(u);

                    String role = pref.map(UserPreference::getPreferredRole).orElse(u.getPrimaryRole());
                    if (role == null || role.isBlank()) role = "Full Stack Developer";

                    String bio = pref.map(UserPreference::getBio).orElse(u.getBio());
                    if (bio == null || bio.isBlank()) bio = "Passionate student developer.";

                    String name = (u.getLastName() != null && !u.getLastName().isBlank())
                            ? (u.getFirstName() != null ? u.getFirstName() + " " + u.getLastName() : u.getLastName())
                            : (u.getFirstName() != null ? u.getFirstName() : u.getEmail());

                    return TeammateMatchResponse.builder()
                            .userId(u.getId())
                            .name(name)
                            .preferredRole(role)
                            .bio(bio)
                            .yearsOfExperience(exp.map(UserExperience::getYearsOfExperience).orElse(2))
                            .projectCount(exp.map(UserExperience::getProjectCount).orElse(3))
                            .skills(skills)
                            .compatibilityScore(92.0)
                            .matchReason("Registered platform candidate")
                            .build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.<List<TeammateMatchResponse>>builder()
                .success(true)
                .message("Candidates retrieved")
                .data(candidates)
                .build());
    }
}
