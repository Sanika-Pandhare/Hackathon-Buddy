package com.hackathonbuddy.controller;

import com.hackathonbuddy.dto.request.CreateHackathonRequest;
import com.hackathonbuddy.dto.response.ApiResponse;
import com.hackathonbuddy.dto.response.HackathonPageResponse;
import com.hackathonbuddy.dto.response.HackathonResponse;
import com.hackathonbuddy.entity.User;
import com.hackathonbuddy.model.Hackathon;
import com.hackathonbuddy.repository.UserRepository;
import com.hackathonbuddy.service.HackathonAggregationService;
import com.hackathonbuddy.service.HackathonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hackathons")
@RequiredArgsConstructor
@Tag(name = "Hackathons", description = "Hackathon aggregation, search, and management API")
public class HackathonController {

    private final HackathonService hackathonService;
    private final HackathonAggregationService aggregationService;
    private final UserRepository userRepository;
    private final com.hackathonbuddy.service.NotificationService notificationService;

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) return null;
        return userRepository.findByEmail(userDetails.getUsername()).orElse(null);
    }

    @GetMapping
    @Operation(summary = "Get all hackathons (aggregated or database)", description = "Fetches hackathons from external portals (Devpost, Devfolio, HackerEarth, Unstop) or internal database based on filter parameters.")
    public ResponseEntity<ApiResponse<?>> getAllHackathons(
            @Parameter(description = "Platform filter: devpost, devfolio, hackerearth, unstop") @RequestParam(required = false) List<String> platform,
            @Parameter(description = "Status filter: open, upcoming") @RequestParam(required = false) String status,
            @Parameter(description = "Case-insensitive search in title, organizer, or tags") @RequestParam(required = false) String search,
            @Parameter(description = "Mode filter: Online, In-Person") @RequestParam(required = false) String mode,
            @Parameter(description = "Featured filter: true, false") @RequestParam(required = false) Boolean featured,
            @Parameter(description = "Sort field: deadline, title, platform") @RequestParam(required = false, defaultValue = "deadline") String sort,
            @Parameter(description = "Sort direction: asc, desc") @RequestParam(required = false, defaultValue = "asc") String direction,
            @Parameter(description = "0-indexed page number") @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (platform != null || status != null || search != null || mode != null || featured != null) {
            HackathonPageResponse pageResponse = aggregationService.fetchAndAggregate(
                    platform, status, search, mode, featured, sort, direction, page, size);
            return ResponseEntity.ok(ApiResponse.success("Aggregated hackathons retrieved", pageResponse));
        }

        User user = getCurrentUser(userDetails);
        List<HackathonResponse> hackathons = hackathonService.getAllHackathons(user);
        return ResponseEntity.ok(ApiResponse.<List<HackathonResponse>>builder()
                .success(true).message("Hackathons retrieved").data(hackathons).build());
    }

    @GetMapping("/external")
    @Operation(summary = "Get aggregated external portal hackathons", description = "Aggregates hackathons directly from Devpost, Devfolio, HackerEarth, and Unstop.")
    public ResponseEntity<ApiResponse<HackathonPageResponse>> getExternalHackathons(
            @Parameter(description = "Platform filter: devpost, devfolio, hackerearth, unstop") @RequestParam(required = false) List<String> platform,
            @Parameter(description = "Status filter: open, upcoming") @RequestParam(required = false) String status,
            @Parameter(description = "Case-insensitive search in title, organizer, or tags") @RequestParam(required = false) String search,
            @Parameter(description = "Mode filter: Online, In-Person") @RequestParam(required = false) String mode,
            @Parameter(description = "Featured filter: true, false") @RequestParam(required = false) Boolean featured,
            @Parameter(description = "Sort field: deadline, title, platform") @RequestParam(required = false, defaultValue = "deadline") String sort,
            @Parameter(description = "Sort direction: asc, desc") @RequestParam(required = false, defaultValue = "asc") String direction,
            @Parameter(description = "0-indexed page number") @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "20") int size) {

        HackathonPageResponse pageResponse = aggregationService.fetchAndAggregate(
                platform, status, search, mode, featured, sort, direction, page, size);
        return ResponseEntity.ok(ApiResponse.success("External hackathons retrieved", pageResponse));
    }

    @GetMapping("/recommended")
    @Operation(summary = "Get recommended hackathons")
    public ResponseEntity<ApiResponse<List<HackathonResponse>>> getRecommended(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        List<HackathonResponse> hackathons = hackathonService.getRecommendedHackathons(user);
        return ResponseEntity.ok(ApiResponse.<List<HackathonResponse>>builder()
                .success(true).message("Recommended hackathons retrieved").data(hackathons).build());
    }

    @GetMapping("/upcoming")
    @Operation(summary = "Get upcoming hackathons")
    public ResponseEntity<ApiResponse<List<HackathonResponse>>> getUpcoming(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        List<HackathonResponse> hackathons = hackathonService.getUpcomingHackathons(user);
        return ResponseEntity.ok(ApiResponse.<List<HackathonResponse>>builder()
                .success(true).message("Upcoming hackathons retrieved").data(hackathons).build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get hackathon by ID", description = "Retrieves an aggregated external hackathon by string ID (e.g., devpost-123) or internal database hackathon by numeric ID.")
    public ResponseEntity<ApiResponse<?>> getById(
            @Parameter(description = "Hackathon ID (numeric DB ID or string external ID)") @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (id.startsWith("devpost-") || id.startsWith("devfolio-") || id.startsWith("hackerearth-") || id.startsWith("unstop-")) {
            Hackathon externalHackathon = aggregationService.findById(id).orElse(null);
            if (externalHackathon != null) {
                return ResponseEntity.ok(ApiResponse.success("External hackathon retrieved", externalHackathon));
            }
            return ResponseEntity.status(404).body(ApiResponse.error("Hackathon not found with id: " + id));
        }

        try {
            Long numericId = Long.parseLong(id);
            User user = getCurrentUser(userDetails);
            HackathonResponse hackathon = hackathonService.getHackathonById(numericId, user);
            return ResponseEntity.ok(ApiResponse.<HackathonResponse>builder()
                    .success(true).message("Hackathon retrieved").data(hackathon).build());
        } catch (NumberFormatException e) {
            Hackathon externalHackathon = aggregationService.findById(id).orElse(null);
            if (externalHackathon != null) {
                return ResponseEntity.ok(ApiResponse.success("External hackathon retrieved", externalHackathon));
            }
            return ResponseEntity.status(404).body(ApiResponse.error("Hackathon not found with id: " + id));
        }
    }

    @PostMapping
    @Operation(summary = "Create a new hackathon")
    public ResponseEntity<ApiResponse<HackathonResponse>> createHackathon(
            @RequestBody CreateHackathonRequest request) {
        HackathonResponse hackathon = hackathonService.createHackathon(request);

        // Broadcast announcement to all users
        try {
            userRepository.findAll().forEach(u -> {
                notificationService.createNotification(u, "Hackathon Announcement: " + hackathon.getTitle(),
                        "New hackathon '" + hackathon.getTitle() + "' is now open for registration! Deadline: " +
                        (hackathon.getRegistrationDeadline() != null ? hackathon.getRegistrationDeadline() : "Soon"),
                        "hackathon", "🚀", "View Hackathon", "/hackathons");
            });
        } catch (Exception ignored) {}

        return ResponseEntity.ok(ApiResponse.<HackathonResponse>builder()
                .success(true).message("Hackathon created").data(hackathon).build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete hackathon by ID")
    public ResponseEntity<ApiResponse<Void>> deleteHackathon(@PathVariable Long id) {
        hackathonService.deleteHackathon(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Hackathon deleted").build());
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update hackathon status")
    public ResponseEntity<ApiResponse<HackathonResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        HackathonResponse hackathon = hackathonService.updateHackathonStatus(id, status);
        return ResponseEntity.ok(ApiResponse.<HackathonResponse>builder()
                .success(true).message("Hackathon status updated").data(hackathon).build());
    }
}
