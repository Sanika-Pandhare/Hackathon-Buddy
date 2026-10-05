package com.hackathonbuddy.service.impl;

import com.hackathonbuddy.dto.request.LoginRequest;
import com.hackathonbuddy.dto.request.RegisterRequest;
import com.hackathonbuddy.dto.response.ApiResponse;
import com.hackathonbuddy.dto.response.AuthResponse;
import com.hackathonbuddy.dto.response.UserResponse;
import com.hackathonbuddy.entity.Role;
import com.hackathonbuddy.entity.Skill;
import com.hackathonbuddy.entity.User;
import com.hackathonbuddy.entity.UserSkill;
import com.hackathonbuddy.exception.ResourceNotFoundException;
import com.hackathonbuddy.exception.UnauthorizedException;
import com.hackathonbuddy.exception.UserAlreadyExistsException;
import com.hackathonbuddy.repository.RoleRepository;
import com.hackathonbuddy.repository.SkillRepository;
import com.hackathonbuddy.repository.UserRepository;
import com.hackathonbuddy.repository.UserSkillRepository;
import com.hackathonbuddy.security.JwtService;
import com.hackathonbuddy.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserSkillRepository userSkillRepository;
    private final SkillRepository skillRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final com.hackathonbuddy.service.NotificationService notificationService;

    @Override
    @Transactional
    public ApiResponse<AuthResponse> register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException(normalizedEmail);
        }

        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", "STUDENT"));

        // Handle fullName → firstName + lastName split
        String firstName = request.getFirstName();
        String lastName = request.getLastName();
        if ((firstName == null || firstName.isBlank()) && request.getFullName() != null) {
            String[] parts = request.getFullName().trim().split("\\s+", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : "";
        }
        if (firstName == null) firstName = "";
        if (lastName == null) lastName = "";

        String roleName = request.getPrimaryRole();
        if (roleName == null || roleName.isBlank()) {
            roleName = "Full Stack Developer";
        }

        String locationStr = request.getLocation();
        if (locationStr == null || locationStr.isBlank()) {
            locationStr = "India";
        }

        String bioStr = "Passionate student developer.";
        if (request.getTechSkills() != null && !request.getTechSkills().isBlank()) {
            bioStr += " Skilled in " + request.getTechSkills() + ".";
        }

        User user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(studentRole)
                .primaryRole(roleName)
                .location(locationStr)
                .bio(bioStr)
                .githubUrl(request.getGithubUrl())
                .isActive(true)
                .profileComplete(true)
                .build();

        User savedUser = userRepository.save(user);

        // Save techSkills if provided in RegisterRequest
        if (request.getTechSkills() != null && !request.getTechSkills().isBlank()) {
            String[] skillsArr = request.getTechSkills().split(",");
            for (String sName : skillsArr) {
                String cleanSkill = sName.trim();
                if (!cleanSkill.isEmpty()) {
                    final String skillToFind = cleanSkill;
                    Skill skill = skillRepository.findByNameIgnoreCase(skillToFind)
                            .orElseGet(() -> skillRepository.save(Skill.builder().name(skillToFind).category("General").build()));
                    userSkillRepository.save(UserSkill.builder()
                            .user(savedUser)
                            .skill(skill)
                            .proficiencyLevel(4)
                            .build());
                }
            }
        }

        String jwtToken = jwtService.generateToken(savedUser.getId(), savedUser.getEmail(), savedUser.getRole().getName());

        UserResponse userResponse = buildUserResponse(savedUser);

        try {
            notificationService.notifyAdmins("New Student Registration",
                    "Student " + savedUser.getFirstName() + " (" + savedUser.getEmail() + ") registered.", "user_registered");
        } catch (Exception e) {
            // Ignore if admin notification fails during registration
        }

        AuthResponse authResponse = AuthResponse.builder()
                .token(jwtToken)
                .user(userResponse)
                .build();

        return ApiResponse.success("User registered successfully", authResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<AuthResponse> login(LoginRequest request) {
        String cleanEmail = request.getEmail() != null ? request.getEmail().toLowerCase().trim() : "";
        String rawPassword = request.getPassword() != null ? request.getPassword().trim() : "";

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        cleanEmail,
                        rawPassword
                )
        );

        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", cleanEmail));

        if (!user.getIsActive()) {
            throw new UnauthorizedException("User account is disabled");
        }

        String jwtToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().getName());

        UserResponse userResponse = buildUserResponse(user);

        AuthResponse authResponse = AuthResponse.builder()
                .token(jwtToken)
                .user(userResponse)
                .build();

        return ApiResponse.success("Login successful", authResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<UserResponse> getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        UserResponse userResponse = buildUserResponse(user);

        return ApiResponse.success("Current user fetched successfully", userResponse);
    }

    /**
     * Build a rich UserResponse matching what the frontend expects.
     */
    private UserResponse buildUserResponse(User user) {
        List<String> skillNames = List.of();
        try {
            skillNames = userSkillRepository.findByUser(user)
                    .stream()
                    .map(us -> us.getSkill().getName())
                    .collect(Collectors.toList());
        } catch (Exception ignored) {
        }

        boolean isAdmin = user.getRole() != null &&
                ("ADMIN".equalsIgnoreCase(user.getRole().getName()) ||
                 "HACKATHON_ADMIN".equalsIgnoreCase(user.getRole().getName()) ||
                 "DEVELOPER_ADMIN".equalsIgnoreCase(user.getRole().getName()) ||
                 "SUPER_ADMIN".equalsIgnoreCase(user.getRole().getName()));

        String fullName = ((user.getFirstName() != null ? user.getFirstName() : "") + " " + (user.getLastName() != null ? user.getLastName() : "")).trim();
        if (fullName.isBlank()) {
            fullName = user.getEmail() != null ? user.getEmail().split("@")[0] : "Student Candidate";
        }

        String techSkillsStr = !skillNames.isEmpty() ? String.join(", ", skillNames) : "";

        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(fullName)
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole() != null ? user.getRole().getName() : "STUDENT")
                .primaryRole(user.getPrimaryRole() != null ? user.getPrimaryRole() : "Full Stack Developer")
                .location(user.getLocation() != null ? user.getLocation() : "India")
                .bio(user.getBio() != null ? user.getBio() : "Passionate student developer.")
                .githubUrl(user.getGithubUrl())
                .linkedinUrl(user.getLinkedinUrl())
                .techSkills(techSkillsStr)
                .skills(skillNames)
                .domains(List.of())
                .isActive(user.getIsActive())
                .isAdmin(isAdmin)
                .profileComplete(user.getProfileComplete())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
