package com.hackathonbuddy.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "sender_id")
    private Long senderId;

    @Column(nullable = false)
    private String senderName;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    private String messageType; // TEXT, IMAGE, FILE

    @Column(name = "created_at")
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) timestamp = LocalDateTime.now();
        if (messageType == null) messageType = "TEXT";
        if (teamId == null) teamId = conversationId;
        if (conversationId == null) conversationId = teamId;
    }

    @JsonProperty("teamId")
    public Long getTeamId() {
        return teamId != null ? teamId : conversationId;
    }

    @JsonProperty("teamId")
    public void setTeamId(Long teamId) {
        this.teamId = teamId;
        this.conversationId = teamId;
    }

    @JsonProperty("createdAt")
    public LocalDateTime getCreatedAt() {
        return timestamp;
    }

    @JsonProperty("createdAt")
    public void setCreatedAt(LocalDateTime createdAt) {
        this.timestamp = createdAt;
    }
}
