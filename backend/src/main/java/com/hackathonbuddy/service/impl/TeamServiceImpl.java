package com.hackathonbuddy.service.impl;

import com.hackathonbuddy.dto.request.CreateTeamRequest;
import com.hackathonbuddy.dto.response.TeamMemberResponse;
import com.hackathonbuddy.dto.response.TeamResponse;
import com.hackathonbuddy.entity.*;
import com.hackathonbuddy.repository.*;
import com.hackathonbuddy.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final HackathonRepository hackathonRepository;
    private final com.hackathonbuddy.service.NotificationService notificationService;

    @Override
    public TeamResponse getMyTeam(User user) {
        return teamRepository.findFirstTeamForUser(user)
                .map(team -> toResponse(team, user))
                .orElse(null);
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

    @Override
    public List<TeamResponse> getAllMyTeams(User user) {
        List<Team> teams = teamRepository.findTeamsByMember(user);
        
        // If user is not yet in any team, auto-join existing team or create default squad
        if (teams.isEmpty()) {
            List<Team> allTeams = teamRepository.findAll();
            if (!allTeams.isEmpty()) {
                Team defaultTeam = allTeams.get(0);
                if (!teamMemberRepository.existsByTeamAndUser(defaultTeam, user)) {
                    TeamMember member = TeamMember.builder()
                            .team(defaultTeam)
                            .user(user)
                            .role("Member")
                            .status("ACCEPTED")
                            .build();
                    teamMemberRepository.save(member);
                }
                teams = teamRepository.findTeamsByMember(user);
            } else if (user != null) {
                // First team in platform
                CreateTeamRequest defaultReq = new CreateTeamRequest();
                defaultReq.setName("General Squad");
                defaultReq.setDescription("Default team channel for live squad collaboration");
                createTeam(defaultReq, user);
                teams = teamRepository.findTeamsByMember(user);
            }
        }

        return teams.stream()
                .map(team -> toResponse(team, user))
                .collect(Collectors.toList());
    }

    @Override
    public TeamResponse createTeam(CreateTeamRequest request, User user) {
        Team team = Team.builder()
                .name(request.getName())
                .description(request.getDescription())
                .leader(user)
                .skillCoveragePercent(0)
                .build();

        if (request.getHackathonId() != null) {
            hackathonRepository.findById(request.getHackathonId())
                    .ifPresent(team::setHackathon);
        }

        team = teamRepository.save(team);

        // Auto-add leader as first member
        TeamMember leaderMember = TeamMember.builder()
                .team(team)
                .user(user)
                .role("Team Leader")
                .status("ACCEPTED")
                .build();
        teamMemberRepository.save(leaderMember);

        // Notify admins of new team creation
        try {
            notificationService.notifyAdmins("New Squad Created",
                    "Squad '" + team.getName() + "' was created by " + getUserDisplayName(user), "team_created");
        } catch (Exception ignored) {}

        return toResponse(team, user);
    }

    @Override
    public TeamResponse updateTeam(Long teamId, CreateTeamRequest request, User user) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found"));
        
        if (request.getName() != null && !request.getName().isBlank()) {
            team.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            team.setDescription(request.getDescription().trim());
        }
        
        Team saved = teamRepository.save(team);
        return toResponse(saved, user);
    }

    @Override
    public TeamResponse addMember(Long teamId, Long userId, String role, User currentUser) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found"));
        User newMember = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String memberRole = role != null ? role : "Member";
        if (!teamMemberRepository.existsByTeamAndUser(team, newMember)) {
            TeamMember member = TeamMember.builder()
                    .team(team)
                    .user(newMember)
                    .role(memberRole)
                    .status("ACCEPTED")
                    .build();
            teamMemberRepository.save(member);
        }

        // Notify added member
        try {
            notificationService.createNotification(newMember, "Added to Squad",
                    "You were added to squad " + team.getName() + " as " + memberRole, "team", "🎉", "View Team", "/teams");
        } catch (Exception ignored) {}

        return toResponse(team, currentUser);
    }

    @Override
    public TeamResponse removeMember(Long teamId, Long userId, User currentUser) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found"));
        User memberUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        teamMemberRepository.findByTeamAndUser(team, memberUser).ifPresent(tm -> {
            teamMemberRepository.delete(tm);
            // Notify removed member
            try {
                notificationService.createNotification(memberUser, "Removed from Squad",
                        "You were removed from squad " + team.getName(), "team", "ℹ️", "View Teams", "/teams");
            } catch (Exception ignored) {}
        });

        return toResponse(team, currentUser);
    }

    @Override
    public TeamResponse updateMemberRole(Long teamId, Long userId, String newRole, User currentUser) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found"));
        User memberUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        teamMemberRepository.findByTeamAndUser(team, memberUser).ifPresent(tm -> {
            tm.setRole(newRole);
            teamMemberRepository.save(tm);
            // Notify affected member
            try {
                notificationService.createNotification(memberUser, "Role Updated",
                        "Your role in squad " + team.getName() + " was changed to " + newRole, "team", "👤", "View Team", "/teams");
            } catch (Exception ignored) {}
        });

        return toResponse(team, currentUser);
    }

    @Override
    public TeamResponse getSkillGap(Long teamId, User currentUser) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found"));
        // Skill gap analysis will be computed by Python ML service
        // For now return team with computed coverage
        return toResponse(team, currentUser);
    }

    private TeamResponse toResponse(Team team, User currentUser) {
        List<TeamMember> members = teamMemberRepository.findByTeam(team);

        List<TeamMemberResponse> memberResponses = members.stream()
                .filter(m -> m.getStatus() == null || (!"REJECTED".equalsIgnoreCase(m.getStatus()) && !"DECLINED".equalsIgnoreCase(m.getStatus())))
                .map(m -> {
                    String fullName = getUserDisplayName(m.getUser());
                    return TeamMemberResponse.builder()
                            .id(m.getId())
                            .userId(m.getUser().getId())
                            .name(fullName)
                            .email(m.getUser().getEmail())
                            .role(m.getRole())
                            .status(m.getStatus() != null ? m.getStatus() : "ACCEPTED")
                            .isCurrentUser(currentUser != null && m.getUser().getId().equals(currentUser.getId()))
                            .build();
                })
                .collect(Collectors.toList());

        if (team.getLeader() != null) {
            boolean leaderInMembers = memberResponses.stream()
                    .anyMatch(m -> m.getUserId().equals(team.getLeader().getId()));
            if (!leaderInMembers) {
                User leaderUser = team.getLeader();
                String fullName = getUserDisplayName(leaderUser);
                TeamMemberResponse leaderResp = TeamMemberResponse.builder()
                        .id(null)
                        .userId(leaderUser.getId())
                        .name(fullName)
                        .email(leaderUser.getEmail())
                        .role("Team Leader")
                        .status("ACCEPTED")
                        .isCurrentUser(currentUser != null && leaderUser.getId().equals(currentUser.getId()))
                        .build();
                memberResponses.add(0, leaderResp);
            }
        }

        TeamMemberResponse leader = memberResponses.stream()
                .filter(m -> team.getLeader() != null && m.getUserId().equals(team.getLeader().getId()))
                .findFirst()
                .orElse(null);

        // Simulated missing skills until ML service integration
        List<String> missingSkills = Arrays.asList("Docker", "AWS", "Kubernetes");

        return TeamResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .description(team.getDescription())
                .leader(leader)
                .members(memberResponses)
                .skillCoveragePercent(team.getSkillCoveragePercent() != null ? team.getSkillCoveragePercent() : 82)
                .missingSkills(missingSkills)
                .isActive(team.getIsActive())
                .build();
    }
}
