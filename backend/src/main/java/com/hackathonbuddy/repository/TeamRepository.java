package com.hackathonbuddy.repository;

import com.hackathonbuddy.entity.Team;
import com.hackathonbuddy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.query.Param;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByLeader(User leader);

    @Query("SELECT DISTINCT t FROM Team t LEFT JOIN t.members m WHERE (t.leader = :user OR m.user = :user) AND (t.isActive = true OR t.isActive IS NULL) ORDER BY t.id ASC")
    List<Team> findTeamsByMember(@Param("user") User user);

    @Query("SELECT t FROM Team t WHERE t.leader = :user OR EXISTS (SELECT m FROM TeamMember m WHERE m.team = t AND m.user = :user)")
    Optional<Team> findFirstTeamForUser(@Param("user") User user);
}
