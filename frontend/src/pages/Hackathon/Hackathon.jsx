import React, { useMemo, useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  Search,
  MapPin,
  Calendar,
  UsersRound,
  Clock3,
  X,
  ExternalLink,
  SlidersHorizontal,
  Sparkles,
  CheckCircle2
} from "lucide-react";
import { useApp } from "../../context/AppContext";
import { hackathonAPI, registrationAPI } from "../../api/api";
import "./Hackathon.css";

function Hackathon() {
  const navigate = useNavigate();
  const {
    hackathons: contextHackathons,
    registeredHackathons: contextRegistered,
    toggleHackathonRegistration,
    currentUser
  } = useApp();

  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("All");
  const [status, setStatus] = useState("All");
  const [selectedHackathon, setSelectedHackathon] = useState(null);
  const [showRegForm, setShowRegForm] = useState(false);
  const [participationType, setParticipationType] = useState("INDIVIDUAL");
  const [registrationSuccess, setRegistrationSuccess] = useState(false);
  const [formData, setFormData] = useState({
    teamName: "",
    role: "Fullstack",
    experienceLevel: "Intermediate",
    githubUrl: "",
    motivation: "",
    agreeTerms: true,
  });

  const [hackathonsList, setHackathonsList] = useState(contextHackathons);
  const [userRegistrations, setUserRegistrations] = useState(contextRegistered);

  const handleRegistrationSubmit = async (e) => {
    e.preventDefault();
    if (!selectedHackathon) return;

    const payload = {
      hackathonTitle: selectedHackathon.title,
      hackathonCategory: selectedHackathon.category || "General",
      participationType,
      teamName: participationType === "TEAM" ? formData.teamName : "Individual Hacker",
      role: formData.role,
      experienceLevel: formData.experienceLevel,
      githubUrl: formData.githubUrl,
      motivation: formData.motivation,
      agreeTerms: formData.agreeTerms,
    };

    await toggleHackathonRegistration(selectedHackathon.id, payload);
    setUserRegistrations((prev) => (prev.includes(selectedHackathon.id) ? prev : [...prev, selectedHackathon.id]));
    setRegistrationSuccess(true);
  };

  useEffect(() => {
    // 1. Fetch all hackathons from Spring Boot HackathonController (/api/hackathons)
    hackathonAPI.getAll()
      .then((res) => {
        if (res.data?.success && Array.isArray(res.data.data)) {
          setHackathonsList(res.data.data);
        }
      })
      .catch(() => {});

    // 2. Fetch my registrations from RegistrationController (/api/registrations)
    registrationAPI.getMyRegistrations()
      .then((res) => {
        if (res.data?.success && Array.isArray(res.data.data)) {
          setUserRegistrations(res.data.data.map(r => r.id));
        }
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (contextHackathons && contextHackathons.length > 0) {
      setHackathonsList(contextHackathons);
    }
  }, [contextHackathons]);

  useEffect(() => {
    if (contextRegistered) {
      setUserRegistrations(contextRegistered);
    }
  }, [contextRegistered]);

  const categories = [
    "All",
    "AI/ML",
    "Smart City",
    "FinTech",
    "Web3",
    "Environment",
    "Healthcare",
  ];

  const filteredHackathons = useMemo(() => {
    const listToFilter = hackathonsList.length > 0 ? hackathonsList : contextHackathons;
    return listToFilter.filter((h) => {
      const matchesSearch =
        h.title.toLowerCase().includes(search.toLowerCase()) ||
        (h.category && h.category.toLowerCase().includes(search.toLowerCase())) ||
        (h.description && h.description.toLowerCase().includes(search.toLowerCase()));

      const matchesCategory =
        category === "All" || h.category === category;

      const matchesStatus =
        status === "All" || h.status === status;

      return matchesSearch && matchesCategory && matchesStatus;
    });
  }, [hackathonsList, contextHackathons, search, category, status]);

  return (
    <div className="hackathon-page">
      {/* MAIN CONTENT */}
      <main className="hackathon-main">
        {/* TOP HEADER */}
        <header className="hackathon-header">
          <div className="header-title">
            <div className="hackathon-breadcrumb">HackathonBuddy / Hackathons</div>
            <h1>Explore Hackathons</h1>
            <p>
              Discover high-impact opportunities, join elite competitions, and win prizes.
            </p>
          </div>

          <div className="header-right">
            <div className="header-search">
              <Search size={18} />
              <input
                type="text"
                placeholder="Search hackathons, topics, skills..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
          </div>
        </header>

        {/* HERO BANNER */}
        <section className="hackathon-hero">
          <div className="hero-content">
            <div className="hero-badge">
              <Sparkles size={14} />
              FIND YOUR NEXT CHALLENGE
            </div>
            <h2>
              Build. Compete.
              <span> Create Impact.</span>
            </h2>
            <p>
              Match with challenges aligned with your tech stack ({currentUser?.techSkills || "React, Python, AI"}).
            </p>
          </div>
          <div className="hero-emoji">🏆</div>
        </section>

        {/* CONTROLS / FILTERS */}
        <section className="hackathon-controls">
          <div className="category-filter">
            <span className="filter-label">Categories</span>
            <div className="category-buttons">
              {categories.map((item) => (
                <button
                  key={item}
                  className={
                    category === item
                      ? "category-button active"
                      : "category-button"
                  }
                  onClick={() => setCategory(item)}
                >
                  {item}
                </button>
              ))}
            </div>
          </div>

          <div className="status-filter">
            <SlidersHorizontal size={17} />
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value)}
            >
              <option value="All">All Status</option>
              <option value="Open">Open Now</option>
              <option value="Closed">Closed</option>
            </select>
          </div>
        </section>

        {/* RESULTS HEADER */}
        <div className="results-header">
          <div>
            <h3>All Hackathons</h3>
            <span>{filteredHackathons.length} active opportunities found</span>
          </div>
        </div>

        {/* HACKATHON GRID */}
        <section className="hackathon-grid">
          {filteredHackathons.length === 0 ? (
            <div className="no-results">
              <div className="no-results-icon">🔍</div>
              <h3>No hackathons found</h3>
              <p>Try changing your search query or reset filters.</p>
              <button
                onClick={() => {
                  setSearch("");
                  setCategory("All");
                  setStatus("All");
                }}
              >
                Clear All Filters
              </button>
            </div>
          ) : (
            filteredHackathons.map((hackathon) => {
              const isRegistered = userRegistrations.includes(hackathon.id);
              return (
                <article className="hackathon-card" key={hackathon.id}>
                  {/* CARD TOP */}
                  <div className="card-top">
                    <div className={`hackathon-icon ${hackathon.color || "purple"}`}>
                      {hackathon.icon || "🏆"}
                    </div>
                    <div className="card-match">
                      <span>{hackathon.match || 85}%</span>
                      <small>Match</small>
                    </div>
                  </div>

                  {/* TITLE & DESCRIPTION */}
                  <div className="card-title-area">
                    <div className="card-category">{hackathon.category || "General"}</div>
                    <h3>{hackathon.title}</h3>
                    <p>{hackathon.description}</p>
                  </div>

                  {/* INFO LIST */}
                  <div className="card-info">
                    <div>
                      <Calendar size={15} />
                      <span>{hackathon.date || hackathon.startDate || "Upcoming"}</span>
                    </div>
                    <div>
                      <MapPin size={15} />
                      <span>{hackathon.location || "Online"}</span>
                    </div>
                    <div>
                      <UsersRound size={15} />
                      <span>{(hackathon.participants || 0).toLocaleString()} hackers</span>
                    </div>
                    <div>
                      <Clock3 size={15} />
                      <span>{hackathon.duration || "48 Hours"}</span>
                    </div>
                  </div>

                  {/* PRIZE */}
                  <div className="card-prize">
                    <div>
                      <small>PRIZE POOL</small>
                      <strong>{hackathon.prize || hackathon.prizePool || "₹1,00,000"}</strong>
                    </div>
                    <div className="card-level">{hackathon.level || "Intermediate"}</div>
                  </div>

                  {/* ACTIONS */}
                  <div className="card-actions">
                    <button
                      className="details-button"
                      onClick={() => setSelectedHackathon(hackathon)}
                    >
                      View Details
                    </button>

                    <button
                      className={`register-button ${isRegistered ? "registered" : ""}`}
                      onClick={() => {
                        if (isRegistered) {
                          toggleHackathonRegistration(hackathon.id);
                          setUserRegistrations(prev => prev.filter(id => id !== hackathon.id));
                        } else {
                          setSelectedHackathon(hackathon);
                          setShowRegForm(true);
                        }
                      }}
                    >
                      {isRegistered ? "✓ Registered" : "Register"}
                    </button>
                  </div>
                </article>
              );
            })
          )}
        </section>
      </main>

      {/* DETAILS & UNSTOP-STYLE REGISTRATION MODAL */}
      {selectedHackathon && (
        <div
          className="modal-overlay"
          onClick={() => {
            setSelectedHackathon(null);
            setShowRegForm(false);
            setRegistrationSuccess(false);
          }}
        >
          <div
            className="hackathon-modal unstop-modal-theme"
            onClick={(e) => e.stopPropagation()}
            style={{ maxWidth: "620px", width: "92%", maxHeight: "90vh", overflowY: "auto", borderRadius: "18px", padding: "28px" }}
          >
            <button
              className="modal-close"
              onClick={() => {
                setSelectedHackathon(null);
                setShowRegForm(false);
                setRegistrationSuccess(false);
              }}
              aria-label="Close modal"
            >
              <X size={20} />
            </button>

            {registrationSuccess ? (
              /* UNSTOP TICKET CONFIRMATION */
              <div style={{ textAlign: "center", padding: "15px 10px" }}>
                <div style={{ width: "70px", height: "70px", borderRadius: "50%", background: "linear-gradient(135deg, #10b981, #059669)", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 16px", color: "white", boxShadow: "0 0 25px rgba(16, 185, 129, 0.4)" }}>
                  <CheckCircle2 size={42} />
                </div>
                <div style={{ background: "rgba(16, 185, 129, 0.15)", color: "#10b981", padding: "4px 14px", borderRadius: "20px", fontSize: "12px", fontWeight: "700", display: "inline-block", marginBottom: "12px" }}>
                  ✓ REGISTRATION CONFIRMED • UNSTOP PORTAL
                </div>
                <h2 style={{ fontSize: "24px", color: "#f8fafc", margin: "0 0 8px" }}>
                  You're Registered for {selectedHackathon.title}!
                </h2>
                <p style={{ color: "#94a3b8", fontSize: "14px", margin: "0 0 24px" }}>
                  Your spot is locked in. We have synced your details with the hackathon portal and sent a notification to your dashboard.
                </p>

                {/* TICKET CARD */}
                <div style={{ background: "linear-gradient(145deg, #1e1b4b, #0f172a)", border: "1px dashed #6366f1", borderRadius: "16px", padding: "20px", textAlign: "left", marginBottom: "24px", position: "relative" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", borderBottom: "1px solid rgba(255,255,255,0.1)", paddingBottom: "12px", marginBottom: "14px" }}>
                    <div>
                      <small style={{ color: "#a5b4fc", fontSize: "11px", textTransform: "uppercase", letterSpacing: "1px" }}>Participant</small>
                      <h4 style={{ color: "white", margin: 0, fontSize: "16px" }}>{currentUser?.fullName || "Developer Hacker"}</h4>
                    </div>
                    <div style={{ textAlign: "right" }}>
                      <small style={{ color: "#a5b4fc", fontSize: "11px", textTransform: "uppercase", letterSpacing: "1px" }}>Ticket ID</small>
                      <div style={{ color: "#38bdf8", fontWeight: "bold", fontFamily: "monospace", fontSize: "14px" }}>#HB-REG-{(selectedHackathon.id || "").toString().slice(-4)}-{Math.floor(1000 + Math.random() * 9000)}</div>
                    </div>
                  </div>

                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px", fontSize: "13px" }}>
                    <div>
                      <span style={{ color: "#64748b", display: "block" }}>Platform / Track</span>
                      <strong style={{ color: "#e2e8f0" }}>{selectedHackathon.platform || selectedHackathon.category || "Unstop Challenge"}</strong>
                    </div>
                    <div>
                      <span style={{ color: "#64748b", display: "block" }}>Team Name</span>
                      <strong style={{ color: "#e2e8f0" }}>{formData.teamName || "Individual Hacker"}</strong>
                    </div>
                    <div>
                      <span style={{ color: "#64748b", display: "block" }}>Prize Pool</span>
                      <strong style={{ color: "#f59e0b" }}>{selectedHackathon.prize || selectedHackathon.prizePool || "₹1,00,000"}</strong>
                    </div>
                    <div>
                      <span style={{ color: "#64748b", display: "block" }}>Event Status</span>
                      <strong style={{ color: "#10b981" }}>Confirmed / Active</strong>
                    </div>
                  </div>
                </div>

                <div style={{ display: "flex", gap: "12px" }}>
                  <button
                    onClick={() => {
                      setSelectedHackathon(null);
                      setShowRegForm(false);
                      setRegistrationSuccess(false);
                      navigate("/teams");
                    }}
                    style={{ flex: 1, padding: "12px", borderRadius: "10px", border: "1px solid #6366f1", background: "transparent", color: "#a5b4fc", fontWeight: "600", cursor: "pointer" }}
                  >
                    Find Teammates →
                  </button>
                  <button
                    onClick={() => {
                      setSelectedHackathon(null);
                      setShowRegForm(false);
                      setRegistrationSuccess(false);
                    }}
                    style={{ flex: 1, padding: "12px", borderRadius: "10px", border: "none", background: "linear-gradient(135deg, #6366f1, #4f46e5)", color: "white", fontWeight: "700", cursor: "pointer", boxShadow: "0 4px 14px rgba(99, 102, 241, 0.4)" }}
                  >
                    Done & Explore More
                  </button>
                </div>
              </div>
            ) : !showRegForm ? (
              /* HACKATHON DETAILS VIEW */
              <>
                <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "16px" }}>
                  <div className={`modal-icon ${selectedHackathon.color || "purple"}`} style={{ width: "56px", height: "56px", fontSize: "28px", margin: 0 }}>
                    {selectedHackathon.icon || "🏆"}
                  </div>
                  <div>
                    <span style={{ background: "rgba(99, 102, 241, 0.15)", color: "#a5b4fc", padding: "4px 10px", borderRadius: "12px", fontSize: "12px", fontWeight: "600" }}>
                      {selectedHackathon.platform || selectedHackathon.category || "Hackathon"}
                    </span>
                    <h2 style={{ margin: "4px 0 0", fontSize: "22px", color: "white" }}>{selectedHackathon.title}</h2>
                  </div>
                </div>

                <p className="modal-description" style={{ color: "#94a3b8", lineHeight: "1.6", fontSize: "14px", margin: "0 0 20px" }}>
                  {selectedHackathon.description}
                </p>

                <div className="modal-details" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "14px", background: "rgba(15, 23, 42, 0.6)", padding: "16px", borderRadius: "12px", border: "1px solid #1e293b", marginBottom: "20px" }}>
                  <div>
                    <Calendar size={17} color="#818cf8" />
                    <span>
                      <small style={{ color: "#64748b" }}>Event Date</small>
                      <strong style={{ color: "#e2e8f0" }}>{selectedHackathon.date || selectedHackathon.startDate || "Upcoming"}</strong>
                    </span>
                  </div>
                  <div>
                    <Clock3 size={17} color="#38bdf8" />
                    <span>
                      <small style={{ color: "#64748b" }}>Duration</small>
                      <strong style={{ color: "#e2e8f0" }}>{selectedHackathon.duration || "48 Hours"}</strong>
                    </span>
                  </div>
                  <div>
                    <MapPin size={17} color="#34d399" />
                    <span>
                      <small style={{ color: "#64748b" }}>Location / Mode</small>
                      <strong style={{ color: "#e2e8f0" }}>{selectedHackathon.location || "Online"}</strong>
                    </span>
                  </div>
                  <div>
                    <UsersRound size={17} color="#f472b6" />
                    <span>
                      <small style={{ color: "#64748b" }}>Participants</small>
                      <strong style={{ color: "#e2e8f0" }}>{(selectedHackathon.participants || 0).toLocaleString()} hackers</strong>
                    </span>
                  </div>
                </div>

                <div className="modal-prize" style={{ background: "linear-gradient(135deg, rgba(245, 158, 11, 0.1), rgba(217, 119, 6, 0.15))", border: "1px solid rgba(245, 158, 11, 0.3)", borderRadius: "12px", padding: "14px 20px", display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "20px" }}>
                  <span style={{ color: "#fcd34d", fontWeight: "600", fontSize: "14px" }}>Total Prize Pool</span>
                  <strong style={{ color: "#fbbf24", fontSize: "20px", fontWeight: "800" }}>{selectedHackathon.prize || selectedHackathon.prizePool || "₹1,00,000"}</strong>
                </div>

                <button
                  className={`modal-register ${
                    userRegistrations.includes(selectedHackathon.id) ? "registered" : ""
                  }`}
                  style={{ width: "100%", padding: "14px", borderRadius: "12px", fontWeight: "700", fontSize: "15px", cursor: "pointer", border: "none", background: userRegistrations.includes(selectedHackathon.id) ? "#10b981" : "linear-gradient(135deg, #6366f1, #4f46e5)", color: "white", boxShadow: "0 4px 16px rgba(99, 102, 241, 0.3)", marginBottom: "10px" }}
                  onClick={() => {
                    if (userRegistrations.includes(selectedHackathon.id)) {
                      toggleHackathonRegistration(selectedHackathon.id);
                      setUserRegistrations(prev => prev.filter(id => id !== selectedHackathon.id));
                    } else {
                      setShowRegForm(true);
                    }
                  }}
                >
                  {userRegistrations.includes(selectedHackathon.id)
                    ? "✓ You are Registered (Click to withdraw)"
                    : "Proceed to Unstop Registration →"}
                </button>

                {selectedHackathon.url && (
                  <button
                    className="modal-external"
                    style={{ width: "100%", padding: "11px", borderRadius: "10px", border: "1px solid #334155", background: "transparent", color: "#94a3b8", display: "flex", alignItems: "center", justifyContent: "center", gap: "8px", cursor: "pointer" }}
                    onClick={() => window.open(selectedHackathon.url, "_blank")}
                  >
                    Visit Official Portal Website
                    <ExternalLink size={14} />
                  </button>
                )}
              </>
            ) : (
              /* UNSTOP-STYLE REGISTRATION FORM */
              <div className="registration-form">
                <div style={{ borderBottom: "1px solid #1e293b", paddingBottom: "14px", marginBottom: "18px" }}>
                  <span style={{ color: "#6366f1", fontSize: "12px", fontWeight: "700", letterSpacing: "0.5px", textTransform: "uppercase" }}>
                    UNSTOP HACKATHON ENTRY FORM
                  </span>
                  <h2 style={{ margin: "4px 0 0", fontSize: "20px", color: "white" }}>
                    Register for {selectedHackathon.title}
                  </h2>
                </div>

                {/* PARTICIPATION TYPE TAB TOGGLE */}
                <div style={{ display: "flex", background: "#0f172a", borderRadius: "10px", padding: "4px", marginBottom: "20px", border: "1px solid #1e293b" }}>
                  <button
                    type="button"
                    onClick={() => setParticipationType("INDIVIDUAL")}
                    style={{ flex: 1, padding: "10px", borderRadius: "8px", border: "none", background: participationType === "INDIVIDUAL" ? "#6366f1" : "transparent", color: participationType === "INDIVIDUAL" ? "white" : "#94a3b8", fontWeight: "600", fontSize: "13px", cursor: "pointer" }}
                  >
                    👤 Individual Hacker
                  </button>
                  <button
                    type="button"
                    onClick={() => setParticipationType("TEAM")}
                    style={{ flex: 1, padding: "10px", borderRadius: "8px", border: "none", background: participationType === "TEAM" ? "#6366f1" : "transparent", color: participationType === "TEAM" ? "white" : "#94a3b8", fontWeight: "600", fontSize: "13px", cursor: "pointer" }}
                  >
                    👥 Team Registration
                  </button>
                </div>

                <form
                  onSubmit={handleRegistrationSubmit}
                  style={{ display: "flex", flexDirection: "column", gap: "16px" }}
                >
                  {/* FULL NAME & EMAIL (READ ONLY / PREFILLED) */}
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
                    <div>
                      <label style={{ display: "block", marginBottom: "6px", fontSize: "13px", color: "#cbd5e1" }}>Full Name</label>
                      <input
                        type="text"
                        value={currentUser?.fullName || "Developer"}
                        disabled
                        style={{ width: "100%", padding: "10px", borderRadius: "8px", border: "1px solid #1e293b", background: "#090d16", color: "#94a3b8", cursor: "not-allowed" }}
                      />
                    </div>
                    <div>
                      <label style={{ display: "block", marginBottom: "6px", fontSize: "13px", color: "#cbd5e1" }}>Email Address</label>
                      <input
                        type="email"
                        value={currentUser?.email || "user@example.com"}
                        disabled
                        style={{ width: "100%", padding: "10px", borderRadius: "8px", border: "1px solid #1e293b", background: "#090d16", color: "#94a3b8", cursor: "not-allowed" }}
                      />
                    </div>
                  </div>

                  {/* TEAM NAME IF TEAM REGISTRATION */}
                  {participationType === "TEAM" && (
                    <div>
                      <label style={{ display: "block", marginBottom: "6px", fontSize: "13px", color: "#cbd5e1" }}>
                        Team Name <span style={{ color: "#ef4444" }}>*</span>
                      </label>
                      <input
                        type="text"
                        value={formData.teamName}
                        onChange={(e) => setFormData({ ...formData, teamName: e.target.value })}
                        placeholder="e.g. CodeCrafters / AI Innovators"
                        required
                        style={{ width: "100%", padding: "10px 14px", borderRadius: "8px", border: "1px solid #334155", background: "#0f172a", color: "white" }}
                      />
                    </div>
                  )}

                  {/* ROLE & EXPERIENCE LEVEL */}
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
                    <div>
                      <label style={{ display: "block", marginBottom: "6px", fontSize: "13px", color: "#cbd5e1" }}>
                        Developer Role <span style={{ color: "#ef4444" }}>*</span>
                      </label>
                      <select
                        value={formData.role}
                        onChange={(e) => setFormData({ ...formData, role: e.target.value })}
                        required
                        style={{ width: "100%", padding: "10px 12px", borderRadius: "8px", border: "1px solid #334155", background: "#0f172a", color: "white" }}
                      >
                        <option value="Fullstack">Fullstack Developer</option>
                        <option value="Frontend">Frontend Developer</option>
                        <option value="Backend">Backend Developer</option>
                        <option value="AI/ML">AI & ML Specialist</option>
                        <option value="Design">UI/UX Designer</option>
                        <option value="Mobile">Mobile App Developer</option>
                      </select>
                    </div>

                    <div>
                      <label style={{ display: "block", marginBottom: "6px", fontSize: "13px", color: "#cbd5e1" }}>Experience Level</label>
                      <select
                        value={formData.experienceLevel}
                        onChange={(e) => setFormData({ ...formData, experienceLevel: e.target.value })}
                        style={{ width: "100%", padding: "10px 12px", borderRadius: "8px", border: "1px solid #334155", background: "#0f172a", color: "white" }}
                      >
                        <option value="Beginner">Beginner (0-1 yrs)</option>
                        <option value="Intermediate">Intermediate (1-3 yrs)</option>
                        <option value="Advanced">Advanced (3+ yrs)</option>
                        <option value="Pro Hacker">Pro Hackathon Winner</option>
                      </select>
                    </div>
                  </div>

                  {/* GITHUB / PORTFOLIO LINK */}
                  <div>
                    <label style={{ display: "block", marginBottom: "6px", fontSize: "13px", color: "#cbd5e1" }}>GitHub / Portfolio Link</label>
                    <input
                      type="url"
                      value={formData.githubUrl}
                      onChange={(e) => setFormData({ ...formData, githubUrl: e.target.value })}
                      placeholder="https://github.com/yourname"
                      style={{ width: "100%", padding: "10px 14px", borderRadius: "8px", border: "1px solid #334155", background: "#0f172a", color: "white" }}
                    />
                  </div>

                  {/* MOTIVATION / PROPOSED IDEA */}
                  <div>
                    <label style={{ display: "block", marginBottom: "6px", fontSize: "13px", color: "#cbd5e1" }}>
                      Why do you want to join this challenge? <span style={{ color: "#ef4444" }}>*</span>
                    </label>
                    <textarea
                      value={formData.motivation}
                      onChange={(e) => setFormData({ ...formData, motivation: e.target.value })}
                      placeholder="Share what skills or solution you plan to build..."
                      required
                      rows="3"
                      style={{ width: "100%", padding: "10px 14px", borderRadius: "8px", border: "1px solid #334155", background: "#0f172a", color: "white", resize: "vertical" }}
                    />
                  </div>

                  {/* AGREEMENT CHECKBOX */}
                  <label style={{ display: "flex", alignItems: "center", gap: "10px", fontSize: "13px", color: "#94a3b8", cursor: "pointer", marginTop: "4px" }}>
                    <input
                      type="checkbox"
                      checked={formData.agreeTerms}
                      onChange={(e) => setFormData({ ...formData, agreeTerms: e.target.checked })}
                      required
                      style={{ accentColor: "#6366f1", width: "16px", height: "16px" }}
                    />
                    I accept the Code of Conduct and official Unstop competition rules.
                  </label>

                  {/* FORM ACTION BUTTONS */}
                  <div style={{ display: "flex", gap: "12px", marginTop: "12px" }}>
                    <button
                      type="button"
                      onClick={() => setShowRegForm(false)}
                      style={{ flex: 1, padding: "12px", background: "transparent", border: "1px solid #334155", borderRadius: "10px", color: "#cbd5e1", fontWeight: "600", cursor: "pointer" }}
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      style={{ flex: 1.5, padding: "12px", background: "linear-gradient(135deg, #6366f1, #4f46e5)", border: "none", borderRadius: "10px", color: "white", fontWeight: "700", cursor: "pointer", boxShadow: "0 4px 14px rgba(99, 102, 241, 0.4)" }}
                    >
                      Complete Registration 🚀
                    </button>
                  </div>
                </form>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default Hackathon;