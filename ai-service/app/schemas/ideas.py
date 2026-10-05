from pydantic import BaseModel
from typing import List, Optional, Dict, Any

class TeamMemberSchema(BaseModel):
    name: str = "Member"
    skills: List[str] = []

class TeamSchema(BaseModel):
    team_name: str = "Squad"
    members: List[TeamMemberSchema] = []

class HackathonSchema(BaseModel):
    name: str = "Hackathon"
    theme: str = "General"
    problem_statement: str = "Solve key challenge"

class IdeaGenerationRequest(BaseModel):
    team: Optional[TeamSchema] = None
    hackathon: Optional[HackathonSchema] = None
    domain: Optional[str] = "General"
    userSkills: Optional[List[str]] = []
    hackathonId: Optional[int] = 0
    category: Optional[str] = "General"

class IdeaItemSchema(BaseModel):
    title: str
    description: str
    techStack: str
    problemStatement: str
    category: str
    confidenceScore: int = 85

class IdeaGenerationResponse(BaseModel):
    idea: Optional[str] = None
    ideas: List[IdeaItemSchema] = []

