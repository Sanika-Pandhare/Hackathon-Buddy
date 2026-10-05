import React, { useState, useEffect, useMemo } from "react";
import {
  Brain,
  Users,
  BarChart3,
  Lightbulb,
  Sparkles,
  Search,
  CheckCircle2,
  Target,
  Code2,
  Zap,
  ArrowRight,
  UserPlus,
  Loader2
} from "lucide-react";
import { useApp } from "../../context/AppContext";
import { aiAPI } from "../../api/api";
import "./AIHub.css";

function AIHub() {
  const { currentUser, teamMembers, addTeamMember, addNotification, generatedIdeas, setGeneratedIdeas } = useApp();
  const [activeModule, setActiveModule] = useState("matching");

  const userSkillsFormatted = React.useMemo(() => {
    if (!currentUser) return "React, Python, Machine Learning";
    if (Array.isArray(currentUser.skills) && currentUser.skills.length > 0) {
      return currentUser.skills.join(", ");
    }
    if (currentUser.techSkills) {
      return currentUser.techSkills;
    }
    return "React, Python, Machine Learning";
  }, [currentUser]);

  /* TEAM MATCHING STATE */
  const [skills, setSkills] = useState(userSkillsFormatted);
  const [domain, setDomain] = useState("AI / ML");
  const [role, setRole] = useState("Full Stack Developer");
  const [matchingResults, setMatchingResults] = useState([]);
  const [isMatchingLoading, setIsMatchingLoading] = useState(false);

  useEffect(() => {
    if (userSkillsFormatted) {
      setSkills(userSkillsFormatted);
    }
  }, [userSkillsFormatted]);

  /* SKILL GAP STATE */
  const [skillAnalysis, setSkillAnalysis] = useState(null);
  const [isSkillLoading, setIsSkillLoading] = useState(false);

  /* PROJECT IDEA STATE */
  const [ideaDomain, setIdeaDomain] = useState("AI / ML");
  const [technology, setTechnology] = useState("React + Python");
  const [difficulty, setDifficulty] = useState("Intermediate");
  const [isIdeaLoading, setIsIdeaLoading] = useState(false);

  // Aggregate current squad members for skill gap comparison
  const squadMembersList = React.useMemo(() => {
    const list = [...(teamMembers || [])];
    if (currentUser) {
      const hasLeader = list.some(m => m.isYou || m.name === currentUser.fullName || m.name === currentUser.firstName);
      if (!hasLeader) {
        list.unshift({
          id: currentUser.id || "you",
          name: currentUser.fullName || (currentUser.firstName ? `${currentUser.firstName} ${currentUser.lastName || ''}`.trim() : currentUser.email) || "Leader",
          role: currentUser.primaryRole || currentUser.role || "Full Stack Developer",
          skills: currentUser.techSkills ? currentUser.techSkills.split(",").map(s => s.trim()).filter(Boolean) : (currentUser.skills || ["React", "Node.js", "Python"]),
          letter: (currentUser.fullName || currentUser.firstName || "U").charAt(0).toUpperCase(),
          isYou: true
        });
      }
    }
    return list;
  }, [teamMembers, currentUser]);

  // Skill Gap Analyzer function for any generated project blueprint
  const analyzeIdeaSkillGap = (idea) => {
    let reqSkills = [];
    if (Array.isArray(idea.tags) && idea.tags.length > 0) {
      reqSkills = idea.tags;
    } else if (idea.techStack) {
      reqSkills = idea.techStack.split(/[,•]/).map(s => s.trim()).filter(Boolean);
    } else {
      reqSkills = ["React", "Node.js", "Python", "PostgreSQL", "Docker", "AWS"];
    }

    const uniqueReqMap = new Map();
    reqSkills.forEach(s => {
      const key = s.trim().toLowerCase();
      if (key && !uniqueReqMap.has(key)) {
        uniqueReqMap.set(key, s.trim());
      }
    });
    const requiredSkills = Array.from(uniqueReqMap.values());

    const availableSkills = [];
    const missingSkills = [];

    requiredSkills.forEach(skill => {
      const normSkill = skill.toLowerCase();
      const matchingMember = squadMembersList.find(m => {
        const mSkills = Array.isArray(m.skills)
          ? m.skills
          : (typeof m.skills === 'string' ? m.skills.split(',').map(x => x.trim()) : []);
        return mSkills.some(ms => ms.toLowerCase().includes(normSkill) || normSkill.includes(ms.toLowerCase()));
      });

      if (matchingMember) {
        availableSkills.push({
          skill,
          member: matchingMember
        });
      } else {
        missingSkills.push(skill);
      }
    });

    const missingSkillAssignments = missingSkills.map(missingSkill => {
      const normMissing = missingSkill.toLowerCase();
      let bestMember = squadMembersList[0] || {
        name: currentUser?.fullName || "Squad Member",
        role: currentUser?.primaryRole || "Developer",
        letter: "M",
        isYou: true
      };
      let bestScore = -1;
      let rationale = "";

      squadMembersList.forEach(m => {
        const roleStr = (m.role || "").toLowerCase();
        let score = 0;
        let reason = "Assigned for cross-skilling & blueprint execution";

        if (normMissing.includes("docker") || normMissing.includes("aws") || normMissing.includes("cloud") || normMissing.includes("devops") || normMissing.includes("kubernetes")) {
          if (roleStr.includes("devops") || roleStr.includes("cloud") || roleStr.includes("sys")) {
            score += 10;
            reason = "Specialized role alignment for cloud deployment & containerization";
          } else if (roleStr.includes("backend") || roleStr.includes("full stack")) {
            score += 5;
            reason = "Backend engineering background enables fast infrastructure onboarding";
          }
        } else if (normMissing.includes("python") || normMissing.includes("ml") || normMissing.includes("ai") || normMissing.includes("tensor") || normMissing.includes("nlp") || normMissing.includes("data")) {
          if (roleStr.includes("ml") || roleStr.includes("data") || roleStr.includes("ai")) {
            score += 10;
            reason = "Direct domain alignment with Machine Learning & AI pipelines";
          } else if (roleStr.includes("backend") || roleStr.includes("full stack")) {
            score += 5;
            reason = "Strong backend foundation enables quick Python AI adoption";
          }
        } else if (normMissing.includes("react") || normMissing.includes("figma") || normMissing.includes("ui") || normMissing.includes("css") || normMissing.includes("frontend") || normMissing.includes("web")) {
          if (roleStr.includes("ui") || roleStr.includes("designer") || roleStr.includes("frontend")) {
            score += 10;
            reason = "Frontend & UI design experience provides seamless adoption";
          } else if (roleStr.includes("full stack")) {
            score += 5;
            reason = "Full stack versatility covers UI component integration";
          }
        } else {
          if (roleStr.includes("full stack")) {
            score += 6;
            reason = "Versatile full stack capability suited for rapid skill acquisition";
          }
        }

        if (score > bestScore) {
          bestScore = score;
          bestMember = m;
          rationale = reason;
        }
      });

      return {
        skill: missingSkill,
        assignedMember: bestMember,
        rationale: rationale || "Best suited squad member for rapid skill acquisition"
      };
    });

    const coveragePercent = Math.round((availableSkills.length / (requiredSkills.length || 1)) * 100);

    return {
      requiredSkills,
      availableSkills,
      missingSkills,
      missingSkillAssignments,
      coveragePercent
    };
  };

  const candidatePool = [
    {
      id: 1,
      name: "Rahul Sharma",
      role: "Backend Developer",
      skills: ["Java", "Spring Boot", "PostgreSQL", "Docker", "REST API"],
      domain: "AI / ML",
      match: 94,
    },
    {
      id: 2,
      name: "Priya Singh",
      role: "UI/UX Designer",
      skills: ["Figma", "UI/UX", "Prototyping", "Design Systems", "Tailwind"],
      domain: "Web",
      match: 87,
    },
    {
      id: 3,
      name: "Rohan Mehta",
      role: "ML Developer",
      skills: ["Python", "TensorFlow", "Machine Learning", "PyTorch", "NLP"],
      domain: "AI / ML",
      match: 96,
    },
    {
      id: 4,
      name: "Aman Khan",
      role: "DevOps Engineer",
      skills: ["AWS", "Docker", "Kubernetes", "CI/CD", "Linux"],
      domain: "Cloud",
      match: 91,
    }
  ];

  /* FIND TEAMMATES */
  const handleFindTeammates = async () => {
    setIsMatchingLoading(true);
    try {
      const response = await aiAPI.findTeammates();
      if (response.data?.data && Array.isArray(response.data.data) && response.data.data.length > 0) {
        const mapped = response.data.data.map((cand) => ({
          id: cand.userId || Math.random(),
          name: cand.name || "Candidate",
          role: cand.preferredRole || role || "Developer",
          skills: cand.skills || ["React", "Python"],
          domain: cand.preferredDomains || domain,
          calculatedMatch: Math.round(cand.compatibilityScore || 85),
          matchReason: cand.matchReason
        }));
        setMatchingResults(mapped);
        setIsMatchingLoading(false);
        return;
      }
    } catch (error) {
      console.warn("Backend AI matching unavailable, using local calculation fallback:", error);
    }

    // Local fallback calculation if API has 0 candidates or is offline
    const querySkills = skills
      .toLowerCase()
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);

    const results = candidatePool
      .map((cand) => {
        const matched = cand.skills.filter((cs) =>
          querySkills.some((qs) => cs.toLowerCase().includes(qs))
        );
        let score = cand.match;
        if (querySkills.length > 0) {
          score = Math.min(99, cand.match + matched.length * 3);
        }
        return {
          ...cand,
          calculatedMatch: score,
          matchedSkills: matched,
        };
      })
      .sort((a, b) => b.calculatedMatch - a.calculatedMatch);

    setMatchingResults(results);
    setIsMatchingLoading(false);
  };

  /* SKILL GAP ANALYSIS */
  const handleSkillAnalysis = async () => {
    setIsSkillLoading(true);
    try {
      const response = await aiAPI.skillGap(1);
      if (response.data?.data) {
        const data = response.data.data;
        const available = Array.isArray(data.available_skills)
          ? data.available_skills.map(s => typeof s === 'string' ? s : (s.matched_skill || s.required_skill))
          : (data.availableSkills || ["React", "Node.js", "PostgreSQL"]);
        const missing = Array.isArray(data.missing_skills)
          ? data.missing_skills.map(s => typeof s === 'string' ? s : (s.required_skill || s.best_match))
          : (data.missingSkills || ["Docker", "AWS", "Kubernetes"]);
        const coverage = data.skill_coverage_percentage || data.coveragePercent || 82;

        setSkillAnalysis({
          requiredSkills: [...available, ...missing],
          availableSkills: available,
          missingSkills: missing,
          coverage: Math.round(coverage),
        });
        setIsSkillLoading(false);
        return;
      }
    } catch (error) {
      console.warn("Backend AI skill gap unavailable, using local calculation fallback:", error);
    }

    // Local fallback
    const userSkillsArr = currentUser?.skills || ["React", "JavaScript", "Node.js", "PostgreSQL"];
    const targetStack = ["React", "Node.js", "Python", "Machine Learning", "PostgreSQL", "Docker", "AWS", "Figma"];

    const available = targetStack.filter((s) =>
      userSkillsArr.some((us) => us.toLowerCase() === s.toLowerCase())
    );
    const missing = targetStack.filter(
      (s) => !userSkillsArr.some((us) => us.toLowerCase() === s.toLowerCase())
    );
    const coverage = Math.round((available.length / targetStack.length) * 100);

    setSkillAnalysis({
      requiredSkills: targetStack,
      availableSkills: available,
      missingSkills: missing,
      coverage,
    });
    setIsSkillLoading(false);
  };

  /* PROJECT IDEA GENERATOR */
  const handleGenerateIdeas = async () => {
    setIsIdeaLoading(true);

    // Extract active squad skills
    const squadSkillsSet = new Set();
    if (currentUser?.techSkills) {
      currentUser.techSkills.split(",").map((s) => s.trim()).filter(Boolean).forEach((s) => squadSkillsSet.add(s));
    }
    if (Array.isArray(currentUser?.skills)) {
      currentUser.skills.forEach((s) => squadSkillsSet.add(s));
    }
    (matchingResults || []).slice(0, 3).forEach((m) => {
      if (Array.isArray(m.skills)) {
        m.skills.forEach((s) => squadSkillsSet.add(s));
      }
    });
    const activeSquadSkills = Array.from(squadSkillsSet);

    try {
      const response = await aiAPI.generateIdeas(null, ideaDomain, activeSquadSkills);
      if (response.data?.data && Array.isArray(response.data.data) && response.data.data.length > 0) {
        const ideas = response.data.data.map(item => ({
          title: item.title,
          description: item.description || item.problemStatement,
          tags: (item.techStack || "").split(/[,•]/).map(s => s.trim()).filter(Boolean),
          level: difficulty
        }));
        setGeneratedIdeas(ideas);
        addNotification({
          type: "skill",
          icon: "💡",
          title: "AI Project Blueprints Generated",
          message: `Generated ${ideas.length} new ${ideaDomain} project blueprints leveraging squad skills.`,
          action: "View AI Hub",
          route: "/ai-hub"
        });
        setIsIdeaLoading(false);
        return;
      }
    } catch (error) {
      console.warn("Backend AI idea generator unavailable, using local blueprints fallback:", error);
    }

    // Fallback ideas
    const ideas = [
      {
        title: "AI Hackathon Teammate Matcher & Squad Co-pilot",
        description:
          "Build an intelligent matching agent that scores candidate compatibility using vector similarity and generates sprint roadmaps.",
        tags: ["AI", "Matching", "React", "Python"],
        level: difficulty
      },
      {
        title: "Autonomous Sprint Task & Skill Gap Solver",
        description:
          "Analyze team repository commits, detect missing architectural skills, and generate AI-guided micro-learning exercises.",
        tags: ["ML", "Analytics", "FastAPI", "DevOps"],
        level: difficulty
      },
      {
        title: "Zero-Knowledge FinTech Micro-Lending Protocol",
        description:
          "A decentralized credit-scoring system that utilizes zero-knowledge proofs and ML risk models for instant collateral-free loans.",
        tags: ["Web3", "FinTech", "Smart Contracts", "AI"],
        level: difficulty
      },
      {
        title: "Smart Urban Mobility & EV Charging Mesh",
        description:
          "Real-time IoT telemetry and dynamic pricing routing system for autonomous EV fleet dispatch across metropolitan zones.",
        tags: ["IoT", "Smart City", "Real-Time", "React"],
        level: difficulty
      }
    ];

    setGeneratedIdeas(ideas);
    addNotification({
      type: "skill",
      icon: "💡",
      title: "Project Ideas Generated",
      message: `Generated 4 new ${ideaDomain} hackathon project blueprints.`,
      action: "View AI Hub",
      route: "/ai-hub"
    });
    setIsIdeaLoading(false);
  };

  const handleConnect = (candidate) => {
    addTeamMember({
      id: Date.now(),
      name: candidate.name,
      role: candidate.role,
      skills: candidate.skills,
      letter: candidate.name.charAt(0)
    });
    alert(`Connected with ${candidate.name}! They have been added to your squad.`);
  };

  return (
    <div className="aihub-page">
      {/* HEADER */}
      <div className="aihub-header">
        <div>
          <div className="aihub-breadcrumb">HackathonBuddy / AI Hub</div>
          <h1>
            AI <span>INNOVATION HUB</span>
          </h1>
          <p>
            Intelligent tools to match teammates, audit squad capabilities, and architect winning hackathon solutions.
          </p>
        </div>

        <div className="aihub-header-icon">
          <Brain size={34} />
        </div>
      </div>

      {/* MODULE NAVIGATION CARDS */}
      <div className="aihub-module-grid">
        <button
          className={`aihub-module-card ${activeModule === "matching" ? "active" : ""}`}
          onClick={() => setActiveModule("matching")}
        >
          <div className="module-icon matching">
            <Users size={24} />
          </div>
          <div>
            <h3>AI Teammate Matching</h3>
            <p>Find teammates based on skill requirements, roles and domain compatibility.</p>
          </div>
        </button>

        <button
          className={`aihub-module-card ${activeModule === "skills" ? "active" : ""}`}
          onClick={() => {
            setActiveModule("skills");
            if (!skillAnalysis) handleSkillAnalysis();
          }}
        >
          <div className="module-icon skills">
            <BarChart3 size={24} />
          </div>
          <div>
            <h3>Skill Gap Analysis</h3>
            <p>Discover missing technical capabilities in your project team.</p>
          </div>
        </button>

        <button
          className={`aihub-module-card ${activeModule === "ideas" ? "active" : ""}`}
          onClick={() => {
            setActiveModule("ideas");
            if (generatedIdeas.length === 0) handleGenerateIdeas();
          }}
        >
          <div className="module-icon ideas">
            <Lightbulb size={24} />
          </div>
          <div>
            <h3>Project Idea Generator</h3>
            <p>Generate high-scoring hackathon project concepts tailored to your stack.</p>
          </div>
        </button>
      </div>

      {/* MODULE 1: AI TEAM MATCHING */}
      {activeModule === "matching" && (
        <section className="aihub-workspace">
          <div className="workspace-heading">
            <div>
              <span className="workspace-label">AI POWERED MATCHMAKER</span>
              <h2>Find Your Ideal Hackathon Teammates</h2>
              <p>Define what your team needs and our matching algorithm will rank candidate compatibility.</p>
            </div>
            <Sparkles className="heading-sparkle" />
          </div>

          <div className="matching-layout">
            <div className="aihub-form-card">
              <div className="form-group">
                <label>REQUIRED SKILLS</label>
                <input
                  type="text"
                  placeholder="React, Python, Machine Learning, Docker..."
                  value={skills}
                  onChange={(e) => setSkills(e.target.value)}
                />
                <small>Separate multiple skills with commas.</small>
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label>PROJECT DOMAIN</label>
                  <select value={domain} onChange={(e) => setDomain(e.target.value)}>
                    <option>AI / ML</option>
                    <option>Web Development</option>
                    <option>FinTech</option>
                    <option>HealthTech</option>
                    <option>Web3</option>
                    <option>All Domains</option>
                  </select>
                </div>

                <div className="form-group">
                  <label>TARGET ROLE</label>
                  <select value={role} onChange={(e) => setRole(e.target.value)}>
                    <option>Full Stack Developer</option>
                    <option>Backend Developer</option>
                    <option>Frontend Developer</option>
                    <option>ML Developer</option>
                    <option>UI/UX Designer</option>
                    <option>Any Role</option>
                  </select>
                </div>
              </div>

              <button className="ai-primary-button" onClick={handleFindTeammates} disabled={isMatchingLoading}>
                {isMatchingLoading ? <Loader2 size={18} className="animate-spin" /> : <Search size={18} />}
                {isMatchingLoading ? "RUNNING ML ENGINE..." : "RUN AI MATCHING ENGINE"}
              </button>
            </div>

            <div className="ai-info-card">
              <Target size={28} />
              <h3>How AI Matching Works</h3>
              <p>
                Our engine combines vector skill similarity, role diversity indexing,
                and historical collaboration scores to suggest optimal squads.
              </p>
              <div className="matching-points">
                <span><CheckCircle2 size={16} /> Technical Skill Overlap</span>
                <span><CheckCircle2 size={16} /> Complementary Role Distribution</span>
                <span><CheckCircle2 size={16} /> Domain Track Synergy</span>
              </div>
            </div>
          </div>

          {/* RESULTS */}
          {matchingResults.length > 0 && (
            <div className="results-section">
              <div className="results-heading">
                <h3>Recommended Candidates</h3>
                <span>{matchingResults.length} high-synergy matches</span>
              </div>

              <div className="teammate-grid">
                {matchingResults.map((member) => (
                  <div className="teammate-card" key={member.id}>
                    <div className="teammate-top">
                      <div className="avatar">{member.name.charAt(0)}</div>
                      <div>
                        <h3>{member.name}</h3>
                        <p>{member.role}</p>
                      </div>
                      <div className="match-score">
                        {member.calculatedMatch}%
                        <span>MATCH</span>
                      </div>
                    </div>

                    <div className="skill-tags">
                      {member.skills.map((skill) => (
                        <span key={skill}>{skill}</span>
                      ))}
                    </div>

                    <button className="connect-button" onClick={() => handleConnect(member)}>
                      <UserPlus size={16} />
                      ADD TO SQUAD
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </section>
      )}

      {/* MODULE 2: SKILL GAP ANALYSIS */}
      {activeModule === "skills" && (
        <section className="aihub-workspace">
          <div className="workspace-heading">
            <div>
              <span className="workspace-label">TEAM CAPABILITY AUDIT</span>
              <h2>Skill Gap & Balance Analysis</h2>
              <p>Audit your project stack against required production skills to prevent bottlenecks.</p>
            </div>
            <BarChart3 className="heading-sparkle" />
          </div>

          <div className="skill-analysis-card">
            <div className="analysis-top">
              <div>
                <h3>Production Readiness Coverage</h3>
                <p>Based on active developer profiles and project targets</p>
              </div>
              <div className="coverage-score">
                {skillAnalysis ? `${skillAnalysis.coverage}%` : "50%"}
              </div>
            </div>

            <div className="coverage-bar">
              <div style={{ width: `${skillAnalysis ? skillAnalysis.coverage : 50}%` }} />
            </div>

            <button
              className="ai-primary-button analysis-button"
              onClick={handleSkillAnalysis}
              disabled={isSkillLoading}
            >
              {isSkillLoading ? <Loader2 size={18} className="animate-spin" /> : <BarChart3 size={18} />}
              {isSkillLoading ? "ANALYZING SQUAD..." : "RE-CALCULATE SQUAD BALANCE"}
            </button>
          </div>

          {skillAnalysis && (
            <div className="analysis-result">
              <div className="analysis-column">
                <h3>✓ Available Covered Skills ({skillAnalysis.availableSkills.length})</h3>
                {skillAnalysis.availableSkills.map((skill) => (
                  <div className="analysis-skill available" key={skill}>
                    <CheckCircle2 size={16} />
                    {skill}
                  </div>
                ))}
              </div>

              <div className="analysis-column">
                <h3>⚡ Missing Capabilities to Recruit ({skillAnalysis.missingSkills.length})</h3>
                {skillAnalysis.missingSkills.map((skill) => (
                  <div className="analysis-skill missing" key={skill}>
                    <Zap size={16} />
                    {skill}
                  </div>
                ))}
              </div>
            </div>
          )}
        </section>
      )}

      {/* MODULE 3: PROJECT IDEA GENERATOR */}
      {activeModule === "ideas" && (
        <section className="aihub-workspace">
          <div className="workspace-heading">
            <div>
              <span className="workspace-label">GENERATIVE AI BLUEPRINTS</span>
              <h2>Project Idea Generator</h2>
              <p>Generate innovative, prize-worthy hackathon ideas customized to your preferred technologies.</p>
            </div>
            <Lightbulb className="heading-sparkle" />
          </div>

          <div className="idea-generator-card">
            <div className="form-row">
              <div className="form-group">
                <label>DOMAIN</label>
                <select value={ideaDomain} onChange={(e) => setIdeaDomain(e.target.value)}>
                  <option>AI / ML</option>
                  <option>HealthTech</option>
                  <option>FinTech</option>
                  <option>EdTech</option>
                  <option>Web3</option>
                  <option>Smart City</option>
                </select>
              </div>

              <div className="form-group">
                <label>PRIMARY TECH STACK</label>
                <select value={technology} onChange={(e) => setTechnology(e.target.value)}>
                  <option>React + Python</option>
                  <option>React + Node.js</option>
                  <option>Java + Spring Boot</option>
                  <option>Python + FastAPI</option>
                  <option>MERN Stack</option>
                </select>
              </div>

              <div className="form-group">
                <label>DIFFICULTY LEVEL</label>
                <select value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
                  <option>Beginner</option>
                  <option>Intermediate</option>
                  <option>Advanced</option>
                </select>
              </div>
            </div>

            <button className="ai-primary-button" onClick={handleGenerateIdeas} disabled={isIdeaLoading}>
              {isIdeaLoading ? <Loader2 size={18} className="animate-spin" /> : <Sparkles size={18} />}
              {isIdeaLoading ? "GENERATING BLUEPRINTS..." : "GENERATE HACKATHON BLUEPRINTS"}
            </button>
          </div>

          {generatedIdeas.length > 0 && (
            <div className="ideas-grid full-blueprints">
              {generatedIdeas.map((idea, index) => {
                const analysis = analyzeIdeaSkillGap(idea);
                return (
                  <div className="idea-card full-blueprint" key={index}>
                    <div className="idea-header">
                      <div className="idea-number">0{index + 1} • AI HACKATHON BLUEPRINT</div>
                      <h3>{idea.title}</h3>
                      <p>{idea.description}</p>
                      <div className="skill-tags">
                        {idea.tags.map((tag) => (
                          <span key={tag}>{tag}</span>
                        ))}
                      </div>
                    </div>

                    {/* SKILL GAP ANALYZER INTEGRATION */}
                    <div className="idea-skill-analysis">
                      <div className="analysis-card-header">
                        <div className="analysis-title">
                          <Target size={16} />
                          <span>Squad Skill Gap & Capability Analyzer</span>
                        </div>
                        <div className="coverage-badge">
                          <span>Coverage: {analysis.coveragePercent}%</span>
                          <div className="coverage-bar-track">
                            <div
                              className="coverage-bar-fill"
                              style={{ width: `${analysis.coveragePercent}%` }}
                            />
                          </div>
                        </div>
                      </div>

                      <div className="skill-analysis-three-col">
                        {/* 1. REQUIRED SKILLS */}
                        <div className="analysis-col">
                          <h4><Code2 size={14} /> Required Skills ({analysis.requiredSkills.length})</h4>
                          <div className="skill-badges-list">
                            {analysis.requiredSkills.map(s => (
                              <span key={s} className="badge-req">{s}</span>
                            ))}
                          </div>
                        </div>

                        {/* 2. AVAILABLE IN SQUAD */}
                        <div className="analysis-col">
                          <h4><CheckCircle2 size={14} /> Squad Available ({analysis.availableSkills.length})</h4>
                          <div className="skill-badges-list">
                            {analysis.availableSkills.length === 0 ? (
                              <span className="badge-empty">No direct skill match</span>
                            ) : (
                              analysis.availableSkills.map(({ skill, member }) => (
                                <span key={skill} className="badge-avail">
                                  {skill} <small>({member.isYou ? "You" : (member.name ? member.name.split(" ")[0] : "Member")})</small>
                                </span>
                              ))
                            )}
                          </div>
                        </div>

                        {/* 3. MISSING SKILL GAPS */}
                        <div className="analysis-col">
                          <h4><Zap size={14} /> Missing Gaps ({analysis.missingSkills.length})</h4>
                          <div className="skill-badges-list">
                            {analysis.missingSkills.length === 0 ? (
                              <span className="badge-complete">100% Squad Skill Coverage!</span>
                            ) : (
                              analysis.missingSkills.map(s => (
                                <span key={s} className="badge-missing">{s}</span>
                              ))
                            )}
                          </div>
                        </div>
                      </div>

                      {/* 4. LEARNING & COVERAGE ASSIGNMENTS */}
                      {analysis.missingSkillAssignments.length > 0 && (
                        <div className="learning-assignments-section">
                          <h4><Brain size={15} /> Recommended Skill Learning & Member Coverage</h4>
                          <div className="learning-cards-grid">
                            {analysis.missingSkillAssignments.map(({ skill, assignedMember, rationale }) => (
                              <div key={skill} className="learning-assignment-card">
                                <div className="learning-skill">
                                  <span className="missing-tag">Skill Gap: {skill}</span>
                                </div>
                                <div className="learning-member">
                                  <div className="member-avatar">
                                    {assignedMember.letter || (assignedMember.name ? assignedMember.name.charAt(0) : "M")}
                                  </div>
                                  <div>
                                    <strong>{assignedMember.name} {assignedMember.isYou && "(You)"}</strong>
                                    <div className="member-role">{assignedMember.role}</div>
                                  </div>
                                </div>
                                <div className="learning-rationale">
                                  <Sparkles size={13} style={{ flexShrink: 0, marginTop: "2px" }} />
                                  <span>{rationale}</span>
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </section>
      )}

      {/* FOOTER BADGE */}
      <div className="aihub-footer">
        <Code2 size={16} />
        HackathonBuddy AI Engine v2.4 • Integrated Backend & ML Service
      </div>
    </div>
  );
}

export default AIHub;