package com.hackathonbuddy.controller;

import com.hackathonbuddy.dto.response.ApiResponse;
import com.hackathonbuddy.entity.ChatMessage;
import com.hackathonbuddy.entity.Team;
import com.hackathonbuddy.entity.User;
import com.hackathonbuddy.repository.ChatMessageRepository;
import com.hackathonbuddy.repository.TeamMemberRepository;
import com.hackathonbuddy.repository.TeamRepository;
import com.hackathonbuddy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private User getUser(UserDetails ud) {
        if (ud == null) return null;
        return userRepository.findByEmail(ud.getUsername()).orElse(null);
    }

    private String getUserDisplayName(User user) {
        if (user == null) return "Squad Member";
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last = user.getLastName() != null ? user.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        if (!full.isEmpty() && !"null".equalsIgnoreCase(full) && !"null null".equalsIgnoreCase(full)) {
            return full;
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return "Squad Member";
    }

    private boolean isMemberOfTeam(User user, Long teamId) {
        if (user == null || teamId == null) return false;
        Team team = teamRepository.findById(teamId).orElse(null);
        if (team == null) return false;

        // Check if leader
        if (team.getLeader() != null && team.getLeader().getId().equals(user.getId())) {
            return true;
        }

        // Check team member (allow if present and not explicitly REJECTED/DECLINED)
        boolean isMember = teamMemberRepository.findByTeamAndUser(team, user)
                .map(m -> m.getStatus() == null || (!"REJECTED".equalsIgnoreCase(m.getStatus()) && !"DECLINED".equalsIgnoreCase(m.getStatus())))
                .orElse(false);

        if (isMember) return true;

        // Auto-add user as member of team if accessing squad chat
        try {
            com.hackathonbuddy.entity.TeamMember newMember = com.hackathonbuddy.entity.TeamMember.builder()
                    .team(team)
                    .user(user)
                    .role("Member")
                    .status("ACCEPTED")
                    .build();
            teamMemberRepository.save(newMember);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * REST endpoint to fetch message history for a team
     */
    @GetMapping("/api/chat/{teamId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessage>>> getMessages(
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.<List<ChatMessage>>builder()
                            .success(false).message("Unauthorized: Login required").build());
        }

        if (!isMemberOfTeam(user, teamId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.<List<ChatMessage>>builder()
                            .success(false).message("Forbidden: You are not a member of this team").build());
        }

        List<ChatMessage> messages = chatMessageRepository
                .findByConversationIdOrderByTimestampAsc(teamId);
        return ResponseEntity.ok(ApiResponse.<List<ChatMessage>>builder()
                .success(true).message("Messages retrieved").data(messages).build());
    }

    /**
     * REST endpoint to send a text message in a team chat
     */
    @PostMapping("/api/chat/{teamId}/messages")
    public ResponseEntity<ApiResponse<ChatMessage>> postMessage(
            @PathVariable Long teamId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.<ChatMessage>builder()
                            .success(false).message("Unauthorized: Login required").build());
        }

        if (!isMemberOfTeam(user, teamId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.<ChatMessage>builder()
                            .success(false).message("Forbidden: You are not a member of this team").build());
        }

        String content = body != null ? body.get("content") : null;
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ChatMessage>builder()
                            .success(false).message("Message content cannot be empty").build());
        }

        String displayName = getUserDisplayName(user);

        ChatMessage chatMessage = ChatMessage.builder()
                .conversationId(teamId)
                .teamId(teamId)
                .senderId(user.getId())
                .senderName(displayName)
                .content(content.trim())
                .messageType("TEXT")
                .timestamp(LocalDateTime.now())
                .build();

        ChatMessage saved = chatMessageRepository.save(chatMessage);

        // Broadcast to WebSocket subscribers on /topic/chat/{teamId}
        try {
            messagingTemplate.convertAndSend("/topic/chat/" + teamId, saved);
        } catch (Exception ignored) {}

        return ResponseEntity.ok(ApiResponse.<ChatMessage>builder()
                .success(true).message("Message sent").data(saved).build());
    }

    /**
     * WebSocket STOMP endpoint: client sends to /app/chat/{teamId}
     * Server broadcasts to /topic/chat/{teamId}
     */
    @MessageMapping("/chat/{teamId}")
    @SendTo("/topic/chat/{teamId}")
    public ChatMessage sendMessage(
            @DestinationVariable Long teamId,
            @Payload ChatMessage message) {
        message.setConversationId(teamId);
        message.setTeamId(teamId);
        if (message.getTimestamp() == null) {
            message.setTimestamp(LocalDateTime.now());
        }
        if (message.getMessageType() == null) {
            message.setMessageType("TEXT");
        }
        if (message.getSenderName() == null || message.getSenderName().isBlank() || "null".equalsIgnoreCase(message.getSenderName())) {
            message.setSenderName("Squad Member");
        }
        return chatMessageRepository.save(message);
    }
}
