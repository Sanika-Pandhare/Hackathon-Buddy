package com.hackathonbuddy.service.impl;

import com.hackathonbuddy.dto.response.NotificationResponse;
import com.hackathonbuddy.entity.Notification;
import com.hackathonbuddy.entity.Team;
import com.hackathonbuddy.entity.TeamMember;
import com.hackathonbuddy.entity.User;
import com.hackathonbuddy.repository.NotificationRepository;
import com.hackathonbuddy.repository.TeamMemberRepository;
import com.hackathonbuddy.repository.TeamRepository;
import com.hackathonbuddy.repository.UserRepository;
import com.hackathonbuddy.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Override
    public List<NotificationResponse> getNotifications(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public long getUnreadCount(User user) {
        return notificationRepository.countByUserAndIsReadFalse(user);
    }

    @Override
    @Transactional
    public void markAllAsRead(User user) {
        List<Notification> unread = notificationRepository.findByUserAndIsReadFalseOrderByCreatedAtDesc(user);
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setIsRead(true);
            notificationRepository.save(n);
        });
    }

    @Override
    @Transactional
    public void deleteNotification(Long notificationId) {
        notificationRepository.deleteById(notificationId);
    }

    @Override
    @Transactional
    public void clearAllNotifications(User user) {
        notificationRepository.deleteByUser(user);
    }

    @Override
    @Transactional
    public void createNotification(User user, String title, String message, String type) {
        createNotification(user, title, message, type, null, null, null);
    }

    @Override
    @Transactional
    public void createNotification(User user, String title, String message, String type,
                                    String icon, String action, String route) {
        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(type)
                .icon(icon != null ? icon : "🔔")
                .action(action)
                .route(route != null ? route : "/notifications")
                .build();
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void sendTeamInvitation(User sender, Long receiverId, Long teamId, String role) {
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new RuntimeException("Receiver user not found"));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found"));

        String senderName = (sender.getFirstName() + " " + (sender.getLastName() != null ? sender.getLastName() : "")).trim();
        String roleName = role != null ? role : "Member";

        Notification notification = Notification.builder()
                .user(receiver)
                .title("Team Invitation: " + team.getName())
                .message(senderName + " invited you to join " + team.getName() + " as " + roleName)
                .type("team_invitation")
                .icon("🤝")
                .action("ACCEPT_REJECT")
                .route("/teams")
                .targetTeamId(team.getId())
                .targetUserId(sender.getId())
                .responseStatus("PENDING")
                .build();
        notificationRepository.save(notification);

        // Also create pending TeamMember record in database
        if (!teamMemberRepository.existsByTeamAndUser(team, receiver)) {
            TeamMember tm = TeamMember.builder()
                    .team(team)
                    .user(receiver)
                    .role(roleName)
                    .status("PENDING")
                    .build();
            teamMemberRepository.save(tm);
        }
    }

    @Override
    @Transactional
    public void sendJoinRequest(User sender, Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found"));
        User leader = team.getLeader();
        if (leader == null) return;

        String senderName = (sender.getFirstName() + " " + (sender.getLastName() != null ? sender.getLastName() : "")).trim();

        Notification notification = Notification.builder()
                .user(leader)
                .title("Team Join Request: " + team.getName())
                .message(senderName + " requested to join your squad " + team.getName())
                .type("team_request")
                .icon("🙋‍♂️")
                .action("ACCEPT_REJECT")
                .route("/teams")
                .targetTeamId(team.getId())
                .targetUserId(sender.getId())
                .responseStatus("PENDING")
                .build();
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void respondToNotification(Long notificationId, String action, User currentUser) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        notification.setIsRead(true);

        boolean isAccept = "ACCEPT".equalsIgnoreCase(action);
        notification.setResponseStatus(isAccept ? "ACCEPTED" : "REJECTED");
        notificationRepository.save(notification);

        String currentUserName = (currentUser.getFirstName() + " " + (currentUser.getLastName() != null ? currentUser.getLastName() : "")).trim();

        // 1. Team Invitation response
        if ("team_invitation".equals(notification.getType()) && notification.getTargetTeamId() != null) {
            teamRepository.findById(notification.getTargetTeamId()).ifPresent(team -> {
                teamMemberRepository.findByTeamAndUser(team, currentUser).ifPresent(tm -> {
                    tm.setStatus(isAccept ? "ACCEPTED" : "REJECTED");
                    teamMemberRepository.save(tm);
                });

                if (notification.getTargetUserId() != null) {
                    userRepository.findById(notification.getTargetUserId()).ifPresent(sender -> {
                        String msg = isAccept
                                ? currentUserName + " accepted your invitation to join " + team.getName()
                                : currentUserName + " declined your invitation to join " + team.getName();
                        createNotification(sender, isAccept ? "Invitation Accepted" : "Invitation Declined",
                                msg, "team", isAccept ? "✅" : "❌", "View Team", "/teams");
                    });
                }
            });
        }

        // 2. Team Join Request response
        if ("team_request".equals(notification.getType()) && notification.getTargetTeamId() != null && notification.getTargetUserId() != null) {
            teamRepository.findById(notification.getTargetTeamId()).ifPresent(team -> {
                userRepository.findById(notification.getTargetUserId()).ifPresent(requestor -> {
                    if (isAccept) {
                        if (!teamMemberRepository.existsByTeamAndUser(team, requestor)) {
                            TeamMember tm = TeamMember.builder()
                                    .team(team)
                                    .user(requestor)
                                    .role(requestor.getPrimaryRole() != null ? requestor.getPrimaryRole() : "Member")
                                    .status("ACCEPTED")
                                    .build();
                            teamMemberRepository.save(tm);
                        } else {
                            teamMemberRepository.findByTeamAndUser(team, requestor).ifPresent(tm -> {
                                tm.setStatus("ACCEPTED");
                                teamMemberRepository.save(tm);
                            });
                        }
                    }

                    String msg = isAccept
                            ? "Your request to join team " + team.getName() + " was accepted!"
                            : "Your request to join team " + team.getName() + " was declined.";
                    createNotification(requestor, isAccept ? "Join Request Accepted" : "Join Request Declined",
                            msg, "team", isAccept ? "🎉" : "ℹ️", "View Team", "/teams");
                });
            });
        }
    }

    @Override
    @Transactional
    public void notifyTeamMembers(Long teamId, String title, String message, String type, String icon, String action, String route) {
        teamRepository.findById(teamId).ifPresent(team -> {
            List<TeamMember> members = teamMemberRepository.findByTeamAndStatus(team, "ACCEPTED");
            for (TeamMember m : members) {
                createNotification(m.getUser(), title, message, type, icon, action, route);
            }
        });
    }

    @Override
    @Transactional
    public void notifyAdmins(String title, String message, String type) {
        List<User> admins = userRepository.findAll().stream()
                .filter(u -> u.getRole() != null &&
                        ("ADMIN".equalsIgnoreCase(u.getRole().getName()) ||
                         "SUPER_ADMIN".equalsIgnoreCase(u.getRole().getName()) ||
                         "HACKATHON_ADMIN".equalsIgnoreCase(u.getRole().getName())))
                .collect(Collectors.toList());

        for (User admin : admins) {
            createNotification(admin, title, message, type, "🛡️", "Admin Dashboard", "/admin");
        }
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .isRead(n.getIsRead())
                .unread(!Boolean.TRUE.equals(n.getIsRead()))
                .icon(n.getIcon())
                .action(n.getAction())
                .route(n.getRoute())
                .actionUrl(n.getActionUrl())
                .targetTeamId(n.getTargetTeamId())
                .targetUserId(n.getTargetUserId())
                .responseStatus(n.getResponseStatus())
                .time(formatRelativeTime(n.getCreatedAt()))
                .date(n.getCreatedAt() != null && n.getCreatedAt().toLocalDate().equals(LocalDateTime.now().toLocalDate()) ? "Today" : "Earlier")
                .createdAt(n.getCreatedAt())
                .build();
    }

    private String formatRelativeTime(LocalDateTime dateTime) {
        if (dateTime == null) return "Just now";
        Duration duration = Duration.between(dateTime, LocalDateTime.now());
        long minutes = duration.toMinutes();
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " minutes ago";
        long hours = duration.toHours();
        if (hours < 24) return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        long days = duration.toDays();
        return days + " day" + (days > 1 ? "s" : "") + " ago";
    }
}
