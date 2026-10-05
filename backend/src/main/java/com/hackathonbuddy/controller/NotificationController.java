package com.hackathonbuddy.controller;

import com.hackathonbuddy.dto.response.ApiResponse;
import com.hackathonbuddy.dto.response.NotificationResponse;
import com.hackathonbuddy.entity.User;
import com.hackathonbuddy.repository.UserRepository;
import com.hackathonbuddy.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    private User getUser(UserDetails ud) {
        return userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        List<NotificationResponse> notifications = notificationService.getNotifications(user);
        return ResponseEntity.ok(ApiResponse.<List<NotificationResponse>>builder()
                .success(true).message("Notifications retrieved").data(notifications).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createNotification(
            @RequestBody java.util.Map<String, String> payload,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        String title = payload.getOrDefault("title", "Notification");
        String message = payload.getOrDefault("message", "");
        String type = payload.getOrDefault("type", "info");
        String icon = payload.get("icon");
        String action = payload.get("action");
        String route = payload.get("route");
        notificationService.createNotification(user, title, message, type, icon, action, route);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Notification created successfully").build());
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        long count = notificationService.getUnreadCount(user);
        return ResponseEntity.ok(ApiResponse.<Long>builder()
                .success(true).message("Unread count").data(count).build());
    }

    @PutMapping("/mark-all-read")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        notificationService.markAllAsRead(user);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("All notifications marked as read").build());
    }

    @PutMapping("/{id}/mark-read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Notification marked as read").build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Notification deleted").build());
    }

    @PostMapping("/invite")
    public ResponseEntity<ApiResponse<Void>> sendInvitation(
            @RequestBody java.util.Map<String, Object> payload,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        Long receiverId = payload.get("receiverId") != null ? Long.valueOf(payload.get("receiverId").toString()) : null;
        Long teamId = payload.get("teamId") != null ? Long.valueOf(payload.get("teamId").toString()) : null;
        String role = (String) payload.getOrDefault("role", "Member");
        if (receiverId != null && teamId != null) {
            notificationService.sendTeamInvitation(user, receiverId, teamId, role);
        }
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Team invitation sent successfully").build());
    }

    @PostMapping("/join-request")
    public ResponseEntity<ApiResponse<Void>> sendJoinRequest(
            @RequestBody java.util.Map<String, Object> payload,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        Long teamId = payload.get("teamId") != null ? Long.valueOf(payload.get("teamId").toString()) : null;
        if (teamId != null) {
            notificationService.sendJoinRequest(user, teamId);
        }
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Join request sent successfully").build());
    }

    @PostMapping("/{id}/respond")
    public ResponseEntity<ApiResponse<Void>> respondToNotification(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> payload,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        String action = payload.getOrDefault("action", "ACCEPT");
        notificationService.respondToNotification(id, action, user);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Responded to notification successfully").build());
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        notificationService.clearAllNotifications(user);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("All notifications cleared").build());
    }
}
