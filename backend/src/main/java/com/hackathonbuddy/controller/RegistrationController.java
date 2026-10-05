package com.hackathonbuddy.controller;

import com.hackathonbuddy.dto.request.HackathonRegistrationRequest;
import com.hackathonbuddy.dto.response.ApiResponse;
import com.hackathonbuddy.dto.response.HackathonResponse;
import com.hackathonbuddy.entity.Hackathon;
import com.hackathonbuddy.entity.Registration;
import com.hackathonbuddy.entity.User;
import com.hackathonbuddy.repository.HackathonRepository;
import com.hackathonbuddy.repository.RegistrationRepository;
import com.hackathonbuddy.repository.UserRepository;
import com.hackathonbuddy.service.ActivityService;
import com.hackathonbuddy.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationRepository registrationRepository;
    private final HackathonRepository hackathonRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final NotificationService notificationService;

    private User getUser(UserDetails ud) {
        return userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Hackathon resolveHackathon(String hackathonId, HackathonRegistrationRequest request) {
        // Try numeric ID first
        try {
            Long numericId = Long.parseLong(hackathonId);
            Optional<Hackathon> existing = hackathonRepository.findById(numericId);
            if (existing.isPresent()) {
                return existing.get();
            }
        } catch (NumberFormatException ignored) {
            // String ID (e.g. devpost-123)
        }

        // Try title if provided in request
        String title = (request != null && request.getHackathonTitle() != null && !request.getHackathonTitle().isBlank())
                ? request.getHackathonTitle()
                : "Hackathon Challenge #" + hackathonId;

        return hackathonRepository.findAll().stream()
                .filter(h -> h.getTitle() != null && h.getTitle().equalsIgnoreCase(title))
                .findFirst()
                .orElseGet(() -> {
                    Hackathon newHackathon = Hackathon.builder()
                            .title(title)
                            .category(request != null && request.getHackathonCategory() != null ? request.getHackathonCategory() : "General")
                            .description("Registered challenge via portal")
                            .organizer("External Portal / HackathonBuddy")
                            .mode("ONLINE")
                            .status("Open")
                            .isActive(true)
                            .participantCount(1)
                            .build();
                    return hackathonRepository.save(newHackathon);
                });
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<HackathonResponse>>> getMyRegistrations(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        List<HackathonResponse> registrations = registrationRepository.findByUser(user)
                .stream()
                .filter(r -> !"WITHDRAWN".equalsIgnoreCase(r.getStatus()))
                .map(r -> HackathonResponse.builder()
                        .id(r.getHackathon().getId())
                        .title(r.getHackathon().getTitle())
                        .category(r.getHackathon().getCategory())
                        .prizePool(r.getHackathon().getPrizePool())
                        .isRegistered(true)
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.<List<HackathonResponse>>builder()
                .success(true).message("Registrations retrieved").data(registrations).build());
    }

    @PostMapping("/{hackathonId}")
    public ResponseEntity<ApiResponse<String>> register(
            @PathVariable String hackathonId,
            @RequestBody(required = false) HackathonRegistrationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        Hackathon hackathon = resolveHackathon(hackathonId, request);

        Optional<Registration> existingReg = registrationRepository.findByUserAndHackathon(user, hackathon);
        if (existingReg.isPresent() && "REGISTERED".equalsIgnoreCase(existingReg.get().getStatus())) {
            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .success(true).message("Already registered for this hackathon").data("REGISTERED").build());
        }

        Registration registration;
        if (existingReg.isPresent()) {
            registration = existingReg.get();
            registration.setStatus("REGISTERED");
        } else {
            registration = Registration.builder()
                    .user(user)
                    .hackathon(hackathon)
                    .status("REGISTERED")
                    .build();
        }

        if (request != null) {
            registration.setTeamName(request.getTeamName());
            registration.setRole(request.getRole());
            registration.setMotivation(request.getMotivation());
        }

        registrationRepository.save(registration);

        // Update participant count
        if (hackathon.getParticipantCount() == null) {
            hackathon.setParticipantCount(1);
        } else {
            hackathon.setParticipantCount(hackathon.getParticipantCount() + 1);
        }
        hackathonRepository.save(hackathon);

        // Log activity and create notification
        activityService.logActivity(user, "REGISTRATION",
                "You registered for " + hackathon.getTitle(),
                hackathon.getId().toString(), "HACKATHON");
        notificationService.createNotification(user,
                "Registration Confirmed",
                "You've successfully registered for " + hackathon.getTitle() + "!",
                "SUCCESS");

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true).message("Successfully registered").data("REGISTERED").build());
    }

    @DeleteMapping("/{hackathonId}")
    public ResponseEntity<ApiResponse<String>> withdraw(
            @PathVariable String hackathonId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        
        // Try finding existing registration for numeric or matching hackathon
        try {
            Long numericId = Long.parseLong(hackathonId);
            hackathonRepository.findById(numericId).ifPresent(hackathon -> {
                registrationRepository.findByUserAndHackathon(user, hackathon)
                        .ifPresent(r -> {
                            r.setStatus("WITHDRAWN");
                            registrationRepository.save(r);
                        });
            });
        } catch (NumberFormatException ignored) {}

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true).message("Registration status updated").build());
    }
}
