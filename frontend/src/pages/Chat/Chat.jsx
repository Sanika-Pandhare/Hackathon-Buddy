import React, { useMemo, useState, useEffect, useRef } from "react";
import { Send, Search, CheckCheck, Users as UsersIcon, MessageSquare, AlertCircle, Plus, X } from "lucide-react";
import { useApp } from "../../context/AppContext";
import { chatAPI, teamAPI } from "../../api/api";
import "./Chat.css";

function Chat() {
  const { currentUser } = useApp();
  const [teams, setTeams] = useState([]);
  const [selectedTeamId, setSelectedTeamId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [inputText, setInputText] = useState("");
  const [search, setSearch] = useState("");
  const [activeTab, setActiveTab] = useState("All");
  const [showRightSidebar, setShowRightSidebar] = useState(false);
  const [loadingTeams, setLoadingTeams] = useState(true);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [forbidden, setForbidden] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newTeamName, setNewTeamName] = useState("");
  const [newTeamDesc, setNewTeamDesc] = useState("");

  const messagesEndRef = useRef(null);

  // 1. Fetch user's teams (or auto-create General Squad if none exist)
  const loadMyTeams = async () => {
    setLoadingTeams(true);
    try {
      const res = await teamAPI.getAll();
      if (res.data?.success && Array.isArray(res.data.data)) {
        let teamList = res.data.data.map((t, idx) => ({
          id: t.id,
          name: t.name,
          short: t.name ? t.name.substring(0, 2).toUpperCase() : "TM",
          description: t.description,
          members: t.members || [],
          leader: t.leader,
          color: ["purple", "blue", "pink", "navy", "green"][idx % 5],
        }));

        // If user has no teams, auto-create a General Squad so chat works immediately!
        if (teamList.length === 0 && currentUser) {
          try {
            const createRes = await teamAPI.create({
              name: "General Squad",
              description: "Default team channel for live squad collaboration",
            });
            if (createRes.data?.success && createRes.data.data) {
              const newTeam = createRes.data.data;
              teamList = [
                {
                  id: newTeam.id,
                  name: newTeam.name,
                  short: "GS",
                  description: newTeam.description,
                  members: newTeam.members || [],
                  leader: newTeam.leader,
                  color: "blue",
                },
              ];
            }
          } catch (e) {
            console.error("Error auto-creating default squad:", e);
          }
        }

        setTeams(teamList);
        if (teamList.length > 0 && (!selectedTeamId || !teamList.some((t) => t.id === selectedTeamId))) {
          setSelectedTeamId(teamList[0].id);
        }
      }
    } catch (err) {
      console.error("Failed to load user teams:", err);
    } finally {
      setLoadingTeams(false);
    }
  };

  useEffect(() => {
    loadMyTeams();
  }, [currentUser]);

  // 2. Fetch messages for selected team
  const fetchMessages = async (teamId) => {
    if (!teamId) return;
    setLoadingMessages(true);
    try {
      const res = await chatAPI.getMessages(teamId);
      if (res.data?.success && Array.isArray(res.data.data)) {
        setMessages(res.data.data);
        setForbidden(false);
      }
    } catch (err) {
      if (err.response?.status === 403) {
        setForbidden(true);
        setMessages([]);
      } else {
        console.error("Failed to fetch messages:", err);
      }
    } finally {
      setLoadingMessages(false);
    }
  };

  useEffect(() => {
    if (selectedTeamId) {
      fetchMessages(selectedTeamId);
    }
  }, [selectedTeamId]);

  // 3. Real-time updates: WebSocket with fast polling fallback
  useEffect(() => {
    if (!selectedTeamId || forbidden) return;

    let ws;
    try {
      const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
      const wsUrl = `${protocol}//localhost:8081/ws/websocket`;
      ws = new WebSocket(wsUrl);

      ws.onopen = () => {
        ws.send("CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\0");
        setTimeout(() => {
          ws.send(`SUBSCRIBE\nid:sub-0\ndestination:/topic/chat/${selectedTeamId}\n\n\0`);
        }, 300);
      };

      ws.onmessage = (event) => {
        if (event.data && event.data.includes("MESSAGE")) {
          try {
            const bodyIdx = event.data.indexOf("\n\n");
            if (bodyIdx !== -1) {
              const jsonStr = event.data.substring(bodyIdx + 2, event.data.length - 1).trim();
              if (jsonStr.startsWith("{") && jsonStr.endsWith("}")) {
                const msg = JSON.parse(jsonStr);
                setMessages((prev) => {
                  if (prev.some((m) => m.id === msg.id)) return prev;
                  return [...prev, msg];
                });
              }
            }
          } catch (e) {
            // ignore
          }
        }
      };
    } catch (e) {
      console.warn("WebSocket warning:", e);
    }

    // Polling fallback every 2 seconds
    const pollInterval = setInterval(() => {
      chatAPI
        .getMessages(selectedTeamId)
        .then((res) => {
          if (res.data?.success && Array.isArray(res.data.data)) {
            setMessages(res.data.data);
          }
        })
        .catch(() => {});
    }, 2000);

    return () => {
      clearInterval(pollInterval);
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.close();
      }
    };
  }, [selectedTeamId, forbidden]);

  // Scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const activeTeam = useMemo(() => {
    return teams.find((t) => t.id === selectedTeamId) || teams[0];
  }, [teams, selectedTeamId]);

  const filteredTeams = useMemo(() => {
    return teams.filter((t) => {
      const q = search.toLowerCase();
      return t.name.toLowerCase().includes(q) || (t.description && t.description.toLowerCase().includes(q));
    });
  }, [teams, search]);

  const handleSendMessage = async () => {
    if (!inputText.trim() || !selectedTeamId) return;

    const content = inputText.trim();
    setInputText("");

    // Optimistic UI update
    const tempMsg = {
      id: Date.now(),
      senderId: currentUser?.id,
      senderName: currentUser?.fullName || currentUser?.firstName || "You",
      content: content,
      timestamp: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, tempMsg]);

    try {
      const res = await chatAPI.sendMessage(selectedTeamId, content);
      if (res.data?.success && res.data.data) {
        const savedMsg = res.data.data;
        setMessages((prev) => prev.map((m) => (m.id === tempMsg.id ? savedMsg : m)));
      }
    } catch (err) {
      console.error("Error sending message:", err);
      // Revert temp msg on error
      setMessages((prev) => prev.filter((m) => m.id !== tempMsg.id));
      if (err.response?.status === 403) {
        alert("You are not authorized to send messages to this team.");
      }
    }
  };

  const handleCreateTeam = async (e) => {
    e.preventDefault();
    if (!newTeamName.trim()) return;
    try {
      const res = await teamAPI.create({
        name: newTeamName.trim(),
        description: newTeamDesc.trim() || "Team chat channel",
      });
      if (res.data?.success && res.data.data) {
        setShowCreateModal(false);
        setNewTeamName("");
        setNewTeamDesc("");
        await loadMyTeams();
      }
    } catch (err) {
      alert("Failed to create squad: " + (err.response?.data?.message || err.message));
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const formatTime = (ts) => {
    if (!ts) return "Just now";
    try {
      const date = new Date(ts);
      return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    } catch (e) {
      return "Just now";
    }
  };

  return (
    <div className="chat-page">
      {/* LEFT SIDEBAR - TEAMS LIST */}
      <aside className="chat-sidebar">
        <div className="sidebar-header" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div>
            <div className="section-kicker">TEAM CHAT</div>
            <h1>Messages</h1>
          </div>
          <button
            onClick={() => setShowCreateModal(true)}
            style={{
              background: "#38bdf8",
              color: "#0f172a",
              border: "none",
              borderRadius: "8px",
              padding: "6px 10px",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              gap: "4px",
              fontSize: "12px",
              fontWeight: "bold",
            }}
            title="Create New Squad"
          >
            <Plus size={14} /> New Squad
          </button>
        </div>

        <div className="conversation-search">
          <Search size={16} />
          <input
            type="text"
            placeholder="Search teams..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        <div className="chat-tabs">
          <button className="active">My Squads ({teams.length})</button>
        </div>

        <div className="conversation-list">
          {loadingTeams ? (
            <div style={{ padding: "20px", textAlign: "center", color: "#94a3b8" }}>Loading squads...</div>
          ) : filteredTeams.length === 0 ? (
            <div style={{ padding: "20px", textAlign: "center", color: "#94a3b8", fontSize: "14px" }}>
              No squads found. Click "+ New Squad" to start!
            </div>
          ) : (
            filteredTeams.map((t) => (
              <button
                key={t.id}
                className={`conversation-item ${selectedTeamId === t.id ? "selected" : ""}`}
                onClick={() => setSelectedTeamId(t.id)}
              >
                <div className={`conversation-avatar ${t.color}`}>
                  {t.short}
                  <span className="online-dot" />
                </div>

                <div className="conversation-content">
                  <div className="conversation-top">
                    <strong>{t.name}</strong>
                    <span>{t.members.length} members</span>
                  </div>
                  <div className="conversation-bottom">
                    <p>{t.description || "Team Channel"}</p>
                  </div>
                </div>
              </button>
            ))
          )}
        </div>

        <div className="sidebar-user">
          <div className="user-avatar">{currentUser?.firstName?.charAt(0) || "U"}</div>
          <div>
            <strong>{currentUser?.fullName || currentUser?.firstName || "User"}</strong>
            <span>Active in Team Chat</span>
          </div>
          <span className="user-online">●</span>
        </div>
      </aside>

      {/* MAIN CHAT AREA */}
      <main className="chat-main">
        {forbidden ? (
          <div style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "40px", textAlign: "center", color: "#ef4444" }}>
            <AlertCircle size={48} style={{ marginBottom: "16px" }} />
            <h2>Access Denied</h2>
            <p style={{ color: "#94a3b8", marginTop: "8px" }}>You are not a member of this team and cannot view or send messages.</p>
          </div>
        ) : !activeTeam ? (
          <div style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "40px", textAlign: "center" }}>
            <MessageSquare size={48} style={{ color: "#38bdf8", marginBottom: "16px" }} />
            <h2>No Squad Selected</h2>
            <p style={{ color: "#94a3b8", marginTop: "8px" }}>Create or select a squad from the sidebar to start chatting.</p>
          </div>
        ) : (
          <>
            {/* HEADER */}
            <header className="chat-header">
              <div className="chat-title-area">
                <div className="large-chat-avatar">
                  {activeTeam.short}
                  <span className="online-dot" />
                </div>
                <div>
                  <h2>{activeTeam.name}</h2>
                  <span>👥 {activeTeam.members?.length || 1} Squad Members</span>
                </div>
              </div>

              <div className="chat-header-actions">
                <button onClick={() => setShowRightSidebar(!showRightSidebar)} title="Toggle Squad Members">
                  <UsersIcon size={18} />
                </button>
              </div>
            </header>

            {/* MESSAGES AREA */}
            <section className="messages-area">
              <div className="project-banner">
                <div className="project-banner-icon">#</div>
                <div className="project-banner-content">
                  <strong>{activeTeam.name}</strong>
                  <span>Official Squad Channel</span>
                </div>
              </div>

              <div className="today-divider">
                <span />
                <b>LIVE CHAT MESSAGES</b>
                <span />
              </div>

              <div className="messages-list">
                {loadingMessages && messages.length === 0 ? (
                  <div style={{ textAlign: "center", color: "#94a3b8", padding: "20px" }}>Loading messages...</div>
                ) : messages.length === 0 ? (
                  <div style={{ textAlign: "center", color: "#94a3b8", padding: "40px 20px" }}>
                    No messages in <b>#{activeTeam.name}</b> yet. Type below to start the conversation!
                  </div>
                ) : (
                  messages.map((m) => {
                    const isSentByMe =
                      currentUser &&
                      (String(m.senderId) === String(currentUser.id) ||
                        (m.senderName && currentUser.fullName && m.senderName.toLowerCase() === currentUser.fullName.toLowerCase()) ||
                        (m.senderName && currentUser.firstName && m.senderName.toLowerCase().includes(currentUser.firstName.toLowerCase())) ||
                        (m.senderName && currentUser.email && m.senderName.toLowerCase() === currentUser.email.toLowerCase()));

                    const displayName = m.senderName && m.senderName !== "null" && m.senderName !== "null null"
                      ? m.senderName
                      : "Squad Member";

                    const senderInitial = displayName ? displayName.charAt(0).toUpperCase() : "U";

                    return (
                      <div key={m.id || Math.random()} className={`message-row ${isSentByMe ? "sent-row" : "received-row"}`}>
                        {!isSentByMe && <div className="message-avatar">{senderInitial}</div>}
                        <div className="message-block">
                          <span className="message-sender">{displayName}</span>
                          <div className={`message-bubble ${isSentByMe ? "sent-bubble" : "received-bubble"}`}>
                            {m.content}
                          </div>
                          <div className={`message-time ${isSentByMe ? "sent-time" : ""}`}>
                            {formatTime(m.timestamp || m.createdAt)}
                            {isSentByMe && <CheckCheck size={14} style={{ color: "#38bdf8", marginLeft: "4px" }} />}
                          </div>
                        </div>
                      </div>
                    );
                  })
                )}
                <div ref={messagesEndRef} />
              </div>
            </section>

            {/* COMPOSER */}
            <div className="chat-composer-wrapper">
              <div className="composer">
                <textarea
                  value={inputText}
                  onChange={(e) => setInputText(e.target.value)}
                  onKeyDown={handleKeyDown}
                  placeholder={`Message #${activeTeam.name}...`}
                  rows="1"
                />

                <button className="send-btn" onClick={handleSendMessage} title="Send message">
                  <Send size={16} />
                </button>
              </div>
            </div>
          </>
        )}
      </main>

      {/* RIGHT SIDEBAR - SQUAD DETAILS */}
      <aside className={`project-sidebar ${showRightSidebar ? "open" : ""}`}>
        <div className="project-sidebar-header">
          <span>SQUAD DETAILS</span>
        </div>

        <div className="project-icon">&lt;/&gt;</div>
        <h2>{activeTeam?.name}</h2>
        <p>{activeTeam?.description || "Live squad collaboration space."}</p>

        <div className="project-divider" />

        <section className="project-section">
          <div className="section-title-row">
            <h3>Members</h3>
            <span>{activeTeam?.members?.length || 0}</span>
          </div>

          <div className="members-list">
            {activeTeam?.members?.map((member) => {
              const memName = member.name || member.fullName || member.email || "Squad Member";
              const memInitial = memName.charAt(0).toUpperCase();
              return (
                <div className="member-item" key={member.id || member.userId || Math.random()}>
                  <div className="member-avatar">
                    {memInitial}
                    <span className="member-online" />
                  </div>
                  <div>
                    <strong>{memName}</strong>
                    <span>{member.role || "Member"}</span>
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      </aside>

      {/* CREATE SQUAD MODAL */}
      {showCreateModal && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15, 23, 42, 0.75)", backdropFilter: "blur(4px)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 9999 }}>
          <div style={{ background: "#1e293b", border: "1px solid #334155", borderRadius: "12px", width: "90%", maxWidth: "420px", padding: "24px", color: "#fff" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "16px" }}>
              <h3 style={{ margin: 0, fontSize: "18px", fontWeight: "bold" }}>Create New Squad</h3>
              <button onClick={() => setShowCreateModal(false)} style={{ background: "none", border: "none", color: "#94a3b8", cursor: "pointer" }}>
                <X size={20} />
              </button>
            </div>
            <form onSubmit={handleCreateTeam}>
              <div style={{ marginBottom: "16px" }}>
                <label style={{ display: "block", fontSize: "13px", color: "#94a3b8", marginBottom: "6px" }}>Squad Name</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. AI Champions"
                  value={newTeamName}
                  onChange={(e) => setNewTeamName(e.target.value)}
                  style={{ width: "100%", padding: "10px", background: "#0f172a", border: "1px solid #334155", borderRadius: "6px", color: "#fff", outline: "none" }}
                />
              </div>
              <div style={{ marginBottom: "20px" }}>
                <label style={{ display: "block", fontSize: "13px", color: "#94a3b8", marginBottom: "6px" }}>Description</label>
                <input
                  type="text"
                  placeholder="e.g. Building AI hackathon project"
                  value={newTeamDesc}
                  onChange={(e) => setNewTeamDesc(e.target.value)}
                  style={{ width: "100%", padding: "10px", background: "#0f172a", border: "1px solid #334155", borderRadius: "6px", color: "#fff", outline: "none" }}
                />
              </div>
              <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}>
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  style={{ padding: "8px 16px", background: "transparent", border: "1px solid #475569", color: "#cbd5e1", borderRadius: "6px", cursor: "pointer" }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  style={{ padding: "8px 16px", background: "#38bdf8", border: "none", color: "#0f172a", fontWeight: "bold", borderRadius: "6px", cursor: "pointer" }}
                >
                  Create & Open Chat
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default Chat;